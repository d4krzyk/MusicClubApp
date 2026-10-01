import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import Alert from 'react-bootstrap/Alert';
import Button from 'react-bootstrap/Button';
import Card from 'react-bootstrap/Card';
import Spinner from 'react-bootstrap/Spinner';
import { describeError } from '../api/client';
import * as klany from '../api/klany';
import ClanBadge from '../components/ClanBadge';
import EmptyState from '../components/EmptyState';
import { IconClan, IconPin, IconSearch } from '../components/Icons';
import KlanAnkiety from '../components/klan/KlanAnkiety';
import KlanCzat from '../components/klan/KlanCzat';
import KlanCzlonkowie from '../components/klan/KlanCzlonkowie';
import KlanKoncerty from '../components/klan/KlanKoncerty';
import KlanMuzyka from '../components/klan/KlanMuzyka';
import KlanOKlanie from '../components/klan/KlanOKlanie';
import KlanPosty from '../components/klan/KlanPosty';
import KlanRanking from '../components/klan/KlanRanking';
import KlanTytuly from '../components/klan/KlanTytuly';
import KlanUstawienia from '../components/klan/KlanUstawienia';
import KlanZakladanie from '../components/klan/KlanZakladanie';
import ReportButton from '../components/ReportButton';
import { formatDate, formatDateTime, timeAgo } from '../utils/dates';

/**
 * Klan. Dwa adresy, jedna strona: /klan to MOJ klan (a bez klanu - zaproszenia i zakladanie),
 * /klany/:id to strona dowolnego klanu - jawne dane dla kazdego, czat i posty tylko dla czlonkow.
 */
export default function ClanPage() {
  const { t } = useTranslation();
  const { id } = useParams();
  const navigate = useNavigate();

  const [klan, setKlan] = useState(null);
  const [zaproszenia, setZaproszenia] = useState([]);
  const [prosby, setProsby] = useState([]);
  const [ostrzezenie, setOstrzezenie] = useState(null);
  const [ladowanie, setLadowanie] = useState(true);
  const [blad, setBlad] = useState(null);
  const [zakladka, setZakladka] = useState(null);

  const wczytaj = useCallback(async () => {
    setLadowanie(true);
    setBlad(null);
    try {
      if (id) {
        setKlan(await klany.jeden(id));
        setZaproszenia([]);
        setProsby([]);
      } else {
        const moj = await klany.moj();
        setKlan(moj.clan);
        setZaproszenia(moj.invitations);
        setProsby(moj.requests ?? []);
      }
    } catch (problem) {
      setBlad(problem.response?.status === 404 ? t('clans.notFound') : describeError(problem).message);
    } finally {
      setLadowanie(false);
    }
  }, [id, t]);

  useEffect(() => {
    setZakladka(null);
    wczytaj();
  }, [wczytaj]);

  /** Odpowiedz "moj klan i zaproszenia" po przyjeciu, odrzuceniu albo odejsciu. */
  function poZmianieMojego(moj) {
    setKlan(moj.clan);
    setZaproszenia(moj.invitations);
    setProsby(moj.requests ?? []);
    setZakladka(null);
    if (id) {
      navigate('/klan');
    }
  }

  if (ladowanie) {
    return (
      <div className="text-center py-5 text-body-secondary">
        <Spinner animation="border" size="sm" className="me-2" />{t('common.loading')}
      </div>
    );
  }
  if (blad) {
    return <Alert variant="warning">{blad}</Alert>;
  }
  if (!klan) {
    return (
      <BezKlanu
        zaproszenia={zaproszenia}
        prosby={prosby}
        onZmiana={poZmianieMojego}
        onZalozony={(nowy, uwaga) => { setOstrzezenie(uwaga ?? null); setKlan(nowy); }}
      />
    );
  }

  return (
    <Klan
      klan={klan}
      ostrzezenie={ostrzezenie}
      onOstrzezenie={setOstrzezenie}
      zakladka={zakladka}
      onZakladka={setZakladka}
      onZmiana={setKlan}
      onMoj={poZmianieMojego}
      onRozwiazany={() => { setKlan(null); navigate('/klan'); wczytaj(); }}
    />
  );
}

function Klan({ klan, ostrzezenie, onOstrzezenie, zakladka, onZakladka, onZmiana, onMoj, onRozwiazany }) {
  const { t, i18n } = useTranslation();
  const [blad, setBlad] = useState(null);
  const czlonek = klan.myRole != null;
  const zarzad = klan.myRole === 'FOUNDER' || klan.myRole === 'ADMIN';
  const aktywna = zakladka ?? (klan.canSeeContent ? 'czat' : 'oklanie');

  async function odejdz() {
    if (!window.confirm(t('clans.leaveConfirm', { name: klan.name }))) {
      return;
    }
    try {
      onMoj(await klany.odejdz(klan.id));
    } catch (problem) {
      setBlad(describeError(problem).message);
    }
  }

  async function rozwiazJakoAdmin() {
    if (!window.confirm(t('clans.settings.disbandConfirm', { name: klan.name }))) {
      return;
    }
    try {
      await klany.rozwiaz(klan.id);
      onRozwiazany();
    } catch (problem) {
      setBlad(describeError(problem).message);
    }
  }

  const karty = [
    !klan.canSeeContent && { klucz: 'oklanie', tekst: t('clans.tabs.about') },
    klan.canSeeContent && { klucz: 'czat', tekst: t('clans.tabs.chat'), licznik: klan.unreadChat },
    klan.canSeeContent && { klucz: 'posty', tekst: t('clans.tabs.posts') },
    klan.canSeeContent && { klucz: 'muzyka', tekst: t('clans.tabs.music') },
    klan.canSeeContent && { klucz: 'koncerty', tekst: t('clans.tabs.events') },
    klan.canSeeContent && { klucz: 'ankiety', tekst: t('clans.tabs.polls') },
    klan.canSeeContent && { klucz: 'ranking', tekst: t('clans.tabs.ranking') },
    klan.canSeeContent && { klucz: 'tytuly', tekst: t('clans.tabs.titles') },
    // zarzad widzi tu licznik oczekujacych prosb o dolaczenie
    { klucz: 'czlonkowie', tekst: t('clans.tabs.members'), licznik: zarzad ? klan.requests.length : 0 },
    zarzad && { klucz: 'ustawienia', tekst: t('clans.tabs.settings') },
  ].filter(Boolean);

  return (
    <div className="klan-strona mx-auto" style={{ '--klan': klan.colorHex }}>
      <header className="klan-naglowek">
        <div className="klan-zdjecie">{klan.photoUrl && <img src={klan.photoUrl} alt="" />}</div>
        <div className="klan-nagl-cialo">
          <div className="klan-ikona">{klan.iconUrl ? <img src={klan.iconUrl} alt="" /> : <IconClan size={40} />}</div>
          <div className="d-flex align-items-center gap-2 flex-wrap mb-1">
            <h1 className="h4 mb-0 klan-nazwa">{klan.name}</h1>
            <ClanBadge clan={klan} link={false} />
          </div>
          <p className="small text-body-secondary mb-2">
            {t('clans.meta', { count: klan.memberCount, date: formatDate(klan.createdAt, i18n.language) })}
          </p>
          {klan.motto && <p className="klan-haslo mb-2">„{klan.motto}"</p>}
          {klan.description && <p className="klan-opis mb-2">{klan.description}</p>}
          {(klan.city || klan.genres.length > 0) && (
            <div className="klan-wizytowka-wiersz">
              {klan.city && <span className="klan-fakt"><IconPin size={14} /> {klan.city}</span>}
              {klan.genres.map((g) => <span key={g} className="klan-gatunek klan-gatunek-wybrany">{g}</span>)}
            </div>
          )}

          <div className="d-flex gap-2 flex-wrap">
            {czlonek && (
              <Button variant="outline-secondary" size="sm" onClick={odejdz}>{t('clans.leave')}</Button>
            )}
            {klan.viewingAsAdmin && (
              <Button variant="outline-danger" size="sm" onClick={rozwiazJakoAdmin}>{t('clans.adminDisband')}</Button>
            )}
            {/* Zglosic mozna kazdy klan poza wlasnym; administrator aplikacji o zgloszeniach decyduje, nie zglasza */}
            <Link to="/klany" className="btn btn-outline-secondary btn-sm">
              <IconSearch size={13} className="me-1" />{t('clans.directory.browse')}
            </Link>
            {klan.myRole !== 'FOUNDER' && !klan.viewingAsAdmin && (
              <ReportButton clanId={klan.id} clanName={klan.name} contexts={['CLAN']} />
            )}
          </div>
        </div>
      </header>

      {blad && <Alert variant="danger" className="mt-3 mb-0">{blad}</Alert>}
      {ostrzezenie && (
        <Alert variant="warning" className="mt-3 mb-0" dismissible onClose={() => onOstrzezenie(null)}>{ostrzezenie}</Alert>
      )}

      {klan.viewingAsAdmin && (
        <Alert variant="warning" className="mt-3 mb-0 klan-uwaga">{t('clans.adminBanner')}</Alert>
      )}
      {czlonek && <p className="small text-body-secondary mt-3 mb-0">{t('clans.moderationNotice')}</p>}

      {klan.canSeeContent && klan.announcement && (
        <aside className="klan-ogloszenie" aria-label={t('clans.announcement.title')}>
          <IconPin size={18} />
          <div className="klan-ogloszenie-tresc">
            <strong>{t('clans.announcement.title')}</strong>
            <p className="klan-ogloszenie-tekst">{klan.announcement}</p>
            {klan.announcementAt && (
              <span className="small text-body-secondary">
                {t('clans.announcement.updated', { when: formatDateTime(klan.announcementAt, i18n.language) })}
              </span>
            )}
          </div>
        </aside>
      )}
      {klan.canSeeContent && klan.rules && <Zasady klan={klan} />}

      <nav className="klan-zakladki" aria-label={t('clans.tabs.label')}>
        {karty.map((k) => (
          <Button
            key={k.klucz}
            variant={aktywna === k.klucz ? 'primary' : 'outline-secondary'}
            size="sm"
            aria-pressed={aktywna === k.klucz}
            onClick={() => onZakladka(k.klucz)}
          >
            {k.tekst}
            {k.licznik > 0 && (
              <span className="klan-zakladka-licznik" aria-label={k.klucz === 'czlonkowie' ? t('clans.requests.count', { count: k.licznik }) : t('clans.unread', { count: k.licznik })}>
                {k.licznik > 99 ? '99+' : k.licznik}
              </span>
            )}
          </Button>
        ))}
      </nav>

      {aktywna === 'oklanie' && !klan.canSeeContent && <KlanOKlanie klan={klan} onZmiana={onZmiana} onMoj={onMoj} />}
      {aktywna === 'czat' && klan.canSeeContent && <KlanCzat klan={klan} onZmiana={onZmiana} />}
      {aktywna === 'posty' && klan.canSeeContent && <KlanPosty klan={klan} />}
      {aktywna === 'muzyka' && klan.canSeeContent && <KlanMuzyka klan={klan} />}
      {aktywna === 'koncerty' && klan.canSeeContent && <KlanKoncerty klan={klan} />}
      {aktywna === 'ankiety' && klan.canSeeContent && <KlanAnkiety klan={klan} />}
      {aktywna === 'ranking' && klan.canSeeContent && <KlanRanking klan={klan} />}
      {aktywna === 'tytuly' && klan.canSeeContent && <KlanTytuly klan={klan} onZmiana={onZmiana} />}
      {aktywna === 'czlonkowie' && <KlanCzlonkowie klan={klan} onZmiana={onZmiana} />}
      {aktywna === 'ustawienia' && zarzad && (
        <KlanUstawienia klan={klan} onZmiana={onZmiana} onRozwiazany={onRozwiazany} />
      )}
    </div>
  );
}

/**
 * Zasady klanu na jego stronie. Rozwiniete, dopoki ktos ich nie potwierdzi ("Rozumiem") - dzieki temu
 * nowy czlonek na pewno je zobaczy; po zmianie tresci rozwijaja sie znowu. Potwierdzenie pamieta
 * tylko przegladarka (nic nie idzie na serwer).
 */
function Zasady({ klan }) {
  const { t } = useTranslation();
  const klucz = `mc-klan-zasady-${klan.id}`;
  const [otwarte, setOtwarte] = useState(() => {
    try {
      return localStorage.getItem(klucz) !== klan.rules;
    } catch {
      return true;
    }
  });

  function potwierdz() {
    try {
      localStorage.setItem(klucz, klan.rules);
    } catch {
      // bez zapisu zasady po prostu rozwina sie przy nastepnej wizycie
    }
    setOtwarte(false);
  }

  return (
    <aside className="klan-zasady" aria-label={t('clans.rules.title')}>
      <IconClan size={18} />
      <div className="klan-zasady-tresc">
        <div className="d-flex align-items-center flex-wrap gap-2">
          <strong>{t('clans.rules.title')}</strong>
          {!otwarte && (
            <Button variant="link" size="sm" className="p-0" onClick={() => setOtwarte(true)}>
              {t('clans.rules.show')}
            </Button>
          )}
        </div>
        {otwarte && (
          <>
            <p className="klan-zasady-tekst">{klan.rules}</p>
            {klan.myRole != null && (
              <Button size="sm" variant="outline-secondary" onClick={potwierdz}>{t('clans.rules.ack')}</Button>
            )}
          </>
        )}
      </div>
    </aside>
  );
}

/**
 * Kto nie jest w zadnym klanie: zaproszenia, ktore na niego czekaja, jego prosby o dolaczenie,
 * przejscie do przegladarki klanow i zakladanie wlasnego.
 */
function BezKlanu({ zaproszenia, prosby, onZmiana, onZalozony }) {
  const { t, i18n } = useTranslation();
  const [zajety, setZajety] = useState(false);
  const [blad, setBlad] = useState(null);

  async function odpowiedz(akcja) {
    setZajety(true);
    setBlad(null);
    try {
      onZmiana(await akcja());
    } catch (problem) {
      setBlad(describeError(problem).message);
    } finally {
      setZajety(false);
    }
  }

  // Cofniecie prosby zwraca strone klanu, a nie "moj klan" - po nim wczytujemy "moj klan" od nowa
  async function cofnij(prosba) {
    setZajety(true);
    setBlad(null);
    try {
      await klany.cofnijProsbe(prosba.clan.id);
      onZmiana(await klany.moj());
    } catch (problem) {
      setBlad(describeError(problem).message);
    } finally {
      setZajety(false);
    }
  }

  return (
    <div className="klan-strona mx-auto">
      <div className="d-flex align-items-center flex-wrap gap-2 mb-3">
        <h1 className="h4 mb-0 me-auto">{t('clans.title')}</h1>
        <Link to="/klany" className="btn btn-primary btn-sm">
          <IconSearch size={13} className="me-1" />{t('clans.directory.browse')}
        </Link>
      </div>
      {blad && <Alert variant="danger">{blad}</Alert>}

      {zaproszenia.length > 0 && (
        <Card className="mb-4">
          <Card.Body>
            <Card.Title as="h2" className="h6 text-uppercase text-body-secondary">{t('clans.invitations.title')}</Card.Title>
            <ul className="list-unstyled mb-0">
              {zaproszenia.map((z) => (
                <li key={z.id}>
                <div className="klan-czlonek">
                  <span className="klan-czlonek-nazwa">
                    <span>
                      <ClanBadge clan={z.clan} className="me-2" />
                      <Link to={`/klany/${z.clan.id}`} className="text-reset">{z.clan.name}</Link>
                    </span>
                    <span className="small text-body-secondary">
                      {t('clans.invitations.from', { username: z.inviterUsername, count: z.memberCount })}
                    </span>
                  </span>
                  <span className="klan-czlonek-akcje">
                    <Button size="sm" disabled={zajety} onClick={() => odpowiedz(() => klany.przyjmij(z.id))}>
                      {t('clans.invitations.accept')}
                    </Button>
                    <Button size="sm" variant="outline-secondary" disabled={zajety}
                      onClick={() => odpowiedz(() => klany.odrzuc(z.id))}>
                      {t('clans.invitations.decline')}
                    </Button>
                  </span>
                </div>
                {z.rules && (
                  <details className="klan-zasady-zaproszenie">
                    <summary>{t('clans.rules.inInvitation')}</summary>
                    <p className="klan-zasady-tekst mb-0">{z.rules}</p>
                  </details>
                )}
                </li>
              ))}
            </ul>
          </Card.Body>
        </Card>
      )}

      {prosby.length > 0 && (
        <Card className="mb-4">
          <Card.Body>
            <Card.Title as="h2" className="h6 text-uppercase text-body-secondary">{t('clans.myRequests.title')}</Card.Title>
            <ul className="list-unstyled mb-0">
              {prosby.map((p) => (
                <li key={p.id} className="klan-czlonek">
                  <span className="klan-czlonek-nazwa">
                    <span>
                      <ClanBadge clan={p.clan} className="me-2" />
                      <Link to={`/klany/${p.clan.id}`} className="text-reset">{p.clan.name}</Link>
                    </span>
                    <span className="small text-body-secondary">
                      {p.status === 'PENDING' ? t('clans.myRequests.pending') : t('clans.myRequests.declined')}
                      {' · '}{timeAgo(p.createdAt, i18n.language)}
                    </span>
                  </span>
                  {p.status === 'PENDING' && (
                    <span className="klan-czlonek-akcje">
                      <Button size="sm" variant="outline-secondary" disabled={zajety} onClick={() => cofnij(p)}>
                        {t('clans.join.withdraw')}
                      </Button>
                    </span>
                  )}
                </li>
              ))}
            </ul>
          </Card.Body>
        </Card>
      )}

      {zaproszenia.length === 0 && prosby.length === 0 && (
        <EmptyState
          icon={IconClan}
          title={t('clans.none.title')}
          text={t('clans.none.text')}
          className="mb-4"
          action={<Link to="/klany" className="btn btn-primary btn-sm">{t('clans.directory.browse')}</Link>}
        />
      )}

      <KlanZakladanie onZalozony={onZalozony} />
    </div>
  );
}
