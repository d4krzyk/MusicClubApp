import { useT } from './i18n.js';
import { dateParts, money } from './format.js';

export function Star({ on, onClick, label }) {
  return (
    <button type="button" className={`mc-star${on ? ' on' : ''}`} aria-pressed={on} aria-label={label} title={label}
      onClick={(e) => { e.preventDefault(); e.stopPropagation(); onClick(); }}>
      <svg viewBox="0 0 24 24" width="22" height="22" aria-hidden="true">
        <path d="M12 3.2l2.7 5.5 6.1.9-4.4 4.3 1 6.1L12 17.1 6.6 20l1-6.1L3.2 9.6l6.1-.9z"
          fill={on ? 'currentColor' : 'none'} stroke="currentColor" strokeWidth="1.8" strokeLinejoin="round" />
      </svg>
    </button>
  );
}

export function StatusBadge({ status }) {
  const { t } = useT();
  if (status === 'SCHEDULED') return null;
  const klucz = { CANCELLED: 'card.cancelled', POSTPONED: 'card.postponed', RESCHEDULED: 'card.rescheduled' }[status];
  return <span className="mc-badge mc-badge-warn">{t(klucz)}</span>;
}

export function Reasons({ reasons }) {
  const { t } = useT();
  return (
    <ul className="mc-reasons">
      {reasons.map((r, i) => <li key={i} className={`mc-reason ${r.kind.toLowerCase()}`}>{t(`reason.${r.kind}`, r.value)}</li>)}
    </ul>
  );
}

export function EventCard({ item, saved, onToggleSave, index = 0 }) {
  const { t, lang } = useT();
  const { event: e, moreDates, match, km } = item;
  const d = dateParts(e.date, lang);
  return (
    <li className="mc-event mc-card" style={{ '--i': Math.min(index, 10) }}>
      <a className="mc-event-link" href={`#/e/${encodeURIComponent(e.id)}`}>
        <div className="mc-date" aria-hidden="true"><b>{d.day}</b><span>{d.month}</span></div>
        {e.thumbUrl ? <img className="mc-thumb" src={e.thumbUrl} alt="" loading="lazy" referrerPolicy="no-referrer" /> : <div className="mc-thumb mc-thumb-empty" />}
        <div className="mc-event-body">
          <h2 className="mc-event-name">{e.name}</h2>
          <p className="mc-event-meta">
            <span className="mc-sr">{d.weekday}, {d.full}. </span>
            <span className="mc-trunc">{[e.venueName, e.city].filter(Boolean).join(' · ')}{e.time ? ` · ${e.time}` : ''}</span>
          </p>
          <div className="mc-chips">
            <StatusBadge status={e.status} />
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

export function SyncBar({ app }) {
  const { t, lang } = useT();
  const { status, data } = app;
  let tekst;
  if (status.running) tekst = status.step === 'tags' ? t('sync.tags', status.done, status.total) : t('sync.events', status.done, status.total || '…');
  else if (status.error) tekst = bladTekst(status.error, t);
  else if (status.message === 'sync.done') tekst = t('sync.done', status.count ?? data.events.length);
  else if (status.message) tekst = t(status.message);
  else if (data.syncedAt) tekst = t('sync.last', new Date(data.syncedAt).toLocaleString(lang === 'en' ? 'en-GB' : 'pl-PL', { dateStyle: 'medium', timeStyle: 'short' }));
  else tekst = t('sync.never');
  const procent = status.running && status.total ? Math.min(100, Math.round((status.done / status.total) * 100)) : null;
  return (
    <div className={`mc-sync${status.error ? ' mc-sync-error' : ''}`} role="status">
      <span className="mc-sync-text">{tekst}</span>
      {status.running
        ? <button type="button" className="mc-btn mc-btn-small" onClick={app.stop}>{t('sync.stop')}</button>
        : <button type="button" className="mc-btn mc-btn-small" onClick={() => app.refresh()}>{t('sync.refresh')}</button>}
      {procent != null && <div className="mc-progress"><div style={{ width: `${procent}%` }} /></div>}
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

