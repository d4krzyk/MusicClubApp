import { Capacitor } from '@capacitor/core';
import { BackgroundRunner } from '@capacitor/background-runner';
import { stanDoTla } from './lib/powiadomienia.js';

// Most do pracy w tle (src/runner/background.js): aplikacja przekazuje stan, prosi o zgode, wysyla probe.
// Tylko w aplikacji na Androida - w przegladarce nie ma pracy w tle.
const LABEL = 'pl.musicclub.solo.koncerty';
export const dostepne = () => Capacitor.isNativePlatform();

const wyslij = (event, details = {}) => BackgroundRunner.dispatchEvent({ label: LABEL, event, details });

export async function zgoda() {
  const s = await BackgroundRunner.requestPermissions({ apis: ['notifications'] });
  return s.notifications === 'granted';
}

export const probne = () => wyslij('test');
export const sprawdzTeraz = () => wyslij('sprawdz');
export const ostatnie = () => wyslij('info');

export async function przekazStan(arg) {
  if (!dostepne()) return;
  try { await wyslij('stan', stanDoTla(arg)); } catch { /* praca w tle niedostepna - aplikacja dziala dalej */ }
}
