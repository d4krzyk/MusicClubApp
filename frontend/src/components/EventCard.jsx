import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import EventReasons from './EventReasons';
import { IconCalendar, IconClock, IconFriends, IconPin } from './Icons';
import {
  godzina, nazwaMiasta, plakietka, wykonawcySpozaNazwy,
} from '../utils/wydarzenia';

/**
 * Jedno wydarzenie na liscie.
 *
 * Cala karta jest odnosnikiem - na telefonie trafienie palcem w maly
 * napis "szczegoly" byloby loteria.
 */
export default function EventCard({ wydarzenie, pokazPowody = false, indeks = 0 }) {
  const { t, i18n } = useTranslation();
  const { dzien, miesiac } = plakietka(wydarzenie.date, i18n.language);
  const miasto = nazwaMiasta(wydarzenie.cityKey, wydarzenie.city, i18n.language);
  const sklad = wykonawcySpozaNazwy(wydarzenie.name, wydarzenie.performers).slice(0, 3);
  const odwolane = wydarzenie.status === 'CANCELLED' || wydarzenie.withdrawn;

  /* "12 osob idzie · 30 zainteresowanych · 2 znajomych" - tylko to, co nie jest zerem */
  const spolecznosc = [
    wydarzenie.going > 0 && t('events.goingCount', { count: wydarzenie.going }),
    wydarzenie.interested > 0 && t('events.interestedCount', { count: wydarzenie.interested }),
    wydarzenie.friends > 0 && t('events.friendsCount', { count: wydarzenie.friends }),
  ].filter(Boolean);

  return (
    <Link
      to={`/wydarzenia/${wydarzenie.id}`}
      className={`wydarzenie-karta mc-wejscie${odwolane ? ' is-odwolane' : ''}`}
      style={{ '--i': indeks }}
    >
      <div className="wydarzenie-zdjecie">
        {wydarzenie.thumbUrl ? (
          /* loading="lazy": lista ma dziesiatki zdjec, a na ekran mieszcza sie trzy */
          <img src={wydarzenie.thumbUrl} alt="" loading="lazy" decoding="async" />
        ) : (
          <span className="wydarzenie-zdjecie-zastepcze" aria-hidden="true">
            <IconCalendar size={28} />
          </span>
        )}

        <span className="wydarzenie-plakietka" aria-hidden="true">
          <span className="wydarzenie-plakietka-dzien">{dzien}</span>
          <span className="wydarzenie-plakietka-miesiac">{miesiac}</span>
        </span>

        {/* Moj zapis - widac go od razu, bez wchodzenia w wydarzenie */}
        {wydarzenie.myStatus && (
          <span className={`wydarzenie-moj-status wydarzenie-moj-status-${wydarzenie.myStatus.toLowerCase()}`}>
            {t(`events.myStatus.${wydarzenie.myStatus}`)}
          </span>
        )}
      </div>

      <div className="wydarzenie-tresc">
        <div className="wydarzenie-nazwa">{wydarzenie.name}</div>

        <div className="wydarzenie-meta">
          <span>
            <IconClock size={12} />
            {wydarzenie.time ? godzina(wydarzenie.time) : t('events.timeUnknown')}
          </span>
          <span>
            <IconPin size={12} />
            {[wydarzenie.venueName, miasto].filter(Boolean).join(' · ')}
          </span>
          {wydarzenie.distanceKm != null && (
            <span className="wydarzenie-odleglosc">
              {wydarzenie.distanceKm === 0
                ? t('location.inYourCity')
                : t('location.kmAway', { km: wydarzenie.distanceKm })}
            </span>
          )}
        </div>

        {sklad.length > 0 && (
          <div className="wydarzenie-sklad">{sklad.join(' · ')}</div>
        )}

        {pokazPowody && <EventReasons powody={wydarzenie.reasons} maks={3} />}

        {spolecznosc.length > 0 && (
          <div className="wydarzenie-spolecznosc">{spolecznosc.join(' · ')}</div>
        )}

        {/* Ekipy - czy ktos szuka towarzystwa na ten koncert (albo czy mam juz swoja) */}
        {(wydarzenie.myCrewId || wydarzenie.crews > 0) && (
          <div className={`wydarzenie-ekipy${wydarzenie.myCrewId ? ' is-moja' : ''}`}>
            <IconFriends size={12} />
            {wydarzenie.myCrewId ? t('crews.inCrew') : t('crews.count', { count: wydarzenie.crews })}
          </div>
        )}

        {(wydarzenie.withdrawn || wydarzenie.status !== 'SCHEDULED'
          || wydarzenie.moreDates > 0 || wydarzenie.genre) && (
          <div className="wydarzenie-plakietki">
            {wydarzenie.withdrawn && (
              <span className="badge wydarzenie-status wydarzenie-status-cancelled">
                {t('events.withdrawnBadge')}
              </span>
            )}
            {!wydarzenie.withdrawn && wydarzenie.status !== 'SCHEDULED' && (
              <span className={`badge wydarzenie-status wydarzenie-status-${wydarzenie.status.toLowerCase()}`}>
                {t(`events.status.${wydarzenie.status}`)}
              </span>
            )}
            {wydarzenie.moreDates > 0 && (
              <span className="badge wydarzenie-terminy">
                {t('events.moreDates', { count: wydarzenie.moreDates })}
              </span>
            )}
            {wydarzenie.genre && (
              <span className="badge wydarzenie-gatunek">{wydarzenie.genre}</span>
            )}
          </div>
        )}
      </div>
    </Link>
  );
}
