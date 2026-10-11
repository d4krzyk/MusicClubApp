import { useMemo, useState } from 'react';
import { useT } from '../i18n.js';
import { rank, emptyTaste } from '../lib/match.js';
import { today } from '../format.js';
import { EventCard, SyncBar, SkeletonCards } from '../components.jsx';
import Przelacznik from '../ui/Przelacznik.jsx';
import Suwak from '../ui/Suwak.jsx';
import Wybor from '../ui/Wybor.jsx';
import Arkusz from '../ui/Arkusz.jsx';
import { useToast } from '../ui/Toast.jsx';
import { IkPinezka, IkSzukaj, IkX, IkZasieg, IkStrzalka } from '../ui/Ikony.jsx';
import { go } from '../nav.js';
import { statusOf, setStatus, mine } from '../lib/udzial.js';
import { icsMany } from '../lib/calendar.js';
import { saveFile } from '../saveFile.js';
import { IkPlik } from '../ui/Ikony.jsx';


export default function EventsScreen({ app, filtry, setFiltry }) {
  const { t } = useT();
  const toast = useToast();
  const { settings, events, tags, taste, origin } = app;
  const [arkuszZasiegu, setArkuszZasiegu] = useState(false);
  const mojeFiltr = settings.mojeFiltr ?? 'ALL';
  const dzis = today();
  const promien = filtry.promien ?? settings.radius;
  const set = (zmiana) => setFiltry((f) => ({ ...f, ...zmiana }));

  const miasta = useMemo(() => {
    const m = new Map();
    for (const e of events) {
      if (!e.cityKey || e.date < dzis) continue;
      const w = m.get(e.cityKey) ?? { name: e.city, n: 0 };
      w.n++;
      m.set(e.cityKey, w);
    }
    return [...m.entries()].sort((a, b) => b[1].n - a[1].n || a[1].name.localeCompare(b[1].name, 'pl'));
  }, [events, dzis]);

  const lista = useMemo(() => {
    if (filtry.widok === 'MINE') {
      return rank(mine(events, settings, mojeFiltr), taste, tags, { view: 'UPCOMING', today: dzis, origin, phrase: filtry.fraza, includeOther: true });
    }
    return rank(events, taste, tags, { view: filtry.widok, today: dzis, origin, radiusKm: filtry.miasto ? 0 : promien,
      cityKey: filtry.miasto, phrase: filtry.fraza });
  }, [events, settings, taste, tags, origin, filtry, promien, dzis, mojeFiltr]);

  // Gwiazdka na karcie: nic -> zainteresowany; zainteresowany albo biore udzial -> zdjete.
  function przelaczZapis(e) {
    const nowy = statusOf(e, settings) ? null : 'INTERESTED';
    app.setSettings((s) => setStatus(s, e, nowy));
    toast(t(nowy ? 'part.toastInterested' : 'part.toastNone'));
  }

  const moje = useMemo(() => mine(events, settings, 'ALL').filter((e) => e.date >= dzis), [events, settings, dzis]);
  const ileMoich = moje.length;
  const ile = (st) => moje.filter((e) => e.udzial === st).length;

  function eksportuj() {
    const doPliku = moje.filter((e) => mojeFiltr === 'ALL' || e.udzial === mojeFiltr);
    const nazwa = mojeFiltr === 'GOING' ? t('part.going') : mojeFiltr === 'INTERESTED' ? t('part.interested') : 'MusicClub';
    saveFile(`musicclub-${mojeFiltr.toLowerCase()}.ics`, 'text/calendar', icsMany(doPliku, { name: `MusicClub — ${nazwa}` }))
      .then(() => toast(t('part.exported', doPliku.length))).catch(() => {});
  }
  const widoki = [{ value: 'FOR_YOU', label: t('tab.forYou') }, { value: 'UPCOMING', label: t('tab.upcoming') },
    { value: 'MINE', label: ileMoich ? `${t('tab.mine')} · ${ileMoich}` : t('tab.mine') }];
  const pusty = filtry.widok === 'MINE' ? 'list.empty.mine' : !events.length ? 'list.noData' : filtry.widok === 'FOR_YOU' ? 'list.empty.forYou' : 'list.empty.upcoming';
  const opcjeMiast = [{ value: '', label: t('list.allCities'), dopisek: null },
    ...miasta.map(([k, w]) => ({ value: k, label: w.name, dopisek: w.n }))];
  const ladowanie = app.status.running && !events.length;

  return (
    <section>
      <SyncBar app={app} />
      <Przelacznik tryb="tabs" opcje={widoki} wartosc={filtry.widok} onChange={(v) => set({ widok: v })} etykieta="Widok" />
      <div className="mc-filters">
        <label className="mc-szukaj">
          <IkSzukaj size={18} />
          <input type="search" placeholder={t('list.search')} value={filtry.fraza} onChange={(e) => set({ fraza: e.target.value })} aria-label={t('list.search')} />
          {filtry.fraza && <button type="button" className="mc-ikona-btn mc-wejscie-pyk" onClick={() => set({ fraza: '' })} aria-label={t('list.clear')}><IkX size={16} /></button>}
        </label>
        {filtry.widok !== 'MINE' && (
          <div className="mc-chipy">
            <Wybor wPolu={false} etykieta={t('list.city')} ikona={<IkPinezka size={16} />} opcje={opcjeMiast} wartosc={filtry.miasto}
              onChange={(v) => set({ miasto: v })} szukaj={t('list.searchCity')} pustyTekst={t('list.noCities')} zamknij={t('common.close')} />
            <button type="button" className={`mc-chip${promien && origin && !filtry.miasto ? ' aktywny' : ''}`} aria-haspopup="dialog" onClick={() => setArkuszZasiegu(true)}>
              <IkZasieg size={16} />
              <span className="mc-wybor-wartosc">{filtry.miasto ? t('list.radiusAll') : promien && origin ? `${promien} km` : t('list.radiusAll')}</span>
              <IkStrzalka size={16} className="mc-wybor-strzalka" />
            </button>
          </div>
        )}
      </div>
      {filtry.widok === 'MINE' && ileMoich > 0 && (
        <div className="mc-moje-pasek mc-wejscie">
          <Przelacznik maly opcje={[{ value: 'ALL', label: `${t('part.all')} ${ileMoich}` }, { value: 'GOING', label: `${t('part.going')} ${ile('GOING')}` },
            { value: 'INTERESTED', label: `${t('part.interested')} ${ile('INTERESTED')}` }]} wartosc={mojeFiltr}
            onChange={(v) => app.setSettings({ mojeFiltr: v })} etykieta={t('tab.mine')} />
          <button type="button" className="mc-btn mc-btn-small" onClick={eksportuj} title={t('part.exportHint')}><IkPlik size={16} />{t('part.export')}</button>
        </div>
      )}
      {filtry.widok === 'FOR_YOU' && emptyTaste(taste) && !!events.length && <p className="mc-hint mc-wejscie">{t('list.noTaste')}</p>}
      {ladowanie ? <SkeletonCards /> : lista.length === 0
        ? <p className="mc-empty mc-wejscie">{t(pusty)}</p>
        : (
          <ul className="mc-list" key={`${filtry.widok}|${filtry.miasto}|${promien}|${filtry.widok === 'MINE' ? mojeFiltr : ''}`}>
            {lista.map((item, i) => (
              <EventCard key={item.event.id} item={item} index={i} udzial={statusOf(item.event, settings)} onToggleSave={() => przelaczZapis(item.event)} />))}
          </ul>
        )}

      <Arkusz otwarty={arkuszZasiegu} onClose={() => setArkuszZasiegu(false)} tytul={origin ? t('list.radiusSheet', settings.city) : t('list.radiusShort')} zamknij={t('common.close')}>
        {origin ? (
          <>
            <Suwak etykieta={t('list.radius')} calyKraj={t('list.radiusAll')} wartosc={filtry.miasto ? 0 : promien}
              onChange={(v) => { set({ promien: v, miasto: '' }); app.setSettings({ radius: v }); }} />
            <p className="mc-hint mc-suwak-licznik" aria-live="polite">{t('list.count', lista.length)}</p>
            <button type="button" className="mc-btn mc-btn-primary mc-btn-duzy" onClick={() => setArkuszZasiegu(false)}>{t('list.done')}</button>
          </>
        ) : (
          <>
            <p>{t('list.noCityForRadius')}</p>
            <button type="button" className="mc-btn mc-btn-primary" onClick={() => { setArkuszZasiegu(false); go('/ustawienia'); }}>{t('list.setCity')}</button>
          </>
        )}
      </Arkusz>
    </section>
  );
}
