import { useEffect, useId, useRef, useState } from 'react';
import { useT } from '../i18n.js';
import { IkPinezka } from './Ikony.jsx';
import { suggestPlaces } from '../lib/adresy.js';

// Pole tekstowe z podpowiedziami miejsc i adresow w wybranym miescie (Photon / OpenStreetMap). Lista rozwija sie pod
// polem (nie na nim - w przewijanym arkuszu nic sie nie ucina), strzalki i Enter wybieraja, Esc zwija liste. Pytamy
// dopiero po przerwie w pisaniu, tylko o to, co wpisal czlowiek - nie o wartosc ustawiona z podpowiedzi.
export default function PolePodpowiedzi({ etykieta, value, onChange, onWybierz, miasto, maxLength }) {
  const { t } = useT();
  const id = useId();
  const [fraza, setFraza] = useState(null); // null = nic nie wpisano od otwarcia albo od wyboru
  const [lista, setLista] = useState([]);
  const [stan, setStan] = useState('cisza'); // cisza | szuka | brak | blad
  const [aktywna, setAktywna] = useState(-1);
  const [otwarta, setOtwarta] = useState(false);
  const pole = useRef(null);

  useEffect(() => {
    if (fraza == null || fraza.trim().length < 3) { setLista([]); setStan('cisza'); return undefined; }
    const ctrl = new AbortController();
    const t1 = setTimeout(async () => {
      setStan('szuka');
      try {
        const wynik = await suggestPlaces(fraza, miasto, { signal: ctrl.signal });
        setLista(wynik);
        setAktywna(-1);
        setStan(wynik.length ? 'cisza' : 'brak');
      } catch (e) {
        if (e?.name !== 'AbortError') { setLista([]); setStan('blad'); }
      }
    }, 350);
    return () => { clearTimeout(t1); ctrl.abort(); };
  }, [fraza, miasto]);

  const wybierz = (s) => {
    onWybierz(s);
    setFraza(null);
    setLista([]);
    setOtwarta(false);
    pole.current?.focus();
  };

  const widac = otwarta && (lista.length > 0 || stan !== 'cisza');
  const klawisz = (e) => {
    if (!widac || !lista.length) {
      if (e.key === 'Escape' && widac) { e.stopPropagation(); setOtwarta(false); }
      return;
    }
    if (e.key === 'ArrowDown') { e.preventDefault(); setAktywna((a) => (a + 1) % lista.length); }
    else if (e.key === 'ArrowUp') { e.preventDefault(); setAktywna((a) => (a <= 0 ? lista.length - 1 : a - 1)); }
    else if (e.key === 'Enter' && aktywna >= 0) { e.preventDefault(); wybierz(lista[aktywna]); }
    else if (e.key === 'Escape') { e.stopPropagation(); setOtwarta(false); } // zwija liste, arkusz zostaje
  };

  return (
    <div className="mc-field mc-podpowiedzi">
      <label htmlFor={`${id}-pole`}>{etykieta}</label>
      <input
        ref={pole}
        id={`${id}-pole`}
        value={value}
        maxLength={maxLength}
        autoComplete="off"
        role="combobox"
        aria-autocomplete="list"
        aria-expanded={widac}
        aria-controls={`${id}-lista`}
        aria-activedescendant={widac && aktywna >= 0 ? `${id}-${aktywna}` : undefined}
        onChange={(e) => { onChange(e.target.value); setFraza(e.target.value); setOtwarta(true); }}
        onKeyDown={klawisz}
        onBlur={() => setOtwarta(false)}
        onFocus={() => { if (lista.length) setOtwarta(true); }}
      />
      {widac && (
        <div className="mc-podpowiedzi-lista">
          {lista.length > 0 && <ul id={`${id}-lista`} role="listbox" aria-label={etykieta}>
            {lista.map((s, i) => (
              <li key={s.label} id={`${id}-${i}`} role="option" aria-selected={i === aktywna} className={i === aktywna ? 'aktywna' : ''}
                // mousedown bez domyslnej akcji: fokus zostaje w polu, a klikniecie i tak wybiera
                onMouseDown={(e) => e.preventDefault()} onClick={() => wybierz(s)}>
                <IkPinezka size={16} />
                <span className="mc-podpowiedz-tekst">
                  <b>{s.name ?? s.address}</b>
                  <small>{[s.name ? s.address : null, s.city].filter(Boolean).join(', ')}</small>
                </span>
              </li>
            ))}
          </ul>}
          {stan === 'szuka' && !lista.length && <p className="mc-hint"><span className="mc-spinner" aria-hidden="true" /> {t('edit.searching')}</p>}
          {stan === 'brak' && <p className="mc-hint">{t('edit.noSuggestions', miasto || '—')}</p>}
          {stan === 'blad' && <p className="mc-hint">{t('edit.suggestError')}</p>}
          <p className="mc-podpowiedzi-zrodlo">© OpenStreetMap · Photon</p>
        </div>
      )}
    </div>
  );
}
