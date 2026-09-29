import { useCallback, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import Button from 'react-bootstrap/Button';
import Card from 'react-bootstrap/Card';
import Form from 'react-bootstrap/Form';
import Spinner from 'react-bootstrap/Spinner';
import { describeError } from '../api/client';
import * as api from '../api/push';
import * as push from '../utils/push';
import { IconBell } from './Icons';

/**
 * Powiadomienia: przypomnienia o wydarzeniach (konto) i push na tym
 * urzadzeniu. Przelacznik przypomnien zapisuje sie od razu - to jedno
 * ustawienie, bez formularza.
 */
export default function UstawieniaPowiadomien() {
  const { t, i18n } = useTranslation();
  const [dane, setDane] = useState(null);
  const [urzadzenie, setUrzadzenie] = useState(null);
  const [zajety, setZajety] = useState(false);
  const [komunikat, setKomunikat] = useState(null);

  const odswiez = useCallback(async () => {
    const [ustawienia, stan] = await Promise.all([api.ustawienia(), push.stan()]);
    setDane(ustawienia);
    setUrzadzenie(stan);
  }, []);

  useEffect(() => {
    odswiez().catch((p) => setKomunikat({ ok: false, tekst: describeError(p).message }));
  }, [odswiez]);

  async function wykonaj(akcja, sukces) {
    setZajety(true);
    setKomunikat(null);
    try {
      const wynik = await akcja();
      await odswiez();
      if (sukces) {
        setKomunikat({ ok: true, tekst: typeof sukces === 'function' ? sukces(wynik) : sukces });
      }
    } catch (problem) {
      setKomunikat({ ok: false, tekst: describeError(problem).message });
    } finally {
      setZajety(false);
    }
  }

  const przypomnienia = (wlaczone) => wykonaj(() => api.zmienPrzypomnienia(wlaczone), t('push.saved'));
  const wlacz = () => wykonaj(
    () => push.wlacz(dane.publicKey, i18n.language),
    (zgoda) => (zgoda ? t('push.enabled') : t('push.permissionRefused')),
  );
  const wylacz = () => wykonaj(() => push.wylacz(), t('push.disabled'));
  const probne = () => wykonaj(() => api.probne(), t('push.testSent'));

  return (
    <Card className="mb-4">
      <Card.Body>
        <Card.Title as="h2" className="h6 text-uppercase text-body-secondary">{t('push.title')}</Card.Title>

        {!dane ? (
          komunikat ? <div className="text-danger small">{komunikat.tekst}</div> : <Spinner size="sm" />
        ) : (
          <>
            <Form.Check
              type="switch"
              id="eventReminders"
              className="mb-1"
              label={t('push.reminders')}
              checked={dane.eventReminders}
              disabled={zajety}
              onChange={(e) => przypomnienia(e.target.checked)}
            />
            <Form.Text as="p" className="mb-3">{t('push.remindersHint')}</Form.Text>

            <h3 className="h6 mb-2">{t('push.thisDevice')}</h3>
            {!dane.available && <p className="small text-body-secondary mb-0">{t('push.serverOff')}</p>}

            {dane.available && urzadzenie === 'nieobslugiwane' && (
              <p className="small text-body-secondary mb-0">{t('push.unsupported')}</p>
            )}

            {dane.available && urzadzenie === 'zablokowane' && (
              <p className="small text-body-secondary mb-0">{t('push.blocked')}</p>
            )}

            {dane.available && urzadzenie === 'wylaczone' && (
              <>
                <p className="small text-body-secondary mb-2">{t('push.offHint')}</p>
                <Button onClick={wlacz} disabled={zajety}>
                  <IconBell className="me-2" />
                  {t('push.enable')}
                </Button>
              </>
            )}

            {dane.available && urzadzenie === 'wlaczone' && (
              <>
                <p className="small mb-2">
                  <span className="badge text-bg-success me-2">{t('push.on')}</span>
                  {t('push.devices', { count: dane.devices })}
                </p>
                <div className="d-flex gap-2 flex-wrap">
                  <Button variant="outline-secondary" onClick={probne} disabled={zajety}>{t('push.test')}</Button>
                  <Button variant="outline-danger" onClick={wylacz} disabled={zajety}>{t('push.disable')}</Button>
                </div>
              </>
            )}

            {komunikat && (
              <div className={`small mt-2 ${komunikat.ok ? 'text-success' : 'text-danger'}`} role="status">
                {komunikat.tekst}
              </div>
            )}
          </>
        )}
      </Card.Body>
    </Card>
  );
}
