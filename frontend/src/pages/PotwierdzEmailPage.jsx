import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import Button from 'react-bootstrap/Button';
import Card from 'react-bootstrap/Card';
import Spinner from 'react-bootstrap/Spinner';
import { describeError } from '../api/client';
import * as konto from '../api/konto';
import { useAuth } from '../auth/AuthContext';
import { IconCheckCircle, IconCross } from '../components/Icons';

/**
 * Strona, na ktora prowadzi link z wiadomosci: /potwierdz-email?token=...
 *
 * Potwierdzenie idzie zapytaniem POST wyslanym przez te strone, a nie samym
 * otwarciem linku. Programy pocztowe i antywirusy otwieraja linki z
 * wiadomosci "na probe", zeby sprawdzic, dokad prowadza - gdyby wystarczylo
 * wejsc pod adres, potwierdzalyby konta same, bez udzialu czlowieka.
 *
 * Nie loguje: link mogl zostac otwarty na telefonie, a konto zakladane na
 * komputerze. Zalogowany (zmiana adresu) dostaje po prostu nowe dane konta.
 */
export default function PotwierdzEmailPage() {
  const { t } = useTranslation();
  const { user, refreshUser } = useAuth();
  const navigate = useNavigate();
  const [parametry] = useSearchParams();

  /* Token czytamy raz, przy wejsciu - zaraz potem znika z paska adresu. */
  const [token] = useState(() => parametry.get('token') ?? '');
  const [wynik, setWynik] = useState(null);
  const [blad, setBlad] = useState(null);

  /*
   * React w trybie deweloperskim uruchamia efekty dwa razy. Drugie wyslanie
   * tego samego jednorazowego tokenu skonczyloby sie bledem "link juz uzyty"
   * - mimo ze pierwsze sie udalo.
   */
  const wyslane = useRef(false);

  useEffect(() => {
    if (wyslane.current) {
      return;
    }
    wyslane.current = true;

    // Token nie powinien zostac w historii przegladarki ani w zakladkach
    navigate('/potwierdz-email', { replace: true });

    if (!token) {
      setBlad(t('verify.noToken'));
      return;
    }
    konto.potwierdzEmail(token)
      .then(setWynik)
      .catch((problem) => setBlad(describeError(problem).message));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  /* Zmiana adresu przy otwartej sesji - pasek i ustawienia od razu z nowym adresem. */
  useEffect(() => {
    if (wynik?.result === 'CHANGED' && user) {
      konto.ktoJestem().then(refreshUser).catch(() => {});
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [wynik]);

  let tresc;
  if (!wynik && !blad) {
    tresc = (
      <div className="text-center py-4 text-body-secondary">
        <Spinner animation="border" size="sm" className="me-2" />
        {t('verify.checking')}
      </div>
    );
  } else if (blad) {
    tresc = (
      <>
        <span className="skrzynka-ikona is-blad" aria-hidden="true"><IconCross size={26} /></span>
        <h1 className="h5 mb-2">{t('verify.failedTitle')}</h1>
        <p className="mb-3">{blad}</p>
        <div className="d-grid gap-2">
          {user ? (
            <Link to="/settings" className="btn btn-primary">{t('verify.goToSettings')}</Link>
          ) : (
            <Link to="/login" className="btn btn-primary">{t('verify.goToLogin')}</Link>
          )}
        </div>
        {!user && <p className="text-body-secondary small mt-3 mb-0">{t('verify.failedHint')}</p>}
      </>
    );
  } else {
    const zmiana = wynik.result === 'CHANGED';
    tresc = (
      <>
        <span className="skrzynka-ikona" aria-hidden="true"><IconCheckCircle size={28} /></span>
        <h1 className="h5 mb-2">{zmiana ? t('verify.changedTitle') : t('verify.doneTitle')}</h1>
        <p className="mb-1">{zmiana ? t('verify.changedText') : t('verify.doneText')}</p>
        <p className="skrzynka-adres">{wynik.email}</p>
        <div className="d-grid gap-2">
          {user ? (
            <Link to="/" className="btn btn-primary">{t('verify.goToApp')}</Link>
          ) : (
            <Button
              onClick={() => navigate('/login', { state: { verified: true, username: wynik.username } })}
            >
              {t('verify.signIn')}
            </Button>
          )}
        </div>
      </>
    );
  }

  return (
    <Card className="mx-auto" style={{ maxWidth: 420 }}>
      <Card.Body className="p-4 skrzynka" aria-live="polite">
        {tresc}
      </Card.Body>
    </Card>
  );
}
