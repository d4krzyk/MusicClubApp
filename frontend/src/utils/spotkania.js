/**
 * Spotkania w czacie: stan w czasie (przed / trwa / po / odwolane), domyslne godziny w formularzu, walidacja
 * i plik kalendarza (.ics). Czyste funkcje - testy w spotkania.test.js (node --test).
 */

export const PRZYPOMNIENIA = [0, 15, 30, 60, 120, 1440];
export const MAKS_MIEJSCE = 100;
export const MAKS_NOTATKA = 200;
/** Spotkanie trwa najwyzej dobe i zaczyna sie najpozniej za 60 dni - tak samo pilnuje serwer. */
export const MAKS_CZAS_MIN = 24 * 60;
export const MAKS_DNI_DO = 60;

/** Stan spotkania wzgledem chwili {@code teraz}: ODWOLANE, PRZED, TRWA albo PO. */
export function stanSpotkania(spotkanie, teraz = new Date()) {
  if (spotkanie.cancelled) {
    return 'ODWOLANE';
  }
  const od = new Date(spotkanie.startsAt).getTime();
  const doK = new Date(spotkanie.endsAt).getTime();
  const t = teraz.getTime();
  if (t < od) return 'PRZED';
  if (t < doK) return 'TRWA';
  return 'PO';
}

/** Ile minut do poczatku (zaokraglone w gore), gdy spotkanie jeszcze sie nie zaczelo; inaczej null. */
export function minutDo(spotkanie, teraz = new Date()) {
  const roznica = new Date(spotkanie.startsAt).getTime() - teraz.getTime();
  return roznica > 0 ? Math.ceil(roznica / 60000) : null;
}

/** Domyslne "od": najblizsza pelna polgodzina, co najmniej 30 min od teraz; "do" = godzina pozniej. */
export function domyslneGodziny(teraz = new Date()) {
  const od = new Date(teraz.getTime() + 30 * 60000);
  od.setSeconds(0, 0);
  const m = od.getMinutes();
  od.setMinutes(m === 0 || m === 30 ? m : (m < 30 ? 30 : 60));
  const doK = new Date(od.getTime() + 60 * 60000);
  return { od, do: doK };
}

/** Data i godzina z pol formularza (lokalny czas przegladarki) -> Date; null, gdy niepelne. */
export function zPol(data, godzina) {
  if (!data || !godzina) {
    return null;
  }
  const [r, mies, d] = data.split('-').map(Number);
  const [g, min] = godzina.split(':').map(Number);
  if ([r, mies, d, g, min].some((x) => !Number.isFinite(x))) {
    return null;
  }
  return new Date(r, mies - 1, d, g, min, 0, 0);
}

/** Date -> wartosci pol formularza w czasie lokalnym ("2026-10-10", "18:30"). */
export function doPol(data) {
  const p = (x) => String(x).padStart(2, '0');
  return {
    data: `${data.getFullYear()}-${p(data.getMonth() + 1)}-${p(data.getDate())}`,
    godzina: `${p(data.getHours())}:${p(data.getMinutes())}`,
  };
}

/**
 * Bledy formularza (klucze tlumaczen) - te same zasady co na serwerze. "Do" wczesniej niz "od" znaczy przejscie
 * przez polnoc (koncert 23:00 - 1:00): wtedy "do" jest nastepnego dnia.
 */
export function sprawdzSpotkanie({ miejsce, od, doK, notatka = '' }, teraz = new Date()) {
  const bledy = {};
  if (!miejsce || !miejsce.trim()) bledy.miejsce = 'meetings.errors.placeRequired';
  else if (miejsce.trim().length > MAKS_MIEJSCE) bledy.miejsce = 'meetings.errors.placeTooLong';
  if (notatka.length > MAKS_NOTATKA) bledy.notatka = 'meetings.errors.noteTooLong';
  if (!od) bledy.od = 'meetings.errors.startRequired';
  else if (od.getTime() < teraz.getTime() - 5 * 60000) bledy.od = 'meetings.errors.startInPast';
  else if (od.getTime() > teraz.getTime() + MAKS_DNI_DO * 86400000) bledy.od = 'meetings.errors.startTooFar';
  if (od && doK) {
    const min = (doK.getTime() - od.getTime()) / 60000;
    if (min <= 0) bledy.do = 'meetings.errors.endBeforeStart';
    else if (min > MAKS_CZAS_MIN) bledy.do = 'meetings.errors.tooLong';
  }
  return bledy;
}

/** "Do" przed "od" tego samego dnia = przejscie przez polnoc: "do" przesuwa sie o dobe. */
export function koniecPoPolnocy(od, doK) {
  if (od && doK && doK.getTime() <= od.getTime()) {
    return new Date(doK.getTime() + 86400000);
  }
  return doK;
}

/* ------------------------------------------------------------------------------------------------ */
/*  Kalendarz (.ics, RFC 5545)                                                                       */
/* ------------------------------------------------------------------------------------------------ */

function utc(data) {
  const d = new Date(data);
  const p = (x) => String(x).padStart(2, '0');
  return `${d.getUTCFullYear()}${p(d.getUTCMonth() + 1)}${p(d.getUTCDate())}T${p(d.getUTCHours())}${p(d.getUTCMinutes())}${p(d.getUTCSeconds())}Z`;
}

/** Tekst w polu kalendarza: ukosnik, przecinek, srednik i nowa linia musza byc poprzedzone ukosnikiem. */
export function icsTekst(s) {
  return String(s ?? '').replace(/\\/g, '\\\\').replace(/;/g, '\\;').replace(/,/g, '\\,').replace(/\r?\n/g, '\\n');
}

/** Linie dluzsze niz 75 bajtow lamie sie spacja na poczatku kolejnej (RFC 5545, 3.1). */
function zawin(linia) {
  const bajty = new TextEncoder();
  if (bajty.encode(linia).length <= 75) {
    return linia;
  }
  const wynik = [];
  let biezaca = '';
  for (const znak of linia) {
    if (bajty.encode(biezaca + znak).length > (wynik.length === 0 ? 75 : 74)) {
      wynik.push(biezaca);
      biezaca = znak;
    } else {
      biezaca += znak;
    }
  }
  wynik.push(biezaca);
  return wynik.join('\r\n ');
}

/** Plik .ics jednego spotkania (z alarmem, gdy jest przypomnienie). */
export function plikIcs(s, { tytul, teraz = new Date() } = {}) {
  const linie = [
    'BEGIN:VCALENDAR',
    'VERSION:2.0',
    'PRODID:-//MusicClub//Spotkania//PL',
    'CALSCALE:GREGORIAN',
    'BEGIN:VEVENT',
    `UID:spotkanie-${s.id}@musicclub`,
    `DTSTAMP:${utc(teraz)}`,
    `DTSTART:${utc(s.startsAt)}`,
    `DTEND:${utc(s.endsAt)}`,
    `SUMMARY:${icsTekst(tytul ?? s.place)}`,
    `LOCATION:${icsTekst(s.place)}`,
  ];
  if (Number.isFinite(s.latitude) && Number.isFinite(s.longitude)) {
    linie.push(`GEO:${s.latitude};${s.longitude}`);
  }
  if (s.note) {
    linie.push(`DESCRIPTION:${icsTekst(s.note)}`);
  }
  if (s.cancelled) {
    linie.push('STATUS:CANCELLED');
  }
  if (s.remindMinutes > 0) {
    linie.push('BEGIN:VALARM', 'ACTION:DISPLAY', `DESCRIPTION:${icsTekst(tytul ?? s.place)}`,
      `TRIGGER:-PT${s.remindMinutes}M`, 'END:VALARM');
  }
  linie.push('END:VEVENT', 'END:VCALENDAR');
  return `${linie.map(zawin).join('\r\n')}\r\n`;
}
