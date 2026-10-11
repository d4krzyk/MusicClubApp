// Twoj udzial w wydarzeniu: "Biore udzial" (GOING), "Zainteresowany" (INTERESTED) albo nic.
// Zapis w ustawieniach: udzial[id] = GOING / INTERESTED / NONE (NONE = swiadomie zdjete z wydarzenia z kalendarza),
// saved[id] = kopia wydarzenia, zeby "Moje" pokazywalo je takze, gdy zniknie ze zrodla.
// Wydarzenia z twojego kalendarza (Facebook) maja udzial z kalendarza: "Wezme udzial" albo "Zainteresowany".

export const GOING = 'GOING';
export const INTERESTED = 'INTERESTED';

const ids = (e) => [e.id, ...(e.mergedIds ?? [])];

export function statusOf(e, s) {
  for (const id of ids(e)) {
    const o = s.udzial?.[id];
    if (o) return o === 'NONE' ? null : o;
  }
  if (ids(e).some((id) => s.saved?.[id])) return INTERESTED;
  if (e.source === 'ics' || e.inCalendar) return e.partstat ?? INTERESTED;
  return null;
}

// Zmiana udzialu -> fragment ustawien do zapisania. status null = zdejmij.
export function setStatus(s, e, status) {
  const udzial = { ...(s.udzial ?? {}) };
  const saved = { ...(s.saved ?? {}) };
  for (const id of ids(e)) { delete udzial[id]; delete saved[id]; }
  const zKalendarza = e.source === 'ics' || e.inCalendar;
  if (status) {
    udzial[e.id] = status;
    saved[e.id] = e;
  } else if (zKalendarza) {
    udzial[e.id] = 'NONE';
  }
  return { udzial, saved };
}

// Lista do zakladki "Moje": wszystkie wydarzenia z udzialem (takze zapisane kopie, ktorych juz nie ma w zrodlach).
export function mine(events, s, filtr = 'ALL') {
  const wynik = [];
  const widziane = new Set();
  for (const e of events) {
    const st = statusOf(e, s);
    if (!st) continue;
    ids(e).forEach((id) => widziane.add(id));
    if (filtr === 'ALL' || filtr === st) wynik.push({ ...e, udzial: st });
  }
  for (const [id, kopia] of Object.entries(s.saved ?? {})) {
    if (widziane.has(id)) continue;
    const st = s.udzial?.[id] && s.udzial[id] !== 'NONE' ? s.udzial[id] : INTERESTED;
    if (filtr === 'ALL' || filtr === st) wynik.push({ ...kopia, udzial: st });
  }
  return wynik;
}
