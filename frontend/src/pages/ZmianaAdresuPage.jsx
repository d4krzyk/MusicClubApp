import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import Button from 'react-bootstrap/Button';
import Card from 'react-bootstrap/Card';
import Spinner from 'react-bootstrap/Spinner';
import { describeError } from '../api/client';
import * as konto from '../api/konto';
import { useAuth } from '../auth/AuthContext';
import { IconCheckCircle, IconCross, IconMail, IconShieldAlert } from '../components/Icons';

/**
 * Link ze STAREJ skrzynki przy zmianie adresu: /potwierdz-zmiane-adresu?token=...
 *
 * Tu, inaczej niz przy potwierdzeniu adresu, nic nie dzieje sie samo -
 * wlasciciel wybiera: zgoda albo "to nie ja". Dopiero klikniecie wysyla
 * decyzje, wiec skaner poczty otwierajacy link niczego nie przesadzi.
 */
export default function ZmianaAdresuPage() {
  const { t } = useTranslation();
  const { user, refreshUser } = useAuth();
  const navigate = useNavigate();
  const [parametry] = useSearchParams();
  const [token] = useState(() => parametry.get('token') ?? '');

  const [info, setInfo] = useState(null);
  const [wynik, setWynik] = useState(null);
  const [blad, setBlad] = useState(null);
  const [wysylanie, setWysylanie] = useState(false);
  const sprawdzone = useRef(false);

  useEffect(() => {
    if (sprawdzone.current) {
      return;
    }
    sprawdzone.current = true;
    navigate('/potwierdz-zmiane-adresu', { replace: true });
    if (!token) {
      setBlad(t('verify.noToken'));
      return;
    }
    konto.infoZmianyAdresu(token)
      .then(setInfo)
      .catch((problem) => setBlad(describeError(problem).message));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function zdecyduj(zgoda) {
    setWysylanie(true);
    try {
      const odpowiedz = zgoda ? await konto.zgodaNaZmianeAdresu(token) : await konto.odrzucZmianeAdresu(token);
      setWynik(odpowiedz);
      if (user) {
        if (odpowiedz.result === 'CHANGE_DENIED') {
          // Wszystkie sesje tego konta wlasnie wygasly
          refreshUser(null);
        } else {
          konto.ktoJestem().then(refreshUser).catch(() => {});
        }
      }
    } catch (problem) {
      setBlad(describeError(problem).message);
    } finally {
      setWysylanie(false);
    }
  }

  let tresc;
  if (blad) {
    tresc = (
      <>
        <span className="skrzynka-ikona is-blad" aria-hidden="true"><IconCross size={26} /></span>
        <h1 className="h5 mb-2">{t('verify.failedTitle')}</h1>
        <p className="mb-3">{blad}</p>
        <Link to={user ? '/settings' : '/login'} className="btn btn-primary w-100">
          {user ? t('verify.goToSettings') : t('verify.goToLogin')}
        </Link>
      </>
    );
  } else if (wynik?.result === 'CHANGE_DENIED') {
    tresc = (
      <>
        <span className="skrzynka-ikona is-blad" aria-hidden="true"><IconShieldAlert size={26} /></span>
        <h1 className="h5 mb-2">{t('approve.deniedTitle')}</h1>
        <p className="mb-3">{t('approve.deniedText')}</p>
        <Link to="/reset-hasla" className="btn btn-primary w-100">{t('approve.setNewPassword')}</Link>
      </>
    );
  } else if (wynik) {
    const zmieniony = wynik.result === 'CHANGED';
    tresc = (
      <>
        <span className="skrzynka-ikona" aria-hidden="true">
          {zmieniony ? <IconCheckCircle size={28} /> : <IconMail size={28} />}
        </span>
        <h1 className="h5 mb-2">{zmieniony ? t('verify.changedTitle') : t('approve.waitingTitle')}</h1>
        <p className="mb-1">{zmieniony ? t('verify.changedText') : t('approve.waitingText')}</p>
        <p className="skrzynka-adres">{wynik.email}</p>
        <Link to={user ? '/settings' : '/login'} className="btn btn-primary w-100">
          {user ? t('verify.goToSettings') : t('verify.goToLogin')}
        </Link>
      </>
    );
  } else if (!info) {
    tresc = (
      <div className="text-center py-4 text-body-secondary">
        <Spinner animation="border" size="sm" className="me-2" />
        {t('common.loading')}
      </div>
    );
  } else {
    tresc = (
      <>
        <span className="skrzynka-ikona" aria-hidden="true"><IconMail size={28} /></span>
        <h1 className="h5 mb-2">{t('approve.title')}</h1>
        <p className="mb-1">{t('approve.question', { username: info.username })}</p>
        <p className="skrzynka-adres">{info.newEmail}</p>
        <div className="d-grid gap-2">
          <Button disabled={wysylanie} onClick={() => zdecyduj(true)}>{t('approve.yes')}</Button>
          <Button variant="outline-danger" disabled={wysylanie} onClick={() => zdecyduj(false)}>
            {t('approve.no')}
          </Button>
        </div>
        <p className="text-body-secondary small mt-3 mb-0">{t('approve.noHint')}</p>
      </>
    );
  }

  return (
    <Card className="mx-auto" style={{ maxWidth: 420 }}>
      <Card.Body className="p-4 skrzynka" aria-live="polite">{tresc}</Card.Body>
    </Card>
  );
}
