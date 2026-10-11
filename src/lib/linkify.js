// Adresy w zwyklym tekscie (opisy z Facebooka, Ticketmastera) -> czesci do pokazania jako odnosniki.
// Zwraca liste { text } i { url, label }; zadnego HTML-a - komponent sklada z tego elementy Reacta.
const ADRES = /https?:\/\/[^\s<>"'`]+/giu;
const KONCOWKA = /[.,;:!?…'"»”)\]]+$/u;

// Krotki napis zamiast calego adresu: bez "https://www.", najwyzej ~38 znakow.
export function shortUrl(url, max = 38) {
  const bez = url.replace(/^https?:\/\/(www\.)?/i, '').replace(/\/$/, '');
  return bez.length > max ? `${bez.slice(0, max - 1)}…` : bez;
}

export function linkify(tekst) {
  if (!tekst) return [];
  const wynik = [];
  let ostatni = 0;
  for (const m of tekst.matchAll(ADRES)) {
    let url = m[0];
    // kropka albo nawias po adresie to interpunkcja zdania - chyba ze nawias otwiera sie w samym adresie
    let ogon = url.match(KONCOWKA)?.[0] ?? '';
    if (ogon.startsWith(')') && (url.match(/\(/g) ?? []).length > (url.slice(0, -ogon.length).match(/\)/g) ?? []).length) ogon = ogon.slice(1);
    if (ogon) url = url.slice(0, -ogon.length);
    if (m.index > ostatni) wynik.push({ text: tekst.slice(ostatni, m.index) });
    wynik.push({ url, label: shortUrl(url) });
    ostatni = m.index + url.length;
  }
  if (ostatni < tekst.length) wynik.push({ text: tekst.slice(ostatni) });
  return wynik;
}
