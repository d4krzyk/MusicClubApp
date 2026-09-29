import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import Button from 'react-bootstrap/Button';
import Card from 'react-bootstrap/Card';
import Form from 'react-bootstrap/Form';
import { describeError } from '../api/client';
import * as prywatnosc from '../api/prywatnosc';
import Avatar from './Avatar';

/**
 * Prywatnosc: kto widzi profil, kto moze zaprosic, aktywnosc, propozycje,
 * domyslne ukrycie na listach uczestnikow. Zapis przyciskiem - piec
 * ustawien zmienianych jednym ruchem to jeden zapis, a nie piec.
 */
export function UstawieniaPrywatnosci() {
  const { t } = useTranslation();
  const [dane, setDane] = useState(null);
  const [stan, setStan] = useState(null);
  const [wysylanie, setWysylanie] = useState(false);

  useEffect(() => {
    prywatnosc.ustawienia().then(setDane).catch((p) => setStan({ ok: false, tekst: describeError(p).message }));
  }, []);

  function ustaw(pole, wartosc) {
    setDane((d) => ({ ...d, [pole]: wartosc }));
    setStan(null);
  }

  async function zapisz(e) {
    e.preventDefault();
    setWysylanie(true);
    try {
      setDane(await prywatnosc.zapiszUstawienia(dane));
      setStan({ ok: true, tekst: t('privacy.saved') });
    } catch (problem) {
      setStan({ ok: false, tekst: describeError(problem).message });
    } finally {
      setWysylanie(false);
    }
  }

  return (
    <Card className="mb-4">
      <Card.Body>
        <Card.Title as="h2" className="h6 text-uppercase text-body-secondary">{t('privacy.title')}</Card.Title>
        {!dane ? (
          stan && <div className="text-danger small">{stan.tekst}</div>
        ) : (
          <Form onSubmit={zapisz}>
            <Form.Group className="mb-3" controlId="profileVisibility">
              <Form.Label>{t('privacy.profileVisibility')}</Form.Label>
              <Form.Select value={dane.profileVisibility} onChange={(e) => ustaw('profileVisibility', e.target.value)}>
                <option value="EVERYONE">{t('privacy.everyone')}</option>
                <option value="FRIENDS">{t('privacy.friendsOnly')}</option>
              </Form.Select>
              <Form.Text>{t('privacy.profileVisibilityHint')}</Form.Text>
            </Form.Group>

            <Form.Group className="mb-3" controlId="friendRequestsFrom">
              <Form.Label>{t('privacy.friendRequestsFrom')}</Form.Label>
              <Form.Select value={dane.friendRequestsFrom} onChange={(e) => ustaw('friendRequestsFrom', e.target.value)}>
                <option value="EVERYONE">{t('privacy.everyone')}</option>
                <option value="FRIENDS_OF_FRIENDS">{t('privacy.friendsOfFriends')}</option>
                <option value="NOBODY">{t('privacy.nobody')}</option>
              </Form.Select>
            </Form.Group>

            <Form.Check
              type="switch"
              id="showOnline"
              className="mb-2"
              label={t('privacy.showOnline')}
              checked={dane.showOnline}
              onChange={(e) => ustaw('showOnline', e.target.checked)}
            />
            <Form.Check
              type="switch"
              id="showInSuggestions"
              className="mb-2"
              label={t('privacy.showInSuggestions')}
              checked={dane.showInSuggestions}
              onChange={(e) => ustaw('showInSuggestions', e.target.checked)}
            />
            <Form.Check
              type="switch"
              id="hideOnAttendeeLists"
              className="mb-3"
              label={t('privacy.hideOnAttendeeLists')}
              checked={dane.hideOnAttendeeLists}
              onChange={(e) => ustaw('hideOnAttendeeLists', e.target.checked)}
            />

            <Button type="submit" disabled={wysylanie}>{t('settings.save')}</Button>
            {stan && (
              <span className={`small ms-2 ${stan.ok ? 'text-success' : 'text-danger'}`} role="status">
                {stan.tekst}
              </span>
            )}
          </Form>
        )}
      </Card.Body>
    </Card>
  );
}

/** Moja lista zablokowanych - z odblokowaniem. */
export function Zablokowani() {
  const { t } = useTranslation();
  const [lista, setLista] = useState(null);
  const [blad, setBlad] = useState(null);

  useEffect(() => {
    prywatnosc.zablokowani().then(setLista).catch((p) => setBlad(describeError(p).message));
  }, []);

  async function odblokuj(login) {
    try {
      await prywatnosc.odblokuj(login);
      setLista((l) => l.filter((u) => u.username !== login));
    } catch (problem) {
      setBlad(describeError(problem).message);
    }
  }

  return (
    <Card className="mb-4">
      <Card.Body>
        <Card.Title as="h2" className="h6 text-uppercase text-body-secondary">{t('block.listTitle')}</Card.Title>
        {blad && <div className="text-danger small mb-2">{blad}</div>}
        {lista && lista.length === 0 && <p className="small text-body-secondary mb-0">{t('block.listEmpty')}</p>}
        {lista && lista.length > 0 && (
          <ul className="list-unstyled mb-0 zablokowani">
            {lista.map((u) => (
              <li key={u.username} className="d-flex align-items-center gap-2 py-1">
                <Avatar avatarUrl={u.avatarUrl} username={u.username} size={32} />
                <Link to={`/profil/${encodeURIComponent(u.username)}`} className="flex-grow-1 text-truncate">
                  {u.username}
                </Link>
                <Button size="sm" variant="outline-secondary" onClick={() => odblokuj(u.username)}>
                  {t('block.unblock')}
                </Button>
              </li>
            ))}
          </ul>
        )}
        <p className="small text-body-secondary mt-2 mb-0">{t('block.listHint')}</p>
      </Card.Body>
    </Card>
  );
}
