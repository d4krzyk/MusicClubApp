import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import Alert from 'react-bootstrap/Alert';
import Button from 'react-bootstrap/Button';
import Card from 'react-bootstrap/Card';
import Form from 'react-bootstrap/Form';
import Spinner from 'react-bootstrap/Spinner';
import { describeError } from '../api/client';
import * as klany from '../api/klany';
import ClanBadge from '../components/ClanBadge';
import EmptyState from '../components/EmptyState';
import { IconClan } from '../components/Icons';
import KlanCzat from '../components/klan/KlanCzat';
import KlanCzlonkowie from '../components/klan/KlanCzlonkowie';
import KlanPosty from '../components/klan/KlanPosty';
import KlanUstawienia from '../components/klan/KlanUstawienia';
import { formatDate } from '../utils/dates';

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
      } else {
        const moj = await klany.moj();
        setKlan(moj.clan);
        setZaproszenia(moj.invitations);
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
    return <BezKlanu zaproszenia={zaproszenia} onZmiana={poZmianieMojego} onZalozony={setKlan} />;
  }

  return (
    <Klan
      klan={klan}
      zakladka={zakladka}
      onZakladka={setZakladka}
      onZmiana={setKlan}
      onMoj={poZmianieMojego}
      onRozwiazany={() => { setKlan(null); navigate('/klan'); wczytaj(); }}
    />
  );
}

function Klan({ klan, zakladka, onZakladka, onZmiana, onMoj, onRozwiazany }) {
  const { t, i18n } = useTranslation();
  const [blad, setBlad] = useState(null);
  const czlonek = klan.myRole != null;
  const zarzad = klan.myRole === 'FOUNDER' || klan.myRole === 'ADMIN';
  const aktywna = zakladka ?? (klan.canSeeContent ? 'czat' : 'czlonkowie');

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
    klan.canSeeContent && { klucz: 'czat', tekst: t('clans.tabs.chat') },
    klan.canSeeContent && { klucz: 'posty', tekst: t('clans.tabs.posts') },
    { klucz: 'czlonkowie', tekst: t('clans.tabs.members') },
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
          {klan.description && <p className="klan-opis mb-2">{klan.description}</p>}

          <div className="d-flex gap-2 flex-wrap">
            {czlonek && (
              <Button variant="outline-secondary" size="sm" onClick={odejdz}>{t('clans.leave')}</Button>
            )}
            {klan.viewingAsAdmin && (
              <Button variant="outline-danger" size="sm" onClick={rozwiazJakoAdmin}>{t('clans.adminDisband')}</Button>
            )}
          </div>
        </div>
      </header>

      {blad && <Alert variant="danger" className="mt-3 mb-0">{blad}</Alert>}

      {klan.viewingAsAdmin && (
        <Alert variant="warning" className="mt-3 mb-0 klan-uwaga">{t('clans.adminBanner')}</Alert>
      )}
      {!czlonek && !klan.viewingAsAdmin && (
        <Alert variant="secondary" className="mt-3 mb-0 klan-uwaga">{t('clans.inviteOnly')}</Alert>
      )}
      {czlonek && <p className="small text-body-secondary mt-3 mb-0">{t('clans.moderationNotice')}</p>}

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
          </Button>
        ))}
      </nav>

      {aktywna === 'czat' && klan.canSeeContent && <KlanCzat klan={klan} />}
      {aktywna === 'posty' && klan.canSeeContent && <KlanPosty klan={klan} />}
      {aktywna === 'czlonkowie' && <KlanCzlonkowie klan={klan} onZmiana={onZmiana} />}
      {aktywna === 'ustawienia' && zarzad && (
        <KlanUstawienia klan={klan} onZmiana={onZmiana} onRozwiazany={onRozwiazany} />
      )}
    </div>
  );
}

/** Kto nie jest w zadnym klanie: zaproszenia, ktore na niego czekaja, i zakladanie wlasnego. */
function BezKlanu({ zaproszenia, onZmiana, onZalozony }) {
  const { t } = useTranslation();
  const [zajety, setZajety] = useState(false);
  const [blad, setBlad] = useState(null);
  const [nazwa, setNazwa] = useState('');
  const [skrot, setSkrot] = useState('');
  const [opis, setOpis] = useState('');

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

  async function zaloz(e) {
    e.preventDefault();
    setZajety(true);
    setBlad(null);
    try {
      onZalozony(await klany.zaloz({ name: nazwa, tag: skrot, description: opis }));
    } catch (problem) {
      setBlad(describeError(problem).message);
    } finally {
      setZajety(false);
    }
  }

  return (
    <div className="klan-strona mx-auto">
      <h1 className="h4 mb-3">{t('clans.title')}</h1>
      {blad && <Alert variant="danger">{blad}</Alert>}

      {zaproszenia.length > 0 && (
        <Card className="mb-4">
          <Card.Body>
            <Card.Title as="h2" className="h6 text-uppercase text-body-secondary">{t('clans.invitations.title')}</Card.Title>
            <ul className="list-unstyled mb-0">
              {zaproszenia.map((z) => (
                <li key={z.id} className="klan-czlonek">
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
                </li>
              ))}
            </ul>
          </Card.Body>
        </Card>
      )}

      {zaproszenia.length === 0 && (
        <EmptyState icon={IconClan} title={t('clans.none.title')} text={t('clans.none.text')} className="mb-4" />
      )}

      <Card>
        <Card.Body>
          <Card.Title as="h2" className="h6 text-uppercase text-body-secondary">{t('clans.create.title')}</Card.Title>
          <p className="small text-body-secondary">{t('clans.create.hint')}</p>
          <Form onSubmit={zaloz} noValidate>
            <div className="row g-2 mb-3">
              <div className="col-12 col-sm-8">
                <Form.Label htmlFor="nowy-klan-nazwa">{t('clans.create.name')}</Form.Label>
                <Form.Control id="nowy-klan-nazwa" value={nazwa} maxLength={32} onChange={(e) => setNazwa(e.target.value)} />
              </div>
              <div className="col-12 col-sm-4">
                <Form.Label htmlFor="nowy-klan-skrot">{t('clans.create.tag')}</Form.Label>
                <Form.Control id="nowy-klan-skrot" value={skrot} maxLength={5} autoCapitalize="characters"
                  onChange={(e) => setSkrot(e.target.value.toUpperCase())} />
                <Form.Text>{t('clans.create.tagHint')}</Form.Text>
              </div>
            </div>
            <Form.Label htmlFor="nowy-klan-opis">{t('clans.create.description')}</Form.Label>
            <Form.Control id="nowy-klan-opis" as="textarea" rows={3} maxLength={300} value={opis}
              onChange={(e) => setOpis(e.target.value)} className="mb-3" />
            <Button type="submit" disabled={zajety || !nazwa.trim() || !skrot.trim()}>{t('clans.create.submit')}</Button>
          </Form>
        </Card.Body>
      </Card>
    </div>
  );
}
