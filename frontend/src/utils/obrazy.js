/*
 * Zmniejszanie zdjec przed wyslaniem.
 *
 * Zdjecie prosto z aparatu ma 3-8 MB. Serwer przyjmuje 5 MB na plik, wiec
 * czesc takich zdjec w ogole nie przechodzila, a te, ktore przechodzily,
 * pobieral potem w calosci KAZDY, kto przewinal obok nich tablice.
 *
 * Za duze zdjecie widac w formularzu i mozna je zmniejszyc jednym klikaniem.
 * Kto tego nie zrobi, nie dostanie bledu: to samo dzieje sie automatycznie
 * przy wysylaniu (patrz api/posty.js i api/konto.js).
 */

/** Powyzej tego rozmiaru zdjecie trzeba zmniejszyc. Tyle samo przyjmuje serwer. */
export const LIMIT_BAJTOW = 5 * 1024 * 1024;

/** Od tego zaczynamy: dluzszy bok nie wiekszy niz tyle pikseli. */
const MAKS_BOK = 2048;

/** Kolejne proby jakosci, od najlepszej. */
const JAKOSCI = [0.85, 0.75, 0.65, 0.55];

/** Ile razy wolno jeszcze zmniejszyc wymiary, gdy sama jakosc nie wystarczy. */
const PROBY_WYMIAROW = 4;

/*
 * GIF-y zostawiamy w spokoju. Plotno zapisuje jedna klatke, wiec animacja
 * zamienilaby sie w nieruchomy obrazek - a to juz nie jest to samo zdjecie.
 */
const BEZ_ZMNIEJSZANIA = new Set(['image/gif']);

/** Czy przegladarka umie zapisac WebP. Sprawdzamy raz. */
let obslugujeWebp = null;

function umieWebp() {
  if (obslugujeWebp === null) {
    const plotno = document.createElement('canvas');
    plotno.width = 1;
    plotno.height = 1;
    obslugujeWebp = plotno.toDataURL('image/webp').startsWith('data:image/webp');
  }
  return obslugujeWebp;
}

/**
 * Czytelny rozmiar do pokazania obok zdjecia.
 *
 * Miedzy liczba a jednostka jest spacja nierozdzielajaca - w waskiej kolumnie
 * z miniatura podpis lamal sie inaczej w srodku, na "1,8 / MB".
 */
export function formatujRozmiar(bajty) {
  if (bajty >= 1024 * 1024) {
    return `${(bajty / 1024 / 1024).toFixed(1).replace('.', ',')}\u00a0MB`;
  }
  return `${Math.round(bajty / 1024)}\u00a0kB`;
}

export function czyZaDuzy(plik) {
  return plik instanceof Blob && plik.size > LIMIT_BAJTOW;
}

/** GIF-a nie ruszamy, wiec nie ma sensu proponowac przycisku. */
export function czyDaSieZmniejszyc(plik) {
  return plik instanceof Blob && !BEZ_ZMNIEJSZANIA.has(plik.type);
}

/**
 * Wczytuje plik do postaci, ktora da sie narysowac na plotnie.
 *
 * imageOrientation: 'from-image' jest tu wazne. Zdjecia robione telefonem
 * trzymanym bokiem sa zapisane poziomo, a informacja o obrocie siedzi
 * w danych EXIF. Przy rysowaniu na plotnie EXIF przepada - bez tej opcji
 * kazde takie zdjecie wyszloby obrocone o 90 stopni.
 */
async function wczytaj(plik) {
  if (typeof createImageBitmap === 'function') {
    return createImageBitmap(plik, { imageOrientation: 'from-image' });
  }

  /* Starsze przegladarki: zwykly <img>. Obrotu z EXIF nie naprawi, ale
     lepsze to niz brak zmniejszania. */
  const adres = URL.createObjectURL(plik);
  try {
    return await new Promise((gotowe, blad) => {
      const obraz = new Image();
      obraz.onload = () => gotowe(obraz);
      obraz.onerror = () => blad(new Error('Nie udalo sie wczytac obrazu'));
      obraz.src = adres;
    });
  } finally {
    URL.revokeObjectURL(adres);
  }
}

const szerokoscObrazu = (obraz) => obraz.width || obraz.naturalWidth;
const wysokoscObrazu = (obraz) => obraz.height || obraz.naturalHeight;

/**
 * Czy obraz ma gdziekolwiek przezroczystosc.
 *
 * Sprawdzamy na pomniejszonej kopii - pelne zdjecie z aparatu to kilkanascie
 * milionow pikseli i skanowanie ich wszystkich byloby wolniejsze niz samo
 * zmniejszanie. Do odpowiedzi "czy jest tu gdzies dziura" taka probka
 * w zupelnosci wystarcza.
 */
function maPrzezroczystosc(obraz) {
  const BOK = 128;
  const skala = Math.min(1, BOK / Math.max(szerokoscObrazu(obraz), wysokoscObrazu(obraz)));
  const szerokosc = Math.max(1, Math.round(szerokoscObrazu(obraz) * skala));
  const wysokosc = Math.max(1, Math.round(wysokoscObrazu(obraz) * skala));

  const plotno = document.createElement('canvas');
  plotno.width = szerokosc;
  plotno.height = wysokosc;
  const kontekst = plotno.getContext('2d', { willReadFrequently: true });
  kontekst.drawImage(obraz, 0, 0, szerokosc, wysokosc);

  const piksele = kontekst.getImageData(0, 0, szerokosc, wysokosc).data;
  for (let i = 3; i < piksele.length; i += 4) {
    if (piksele[i] < 250) {
      return true;
    }
  }
  return false;
}

/**
 * Format, w ktorym zapisujemy wynik.
 *
 * Domyslnie JPEG: czyta go wszystko, a dla zdjec jest bezkonkurencyjny.
 * PNG odpada jako format wyjsciowy - przy zdjeciu 2048x1536 wychodzil
 * 6,69 MB wobec 0,66 MB w JPEG, czyli dziesiec razy wiecej (zmierzone).
 * Zapisanie "skompresowanego" zdjecia jako PNG czesto dalo by plik WIEKSZY
 * niz oryginal, czyli dokladnie odwrotnie, niz o to chodzi.
 *
 * WebP wchodzi tylko tam, gdzie JPEG nie umie: przy przezroczystosci.
 * JPEG podlozylby pod nia biale tlo.
 */
function wybierzFormat(obraz) {
  if (maPrzezroczystosc(obraz) && umieWebp()) {
    return 'image/webp';
  }
  return 'image/jpeg';
}

function narysuj(obraz, skala, typ, jakosc) {
  const szerokosc = Math.max(1, Math.round(szerokoscObrazu(obraz) * skala));
  const wysokosc = Math.max(1, Math.round(wysokoscObrazu(obraz) * skala));

  const plotno = document.createElement('canvas');
  plotno.width = szerokosc;
  plotno.height = wysokosc;
  const kontekst = plotno.getContext('2d');

  /* JPEG nie zna przezroczystosci - bez tego przezroczyste tlo wyszloby czarne. */
  if (typ === 'image/jpeg') {
    kontekst.fillStyle = '#ffffff';
    kontekst.fillRect(0, 0, szerokosc, wysokosc);
  }

  kontekst.drawImage(obraz, 0, 0, szerokosc, wysokosc);
  return new Promise((gotowe) => plotno.toBlob(gotowe, typ, jakosc));
}

function zbudujPlik(blob, oryginal, typ) {
  const rozszerzenie = typ === 'image/webp' ? '.webp' : '.jpg';
  const nazwa = (oryginal.name || 'zdjecie').replace(/\.[^.]+$/, '') + rozszerzenie;
  return new File([blob], nazwa, { type: typ, lastModified: Date.now() });
}

/**
 * Zmniejsza zdjecie tak, zeby zmiescilo sie w limicie.
 *
 * Rzuca wyjatkiem, gdy sie nie uda - wolajacy ma wtedy szanse cos powiedziec
 * uzytkownikowi zamiast po cichu wyslac plik, ktory i tak zostanie odrzucony.
 */
export async function zmniejsz(plik) {
  const obraz = await wczytaj(plik);
  const typ = wybierzFormat(obraz);
  const dluzszyBok = Math.max(szerokoscObrazu(obraz), wysokoscObrazu(obraz));
  let skala = Math.min(1, MAKS_BOK / dluzszyBok);

  for (let proba = 0; proba < PROBY_WYMIAROW; proba++) {
    for (const jakosc of JAKOSCI) {
      const blob = await narysuj(obraz, skala, typ, jakosc);
      if (blob && blob.size <= LIMIT_BAJTOW) {
        return zbudujPlik(blob, plik, typ);
      }
    }
    /* Sama jakosc nie wystarczyla - schodzimy z wymiarami i probujemy od nowa. */
    skala *= 0.8;
  }

  throw new Error('Nie udalo sie zmiescic zdjecia w limicie');
}

/**
 * Oddaje zdjecie zmieszczone w limicie albo oryginal, gdy nie trzeba
 * go ruszac lub gdy przerabianie sie nie uda.
 *
 * To siatka bezpieczenstwa przy wysylaniu - kto nie kliknal "zmniejsz"
 * w formularzu, i tak nie zobaczy bledu.
 */
export async function zmniejszJesliTrzeba(plik) {
  if (!czyZaDuzy(plik) || !czyDaSieZmniejszyc(plik)) {
    return plik;
  }

  try {
    return await zmniejsz(plik);
  } catch {
    /*
     * Cokolwiek by sie nie udalo - wysylamy oryginal. Serwer odrzuci go
     * z czytelnym komunikatem, a to i tak lepsze niz zjedzenie calego wpisu
     * przez blad w przerabianiu zdjecia.
     */
    return plik;
  }
}

/**
 * To samo dla listy plikow, ale po kolei, nie rownolegle.
 *
 * Dziesiec zdjec naraz to na telefonie kilkaset megabajtow w pamieci -
 * przegladarka potrafi przy tym ubic karte.
 */
export async function zmniejszWszystkie(pliki) {
  const wynik = [];
  for (const plik of pliki) {
    wynik.push(await zmniejszJesliTrzeba(plik));
  }
  return wynik;
}
