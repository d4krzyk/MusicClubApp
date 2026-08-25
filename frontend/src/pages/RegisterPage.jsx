import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import Card from 'react-bootstrap/Card';
import Form from 'react-bootstrap/Form';
import Button from 'react-bootstrap/Button';
import Alert from 'react-bootstrap/Alert';
import { useAuth } from '../auth/AuthContext';
import { opiszBlad } from '../api/client';
import Pole from '../components/Pole';

export default function RegisterPage() {
  const { t } = useTranslation();
  const { register } = useAuth();
  const navigate = useNavigate();

  const [dane, setDane] = useState({
    username: '',
    email: '',
    password: '',
    confirmPassword: '',
  });

  const [bledyPol, setBledyPol] = useState({});
  const [bladOgolny, setBladOgolny] = useState(null);
  const [wysylanie, setWysylanie] = useState(false);

  function ustaw(pole, wartosc) {
    setDane((poprzednie) => ({ ...poprzednie, [pole]: wartosc }));
  }

  async function wyslij(e) {
    e.preventDefault();
    setBledyPol({});
    setBladOgolny(null);
    setWysylanie(true);

    try {
      await register(dane);

      /*
       * Rejestracja nie loguje automatycznie - backend tylko zaklada konto.
       * Przenosimy wiec na ekran logowania i przekazujemy informacje,
       * zeby pokazal komunikat o sukcesie zamiast pustego formularza
       * bez wyjasnienia, co sie stalo.
       */
      navigate('/login', { replace: true, state: { zarejestrowano: true } });
    } catch (error) {
      /*
       * Backend zwraca bledy per pole (422) - podswietlamy konkretne inputy.
       * Zajety login lub e-mail (409) trafia do komunikatu ogolnego.
       * Oba teksty przychodza juz w wybranym jezyku, bo wysylamy
       * naglowek Accept-Language.
       */
      const opis = opiszBlad(error);
      setBledyPol(opis.fieldErrors);
      setBladOgolny(opis.message ?? (opis.messageKey ? t(opis.messageKey) : null));
    } finally {
      setWysylanie(false);
    }
  }

  return (
    <Card className="mx-auto" style={{ maxWidth: 420 }}>
      <Card.Body className="p-4">
        <Card.Title as="h1" className="h4 mb-3">
          {t('register.title')}
        </Card.Title>

        {bladOgolny && <Alert variant="danger">{bladOgolny}</Alert>}

        <Form onSubmit={wyslij} noValidate>
          <Pole
            id="username"
            label={t('register.username')}
            wartosc={dane.username}
            onChange={(v) => ustaw('username', v)}
            blad={bledyPol.username}
            podpowiedz={t('register.usernameHint')}
            autoComplete="username"
          />

          <Pole
            id="email"
            label={t('register.email')}
            typ="email"
            wartosc={dane.email}
            onChange={(v) => ustaw('email', v)}
            blad={bledyPol.email}
            autoComplete="email"
          />

          <Pole
            id="password"
            label={t('register.password')}
            typ="password"
            wartosc={dane.password}
            onChange={(v) => ustaw('password', v)}
            blad={bledyPol.password}
            podpowiedz={t('register.passwordHint')}
            autoComplete="new-password"
          />

          <Pole
            id="confirmPassword"
            label={t('register.confirmPassword')}
            typ="password"
            wartosc={dane.confirmPassword}
            onChange={(v) => ustaw('confirmPassword', v)}
            blad={bledyPol.confirmPassword}
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
