import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useAuth } from '../auth/AuthContext';
import { opiszBlad } from '../api/client';
import Pole from '../components/Pole';

/**
 * Ustawienia wlasnego konta: dane profilu oraz zmiana hasla.
 *
 * <p>Dwa OSOBNE formularze, a nie jeden wielki. Powody:</p>
 * <ul>
 *   <li>zmiana hasla wymaga podania obecnego hasla, a zmiana e-maila nie -
 *       w jednym formularzu trzeba by pytac o haslo takze przy poprawianiu
 *       literowki w adresie,</li>
 *   <li>bledy i komunikaty o sukcesie dotycza wtedy tej czesci, ktorej
 *       naprawde dotycza.</li>
 * </ul>
 */
export default function SettingsPage() {
  const { t } = useTranslation();
  const { user } = useAuth();

  return (
    <div className="karta">
      <h1>{t('settings.title')}</h1>

      {/*
        key = login. Gdy login sie zmieni, React tworzy formularz od nowa,
        wiec pola startuja z nowymi wartosciami. Bez tego stan formularza
        zostalby przy starych danych, mimo ze konto ma juz inna nazwe.
      */}
      <FormularzProfilu key={user.username} />
      <FormularzHasla />
    </div>
  );
}

/** Zmiana loginu i adresu e-mail. */
function FormularzProfilu() {
  const { t } = useTranslation();
  const { user, updateProfile } = useAuth();

  const [dane, setDane] = useState({ username: user.username, email: user.email });
  const [bledyPol, setBledyPol] = useState({});
  const [bladOgolny, setBladOgolny] = useState(null);
  const [zapisano, setZapisano] = useState(false);
  const [wysylanie, setWysylanie] = useState(false);

  function ustaw(pole, wartosc) {
    setDane((p) => ({ ...p, [pole]: wartosc }));
    // Komunikat "zapisano" znika, gdy tylko uzytkownik znowu cos zmienia -
    // inaczej wisialby nad formularzem z niezapisanymi juz danymi
    setZapisano(false);
  }

  async function wyslij(e) {
    e.preventDefault();
    setBledyPol({});
    setBladOgolny(null);
    setZapisano(false);
    setWysylanie(true);

    try {
      await updateProfile(dane);
      setZapisano(true);
    } catch (error) {
      const opis = opiszBlad(error);
      setBledyPol(opis.fieldErrors);
      setBladOgolny(opis.message ?? (opis.messageKey ? t(opis.messageKey) : null));
    } finally {
      setWysylanie(false);
    }
  }

  return (
    <section>
      <h2>{t('settings.profile')}</h2>

      {zapisano && <p className="sukces">{t('settings.profileSaved')}</p>}
      {bladOgolny && <p className="blad-ogolny" role="alert">{bladOgolny}</p>}

      <form onSubmit={wyslij} noValidate>
        <Pole
          id="username"
          label={t('settings.username')}
          wartosc={dane.username}
          onChange={(v) => ustaw('username', v)}
          blad={bledyPol.username}
          podpowiedz={t('settings.usernameHint')}
          autoComplete="username"
        />

        <Pole
          id="email"
          label={t('settings.email')}
          typ="email"
          wartosc={dane.email}
          onChange={(v) => ustaw('email', v)}
          blad={bledyPol.email}
          autoComplete="email"
        />

        <button type="submit" className="przycisk" disabled={wysylanie}>
          {wysylanie ? t('settings.saving') : t('settings.save')}
        </button>
      </form>
    </section>
  );
}

/** Zmiana hasla - wymaga podania obecnego. */
function FormularzHasla() {
  const { t } = useTranslation();
  const { changePassword } = useAuth();

  const pusty = { currentPassword: '', password: '', confirmPassword: '' };
  const [dane, setDane] = useState(pusty);
  const [bledyPol, setBledyPol] = useState({});
  const [bladOgolny, setBladOgolny] = useState(null);
  const [zmieniono, setZmieniono] = useState(false);
  const [wysylanie, setWysylanie] = useState(false);

  function ustaw(pole, wartosc) {
    setDane((p) => ({ ...p, [pole]: wartosc }));
    setZmieniono(false);
  }

  async function wyslij(e) {
    e.preventDefault();
    setBledyPol({});
    setBladOgolny(null);
    setZmieniono(false);
    setWysylanie(true);

    try {
      await changePassword(dane);
      setZmieniono(true);
      // Czyscimy pola - hasla nie maja po co wisiec w formularzu po zapisie
      setDane(pusty);
    } catch (error) {
      const opis = opiszBlad(error);
      setBledyPol(opis.fieldErrors);
      setBladOgolny(opis.message ?? (opis.messageKey ? t(opis.messageKey) : null));
    } finally {
      setWysylanie(false);
    }
  }

  return (
    <section>
      <h2>{t('settings.password')}</h2>

      {zmieniono && <p className="sukces">{t('settings.passwordChanged')}</p>}
      {bladOgolny && <p className="blad-ogolny" role="alert">{bladOgolny}</p>}

      <form onSubmit={wyslij} noValidate>
        <Pole
          id="currentPassword"
          label={t('settings.currentPassword')}
          typ="password"
          wartosc={dane.currentPassword}
          onChange={(v) => ustaw('currentPassword', v)}
          blad={bledyPol.currentPassword}
          autoComplete="current-password"
        />

        <Pole
          id="password"
          label={t('settings.newPassword')}
          typ="password"
          wartosc={dane.password}
          onChange={(v) => ustaw('password', v)}
          blad={bledyPol.password}
          podpowiedz={t('settings.newPasswordHint')}
          autoComplete="new-password"
        />

        <Pole
          id="confirmPassword"
          label={t('settings.confirmNewPassword')}
          typ="password"
          wartosc={dane.confirmPassword}
          onChange={(v) => ustaw('confirmPassword', v)}
          blad={bledyPol.confirmPassword}
          autoComplete="new-password"
        />

        <button type="submit" className="przycisk" disabled={wysylanie}>
          {wysylanie ? t('settings.saving') : t('settings.changePassword')}
        </button>
      </form>
    </section>
  );
}
