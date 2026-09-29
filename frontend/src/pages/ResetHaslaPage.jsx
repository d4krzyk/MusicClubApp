import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import Alert from 'react-bootstrap/Alert';
import Button from 'react-bootstrap/Button';
import Card from 'react-bootstrap/Card';
import Form from 'react-bootstrap/Form';
import { describeError } from '../api/client';
import * as konto from '../api/konto';
import Field from '../components/Field';
import { IconMail } from '../components/Icons';
import useInfoSerwera from '../hooks/useInfoSerwera';

/**
 * "Nie pamietam hasla". Odpowiedz jest zawsze ta sama - serwer nie mowi,
 * czy konto z tym adresem istnieje - wiec i strona mowi "jesli istnieje".
 */
export default function ResetHaslaPage() {
  const { t } = useTranslation();
  const info = useInfoSerwera();
  const [email, setEmail] = useState('');
  const [wyslano, setWyslano] = useState(null);
  const [bledy, setBledy] = useState({});
  const [blad, setBlad] = useState(null);
  const [wysylanie, setWysylanie] = useState(false);

  async function wyslij(e) {
    e.preventDefault();
    setBledy({});
    setBlad(null);
    setWysylanie(true);
    try {
      await konto.poprosONoweHaslo(email.trim());
      setWyslano(email.trim());
    } catch (problem) {
      const szczegoly = describeError(problem);
      setBledy(szczegoly.fieldErrors);
      setBlad(szczegoly.message);
    } finally {
      setWysylanie(false);
    }
  }

  return (
    <Card className="mx-auto" style={{ maxWidth: 420 }}>
      <Card.Body className="p-4">
        {wyslano ? (
          <div className="skrzynka" aria-live="polite">
            <span className="skrzynka-ikona" aria-hidden="true"><IconMail size={28} /></span>
            <h1 className="h5 mb-2">{t('reset.sentTitle')}</h1>
            <p className="mb-1">{t('reset.sentText')}</p>
            <p className="skrzynka-adres">{wyslano}</p>
            <p className="text-body-secondary small mb-3">{t('reset.sentHint')}</p>
            <Link to="/login" className="btn btn-outline-primary w-100">{t('verify.goToLogin')}</Link>
          </div>
        ) : (
          <>
            <Card.Title as="h1" className="h4 mb-2">{t('reset.title')}</Card.Title>
            <p className="text-body-secondary small">{t('reset.intro')}</p>
            {info && !info.mailEnabled && <Alert variant="warning" className="small">{t('reset.noMail')}</Alert>}
            {blad && <Alert variant="danger">{blad}</Alert>}
            <Form onSubmit={wyslij} noValidate className="tiles-in-form">
              <Field
                id="email"
                label={t('register.email')}
                typ="email"
                value={email}
                onChange={setEmail}
                error={bledy.email}
                autoComplete="email"
              />
              <Button type="submit" className="w-100" disabled={wysylanie || !email.trim() || info?.mailEnabled === false}>
                {t('reset.submit')}
              </Button>
            </Form>
            <p className="text-center text-body-secondary small mt-3 mb-0">
              <Link to="/login">{t('verify.backToLogin')}</Link>
            </p>
          </>
        )}
      </Card.Body>
    </Card>
  );
}
