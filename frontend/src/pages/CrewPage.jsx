import { useCallback, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useNavigate, useParams } from 'react-router-dom';
import Button from 'react-bootstrap/Button';
import { describeError } from '../api/client';
import { ODSWIEZ_LICZNIK } from '../utils/klan';
import * as ekipy from '../api/ekipy';
import Avatar from '../components/Avatar';
import EmptyState from '../components/EmptyState';
import EkipaCzat from '../components/ekipa/EkipaCzat';
import EkipaForm from '../components/ekipa/EkipaForm';
import KartaEkipy from '../components/ekipa/KartaEkipy';
import { IconArrowLeft, IconCalendar, IconFriends, IconLock, IconPin } from '../components/Icons';

/**
 * Strona ekipy: na jaki koncert, kto jedzie, skad, czat z miejscem zbiorki. Zakladajacy przyjmuje prosby, zmienia
 * opis i limit, zamyka nabor i moze usunac kogos z ekipy. Kto nie jest w ekipie, widzi ja jak karte pod wydarzeniem.
 */
export default function CrewPage() {
  const { id } = useParams();
  const { t, i18n } = useTranslation();
  const navigate = useNavigate();
  const [e, setE] = useState(null);
  const [blad, setBlad] = useState(null);
  const [edycja, setEdycja] = useState(false);
  const [zajete, setZajete] = useState(false);
  const [bladAkcji, setBladAkcji] = useState(null);

  const wczytaj = useCallback(() => {
    ekipy.ekipa(id).then(setE).catch((p) => setBlad(p.response?.status === 404 ? t('crews.notFound') : describeError(p).message));
  }, [id, t]);
  useEffect(() => { wczytaj(); }, [wczytaj]);

  async function dzialaj(fn) {
    setZajete(true);
    setBladAkcji(null);
    try {
      const wynik = await fn();
      if (wynik?.crew) setE(wynik);
      else wczytaj();
    } catch (p) {
      setBladAkcji(describeError(p).message);
    } finally {
      setZajete(false);
    }
  }

  if (blad) {
    return <EmptyState icon={IconFriends} title={blad} action={<Link to="/wydarzenia">{t('crews.toEvents')}</Link>} />;
  }
  if (!e) {
    return <div className="ekipa-szkielet duzy" aria-busy="true" aria-label={t('common.loading')} />;
  }

  const k = e.crew;
  const czlonek = k.myState === 'FOUNDER' || k.myState === 'MEMBER';
  const zakladajacy = k.myState === 'FOUNDER';
  const data = new Intl.DateTimeFormat(i18n.language, { weekday: 'long', day: 'numeric', month: 'long' }).format(new Date(e.eventDate));

  async function odejdz() {
    const pytanie = zakladajacy && e.members.length > 1 ? t('crews.leaveFounderConfirm') : zakladajacy ? t('crews.leaveLastConfirm') : t('crews.leaveConfirm');
    if (!window.confirm(pytanie)) return;
    setZajete(true);
    try {
      await ekipy.odejdz(k.id);
      navigate(`/wydarzenia/${e.eventId}`);
    } catch (p) {
      setBladAkcji(describeError(p).message);
      setZajete(false);
    }
  }

  return (
    <div className="ekipa-strona tiles-in">
      <Link to={`/wydarzenia/${e.eventId}`} className="ekipa-wstecz small"><IconArrowLeft size={12} /> {t('crews.backToEvent')}</Link>

      <header className="ekipa-naglowek">
        {e.eventImageUrl && <img src={e.eventImageUrl} alt="" className="ekipa-naglowek-zdjecie" referrerPolicy="no-referrer" />}
        <div className="ekipa-naglowek-tekst">
          <span className="ekipa-naglowek-etykieta">{t('crews.crewFor')}</span>
          <h1 className="h5 mb-1"><Link to={`/wydarzenia/${e.eventId}`}>{e.eventName}</Link></h1>
          <div className="small text-body-secondary">
            <IconCalendar size={12} /> {data}{e.eventTime ? ` · ${e.eventTime.slice(0, 5)}` : ''}
            {e.venueName && <> · {e.venueName}</>}{e.eventCity && <>, {e.eventCity}</>}
          </div>
        </div>
      </header>

      <section className="ekipa-opis-sekcja">
        <h2 className="h4 mb-1">{k.title || t('crews.defaultName', { username: k.founder })}</h2>
        <div className="ekipa-karta-fakty small text-body-secondary mb-2">
          <span>{t('crews.seats', { members: k.members, capacity: k.capacity })}</span>
          {k.departureCity && <span><IconPin size={11} /> {t('crews.from', { city: k.departureCity })}</span>}
          <span>{k.joinPolicy === 'APPROVAL' ? <><IconLock size={10} /> {t('crews.policy.APPROVAL')}</> : t('crews.policy.OPEN')}</span>
          {k.closed && <span className="ekipa-plakietka">{t('crews.closed')}</span>}
        </div>
        {k.description && <p className="ekipa-opis">{k.description}</p>}
        {!czlonek && <ul className="list-unstyled mb-0"><KartaEkipy k={k} onZmiana={() => wczytaj()} /></ul>}
      </section>

      <section className="ekipa-sklad" aria-labelledby="ekipa-sklad">
        <h3 id="ekipa-sklad" className="h6 wydarzenie-sekcja">{t('crews.members')}</h3>
        <ul className="list-unstyled ekipa-osoby">
          {e.members.map((m) => (
            <li key={m.username} className="ekipa-osoba">
              <Link to={`/profil/${encodeURIComponent(m.username)}`} className="ekipa-osoba-link">
                <Avatar avatarUrl={m.avatarUrl} username={m.username} size={32} />
                <span className="text-truncate">{m.username}</span>
              </Link>
              {m.role === 'FOUNDER' && <span className="ekipa-plakietka is-twoja">{t('crews.founder')}</span>}
              {zakladajacy && m.role !== 'FOUNDER' && (
                <Button size="sm" variant="link" className="ms-auto text-danger p-0" disabled={zajete}
                  onClick={() => window.confirm(t('crews.kickConfirm', { username: m.username })) && dzialaj(() => ekipy.wyrzuc(k.id, m.username))}>
                  {t('crews.kick')}
                </Button>
              )}
            </li>
          ))}
        </ul>
      </section>

      {zakladajacy && e.requests.length > 0 && (
        <section className="ekipa-prosby" aria-labelledby="ekipa-prosby">
          <h3 id="ekipa-prosby" className="h6 wydarzenie-sekcja">{t('crews.requests', { count: e.requests.length })}</h3>
          <ul className="list-unstyled">
            {e.requests.map((r) => (
              <li key={r.id} className="ekipa-prosba-wiersz">
                <Link to={`/profil/${encodeURIComponent(r.username)}`} className="ekipa-osoba-link">
                  <Avatar avatarUrl={r.avatarUrl} username={r.username} size={28} />
                  <span>{r.username}</span>
                </Link>
                {r.message && <p className="small mb-1 ekipa-prosba-tresc">{r.message}</p>}
                <div className="d-flex gap-2">
                  <Button size="sm" disabled={zajete} onClick={() => dzialaj(() => ekipy.przyjmij(k.id, r.id))}>{t('crews.accept')}</Button>
                  <Button size="sm" variant="outline-secondary" disabled={zajete} onClick={() => dzialaj(() => ekipy.odrzuc(k.id, r.id))}>{t('crews.decline')}</Button>
                </div>
              </li>
            ))}
          </ul>
        </section>
      )}

      {bladAkcji && <div className="small text-danger mb-2" role="alert">{bladAkcji}</div>}

      {czlonek && (
        <section className="ekipa-czat-sekcja" aria-labelledby="ekipa-czat">
          <h3 id="ekipa-czat" className="h6 wydarzenie-sekcja">{t('crews.chat.title')}</h3>
          <EkipaCzat ekipaId={k.id} otwarty={e.chatOpen}
            onPrzeczytane={() => window.dispatchEvent(new Event(ODSWIEZ_LICZNIK))} />
        </section>
      )}

      {zakladajacy && !e.eventPast && (
        <section className="ekipa-ustawienia">
          {edycja ? (
            <EkipaForm poczatek={k} idPrefix="edycja-ekipy" onAnuluj={() => setEdycja(false)}
              onZapisz={async (f) => { setE(await ekipy.zmien(k.id, f)); setEdycja(false); }} />
          ) : (
            <div className="d-flex flex-wrap gap-2">
              <Button size="sm" variant="outline-secondary" onClick={() => setEdycja(true)}>{t('crews.edit')}</Button>
              <Button size="sm" variant="outline-secondary" disabled={zajete} onClick={() => dzialaj(() => ekipy.zamknijNabor(k.id, !k.closed))}>
                {k.closed ? t('crews.reopen') : t('crews.close')}
              </Button>
            </div>
          )}
        </section>
      )}

      {czlonek && (
        <div className="mt-3">
          <Button size="sm" variant="link" className="text-danger p-0" disabled={zajete} onClick={odejdz}>{t('crews.leave')}</Button>
        </div>
      )}
    </div>
  );
}
