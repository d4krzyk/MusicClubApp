import { useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import Card from 'react-bootstrap/Card';
import Form from 'react-bootstrap/Form';
import Button from 'react-bootstrap/Button';
import Alert from 'react-bootstrap/Alert';
import { useAuth } from '../auth/AuthContext';
import { describeError } from '../api/client';
import Field from '../components/Field';
import SprawdzSkrzynke from '../components/SprawdzSkrzynke';
import useInfoSerwera from '../hooks/useInfoSerwera';

export default function LoginPage() {
  const { t } = useTranslation();
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const info = useInfoSerwera();

  // Po kliknieciu w link potwierdzajacy strona potwierdzenia podaje tu login
  const [username, setUsername] = useState(location.state?.username ?? '');
  const [password, setPassword] = useState('');
  const [rememberMe, setRememberMe] = useState(false);

  const [fieldErrors, setFieldErrors] = useState({});
  const [generalError, setGeneralError] = useState(null);
  const [wysylanie, setWysylanie] = useState(false);

  /* Haslo dobre, ale adres niepotwierdzony - tekst z serwera z zamaskowanym adresem. */
  const [niepotwierdzony, setNiepotwierdzony] = useState(null);

  // Komunikat po udanej rejestracji - przekazany przez RegisterPage
  const messageAfterRegister = location.state?.registered;

  async function submit(e) {
    e.preventDefault();

    // Czyscimy poprzednie bledy, zeby nie zostawaly na ekranie po poprawce
    setFieldErrors({});
    setGeneralError(null);
    setWysylanie(true);

    try {
      await login(username, password, rememberMe);

      /*
       * Wracamy tam, skad uzytkownik zostal odeslany na logowanie (np. probowal wejsc na /users).
       */
      navigate(location.state?.from ?? '/', { replace: true });
    } catch (error) {
      const details = describeError(error);
      if (details.code === 'EMAIL_NOT_VERIFIED') {
        setNiepotwierdzony(details.message);
        return;
      }
      setFieldErrors(details.fieldErrors);
      setGeneralError(details.message);
    } finally {
      // finally - zeby przycisk odblokowal sie takze po bledzie
      setWysylanie(false);
    }
  }

  if (niepotwierdzony) {
    return (
      <Card className="mx-auto" style={{ maxWidth: 420 }}>
        <Card.Body className="p-4">
          <SprawdzSkrzynke
            username={username}
            password={password}
            opis={niepotwierdzony}
            onBack={() => setNiepotwierdzony(null)}
          />
        </Card.Body>
      </Card>
    );
  }

  return (
    <Card className="mx-auto" style={{ maxWidth: 420 }}>
      <Card.Body className="p-4">
        <Card.Title as="h1" className="h4 mb-3">
          {t('login.title')}
        </Card.Title>

        {messageAfterRegister && <Alert variant="success">{t('login.registered')}</Alert>}
        {location.state?.verified && <Alert variant="success">{t('login.verified')}</Alert>}
        {location.state?.passwordReset && <Alert variant="success">{t('login.passwordReset')}</Alert>}
        {generalError && <Alert variant="danger">{generalError}</Alert>}

        <Form onSubmit={submit} noValidate className="tiles-in-form">
          <Field
            id="username"
            label={t('login.username')}
            value={username}
            onChange={setUsername}
            error={fieldErrors.username}
            autoComplete="username"
          />

          <Field
            id="password"
            label={t('login.password')}
            typ="password"
            value={password}
            onChange={setPassword}
            error={fieldErrors.password}
            autoComplete="current-password"
          />

          {/* Bez poczty link nie mialby jak dojsc - wtedy nie kusimy */}
          {info?.mailEnabled && (
            <div className="text-end small mb-2 mt-n2">
              <Link to="/reset-hasla">{t('login.forgot')}</Link>
            </div>
          )}

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
