// Nazwa wykonawcy w postaci do porownywania: male litery, bez polskich znakow i interpunkcji, bez "the" na poczatku.
export function nameKey(name) {
  if (name == null) return '';
  const ascii = String(name)
    .normalize('NFD')
    .replace(/\p{M}/gu, '')
    .replace(/ł/g, 'l')
    .replace(/Ł/g, 'L')
    .toLowerCase()
    .replaceAll('&', ' and ');
  const key = ascii.replace(/[^a-z0-9]+/g, ' ').trim();
  return key.startsWith('the ') ? key.slice(4) : key;
}

// Czy w tekscie (np. nazwie wydarzenia) pada ta nazwa jako cale slowa. Krotkich nazw (< 4) nie szukamy tak.
export function mentions(textKey, key) {
  if (key.length < 4 || !textKey) return false;
  return (` ${textKey} `).includes(` ${key} `);
}

// Klucz miasta: bez polskich znakow, male litery; "warszawa" = "warsaw".
const TE_SAME = { warszawa: 'warsaw' };
export function cityKey(city) {
  if (city == null || !String(city).trim()) return null;
  const k = String(city).trim().normalize('NFD').replace(/\p{M}/gu, '').replace(/ł/g, 'l').replace(/Ł/g, 'L')
    .toLowerCase().replace(/\s+/g, ' ');
  return TE_SAME[k] ?? k;
}

// Co laczy terminy jednej serii: nazwa i miejsce.
export function seriesKey(name, venueId, venueName) {
  const nazwa = name.toLowerCase().replace(/\s+/g, ' ').trim();
  const miejsce = venueId ?? (venueName ? venueName.toLowerCase().trim() : '');
  return `${nazwa}|${miejsce}`.slice(0, 400);
}
