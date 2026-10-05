/** Linki w tresci wiadomosci: podzial tekstu na zwykly tekst i adresy http(s) oraz skracanie adresu do pokazania. */

const LINK = /https?:\/\/[^\s<>"']+/gi;

/** Znaki interpunkcji, ktore zwykle koncza zdanie, a nie adres. */
const KONCOWKA = /[.,!?;:)\]}'"»”]+$/;

const MAKS_DLUGOSC = 42;

export function linkiWTekscie(tekst) {
  const czesci = [];
  let ostatni = 0;
  for (const trafienie of tekst.matchAll(LINK)) {
    let adres = trafienie[0];
    const koncowka = adres.match(KONCOWKA);
    // nawias zamykajacy zostaje, gdy adres ma tez otwierajacy (np. Wikipedia)
    if (koncowka && !(koncowka[0] === ')' && adres.includes('('))) {
      adres = adres.slice(0, adres.length - koncowka[0].length);
    }
    if (trafienie.index > ostatni) {
      czesci.push({ tekst: tekst.slice(ostatni, trafienie.index) });
    }
    czesci.push({ link: adres });
    ostatni = trafienie.index + adres.length;
  }
  if (ostatni < tekst.length) {
    czesci.push({ tekst: tekst.slice(ostatni) });
  }
  return czesci;
}

export function skrocAdres(adres) {
  const bez = adres.replace(/^https?:\/\//i, '').replace(/^www\./i, '');
  return bez.length > MAKS_DLUGOSC ? `${bez.slice(0, MAKS_DLUGOSC - 1)}…` : bez;
}
