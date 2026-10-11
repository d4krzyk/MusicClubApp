import { useRef, useState } from 'react';
import { useT } from '../i18n.js';
import { KRAJE } from '../format.js';
import { CITIES, cityByName } from '../lib/location.js';
import { go } from '../nav.js';
import { saveFile } from '../saveFile.js';

function KeyField({ label, help, helpUrl, value, onChange }) {
  const { t } = useT();
  const [pokaz, setPokaz] = useState(false);
  return (
    <label className="mc-field">{label}
      <span className="mc-inline">
        <input type={pokaz ? 'text' : 'password'} value={value} onChange={(e) => onChange(e.target.value)} autoComplete="off" autoCapitalize="off" spellCheck="false" />
        <button type="button" className="mc-btn mc-btn-small" onClick={() => setPokaz(!pokaz)}>{pokaz ? t('set.hide') : t('set.show')}</button>
      </span>
      <a className="mc-hint" href={helpUrl} target="_blank" rel="noopener noreferrer">{help}</a>
    </label>
  );
}

export default function SettingsScreen({ app }) {
  const { t, lang } = useT();
  const s = app.settings;
  const plik = useRef(null);
  const [motyw, setMotyw] = useState(() => { try { return localStorage.getItem('motyw') ?? 'auto'; } catch { return 'auto'; } });
  const [komunikat, setKomunikat] = useState(null);

  function ustawMotyw(m) {
    setMotyw(m);
    try { m === 'auto' ? localStorage.removeItem('motyw') : localStorage.setItem('motyw', m); } catch { /* nic */ }
    const wybrany = m === 'auto' ? (window.matchMedia('(prefers-color-scheme: light)').matches ? 'light' : 'dark') : m;
    document.documentElement.setAttribute('data-theme', wybrany);
  }

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
      setKomunikat(t('common.saved'));
    } catch { setKomunikat(t('err.generic', 'plik')); }
  }

  const miasto = s.city.trim();
  const znaneMiasto = !miasto || !!cityByName(miasto);

  return (
    <section className="mc-settings">
      <h1>{t('set.title')}</h1>

      <div className="mc-card mc-section">
        <h2>{t('set.keys')}</h2>
        <p className="mc-hint">{t('set.keysIntro')}</p>
        <KeyField label={t('set.tmKey')} help={t('set.tmHelp')} helpUrl="https://developer.ticketmaster.com/" value={s.tmKey} onChange={(v) => app.setSettings({ tmKey: v.trim() })} />
        <KeyField label={t('set.lfmKey')} help={t('set.lfmHelp')} helpUrl="https://www.last.fm/api/account/create" value={s.lfmKey} onChange={(v) => app.setSettings({ lfmKey: v.trim() })} />
      </div>

      <div className="mc-card mc-section">
        <h2>{t('set.where')}</h2>
        <label className="mc-field">{t('set.country')}
          <select value={s.country} onChange={(e) => app.setSettings({ country: e.target.value })}>
            {KRAJE.map((k) => <option key={k} value={k}>{new Intl.DisplayNames([lang], { type: 'region' }).of(k)} ({k})</option>)}
          </select>
        </label>
        <label className="mc-field">{t('set.months')}
          <select value={s.months} onChange={(e) => app.setSettings({ months: Number(e.target.value) })}>
            {[1, 2, 3, 6, 9, 12].map((n) => <option key={n} value={n}>{n}</option>)}
          </select>
        </label>
        <label className="mc-field">{t('set.city')}
          <input list="mc-miasta" value={s.city} onChange={(e) => app.setSettings({ city: e.target.value })} autoComplete="off" />
          <datalist id="mc-miasta">{CITIES.slice(0, 120).map((c) => <option key={c.name} value={c.name} />)}</datalist>
          <span className="mc-hint">{znaneMiasto ? t('set.cityHint') : t('set.cityUnknown')}</span>
        </label>
        <label className="mc-field">{t('set.radius')}
          <select value={s.radius} disabled={!cityByName(miasto)} onChange={(e) => app.setSettings({ radius: Number(e.target.value) })}>
            {[0, 30, 50, 100, 200].map((km) => <option key={km} value={km}>{km === 0 ? t('list.radiusAll') : `${km} km`}</option>)}
          </select>
        </label>
      </div>

      <div className="mc-card mc-section">
        <h2>{t('set.look')}</h2>
        <label className="mc-field">{t('set.lang')}
          <select value={s.lang || (lang === 'en' ? 'en' : 'pl')} onChange={(e) => app.setSettings({ lang: e.target.value })}>
            <option value="pl">Polski</option><option value="en">English</option>
          </select>
        </label>
        <label className="mc-field">{t('set.theme')}
          <select value={motyw} onChange={(e) => ustawMotyw(e.target.value)}>
            <option value="auto">{t('set.theme.auto')}</option><option value="light">{t('set.theme.light')}</option><option value="dark">{t('set.theme.dark')}</option>
          </select>
        </label>
      </div>

      <div className="mc-card mc-section">
        <h2>{t('set.data')}</h2>
        <div className="mc-actions">
          <button type="button" className="mc-btn" onClick={eksport}>{t('set.export')}</button>
          <button type="button" className="mc-btn" onClick={() => plik.current?.click()}>{t('set.import')}</button>
          <input ref={plik} type="file" accept="application/json,.json" hidden onChange={import_} />
        </div>
        <p className="mc-hint">{t('set.exportNote')}</p>
        {komunikat && <p className="mc-ok" role="status">{komunikat}</p>}
        <button type="button" className="mc-btn mc-btn-danger" onClick={() => { if (window.confirm(t('set.wipeConfirm'))) app.wipe().then(() => go('/')); }}>{t('set.wipe')}</button>
      </div>

      <div className="mc-card mc-section">
        <h2>{t('set.about')}</h2>
        <p>{t('set.aboutText')}</p>
      </div>
    </section>
  );
}
