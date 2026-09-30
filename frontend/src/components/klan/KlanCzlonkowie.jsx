import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import Alert from 'react-bootstrap/Alert';
import Button from 'react-bootstrap/Button';
import Card from 'react-bootstrap/Card';
import Form from 'react-bootstrap/Form';
import { describeError } from '../../api/client';
import * as klany from '../../api/klany';
import Avatar from '../Avatar';
import { timeAgo } from '../../utils/dates';

/**
 * Czlonkowie, zaproszenia i glosowanie na kolor.
 *
 * Kolor klanu nie jest ustawiany przez nikogo: kazdy czlonek oddaje jeden glos, wygrywa kolor
 * z najwieksza liczba glosow (przy remisie ten, na ktory zaglosowano wczesniej). Glosy sa jawne.
 */
export default function KlanCzlonkowie({ klan, onZmiana }) {
  const { t, i18n } = useTranslation();
  const [login, setLogin] = useState('');
  const [zajety, setZajety] = useState(false);
  const [blad, setBlad] = useState(null);
  const [info, setInfo] = useState(null);

  const jestem = klan.myRole != null;
  const zarzadzam = klan.myRole === 'FOUNDER' || klan.myRole === 'ADMIN';
  const zalozyciel = klan.myRole === 'FOUNDER';

  async function wykonaj(akcja, komunikat) {
    setZajety(true);
    setBlad(null);
    setInfo(null);
    try {
      onZmiana(await akcja());
      if (komunikat) {
        setInfo(komunikat);
      }
    } catch (problem) {
      setBlad(describeError(problem).message);
    } finally {
      setZajety(false);
    }
  }

  async function zapros(e) {
    e.preventDefault();
    const cel = login.trim();
    if (!cel) {
      return;
    }
    await wykonaj(() => klany.zapros(klan.id, cel), t('clans.members.invited', { username: cel }));
    setLogin('');
  }

  const rolaNazwa = (rola) => t(`clans.role.${rola}`);
  const wygrywajacy = klan.color;

  return (
    <div className="d-grid gap-3">
      {blad && <Alert variant="danger" className="mb-0">{blad}</Alert>}
      {info && <Alert variant="success" className="mb-0" dismissible onClose={() => setInfo(null)}>{info}</Alert>}

      {jestem && (
        <Card>
          <Card.Body>
            <Card.Title as="h2" className="h6 text-uppercase text-body-secondary">{t('clans.color.title')}</Card.Title>
            <p className="small text-body-secondary">{t('clans.color.hint')}</p>
            <div className="klan-paleta" role="group" aria-label={t('clans.color.title')}>
              {klan.palette.map((k) => (
                <button
                  key={k.key}
                  type="button"
                  className={`klan-kolor${klan.myVote === k.key ? ' is-moj' : ''}${wygrywajacy === k.key ? ' is-wygrywa' : ''}`}
                  style={{ '--probka': k.hex }}
                  disabled={zajety}
                  aria-pressed={klan.myVote === k.key}
                  title={t(`clans.color.names.${k.key}`)}
                  onClick={() => wykonaj(() => klany.glosuj(klan.id, klan.myVote === k.key ? null : k.key))}
                >
                  <span className="klan-kolor-probka" />
                  <span>{t(`clans.color.names.${k.key}`)}</span>
                  <span className="text-body-secondary">{k.votes}</span>
                </button>
              ))}
            </div>
            <p className="small text-body-secondary mb-0 mt-2">
              {t('clans.color.current', { color: t(`clans.color.names.${klan.color}`) })}
              {klan.myVote ? ` ${t('clans.color.myVote', { color: t(`clans.color.names.${klan.myVote}`) })}` : ` ${t('clans.color.noVote')}`}
            </p>
          </Card.Body>
        </Card>
      )}

      {jestem && (
        <Card>
          <Card.Body>
            <Card.Title as="h2" className="h6 text-uppercase text-body-secondary">{t('clans.members.invite')}</Card.Title>
            <p className="small text-body-secondary">{t('clans.members.inviteHint')}</p>
            <Form onSubmit={zapros} className="d-flex gap-2" noValidate>
              <Form.Control
                value={login}
                onChange={(e) => setLogin(e.target.value)}
                placeholder={t('clans.members.usernamePlaceholder')}
                aria-label={t('clans.members.usernamePlaceholder')}
                maxLength={64}
                autoCapitalize="none"
                autoComplete="off"
              />
              <Button type="submit" disabled={zajety || !login.trim()}>{t('clans.members.send')}</Button>
            </Form>

            {klan.invitations.length > 0 && (
              <div className="mt-3">
                <h3 className="h6">{t('clans.members.pending')}</h3>
                <ul className="list-unstyled mb-0">
                  {klan.invitations.map((z) => (
                    <li key={z.id} className="d-flex align-items-center gap-2 py-1 small">
                      <span className="flex-grow-1 text-break">
                        <Link to={`/profil/${z.inviteeUsername}`}>{z.inviteeUsername}</Link>
                        <span className="text-body-secondary">
                          {' · '}{t('clans.members.invitedBy', { username: z.inviterUsername })}
                          {' · '}{timeAgo(z.createdAt, i18n.language)}
                        </span>
                      </span>
                      {z.canCancel && (
                        <Button
                          variant="outline-secondary"
                          size="sm"
                          disabled={zajety}
                          onClick={() => wykonaj(() => klany.cofnijZaproszenie(klan.id, z.id))}
                        >
                          {t('clans.members.cancelInvite')}
                        </Button>
                      )}
                    </li>
                  ))}
                </ul>
              </div>
            )}
          </Card.Body>
        </Card>
      )}

      <Card>
        <Card.Body>
          <Card.Title as="h2" className="h6 text-uppercase text-body-secondary">
            {t('clans.members.title')} <span className="text-body-secondary">({klan.memberCount}/{klan.maxMembers})</span>
          </Card.Title>
          <ul className="list-unstyled mb-0">
            {klan.members.map((m) => {
              const mogeWyrzucic = m.role !== 'FOUNDER' && !m.me && (zalozyciel || (zarzadzam && m.role === 'MEMBER'));
              return (
                <li key={m.username} className="klan-czlonek">
                  <Avatar avatarUrl={m.avatarUrl} username={m.username} size={40} />
                  <span className="klan-czlonek-nazwa">
                    <Link to={`/profil/${m.username}`}>{m.username}{m.me ? ` (${t('clans.members.you')})` : ''}</Link>
                    <span className="small text-body-secondary">
                      {rolaNazwa(m.role)}
                      {m.vote && (
                        <>
                          {' · '}
                          <span
                            className="d-inline-block rounded-circle align-middle"
                            style={{ width: 9, height: 9, background: klan.palette.find((k) => k.key === m.vote)?.hex }}
                            aria-hidden="true"
                          />
                          {' '}{t(`clans.color.names.${m.vote}`)}
                        </>
                      )}
                    </span>
                  </span>
                  <span className="klan-czlonek-akcje">
                    {zalozyciel && m.role !== 'FOUNDER' && (
                      <>
                        <Button
                          variant="outline-secondary"
                          size="sm"
                          disabled={zajety}
                          onClick={() => wykonaj(() => klany.ustawRole(klan.id, m.username, m.role === 'ADMIN' ? 'MEMBER' : 'ADMIN'))}
                        >
                          {m.role === 'ADMIN' ? t('clans.members.demote') : t('clans.members.promote')}
                        </Button>
                        <Button
                          variant="outline-secondary"
                          size="sm"
                          disabled={zajety}
                          onClick={() => {
                            if (window.confirm(t('clans.members.transferConfirm', { username: m.username }))) {
                              wykonaj(() => klany.przekaz(klan.id, m.username));
                            }
                          }}
                        >
                          {t('clans.members.transfer')}
                        </Button>
                      </>
                    )}
                    {mogeWyrzucic && (
                      <Button
                        variant="outline-danger"
                        size="sm"
                        disabled={zajety}
                        onClick={() => {
                          if (window.confirm(t('clans.members.kickConfirm', { username: m.username }))) {
                            wykonaj(() => klany.wyrzuc(klan.id, m.username));
                          }
                        }}
                      >
                        {t('clans.members.kick')}
                      </Button>
                    )}
                  </span>
                </li>
              );
            })}
          </ul>
        </Card.Body>
      </Card>
    </div>
  );
}
