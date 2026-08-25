import { useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import Card from 'react-bootstrap/Card';
import Form from 'react-bootstrap/Form';
import Button from 'react-bootstrap/Button';
import Alert from 'react-bootstrap/Alert';
import { useAuth } from '../auth/AuthContext';
import { opiszBlad } from '../api/client';
import Pole from '../components/Pole';

export default function LoginPage() {
  const { t } = useTranslation();
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [rememberMe, setRememberMe] = useState(false);

  const [bledyPol, setBledyPol] = useState({});
  const [bladOgolny, setBladOgolny] = useState(null);
  const [wysylanie, setWysylanie] = useState(false);

  // Komunikat po udanej rejestracji - przekazany przez RegisterPage
  const komunikatPoRejestracji = location.state?.zarejestrowano;

  async function wyslij(e) {
    e.preventDefault();

    // Czyscimy poprzednie bledy, zeby nie zostawaly na ekranie po poprawce
    setBledyPol({});
    setBladOgolny(null);
    setWysylanie(true);

    try {
      await login(username, password, rememberMe);

      /*
       * Wracamy tam, skad uzytkownik zostal odeslany na logowanie
       * (np. probowal wejsc na /users). Gdy wszedl tu sam - na strone glowna.
       * replace: true usuwa ekran logowania z historii, wiec "wstecz"
       * nie cofa do formularza po zalogowaniu.
       */
      navigate(location.state?.from ?? '/', { replace: true });
    } catch (error) {
      const opis = opiszBlad(error);
      setBledyPol(opis.fieldErrors);
      setBladOgolny(opis.message ?? (opis.messageKey ? t(opis.messageKey) : null));
    } finally {
      // finally - zeby przycisk odblokowal sie takze po bledzie
      setWysylanie(false);
    }
  }

  return (
    <Card className="mx-auto" style={{ maxWidth: 420 }}>
      <Card.Body className="p-4">
        <Card.Title as="h1" className="h4 mb-3">
          {t('login.title')}
        </Card.Title>

        {komunikatPoRejestracji && <Alert variant="success">{t('login.registered')}</Alert>}
        {bladOgolny && <Alert variant="danger">{bladOgolny}</Alert>}

        <Form onSubmit={wyslij} noValidate>
          <Pole
            id="username"
            label={t('login.username')}
            wartosc={username}
            onChange={setUsername}
            blad={bledyPol.username}
            autoComplete="username"
          />

          <Pole
            id="password"
            label={t('login.password')}
            typ="password"
            wartosc={password}
            onChange={setPassword}
            blad={bledyPol.password}
            autoComplete="current-password"
          />

          {/* Wymaganie nr 17 - "zapamietaj mnie" */}
          <Form.Check
            className="mb-3"
            id="rememberMe"
            label={t('login.rememberMe')}
            checked={rememberMe}
            onChange={(e) => setRememberMe(e.target.checked)}
          />

          {/* disabled podczas wysylania chroni przed podwojnym klinieciem */}
          <Button type="submit" className="w-100" disabled={wysylanie}>
            {wysylanie ? t('login.submitting') : t('login.submit')}
          </Button>
        </Form>

        <p className="text-center text-body-secondary small mt-3 mb-0">
          {t('login.noAccount')} <Link to="/register">{t('login.goToRegister')}</Link>
        </p>
      </Card.Body>
    </Card>
  );
}
