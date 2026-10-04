package com.musicclubapp.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prawdziwe pliki z ImageIO z dopisanymi metadanymi takimi, jakie zapisuje telefon: EXIF z GPS i obrotem,
 * XMP, komentarz, MPF. Po czyszczeniu plik ma sie dalej dekodowac, miec te same piksele i ten sam obrot.
 */
@DisplayName("Czyszczenie metadanych zdjec")
class ImageMetadataTest {

    private static final String GPS = "GPS 52.4064N 16.9252E dom";

    private static byte[] jpeg() throws IOException {
        BufferedImage obraz = new BufferedImage(40, 20, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = obraz.createGraphics();
        g.setColor(Color.MAGENTA);
        g.fillRect(0, 0, 20, 20);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(obraz, "jpg", out);
        return out.toByteArray();
    }

    /** EXIF little-endian (tak zapisuje wiekszosc telefonow): obrot + "GPS" w opisie. */
    private static byte[] exifSegment(int orientation, boolean little) {
        byte[] opis = GPS.getBytes(StandardCharsets.US_ASCII);
        ByteArrayOutputStream tiff = new ByteArrayOutputStream();
        if (little) {
            tiff.writeBytes(new byte[] {'I', 'I', 0x2A, 0, 8, 0, 0, 0});
            tiff.writeBytes(new byte[] {2, 0});
            // ImageDescription (0x010E), ASCII, dlugosc, offset do tekstu za IFD
            int offset = 8 + 2 + 2 * 12 + 4;
            tiff.writeBytes(new byte[] {0x0E, 0x01, 2, 0, (byte) opis.length, 0, 0, 0, (byte) offset, 0, 0, 0});
            tiff.writeBytes(new byte[] {0x12, 0x01, 3, 0, 1, 0, 0, 0, (byte) orientation, 0, 0, 0});
            tiff.writeBytes(new byte[] {0, 0, 0, 0});
        } else {
            tiff.writeBytes(new byte[] {'M', 'M', 0, 0x2A, 0, 0, 0, 8});
            tiff.writeBytes(new byte[] {0, 2});
            int offset = 8 + 2 + 2 * 12 + 4;
            tiff.writeBytes(new byte[] {0x01, 0x0E, 0, 2, 0, 0, 0, (byte) opis.length, 0, 0, 0, (byte) offset});
            tiff.writeBytes(new byte[] {0x01, 0x12, 0, 3, 0, 0, 0, 1, 0, (byte) orientation, 0, 0});
            tiff.writeBytes(new byte[] {0, 0, 0, 0});
        }
        tiff.writeBytes(opis);
        byte[] dane = tiff.toByteArray();
        return segment(0xE1, concat("Exif\0\0".getBytes(StandardCharsets.ISO_8859_1), dane));
    }

    private static byte[] segment(int marker, byte[] dane) {
        int dl = dane.length + 2;
        return concat(new byte[] {(byte) 0xFF, (byte) marker, (byte) (dl >> 8), (byte) dl}, dane);
    }

    private static byte[] concat(byte[]... czesci) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] c : czesci) {
            out.writeBytes(c);
        }
        return out.toByteArray();
    }

    /** Wstawia segmenty zaraz po SOI (FFD8). */
    private static byte[] zSegmentami(byte[] jpeg, byte[]... segmenty) {
        byte[] srodek = concat(segmenty);
        return concat(new byte[] {jpeg[0], jpeg[1]}, srodek, java.util.Arrays.copyOfRange(jpeg, 2, jpeg.length));
    }

    private static boolean zawiera(byte[] dane, String tekst) {
        return new String(dane, StandardCharsets.ISO_8859_1).contains(tekst);
    }

    private static BufferedImage dekoduj(byte[] dane) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(dane));
    }

    @Test
    @DisplayName("JPEG z telefonu: GPS, XMP, komentarz i MPF znikaja, obraz sie dekoduje, piksele bez zmian")
    void jpegLosesMetadata() throws IOException {
        byte[] czysty = jpeg();
        byte[] zTelefonu = zSegmentami(czysty,
            exifSegment(1, true),
            segment(0xE1, "http://ns.adobe.com/xap/1.0/\0<x:xmpmeta>GPS tajne</x:xmpmeta>".getBytes(StandardCharsets.ISO_8859_1)),
            segment(0xE2, "MPF\0dane drugiego obrazu".getBytes(StandardCharsets.ISO_8859_1)),
            segment(0xED, "Photoshop 3.0\0IPTC miasto".getBytes(StandardCharsets.ISO_8859_1)),
            segment(0xFE, "komentarz z aparatu".getBytes(StandardCharsets.ISO_8859_1)));
        assertThat(zawiera(zTelefonu, "GPS")).isTrue();

        byte[] wynik = ImageMetadata.strip(zTelefonu);

        assertThat(zawiera(wynik, "GPS")).isFalse();
        assertThat(zawiera(wynik, "Exif")).isFalse();             // obrot 1 = nie ma czego zostawiac
        assertThat(zawiera(wynik, "MPF")).isFalse();
        assertThat(zawiera(wynik, "Photoshop")).isFalse();
        assertThat(zawiera(wynik, "komentarz")).isFalse();
        // to, co zostalo, to dokladnie plik z ImageIO (bez dopisanych segmentow)
        assertThat(wynik).isEqualTo(czysty);
        BufferedImage obraz = dekoduj(wynik);
        assertThat(obraz.getWidth()).isEqualTo(40);
        assertThat(new Color(obraz.getRGB(5, 5)).getRed()).isGreaterThan(200);
    }

    @Test
    @DisplayName("obrot z EXIF zostaje jako minimalny EXIF - zarowno II, jak i MM - a GPS znika")
    void orientationSurvives() throws IOException {
        for (boolean little : new boolean[] {true, false}) {
            byte[] zTelefonu = zSegmentami(jpeg(), exifSegment(6, little));
            byte[] wynik = ImageMetadata.strip(zTelefonu);

            assertThat(zawiera(wynik, "GPS")).isFalse();
            assertThat(zawiera(wynik, "Exif")).isTrue();
            // minimalny EXIF stoi zaraz po JFIF (APP0) i mowi "6"
            int app1 = new String(wynik, StandardCharsets.ISO_8859_1).indexOf("Exif") - 4;
            assertThat(wynik[app1] & 0xFF).isEqualTo(0xFF);
            assertThat(wynik[app1 + 1] & 0xFF).isEqualTo(0xE1);
            assertThat(ImageMetadata.readOrientation(wynik, app1 + 10, 26)).isEqualTo(6);
            assertThat(dekoduj(wynik).getWidth()).isEqualTo(40);
        }
    }

    @Test
    @DisplayName("profil barw ICC (APP2) zostaje - bez niego kolory potrafia wyjsc wyblakle")
    void iccStays() {
        byte[] icc = segment(0xE2, "ICC_PROFILE\0\1\1profil".getBytes(StandardCharsets.ISO_8859_1));
        byte[] wynik = ImageMetadata.strip(zSegmentami(jpegUnchecked(), icc, exifSegment(1, true)));
        assertThat(zawiera(wynik, "ICC_PROFILE")).isTrue();
        assertThat(zawiera(wynik, "GPS")).isFalse();
    }

    @Test
    @DisplayName("PNG: tEXt, iTXt, zTXt, eXIf i tIME znikaja, obraz sie dekoduje")
    void pngLosesMetadata() throws IOException {
        BufferedImage obraz = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(obraz, "png", out);
        byte[] png = out.toByteArray();
        // fragmenty wstawiamy zaraz po IHDR (8 bajtow sygnatury + 25 bajtow IHDR)
        byte[] metadane = concat(
            chunk("tEXt", "Comment\0" + GPS),
            chunk("iTXt", "XML:com.adobe.xmp\0\0\0\0\0GPS"),
            chunk("zTXt", "Opis\0\0GPS"),
            chunk("eXIf", "MM\0*GPS"),
            chunk("tIME", "\u0007ê\n\u0004\f\u0000\u0000"));
        byte[] zMetadanymi = concat(java.util.Arrays.copyOf(png, 33), metadane,
            java.util.Arrays.copyOfRange(png, 33, png.length));
        assertThat(dekoduj(zMetadanymi)).isNotNull();

        byte[] wynik = ImageMetadata.strip(zMetadanymi);
        assertThat(zawiera(wynik, "GPS")).isFalse();
        assertThat(zawiera(wynik, "tIME")).isFalse();
        assertThat(wynik).isEqualTo(png);
        assertThat(dekoduj(wynik).getWidth()).isEqualTo(10);
    }

    @Test
    @DisplayName("plik, ktory tylko udaje JPEG/PNG albo jest urwany, zostaje bez zmian")
    void malformedStaysAsIs() {
        byte[] urwany = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE1, 0x7F, 0x7F, 'E', 'x'};
        assertThat(ImageMetadata.strip(urwany)).isEqualTo(urwany);
        byte[] tekst = "to nie jest obrazek".getBytes(StandardCharsets.US_ASCII);
        assertThat(ImageMetadata.strip(tekst)).isEqualTo(tekst);
        assertThat(ImageMetadata.strip(new byte[] {1, 2})).containsExactly(1, 2);
        assertThat(ImageMetadata.strip(null)).isNull();
    }

    private static byte[] jpegUnchecked() {
        try {
            return jpeg();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] chunk(String typ, String dane) {
        byte[] d = dane.getBytes(StandardCharsets.ISO_8859_1);
        byte[] t = typ.getBytes(StandardCharsets.ISO_8859_1);
        CRC32 crc = new CRC32();
        crc.update(t);
        crc.update(d);
        long c = crc.getValue();
        return concat(new byte[] {(byte) (d.length >> 24), (byte) (d.length >> 16), (byte) (d.length >> 8), (byte) d.length},
            t, d, new byte[] {(byte) (c >> 24), (byte) (c >> 16), (byte) (c >> 8), (byte) c});
    }
}
