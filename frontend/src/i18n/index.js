import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import pl from './pl.json';
import en from './en.json';
import { ustawJezykZapytan } from '../api/client';

export const JEZYKI = ['pl', 'en'];
const KLUCZ_W_PAMIECI = 'musicclub.lang';

/**
 * Ustala jezyk przy starcie aplikacji, w kolejnosci:
 *   1. wybor zapamietany wczesniej przez uzytkownika,
 *   2. jezyk ustawiony w przegladarce,
 *   3. polski.
 */
function jezykPoczatkowy() {
  const zapamietany = localStorage.getItem(KLUCZ_W_PAMIECI);
  if (JEZYKI.includes(zapamietany)) {
    return zapamietany;
  }
  const zPrzegladarki = navigator.language?.slice(0, 2);
  return JEZYKI.includes(zPrzegladarki) ? zPrzegladarki : 'pl';
}

const jezyk = jezykPoczatkowy();

i18n.use(initReactI18next).init({
  resources: {
    pl: { translation: pl },
    en: { translation: en },
  },
  lng: jezyk,
  fallbackLng: 'pl',
  interpolation: {
    // React sam zabezpiecza tekst przed wstrzyknieciem HTML-a
    escapeValue: false,
  },
});

// Ten sam jezyk wysylamy do backendu, zeby bledy walidacji tez byly przetlumaczone
ustawJezykZapytan(jezyk);

/**
 * Przelacza jezyk calej aplikacji - napisy w interfejsie ORAZ komunikaty
 * bledow przychodzace z serwera.
 */
export function zmienJezyk(nowyJezyk) {
  if (!JEZYKI.includes(nowyJezyk)) {
    return;
  }
  i18n.changeLanguage(nowyJezyk);
  ustawJezykZapytan(nowyJezyk);
  localStorage.setItem(KLUCZ_W_PAMIECI, nowyJezyk);
  document.documentElement.lang = nowyJezyk;
}

document.documentElement.lang = jezyk;

export default i18n;
