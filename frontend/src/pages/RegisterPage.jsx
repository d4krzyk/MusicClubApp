import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import Card from 'react-bootstrap/Card';
import Form from 'react-bootstrap/Form';
import Button from 'react-bootstrap/Button';
import Alert from 'react-bootstrap/Alert';
import { useAuth } from '../auth/AuthContext';
import { describeError } from '../api/client';
import Field from '../components/Field';
import SprawdzSkrzynke from '../components/SprawdzSkrzynke';

export default function RegisterPage() {
  const { t } = useTranslation();
  const { register } = useAuth();
  const navigate = useNavigate();

  const [data, setData] = useState({
    username: '',
    email: '',
    password: '',
    confirmPassword: '',
  });

  const [fieldErrors, setFieldErrors] = useState({});
  const [generalError, setGeneralError] = useState(null);
  const [wysylanie, setWysylanie] = useState(false);

  /* Adres, na ktory poszedl link - gdy serwer wymaga potwierdzenia. */
  const [wyslanoNa, setWyslanoNa] = useState(null);

  function ustaw(field, value) {
    setData((previous) => ({ ...previous, [field]: value }));
  }

  async function submit(e) {
    e.preventDefault();
    setFieldErrors({});
    setGeneralError(null);
    setWysylanie(true);

    try {
      const konto = await register(data);

      /*
       * Serwer z poczta czeka na klikniecie w link - zostajemy tu i mowimy,
       * gdzie go szukac. Bez poczty konto jest od razu gotowe: na logowanie.
       */
      if (konto.emailVerified === false) {
        setWyslanoNa(konto.email);
        return;
      }
      navigate('/login', { replace: true, state: { registered: true } });
    } catch (error) {
      /* Backend zwraca bledy per pole (422) - podswietlamy konkretne inputy. */
      const details = describeError(error);
      setFieldErrors(details.fieldErrors);
      setGeneralError(details.message);
    } finally {
      setWysylanie(false);
    }
  }

  if (wyslanoNa) {
    return (
      <Card className="mx-auto" style={{ maxWidth: 420 }}>
        <Card.Body className="p-4">
          <SprawdzSkrzynke
            username={data.username}
            password={data.password}
            email={wyslanoNa}
            swiezo
          />
        </Card.Body>
      </Card>
    );
  }

  return (
    <Card className="mx-auto" style={{ maxWidth: 420 }}>
      <Card.Body className="p-4">
        <Card.Title as="h1" className="h4 mb-3">
          {t('register.title')}
        </Card.Title>

        {generalError && <Alert variant="danger">{generalError}</Alert>}

        <Form onSubmit={submit} noValidate className="tiles-in-form">
          <Field
            id="username"
            label={t('register.username')}
            value={data.username}
            onChange={(v) => ustaw('username', v)}
            error={fieldErrors.username}
            suggestion={t('register.usernameHint')}
            autoComplete="username"
          />

          <Field
            id="email"
            label={t('register.email')}
            typ="email"
            value={data.email}
            onChange={(v) => ustaw('email', v)}
            error={fieldErrors.email}
            autoComplete="email"
          />

          <Field
            id="password"
            label={t('register.password')}
            typ="password"
            value={data.password}
            onChange={(v) => ustaw('password', v)}
            error={fieldErrors.password}
            suggestion={t('register.passwordHint')}
            autoComplete="new-password"
          />

          <Field
            id="confirmPassword"
            label={t('register.confirmPassword')}
            typ="password"
            value={data.confirmPassword}
            onChange={(v) => ustaw('confirmPassword', v)}
            error={fieldErrors.confirmPassword}
            autoComplete="new-password"
          />

          <Button type="submit" className="w-100" disabled={wysylanie}>
            {wysylanie ? t('register.submitting') : t('register.submit')}
          </Button>
        </Form>

        <p className="text-center text-body-secondary small mt-3 mb-0">
          {t('register.haveAccount')} <Link to="/login">{t('register.goToLogin')}</Link>
        </p>
      </Card.Body>
    </Card>
  );
}
