import { plain } from './plainText.js';

// Formulki sprzedazowe i prawne z opisow Ticketmastera - to nie jest opis koncertu. Wycinamy cale zdania z formulkami;
// same formulki = brak opisu.
const FORMULKA = new RegExp([
  'service fee', 'booking fee', 'handling fee', 'transaction fee', 'additional fees?',
  'conditions agreed with the organi[sz]er', 'prices? (include|includes|including) vat',
  'opłat\\p{L}* (serwisow|manipulacyjn|transakcyjn|dodatkow)', 'do ceny biletu', 'cen\\p{L}* biletu (zawiera|obejmuje)',
  'seating (chart|plan|map)', 'general layout of the venue', '(buy|purchase|book) accessible tickets?',
  'plan (sali|miejsc|obiektu|widowni)', 'charakter poglądowy',
  'bilet\\p{L}* dla os(ób|oby) (z niepełnosprawn|niepełnosprawn)',
  'ticket limit', '(limit|maximum) (of )?\\d+ tickets?', 'limit biletów', 'maks(ymalnie|\\.) \\d+ bilet',
  'non-?refundable', 'no refunds?', 'nie podlegaj\\p{L}* zwrotowi',
  'ticketmaster app', 'mobile tickets?', 'print[- ]at[- ]home', 'aplikacji ticketmaster', 'bilet\\p{L}* mobiln',
  'registered office', 'commercial register', 'court register', 'vat (no|number|id)\\b', 'share capital',
  '\\b(krs|nip|regon)\\b\\W{0,3}\\d', 'z siedzibą', 'kapitał\\p{L}* zakładow', 'rejestr\\p{L}* (sądow|przedsiębiorców)',
].join('|'), 'iu');

const KONIEC = /[.!?…]\s+(?=["„«(]?\p{Lu})/gu;
const SKROTY = new Set(['ul', 'al', 'pl', 'os', 'nr', 'sp', 'św', 'ks', 'dr', 'prof', 'inż', 'mgr', 'godz', 'ok', 'tel',
  'np', 'tj', 'tzw', 'itd', 'itp', 'zł', 'no', 'st', 'mr', 'mrs', 'ms', 'jr', 'sr', 'vs', 'etc', 'ca', 'approx', 'ltd',
  'inc', 'co', 'nip', 'krs']);

// Czy przed znakiem na pozycji i stoi skrot albo pojedyncza litera ("Sp", "ul", "o" z "o.o.").
function skrotPrzed(linia, i) {
  if (linia[i] !== '.') return false;
  let j = i;
  while (j > 0 && /\p{L}/u.test(linia[j - 1])) j--;
  const slowo = linia.slice(j, i).toLowerCase();
  return slowo.length === 1 || SKROTY.has(slowo);
}

export function sentences(linia) {
  const wynik = [];
  let poczatek = 0;
  for (const m of linia.matchAll(KONIEC)) {
    if (skrotPrzed(linia, m.index)) continue;
    wynik.push(linia.slice(poczatek, m.index + 1).trim());
    poczatek = m.index + m[0].length;
  }
  const reszta = linia.slice(poczatek).trim();
  if (reszta) wynik.push(reszta);
  return wynik;
}

// Tekst bez formulek, z zachowanymi akapitami; null, gdy nic nie zostalo.
export function strip(tekst) {
  const czysty = plain(tekst);
  if (czysty == null) return null;
  const linie = czysty.split('\n').map((l) => sentences(l).filter((z) => !FORMULKA.test(z)).join(' '));
  return plain(linie.join('\n'));
}
