import { useId, useState } from 'react';
import { useT } from './i18n.js';
import { dateParts, money } from './format.js';
import { IkGwiazda, IkX, IkIde, IkPtaszek, IkLink, IkSchowek } from './ui/Ikony.jsx';
import { useToast } from './ui/Toast.jsx';
import { cleanKey, keyShape, checkKey } from './lib/keys.js';
import { czytajSchowek } from './schowek.js';
import { linkify } from './lib/linkify.js';

export function Star({ on, onClick, label }) {
  return (
    <button type="button" className={`mc-star${on ? ' on' : ''}`} aria-pressed={on} aria-label={label} title={label}
      onClick={(e) => { e.preventDefault(); e.stopPropagation(); onClick(); }}>
      <IkGwiazda size={22} wypelniona={on} key={on ? 'tak' : 'nie'} className="mc-star-ikona" />
    </button>
  );
}

export function StatusBadge({ status }) {
  const { t } = useT();
  if (!status || status === 'SCHEDULED') return null;
  const klucz = { CANCELLED: 'card.cancelled', POSTPONED: 'card.postponed', RESCHEDULED: 'card.rescheduled' }[status];
  return <span className="mc-badge mc-badge-warn">{t(klucz)}</span>;
}

export function Reasons({ reasons }) {
  const { t } = useT();
  return (
    <ul className="mc-reasons">
      {reasons.map((r, i) => (
        <li key={i} className={`mc-reason ${r.kind.toLowerCase()}`}>
          {r.kind === 'CALENDAR' ? t('reason.CALENDAR', r.value ? ` · ${r.value}` : '') : t(`reason.${r.kind}`, r.value)}
        </li>
      ))}
    </ul>
  );
}

// Obrazek, ktory pojawia sie plynnie dopiero, gdy sie wczyta (bez "skakania" i bez ikony zepsutego obrazka).
export function Obraz({ src, className, alt = '' }) {
  const [stan, setStan] = useState('laduje');
  if (!src || stan === 'blad') return <div className={`${className} mc-obraz-pusty`} aria-hidden="true" />;
  return (
    <img className={`${className} mc-obraz${stan === 'jest' ? ' jest' : ''}`} src={src} alt={alt} loading="lazy" decoding="async"
      referrerPolicy="no-referrer" onLoad={() => setStan('jest')} onError={() => setStan('blad')} />
  );
}

export function EventCard({ item, udzial, onToggleSave, index = 0 }) {
  const { t, lang } = useT();
  const { event: e, moreDates, match, km } = item;
  const d = dateParts(e.date, lang);
  const zrodlo = e.source === 'bit' ? t('card.source.bit') : e.source === 'ics' ? t('card.source.ics') : null;
  return (
    <li className={`mc-event mc-card mc-wejscie${e.music === false ? ' mc-event-inne' : ''}`} style={{ '--i': Math.min(index, 10) }}>
      <a className="mc-event-link" href={`#/e/${encodeURIComponent(e.id)}`}>
        <div className="mc-date" aria-hidden="true"><b>{d.day}</b><span>{d.month}</span></div>
        <Obraz className="mc-thumb" src={e.thumbUrl} />
        <div className="mc-event-body">
          <h2 className="mc-event-name">{e.name}</h2>
          <p className="mc-event-meta">
            <span className="mc-sr">{d.weekday}, {d.full}. </span>
            <span className="mc-trunc">{[e.venueName, e.city].filter(Boolean).join(' · ')}{e.time ? ` · ${e.time}` : ''}</span>
          </p>
          <div className="mc-chips">
            <StatusBadge status={e.status} />
            {udzial === 'GOING' && <span className="mc-badge mc-badge-ide">{t('part.going')}</span>}
            {zrodlo && <span className="mc-badge mc-badge-zrodlo">{zrodlo}</span>}
            {e.music === false && <span className="mc-badge mc-badge-inne">{t('kind.other')}</span>}
            {e.textGenres?.length > 0 && <span className="mc-badge">{e.textGenres.slice(0, 2).join(' · ')}</span>}
            {moreDates > 0 && <span className="mc-badge">{t('card.moreDates', moreDates)}</span>}
            {km != null && km >= 1 && <span className="mc-badge">{t('card.km', Math.round(km))}</span>}
            {e.organizer?.priceMin != null && <span className="mc-badge">{money(e.organizer.priceMin, e.organizer.priceMax, e.organizer.priceCurrency, lang)}</span>}
          </div>
          {match.reasons.length > 0 && <Reasons reasons={match.reasons} />}
        </div>
      </a>
      {udzial === 'GOING'
        ? <button type="button" className="mc-star on mc-star-ide" aria-pressed="true" aria-label={t('part.going')} title={t('part.going')}
          onClick={(ev) => { ev.preventDefault(); onToggleSave(); }}><IkIde size={24} wypelniona className="mc-star-ikona" /></button>
        : <Star on={!!udzial} onClick={onToggleSave} label={udzial ? t('unsave') : t('part.interested')} />}
    </li>
  );
}

export function SkeletonCards({ n = 4 }) {
  return (
    <ul className="mc-list" aria-hidden="true">
      {Array.from({ length: n }, (_, i) => (
        <li key={i} className="mc-card mc-szkielet" style={{ '--i': i }}><span /><span /><span /></li>
      ))}
    </ul>
  );
}

export function SyncBar({ app }) {
  const { t, lang } = useT();
  const { status, data } = app;
  let tekst;
  if (status.running) {
    tekst = status.step === 'tags' ? t('sync.tags', status.done, status.total || '…')
      : status.step === 'bit' ? t('sync.bit', status.done, status.total || '…')
        : status.step === 'calendars' ? t('sync.calendars') : t('sync.events', status.done, status.total || '…');
  } else if (status.error) tekst = bladTekst(status.error, t);
  else if (status.message === 'sync.done') tekst = t('sync.done', status.count ?? data.events.length);
  else if (status.message) tekst = t(status.message);
  else if (data.syncedAt) tekst = t('sync.last', new Date(data.syncedAt).toLocaleString(lang === 'en' ? 'en-GB' : 'pl-PL', { dateStyle: 'medium', timeStyle: 'short' }));
  else tekst = t('sync.never');
  const procent = status.running && status.total ? Math.min(100, Math.round((status.done / status.total) * 100)) : null;
  return (
    <div className={`mc-sync${status.error ? ' mc-sync-error' : ''}${status.running ? ' trwa' : ''}`} role="status">
      <span className="mc-sync-text" key={tekst.slice(0, 12)}>{tekst}</span>
      {status.running && <button type="button" className="mc-ikona-btn" onClick={app.stop} aria-label={t('sync.stop')} title={t('sync.stop')}><IkX size={18} /></button>}
      <div className={`mc-progress${status.running ? ' widoczny' : ''}${status.running && procent == null ? ' nieokreslony' : ''}`}>
        <div style={{ width: `${procent ?? 30}%` }} />
      </div>
    </div>
  );
}

export function bladTekst(err, t) {
  if (err.kod === 'KLUCZ_BRAK') return t('sync.needKey');
  if (err.kod === 'KLUCZ') return t('err.KLUCZ');
  if (err.kod === 'LIMIT') return t('err.LIMIT');
  if (err.kod === 'SIEC') return t('err.SIEC', err.tekst ?? '');
  return t('err.generic', err.tekst ?? '');
}

// Pole klucza API: ukryte, "Wklej" ze schowka, czyszczenie wklejonego tekstu, "Sprawdz" jednym zapytaniem
// i instrukcja z linkami prosto do stron, na ktorych klucz lezy.
const JAK = {
  tm: [['key.tm.1', 'https://developer.ticketmaster.com/'], ['key.tm.2', 'https://developer-account.ticketmaster.com/'], ['key.tm.3', null]],
  lfm: [['key.lfm.1', 'https://www.last.fm/api/accounts'], ['key.lfm.2', 'https://www.last.fm/api/account/create'], ['key.lfm.3', null]],
  bit: [['key.bit.1', 'https://help.artists.bandsintown.com/en/articles/9186477-api-documentation'], ['key.bit.2', null]],
};

export function KeyField({ rodzaj, label, value, onChange, blad }) {
  const { t } = useT();
  const toast = useToast();
  const [pokaz, setPokaz] = useState(false);
  const [jak, setJak] = useState(false);
  const [stan, setStan] = useState(null); // null | 'sprawdzam' | wynik checkKey
  const id = useId();
  const ksztalt = keyShape(rodzaj, value);

  const zmien = (v) => { onChange(cleanKey(v)); setStan(null); };
  async function wklej() {
    const tekst = await czytajSchowek();
    if (tekst == null || !tekst.trim()) { toast(t('key.pasteManual'), 'blad'); document.getElementById(id)?.focus(); return; }
    zmien(tekst);
    toast(t('key.pasted'));
  }
  async function sprawdz() {
    setStan('sprawdzam');
    setStan(await checkKey(rodzaj, value));
  }

  return (
    <div className="mc-field mc-klucz">
      <label htmlFor={id}>{label}</label>
      <span className={`mc-pole-z-przyciskiem${blad || stan === 'zly' ? ' blad' : ''}${stan === 'ok' ? ' dobry' : ''}`}>
        <input id={id} type={pokaz ? 'text' : 'password'} value={value} onChange={(e) => zmien(e.target.value)} autoComplete="off"
          autoCapitalize="off" spellCheck="false" aria-invalid={!!blad || stan === 'zly'} placeholder={t('key.placeholder')} />
        <button type="button" className="mc-pole-przycisk" onClick={() => setPokaz(!pokaz)}>{pokaz ? t('set.hide') : t('set.show')}</button>
      </span>
      <span className="mc-klucz-akcje">
        <button type="button" className="mc-btn mc-btn-small" onClick={wklej}><IkSchowek size={16} />{t('key.paste')}</button>
        <button type="button" className="mc-btn mc-btn-small" disabled={!value || stan === 'sprawdzam'} onClick={sprawdz}>
          {stan === 'sprawdzam' ? <span className="mc-spinner" aria-hidden="true" /> : <IkPtaszek size={16} />}{t('key.check')}
        </button>
        <button type="button" className="mc-link-btn" aria-expanded={jak} onClick={() => setJak(!jak)}>{t('key.how')}</button>
      </span>
      {stan && stan !== 'sprawdzam' && <span className={`mc-klucz-wynik ${stan} mc-wejscie`} role="status">{t(`key.result.${stan}`)}</span>}
      {!stan && ksztalt === 'secret' && <span className="mc-hint mc-uwaga mc-wejscie">{t('key.secret')}</span>}
      {!stan && ksztalt === 'dziwny' && <span className="mc-hint mc-uwaga mc-wejscie">{t(`key.shape.${rodzaj}`)}</span>}
      {blad && <span className="mc-error mc-wejscie" role="alert">{blad}</span>}
      <Rozwijane otwarte={jak}>
        <ol className="mc-klucz-kroki">
          {JAK[rodzaj].map(([klucz, url], i) => (
            <li key={klucz}>
              <span>{t(klucz)}</span>
              {url && <a className="mc-btn mc-btn-small" href={url} target="_blank" rel="noopener noreferrer"><IkLink size={14} />{t(`key.open.${i}`)}</a>}
            </li>))}
        </ol>
      </Rozwijane>
    </div>
  );
}

// Plynne rozwijanie (wysokosc od 0 do tresci - przez grid 0fr -> 1fr, bez mierzenia).
export function Rozwijane({ otwarte, children, className = '' }) {
  return (
    <div className={`mc-rozwijane${otwarte ? ' otwarte' : ''} ${className}`} aria-hidden={!otwarte}>
      <div className="mc-rozwijane-srodek">{children}</div>
    </div>
  );
}

// Zwykly tekst z adresami jako odnosnikami (krotki napis, pelny adres w podpowiedzi). Zadnego HTML-a z zewnatrz.
export function Tekst({ children, className = 'mc-prewrap' }) {
  return (
    <p className={className}>
      {linkify(children).map((c, i) => (c.url
        ? <a key={i} href={c.url} title={c.url} target="_blank" rel="noopener noreferrer nofollow" className="mc-link-w-tekscie">{c.label}</a>
        : <span key={i}>{c.text}</span>))}
    </p>
  );
}

// Dlugi opis zwiniety do kilku linii z "Pokaz calosc" - opisy z Facebooka potrafia miec kilka ekranow.
export function DlugiTekst({ children }) {
  const { t } = useT();
  const [caly, setCaly] = useState(false);
  const dlugi = (children?.length ?? 0) > 480 || (children?.split('\n').length ?? 0) > 9;
  return (
    <div className={`mc-dlugi${dlugi && !caly ? ' zwiniety' : ''}`}>
      <Tekst>{children}</Tekst>
      {dlugi && <button type="button" className="mc-link-btn" aria-expanded={caly} onClick={() => setCaly(!caly)}>{caly ? t('event.less') : t('event.more')}</button>}
    </div>
  );
}
