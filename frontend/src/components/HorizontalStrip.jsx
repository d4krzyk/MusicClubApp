import { useCallback, useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { IconArrowLeft, IconArrowRight } from './Icons';

/**
 * Pozioma lista przewijana strzalkami.
 *
 * <p><b>Po co osobny komponent.</b> Uklad poziomy mamy juz w trzech miejscach
 * (znajomi, propozycje, ulubieni) i wszedzie znaczy to samo: "to jest lista,
 * zacznij od lewej". Jedno miejsce zamiast trzech kopii sprawia, ze zachowanie
 * strzalek jest wszedzie identyczne - a to wlasnie po nim uzytkownik poznaje,
 * ze ma do czynienia z tym samym rodzajem listy.</p>
 *
 * <p><b>Dlaczego strzalki, a nie samo przewijanie.</b> Palcem przewija sie
 * naturalnie, ale myszka juz nie: pasek poziomy bez widocznego sterowania
 * wyglada jak lista, ktora sie po prostu urywa. Strzalki mowia wprost, ze
 * dalej cos jest.</p>
 *
 * <p>Strzalki pokazujemy <b>tylko wtedy, gdy jest co przewijac</b>, i wygaszamy
 * na koncach. Przycisk, ktory nic nie robi, jest gorszy niz jego brak.</p>
 */
export default function HorizontalStrip({ children, itemWidth = 132, className = '' }) {
  const { t } = useTranslation();
  const track = useRef(null);

  const [canScrollLeft, setCanScrollLeft] = useState(false);
  const [canScrollRight, setCanScrollRight] = useState(false);

  const refreshArrows = useCallback(() => {
    const el = track.current;
    if (!el) return;

    // Jeden piksel zapasu: przy powiekszeniu strony szerokosci wychodza
    // ulamkowe i "koniec listy" nigdy nie wypadalby dokladnie na zero
    setCanScrollLeft(el.scrollLeft > 1);
    setCanScrollRight(el.scrollLeft + el.clientWidth < el.scrollWidth - 1);
  }, []);

  useEffect(() => {
    const el = track.current;
    if (!el) return undefined;

    refreshArrows();

    /*
     * Samo nasluchiwanie przewijania nie wystarczy: lista zmienia sie takze
     * przy zwezeniu okna i po dodaniu pozycji. ResizeObserver lapie oba
     * przypadki - bez niego strzalka potrafilaby zostac aktywna, choc nie ma
     * juz czego przewijac.
     */
    const observer = new ResizeObserver(refreshArrows);
    observer.observe(el);
    for (const child of el.children) observer.observe(child);

    el.addEventListener('scroll', refreshArrows, { passive: true });
    return () => {
      observer.disconnect();
      el.removeEventListener('scroll', refreshArrows);
    };
  }, [refreshArrows, children]);

  function scroll(direction) {
    const el = track.current;
    if (!el) return;

    /*
     * Przewijamy o cala widoczna szerokosc pomniejszona o jeden kafelek.
     * Ten jeden kafelek zostaje na widoku celowo - daje punkt zaczepienia
     * i od razu widac, ze to ta sama lista, a nie nowy ekran.
     */
    const step = Math.max(el.clientWidth - itemWidth, itemWidth);
    el.scrollBy({ left: direction * step, behavior: 'smooth' });
  }

  const hasArrows = canScrollLeft || canScrollRight;

  return (
    <div
      className={`strip ${className}`
        + (canScrollLeft ? ' can-left' : '')
        + (canScrollRight ? ' can-right' : '')}
    >
      {hasArrows && (
        <button
          type="button"
          className="strip-arrow strip-arrow-left"
          onClick={() => scroll(-1)}
          disabled={!canScrollLeft}
          aria-label={t('common.scrollLeft')}
        >
          <IconArrowLeft />
        </button>
      )}

      <div className="strip-track" ref={track}>
        {children}
      </div>

      {hasArrows && (
        <button
          type="button"
          className="strip-arrow strip-arrow-right"
          onClick={() => scroll(1)}
          disabled={!canScrollRight}
          aria-label={t('common.scrollRight')}
        >
          <IconArrowRight />
        </button>
      )}
    </div>
  );
}
