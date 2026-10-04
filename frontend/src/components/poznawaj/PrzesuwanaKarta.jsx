import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';

/** Od tylu pikseli w bok puszczona karta odlatuje (na waskim ekranie - od 30% szerokosci karty). */
const PROG_PX = 110;
/** Szybkie machniecie tez sie liczy, nawet krotkie (px/ms). */
const PREDKOSC = 0.55;
/** Dopoki palec nie przejedzie tylu pikseli, to jeszcze stukniecie (zdjecia), a nie przesuwanie. */
const MARTWA_STREFA = 8;
const CZAS_ODLOTU_MS = 320;

function bezRuchu() {
  return typeof window !== 'undefined' && window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
}

/**
 * Karta na wierzchu talii, ktora da sie przeciagnac palcem albo mysza: w prawo = "tak", w lewo = "nie".
 * Ruch w pionie zostaje dla przewijania karty (touch-action: pan-y), wiec przeciaganie zaczyna sie dopiero,
 * gdy ruch jest bardziej poziomy niz pionowy. W trakcie widac pieczatke TAK/NIE; puszczona za progiem karta
 * odlatuje, a `onOdlecial` dostaje decyzje dopiero po animacji (przy "ogranicz ruch" - od razu).
 *
 * `odlot` (LIKE/PASS) ustawia talia, gdy decyzja przyszla z przycisku albo klawiatury.
 */
export default function PrzesuwanaKarta({ children, odlot, onPuszczona, onOdlecial, glebokosc = 0 }) {
  const { t } = useTranslation();
  const [stan, setStan] = useState({ x: 0, y: 0, ciagnie: false });
  const start = useRef(null);
  const przeciagnieta = useRef(false);
  const element = useRef(null);
  const aktywna = glebokosc === 0;

  /* Odlot: z przycisku albo po puszczeniu za progiem. Koniec animacji = decyzja. */
  useEffect(() => {
    if (!odlot) {
      return undefined;
    }
    if (bezRuchu()) {
      onOdlecial(odlot);
      return undefined;
    }
    const zegar = setTimeout(() => onOdlecial(odlot), CZAS_ODLOTU_MS + 40);
    return () => clearTimeout(zegar);
    // onOdlecial celowo poza lista - odlot ma sie skonczyc jedna decyzja, nawet gdy talia sie przerysuje
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [odlot]);

  function wcisniety(e) {
    if (!aktywna || odlot || (e.pointerType === 'mouse' && e.button !== 0)) {
      return;
    }
    start.current = { x: e.clientX, y: e.clientY, czas: performance.now(), id: e.pointerId };
    przeciagnieta.current = false;
  }

  function ruch(e) {
    const s = start.current;
    if (!s || e.pointerId !== s.id) {
      return;
    }
    const dx = e.clientX - s.x;
    const dy = e.clientY - s.y;
    if (!przeciagnieta.current) {
      if (Math.abs(dx) < MARTWA_STREFA && Math.abs(dy) < MARTWA_STREFA) {
        return;
      }
      if (Math.abs(dy) >= Math.abs(dx)) {
        // Ruch w pionie - to przewijanie karty, nie decyzja
        start.current = null;
        return;
      }
      przeciagnieta.current = true;
      element.current?.setPointerCapture?.(e.pointerId);
    }
    setStan({ x: dx, y: dy * 0.2, ciagnie: true });
  }

  function puszczony(e) {
    const s = start.current;
    start.current = null;
    if (!s || !przeciagnieta.current) {
      return;
    }
    const dx = e.clientX - s.x;
    const szerokosc = element.current?.offsetWidth ?? 360;
    const prog = Math.min(PROG_PX, szerokosc * 0.3);
    const predkosc = Math.abs(dx) / Math.max(performance.now() - s.czas, 1);
    if (Math.abs(dx) > prog || (predkosc > PREDKOSC && Math.abs(dx) > 40)) {
      setStan((p) => ({ ...p, ciagnie: false }));
      onPuszczona(dx > 0 ? 'LIKE' : 'PASS');
    } else {
      setStan({ x: 0, y: 0, ciagnie: false });
    }
  }

  function anulowany() {
    start.current = null;
    if (przeciagnieta.current) {
      setStan({ x: 0, y: 0, ciagnie: false });
    }
  }

  /* Klikniecie, ktore konczy przeciaganie, nie ma przelaczac zdjec ani otwierac odnosnikow */
  function klikniecie(e) {
    if (przeciagnieta.current) {
      e.stopPropagation();
      e.preventDefault();
      przeciagnieta.current = false;
    }
  }

  let x = stan.x;
  let y = stan.y;
  if (odlot) {
    const szer = typeof window !== 'undefined' ? window.innerWidth : 800;
    x = (odlot === 'LIKE' ? 1 : -1) * (szer * 0.6 + 400);
    y = stan.y + 40;
  }
  const obrot = x * 0.05;
  const sila = Math.max(-1, Math.min(1, x / PROG_PX));

  const styl = aktywna
    ? {
      transform: `translate3d(${x}px, ${y}px, 0) rotate(${obrot}deg)`,
      transition: stan.ciagnie ? 'none' : `transform ${odlot ? CZAS_ODLOTU_MS : 260}ms var(--mc-ease)`,
      '--pz-sila': sila,
    }
    : { '--pz-glebokosc': glebokosc };

  return (
    <div
      ref={element}
      className={`pz-warstwa${aktywna ? ' is-wierzch' : ''}${stan.ciagnie ? ' is-ciagnieta' : ''}`}
      style={styl}
      onPointerDown={wcisniety}
      onPointerMove={ruch}
      onPointerUp={puszczony}
      onPointerCancel={anulowany}
      onClickCapture={klikniecie}
      aria-hidden={aktywna ? undefined : true}
    >
      {children}
      {aktywna && (
        <>
          <span className="pz-stempel pz-stempel-tak" style={{ opacity: odlot === 'LIKE' ? 1 : Math.max(0, sila) }}
            aria-hidden="true">{t('discover.stampYes')}</span>
          <span className="pz-stempel pz-stempel-nie" style={{ opacity: odlot === 'PASS' ? 1 : Math.max(0, -sila) }}
            aria-hidden="true">{t('discover.stampNo')}</span>
        </>
      )}
    </div>
  );
}
