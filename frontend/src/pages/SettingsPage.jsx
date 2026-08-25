import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import Card from 'react-bootstrap/Card';
import Form from 'react-bootstrap/Form';
import Button from 'react-bootstrap/Button';
import Alert from 'react-bootstrap/Alert';
import Row from 'react-bootstrap/Row';
import Col from 'react-bootstrap/Col';
import { useAuth } from '../auth/AuthContext';
import client, { opiszBlad } from '../api/client';
import Pole from '../components/Pole';
import Avatar from '../components/Avatar';

/**
 * Ustawienia wlasnego konta: zdjecie, dane profilu i zmiana hasla.
 *
 * <p>Trzy OSOBNE formularze, a nie jeden wielki. Powody:</p>
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
    <Row className="justify-content-center">
      <Col lg={8}>
        <h1 className="h4 mb-3">{t('settings.title')}</h1>

        <FormularzAvatara />

        {/*
          key = login. Gdy login sie zmieni, React tworzy formularz od nowa,
          wiec pola startuja z nowymi wartosciami. Bez tego stan formularza
          zostalby przy starych danych, mimo ze konto ma juz inna nazwe.
        */}
        <FormularzProfilu key={user.username} />
        <FormularzHasla />
      </Col>
    </Row>
  );
}

/** Wgranie i usuwanie zdjecia profilowego. */
function FormularzAvatara() {
  const { t } = useTranslation();
  const { user, odswiezUzytkownika } = useAuth();

  const [plik, setPlik] = useState(null);
  const [blad, setBlad] = useState(null);
  const [komunikat, setKomunikat] = useState(null);
  const [wysylanie, setWysylanie] = useState(false);

  async function wgraj(e) {
    e.preventDefault();
    if (!plik) {
      return;
    }
    setBlad(null);
    setKomunikat(null);
    setWysylanie(true);

    try {
      const formData = new FormData();
      formData.append('file', plik);

      const odpowiedz = await client.put('/profile/avatar', formData);
      odswiezUzytkownika(odpowiedz.data);

      setPlik(null);
      e.target.reset();
      setKomunikat(t('avatar.saved'));
    } catch (error) {
      const opis = opiszBlad(error);
      // Blad pliku backend przypina do pola "images" - tutaj mamy jedno pole,
      // wiec pokazujemy go po prostu jako komunikat nad formularzem
      setBlad(opis.fieldErrors.images ?? opis.message ?? t('errors.unknown'));
    } finally {
      setWysylanie(false);
    }
  }

  async function usun() {
    setBlad(null);
    setKomunikat(null);
    try {
      const odpowiedz = await client.delete('/profile/avatar');
      odswiezUzytkownika(odpowiedz.data);
      setKomunikat(t('avatar.removed'));
    } catch (error) {
      setBlad(opiszBlad(error).message ?? t('errors.unknown'));
    }
  }

  return (
    <Card className="mb-4">
      <Card.Body>
        <Card.Title as="h2" className="h6 text-uppercase text-body-secondary">
          {t('avatar.title')}
        </Card.Title>

        {komunikat && <Alert variant="success">{komunikat}</Alert>}
        {blad && <Alert variant="danger">{blad}</Alert>}

        <div className="d-flex align-items-center gap-3 flex-wrap">
          <Avatar avatarUrl={user.avatarUrl} username={user.username} rozmiar={80} />

          <Form onSubmit={wgraj} className="flex-grow-1">
            <Form.Group controlId="avatar" className="mb-2">
              <Form.Control
                type="file"
                accept="image/*"
                onChange={(e) => setPlik(e.target.files[0] ?? null)}
              />
              <Form.Text muted>{t('avatar.hint')}</Form.Text>
            </Form.Group>

            <div className="d-flex gap-2">
              <Button type="submit" size="sm" disabled={!plik || wysylanie}>
                {wysylanie ? t('avatar.uploading') : t('avatar.upload')}
              </Button>

              {user.avatarUrl && (
                <Button type="button" size="sm" variant="outline-danger" onClick={usun}>
                  {t('avatar.remove')}
                </Button>
              )}
            </div>
          </Form>
        </div>
      </Card.Body>
    </Card>
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
    <Card className="mb-4">
      <Card.Body>
        <Card.Title as="h2" className="h6 text-uppercase text-body-secondary">
          {t('settings.profile')}
        </Card.Title>

        {zapisano && <Alert variant="success">{t('settings.profileSaved')}</Alert>}
        {bladOgolny && <Alert variant="danger">{bladOgolny}</Alert>}

        <Form onSubmit={wyslij} noValidate>
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

          <Button type="submit" disabled={wysylanie}>
            {wysylanie ? t('settings.saving') : t('settings.save')}
          </Button>
        </Form>
      </Card.Body>
    </Card>
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
    <Card className="mb-4">
      <Card.Body>
        <Card.Title as="h2" className="h6 text-uppercase text-body-secondary">
          {t('settings.password')}
        </Card.Title>

        {zmieniono && <Alert variant="success">{t('settings.passwordChanged')}</Alert>}
        {bladOgolny && <Alert variant="danger">{bladOgolny}</Alert>}

        <Form onSubmit={wyslij} noValidate>
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

          <Button type="submit" disabled={wysylanie}>
            {wysylanie ? t('settings.saving') : t('settings.changePassword')}
          </Button>
        </Form>
      </Card.Body>
    </Card>
  );
}
