import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import * as ekipy from '../../api/ekipy';
import * as wydarzenia from '../../api/wydarzenia';
import { IconCalendar, IconFriends, IconSearch } from '../Icons';

/**
 * "Twoje koncerty" na gorze tablicy - po co jest ta aplikacja: najblizsze koncerty, na ktore ide, z ekipa (i liczba
 * nowych wiadomosci w jej czacie) albo z zacheta "Znajdz ekipe". Bez zadnego koncertu - jedno wezwanie do znalezienia
 * koncertu i ludzi. Blad sieci chowa pasek po cichu: tablica dziala dalej.
 */
export default function MojeKoncerty() {
  const { t, i18n } = useTranslation();
  const [dane, setDane] = useState(null);

  useEffect(() => {
    let anulowane = false;
    Promise.all([ekipy.moje(), wydarzenia.lista({ widok: 'moje', rozmiar: 8 })])
      .then(([mojeEkipy, moje]) => {
        if (anulowane) return;
        const wgWydarzenia = new Map(mojeEkipy.map((e) => [e.eventId, e]));
        const ide = (moje.content ?? []).filter((w) => w.myStatus === 'GOING' && !w.withdrawn);
        // Koncerty z ekipa, ktorych nie ma na pierwszej stronie "Moje", tez maja byc widoczne
        const lista = [
          ...ide.map((w) => ({ id: w.id, nazwa: w.name, data: w.date, czas: w.time, miejsce: w.venueName, miasto: w.city, ekipa: wgWydarzenia.get(w.id) })),
          ...mojeEkipy.filter((e) => !ide.some((w) => w.id === e.eventId)).map((e) => ({
            id: e.eventId, nazwa: e.eventName, data: e.eventDate, czas: e.eventTime, miejsce: e.venueName, miasto: e.eventCity, ekipa: e,
          })),
        ].sort((a, b) => `${a.data}${a.czas ?? ''}`.localeCompare(`${b.data}${b.czas ?? ''}`)).slice(0, 8);
        setDane(lista);
      })
      .catch(() => !anulowane && setDane([]));
    return () => { anulowane = true; };
  }, []);

  if (dane === null) {
    return null;
  }

  if (dane.length === 0) {
    return (
      <section className="moje-koncerty is-pusto mb-3" aria-labelledby="moje-koncerty">
        <div>
          <h2 id="moje-koncerty" className="h6 mb-1">{t('crews.home.emptyTitle')}</h2>
          <p className="small mb-0">{t('crews.home.emptyText')}</p>
        </div>
        <Link to="/wydarzenia?widok=dla-ciebie" className="btn btn-sm btn-light"><IconSearch size={12} /> {t('crews.home.findConcert')}</Link>
      </section>
    );
  }

  const dzien = new Intl.DateTimeFormat(i18n.language, { weekday: 'short', day: 'numeric', month: 'short' });
  return (
    <section className="moje-koncerty mb-3" aria-labelledby="moje-koncerty">
      <div className="moje-koncerty-glowa">
        <h2 id="moje-koncerty" className="h6 mb-0"><IconCalendar size={14} /> {t('crews.home.title')}</h2>
        <Link to="/wydarzenia?widok=moje" className="small">{t('crews.home.all')}</Link>
      </div>
      <ul className="moje-koncerty-pasek list-unstyled strip-track">
        {dane.map((k, i) => (
          <li key={k.id} className="moj-koncert mc-wejscie" style={{ '--i': i }}>
            <Link to={`/wydarzenia/${k.id}`} className="moj-koncert-nazwa">{k.nazwa}</Link>
            <span className="small text-body-secondary">
              {dzien.format(new Date(k.data))}{k.czas ? ` · ${k.czas.slice(0, 5)}` : ''}{k.miejsce ? ` · ${k.miejsce}` : ''}
            </span>
            {k.ekipa ? (
              <Link to={`/ekipy/${k.ekipa.crewId}`} className="moj-koncert-ekipa">
                <IconFriends size={12} /> {t('crews.home.yourCrew', { members: k.ekipa.members, capacity: k.ekipa.capacity })}
                {k.ekipa.unreadChat > 0 && <span key={k.ekipa.unreadChat} className="nav-badge moj-koncert-licznik">{k.ekipa.unreadChat}</span>}
              </Link>
            ) : (
              <Link to={`/wydarzenia/${k.id}#ekipy`} className="moj-koncert-ekipa is-szukaj">
                <IconSearch size={12} /> {t('crews.home.findCrew')}
              </Link>
            )}
          </li>
        ))}
      </ul>
    </section>
  );
}
