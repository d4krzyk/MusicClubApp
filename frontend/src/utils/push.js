import * as api from '../api/push';

/*
 * Powiadomienia push po stronie przegladarki.
 *
 * Subskrypcje trzyma przegladarka (pushManager w service workerze), a serwer
 * dostaje jej adres i klucze. Service worker jest tylko w zbudowanej wersji
 * (npm run build / preview) - w "npm run dev" push jest niedostepny.
 */

export function obslugiwane() {
  return typeof window !== 'undefined'
    && 'serviceWorker' in navigator && 'PushManager' in window && 'Notification' in window;
}

async function rejestracja() {
  if (!obslugiwane()) {
    return null;
  }
  return (await navigator.serviceWorker.getRegistration()) ?? null;
}

/** Klucz serwera (base64url) na bajty, jak chce pushManager.subscribe. */
function naBajty(tekst) {
  const b64 = tekst.replace(/-/g, '+').replace(/_/g, '/').padEnd(Math.ceil(tekst.length / 4) * 4, '=');
  return Uint8Array.from(atob(b64), (z) => z.charCodeAt(0));
}

/** Czy subskrypcja powstala z tym kluczem serwera (po zmianie kluczy stara nic nie dostanie). */
function tenSamKlucz(subskrypcja, klucz) {
  const obecny = subskrypcja.options?.applicationServerKey;
  if (!obecny) {
    return true;
  }
  const a = new Uint8Array(obecny);
  const b = naBajty(klucz);
  return a.length === b.length && a.every((x, i) => x === b[i]);
}

async function zapisz(reg, klucz, jezyk) {
  let subskrypcja = await reg.pushManager.getSubscription();
  if (subskrypcja && !tenSamKlucz(subskrypcja, klucz)) {
    await subskrypcja.unsubscribe();
    subskrypcja = null;
  }
  if (!subskrypcja) {
    subskrypcja = await reg.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: naBajty(klucz) });
  }
  await api.zapiszUrzadzenie(subskrypcja, jezyk);
}

/** 'nieobslugiwane' | 'zablokowane' | 'wlaczone' | 'wylaczone' - dla tego urzadzenia. */
export async function stan() {
  const reg = await rejestracja();
  if (!reg) {
    return 'nieobslugiwane';
  }
  if (Notification.permission === 'denied') {
    return 'zablokowane';
  }
  const subskrypcja = await reg.pushManager.getSubscription();
  return subskrypcja && Notification.permission === 'granted' ? 'wlaczone' : 'wylaczone';
}

/** Pyta o zgode i zapisuje urzadzenie. false = ktos odmowil. */
export async function wlacz(klucz, jezyk) {
  const reg = await rejestracja();
  if (!reg) {
    return false;
  }
  if (await Notification.requestPermission() !== 'granted') {
    return false;
  }
  await zapisz(reg, klucz, jezyk);
  return true;
}

/** Wypisuje to urzadzenie - na serwerze i w przegladarce. */
export async function wylacz() {
  const reg = await rejestracja();
  const subskrypcja = reg && await reg.pushManager.getSubscription();
  if (!subskrypcja) {
    return;
  }
  try {
    await api.wypiszUrzadzenie(subskrypcja.endpoint);
  } finally {
    await subskrypcja.unsubscribe();
  }
}

/**
 * Przy kazdym otwarciu aplikacji: jesli to urzadzenie ma wlaczone
 * powiadomienia, zapisuje je na serwerze jeszcze raz. Po zmianie hasla albo
 * "wyloguj z innych urzadzen" serwer zapomina wszystkie urzadzenia - to
 * wraca, a obce (np. zgubiony telefon) juz nie.
 */
export async function synchronizuj(jezyk) {
  if (!obslugiwane() || Notification.permission !== 'granted') {
    return;
  }
  const reg = await rejestracja();
  if (!reg || !(await reg.pushManager.getSubscription())) {
    return;
  }
  const ustawienia = await api.ustawienia();
  if (ustawienia.available) {
    await zapisz(reg, ustawienia.publicKey, jezyk);
  }
}
