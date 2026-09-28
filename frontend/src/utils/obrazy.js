/*
 * Zmniejszanie zdjec przed wyslaniem.
 *
 * Zdjecie prosto z aparatu ma 3-8 MB. Serwer przyjmuje 5 MB na plik, wiec
 * czesc takich zdjec w ogole nie przechodzila, a te, ktore przechodzily,
 * pobieral potem w calosci KAZDY, kto przewinal obok nich tablice.
 *
 * Zmniejszamy dopiero po przekroczeniu limitu - mniejsze pliki ida w oryginale
 * i nie traca na jakosci bez powodu.
 */

/** Powyzej tego rozmiaru zdjecie jest przerabiane. Tyle samo przyjmuje serwer. */
const LIMIT_BAJTOW = 5 * 1024 * 1024;

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
const POMIJANE_TYPY = new Set(['image/gif']);

/** Czy przegladarka umie zapisac WebP. Sprawdzamy raz. */
let obslugujeWebp = null;

function formatWyjsciowy() {
  if (obslugujeWebp === null) {
    const plotno = document.createElement('canvas');
    plotno.width = 1;
    plotno.height = 1;
    obslugujeWebp = plotno.toDataURL('image/webp').startsWith('data:image/webp');
  }
  /* WebP trzyma przezroczystosc i wazy mniej niz JPEG przy tej samej jakosci. */
  return obslugujeWebp ? 'image/webp' : 'image/jpeg';
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

function narysuj(obraz, skala, typ, jakosc) {
  const szerokosc = Math.max(1, Math.round((obraz.width || obraz.naturalWidth) * skala));
  const wysokosc = Math.max(1, Math.round((obraz.height || obraz.naturalHeight) * skala));

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
 * Oddaje zdjecie zmieszczone w limicie albo oryginal, gdy nie trzeba
 * go ruszac lub gdy przerabianie sie nie uda.
 */
export async function zmniejszJesliTrzeba(plik) {
  if (!(plik instanceof Blob) || plik.size <= LIMIT_BAJTOW || POMIJANE_TYPY.has(plik.type)) {
    return plik;
  }

  try {
    const obraz = await wczytaj(plik);
    const typ = formatWyjsciowy();
    const dluzszyBok = Math.max(obraz.width || obraz.naturalWidth, obraz.height || obraz.naturalHeight);
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

    return plik;
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
