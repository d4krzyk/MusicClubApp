import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import Card from 'react-bootstrap/Card';
import Form from 'react-bootstrap/Form';
import Button from 'react-bootstrap/Button';
import Alert from 'react-bootstrap/Alert';
import Row from 'react-bootstrap/Row';
import Col from 'react-bootstrap/Col';
import Modal from 'react-bootstrap/Modal';
import { useAuth } from '../auth/AuthContext';
import { describeError } from '../api/client';
import {
  usunAwatar, usunKonto, usunWszystkiePosty, ustawAwatar,
} from '../api/konto';
import Field from '../components/Field';
import Avatar from '../components/Avatar';
import LanguageSwitch from '../components/LanguageSwitch';
import ThemeToggle from '../components/ThemeToggle';

/** Ustawienia wlasnego konta: zdjecie, dane profilu i zmiana hasla. */
export default function SettingsPage() {
  const { t } = useTranslation();
  const { user } = useAuth();

  return (
    <Row className="justify-content-center">
      {/* Kolejne sekcje ustawien wchodza po kolei, tak jak karty na tablicy */}
      <Col lg={8} className="tiles-in">
        <h1 className="h4 mb-3">{t('settings.title')}</h1>

        <AvatarForm />

        {/* key = login. */}
        <ProfileForm key={user.username} />
        <PasswordForm />
        <WygladIJezyk />
        <StrefaNieodwracalna />
      </Col>
    </Row>
  );
}

/**
 * Jezyk i motyw. Oba stały wczesniej w gornym pasku i zabierały mu jedna trzecia
 * szerokosci; ustawia sie je raz i wraca do nich rzadko, wiec ich miejsce jest tutaj.
 * Niezalogowani maja przelacznik jezyka w stopce - do ustawien by nie doszli.
 */
function WygladIJezyk() {
  const { t } = useTranslation();

  return (
    <Card className="mb-4">
      <Card.Body>
        <Card.Title as="h2" className="h6 text-uppercase text-body-secondary">
          {t('settings.appearance')}
        </Card.Title>

        <div className="ustawienie-wiersz">
          <div>
            <div className="fw-semibold">{t('settings.language')}</div>
            <div className="text-body-secondary small">{t('settings.languageHint')}</div>
          </div>
          <LanguageSwitch />
        </div>

        <hr className="my-3" />

        <div className="ustawienie-wiersz">
          <div>
            <div className="fw-semibold">{t('settings.theme')}</div>
            <div className="text-body-secondary small">{t('settings.themeHint')}</div>
          </div>
          <ThemeToggle />
        </div>
      </Card.Body>
    </Card>
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
      refreshUser(await ustawAwatar(file));

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
      refreshUser(await usunAwatar());
      setMessage(t('avatar.removed'));
    } catch (error) {
      setError(describeError(error).message);
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
      setGeneralError(details.message);
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
      setGeneralError(details.message);
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

/**
 * Operacje, ktorych nie da sie cofnac - kazda za haslem.
 *
 * <p>Haslo, a nie samo potwierdzenie: sesja moze byc otwarta na cudzym
 * komputerze, a odzyskac tego nie ma jak.</p>
 */
function StrefaNieodwracalna() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { logout } = useAuth();

  /** Ktora operacja czeka na potwierdzenie: 'posty', 'konto' albo null. */
  const [wybor, setWybor] = useState(null);
  const [haslo, setHaslo] = useState('');
  const [pracuje, setPracuje] = useState(false);
  const [blad, setBlad] = useState(null);
  const [komunikat, setKomunikat] = useState(null);

  function otworz(operacja) {
    setWybor(operacja);
    setHaslo('');
    setBlad(null);
  }

  async function potwierdz(event) {
    event.preventDefault();
    setPracuje(true);
    setBlad(null);
    try {
      if (wybor === 'posty') {
        const ile = await usunWszystkiePosty(haslo);
        setKomunikat(t('settings.deletePostsDone', { count: ile }));
        setWybor(null);
      } else {
        await usunKonto(haslo);
        /*
         * Konta juz nie ma, wiec czyscimy je takze u siebie - inaczej
         * aplikacja rysowalaby menu nieistniejacego uzytkownika az do
         * odswiezenia strony.
         */
        await logout();
        navigate('/login', { replace: true });
      }
    } catch (problem) {
      const szczegoly = describeError(problem);
      setBlad(szczegoly.fieldErrors.currentPassword ?? szczegoly.message);
    } finally {
      setPracuje(false);
    }
  }

  return (
    <>
      <Card className="mb-4 border-danger-subtle">
        <Card.Body>
          <Card.Title as="h2" className="h6 text-uppercase text-danger-emphasis">
            {t('settings.dangerTitle')}
          </Card.Title>
          <p className="text-body-secondary small">{t('settings.dangerIntro')}</p>

          {komunikat && <Alert variant="success" className="py-2">{komunikat}</Alert>}

          <div className="d-flex flex-column gap-3">
            <div>
              <p className="fw-semibold mb-1">{t('settings.deletePostsTitle')}</p>
              <p className="text-body-secondary small mb-2">{t('settings.deletePostsText')}</p>
              <Button variant="outline-danger" size="sm" onClick={() => otworz('posty')}>
                {t('settings.deletePostsAction')}
              </Button>
            </div>

            <hr className="my-0" />

            <div>
              <p className="fw-semibold mb-1">{t('settings.deleteAccountTitle')}</p>
              <p className="text-body-secondary small mb-2">{t('settings.deleteAccountText')}</p>
              <Button variant="danger" size="sm" onClick={() => otworz('konto')}>
                {t('settings.deleteAccountAction')}
              </Button>
            </div>
          </div>
        </Card.Body>
      </Card>

      <Modal show={Boolean(wybor)} onHide={() => setWybor(null)} centered>
        <Form onSubmit={potwierdz}>
          <Modal.Header closeButton>
            <Modal.Title className="h6">
              {wybor === 'posty'
                ? t('settings.deletePostsTitle')
                : t('settings.deleteAccountTitle')}
            </Modal.Title>
          </Modal.Header>

          <Modal.Body>
            <p className="small">
              {wybor === 'posty'
                ? t('settings.deletePostsText')
                : t('settings.deleteAccountText')}
            </p>

            {blad && <Alert variant="danger" className="py-2">{blad}</Alert>}

            <Field
              id="hasloPotwierdzenia"
              label={t('settings.passwordLabel')}
              typ="password"
              value={haslo}
              onChange={setHaslo}
              suggestion={t('settings.passwordHint')}
              autoComplete="current-password"
            />
          </Modal.Body>

          <Modal.Footer>
            <Button variant="outline-secondary" onClick={() => setWybor(null)}>
              {t('common.cancel')}
            </Button>
            <Button type="submit" variant="danger" disabled={pracuje || !haslo}>
              {pracuje ? t('settings.working') : t('settings.confirm')}
            </Button>
          </Modal.Footer>
        </Form>
      </Modal>
    </>
  );
}
