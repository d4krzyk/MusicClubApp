import { useMemo, useState } from 'react';
import { useT } from '../i18n.js';
import { match, datesOf } from '../lib/match.js';
import { nameKey } from '../lib/nameKeys.js';
import { artistInfo } from '../lib/lastfm.js';
import { dbGet, dbSet } from '../lib/store.js';
import { dateParts, today, money, ics } from '../format.js';
import { Reasons, StatusBadge, Star } from '../components.jsx';
import { saveFile } from '../saveFile.js';

const ETYKIETY = { WEBSITE: 'WWW', SPOTIFY: 'Spotify', YOUTUBE: 'YouTube', INSTAGRAM: 'Instagram', FACEBOOK: 'Facebook',
  TIKTOK: 'TikTok', BANDCAMP: 'Bandcamp', SOUNDCLOUD: 'SoundCloud', WIKIPEDIA: 'Wikipedia', MUSICBRAINZ: 'MusicBrainz',
  TWITTER: 'X / Twitter', ITUNES: 'Apple Music' };
const BIO_WAZNOSC = 30 * 24 * 3600 * 1000;

function Performer({ p, liked, app }) {
  const { t, lang } = useT();
  const [otwarty, setOtwarty] = useState(false);
  const [stan, setStan] = useState({ loading: false, info: null, error: null });
  const gatunki = (app.tags[nameKey(p.name)] ?? []).slice(0, 3);

  async function przelacz() {
    const nowy = !otwarty;
    setOtwarty(nowy);
    if (!nowy || stan.info || stan.loading) return;
    const key = app.settings.lfmKey.trim();
    const klucz = `bio:${lang}:${nameKey(p.name)}`;
    const zapamietane = await dbGet(klucz);
    if (zapamietane && Date.now() - zapamietane.at < BIO_WAZNOSC) { setStan({ loading: false, info: zapamietane.info, error: null }); return; }
    if (!key) { setStan({ loading: false, info: null, error: 'needKey' }); return; }
    setStan({ loading: true, info: null, error: null });
    try {
      const info = await artistInfo(p.name, lang, { key });
      await dbSet(klucz, { at: Date.now(), info });
      setStan({ loading: false, info, error: null });
    } catch (e) {
      setStan({ loading: false, info: null, error: zapamietane ? null : (e?.kod === 'BRAK' ? 'none' : 'needKey'), ...(zapamietane ? { info: zapamietane.info } : {}) });
    }
  }

  const { info } = stan;
  return (
    <li className="mc-performer">
      <div className="mc-performer-head">
        <span className="mc-performer-name">{p.name}{liked && <span className="mc-badge mc-badge-liked">{t('event.liked')}</span>}</span>
        {gatunki.length > 0 && <span className="mc-genres">{gatunki.join(', ')}</span>}
        <button type="button" className="mc-btn mc-btn-small" aria-expanded={otwarty} onClick={przelacz}>
          {otwarty ? t('event.hide') : t('event.who')}
        </button>
      </div>
      {Object.keys(p.links).length > 0 && (
        <p className="mc-links">{Object.entries(p.links).map(([rodzaj, url]) => (
          <a key={rodzaj} href={url} target="_blank" rel="noopener noreferrer nofollow">{ETYKIETY[rodzaj] ?? rodzaj}</a>))}</p>
      )}
      {otwarty && (
        <div className="mc-bio" lang={lang}>
          {stan.loading && <p>{t('bio.loading')}</p>}
          {info?.bio && <p className="mc-prewrap">{info.bio}</p>}
          {info && !info.bio && <p>{t('bio.none')}</p>}
          {info?.listeners && <p className="mc-hint">{t('bio.listeners', info.listeners.toLocaleString(lang === 'en' ? 'en-GB' : 'pl-PL'))}</p>}
          {info?.similar?.length > 0 && <p className="mc-hint">{t('bio.similar', info.similar.join(', '))}</p>}
          {info?.url && <p className="mc-hint"><a href={info.url} target="_blank" rel="noopener noreferrer nofollow">{t('bio.source')}</a></p>}
          {stan.error === 'needKey' && <p>{t('bio.needKey')}</p>}
          {stan.error === 'none' && <p>{t('bio.none')}</p>}
        </div>
      )}
    </li>
  );
}

function pobierzIcs(e) {
  const nazwa = `${e.name.replace(/[^\p{L}\p{N}]+/gu, '-').slice(0, 40)}.ics`;
  return saveFile(nazwa, 'text/calendar', ics(e)).catch(() => { /* okno udostepniania zamkniete */ });
}

export default function EventScreen({ app, id }) {
  const { t, lang } = useT();
  const dzis = today();
  const e = useMemo(() => app.data.events.find((x) => x.id === id) ?? app.settings.saved[id] ?? null, [app.data.events, app.settings.saved, id]);
  const m = useMemo(() => (e ? match(app.taste, e, app.tags) : { score: 0, reasons: [] }), [e, app.taste, app.tags]);
  const inne = useMemo(() => (e ? datesOf(app.data.events, e, dzis).filter((x) => x.id !== e.id) : []), [e, app.data.events, dzis]);

  if (!e) return <section><a className="mc-back" href="#/">← {t('event.back')}</a><p className="mc-empty">{t('event.notFound')}</p></section>;

  const d = dateParts(e.date, lang);
  const zapisany = !!app.settings.saved[e.id];
  const polubieni = new Set([...app.taste.artists.keys()]);
  const o = e.organizer ?? {};
  const mapa = e.lat != null && e.lon != null ? `https://www.openstreetmap.org/?mlat=${e.lat}&mlon=${e.lon}#map=16/${e.lat}/${e.lon}` : null;
  const trasa = e.lat != null && e.lon != null ? `https://www.google.com/maps/dir/?api=1&destination=${e.lat},${e.lon}` : null;

  return (
    <article className="mc-event-page">
      <a className="mc-back" href="#/">← {t('event.back')}</a>
      {e.imageUrl && <img className="mc-hero" src={e.imageUrl} alt="" referrerPolicy="no-referrer" />}
      <header className="mc-event-head">
        <div>
          <h1>{e.name}</h1>
          <p className="mc-when"><span className="mc-cap">{d.weekday}</span>, {d.full} · {e.time ? t('event.time', e.time) : t('event.noTime')}</p>
          <p className="mc-where">{[e.venueName, e.address, e.city].filter(Boolean).join(', ')}</p>
          <div className="mc-chips"><StatusBadge status={e.status} />{e.date < dzis && <span className="mc-badge mc-badge-warn">{t('event.past')}</span>}</div>
        </div>
        <Star on={zapisany} label={zapisany ? t('unsave') : t('save')}
          onClick={() => app.setSettings((s) => { const z = { ...s.saved }; if (z[e.id]) delete z[e.id]; else z[e.id] = e; return { saved: z }; })} />
      </header>

      <div className="mc-actions">
        {e.ticketUrl && <a className="mc-btn mc-btn-primary" href={e.ticketUrl} target="_blank" rel="noopener noreferrer">{t('event.tickets')}</a>}
        {mapa && <a className="mc-btn" href={mapa} target="_blank" rel="noopener noreferrer">{t('event.map')}</a>}
        {trasa && <a className="mc-btn" href={trasa} target="_blank" rel="noopener noreferrer">{t('event.route')}</a>}
        <button type="button" className="mc-btn" onClick={() => pobierzIcs(e)}>{t('event.calendar')}</button>
      </div>

      {m.reasons.length > 0 && (
        <section className="mc-card mc-section"><h2>{t('event.why')}</h2><Reasons reasons={m.reasons} /></section>
      )}

      {e.performers.length > 0 && (
        <section className="mc-card mc-section">
          <h2>{t('event.lineup')}</h2>
          <ul className="mc-performers">{e.performers.map((p) => (
            <Performer key={p.id ?? p.name} p={p} app={app} liked={polubieni.has(nameKey(p.name))} />))}</ul>
        </section>
      )}

      {e.description && <section className="mc-card mc-section"><h2>{t('event.about')}</h2><p className="mc-prewrap">{e.description}</p></section>}

      {(o.pleaseNote || o.promoter || o.priceMin != null || o.ageRestricted || (o.salesStart && Date.parse(o.salesStart) > Date.now()) || o.accessibility) && (
        <section className="mc-card mc-section">
          <h2>{t('event.organizer')}</h2>
          {o.promoter && <p>{o.promoter}</p>}
          {o.priceMin != null && <p>{t('event.price', money(o.priceMin, o.priceMax, o.priceCurrency, lang))}</p>}
          {o.ageRestricted && <p>{t('event.age')}</p>}
          {o.salesStart && Date.parse(o.salesStart) > Date.now() && (
            <p>{t('event.salesStart', new Date(o.salesStart).toLocaleString(lang === 'en' ? 'en-GB' : 'pl-PL', { dateStyle: 'medium', timeStyle: 'short' }))}</p>)}
          {o.pleaseNote && <p className="mc-prewrap">{o.pleaseNote}</p>}
          {o.accessibility && <p className="mc-prewrap">{o.accessibility}</p>}
        </section>
      )}

      {inne.length > 0 && (
        <section className="mc-card mc-section">
          <h2>{t('event.moreDates')}</h2>
          <ul className="mc-dates">{inne.map((x) => (
            <li key={x.id}><a href={`#/e/${encodeURIComponent(x.id)}`}>{dateParts(x.date, lang).full}{x.time ? ` · ${x.time}` : ''}</a></li>))}</ul>
        </section>
      )}
    </article>
  );
}
