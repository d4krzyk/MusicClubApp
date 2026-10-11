import { useRef } from 'react';
import useWskaznik from './useWskaznik.js';

// Przelacznik kilku opcji z plynaca "pigulka" pod wybrana (zamiast listy rozwijanej albo skaczacego tla).
// tryb="tabs" - zakladki (role tab), inaczej grupa przyciskow (aria-pressed).
export default function Przelacznik({ opcje, wartosc, onChange, etykieta, tryb, maly, rozciagniety = true, className = '' }) {
  const ref = useRef(null);
  useWskaznik(ref, `${wartosc}|${opcje.map((o) => o.value).join(',')}`);
  const zakladki = tryb === 'tabs';
  return (
    <div ref={ref} className={`mc-seg${maly ? ' mc-seg-maly' : ''}${rozciagniety ? ' mc-seg-pelny' : ''} ${className}`}
      role={zakladki ? 'tablist' : 'group'} aria-label={etykieta}>
      {opcje.map((o) => {
        const wybrana = o.value === wartosc;
        return (
          <button key={String(o.value)} type="button" className="mc-seg-opcja" disabled={o.disabled}
            aria-label={o.ikona && typeof o.label === 'string' ? o.label : undefined} title={o.ikona && typeof o.label === 'string' ? o.label : undefined}
            {...(zakladki ? { role: 'tab', 'aria-selected': wybrana } : { 'aria-pressed': wybrana })}
            onClick={() => !wybrana && onChange(o.value)}>
            {o.ikona}{o.label != null && <span>{o.label}</span>}
          </button>
        );
      })}
    </div>
  );
}
