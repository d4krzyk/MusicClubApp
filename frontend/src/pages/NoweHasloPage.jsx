import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import Alert from 'react-bootstrap/Alert';
import Button from 'react-bootstrap/Button';
import Card from 'react-bootstrap/Card';
import Form from 'react-bootstrap/Form';
import Spinner from 'react-bootstrap/Spinner';
import { describeError } from '../api/client';
import * as konto from '../api/konto';
import { useAuth } from '../auth/AuthContext';
import Field from '../components/Field';
import { IconCheckCircle, IconCross } from '../components/Icons';

/**
 * Strona z linku resetu: /nowe-haslo?token=...
 *
 * Najpierw sprawdzamy link - zeby ktos nie wpisywal hasla dwa razy tylko po
 * to, zeby dowiedziec sie, ze link wygasl. Token znika z paska adresu.
 */
export default function NoweHasloPage() {
  const { t } = useTranslation();
  const { user, refreshUser } = useAuth();
  const navigate = useNavigate();
  const [parametry] = useSearchParams();
  const [token] = useState(() => parametry.get('token') ?? '');

  const [dlaKonta, setKonto] = useState(null);
  const [bladLinku, setBladLinku] = useState(null);
  const [haslo, setHaslo] = useState('');
  const [powtorzone, setPowtorzone] = useState('');
  const [bledy, setBledy] = useState({});
  const [blad, setBlad] = useState(null);
  const [wysylanie, setWysylanie] = useState(false);
  const [gotowe, setGotowe] = useState(false);
  const sprawdzone = useRef(false);

  useEffect(() => {
    if (sprawdzone.current) {
      return;
    }
    sprawdzone.current = true;
    navigate('/nowe-haslo', { replace: true });
    if (!token) {
      setBladLinku(t('verify.noToken'));
      return;
    }
    konto.sprawdzLinkHasla(token)
      .then(setKonto)
      .catch((problem) => setBladLinku(describeError(problem).message));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function zapisz(e) {
    e.preventDefault();
    setBledy({});
    setBlad(null);
    setWysylanie(true);
    try {
      await konto.ustawNoweHaslo(token, haslo, powtorzone);
      setGotowe(true);
      // Wszystkie sesje tego konta wlasnie wygasly - takze ta, jesli byla
      if (user) {
        refreshUser(null);
      }
    } catch (problem) {
      const szczegoly = describeError(problem);
      setBledy(szczegoly.fieldErrors);
      setBlad(szczegoly.message);
    } finally {
      setWysylanie(false);
    }
  }

  let tresc;
  if (gotowe) {
    tresc = (
      <div className="skrzynka" aria-live="polite">
        <span className="skrzynka-ikona" aria-hidden="true"><IconCheckCircle size={28} /></span>
        <h1 className="h5 mb-2">{t('reset.doneTitle')}</h1>
        <p className="mb-3">{t('reset.doneText')}</p>
        <Button
          className="w-100"
          onClick={() => navigate('/login', { state: { passwordReset: true, username: dlaKonta?.username } })}
        >
          {t('verify.signIn')}
        </Button>
      </div>
    );
  } else if (bladLinku) {
    tresc = (
      <div className="skrzynka" aria-live="polite">
        <span className="skrzynka-ikona is-blad" aria-hidden="true"><IconCross size={26} /></span>
        <h1 className="h5 mb-2">{t('reset.linkFailedTitle')}</h1>
        <p className="mb-3">{bladLinku}</p>
        <Link to="/reset-hasla" className="btn btn-primary w-100">{t('reset.askAgain')}</Link>
      </div>
    );
  } else if (!dlaKonta) {
    tresc = (
      <div className="text-center py-4 text-body-secondary">
        <Spinner animation="border" size="sm" className="me-2" />
        {t('common.loading')}
      </div>
    );
  } else {
    tresc = (
      <>
        <Card.Title as="h1" className="h4 mb-2">{t('reset.newTitle')}</Card.Title>
        <p className="text-body-secondary small">{t('reset.newIntro', { username: dlaKonta.username })}</p>
        {blad && <Alert variant="danger">{blad}</Alert>}
        <Form onSubmit={zapisz} noValidate className="tiles-in-form">
          {/* Ukryty login - menedzer hasel wie, do ktorego konta zapisac nowe haslo */}
          <input type="text" name="username" autoComplete="username" value={dlaKonta.username} readOnly hidden />
          <Field
            id="password"
            label={t('register.password')}
            typ="password"
            value={haslo}
            onChange={setHaslo}
            error={bledy.password}
            suggestion={t('register.passwordHint')}
            autoComplete="new-password"
          />
          <Field
            id="confirmPassword"
            label={t('register.confirmPassword')}
            typ="password"
            value={powtorzone}
            onChange={setPowtorzone}
            error={bledy.confirmPassword}
            autoComplete="new-password"
          />
          <Button type="submit" className="w-100" disabled={wysylanie}>{t('reset.save')}</Button>
        </Form>
        <p className="text-body-secondary small mt-3 mb-0">{t('reset.logoutEverywhere')}</p>
      </>
    );
  }

  return (
    <Card className="mx-auto" style={{ maxWidth: 420 }}>
      <Card.Body className="p-4">{tresc}</Card.Body>
    </Card>
  );
}
