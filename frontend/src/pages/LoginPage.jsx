import { useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
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
      // messageKey jest null, gdy serwer przyslal gotowy tekst albo gdy
      // bledy dotycza konkretnych pol i banner jest zbedny
      setBladOgolny(opis.message ?? (opis.messageKey ? t(opis.messageKey) : null));
    } finally {
      // finally - zeby przycisk odblokowal sie takze po bledzie
      setWysylanie(false);
    }
  }

  return (
    <div className="karta waska">
      <h1>{t('login.title')}</h1>

      {komunikatPoRejestracji && <p className="sukces">{t('login.registered')}</p>}
      {bladOgolny && <p className="blad-ogolny" role="alert">{bladOgolny}</p>}

      <form onSubmit={wyslij} noValidate>
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
        <label className="pole-checkbox">
          <input
            type="checkbox"
            checked={rememberMe}
            onChange={(e) => setRememberMe(e.target.checked)}
          />
          {t('login.rememberMe')}
        </label>

        {/* disabled podczas wysylania chroni przed podwojnym klinieciem */}
        <button type="submit" className="przycisk" disabled={wysylanie}>
          {wysylanie ? t('login.submitting') : t('login.submit')}
        </button>
      </form>

      <p className="pod-formularzem">
        {t('login.noAccount')} <Link to="/register">{t('login.goToRegister')}</Link>
      </p>
    </div>
  );
}
