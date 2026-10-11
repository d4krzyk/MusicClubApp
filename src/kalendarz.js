import { Capacitor, registerPlugin } from '@capacitor/core';
import { span, locationOf, detailsOf, googleCalendarUrl, ics } from './lib/calendar.js';
import { saveFile } from './saveFile.js';

// Na Androidzie: wtyczka KalendarzPlugin.java otwiera Kalendarz Google z wypelnionym wydarzeniem.
const Kalendarz = registerPlugin('Kalendarz');

export const naAndroidzie = () => Capacitor.isNativePlatform() && Capacitor.getPlatform() === 'android';

export async function dodajNatywnie(e, chooserTitle) {
  const s = span(e);
  await Kalendarz.dodaj({ title: e.name, location: locationOf(e), description: detailsOf(e, 1500), begin: s.begin, end: s.end,
    allDay: s.allDay, chooserTitle });
}

export function otworzGoogle(e) {
  window.open(googleCalendarUrl(e), '_blank', 'noopener,noreferrer');
}

export function pobierzIcs(e) {
  const nazwa = `${e.name.replace(/[^\p{L}\p{N}]+/gu, '-').slice(0, 40)}.ics`;
  return saveFile(nazwa, 'text/calendar', ics(e));
}
