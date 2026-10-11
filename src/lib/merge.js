import { nameKey, mentions } from './nameKeys.js';
import { distanceKm } from './location.js';

// Jeden koncert = jedna karta, choc wiemy o nim z kilku zrodel (Ticketmaster, Bandsintown, twoj kalendarz).
// Port EventMerger z wersji serwerowej: ten sam dzien i kraj, to samo miejsce (sala do 500 m albo ta sama nazwa
// sali w tym samym miescie) i ten sam wykonawca albo nazwa; dwie znane godziny rozne o > 3 h = dwa koncerty.

const OGOLNE = new Set(['klub', 'club', 'hala', 'sala', 'centrum', 'center', 'centre', 'live', 'music', 'muzyczny',
  'koncertowa', 'venue', 'the']);

const minuty = (t) => { const [h, m] = t.split(':').map(Number); return h * 60 + m; };

function slowaSali(n) {
  const k = nameKey(n);
  return k ? new Set(k.split(' ').filter((s) => !OGOLNE.has(s))) : new Set();
}

export function sameVenue(a, b) {
  const x = slowaSali(a);
  const y = slowaSali(b);
  if (!x.size || !y.size) return false;
  const [mniejszy, wiekszy] = x.size <= y.size ? [x, y] : [y, x];
  return [...mniejszy].every((s) => wiekszy.has(s)) && [...mniejszy].some((s) => s.length >= 4);
}

// 0 = to nie ten koncert; wyzej = pewniej (sala po wspolrzednych 3, po nazwie 3, wykonawca 3, nazwa 2, godzina 1).
export function score(e, x) {
  if (e.date !== x.date) return 0;
  if (e.country && x.country && e.country !== x.country) return 0;
  let godzina = false;
  if (e.time && x.time) {
    const r = Math.abs(minuty(e.time) - minuty(x.time));
    if (r > 180) return 0;
    godzina = r <= 60;
  }
  const km = e.lat != null && e.lon != null && x.lat != null && x.lon != null ? distanceKm(e.lat, e.lon, x.lat, x.lon) : null;
  const salaWsp = km != null && km <= 0.5;
  const miasto = (e.cityKey && e.cityKey === x.cityKey) || (km != null && km <= 30);
  // Wydarzenie z Facebooka czesto nie ma miasta ("Syrena, ul Wilcza 30") - wtedy miasto nie moze przeszkodzic,
  // ale dwa ROZNE znane miasta tak.
  const bezMiasta = (!e.cityKey && (e.lat == null || e.lon == null)) || (!x.cityKey && (x.lat == null || x.lon == null));
  const salaNazwa = (!!miasto || bezMiasta) && sameVenue(e.venueName, x.venueName);

  const nE = nameKey(e.name);
  const nX = nameKey(x.name);
  const sklad = (z) => [...(z.performers ?? []).map((p) => p.name), ...(z.candidates ?? [])].map(nameKey).filter(Boolean);
  const skladE = new Set(sklad(e));
  const skladX = sklad(x);
  const wykonawca = skladX.some((k) => skladE.has(k) || mentions(nE, k)) || [...skladE].some((k) => mentions(nX, k));
  const nazwa = !!nE && (nE === nX || (nE.length >= 5 && mentions(nX, nE)) || (nX.length >= 5 && mentions(nE, nX)));
  if (!wykonawca && !nazwa) return 0;
  // Bez pewnej sali: ten sam wykonawca o tej samej godzinie (do 1 h) w tym samym albo nieznanym miescie - nikt nie gra
  // w dwoch miejscach naraz.
  if (!salaWsp && !salaNazwa && !(wykonawca && godzina && (miasto || bezMiasta))) return 0;
  return (salaWsp ? 3 : 0) + (salaNazwa ? 3 : 0) + (wykonawca ? 3 : 0) + (nazwa ? 2 : 0) + (godzina ? 1 : 0);
}

const POLA = ['time', 'startUtc', 'timezone', 'venueName', 'address', 'ticketUrl', 'imageUrl', 'thumbUrl',
  'city', 'cityKey', 'country', 'sourceUrl'];
const KROTKI_OPIS = 200;

// Dopisuje zrodlo do wydarzenia i uzupelnia braki - niczego nie nadpisuje.
function wchlon(cel, x) {
  const wynik = { ...cel, sources: [...(cel.sources ?? []), ...(x.sources ?? [])] };
  for (const p of POLA) {
    if ((wynik[p] == null || wynik[p] === '') && x[p] != null) wynik[p] = x[p];
  }
  if ((cel.lat == null || cel.lon == null) && x.lat != null && x.lon != null) { wynik.lat = x.lat; wynik.lon = x.lon; }
  if (!wynik.performers.length && x.performers.length) wynik.performers = x.performers;
  // Opis: pusty albo krotki (Ticketmaster po wycieciu formulek) zastepuje dluzszy - z Facebooka bywa duzo lepszy.
  const a = cel.description ?? '';
  const b = x.description ?? '';
  if (b && (!a || (a.length < KROTKI_OPIS && b.length > a.length))) wynik.description = b;
  const gat = [...new Set([...(cel.genreTags ?? []), ...(x.genreTags ?? [])])];
  if (gat.length) wynik.genreTags = gat;
  const gatT = [...new Set([...(cel.textGenres ?? []), ...(x.textGenres ?? [])])];
  if (gatT.length) wynik.textGenres = gatT;
  if (x.source === 'ics') { wynik.inCalendar = true; if (!wynik.partstat && x.partstat) wynik.partstat = x.partstat; }
  wynik.mergedIds = [...(cel.mergedIds ?? []), x.id];
  return wynik;
}

// primary: wydarzenia glownego zrodla (Ticketmaster), extras: reszta w kolejnosci waznosci (Bandsintown, kalendarze).
// Zwraca jedna liste bez duplikatow. Dopasowanie liczone tylko w obrebie dnia - kilka tysiecy wydarzen to ulamek sekundy.
export function mergeAll(primary, ...extras) {
  const wynik = [];
  const poDniu = new Map();
  const dodaj = (e, i) => { if (!poDniu.has(e.date)) poDniu.set(e.date, []); poDniu.get(e.date).push(i); };
  // Ticketmaster bywa sam ze soba zduplikowany (np. bilety i "pakiet VIP" na ten sam koncert jako dwa wydarzenia):
  // to samo miejsce, dzien i godzina oraz ten sam wykonawca - jedna karta. Terminy serii (inny dzien) zostaja osobno.
  for (const p of primary) {
    const e = { ...p, sources: p.sources ?? [{ kind: 'TICKETMASTER', name: 'Ticketmaster', url: p.ticketUrl ?? null }] };
    const i = (poDniu.get(e.date) ?? []).find((j) => wynik[j].time && e.time && wynik[j].time === e.time && score(wynik[j], e) >= 7);
    if (i != null) wynik[i] = wchlon(wynik[i], e);
    else { wynik.push(e); dodaj(e, wynik.length - 1); }
  }
  for (const lista of extras) {
    for (const x of lista) {
      let najlepszy = -1;
      let punkty = 0;
      for (const i of poDniu.get(x.date) ?? []) {
        const s = score(wynik[i], x);
        if (s > punkty) { punkty = s; najlepszy = i; }
      }
      if (najlepszy >= 0) wynik[najlepszy] = wchlon(wynik[najlepszy], x);
      else { wynik.push({ ...x }); dodaj(x, wynik.length - 1); }
    }
  }
  return wynik;
}
