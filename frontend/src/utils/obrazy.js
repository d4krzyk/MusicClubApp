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
 *
 * Tu jest tez zapis z edytora zdjec (kadr i obrot - components/obraz/EdytorZdjecia.jsx):
 * ta sama droga przez plotno i ten sam limit.
 */

import { macierzObrotu, obroconeWymiary, wPikselach } from './kadr';
import { liczKlatkiGif } from './gifKlatki';

/** Powyzej tego rozmiaru zdjecie trzeba zmniejszyc. Tyle samo przyjmuje serwer. */
export const LIMIT_BAJTOW = 5 * 1024 * 1024;

/** Od tego zaczynamy: dluzszy bok nie wiekszy niz tyle pikseli. */
const MAKS_BOK = 2048;

/** Kolejne proby jakosci, od najlepszej. */
const JAKOSCI = [0.85, 0.75, 0.65, 0.55];

/** Po edycji zaczynamy od lepszej jakosci - zdjecie i tak jest juz przyciete do potrzebnego fragmentu. */
const JAKOSCI_EDYCJI = [0.9, 0.82, 0.72, 0.62];

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
 * Zdjecie z telefonu trzymanego bokiem jest zapisane POZIOMO, a informacja
 * "obroc o 90 stopni" siedzi osobno, w danych EXIF. Gdyby przepadla przy
 * przerabianiu, kazde takie zdjecie wisialoby na tablicy przewrocone.
 *
 * imageOrientation: 'from-image' mowi wprost: zastosuj ten obrot.
 * Dzisiejsze przegladarki robia to same, nawet bez tej opcji (zmierzone
 * w Chromium 141: plik 600x400 ze znacznikiem obrotu wychodzi 400x600
 * tak samo z opcja, jak i bez niej). Starsze potrafily go zignorowac,
 * a napisanie tego wprost nic nie kosztuje.
 */
export async function wczytaj(plik) {
  if (typeof createImageBitmap === 'function') {
    try {
      return await createImageBitmap(plik, { imageOrientation: 'from-image' });
    } catch {
      /* Przegladarki sprzed zmiany specyfikacji znaja tylko 'none' i 'flipY' - na 'from-image' rzucaja
         TypeError. Bez opcji obrot z EXIF moglby przepasc, wiec idziemy droga przez <img>. */
    }
  }

  /* Starsze przegladarki: zwykly <img>. Obrot z EXIF przezywa tez ta droge -
     przegladarka oddaje obrocony obraz i taki trafia na plotno (sprawdzone). */
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

export const szerokoscObrazu = (obraz) => obraz.width || obraz.naturalWidth;
export const wysokoscObrazu = (obraz) => obraz.height || obraz.naturalHeight;

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

/**
 * Rysuje obraz na nowym plotnie i zapisuje. Z edycja: najpierw obrot (co 90 stopni),
 * potem wyciecie kadru - kadr jest w pikselach obrazu juz obroconego (patrz kadr.js).
 */
function narysuj(obraz, skala, typ, jakosc, edycja = null) {
  const W0 = szerokoscObrazu(obraz);
  const H0 = wysokoscObrazu(obraz);
  const obrot = edycja?.obrot ?? 0;
  const { w: W, h: H } = obroconeWymiary(W0, H0, obrot);
  const kadr = edycja?.kadr ? wPikselach(edycja.kadr, W, H) : { x: 0, y: 0, w: W, h: H };
  const szerokosc = Math.max(1, Math.round(kadr.w * skala));
  const wysokosc = Math.max(1, Math.round(kadr.h * skala));

  const plotno = document.createElement('canvas');
  plotno.width = szerokosc;
  plotno.height = wysokosc;
  const kontekst = plotno.getContext('2d');
  if (!kontekst) {
    return Promise.resolve(null);
  }

  /* JPEG nie zna przezroczystosci - bez tego przezroczyste tlo wyszloby czarne. */
  if (typ === 'image/jpeg') {
    kontekst.fillStyle = '#ffffff';
    kontekst.fillRect(0, 0, szerokosc, wysokosc);
  }

  kontekst.imageSmoothingQuality = 'high';
  kontekst.scale(szerokosc / kadr.w, wysokosc / kadr.h);
  kontekst.translate(-kadr.x, -kadr.y);
  kontekst.transform(...macierzObrotu(obrot, W0, H0));
  kontekst.drawImage(obraz, 0, 0, W0, H0);
  return new Promise((gotowe) => plotno.toBlob(gotowe, typ, jakosc));
}

/**
 * Wspolna petla: najpierw coraz nizsza jakosc, potem coraz mniejsze wymiary, az plik zmiesci sie
 * w limicie. toBlob potrafi oddac null (brak pamieci na telefonie) - wtedy probujemy mniejszego.
 */
async function zapiszWLimicie(obraz, plik, skalaStartowa, jakosci, edycja = null) {
  const typ = wybierzFormat(obraz);
  let skala = skalaStartowa;
  for (let proba = 0; proba < PROBY_WYMIAROW; proba++) {
    for (const jakosc of jakosci) {
      const blob = await narysuj(obraz, skala, typ, jakosc, edycja);
      if (blob && blob.size <= LIMIT_BAJTOW) {
        return zbudujPlik(blob, plik, typ);
      }
    }
    /* Sama jakosc nie wystarczyla - schodzimy z wymiarami i probujemy od nowa. */
    skala *= 0.8;
  }

  throw new Error('Nie udalo sie zmiescic zdjecia w limicie');
}

function zbudujPlik(blob, oryginal, zadany) {
  /* Stare Safari na prosbe o WebP po cichu oddaje PNG - plik ma miec typ i rozszerzenie tego, co naprawde jest w srodku */
  const typ = blob.type || zadany;
  const rozszerzenie = { 'image/webp': '.webp', 'image/png': '.png' }[typ] ?? '.jpg';
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
  try {
    const dluzszyBok = Math.max(szerokoscObrazu(obraz), wysokoscObrazu(obraz));
    return await zapiszWLimicie(obraz, plik, Math.min(1, MAKS_BOK / dluzszyBok), JAKOSCI);
  } finally {
    zwolnij(obraz);
  }
}

/** ImageBitmap trzyma odkodowane piksele poza pamiecia JavaScriptu - oddajemy je od razu. */
export function zwolnij(obraz) {
  if (obraz && typeof obraz.close === 'function') {
    obraz.close();
  }
}

/**
 * Zapis z edytora: obraz juz wczytany (edytor go pokazuje), obrot i kadr. Dluzszy bok wyniku najwyzej
 * MAKS_BOK, plik w limicie, format jak przy zmniejszaniu (JPEG, a przy przezroczystosci WebP).
 */
export async function zapiszEdycje(obraz, edycja, oryginal) {
  const W0 = szerokoscObrazu(obraz);
  const H0 = wysokoscObrazu(obraz);
  const { w: W, h: H } = obroconeWymiary(W0, H0, edycja.obrot);
  const kadr = wPikselach(edycja.kadr, W, H);
  const skala = Math.min(1, MAKS_BOK / Math.max(kadr.w, kadr.h));
  return zapiszWLimicie(obraz, oryginal, skala, JAKOSCI_EDYCJI, { obrot: edycja.obrot, kadr });
}

/**
 * Pamiec edycji: plik wynikowy -> oryginal i ustawienia. Ponowna edycja zaczyna od oryginalu
 * z poprzednim kadrem, a nie od juz przycietej i drugi raz skompresowanej kopii.
 * WeakMap - gdy plik wypada z formularza, wpis znika razem z nim.
 */
const edycje = new WeakMap();

export function zapamietajEdycje(wynik, dane) {
  if (wynik && dane?.oryginal && wynik !== dane.oryginal) {
    edycje.set(wynik, dane);
  }
}

export function edycjaPliku(plik) {
  return (plik && edycje.get(plik)) ?? null;
}

/**
 * Czy GIF ma wiecej niz jedna klatke. Edycja zapisuje jedna klatke, wiec animacja by przepadla -
 * edytor o tym uprzedza. Kiedy nie da sie tego stwierdzic, odpowiadamy "tak": ostrzezenie nic nie psuje.
 */
export async function czyAnimowanyGif(plik) {
  if (!(plik instanceof Blob) || plik.type !== 'image/gif') {
    return false;
  }
  try {
    return liczKlatkiGif(new Uint8Array(await plik.arrayBuffer())) > 1;
  } catch {
    return true;
  }
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
