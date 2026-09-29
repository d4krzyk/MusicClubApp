import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { IconCalendar, IconClock, IconPin } from './Icons';
import {
  godzina, nazwaMiasta, plakietka, wykonawcySpozaNazwy,
} from '../utils/wydarzenia';

/**
 * Jedno wydarzenie na liscie.
 *
 * Cala karta jest odnosnikiem - na telefonie trafienie palcem w maly
 * napis "szczegoly" byloby loteria.
 */
export default function EventCard({ wydarzenie }) {
  const { t, i18n } = useTranslation();
  const { dzien, miesiac } = plakietka(wydarzenie.date, i18n.language);
  const miasto = nazwaMiasta(wydarzenie.cityKey, wydarzenie.city, i18n.language);
  const sklad = wykonawcySpozaNazwy(wydarzenie.name, wydarzenie.performers).slice(0, 3);
  const odwolane = wydarzenie.status === 'CANCELLED';

  return (
    <Link
      to={`/wydarzenia/${wydarzenie.id}`}
      className={`wydarzenie-karta${odwolane ? ' is-odwolane' : ''}`}
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
        </div>

        {sklad.length > 0 && (
          <div className="wydarzenie-sklad">{sklad.join(' · ')}</div>
        )}

        {(wydarzenie.status !== 'SCHEDULED' || wydarzenie.moreDates > 0 || wydarzenie.genre) && (
          <div className="wydarzenie-plakietki">
            {wydarzenie.status !== 'SCHEDULED' && (
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
