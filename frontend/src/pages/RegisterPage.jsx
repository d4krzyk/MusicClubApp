import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
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
      // messageKey jest null, gdy serwer przyslal gotowy tekst albo gdy
      // bledy dotycza konkretnych pol i banner jest zbedny
      setBladOgolny(opis.message ?? (opis.messageKey ? t(opis.messageKey) : null));
    } finally {
      setWysylanie(false);
    }
  }

  return (
    <div className="karta waska">
      <h1>{t('register.title')}</h1>

      {bladOgolny && <p className="blad-ogolny" role="alert">{bladOgolny}</p>}

      <form onSubmit={wyslij} noValidate>
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

        <button type="submit" className="przycisk" disabled={wysylanie}>
          {wysylanie ? t('register.submitting') : t('register.submit')}
        </button>
      </form>

      <p className="pod-formularzem">
        {t('register.haveAccount')} <Link to="/login">{t('register.goToLogin')}</Link>
      </p>
    </div>
  );
}
