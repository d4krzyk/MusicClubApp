import { Capacitor } from '@capacitor/core';
import { Geolocation } from '@capacitor/geolocation';
import { cityNear } from './lib/ics.js';

// "Moja lokalizacja": jednorazowy, przyblizony odczyt pozycji -> najblizsze miasto z listy. Wspolrzedne nie sa nigdzie
// zapisywane; zostaje tylko nazwa miasta (jak przy wpisaniu recznym).
// Wynik: { city } albo { error: 'odmowa' | 'brak' | 'daleko' }.
export async function miastoZLokalizacji() {
  let poz;
  try {
    if (Capacitor.isNativePlatform()) {
      const zgoda = await Geolocation.requestPermissions({ permissions: ['coarseLocation'] });
      if (zgoda.coarseLocation !== 'granted' && zgoda.location !== 'granted') return { error: 'odmowa' };
      poz = await Geolocation.getCurrentPosition({ enableHighAccuracy: false, timeout: 15000, maximumAge: 600000 });
    } else {
      if (!navigator.geolocation) return { error: 'brak' };
      poz = await new Promise((ok, nie) => navigator.geolocation.getCurrentPosition(ok, nie,
        { enableHighAccuracy: false, timeout: 15000, maximumAge: 600000 }));
    }
  } catch (e) {
    return { error: e?.code === 1 || /denied|permission/i.test(e?.message ?? '') ? 'odmowa' : 'brak' };
  }
  const city = cityNear(poz.coords.latitude, poz.coords.longitude, 50);
  return city ? { city } : { error: 'daleko' };
}
