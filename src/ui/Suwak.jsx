import { useId } from 'react';

// Zasieg jako suwak: przeciagasz i lista od razu sie zmienia. Ostatnia pozycja to "caly kraj" (bez ograniczenia).
export const STOPNIE = [10, 20, 30, 50, 75, 100, 150, 200, 300, 0];

const indeks = (km) => {
  if (!km) return STOPNIE.length - 1;
  let najblizszy = 0;
  STOPNIE.slice(0, -1).forEach((s, i) => { if (Math.abs(s - km) < Math.abs(STOPNIE[najblizszy] - km)) najblizszy = i; });
  return najblizszy;
};

export default function Suwak({ wartosc, onChange, etykieta, calyKraj, disabled }) {
  const id = useId();
  const i = indeks(wartosc);
  const procent = (i / (STOPNIE.length - 1)) * 100;
  const tekst = STOPNIE[i] ? `${STOPNIE[i]} km` : calyKraj;
  return (
    <div className={`mc-suwak${disabled ? ' wylaczony' : ''}`} style={{ '--p': `${procent}%` }}>
      <div className="mc-suwak-gora">
        <label htmlFor={id}>{etykieta}</label>
        <output htmlFor={id} className="mc-suwak-wartosc" key={tekst}>{tekst}</output>
      </div>
      <input id={id} type="range" min="0" max={STOPNIE.length - 1} step="1" value={i} disabled={disabled}
        aria-valuetext={tekst} onChange={(e) => onChange(STOPNIE[Number(e.target.value)])} />
      <div className="mc-suwak-podzialka" aria-hidden="true">
        <span>10</span><span>50</span><span>100</span><span>200</span><span>{calyKraj}</span>
      </div>
    </div>
  );
}
