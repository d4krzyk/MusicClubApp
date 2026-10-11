import { useMemo, useState } from 'react';
import { useT } from '../i18n.js';
import { nameKey } from '../lib/nameKeys.js';
import { bladTekst } from '../components.jsx';

export default function TasteScreen({ app }) {
  const { t } = useT();
  const { settings, taste } = app;
  const [nowy, setNowy] = useState('');
  const [komunikat, setKomunikat] = useState(null);
  const [laduje, setLaduje] = useState(false);

  const genres = useMemo(() => [...taste.genres.values()].sort((a, b) => b.count - a.count).slice(0, 12), [taste.genres]);
  const lfmKeys = useMemo(() => new Set(settings.lfmArtists.map(nameKey)), [settings.lfmArtists]);

  function dodaj(e) {
    e.preventDefault();
    const nazwa = nowy.trim();
    if (!nazwa) return;
    if (!app.artistNames.some((a) => nameKey(a) === nameKey(nazwa))) {
      app.setSettings((s) => ({ manualArtists: [nazwa, ...s.manualArtists] }));
      setTimeout(() => app.refreshTags(), 0);
    }
    setNowy('');
  }

  function usun(nazwa) {
    const k = nameKey(nazwa);
    app.setSettings((s) => ({ manualArtists: s.manualArtists.filter((a) => nameKey(a) !== k), lfmArtists: s.lfmArtists.filter((a) => nameKey(a) !== k) }));
  }

  async function importuj() {
    setKomunikat(null);
    if (!settings.lfmKey.trim()) { setKomunikat({ blad: t('taste.needLfmKey') }); return; }
    setLaduje(true);
    try {
      const n = await app.importArtists();
      setKomunikat({ ok: t('taste.imported', n) });
      setTimeout(() => app.refreshTags(), 0);
    } catch (e) {
      setKomunikat({ blad: bladTekst({ kod: e?.kod ?? 'ERR', tekst: e?.message }, t) });
    } finally {
      setLaduje(false);
    }
  }

  return (
    <section className="mc-taste">
      <h1>{t('taste.title')}</h1>
      <p className="mc-lead">{t('taste.intro')}</p>

      <div className="mc-card mc-section">
        <h2>{t('taste.lastfm')}</h2>
        <label className="mc-field">{t('taste.lfmUser')}
          <input value={settings.lfmUser} onChange={(e) => app.setSettings({ lfmUser: e.target.value })} autoCapitalize="off" autoComplete="off" spellCheck="false" />
        </label>
        <label className="mc-field">{t('taste.lfmCount')}
          <select value={settings.lfmCount} onChange={(e) => app.setSettings({ lfmCount: Number(e.target.value) })}>
            {[20, 50, 100, 200].map((n) => <option key={n} value={n}>{n}</option>)}
          </select>
        </label>
        <button type="button" className="mc-btn mc-btn-primary" disabled={laduje || !settings.lfmUser.trim()} onClick={importuj}>{t('taste.import')}</button>
        {komunikat?.ok && <p className="mc-ok" role="status">{komunikat.ok}</p>}
        {komunikat?.blad && <p className="mc-error" role="alert">{komunikat.blad}</p>}
      </div>

      <div className="mc-card mc-section">
        <h2>{t('taste.manual')}</h2>
        <form className="mc-inline" onSubmit={dodaj}>
          <input value={nowy} onChange={(e) => setNowy(e.target.value)} aria-label={t('taste.manual')} maxLength={100} />
          <button type="submit" className="mc-btn mc-btn-primary" disabled={!nowy.trim()}>{t('taste.add')}</button>
        </form>
      </div>

      <div className="mc-card mc-section">
        <h2>{t('taste.genres')}</h2>
        {genres.length ? (
          <ul className="mc-tagcloud">{genres.map((g) => <li key={g.display} className="mc-badge">{g.display} · {g.count}</li>)}</ul>
        ) : <p className="mc-hint">{t('taste.noGenres')}</p>}
        {genres.length > 0 && <p className="mc-hint">{t('taste.genresHint')}</p>}
      </div>

      <div className="mc-card mc-section">
        <h2>{t('taste.artists', app.artistNames.length)}</h2>
        {app.artistNames.length === 0 ? <p className="mc-hint">{t('taste.noArtists')}</p> : (
          <ul className="mc-artists">{app.artistNames.map((a) => (
            <li key={nameKey(a)}>
              <span className="mc-trunc">{a}</span>
              {lfmKeys.has(nameKey(a)) && <span className="mc-hint">{t('taste.fromLastfm')}</span>}
              <button type="button" className="mc-x" aria-label={t('taste.remove', a)} title={t('taste.remove', a)} onClick={() => usun(a)}>×</button>
            </li>))}</ul>
        )}
        {settings.lfmArtists.length > 0 && (
          <button type="button" className="mc-btn mc-btn-small" onClick={() => app.setSettings({ lfmArtists: [] })}>{t('taste.clearImported')}</button>)}
      </div>
    </section>
  );
}
