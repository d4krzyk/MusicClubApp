import { useMemo, useState } from 'react';
import { useT } from '../i18n.js';
import Arkusz from './Arkusz.jsx';
import { useToast } from './Toast.jsx';
import { IkPinezka, IkPtaszek, IkStrzalka, IkSzukaj, IkCel } from './Ikony.jsx';
import { CITIES, cityByName } from '../lib/location.js';
import { miastoZLokalizacji } from '../lokalizacja.js';

// Wybor miasta: arkusz z wyszukiwaniem po liscie miast Polski, "Moja lokalizacja" (GPS, jednorazowo) i mozliwosc
// wpisania miasta spoza listy. Zamiast natywnej podpowiedzi <datalist>, ktora na Androidzie wyglada jak z innej aplikacji.
export default function MiastoWybor({ wartosc, onChange, etykieta, gps = true }) {
  const { t } = useT();
  const toast = useToast();
  const [otwarty, setOtwarty] = useState(false);
  const [fraza, setFraza] = useState('');
  const [szuka, setSzuka] = useState(false);
  const znane = cityByName(wartosc);

  const lista = useMemo(() => {
    const f = fraza.trim().toLowerCase().normalize('NFD').replace(/\p{M}/gu, '').replace(/ł/g, 'l');
    const norm = (n) => n.toLowerCase().normalize('NFD').replace(/\p{M}/gu, '').replace(/ł/g, 'l');
    const pasujace = f ? CITIES.filter((c) => norm(c.name).includes(f)) : CITIES;
    return pasujace.sort((a, b) => (f ? Number(!norm(a.name).startsWith(f)) - Number(!norm(b.name).startsWith(f)) : 0)).slice(0, 60);
  }, [fraza]);

  const wybierz = (v) => { onChange(v); setOtwarty(false); };

  async function zGps() {
    setSzuka(true);
    const w = await miastoZLokalizacji();
    setSzuka(false);
    if (w.city) { wybierz(w.city); toast(t('city.found', w.city)); return; }
    toast(t(`city.gps.${w.error}`), 'blad');
  }

  const wpisane = fraza.trim();
  const wpisaneZnane = wpisane && cityByName(wpisane);

  return (
    <>
      <button type="button" className="mc-wybor" aria-haspopup="dialog" onClick={() => { setFraza(''); setOtwarty(true); }}>
        <IkPinezka size={20} />
        <span className="mc-wybor-tekst">
          <span className="mc-wybor-etykieta">{etykieta}</span>
          <span className="mc-wybor-wartosc">{wartosc || t('city.none')}</span>
        </span>
        <IkStrzalka size={16} className="mc-wybor-strzalka" />
      </button>
      {wartosc && !znane && <span className="mc-hint mc-uwaga">{t('set.cityUnknown')}</span>}
      <Arkusz otwarty={otwarty} onClose={() => setOtwarty(false)} tytul={etykieta} zamknij={t('common.close')}>
        {gps && (
          <button type="button" className="mc-akcja mc-akcja-gps" onClick={zGps} disabled={szuka}>
            <span className="mc-akcja-ikona" aria-hidden="true">{szuka ? <span className="mc-spinner" /> : <IkCel />}</span>
            <span><b>{t('city.gps')}</b><small>{t('city.gpsHint')}</small></span>
          </button>
        )}
        <label className="mc-szukaj mc-szukaj-arkusz">
          <IkSzukaj size={18} />
          <input value={fraza} onChange={(e) => setFraza(e.target.value)} placeholder={t('list.searchCity')} aria-label={t('list.searchCity')} data-autofocus />
        </label>
        <ul className="mc-opcje" role="listbox" aria-label={etykieta}>
          {wpisane && !wpisaneZnane && (
            <li><button type="button" role="option" aria-selected="false" className="mc-opcja" onClick={() => wybierz(wpisane)}>
              <span className="mc-opcja-tekst">{t('city.useTyped', wpisane)}</span></button></li>
          )}
          {lista.map((c, i) => (
            <li key={c.name} style={{ '--i': Math.min(i, 12) }}>
              <button type="button" role="option" aria-selected={znane?.name === c.name} className="mc-opcja" onClick={() => wybierz(c.name)}>
                <span className="mc-opcja-tekst">{c.name}</span>
                <IkPtaszek size={18} className="mc-opcja-ptaszek" />
              </button>
            </li>
          ))}
          {wartosc && (
            <li><button type="button" role="option" aria-selected="false" className="mc-opcja mc-opcja-wyczysc" onClick={() => wybierz('')}>
              <span className="mc-opcja-tekst">{t('city.clear')}</span></button></li>
          )}
        </ul>
      </Arkusz>
    </>
  );
}
