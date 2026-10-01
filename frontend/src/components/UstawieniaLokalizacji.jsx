import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import Alert from 'react-bootstrap/Alert';
import Button from 'react-bootstrap/Button';
import Card from 'react-bootstrap/Card';
import Form from 'react-bootstrap/Form';
import { useAuth } from '../auth/AuthContext';
import { describeError } from '../api/client';
import { ustawMiasto } from '../api/lokalizacja';
import { IconPin } from './Icons';
import MiastoInput from './MiastoInput';

/**
 * Moje miasto: po nim aplikacja stawia wyzej ludzi, koncerty, klany i posty z okolicy. Tylko miasto -
 * bez dokladnego adresu i bez pozycji z telefonu.
 */
export default function UstawieniaLokalizacji() {
  const { t } = useTranslation();
  const { user, refreshUser } = useAuth();
  const [tekst, setTekst] = useState(user.city ?? '');
  const [stan, setStan] = useState(null);
  const [wysylanie, setWysylanie] = useState(false);

  const zmienione = tekst.trim() !== (user.city ?? '');

  async function zapisz(nowy) {
    setWysylanie(true);
    setStan(null);
    try {
      const konto = await ustawMiasto(nowy);
      refreshUser(konto);
      setTekst(konto.city ?? '');
      setStan({
        ok: true,
        tekst: !konto.city
          ? t('location.removed')
          : konto.cityLocated ? t('location.saved') : t('location.savedUnknown'),
      });
    } catch (problem) {
      const szczegoly = describeError(problem);
      setStan({ ok: false, tekst: szczegoly.fieldErrors.city ?? szczegoly.message });
    } finally {
      setWysylanie(false);
    }
  }

  return (
    <Card className="mb-4" id="miasto">
      <Card.Body>
        <Card.Title as="h2" className="h6 text-uppercase text-body-secondary">
          {t('location.title')}
        </Card.Title>
        <p className="small text-body-secondary">{t('location.intro')}</p>

        <Form onSubmit={(e) => { e.preventDefault(); zapisz(tekst); }}>
          <Form.Label htmlFor="miasto-pole">{t('location.cityLabel')}</Form.Label>
          <div className="d-flex gap-2 flex-wrap">
            <div className="flex-grow-1 ustawienie-miasto-pole">
              <MiastoInput
                id="miasto-pole"
                wartosc={tekst}
                onChange={(v) => { setTekst(v); setStan(null); }}
                placeholder={t('location.placeholder')}
              />
            </div>
            <Button type="submit" disabled={wysylanie || !zmienione}>
              <IconPin size={14} /> {t('settings.save')}
            </Button>
            {user.city && (
              <Button type="button" variant="outline-secondary" disabled={wysylanie} onClick={() => zapisz('')}>
                {t('location.remove')}
              </Button>
            )}
          </div>
          <Form.Text className="d-block mt-1">{t('location.hint')}</Form.Text>
        </Form>

        {stan && (
          <Alert variant={stan.ok ? 'success' : 'danger'} className="mt-3 mb-0 py-2 small" role="status">
            {stan.tekst}
          </Alert>
        )}
        {user.city && !user.cityLocated && !stan && (
          <Alert variant="info" className="mt-3 mb-0 py-2 small">{t('location.unknownCity')}</Alert>
        )}
      </Card.Body>
    </Card>
  );
}
