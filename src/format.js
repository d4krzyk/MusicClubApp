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

// Plik kalendarza (.ics) dla wydarzenia: pelnodniowy, gdy nie znamy godziny, inaczej "plywajacy" czas lokalny miejsca.
export function ics(e) {
  const d = e.date.replaceAll('-', '');
  const esc = (t) => String(t ?? '').replace(/([\;,])/g, '\\$1').replace(/\n/g, '\\n');
  const nast = (iso) => { const x = new Date(`${iso}T00:00:00Z`); x.setUTCDate(x.getUTCDate() + 1); return x.toISOString().slice(0, 10).replaceAll('-', ''); };
  const start = e.time ? `DTSTART:${d}T${e.time.replace(':', '')}00` : `DTSTART;VALUE=DATE:${d}`;
  const koniec = e.time ? '' : `DTEND;VALUE=DATE:${nast(e.date)}\r\n`;
  const miejsce = [e.venueName, e.address, e.city].filter(Boolean).join(', ');
  return ['BEGIN:VCALENDAR', 'VERSION:2.0', 'PRODID:-//MusicClub//PL', 'BEGIN:VEVENT', `UID:${e.id}@musicclub`,
    `DTSTAMP:${new Date().toISOString().replace(/[-:]/g, '').slice(0, 15)}Z`, start, koniec.trim(),
    `SUMMARY:${esc(e.name)}`, `LOCATION:${esc(miejsce)}`, e.ticketUrl ? `URL:${e.ticketUrl}` : '', 'END:VEVENT', 'END:VCALENDAR']
    .filter(Boolean).join('\r\n');
}

export const KRAJE = ['PL', 'DE', 'CZ', 'SK', 'GB', 'IE', 'FR', 'ES', 'IT', 'NL', 'BE', 'AT', 'CH', 'SE', 'NO', 'DK', 'FI', 'US', 'CA'];
