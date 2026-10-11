// Daty i liczby pokazywane uzytkownikowi. Data wydarzenia to dzien kalendarzowy w miejscu koncertu, bez strefy.
export function dateParts(iso, lang) {
  const [r, m, d] = iso.split('-').map(Number);
  const data = new Date(Date.UTC(r, m - 1, d));
  const opt = { timeZone: 'UTC' };
  const loc = lang === 'en' ? 'en-GB' : 'pl-PL';
  return {
    day: String(d),
    month: data.toLocaleDateString(loc, { ...opt, month: 'short' }).replace('.', ''),
    weekday: data.toLocaleDateString(loc, { ...opt, weekday: 'long' }),
    full: data.toLocaleDateString(loc, { ...opt, day: 'numeric', month: 'long', year: 'numeric' }),
  };
}

export function today(now = new Date()) {
  // "dzis" wg czasu lokalnego urzadzenia
  const p = (n) => String(n).padStart(2, '0');
  return `${now.getFullYear()}-${p(now.getMonth() + 1)}-${p(now.getDate())}`;
}

export function money(min, max, cur, lang) {
  const f = (v) => v.toLocaleString(lang === 'en' ? 'en-GB' : 'pl-PL', { maximumFractionDigits: 2 });
  const zakres = max != null && max !== min ? `${f(min ?? max)}–${f(max)}` : f(min ?? max);
  return `${zakres} ${cur ?? ''}`.trim();
}

export const KRAJE = ['PL', 'DE', 'CZ', 'SK', 'GB', 'IE', 'FR', 'ES', 'IT', 'NL', 'BE', 'AT', 'CH', 'SE', 'NO', 'DK', 'FI', 'US', 'CA'];
