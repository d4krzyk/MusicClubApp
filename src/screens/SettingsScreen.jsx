import { useEffect, useRef, useState } from 'react';
import { czytajMotyw, ustawMotyw } from '../motyw.js';
import Suwak from '../ui/Suwak.jsx';
import { useT } from '../i18n.js';
import { KRAJE } from '../format.js';
import { CITIES, cityByName } from '../lib/location.js';
import { go } from '../nav.js';
import { saveFile } from '../saveFile.js';
import { KeyField, Rozwijane } from '../components.jsx';
import Przelacznik from '../ui/Przelacznik.jsx';
import Wybor from '../ui/Wybor.jsx';
import Arkusz from '../ui/Arkusz.jsx';
import { useToast } from '../ui/Toast.jsx';
import { IkAuto, IkSlonce, IkKsiezyc, IkPlik, IkLink, IkKosz, IkKalendarzPlus } from '../ui/Ikony.jsx';

const flaga = (kod) => String.fromCodePoint(...[...kod].map((c) => 0x1f1e6 + c.charCodeAt(0) - 65));

function Kalendarze({ app }) {
  const { t } = useT();
  const toast = useToast();
  const plik = useRef(null);
  const [arkusz, setArkusz] = useState(false);
  const [jak, setJak] = useState(false);
  const [nazwa, setNazwa] = useState('');
  const [adres, setAdres] = useState('');
  const [blad, setBlad] = useState(null);
  const [laduje, setLaduje] = useState(false);

  async function zPliku(e) {
    const f = e.target.files?.[0];
    e.target.value = '';
    if (!f) return;
    try {
      const k = await app.kalendarze.dodajPlik(await f.text(), f.name.replace(/\.ics$/i, ''));
      toast(t('set.calAdded', k.events.length));
    } catch (er) { toast(er?.message ?? String(er), 'blad'); }
  }

  async function zAdresu(e) {
    e.preventDefault();
    setBlad(null);
    setLaduje(true);
    try {
      const k = await app.kalendarze.dodajAdres(adres, nazwa.trim());
      toast(t('set.calAdded', k.events.length));
      setArkusz(false);
      setNazwa(''); setAdres('');
    } catch (er) { setBlad(er?.message ?? String(er)); } finally { setLaduje(false); }
  }

  return (
    <div className="mc-podsekcja">
      <h3>{t('set.calendars')}</h3>
      <p className="mc-hint">{t('set.calendarsIntro')}</p>
      {app.calendars.length === 0 ? <p className="mc-hint mc-pusto">{t('set.calNone')}</p> : (
        <ul className="mc-kalendarze">{app.calendars.map((k) => (
          <li key={k.id} className="mc-wejscie">
            <span className="mc-kal-ikona" aria-hidden="true">{k.kind === 'url' ? <IkLink size={18} /> : <IkPlik size={18} />}</span>
            <span className="mc-kal-opis">
              <b className="mc-trunc">{k.name}</b>
              <small>{t('set.calEvents', k.events.length)} · {k.kind === 'url' ? new URL(k.url).hostname : t('set.calFile')}</small>
              {k.error && <small className="mc-error">{k.error}</small>}
            </span>
            <button type="button" className="mc-ikona-btn" aria-label={t('set.calRemove', k.name)} onClick={() => app.kalendarze.usun(k.id)}><IkKosz size={18} /></button>
          </li>))}</ul>
      )}
      <div className="mc-actions">
        <button type="button" className="mc-btn" onClick={() => plik.current?.click()}><IkPlik size={18} />{t('set.calImportFile')}</button>
        <button type="button" className="mc-btn" onClick={() => { setBlad(null); setArkusz(true); }}><IkKalendarzPlus size={18} />{t('set.calAddUrl')}</button>
        <input ref={plik} type="file" accept=".ics,text/calendar" hidden onChange={zPliku} />
      </div>
      <button type="button" className="mc-link-btn" aria-expanded={jak} onClick={() => setJak(!jak)}>{t('set.calHowTo')}</button>
      <Rozwijane otwarte={jak}><p className="mc-hint mc-ramka">{t('set.calHowToText')}</p></Rozwijane>

      <Arkusz otwarty={arkusz} onClose={() => setArkusz(false)} tytul={t('set.calAddUrl')} zamknij={t('common.close')}>
        <form onSubmit={zAdresu}>
          <label className="mc-field">{t('set.calUrl')}
            <input value={adres} onChange={(e) => setAdres(e.target.value)} inputMode="url" autoCapitalize="off" autoComplete="off" spellCheck="false" data-autofocus />
          </label>
          <label className="mc-field">{t('set.calName')}
            <input value={nazwa} onChange={(e) => setNazwa(e.target.value)} maxLength={60} />
          </label>
          {blad && <p className="mc-error mc-wejscie" role="alert">{blad}</p>}
          <p className="mc-hint">{t('set.calCors')}</p>
          <button type="submit" className="mc-btn mc-btn-primary mc-btn-duzy" disabled={!adres.trim() || laduje}>
            {laduje && <span className="mc-spinner" aria-hidden="true" />}{t('set.calAdd')}
          </button>
        </form>
      </Arkusz>
    </div>
  );
}

export default function SettingsScreen({ app }) {
  const { t, lang } = useT();
  const toast = useToast();
  const s = app.settings;
  const plik = useRef(null);
  const [motyw, setMotyw] = useState(czytajMotyw);
  useEffect(() => {
    const f = (e) => setMotyw(e.detail);
    window.addEventListener('mc-motyw', f);
    return () => window.removeEventListener('mc-motyw', f);
  }, []);

  function eksport() {
    saveFile('musicclub-ustawienia.json', 'application/json', JSON.stringify({ wersja: 1, ustawienia: s }, null, 2)).catch(() => { /* okno zamkniete */ });
  }

  async function import_(e) {
    const f = e.target.files?.[0];
    e.target.value = '';
    if (!f) return;
    try {
      const json = JSON.parse(await f.text());
      if (!json?.ustawienia || typeof json.ustawienia !== 'object') throw new Error('format');
      app.setSettings(json.ustawienia);
      toast(t('common.saved'));
    } catch { toast(t('err.generic', 'plik'), 'blad'); }
  }

  const miasto = s.city.trim();
  const znaneMiasto = !miasto || !!cityByName(miasto);
  const nazwyKrajow = new Intl.DisplayNames([lang], { type: 'region' });

  return (
    <section className="mc-settings">
      <h1>{t('set.title')}</h1>

      <div className="mc-card mc-section mc-wejscie" style={{ '--i': 0 }}>
        <h2>{t('set.keys')}</h2>
        <p className="mc-hint">{t('set.keysIntro')}</p>
        <KeyField label={t('set.tmKey')} help={t('set.tmHelp')} helpUrl="https://developer.ticketmaster.com/" value={s.tmKey} onChange={(v) => app.setSettings({ tmKey: v.trim() })} />
        <KeyField label={t('set.lfmKey')} help={t('set.lfmHelp')} helpUrl="https://www.last.fm/api/account/create" value={s.lfmKey} onChange={(v) => app.setSettings({ lfmKey: v.trim() })} />
      </div>

      <div className="mc-card mc-section mc-wejscie" style={{ '--i': 1 }}>
        <h2>{t('set.sources')}</h2>
        <p className="mc-hint">{t('set.sourcesIntro')}</p>
        <KeyField label={t('set.bitKey')} help={t('set.bitHelp')} helpUrl="https://help.artists.bandsintown.com/en/articles/9186477-api-documentation" value={s.bitKey ?? ''} onChange={(v) => app.setSettings({ bitKey: v.trim() })} />
        <Kalendarze app={app} />
      </div>

      <div className="mc-card mc-section mc-wejscie" style={{ '--i': 2 }}>
        <h2>{t('set.where')}</h2>
        <Wybor etykieta={t('set.country')} wartosc={s.country} onChange={(v) => app.setSettings({ country: v })} szukaj={t('set.countrySearch')}
          zamknij={t('common.close')} opcje={KRAJE.map((k) => ({ value: k, label: nazwyKrajow.of(k), ikona: flaga(k) }))} ikona={<span className="mc-flaga">{flaga(s.country)}</span>} />
        <div className="mc-field">
          <span>{t('set.months')}</span>
          <Przelacznik opcje={[1, 3, 6, 9, 12].map((n) => ({ value: n, label: String(n) }))} wartosc={s.months}
            onChange={(v) => app.setSettings({ months: v })} etykieta={t('set.months')} />
        </div>
        <label className="mc-field">{t('set.city')}
          <input list="mc-miasta" value={s.city} onChange={(e) => app.setSettings({ city: e.target.value })} autoComplete="off" />
          <datalist id="mc-miasta">{CITIES.slice(0, 120).map((c) => <option key={c.name} value={c.name} />)}</datalist>
          <span className={`mc-hint${znaneMiasto ? '' : ' mc-uwaga'}`}>{znaneMiasto ? t('set.cityHint') : t('set.cityUnknown')}</span>
        </label>
        <Suwak etykieta={t('set.radius')} calyKraj={t('list.radiusAll')} wartosc={cityByName(miasto) ? s.radius : 0}
          disabled={!cityByName(miasto)} onChange={(v) => app.setSettings({ radius: v })} />
      </div>

      <div className="mc-card mc-section mc-wejscie" style={{ '--i': 3 }}>
        <h2>{t('set.look')}</h2>
        <div className="mc-field">
          <span>{t('set.lang')}</span>
          <Przelacznik opcje={[{ value: 'pl', label: 'Polski' }, { value: 'en', label: 'English' }]} wartosc={s.lang || lang}
            onChange={(v) => app.setSettings({ lang: v })} etykieta={t('set.lang')} />
        </div>
        <div className="mc-field">
          <span>{t('set.theme')}</span>
          <Przelacznik wartosc={motyw} onChange={(m) => { setMotyw(m); ustawMotyw(m); }} etykieta={t('set.theme')} opcje={[
            { value: 'auto', label: t('set.theme.auto'), ikona: <IkAuto size={16} /> },
            { value: 'light', label: t('set.theme.light'), ikona: <IkSlonce size={16} /> },
            { value: 'dark', label: t('set.theme.dark'), ikona: <IkKsiezyc size={16} /> }]} />
        </div>
      </div>

      <div className="mc-card mc-section mc-wejscie" style={{ '--i': 4 }}>
        <h2>{t('set.data')}</h2>
        <div className="mc-actions">
          <button type="button" className="mc-btn" onClick={eksport}>{t('set.export')}</button>
          <button type="button" className="mc-btn" onClick={() => plik.current?.click()}>{t('set.import')}</button>
          <input ref={plik} type="file" accept="application/json,.json" hidden onChange={import_} />
        </div>
        <p className="mc-hint">{t('set.exportNote')}</p>
        <button type="button" className="mc-btn mc-btn-danger" onClick={() => { if (window.confirm(t('set.wipeConfirm'))) app.wipe().then(() => go('/')); }}>{t('set.wipe')}</button>
      </div>

      <div className="mc-card mc-section mc-wejscie" style={{ '--i': 5 }}>
        <h2>{t('set.about')}</h2>
        <p>{t('set.aboutText')}</p>
      </div>
    </section>
  );
}
