import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import Alert from 'react-bootstrap/Alert';
import Button from 'react-bootstrap/Button';
import Card from 'react-bootstrap/Card';
import { describeError } from '../../api/client';
import * as klany from '../../api/klany';
import { timeAgo } from '../../utils/dates';
import Avatar from '../Avatar';

/**
 * Prosby o dolaczenie - widzi je tylko zarzad klanu. Przyjecie wpuszcza osobe od razu;
 * odrzucenie nie wysyla powiadomienia i nie podaje powodu (osoba moze sprobowac ponownie po tygodniu).
 */
export default function KlanProsby({ klan, onZmiana }) {
  const { t, i18n } = useTranslation();
  const [zajety, setZajety] = useState(false);
  const [blad, setBlad] = useState(null);

  if (klan.requests.length === 0) {
    return null;
  }

  async function wykonaj(akcja) {
    setZajety(true);
    setBlad(null);
    try {
      onZmiana(await akcja());
    } catch (problem) {
      setBlad(describeError(problem).message);
    } finally {
      setZajety(false);
    }
  }

  return (
    <Card className="klan-prosby">
      <Card.Body>
        <Card.Title as="h2" className="h6 text-uppercase text-body-secondary">
          {t('clans.requests.title')} <span className="text-body-secondary">({klan.requests.length})</span>
        </Card.Title>
        <p className="small text-body-secondary">{t('clans.requests.hint')}</p>
        {blad && <Alert variant="danger">{blad}</Alert>}
        <ul className="list-unstyled mb-0">
          {klan.requests.map((p) => (
            <li key={p.id} className="klan-prosba">
              <div className="klan-czlonek">
                <Avatar avatarUrl={p.avatarUrl} username={p.username} size={40} />
                <span className="klan-czlonek-nazwa">
                  <Link to={`/profil/${p.username}`}>{p.username}</Link>
                  <span className="small text-body-secondary">{timeAgo(p.createdAt, i18n.language)}</span>
                </span>
                <span className="klan-czlonek-akcje">
                  <Button size="sm" disabled={zajety || klan.memberCount >= klan.maxMembers}
                    onClick={() => wykonaj(() => klany.przyjmijProsbe(klan.id, p.id))}>
                    {t('clans.requests.accept')}
                  </Button>
                  <Button size="sm" variant="outline-secondary" disabled={zajety}
                    onClick={() => wykonaj(() => klany.odrzucProsbe(klan.id, p.id))}>
                    {t('clans.requests.decline')}
                  </Button>
                </span>
              </div>
              {p.message && <p className="klan-prosba-tekst">„{p.message}"</p>}
            </li>
          ))}
        </ul>
      </Card.Body>
    </Card>
  );
}
