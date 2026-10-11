import { useId } from 'react';

// Wlacznik tak/nie z opisem (role="switch"); kulka przesuwa sie sprezyscie.
export default function Wlacznik({ wlaczony, onChange, etykieta, opis, disabled }) {
  const id = useId();
  return (
    <div className={`mc-wlacznik-wiersz${disabled ? ' wylaczony' : ''}`}>
      <span className="mc-wlacznik-tekst">
        <label htmlFor={id}>{etykieta}</label>
        {opis && <small>{opis}</small>}
      </span>
      <button id={id} type="button" role="switch" aria-checked={wlaczony} disabled={disabled} className={`mc-wlacznik${wlaczony ? ' wl' : ''}`}
        onClick={() => onChange(!wlaczony)}><span /></button>
    </div>
  );
}
