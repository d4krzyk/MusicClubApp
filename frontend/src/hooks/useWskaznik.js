import { useLayoutEffect } from 'react';

/**
 * Przesuwany wskaznik w przelaczniku z kilku opcji (Wszystko / Znajomi, widoki wydarzen, Lista / Poznawaj):
 * zamiast przeskoku tla z opcji na opcje jedna "pigulka" plynie pod wybrana.
 *
 * Mierzy aktywny przycisk (aria-pressed albo aria-selected) i zapisuje jego polozenie w zmiennych CSS
 * kontenera; reszte robi CSS (style/ruch.css, ".ma-wskaznik::before"). Klase "ma-wskaznik" dostaje kontener
 * dopiero po pomiarze - bez JavaScriptu albo przed nim zostaje zwykle tlo aktywnej opcji, wiec nic nie znika.
 * Przejscie wlacza sie klatke po pierwszym pomiarze, zeby wskaznik nie wjezdzal z lewego rogu przy wejsciu.
 * Szerokosci opcji zmieniaja sie przy wczytaniu kroju i zmianie jezyka - pilnuje tego ResizeObserver.
 */
export default function useWskaznik(ref, aktywny) {
  useLayoutEffect(() => {
    const kontener = ref.current;
    if (!kontener) {
      return undefined;
    }

    // offsetLeft liczy sie od najblizszego przodka z pozycja - ma nim byc sam przelacznik
    if (getComputedStyle(kontener).position === 'static') {
      kontener.style.position = 'relative';
    }

    function zmierz() {
      const opcja = kontener.querySelector(':scope > [aria-pressed="true"], :scope > [aria-selected="true"]');
      if (!opcja) {
        kontener.classList.remove('ma-wskaznik');
        return;
      }
      kontener.style.setProperty('--wskaznik-x', `${opcja.offsetLeft}px`);
      kontener.style.setProperty('--wskaznik-y', `${opcja.offsetTop}px`);
      kontener.style.setProperty('--wskaznik-w', `${opcja.offsetWidth}px`);
      kontener.style.setProperty('--wskaznik-h', `${opcja.offsetHeight}px`);
      kontener.classList.add('ma-wskaznik');
    }

    zmierz();
    let klatka = 0;
    if (!kontener.classList.contains('wskaznik-plynie')) {
      klatka = requestAnimationFrame(() => kontener.classList.add('wskaznik-plynie'));
    }

    let obserwator = null;
    if (typeof ResizeObserver === 'function') {
      obserwator = new ResizeObserver(zmierz);
      obserwator.observe(kontener);
      Array.from(kontener.children).forEach((dziecko) => obserwator.observe(dziecko));
    }
    return () => {
      cancelAnimationFrame(klatka);
      obserwator?.disconnect();
    };
  }, [ref, aktywny]);
}
