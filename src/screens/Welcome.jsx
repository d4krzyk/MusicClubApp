import { useState } from 'react';
import { useT } from '../i18n.js';
import NapisMC from '../NapisMC.jsx';
import { KeyField } from '../components.jsx';

export default function Welcome({ app, onDone }) {
  const { t } = useT();
  const [tm, setTm] = useState(app.settings.tmKey);
  const [lfm, setLfm] = useState(app.settings.lfmKey);
  const [user, setUser] = useState(app.settings.lfmUser);
  const [blad, setBlad] = useState(null);

  function start(e) {
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
    <form className="mc-welcome" onSubmit={start} noValidate>
      <div className="mc-welcome-hero">
        <NapisMC uklad="pionowy" className="mc-welcome-napis" aria-hidden="true" />
        <h1>{t('welcome.title')}</h1>
        <p className="mc-lead">{t('welcome.lead')}</p>
      </div>
      <ol className="mc-steps">
        <li style={{ '--i': 1 }}><a href="https://developer.ticketmaster.com/" target="_blank" rel="noopener noreferrer">{t('welcome.step1')}</a></li>
        <li style={{ '--i': 2 }}><a href="https://www.last.fm/api/account/create" target="_blank" rel="noopener noreferrer">{t('welcome.step2')}</a></li>
        <li style={{ '--i': 3 }}>{t('welcome.step3')}</li>
      </ol>
      <div className="mc-card mc-section mc-welcome-pola">
        <KeyField label={t('set.tmKey')} value={tm} onChange={(v) => { setTm(v); setBlad(null); }} blad={blad} />
        <KeyField label={t('set.lfmKey')} value={lfm} onChange={setLfm} />
        <label className="mc-field">{t('taste.lfmUser')}
          <input value={user} onChange={(e) => setUser(e.target.value)} autoComplete="off" autoCapitalize="off" spellCheck="false" />
        </label>
        <p className="mc-hint">{t('welcome.optional')}</p>
        <button className="mc-btn mc-btn-primary mc-btn-duzy" type="submit">{t('welcome.start')}</button>
      </div>
    </form>
  );
}
