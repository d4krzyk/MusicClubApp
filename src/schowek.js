import { Capacitor } from '@capacitor/core';
import { Clipboard } from '@capacitor/clipboard';

// Tekst ze schowka. Na Androidzie przez wtyczke (WebView nie daje navigator.clipboard), w przegladarce przez API schowka.
// null = nie udalo sie (brak zgody, stara przegladarka) - wtedy uzytkownik wkleja recznie.
export async function czytajSchowek() {
  try {
    if (Capacitor.isNativePlatform()) return (await Clipboard.read()).value ?? null;
    return await navigator.clipboard.readText();
  } catch {
    return null;
  }
}
