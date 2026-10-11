import { useMemo, useState } from 'react';
import Arkusz from './Arkusz.jsx';
import { IkPtaszek, IkStrzalka, IkSzukaj } from './Ikony.jsx';

// Pole wyboru, ktore otwiera arkusz z lista (z wyszukiwaniem, gdy opcji jest duzo) - zamiast natywnego <select>.
export default function Wybor({ etykieta, opcje, wartosc, onChange, ikona, szukaj, pustyTekst, zamknij, wPolu = true, podpowiedz }) {
  const [otwarty, setOtwarty] = useState(false);
  const [fraza, setFraza] = useState('');
  const wybrana = opcje.find((o) => o.value === wartosc);
  const widoczne = useMemo(() => {
    const f = fraza.trim().toLowerCase();
    return f ? opcje.filter((o) => String(o.label).toLowerCase().includes(f)) : opcje;
  }, [opcje, fraza]);

  return (
    <>
      <button type="button" className={wPolu ? 'mc-wybor' : 'mc-chip'} aria-haspopup="dialog" onClick={() => { setFraza(''); setOtwarty(true); }}>
        {ikona}
        <span className="mc-wybor-tekst">
          {wPolu && <span className="mc-wybor-etykieta">{etykieta}</span>}
          <span className="mc-wybor-wartosc">{wybrana?.label ?? '—'}</span>
        </span>
        <IkStrzalka size={16} className="mc-wybor-strzalka" />
      </button>
      <Arkusz otwarty={otwarty} onClose={() => setOtwarty(false)} tytul={etykieta} zamknij={zamknij}>
        {podpowiedz && <p className="mc-hint">{podpowiedz}</p>}
        {szukaj && (
          <label className="mc-szukaj mc-szukaj-arkusz">
            <IkSzukaj size={18} />
            <input value={fraza} onChange={(e) => setFraza(e.target.value)} placeholder={szukaj} aria-label={szukaj} data-autofocus />
          </label>
        )}
        <ul className="mc-opcje" role="listbox" aria-label={etykieta}>
          {widoczne.map((o, i) => (
            <li key={String(o.value)} style={{ '--i': Math.min(i, 12) }}>
              <button type="button" role="option" aria-selected={o.value === wartosc} className="mc-opcja"
                onClick={() => { onChange(o.value); setOtwarty(false); }} {...(!szukaj && o.value === wartosc ? { 'data-autofocus': true } : {})}>
                {o.ikona && <span className="mc-opcja-ikona">{o.ikona}</span>}
                <span className="mc-opcja-tekst">{o.label}</span>
                {o.dopisek != null && <span className="mc-opcja-dopisek">{o.dopisek}</span>}
                <IkPtaszek size={18} className="mc-opcja-ptaszek" />
              </button>
            </li>
          ))}
          {!widoczne.length && <li className="mc-hint mc-opcje-pusto">{pustyTekst}</li>}
        </ul>
      </Arkusz>
    </>
  );
}
