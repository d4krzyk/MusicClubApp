import { useState } from 'react';
import { useT } from '../i18n.js';
import Logo from '../Logo.jsx';

export default function Welcome({ app, onDone }) {
  const { t } = useT();
  const [tm, setTm] = useState(app.settings.tmKey);
  const [lfm, setLfm] = useState(app.settings.lfmKey);
  const [user, setUser] = useState(app.settings.lfmUser);
  const [blad, setBlad] = useState(null);

  async function start(e) {
    e.preventDefault();
    if (!tm.trim()) { setBlad(t('welcome.needTm')); return; }
    app.setSettings({ tmKey: tm.trim(), lfmKey: lfm.trim(), lfmUser: user.trim() });
    onDone();
    // import artystow z Last.fm i pobranie koncertow ida w tle - ekran koncertow pokazuje postep
    setTimeout(async () => {
      if (lfm.trim() && user.trim()) { try { await app.importArtists(); } catch { /* blad pokaze ekran gustu */ } }
      app.refresh();
    }, 0);
  }

  return (
    <form className="mc-welcome mc-card" onSubmit={start}>
      <Logo size={64} />
      <h1>{t('welcome.title')}</h1>
      <p className="mc-lead">{t('welcome.lead')}</p>
      <ol className="mc-steps">
        <li><a href="https://developer.ticketmaster.com/" target="_blank" rel="noopener noreferrer">{t('welcome.step1')}</a></li>
        <li><a href="https://www.last.fm/api/account/create" target="_blank" rel="noopener noreferrer">{t('welcome.step2')}</a></li>
        <li>{t('welcome.step3')}</li>
      </ol>
      <label className="mc-field">{t('set.tmKey')}
        <input value={tm} onChange={(e) => setTm(e.target.value)} autoComplete="off" autoCapitalize="off" spellCheck="false" />
      </label>
      <label className="mc-field">{t('set.lfmKey')}
        <input value={lfm} onChange={(e) => setLfm(e.target.value)} autoComplete="off" autoCapitalize="off" spellCheck="false" />
      </label>
      <label className="mc-field">{t('taste.lfmUser')}
        <input value={user} onChange={(e) => setUser(e.target.value)} autoComplete="off" autoCapitalize="off" spellCheck="false" />
      </label>
      <p className="mc-hint">{t('welcome.optional')}</p>
      {blad && <p className="mc-error" role="alert">{blad}</p>}
      <button className="mc-btn mc-btn-primary" type="submit">{t('welcome.start')}</button>
    </form>
  );
}
