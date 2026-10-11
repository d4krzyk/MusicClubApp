import { useId, useState } from 'react';
import { useT } from './i18n.js';
import { dateParts, money } from './format.js';
import { IkGwiazda, IkOdswiez, IkX } from './ui/Ikony.jsx';
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

export function EventCard({ item, saved, onToggleSave, index = 0 }) {
  const { t, lang } = useT();
  const { event: e, moreDates, match, km } = item;
  const d = dateParts(e.date, lang);
  const zrodlo = e.source === 'bit' ? t('card.source.bit') : e.source === 'ics' ? t('card.source.ics') : null;
  return (
    <li className="mc-event mc-card mc-wejscie" style={{ '--i': Math.min(index, 10) }}>
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
            {zrodlo && <span className="mc-badge mc-badge-zrodlo">{zrodlo}</span>}
            {moreDates > 0 && <span className="mc-badge">{t('card.moreDates', moreDates)}</span>}
            {km != null && km >= 1 && <span className="mc-badge">{t('card.km', Math.round(km))}</span>}
            {e.organizer?.priceMin != null && <span className="mc-badge">{money(e.organizer.priceMin, e.organizer.priceMax, e.organizer.priceCurrency, lang)}</span>}
          </div>
          {match.reasons.length > 0 && <Reasons reasons={match.reasons} />}
        </div>
      </a>
      <Star on={saved} onClick={onToggleSave} label={saved ? t('unsave') : t('save')} />
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
      {status.running
        ? <button type="button" className="mc-ikona-btn" onClick={app.stop} aria-label={t('sync.stop')} title={t('sync.stop')}><IkX size={18} /></button>
        : <button type="button" className="mc-ikona-btn" onClick={() => app.refresh()} aria-label={t('sync.refresh')} title={t('sync.refresh')}><IkOdswiez size={18} /></button>}
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

export function KeyField({ label, help, helpUrl, value, onChange, blad }) {
  const { t } = useT();
  const [pokaz, setPokaz] = useState(false);
  const id = useId();
  return (
    <div className="mc-field">
      <label htmlFor={id}>{label}</label>
      <span className={`mc-pole-z-przyciskiem${blad ? ' blad' : ''}`}>
        <input id={id} type={pokaz ? 'text' : 'password'} value={value} onChange={(e) => onChange(e.target.value)} autoComplete="off"
          autoCapitalize="off" spellCheck="false" aria-invalid={!!blad} />
        <button type="button" className="mc-pole-przycisk" onClick={() => setPokaz(!pokaz)}>{pokaz ? t('set.hide') : t('set.show')}</button>
      </span>
      {blad && <span className="mc-error mc-wejscie" role="alert">{blad}</span>}
      {help && <a className="mc-hint" href={helpUrl} target="_blank" rel="noopener noreferrer">{help}</a>}
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
