import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import Alert from 'react-bootstrap/Alert';
import Button from 'react-bootstrap/Button';
import Form from 'react-bootstrap/Form';
import { describeError } from '../api/client';
import * as konto from '../api/konto';
import Field from './Field';
import { IconMail } from './Icons';

/** Tyle sekund serwer kaze czekac miedzy wiadomosciami - przycisk odlicza to samo. */
const ODSTEP = 60;

/**
 * "Sprawdz skrzynke": po rejestracji i przy logowaniu przed potwierdzeniem adresu.
 *
 * Login i haslo sa tu tylko w pamieci strony - potrzebne do ponownej wysylki,
 * bo przed pierwszym zalogowaniem nie ma jeszcze sesji, ktora by nas poznala.
 *
 * email    - pelny adres, gdy go znamy (rejestracja),
 * opis     - gotowy tekst z serwera z zamaskowanym adresem (logowanie),
 * swiezo   - wiadomosc wlasnie poszla, wiec przycisk od razu odlicza,
 * onBack   - powrot do formularza logowania (na stronie logowania link
 *            "przejdz do logowania" prowadzilby w to samo miejsce).
 */
export default function SprawdzSkrzynke({
  username, password, email = null, opis = null, swiezo = false, onBack = null,
}) {
  const { t } = useTranslation();

  const [adres, setAdres] = useState(email);
  const [odliczanie, setOdliczanie] = useState(swiezo ? ODSTEP : 0);
  const [poprawianie, setPoprawianie] = useState(false);
  const [nowyAdres, setNowyAdres] = useState('');
  const [bladAdresu, setBladAdresu] = useState(null);
  const [blad, setBlad] = useState(null);
  const [wyslanoNa, setWyslanoNa] = useState(null);
  const [wysylanie, setWysylanie] = useState(false);

  useEffect(() => {
    if (odliczanie <= 0) {
      return undefined;
    }
    const zegar = setTimeout(() => setOdliczanie((s) => s - 1), 1000);
    return () => clearTimeout(zegar);
  }, [odliczanie]);

  async function wyslij(poprawiony) {
    setWysylanie(true);
    setBlad(null);
    setBladAdresu(null);
    setWyslanoNa(null);
    try {
      await konto.wyslijLinkPonownie(username, password, poprawiony);
      const nowy = poprawiony ? poprawiony.trim().toLowerCase() : adres;
      if (poprawiony) {
        setAdres(nowy);
        setPoprawianie(false);
        setNowyAdres('');
      }
      setWyslanoNa(nowy);
      setOdliczanie(ODSTEP);
    } catch (problem) {
      const szczegoly = describeError(problem);
      // Serwer mowi, ile czekac - przycisk odlicza dokladnie tyle i to wystarczy za komunikat
      const poIlu = Number(problem.response?.headers?.['retry-after']);
      if (problem.response?.status === 429 && poIlu > 0 && poIlu <= 120) {
        setOdliczanie(poIlu);
      } else if (szczegoly.fieldErrors.email) {
        setBladAdresu(szczegoly.fieldErrors.email);
      } else {
        // Dobowy limit (minuty, godziny) - tu tekst serwera mowi wiecej niz licznik
        setBlad(szczegoly.message);
      }
    } finally {
      setWysylanie(false);
    }
  }

  return (
    <div className="skrzynka" aria-live="polite">
      <span className="skrzynka-ikona" aria-hidden="true">
        <IconMail size={28} />
      </span>
      <h2 className="h5 mb-2">{t('verify.checkTitle')}</h2>

      {opis ? (
        <p className="mb-3">{opis}</p>
      ) : (
        <>
          <p className="mb-1">{t('verify.sentTo')}</p>
          <p className="skrzynka-adres">{adres}</p>
          <p className="text-body-secondary small mb-3">{t('verify.checkText')}</p>
        </>
      )}

      {wyslanoNa && <Alert variant="success" className="small py-2">{t('verify.resent', { email: wyslanoNa })}</Alert>}
      {blad && <Alert variant="danger" className="small py-2">{blad}</Alert>}

      <div className="d-grid gap-2">
        <Button
          variant="outline-primary"
          disabled={wysylanie || odliczanie > 0}
          onClick={() => wyslij(null)}
        >
          {odliczanie > 0 ? t('verify.resendIn', { count: odliczanie }) : t('verify.resend')}
        </Button>

        <Button
          variant="link"
          size="sm"
          className="text-decoration-none"
          aria-expanded={poprawianie}
          onClick={() => setPoprawianie((p) => !p)}
        >
          {t('verify.wrongAddress')}
        </Button>
      </div>

      {poprawianie && (
        <Form
          noValidate
          className="text-start mt-2"
          onSubmit={(e) => {
            e.preventDefault();
            wyslij(nowyAdres);
          }}
        >
          <Field
            id="poprawionyEmail"
            label={t('verify.newAddress')}
            typ="email"
            value={nowyAdres}
            onChange={setNowyAdres}
            error={bladAdresu}
            autoComplete="email"
          />
          <Button type="submit" className="w-100" disabled={wysylanie || odliczanie > 0 || !nowyAdres.trim()}>
            {t('verify.sendToNew')}
          </Button>
        </Form>
      )}

      <p className="text-body-secondary small mt-3 mb-0">
        {t('verify.afterClick')}{' '}
        {onBack ? (
          <Button variant="link" size="sm" className="p-0 align-baseline" onClick={onBack}>
            {t('verify.backToLogin')}
          </Button>
        ) : (
          <Link to="/login">{t('verify.goToLogin')}</Link>
        )}
      </p>
    </div>
  );
}
