import { useEffect, useId, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { IkX } from './Ikony.jsx';

// Arkusz wysuwany od dolu (na komputerze okno na srodku). Wjezdza i zjezdza z animacja, zamyka sie Esc, kliknieciem
// w tlo i przeciagnieciem w dol za uchwyt. Przy otwarciu fokus idzie do srodka, po zamknieciu wraca na przycisk.
export default function Arkusz({ otwarty, onClose, tytul, children, stopka, zamknij = 'Zamknij' }) {
  const [widoczny, setWidoczny] = useState(otwarty);
  const [wychodzi, setWychodzi] = useState(false);
  const panel = useRef(null);
  const wczesniej = useRef(null);
  const przeciaganie = useRef(null);
  const id = useId();

  useEffect(() => {
    if (otwarty) {
      wczesniej.current = document.activeElement;
      setWidoczny(true);
      setWychodzi(false);
    } else if (widoczny) {
      setWychodzi(true);
      const t = setTimeout(() => { setWidoczny(false); setWychodzi(false); wczesniej.current?.focus?.(); }, 220);
      return () => clearTimeout(t);
    }
    return undefined;
  }, [otwarty]); // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    if (!widoczny || wychodzi) return undefined;
    const klawisz = (e) => { if (e.key === 'Escape') onClose(); };
    document.addEventListener('keydown', klawisz);
    document.body.classList.add('mc-bez-przewijania');
    const t = requestAnimationFrame(() => {
      const cel = panel.current?.querySelector('[data-autofocus]') ?? panel.current;
      cel?.focus({ preventScroll: true });
    });
    return () => {
      document.removeEventListener('keydown', klawisz);
      document.body.classList.remove('mc-bez-przewijania');
      cancelAnimationFrame(t);
    };
  }, [widoczny, wychodzi, onClose]);

  if (!widoczny) return null;

  const start = (e) => { przeciaganie.current = { y: e.clientY, dy: 0 }; e.currentTarget.setPointerCapture?.(e.pointerId); };
  const ruch = (e) => {
    if (!przeciaganie.current) return;
    const dy = Math.max(0, e.clientY - przeciaganie.current.y);
    przeciaganie.current.dy = dy;
    panel.current.style.transform = `translateY(${dy}px)`;
    panel.current.style.transition = 'none';
  };
  const koniec = () => {
    if (!przeciaganie.current) return;
    const { dy } = przeciaganie.current;
    przeciaganie.current = null;
    panel.current.style.transition = '';
    panel.current.style.transform = '';
    if (dy > 90) onClose();
  };

  return createPortal(
    <div className={`mc-arkusz-tlo${wychodzi ? ' wychodzi' : ''}`} onMouseDown={(e) => { if (e.target === e.currentTarget) onClose(); }}>
      <div ref={panel} className={`mc-arkusz${wychodzi ? ' wychodzi' : ''}`} role="dialog" aria-modal="true" aria-labelledby={id} tabIndex={-1}>
        <div className="mc-arkusz-uchwyt" onPointerDown={start} onPointerMove={ruch} onPointerUp={koniec} onPointerCancel={koniec}>
          <span />
        </div>
        <header className="mc-arkusz-naglowek">
          <h2 id={id}>{tytul}</h2>
          <button type="button" className="mc-ikona-btn" onClick={onClose} aria-label={zamknij}><IkX /></button>
        </header>
        <div className="mc-arkusz-tresc">{children}</div>
        {stopka && <footer className="mc-arkusz-stopka">{stopka}</footer>}
      </div>
    </div>,
    document.body,
  );
}
