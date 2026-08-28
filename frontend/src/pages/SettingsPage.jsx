import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import Card from 'react-bootstrap/Card';
import Form from 'react-bootstrap/Form';
import Button from 'react-bootstrap/Button';
import Alert from 'react-bootstrap/Alert';
import Row from 'react-bootstrap/Row';
import Col from 'react-bootstrap/Col';
import { useAuth } from '../auth/AuthContext';
import client, { describeError } from '../api/client';
import Field from '../components/Field';
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
      {/* Kolejne sekcje ustawien wchodza po kolei, tak jak karty na tablicy */}
      <Col lg={8} className="tiles-in">
        <h1 className="h4 mb-3">{t('settings.title')}</h1>

        <AvatarForm />

        {/*
          key = login. Gdy login sie zmieni, React tworzy formularz od nowa,
          wiec pola startuja z nowymi wartosciami. Bez tego stan formularza
          zostalby przy starych danych, mimo ze konto ma juz inna nazwe.
        */}
        <ProfileForm key={user.username} />
        <PasswordForm />
      </Col>
    </Row>
  );
}

/** Wgranie i usuwanie zdjecia profilowego. */
function AvatarForm() {
  const { t } = useTranslation();
  const { user, refreshUser } = useAuth();

  const [file, setFile] = useState(null);
  const [error, setError] = useState(null);
  const [message, setMessage] = useState(null);
  const [wysylanie, setWysylanie] = useState(false);

  async function wgraj(e) {
    e.preventDefault();
    if (!file) {
      return;
    }
    setError(null);
    setMessage(null);
    setWysylanie(true);

    try {
      const formData = new FormData();
      formData.append('file', file);

      const response = await client.put('/profile/avatar', formData);
      refreshUser(response.data);

      setFile(null);
      e.target.reset();
      setMessage(t('avatar.saved'));
    } catch (error) {
      const details = describeError(error);
      // Blad pliku backend przypina do pola "images" - tutaj mamy jedno pole,
      // wiec pokazujemy go po prostu jako komunikat nad formularzem
      setError(details.fieldErrors.images ?? details.message ?? t('errors.unknown'));
    } finally {
      setWysylanie(false);
    }
  }

  async function remove() {
    setError(null);
    setMessage(null);
    try {
      const response = await client.delete('/profile/avatar');
      refreshUser(response.data);
      setMessage(t('avatar.removed'));
    } catch (error) {
      setError(describeError(error).message ?? t('errors.unknown'));
    }
  }

  return (
    <Card className="mb-4">
      <Card.Body>
        <Card.Title as="h2" className="h6 text-uppercase text-body-secondary">
          {t('avatar.title')}
        </Card.Title>

        {message && <Alert variant="success">{message}</Alert>}
        {error && <Alert variant="danger">{error}</Alert>}

        <div className="d-flex align-items-center gap-3 flex-wrap">
          <Avatar avatarUrl={user.avatarUrl} username={user.username} size={80} />

          <Form onSubmit={wgraj} className="flex-grow-1">
            <Form.Group controlId="avatar" className="mb-2">
              <Form.Control
                type="file"
                accept="image/*"
                onChange={(e) => setFile(e.target.files[0] ?? null)}
              />
              <Form.Text muted>{t('avatar.hint')}</Form.Text>
            </Form.Group>

            <div className="d-flex gap-2">
              <Button type="submit" size="sm" disabled={!file || wysylanie}>
                {wysylanie ? t('avatar.uploading') : t('avatar.upload')}
              </Button>

              {user.avatarUrl && (
                <Button type="button" size="sm" variant="outline-danger" onClick={remove}>
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
function ProfileForm() {
  const { t } = useTranslation();
  const { user, updateProfile } = useAuth();

  const [data, setData] = useState({ username: user.username, email: user.email });
  const [fieldErrors, setFieldErrors] = useState({});
  const [generalError, setGeneralError] = useState(null);
  const [saved, setSaved] = useState(false);
  const [wysylanie, setWysylanie] = useState(false);

  function ustaw(field, value) {
    setData((p) => ({ ...p, [field]: value }));
    // Komunikat "zapisano" znika, gdy tylko uzytkownik znowu cos zmienia -
    // inaczej wisialby nad formularzem z niezapisanymi juz danymi
    setSaved(false);
  }

  async function submit(e) {
    e.preventDefault();
    setFieldErrors({});
    setGeneralError(null);
    setSaved(false);
    setWysylanie(true);

    try {
      await updateProfile(data);
      setSaved(true);
    } catch (error) {
      const details = describeError(error);
      setFieldErrors(details.fieldErrors);
      setGeneralError(details.message ?? (details.messageKey ? t(details.messageKey) : null));
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

        {saved && <Alert variant="success">{t('settings.profileSaved')}</Alert>}
        {generalError && <Alert variant="danger">{generalError}</Alert>}

        <Form onSubmit={submit} noValidate>
          <Field
            id="username"
            label={t('settings.username')}
            value={data.username}
            onChange={(v) => ustaw('username', v)}
            error={fieldErrors.username}
            suggestion={t('settings.usernameHint')}
            autoComplete="username"
          />

          <Field
            id="email"
            label={t('settings.email')}
            typ="email"
            value={data.email}
            onChange={(v) => ustaw('email', v)}
            error={fieldErrors.email}
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
function PasswordForm() {
  const { t } = useTranslation();
  const { changePassword } = useAuth();

  const empty = { currentPassword: '', password: '', confirmPassword: '' };
  const [data, setData] = useState(empty);
  const [fieldErrors, setFieldErrors] = useState({});
  const [generalError, setGeneralError] = useState(null);
  const [changed, setChanged] = useState(false);
  const [wysylanie, setWysylanie] = useState(false);

  function ustaw(field, value) {
    setData((p) => ({ ...p, [field]: value }));
    setChanged(false);
  }

  async function submit(e) {
    e.preventDefault();
    setFieldErrors({});
    setGeneralError(null);
    setChanged(false);
    setWysylanie(true);

    try {
      await changePassword(data);
      setChanged(true);
      // Czyscimy pola - hasla nie maja po co wisiec w formularzu po zapisie
      setData(empty);
    } catch (error) {
      const details = describeError(error);
      setFieldErrors(details.fieldErrors);
      setGeneralError(details.message ?? (details.messageKey ? t(details.messageKey) : null));
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

        {changed && <Alert variant="success">{t('settings.passwordChanged')}</Alert>}
        {generalError && <Alert variant="danger">{generalError}</Alert>}

        <Form onSubmit={submit} noValidate>
          <Field
            id="currentPassword"
            label={t('settings.currentPassword')}
            typ="password"
            value={data.currentPassword}
            onChange={(v) => ustaw('currentPassword', v)}
            error={fieldErrors.currentPassword}
            autoComplete="current-password"
          />

          <Field
            id="password"
            label={t('settings.newPassword')}
            typ="password"
            value={data.password}
            onChange={(v) => ustaw('password', v)}
            error={fieldErrors.password}
            suggestion={t('settings.newPasswordHint')}
            autoComplete="new-password"
          />

          <Field
            id="confirmPassword"
            label={t('settings.confirmNewPassword')}
            typ="password"
            value={data.confirmPassword}
            onChange={(v) => ustaw('confirmPassword', v)}
            error={fieldErrors.confirmPassword}
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
