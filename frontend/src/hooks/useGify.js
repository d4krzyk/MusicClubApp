import { useEffect, useState } from 'react';
import { status } from '../api/gify';

/**
 * Czy GIF-y dzialaja na tym serwerze (jest klucz dostawcy) - bez tego przycisk GIF sie nie pokazuje.
 * Pytamy raz na otwarcie aplikacji; obietnica jest wspolna dla wszystkich pol komentarza i czatu.
 */
let wspolne = null;

function pobierz() {
  if (!wspolne) {
    wspolne = status().catch(() => {
      // Nie wiemy - lepiej nie pokazywac przycisku, ktory moze nie dzialac; nastepny raz sprobujemy jeszcze raz
      wspolne = null;
      return { enabled: false, attribution: null };
    });
  }
  return wspolne;
}

/** { wlaczone, podpis } - do czasu odpowiedzi serwera wlaczone = false. */
export default function useGify() {
  const [stan, setStan] = useState({ wlaczone: false, podpis: null });

  useEffect(() => {
    let aktualne = true;
    pobierz().then((dane) => {
      if (aktualne) {
        setStan({ wlaczone: Boolean(dane.enabled), podpis: dane.attribution ?? null });
      }
    });
    return () => {
      aktualne = false;
    };
  }, []);

  return stan;
}
