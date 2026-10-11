import { useMemo, useState } from 'react';
import { useT } from '../i18n.js';
import { match, datesOf } from '../lib/match.js';
import { nameKey } from '../lib/nameKeys.js';
import { artistInfo } from '../lib/lastfm.js';
import { dbGet, dbSet } from '../lib/store.js';
import { dateParts, today, money } from '../format.js';
import { Reasons, StatusBadge, Obraz, Rozwijane, Tekst, DlugiTekst } from '../components.jsx';
import Arkusz from '../ui/Arkusz.jsx';
import { useToast } from '../ui/Toast.jsx';
import { IkBilet, IkKalendarzPlus, IkMapa, IkTrasa, IkWstecz, IkLink, IkPlik } from '../ui/Ikony.jsx';
import { naAndroidzie, dodajNatywnie, otworzGoogle, pobierzIcs } from '../kalendarz.js';
import { statusOf, setStatus } from '../lib/udzial.js';
import { IkGwiazda, IkIde } from '../ui/Ikony.jsx';

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
      setStan(zapamietane ? { loading: false, info: zapamietane.info, error: null }
        : { loading: false, info: null, error: e?.kod === 'BRAK' ? 'none' : 'needKey' });
    }
  }

  const { info } = stan;
  return (
    <li className="mc-performer">
      <div className="mc-performer-head">
        <span className="mc-performer-name">{p.name}{liked && <span className="mc-badge mc-badge-liked">{t('event.liked')}</span>}</span>
        {gatunki.length > 0 && <span className="mc-genres">{gatunki.join(', ')}</span>}
        <button type="button" className={`mc-btn mc-btn-small mc-btn-rozwin${otwarty ? ' otwarty' : ''}`} aria-expanded={otwarty} onClick={przelacz}>
          {otwarty ? t('event.hide') : t('event.who')}
        </button>
      </div>
      {Object.keys(p.links ?? {}).length > 0 && (
        <p className="mc-links">{Object.entries(p.links).map(([rodzaj, url]) => (
          <a key={rodzaj} href={url} target="_blank" rel="noopener noreferrer nofollow">{ETYKIETY[rodzaj] ?? rodzaj}</a>))}</p>
      )}
      <Rozwijane otwarte={otwarty}>
        <div className="mc-bio" lang={lang}>
          {stan.loading && <p className="mc-ladowanie"><span className="mc-kropki" aria-hidden="true"><i /><i /><i /></span>{t('bio.loading')}</p>}
          {info?.bio && <Tekst>{info.bio}</Tekst>}
          {info && !info.bio && <p>{t('bio.none')}</p>}
          {info?.listeners && <p className="mc-hint">{t('bio.listeners', info.listeners.toLocaleString(lang === 'en' ? 'en-GB' : 'pl-PL'))}</p>}
          {info?.similar?.length > 0 && <p className="mc-hint">{t('bio.similar', info.similar.join(', '))}</p>}
          {info?.url && <p className="mc-hint"><a href={info.url} target="_blank" rel="noopener noreferrer nofollow">{t('bio.source')}</a></p>}
          {stan.error === 'needKey' && <p>{t('bio.needKey')}</p>}
          {stan.error === 'none' && <p>{t('bio.none')}</p>}
        </div>
      </Rozwijane>
    </li>
  );
}

function DoKalendarza({ e }) {
  const { t } = useT();
  const toast = useToast();
  const [arkusz, setArkusz] = useState(false);

  async function klik() {
    if (!naAndroidzie()) { setArkusz(true); return; }
    try {
      await dodajNatywnie(e, t('event.addCalTitle'));
    } catch {
      toast(t('event.noCalendarApp'), 'blad');
      setArkusz(true);
    }
  }

  return (
    <>
      <button type="button" className="mc-btn" onClick={klik}><IkKalendarzPlus size={18} />{t('event.addCal')}</button>
      <Arkusz otwarty={arkusz} onClose={() => setArkusz(false)} tytul={t('event.addCalTitle')} zamknij={t('common.close')}>
        <div className="mc-akcje-arkusza">
          <button type="button" className="mc-akcja" data-autofocus onClick={() => { otworzGoogle(e); setArkusz(false); }}>
            <span className="mc-akcja-ikona mc-google" aria-hidden="true">31</span>
            <span><b>{t('event.googleCal')}</b><small>{t('event.googleCalHint')}</small></span>
          </button>
          <button type="button" className="mc-akcja" onClick={() => { pobierzIcs(e).catch(() => {}); setArkusz(false); }}>
            <span className="mc-akcja-ikona" aria-hidden="true"><IkPlik /></span>
            <span><b>{t('event.icsFile')}</b></span>
          </button>
        </div>
      </Arkusz>
    </>
  );
}

export default function EventScreen({ app, id }) {
  const { t, lang } = useT();
  const toast = useToast();
  const dzis = today();
  const e = useMemo(() => app.events.find((x) => x.id === id || x.mergedIds?.includes(id)) ?? app.settings.saved[id] ?? null,
    [app.events, app.settings.saved, id]);
  const m = useMemo(() => (e ? match(app.taste, e, app.tags) : { score: 0, reasons: [] }), [e, app.taste, app.tags]);
  const inne = useMemo(() => (e ? datesOf(app.events, e, dzis).filter((x) => x.id !== e.id) : []), [e, app.events, dzis]);

  if (!e) return <section><a className="mc-back" href="#/"><IkWstecz size={18} />{t('event.back')}</a><p className="mc-empty">{t('event.notFound')}</p></section>;

  const d = dateParts(e.date, lang);
  const udzial = statusOf(e, app.settings);
  const ustawUdzial = (st) => {
    const nowy = udzial === st ? null : st;
    app.setSettings((s) => setStatus(s, e, nowy));
    toast(t(nowy === 'GOING' ? 'part.toastGoing' : nowy ? 'part.toastInterested' : 'part.toastNone'));
  };
  const polubieni = new Set([...app.taste.artists.keys()]);
  const o = e.organizer ?? {};
  const mapa = e.lat != null && e.lon != null ? `https://www.openstreetmap.org/?mlat=${e.lat}&mlon=${e.lon}#map=16/${e.lat}/${e.lon}` : null;
  const trasa = e.lat != null && e.lon != null ? `https://www.google.com/maps/dir/?api=1&destination=${e.lat},${e.lon}`
    : e.venueName && e.city ? `https://www.google.com/maps/dir/?api=1&destination=${encodeURIComponent(`${e.venueName}, ${e.city}`)}` : null;
  const zrodla = (e.sources ?? []).filter((z, i, a) => a.findIndex((y) => y.kind === z.kind && y.name === z.name) === i);
  const sprzedaz = o.salesStart && Date.parse(o.salesStart) > Date.now();

  return (
    <article className="mc-event-page">
      <a className="mc-back" href="#/"><IkWstecz size={18} />{t('event.back')}</a>
      {e.imageUrl && <Obraz className="mc-hero" src={e.imageUrl} />}
      <header className="mc-event-head">
        <div>
          <h1>{e.name}</h1>
          <p className="mc-when"><span className="mc-cap">{d.weekday}</span>, {d.full} · {e.time ? t('event.time', e.time) : t('event.noTime')}</p>
          <p className="mc-where">{[e.venueName, e.address, e.city].filter(Boolean).join(', ')}</p>
          <div className="mc-chips">
            <StatusBadge status={e.status} />
            {e.date < dzis && <span className="mc-badge mc-badge-warn">{t('event.past')}</span>}
            {(e.inCalendar || e.source === 'ics') && <span className="mc-badge mc-badge-liked">{t('event.inCalendar')}</span>}
          </div>
        </div>
      </header>

      <div className="mc-udzial" role="group" aria-label={t('tab.mine')}>
        <button type="button" className={`mc-udzial-btn${udzial === 'INTERESTED' ? ' wybrany' : ''}`} aria-pressed={udzial === 'INTERESTED'} onClick={() => ustawUdzial('INTERESTED')}>
          <IkGwiazda size={20} wypelniona={udzial === 'INTERESTED'} key={`z${udzial === 'INTERESTED'}`} />{t('part.interested')}
        </button>
        <button type="button" className={`mc-udzial-btn${udzial === 'GOING' ? ' wybrany' : ''}`} aria-pressed={udzial === 'GOING'} onClick={() => ustawUdzial('GOING')}>
          <IkIde size={20} wypelniona={udzial === 'GOING'} key={`i${udzial === 'GOING'}`} />{t('part.going')}
        </button>
      </div>

      <div className="mc-actions">
        {e.ticketUrl && <a className="mc-btn mc-btn-primary" href={e.ticketUrl} target="_blank" rel="noopener noreferrer"><IkBilet size={18} />{t('event.tickets')}</a>}
        <DoKalendarza e={e} />
        {mapa && <a className="mc-btn" href={mapa} target="_blank" rel="noopener noreferrer"><IkMapa size={18} />{t('event.map')}</a>}
        {trasa && <a className="mc-btn" href={trasa} target="_blank" rel="noopener noreferrer"><IkTrasa size={18} />{t('event.route')}</a>}
        {e.sourceUrl && !e.ticketUrl && <a className="mc-btn" href={e.sourceUrl} target="_blank" rel="noopener noreferrer"><IkLink size={18} />{t('event.openSource')}</a>}
      </div>

      {e.source === 'ics' && (
        <section className={`mc-card mc-section mc-rodzaj mc-wejscie${e.music ? '' : ' inne'}`} style={{ '--i': 0 }}>
          <div className="mc-rodzaj-wiersz">
            <span>
              <b>{e.music ? t('kind.music') : t('kind.notMusic')}</b>
              <small>{t(app.settings.kindOverride?.[e.id] ? 'kind.byYou' : 'kind.guess')}</small>
            </span>
            <button type="button" className="mc-btn mc-btn-small" onClick={() => {
              app.setSettings((s) => {
                const o = { ...(s.kindOverride ?? {}) };
                const nowy = e.music ? 'other' : 'music';
                if (nowy === (e.musicGuess ? 'music' : 'other')) delete o[e.id]; else o[e.id] = nowy;
                return { kindOverride: o };
              });
              toast(t(e.music ? 'kind.toastOther' : 'kind.toastMusic'));
            }}>{e.music ? t('kind.markOther') : t('kind.markMusic')}</button>
          </div>
          {e.textGenres?.length > 0 && (
            <p className="mc-gatunki-tekst">{t('kind.genres')} {e.textGenres.map((g) => <span key={g} className="mc-badge">{g}</span>)}</p>
          )}
        </section>
      )}

      {m.reasons.length > 0 && e.music !== false && (
        <section className="mc-card mc-section mc-wejscie" style={{ '--i': 1 }}><h2>{t('event.why')}</h2><Reasons reasons={m.reasons} /></section>
      )}

      {e.performers.length > 0 && (
        <section className="mc-card mc-section mc-wejscie" style={{ '--i': 2 }}>
          <h2>{t('event.lineup')}</h2>
          <ul className="mc-performers">{e.performers.map((p) => (
            <Performer key={p.id ?? p.name} p={p} app={app} liked={polubieni.has(nameKey(p.name))} />))}</ul>
        </section>
      )}

      {e.description && <section className="mc-card mc-section mc-wejscie" style={{ '--i': 3 }}><h2>{t('event.about')}</h2><DlugiTekst>{e.description}</DlugiTekst></section>}

      {(o.pleaseNote || o.promoter || o.priceMin != null || o.ageRestricted || sprzedaz || o.accessibility) && (
        <section className="mc-card mc-section mc-wejscie" style={{ '--i': 4 }}>
          <h2>{t('event.organizer')}</h2>
          {o.promoter && <p>{o.promoter}</p>}
          {o.priceMin != null && <p>{t('event.price', money(o.priceMin, o.priceMax, o.priceCurrency, lang))}</p>}
          {o.ageRestricted && <p>{t('event.age')}</p>}
          {sprzedaz && <p>{t('event.salesStart', new Date(o.salesStart).toLocaleString(lang === 'en' ? 'en-GB' : 'pl-PL', { dateStyle: 'medium', timeStyle: 'short' }))}</p>}
          {o.pleaseNote && <Tekst>{o.pleaseNote}</Tekst>}
          {o.accessibility && <Tekst>{o.accessibility}</Tekst>}
        </section>
      )}

      {inne.length > 0 && (
        <section className="mc-card mc-section mc-wejscie" style={{ '--i': 5 }}>
          <h2>{t('event.moreDates')}</h2>
          <ul className="mc-dates">{inne.map((x) => (
            <li key={x.id}><a href={`#/e/${encodeURIComponent(x.id)}`}>{dateParts(x.date, lang).full}{x.time ? ` · ${x.time}` : ''}</a></li>))}</ul>
        </section>
      )}

      {zrodla.length > 0 && (
        <p className="mc-hint mc-zrodla">{t('event.sources')}{zrodla.map((z, i) => (
          <span key={`${z.kind}${z.name}`}>{i > 0 && ' · '}{z.url ? <a href={z.url} target="_blank" rel="noopener noreferrer nofollow">{z.name}</a> : z.name}</span>))}</p>
      )}
    </article>
  );
}
