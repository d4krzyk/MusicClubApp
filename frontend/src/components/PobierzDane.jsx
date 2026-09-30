import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import Alert from 'react-bootstrap/Alert';
import Button from 'react-bootstrap/Button';
import Card from 'react-bootstrap/Card';
import Form from 'react-bootstrap/Form';
import { describeError } from '../api/client';
import * as konto from '../api/konto';

/**
 * "Twoje dane": archiwum ZIP z wlasnymi danymi (prawo dostepu i przenoszenia danych). Wymaga hasla -
 * to komplet wrazliwych informacji, wiec ktos z przejeta sesja nie pobierze go ot tak.
 */
export default function PobierzDane() {
  const { t } = useTranslation();
  const [haslo, setHaslo] = useState('');
  const [pracuje, setPracuje] = useState(false);
  const [blad, setBlad] = useState(null);
  const [gotowe, setGotowe] = useState(false);

  async function pobierz(e) {
    e.preventDefault();
    setPracuje(true);
    setBlad(null);
    setGotowe(false);
    try {
      const { blob, nazwa } = await konto.pobierzDane(haslo);
      // Plik zapisujemy przez tymczasowy odnosnik - tak zapisuje sie cos, co przyszlo z POST-a
      const adres = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = adres;
      link.download = nazwa;
      document.body.appendChild(link);
      link.click();
      link.remove();
      setTimeout(() => URL.revokeObjectURL(adres), 10_000);
      setHaslo('');
      setGotowe(true);
    } catch (problem) {
      const szczegoly = describeError(problem);
      setBlad(szczegoly.fieldErrors.currentPassword ?? szczegoly.message);
    } finally {
      setPracuje(false);
    }
  }

  return (
    <Card className="mb-4">
      <Card.Body>
        <Card.Title as="h2" className="h6 text-uppercase text-body-secondary">{t('export.title')}</Card.Title>
        <p className="small text-body-secondary">{t('export.text')}</p>
        <Form onSubmit={pobierz} noValidate>
          <Form.Group className="mb-3" controlId="exportPassword">
            <Form.Label>{t('export.password')}</Form.Label>
            <Form.Control
              type="password"
              value={haslo}
              onChange={(e) => setHaslo(e.target.value)}
              autoComplete="current-password"
              isInvalid={Boolean(blad)}
            />
            <Form.Control.Feedback type="invalid">{blad}</Form.Control.Feedback>
          </Form.Group>
          {gotowe && <Alert variant="success" className="py-2">{t('export.done')}</Alert>}
          <Button type="submit" variant="outline-primary" disabled={pracuje || !haslo}>
            {pracuje ? t('export.preparing') : t('export.download')}
          </Button>
          <p className="small text-body-secondary mt-2 mb-0">{t('export.hint')}</p>
        </Form>
      </Card.Body>
    </Card>
  );
}
