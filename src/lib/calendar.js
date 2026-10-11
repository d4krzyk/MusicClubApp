// "Dodaj do kalendarza": link do Google Calendar (strona) i dane dla kalendarza Androida (Intent ACTION_INSERT).

const DOMYSLNIE_GODZIN = 3; // koncert bez godziny konca - zakladamy 3 h
const pad = (n) => String(n).padStart(2, '0');
const zwarty = (iso) => iso.replace(/[-:]/g, '').replace(/\.\d{3}/, '');
const nastepnyDzien = (d) => {
  const x = new Date(`${d}T00:00:00Z`);
  x.setUTCDate(x.getUTCDate() + 1);
  return x.toISOString().slice(0, 10);
};

export function locationOf(e) {
  return [e.venueName, e.address, e.city].filter(Boolean).join(', ');
}

export function detailsOf(e, max = 700) {
  const czesci = [];
  if (e.description) czesci.push(e.description.length > max ? `${e.description.slice(0, max).trimEnd()}…` : e.description);
  const link = e.ticketUrl ?? e.sourceUrl;
  if (link) czesci.push(link);
  return czesci.join('\n\n');
}

// Chwile poczatku i konca (ms) albo wydarzenie calodniowe. Bez strefy - czas lokalny urzadzenia.
export function span(e) {
  if (!e.time && !e.startUtc) {
    return { allDay: true, begin: Date.parse(`${e.date}T00:00:00Z`), end: Date.parse(`${nastepnyDzien(e.date)}T00:00:00Z`) };
  }
  let begin;
  if (e.startUtc) begin = Date.parse(e.startUtc);
  else {
    const [y, m, d] = e.date.split('-').map(Number);
    const [h, mi] = e.time.split(':').map(Number);
    begin = new Date(y, m - 1, d, h, mi).getTime();
  }
  const end = e.endUtc && Date.parse(e.endUtc) > begin ? Date.parse(e.endUtc) : begin + DOMYSLNIE_GODZIN * 3600e3;
  return { allDay: false, begin, end };
}

export function googleCalendarUrl(e) {
  const s = span(e);
  let dates;
  if (s.allDay) dates = `${e.date.replaceAll('-', '')}/${nastepnyDzien(e.date).replaceAll('-', '')}`;
  else dates = `${zwarty(new Date(s.begin).toISOString())}/${zwarty(new Date(s.end).toISOString())}`;
  const q = new URLSearchParams({ action: 'TEMPLATE', text: e.name, dates, location: locationOf(e), details: detailsOf(e) });
  return `https://calendar.google.com/calendar/render?${q}`;
}

// Plik .ics dla jednego wydarzenia (inne kalendarze, iPhone).
export function ics(e, teraz = new Date()) {
  const esc = (t) => String(t ?? '').replace(/([\;,])/g, '\\$1').replace(/\n/g, '\\n');
  const s = span(e);
  const linie = ['BEGIN:VCALENDAR', 'VERSION:2.0', 'PRODID:-//MusicClub//PL', 'BEGIN:VEVENT',
    `UID:${String(e.id).replace(/[^\w.:@-]/g, '_')}@musicclub`, `DTSTAMP:${zwarty(teraz.toISOString())}`];
  if (s.allDay) linie.push(`DTSTART;VALUE=DATE:${e.date.replaceAll('-', '')}`, `DTEND;VALUE=DATE:${nastepnyDzien(e.date).replaceAll('-', '')}`);
  else linie.push(`DTSTART:${zwarty(new Date(s.begin).toISOString())}`, `DTEND:${zwarty(new Date(s.end).toISOString())}`);
  linie.push(`SUMMARY:${esc(e.name)}`);
  const loc = locationOf(e);
  if (loc) linie.push(`LOCATION:${esc(loc)}`);
  const det = detailsOf(e);
  if (det) linie.push(`DESCRIPTION:${esc(det)}`);
  if (e.lat != null && e.lon != null) linie.push(`GEO:${e.lat};${e.lon}`);
  const link = e.ticketUrl ?? e.sourceUrl;
  if (link) linie.push(`URL:${link}`);
  linie.push('END:VEVENT', 'END:VCALENDAR');
  return linie.join('\r\n');
}

export { pad };
