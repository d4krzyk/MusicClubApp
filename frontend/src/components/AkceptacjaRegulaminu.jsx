import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import Alert from 'react-bootstrap/Alert';
import Button from 'react-bootstrap/Button';
import { describeError } from '../api/client';
import * as konto from '../api/konto';
import { useAuth } from '../auth/AuthContext';

/**
 * Prosba o akceptacje regulaminu: dla kont zalozonych, zanim regulamin powstal, i po kazdej
 * zmianie jego tresci (podniesieniu app.legal.version). Nie blokuje aplikacji - ale zostaje na
 * gorze, dopoki ktos nie kliknie. Kto nie zgadza sie z nowa wersja, moze usunac konto w Ustawieniach.
 */
export default function AkceptacjaRegulaminu() {
  const { t } = useTranslation();
  const { user } = useAuth();
  const [stan, setStan] = useState(null);
  const [zajety, setZajety] = useState(false);
  const [blad, setBlad] = useState(null);
  const login = user?.username;

  useEffect(() => {
    let aktualne = true;
    setStan(null);
    if (login) {
      konto.regulamin().then((s) => aktualne && setStan(s)).catch(() => {});
    }
    return () => { aktualne = false; };
  }, [login]);

  if (!login || !stan || stan.current) {
    return null;
  }

  async function akceptuj() {
    setZajety(true);
    setBlad(null);
    try {
      setStan(await konto.zaakceptujRegulamin());
    } catch (problem) {
      setBlad(describeError(problem).message);
    } finally {
      setZajety(false);
    }
  }

  return (
    <Alert variant="info" className="d-flex align-items-center gap-3 flex-wrap" role="region" aria-label={t('legal.notice.title')}>
      <div className="flex-grow-1">
        <strong>{stan.acceptedVersion ? t('legal.notice.updated') : t('legal.notice.new')}</strong>
        {' '}
        <Link to="/regulamin">{t('legal.terms')}</Link>
        {' · '}
        <Link to="/polityka-prywatnosci">{t('legal.privacy')}</Link>
        {blad && <div className="text-danger small">{blad}</div>}
      </div>
      <Button size="sm" onClick={akceptuj} disabled={zajety}>{t('legal.notice.accept')}</Button>
    </Alert>
  );
}
