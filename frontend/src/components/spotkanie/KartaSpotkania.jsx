import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { describeError } from '../../api/client';
import * as spotkania from '../../api/spotkania';
import { minutDo, plikIcs, stanSpotkania } from '../../utils/spotkania';
import { IconCalendar, IconExternal, IconPin } from '../Icons';
import MapaPunktu, { adresTrasy } from '../mapa/MapaPunktu';

/**
 * Spotkanie w dymku czatu: miejsce, czas (z "za 20 min" / "trwa" / "zakonczone"), notatka, mapka z pinezka, kto
 * bedzie, "Bede" / "Nie dam rady", trasa, plik do kalendarza i "Odwolaj" dla zakladajacego. Stan liczy sie co
 * minute sam - karta nie czeka na serwer, zeby przestac mowic "za 1 min".
 */
export default function KartaSpotkania({ spotkanie, onZmiana }) {
  const { t, i18n } = useTranslation();
  const [teraz, setTeraz] = useState(() => new Date());
  const [zajete, setZajete] = useState(false);
  const [blad, setBlad] = useState(null);

  useEffect(() => {
    const id = setInterval(() => setTeraz(new Date()), 30000);
    return () => clearInterval(id);
  }, []);

  const s = spotkanie;
  const stan = stanSpotkania(s, teraz);
  const otwarte = stan === 'PRZED' || stan === 'TRWA';
  const maPunkt = Number.isFinite(s.latitude) && Number.isFinite(s.longitude);

  async function odpowiedz(status) {
    setZajete(true);
    setBlad(null);
    try {
      onZmiana?.(await spotkania.odpowiedz(s.id, s.myStatus === status ? null : status));
    } catch (problem) {
      setBlad(describeError(problem).message);
    } finally {
      setZajete(false);
    }
  }

  async function odwolaj() {
    if (!window.confirm(t('meetings.cancelConfirm'))) {
      return;
    }
    setZajete(true);
    setBlad(null);
    try {
      onZmiana?.(await spotkania.odwolaj(s.id));
    } catch (problem) {
      setBlad(describeError(problem).message);
    } finally {
      setZajete(false);
    }
  }

  function doKalendarza() {
    const plik = new Blob([plikIcs(s, { tytul: t('meetings.icsTitle', { place: s.place }) })], { type: 'text/calendar;charset=utf-8' });
    const adres = URL.createObjectURL(plik);
    const a = document.createElement('a');
    a.href = adres;
    a.download = `spotkanie-${s.id}.ics`;
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(() => URL.revokeObjectURL(adres), 1000);
  }

  return (
    <div className={`spotkanie is-${stan.toLowerCase()}`} role="group" aria-label={t('meetings.cardLabel', { place: s.place })}>
      <div className="spotkanie-glowa">
        <span className="spotkanie-znak" aria-hidden="true"><IconPin size={13} /></span>
        <span className="spotkanie-rodzaj">{t('meetings.title')}</span>
        <span className={`spotkanie-stan is-${stan.toLowerCase()}`}>{etykietaStanu(stan, s, teraz, t)}</span>
      </div>
      <div className="spotkanie-miejsce">{s.place}</div>
      <div className="spotkanie-czas">{zakres(s, i18n.language)}</div>
      {s.note && <p className="spotkanie-notatka">{s.note}</p>}

      {maPunkt && !s.cancelled && (
        <MapaPunktu lat={s.latitude} lon={s.longitude} podpis={s.place} wysokosc={130} className="spotkanie-mapa" />
      )}

      <div className="spotkanie-ludzie">
        {s.goingCount > 0
          ? t('meetings.going', {
            count: s.goingCount,
            names: (s.going ?? []).slice(0, 3).join(', ') + (s.goingCount > 3 ? '…' : ''),
          })
          : t('meetings.nobodyYet')}
        {s.notGoingCount > 0 && ` · ${t('meetings.notGoing', { count: s.notGoingCount })}`}
      </div>

      {/* Zakladajacy jest na spotkaniu z definicji - gdy nie moze, odwoluje je */}
      {otwarte && s.canRespond && !s.mine && !s.cancelled && (
        <div className="spotkanie-odpowiedz" role="group" aria-label={t('meetings.rsvp')}>
          <button type="button" className={`spotkanie-przycisk${s.myStatus === 'GOING' ? ' is-wybrany' : ''}`}
            aria-pressed={s.myStatus === 'GOING'} disabled={zajete} onClick={() => odpowiedz('GOING')}>
            {t('meetings.imGoing')}
          </button>
          <button type="button" className={`spotkanie-przycisk${s.myStatus === 'NOT_GOING' ? ' is-wybrany' : ''}`}
            aria-pressed={s.myStatus === 'NOT_GOING'} disabled={zajete} onClick={() => odpowiedz('NOT_GOING')}>
            {t('meetings.cantMake')}
          </button>
        </div>
      )}

      <div className="spotkanie-akcje">
        {!s.cancelled && stan !== 'PO' && (
          <a href={adresTrasy(s.latitude, s.longitude, s.place)} target="_blank" rel="noopener noreferrer" className="spotkanie-link">
            {t('map.directions')} <IconExternal size={10} />
          </a>
        )}
        {!s.cancelled && stan !== 'PO' && (
          <button type="button" className="spotkanie-link" onClick={doKalendarza}>
            <IconCalendar size={11} /> {t('meetings.toCalendar')}
          </button>
        )}
        {s.mine && otwarte && !s.cancelled && (
          <button type="button" className="spotkanie-link is-odwolaj" disabled={zajete} onClick={odwolaj}>
            {t('meetings.cancel')}
          </button>
        )}
      </div>

      {otwarte && !s.cancelled && s.myStatus === 'GOING' && s.remindMinutes > 0 && stan === 'PRZED' && (
        <div className="spotkanie-przypomnienie">{t('meetings.willRemind', { czas: czasPrzypomnienia(s.remindMinutes, t) })}</div>
      )}
      {blad && <div className="spotkanie-blad" role="alert">{blad}</div>}
    </div>
  );
}

function etykietaStanu(stan, s, teraz, t) {
  if (stan === 'ODWOLANE') return t('meetings.state.cancelled');
  if (stan === 'TRWA') return t('meetings.state.now');
  if (stan === 'PO') return t('meetings.state.over');
  const min = minutDo(s, teraz);
  if (min < 60) return t('meetings.state.inMinutes', { count: min });
  if (min < 24 * 60) return t('meetings.state.inHours', { count: Math.round(min / 60) });
  return t('meetings.state.inDays', { count: Math.round(min / 1440) });
}

/** "pt, 10 paź · 18:30–19:30" - koniec bez daty, gdy tego samego dnia. */
function zakres(s, jezyk) {
  const od = new Date(s.startsAt);
  const doK = new Date(s.endsAt);
  const dzien = new Intl.DateTimeFormat(jezyk, { weekday: 'short', day: 'numeric', month: 'short' });
  const godz = new Intl.DateTimeFormat(jezyk, { hour: '2-digit', minute: '2-digit' });
  const tenSam = od.toDateString() === doK.toDateString();
  return `${dzien.format(od)} · ${godz.format(od)}–${tenSam ? godz.format(doK) : `${dzien.format(doK)} ${godz.format(doK)}`}`;
}

export function czasPrzypomnienia(minuty, t) {
  if (minuty >= 1440) return t('meetings.remind.day');
  if (minuty >= 60) return t('meetings.remind.hours', { count: minuty / 60 });
  return t('meetings.remind.minutes', { count: minuty });
}
