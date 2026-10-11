import { cityKey, nameKey } from './nameKeys.js';
import { cityByName } from './location.js';

// Podpowiedzi miejsc i adresow przy poprawianiu wydarzenia: Photon (komoot) - geokoder na danych OpenStreetMap, zrobiony
// do podpowiadania w trakcie pisania, bez klucza. Fraza idzie do photon.komoot.io (polityka prywatnosci to mowi w README).
// Uczciwe uzycie: najwyzej jedno zapytanie po przerwie w pisaniu i od 3 liter.

export const API = 'https://photon.komoot.io/api/';

const enc = encodeURIComponent;

export function photonUrl(fraza, miasto, limit = 8) {
  const c = miasto ? cityByName(miasto) : null;
  // \b nie lapie polskich liter ("Łódź"), wiec porownujemy klucze bez znakow diakrytycznych, jako cale slowa
  const juzJest = miasto && ` ${nameKey(fraza)} `.includes(` ${nameKey(miasto)} `);
  const q = miasto && !juzJest ? `${fraza}, ${miasto}` : fraza;
  // bez "lang": publiczny Photon zna tylko kilka jezykow, a domyslnie oddaje nazwy lokalne (po polsku)
  return `${API}?q=${enc(q)}&limit=${limit}${c ? `&lat=${c.lat}&lon=${c.lon}` : ''}`;
}

// Odpowiedz (GeoJSON) -> podpowiedzi. Z tego samego miasta najpierw; bez kraju PL odrzucamy, gdy szukamy w polskim miescie.
export function parsePhoton(json, miasto) {
  const klucz = miasto ? cityKey(miasto) : null;
  const wynik = [];
  const widziane = new Set();
  for (const f of json?.features ?? []) {
    const p = f.properties ?? {};
    const [lon, lat] = f.geometry?.coordinates ?? [];
    const ulica = [p.street, p.housenumber].filter(Boolean).join(' ') || null;
    const miejscowosc = p.city ?? p.town ?? p.village ?? p.locality ?? null;
    const poi = p.osm_key && !['place', 'highway', 'building', 'boundary'].includes(p.osm_key) && p.name;
    const nazwa = poi ? p.name : null;
    const adres = ulica ?? (p.osm_key === 'highway' ? p.name : null);
    if (!nazwa && !adres) continue;
    const s = {
      name: nazwa, address: adres, city: miejscowosc, postcode: p.postcode ?? null,
      lat: Number.isFinite(lat) ? lat : null, lon: Number.isFinite(lon) ? lon : null,
      sameCity: !!klucz && cityKey(miejscowosc) === klucz,
      country: p.countrycode ?? null,
    };
    s.label = [nazwa, adres, miejscowosc].filter(Boolean).join(', ');
    if (widziane.has(s.label)) continue;
    widziane.add(s.label);
    wynik.push(s);
  }
  const zMiasta = wynik.filter((s) => s.sameCity);
  return (zMiasta.length ? zMiasta : wynik.filter((s) => !klucz || !s.country || s.country === 'PL')).slice(0, 6);
}

export async function suggestPlaces(fraza, miasto, { fetchFn = fetch, signal } = {}) {
  if (!fraza || fraza.trim().length < 3) return [];
  const odp = await fetchFn(photonUrl(fraza.trim(), miasto), { signal });
  if (!odp.ok) return [];
  return parsePhoton(await odp.json(), miasto);
}
