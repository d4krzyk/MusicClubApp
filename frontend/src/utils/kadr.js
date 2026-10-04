/*
 * Geometria edytora zdjec: kadr i obrot.
 *
 * Bez DOM-u i bez stanu - same liczby, zeby dalo sie to sprawdzic testami
 * (kadr.test.js, "npm test"). Edytor trzyma kadr w pikselach obrazu JUZ
 * obroconego: obraz 4000x3000 po obrocie o 90 stopni ma 3000x4000 i w tym
 * ukladzie leza x, y, w, h.
 *
 * Kadr przy przeciaganiu liczy sie zawsze od stanu z chwili nacisniecia
 * i calkowitego przesuniecia wskaznika - nie krok po kroku. Liczony krok po
 * kroku zbieralby bledy zaokraglen i "pelzal" przy dociskaniu do krawedzi.
 */

/** Obroty, ktore edytor zna: tylko co cwierc obrotu, zeby nie bylo pustych rogow. */
export const OBROTY = [0, 90, 180, 270];

/** Najkrotszy bok kadru w pikselach obrazu (chyba ze caly obraz jest mniejszy). */
export const MIN_BOK = 32;

/** Wymiary po obrocie: przy 90 i 270 stopniach szerokosc zamienia sie z wysokoscia. */
export function obroconeWymiary(szerokosc, wysokosc, obrot) {
  return obrot % 180 === 0
    ? { w: szerokosc, h: wysokosc }
    : { w: wysokosc, h: szerokosc };
}

/** Kolejny obrot: kierunek +1 = w prawo (zgodnie ze wskazowkami zegara), -1 = w lewo. */
export function nastepnyObrot(obrot, kierunek) {
  return (((obrot + kierunek * 90) % 360) + 360) % 360;
}

const ogranicz = (wartosc, od, doo) => Math.min(Math.max(wartosc, od), doo);

/** Najkrotszy dozwolony bok kadru dla obrazu W x H. */
export function minimalnyBok(W, H) {
  return Math.min(MIN_BOK, W, H);
}

/** Najwiekszy kadr o danych proporcjach (szerokosc / wysokosc), wysrodkowany. Bez proporcji - caly obraz. */
export function pelnyKadr(W, H, proporcje = null) {
  if (!proporcje) {
    return { x: 0, y: 0, w: W, h: H };
  }
  let w = W;
  let h = w / proporcje;
  if (h > H) {
    h = H;
    w = h * proporcje;
  }
  return { x: (W - w) / 2, y: (H - h) / 2, w, h };
}

/**
 * Wpycha kadr w granice obrazu: najpierw przycina za duzy, potem przesuwa.
 * Ostatnia linia obrony przed bledami zaokraglen - wynik zawsze lezy w obrazie.
 */
export function wGranicach(kadr, W, H) {
  // Bez dolnej granicy 1 px: w obrazku 2x1 kadr 3:1 ma 2 x 0,67 i proporcje maja sie zgadzac
  // (calych pikseli pilnuje dopiero wPikselach). Zero i ujemne - na wszelki wypadek 1 px.
  const w = kadr.w > 0 ? Math.min(kadr.w, W) : Math.min(1, W);
  const h = kadr.h > 0 ? Math.min(kadr.h, H) : Math.min(1, H);
  return {
    x: ogranicz(kadr.x, 0, W - w),
    y: ogranicz(kadr.y, 0, H - h),
    w,
    h,
  };
}

/** Przesuniecie calego kadru - zatrzymuje sie na krawedziach obrazu. */
export function przesun(kadr, dx, dy, W, H) {
  return {
    ...kadr,
    x: ogranicz(kadr.x + dx, 0, W - kadr.w),
    y: ogranicz(kadr.y + dy, 0, H - kadr.h),
  };
}

/**
 * Uchwyty: kierunek w poziomie (sx) i w pionie (sy). -1 = lewa/gorna krawedz,
 * +1 = prawa/dolna, 0 = ta os sie nie zmienia.
 */
export const UCHWYTY = {
  nw: [-1, -1], n: [0, -1], ne: [1, -1],
  w: [-1, 0], e: [1, 0],
  sw: [-1, 1], s: [0, 1], se: [1, 1],
};

/**
 * Zmiana rozmiaru kadru uchwytem.
 *
 * Bez proporcji kazda ruszana krawedz chodzi osobno, a przeciwlegla stoi.
 * Z proporcjami: w rogu decyduje ten kierunek, w ktorym wskaznik poszedl dalej,
 * a przy krawedzi bocznej druga os rosnie po rowno w obie strony (i zsuwa sie
 * od krawedzi obrazu, gdy po jednej stronie zabraknie miejsca).
 */
export function zmienRozmiar(kadr, uchwyt, dx, dy, W, H, proporcje = null) {
  const kierunek = UCHWYTY[uchwyt];
  if (!kierunek) {
    return kadr;
  }
  const [sx, sy] = kierunek;
  const min = minimalnyBok(W, H);
  const prawa = kadr.x + kadr.w;
  const dol = kadr.y + kadr.h;

  if (!proporcje) {
    // Kadr juz mniejszy niz minimum (tylko w malenkich obrazkach) nie ma byc na sile powiekszany
    const minW = Math.min(min, kadr.w);
    const minH = Math.min(min, kadr.h);
    let { x, y, w, h } = kadr;
    if (sx === 1) {
      w = ogranicz(prawa + dx, kadr.x + minW, W) - kadr.x;
    } else if (sx === -1) {
      x = ogranicz(kadr.x + dx, 0, prawa - minW);
      w = prawa - x;
    }
    if (sy === 1) {
      h = ogranicz(dol + dy, kadr.y + minH, H) - kadr.y;
    } else if (sy === -1) {
      y = ogranicz(kadr.y + dy, 0, dol - minH);
      h = dol - y;
    }
    return wGranicach({ x, y, w, h }, W, H);
  }

  // Jaka szerokosc proponuje ruch wskaznika
  const zX = kadr.w + sx * dx;
  const zY = (kadr.h + sy * dy) * proporcje;
  let proponowana;
  if (sx !== 0 && sy !== 0) {
    proponowana = Math.abs(zX - kadr.w) >= Math.abs(zY - kadr.w) ? zX : zY;
  } else {
    proponowana = sx !== 0 ? zX : zY;
  }

  // Ile miejsca jest po stronie, w ktora kadr rosnie
  const maksW = sx === 1 ? W - kadr.x : sx === -1 ? prawa : W;
  const maksH = sy === 1 ? H - kadr.y : sy === -1 ? dol : H;
  const najwiecej = Math.min(maksW, maksH * proporcje);
  const najmniej = Math.min(Math.max(min, min * proporcje), najwiecej);
  const w = ogranicz(proponowana, najmniej, najwiecej);
  const h = w / proporcje;

  const srodekX = kadr.x + kadr.w / 2;
  const srodekY = kadr.y + kadr.h / 2;
  const x = sx === 1 ? kadr.x : sx === -1 ? prawa - w : ogranicz(srodekX - w / 2, 0, W - w);
  const y = sy === 1 ? kadr.y : sy === -1 ? dol - h : ogranicz(srodekY - h / 2, 0, H - h);
  return wGranicach({ x, y, w, h }, W, H);
}

/**
 * Kadr obraca sie razem z obrazem - zaznaczony fragment zostaje ten sam.
 * W, H to wymiary PRZED obrotem; kierunek +1 = w prawo.
 */
export function obrocKadr(kadr, W, H, kierunek) {
  if (kierunek > 0) {
    // punkt (x, y) przechodzi w (H - y, x)
    return { x: H - (kadr.y + kadr.h), y: kadr.x, w: kadr.h, h: kadr.w };
  }
  // punkt (x, y) przechodzi w (y, W - x)
  return { x: kadr.y, y: W - (kadr.x + kadr.w), w: kadr.h, h: kadr.w };
}

/**
 * Kadr o nowych proporcjach w miejscu starego: ten sam srodek i mniej wiecej
 * ta sama powierzchnia, ale nie wiekszy niz obraz. Po obrocie przy stalych
 * proporcjach i przy zmianie proporcji z listy.
 */
export function dopasujProporcje(kadr, proporcje, W, H) {
  if (!proporcje) {
    return wGranicach(kadr, W, H);
  }
  const pole = Math.max(kadr.w * kadr.h, 1);
  let w = Math.sqrt(pole * proporcje);
  let h = w / proporcje;
  const skala = Math.min(1, W / w, H / h);
  w *= skala;
  h *= skala;
  const min = minimalnyBok(W, H);
  if (Math.min(w, h) < min) {
    // Za maly kadr (np. waski pasek po zmianie z panoramy na pion) - powiekszamy do minimum
    const powieksz = Math.min(min / Math.min(w, h), W / w, H / h);
    w *= powieksz;
    h *= powieksz;
  }
  const srodekX = kadr.x + kadr.w / 2;
  const srodekY = kadr.y + kadr.h / 2;
  return wGranicach({ x: srodekX - w / 2, y: srodekY - h / 2, w, h }, W, H);
}

/**
 * Wybor proporcji z listy: najwiekszy kadr o tych proporcjach z srodkiem tam, gdzie byl dotychczasowy
 * (przesuniety do srodka, gdy przy krawedzi sie nie miesci). Dopasowanie "z ta sama powierzchnia"
 * zmniejszalo kadr przy kazdym przelaczeniu tam i z powrotem (1:1 -> 16:9 -> 1:1 dawalo 300 zamiast 400 px).
 */
export function najwiekszyKadr(kadr, proporcje, W, H) {
  const { w, h } = pelnyKadr(W, H, proporcje);
  const srodekX = kadr.x + kadr.w / 2;
  const srodekY = kadr.y + kadr.h / 2;
  return wGranicach({ x: srodekX - w / 2, y: srodekY - h / 2, w, h }, W, H);
}

/** Czy edycja cokolwiek zmienia (pol piksela tolerancji na zaokraglenia). */
export function bezZmian(kadr, obrot, W, H) {
  return obrot === 0
    && kadr.x < 0.5 && kadr.y < 0.5
    && Math.abs(kadr.w - W) < 1 && Math.abs(kadr.h - H) < 1;
}

/** Kadr w calych pikselach, ktory na pewno lezy w obrazie i ma co najmniej 1x1. */
export function wPikselach(kadr, W, H) {
  const x = ogranicz(Math.round(kadr.x), 0, Math.max(0, W - 1));
  const y = ogranicz(Math.round(kadr.y), 0, Math.max(0, H - 1));
  const w = ogranicz(Math.round(kadr.w), 1, W - x);
  const h = ogranicz(Math.round(kadr.h), 1, H - y);
  return { x, y, w, h };
}

/**
 * Przeksztalcenie plotna, ktore rysuje obraz zrodlowy (W0 x H0) obrocony o dany kat:
 * [a, b, c, d, e, f] dla setTransform/transform. Sprawdzone w testach na rogach obrazu.
 */
export function macierzObrotu(obrot, W0, H0) {
  switch (obrot) {
    case 90: return [0, 1, -1, 0, H0, 0];
    case 180: return [-1, 0, 0, -1, W0, H0];
    case 270: return [0, -1, 1, 0, 0, W0];
    default: return [1, 0, 0, 1, 0, 0];
  }
}
