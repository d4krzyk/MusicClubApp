import { useMemo, useState } from 'react';
import { useT } from '../i18n.js';
import { nameKey } from '../lib/nameKeys.js';
import { bladTekst } from '../components.jsx';
import Przelacznik from '../ui/Przelacznik.jsx';
import { useToast } from '../ui/Toast.jsx';
import { IkPlus, IkX } from '../ui/Ikony.jsx';

export default function TasteScreen({ app }) {
  const { t } = useT();
  const toast = useToast();
  const { settings, taste } = app;
  const [nowy, setNowy] = useState('');
  const [blad, setBlad] = useState(null);
  const [laduje, setLaduje] = useState(false);

  const genres = useMemo(() => [...taste.genres.values()].sort((a, b) => b.count - a.count).slice(0, 14), [taste.genres]);
  const lfmKeys = useMemo(() => new Set(settings.lfmArtists.map(nameKey)), [settings.lfmArtists]);
  const najwiecej = genres[0]?.count ?? 1;

  function dodaj(e) {
    e.preventDefault();
    const nazwa = nowy.trim();
    if (!nazwa) return;
    if (!app.artistNames.some((a) => nameKey(a) === nameKey(nazwa))) {
      app.setSettings((s) => ({ manualArtists: [nazwa, ...s.manualArtists] }));
      setTimeout(() => app.refreshTags(), 0);
      toast(`+ ${nazwa}`);
    }
    setNowy('');
  }

  function usun(nazwa) {
    const k = nameKey(nazwa);
    app.setSettings((s) => ({ manualArtists: s.manualArtists.filter((a) => nameKey(a) !== k), lfmArtists: s.lfmArtists.filter((a) => nameKey(a) !== k) }));
  }

  async function importuj() {
    setBlad(null);
    if (!settings.lfmKey.trim()) { setBlad(t('taste.needLfmKey')); return; }
    setLaduje(true);
    try {
      const n = await app.importArtists();
      toast(t('taste.imported', n));
      setTimeout(() => app.refreshTags(), 0);
    } catch (e) {
      setBlad(bladTekst({ kod: e?.kod ?? 'ERR', tekst: e?.message }, t));
    } finally {
      setLaduje(false);
    }
  }

  return (
    <section className="mc-taste">
      <h1>{t('taste.title')}</h1>
      <p className="mc-lead">{t('taste.intro')}</p>

      <div className="mc-card mc-section mc-wejscie" style={{ '--i': 0 }}>
        <h2>{t('taste.lastfm')}</h2>
        <label className="mc-field">{t('taste.lfmUser')}
          <input value={settings.lfmUser} onChange={(e) => app.setSettings({ lfmUser: e.target.value })} autoCapitalize="off" autoComplete="off" spellCheck="false" />
        </label>
        <div className="mc-field">
          <span>{t('taste.lfmCount')}</span>
          <Przelacznik opcje={[20, 50, 100, 200].map((n) => ({ value: n, label: String(n) }))} wartosc={settings.lfmCount}
            onChange={(v) => app.setSettings({ lfmCount: v })} etykieta={t('taste.lfmCount')} />
        </div>
        <button type="button" className={`mc-btn mc-btn-primary${laduje ? ' laduje' : ''}`} disabled={laduje || !settings.lfmUser.trim()} onClick={importuj}>
          {laduje && <span className="mc-spinner" aria-hidden="true" />}{t('taste.import')}
        </button>
        {blad && <p className="mc-error mc-wejscie" role="alert">{blad}</p>}
      </div>

      <div className="mc-card mc-section mc-wejscie" style={{ '--i': 1 }}>
        <h2>{t('taste.manual')}</h2>
        <form className="mc-inline" onSubmit={dodaj}>
          <input value={nowy} onChange={(e) => setNowy(e.target.value)} aria-label={t('taste.manual')} maxLength={100} />
          <button type="submit" className="mc-btn mc-btn-primary mc-btn-kwadrat" disabled={!nowy.trim()} aria-label={t('taste.add')}><IkPlus /></button>
        </form>
      </div>

      <div className="mc-card mc-section mc-wejscie" style={{ '--i': 2 }}>
        <h2>{t('taste.genres')}</h2>
        {genres.length ? (
          <ul className="mc-gatunki">{genres.map((g, i) => (
            <li key={g.display} style={{ '--i': i, '--w': `${Math.max(12, (g.count / najwiecej) * 100)}%` }}>
              <span className="mc-gatunek-nazwa">{g.display}</span><span className="mc-gatunek-pasek" /><span className="mc-gatunek-ile">{g.count}</span>
            </li>))}</ul>
        ) : <p className="mc-hint">{t('taste.noGenres')}</p>}
        {genres.length > 0 && <p className="mc-hint">{t('taste.genresHint')}</p>}
      </div>

      <div className="mc-card mc-section mc-wejscie" style={{ '--i': 3 }}>
        <h2>{t('taste.artists', app.artistNames.length)}</h2>
        {app.artistNames.length === 0 ? <p className="mc-hint">{t('taste.noArtists')}</p> : (
          <ul className="mc-artysci">{app.artistNames.map((a, i) => (
            <li key={nameKey(a)} className={`mc-artysta${lfmKeys.has(nameKey(a)) ? ' z-lastfm' : ''}`} style={{ '--i': Math.min(i, 20) }}
              title={lfmKeys.has(nameKey(a)) ? t('taste.fromLastfm') : undefined}>
              <span className="mc-trunc">{a}</span>
              <button type="button" className="mc-x" aria-label={t('taste.remove', a)} onClick={() => usun(a)}><IkX size={14} /></button>
            </li>))}</ul>
        )}
        {settings.lfmArtists.length > 0 && (
          <button type="button" className="mc-btn mc-btn-small mc-odstep" onClick={() => app.setSettings({ lfmArtists: [] })}>{t('taste.clearImported')}</button>)}
      </div>
    </section>
  );
}
