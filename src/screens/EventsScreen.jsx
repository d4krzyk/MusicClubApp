import { useMemo } from 'react';
import { useT } from '../i18n.js';
import { rank, emptyTaste } from '../lib/match.js';
import { today } from '../format.js';
import { EventCard, SyncBar } from '../components.jsx';

const PROMIENIE = [0, 30, 50, 100, 200];

export default function EventsScreen({ app, filtry, setFiltry }) {
  const { t } = useT();
  const { settings, data, tags, taste, origin } = app;
  const dzis = today();
  const promien = filtry.promien ?? settings.radius;
  const set = (zmiana) => setFiltry((f) => ({ ...f, ...zmiana }));

  const miasta = useMemo(() => {
    const m = new Map();
    for (const e of data.events) if (e.cityKey && !m.has(e.cityKey)) m.set(e.cityKey, e.city);
    return [...m.entries()].sort((a, b) => a[1].localeCompare(b[1], 'pl'));
  }, [data.events]);

  const lista = useMemo(() => {
    if (filtry.widok === 'SAVED') {
      const zapisane = Object.values(settings.saved)
        .map((snapshot) => data.events.find((e) => e.id === snapshot.id) ?? snapshot)
        .filter((e) => e.date >= dzis);
      return rank(zapisane, taste, tags, { view: 'UPCOMING', today: dzis, origin, phrase: filtry.fraza });
    }
    return rank(data.events, taste, tags, { view: filtry.widok, today: dzis, origin, radiusKm: filtry.miasto ? 0 : promien,
      cityKey: filtry.miasto, phrase: filtry.fraza });
  }, [data.events, settings.saved, taste, tags, origin, filtry, promien, dzis]);

  function przelaczZapis(e) {
    app.setSettings((s) => {
      const zapisane = { ...s.saved };
      if (zapisane[e.id]) delete zapisane[e.id]; else zapisane[e.id] = e;
      return { saved: zapisane };
    });
  }

  const widoki = [['FOR_YOU', 'tab.forYou'], ['UPCOMING', 'tab.upcoming'], ['SAVED', 'tab.saved']];
  const pusty = filtry.widok === 'SAVED' ? 'list.empty.saved' : !data.events.length ? 'list.noData' : filtry.widok === 'FOR_YOU' ? 'list.empty.forYou' : 'list.empty.upcoming';

  return (
    <section>
      <SyncBar app={app} />
      <div className="mc-tabs" role="tablist" aria-label="Widok">
        {widoki.map(([id, klucz]) => (
          <button key={id} type="button" role="tab" aria-selected={filtry.widok === id} className="mc-tab"
            onClick={() => set({ widok: id })}>
            {t(klucz)}{id === 'SAVED' && Object.keys(settings.saved).length ? ` (${Object.keys(settings.saved).length})` : ''}
          </button>
        ))}
      </div>
      <div className="mc-filters">
        <input type="search" className="mc-search" placeholder={t('list.search')} value={filtry.fraza}
          onChange={(e) => set({ fraza: e.target.value })} aria-label={t('list.search')} />
        {filtry.widok !== 'SAVED' && (
          <>
            <select value={filtry.miasto} onChange={(e) => set({ miasto: e.target.value })} aria-label={t('list.allCities')}>
              <option value="">{t('list.allCities')}</option>
              {miasta.map(([k, n]) => <option key={k} value={k}>{n}</option>)}
            </select>
            <select value={promien} disabled={!origin || !!filtry.miasto} aria-label={t('list.radius')}
              onChange={(e) => { const v = Number(e.target.value); set({ promien: v }); app.setSettings({ radius: v }); }}>
              {PROMIENIE.map((km) => <option key={km} value={km}>{km === 0 ? t('list.radiusAll') : t('list.radiusKm', km, settings.city)}</option>)}
            </select>
          </>
        )}
      </div>
      {!origin && filtry.widok !== 'SAVED' && <p className="mc-hint">{t('list.noCityForRadius')}</p>}
      {filtry.widok === 'FOR_YOU' && emptyTaste(taste) && <p className="mc-hint">{t('list.noTaste')}</p>}
      {lista.length === 0
        ? <p className="mc-empty">{t(pusty)}</p>
        : <ul className="mc-list">{lista.map((item, i) => (
          <EventCard key={item.event.id} item={item} index={i} saved={!!settings.saved[item.event.id]}
            onToggleSave={() => przelaczZapis(item.event)} />))}</ul>}
    </section>
  );
}
