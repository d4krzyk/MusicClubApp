import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import pl from './pl.json';
import en from './en.json';
import { setRequestLanguage } from '../api/client';

export const LANGUAGES = ['pl', 'en'];
const STORAGE_KEY = 'musicclub.lang';

function initialLanguage() {
  const stored = localStorage.getItem(STORAGE_KEY);
  if (LANGUAGES.includes(stored)) {
    return stored;
  }
  const fromBrowser = navigator.language?.slice(0, 2);
  return LANGUAGES.includes(fromBrowser) ? fromBrowser : 'pl';
}

const language = initialLanguage();

i18n.use(initReactI18next).init({
  resources: {
    pl: { translation: pl },
    en: { translation: en },
  },
  lng: language,
  fallbackLng: 'pl',
  interpolation: {
    // React sam zabezpiecza tekst przed wstrzyknieciem HTML-a
    escapeValue: false,
  },
});

// Ten sam jezyk wysylamy do backendu, zeby bledy walidacji tez byly przetlumaczone
setRequestLanguage(language);

/**
 * Przelacza jezyk calej aplikacji - napisy w interfejsie ORAZ komunikaty bledow przychodzace z
 * serwera.
 */
export function changeLanguage(newLanguage) {
  if (!LANGUAGES.includes(newLanguage)) {
    return;
  }
  i18n.changeLanguage(newLanguage);
  setRequestLanguage(newLanguage);
  localStorage.setItem(STORAGE_KEY, newLanguage);
  document.documentElement.lang = newLanguage;
}

document.documentElement.lang = language;

export default i18n;
