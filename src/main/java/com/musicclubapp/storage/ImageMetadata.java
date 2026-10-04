package com.musicclubapp.storage;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Usuwa z wgranych zdjec dane, ktorych nikt nie chcial publikowac: przede wszystkim EXIF z aparatu
 * telefonu - wspolrzedne GPS miejsca, w ktorym zrobiono zdjecie (czesto: dom), model telefonu, date.
 * Zdjecia w postach, awatary i galeria profilu (karta w trybie Poznawaj) oglada kazdy, wiec takie dane
 * nie moga wyjsc poza serwer.
 *
 * <p>JPEG: znikaja segmenty APP1 (EXIF, XMP), APP3-APP13 i APP15 (m.in. IPTC z Photoshopa) oraz komentarze;
 * APP2 zostaje tylko jako profil barw ICC (bez niego kolory potrafia wyjsc wyblakle), APP0 (JFIF) i APP14
 * (Adobe - potrzebny do odczytu CMYK) zostaja. Jedyna rzecz z EXIF, ktora przezywa, to obrot zdjecia: telefon
 * trzymany bokiem zapisuje obraz poziomo i znacznik "obroc o 90 stopni" - bez niego zdjecie wisialoby
 * przewrocone. Wtedy wstawiamy nowy, minimalny EXIF z samym obrotem.</p>
 *
 * <p>PNG: znikaja fragmenty eXIf, tEXt, zTXt, iTXt i tIME. Inne formaty (GIF, WebP) przechodza bez zmian -
 * telefony ich nie produkuja ze zdjec, a przegladarka przy zmniejszaniu zdjecia (utils/obrazy.js) i tak
 * zapisuje je bez EXIF.</p>
 *
 * <p>Plik, ktorego nie da sie przeczytac (urwany, z bledna dlugoscia segmentu), zostaje taki, jaki byl - to,
 * czego nie umiemy przeczytac my, nie przeczyta tez przegladarka jako EXIF.</p>
 */
public final class ImageMetadata {

    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final byte[] EXIF_HEADER = {'E', 'x', 'i', 'f', 0, 0};
    private static final byte[] ICC_HEADER = "ICC_PROFILE\0".getBytes(StandardCharsets.ISO_8859_1);

    private static final int ORIENTATION_TAG = 0x0112;

    private ImageMetadata() {
    }

    /** Zdjecie bez metadanych; rozpoznaje format po pierwszych bajtach, a nie po tym, co podal klient. */
    public static byte[] strip(byte[] data) {
        if (data == null || data.length < 8) {
            return data;
        }
        try {
            if ((data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8) {
                return stripJpeg(data);
            }
            if (Arrays.equals(Arrays.copyOf(data, 8), PNG_SIGNATURE)) {
                return stripPng(data);
            }
        } catch (MalformedImageException e) {
            return data;
        }
        return data;
    }

    /* ------------------------------------------------------------------ */
    /*  JPEG                                                               */
    /* ------------------------------------------------------------------ */

    static byte[] stripJpeg(byte[] in) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(in.length);
        out.write(0xFF);
        out.write(0xD8);

        int orientation = 1;
        int pos = 2;
        // Gdzie wstawic minimalny EXIF: zaraz po APP0 (JFIF), jesli jest, inaczej zaraz po SOI
        int insertAt = 2;
        boolean afterApp0 = false;

        while (pos < in.length) {
            if ((in[pos] & 0xFF) != 0xFF) {
                throw new MalformedImageException();
            }
            // Bajty wypelnienia 0xFF przed markerem
            while (pos < in.length && (in[pos] & 0xFF) == 0xFF) {
                pos++;
            }
            if (pos >= in.length) {
                throw new MalformedImageException();
            }
            int marker = in[pos] & 0xFF;
            pos++;

            // Markery bez dlugosci
            if (marker == 0xD8 || marker == 0x01 || (marker >= 0xD0 && marker <= 0xD7)) {
                out.write(0xFF);
                out.write(marker);
                continue;
            }
            if (marker == 0xD9) {
                out.write(0xFF);
                out.write(0xD9);
                break;
            }
            if (pos + 2 > in.length) {
                throw new MalformedImageException();
            }
            int length = ((in[pos] & 0xFF) << 8) | (in[pos + 1] & 0xFF);
            if (length < 2 || pos + length > in.length) {
                throw new MalformedImageException();
            }
            int dataStart = pos + 2;
            int dataLength = length - 2;

            if (marker == 0xDA) {
                // Poczatek danych obrazu: od tego miejsca kopiujemy wszystko do konca pliku
                out.write(0xFF);
                out.write(0xDA);
                out.write(in, pos, in.length - pos);
                return withOrientation(out.toByteArray(), orientation, insertAt);
            }

            boolean keep = switch (marker) {
                case 0xE1 -> {
                    if (startsWith(in, dataStart, dataLength, EXIF_HEADER)) {
                        orientation = readOrientation(in, dataStart + EXIF_HEADER.length, dataLength - EXIF_HEADER.length);
                    }
                    yield false;
                }
                case 0xE2 -> startsWith(in, dataStart, dataLength, ICC_HEADER);
                case 0xE3, 0xE4, 0xE5, 0xE6, 0xE7, 0xE8, 0xE9, 0xEA, 0xEB, 0xEC, 0xED, 0xEF, 0xFE -> false;
                default -> true;
            };
            if (keep) {
                out.write(0xFF);
                out.write(marker);
                out.write(in, pos, length);
                if (marker == 0xE0 && !afterApp0) {
                    afterApp0 = true;
                    insertAt = out.size();
                }
            }
            pos += length;
        }
        return withOrientation(out.toByteArray(), orientation, insertAt);
    }

    private static byte[] withOrientation(byte[] jpeg, int orientation, int insertAt) {
        if (orientation < 2 || orientation > 8) {
            return jpeg;
        }
        byte[] exif = minimalExif(orientation);
        byte[] wynik = new byte[jpeg.length + exif.length];
        System.arraycopy(jpeg, 0, wynik, 0, insertAt);
        System.arraycopy(exif, 0, wynik, insertAt, exif.length);
        System.arraycopy(jpeg, insertAt, wynik, insertAt + exif.length, jpeg.length - insertAt);
        return wynik;
    }

    /** Segment APP1 z EXIF-em, w ktorym jest tylko obrot (jeden wpis w IFD0, big-endian). */
    static byte[] minimalExif(int orientation) {
        return new byte[] {
            (byte) 0xFF, (byte) 0xE1, 0x00, 0x22,           // APP1, dlugosc 34
            'E', 'x', 'i', 'f', 0, 0,                        // naglowek EXIF
            'M', 'M', 0x00, 0x2A, 0x00, 0x00, 0x00, 0x08,    // TIFF big-endian, IFD0 od bajtu 8
            0x00, 0x01,                                      // jeden wpis
            0x01, 0x12, 0x00, 0x03, 0x00, 0x00, 0x00, 0x01,  // Orientation, SHORT, 1 wartosc
            0x00, (byte) orientation, 0x00, 0x00,            // wartosc
            0x00, 0x00, 0x00, 0x00                           // brak kolejnego IFD
        };
    }

    /** Obrot z IFD0 EXIF-u (TIFF od {@code start}); 1 = bez obrotu, takze gdy czegos nie da sie przeczytac. */
    static int readOrientation(byte[] in, int start, int length) {
        if (length < 8 || start + length > in.length) {
            return 1;
        }
        boolean little;
        if (in[start] == 'I' && in[start + 1] == 'I') {
            little = true;
        } else if (in[start] == 'M' && in[start + 1] == 'M') {
            little = false;
        } else {
            return 1;
        }
        long ifd = u32(in, start + 4, little);
        if (ifd < 8 || ifd + 2 > length) {
            return 1;
        }
        int entries = u16(in, start + (int) ifd, little);
        for (int i = 0; i < entries; i++) {
            int entry = start + (int) ifd + 2 + i * 12;
            if (entry + 12 > start + length) {
                return 1;
            }
            if (u16(in, entry, little) == ORIENTATION_TAG) {
                int value = u16(in, entry + 8, little);
                return value >= 1 && value <= 8 ? value : 1;
            }
        }
        return 1;
    }

    private static int u16(byte[] b, int i, boolean little) {
        return little
            ? (b[i] & 0xFF) | ((b[i + 1] & 0xFF) << 8)
            : ((b[i] & 0xFF) << 8) | (b[i + 1] & 0xFF);
    }

    private static long u32(byte[] b, int i, boolean little) {
        long a = b[i] & 0xFF;
        long c = b[i + 1] & 0xFF;
        long d = b[i + 2] & 0xFF;
        long e = b[i + 3] & 0xFF;
        return little ? a | (c << 8) | (d << 16) | (e << 24) : (a << 24) | (c << 16) | (d << 8) | e;
    }

    private static boolean startsWith(byte[] in, int start, int length, byte[] prefix) {
        if (length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (in[start + i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    /* ------------------------------------------------------------------ */
    /*  PNG                                                                */
    /* ------------------------------------------------------------------ */

    static byte[] stripPng(byte[] in) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(in.length);
        out.write(in, 0, 8);
        int pos = 8;
        while (pos < in.length) {
            if (pos + 12 > in.length) {
                throw new MalformedImageException();
            }
            long length = ((long) (in[pos] & 0xFF) << 24) | ((in[pos + 1] & 0xFF) << 16)
                | ((in[pos + 2] & 0xFF) << 8) | (in[pos + 3] & 0xFF);
            if (length > in.length - pos - 12L) {
                throw new MalformedImageException();
            }
            String type = new String(in, pos + 4, 4, StandardCharsets.ISO_8859_1);
            int total = (int) length + 12;
            boolean drop = switch (type) {
                case "eXIf", "tEXt", "zTXt", "iTXt", "tIME" -> true;
                default -> false;
            };
            if (!drop) {
                out.write(in, pos, total);
            }
            pos += total;
            if ("IEND".equals(type)) {
                break;
            }
        }
        return out.toByteArray();
    }

    /** Plik nie jest tym, czym udaje - wtedy zostawiamy go w spokoju. */
    private static final class MalformedImageException extends RuntimeException {
        MalformedImageException() {
            super(null, null, false, false);
        }
    }
}
