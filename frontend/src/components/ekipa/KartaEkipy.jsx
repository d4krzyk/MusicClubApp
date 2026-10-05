import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import Button from 'react-bootstrap/Button';
import Form from 'react-bootstrap/Form';
import { describeError } from '../../api/client';
import * as ekipy from '../../api/ekipy';
import Avatar from '../Avatar';
import { IconFriends, IconLock, IconPin } from '../Icons';

/**
 * Jedna ekipa na liscie pod wydarzeniem: kto, skad, ile miejsc, znajomi w srodku i jedno dzialanie - "Dolacz",
 * "Popros o miejsce" (z krotka wiadomoscia), "Czekasz na odpowiedz" z cofnieciem albo przejscie do mojej ekipy.
 */
export default function KartaEkipy({ k, i = 0, onZmiana }) {
  const { t } = useTranslation();
  const [prosba, setProsba] = useState(false);
  const [wiadomosc, setWiadomosc] = useState('');
  const [zajete, setZajete] = useState(false);
  const [blad, setBlad] = useState(null);

  const moja = k.myState === 'FOUNDER' || k.myState === 'MEMBER';
  const pelna = k.members >= k.capacity;
  const nazwa = k.title || t('crews.defaultName', { username: k.founder });

  async function dzialaj(fn) {
    setZajete(true);
    setBlad(null);
    try {
      onZmiana(await fn());
      setProsba(false);
      setWiadomosc('');
    } catch (problem) {
      setBlad(describeError(problem).message);
    } finally {
      setZajete(false);
    }
  }

  return (
    <li className={`ekipa-karta mc-wejscie${moja ? ' is-moja' : ''}`} style={{ '--i': i }}>
      <div className="ekipa-karta-glowa">
        <div className="ekipa-karta-nazwa">
          {moja ? <Link to={`/ekipy/${k.id}`}>{nazwa}</Link> : <span>{nazwa}</span>}
          {k.joinPolicy === 'APPROVAL' && (
            <span className="ekipa-plakietka" title={t('crews.policy.APPROVAL')}><IconLock size={10} /> {t('crews.approvalShort')}</span>
          )}
          {moja && <span className="ekipa-plakietka is-twoja">{t('crews.yours')}</span>}
        </div>
        <span className={`ekipa-miejsca${pelna ? ' is-pelna' : ''}`}>
          {t('crews.seats', { members: k.members, capacity: k.capacity })}
        </span>
      </div>

      <div className="ekipa-karta-fakty small text-body-secondary">
        {k.departureCity && (
          <span>
            <IconPin size={11} /> {t('crews.from', { city: k.departureCity })}
            {k.near >= 3 && <span className="ekipa-blisko"> · {t('crews.nearYou')}</span>}
          </span>
        )}
        {k.friends > 0 && <span><IconFriends size={12} /> {t('crews.friendsInside', { count: k.friends })}</span>}
      </div>

      {k.description && <p className="ekipa-karta-opis">{k.description}</p>}

      <div className="ekipa-karta-ludzie" aria-label={t('crews.whoIsIn')}>
        {k.preview.map((p) => (
          <Link key={p.username} to={`/profil/${encodeURIComponent(p.username)}`} className="ekipa-awatar" title={p.username}>
            <Avatar avatarUrl={p.avatarUrl} username={p.username} size={26} />
          </Link>
        ))}
        {k.members > k.preview.length && <span className="ekipa-wiecej">+{k.members - k.preview.length}</span>}
      </div>

      <div className="ekipa-karta-akcje">
        {moja && <Link className="btn btn-sm btn-primary" to={`/ekipy/${k.id}`}>{t('crews.open')}</Link>}
        {!moja && k.myState === 'REQUESTED' && (
          <>
            <span className="small text-body-secondary">{t('crews.requested')}</span>
            <Button size="sm" variant="link" className="p-0" disabled={zajete}
              onClick={() => dzialaj(() => ekipy.cofnijProsbe(k.id))}>{t('crews.cancelRequest')}</Button>
          </>
        )}
        {!moja && k.myState === 'DECLINED' && <span className="small text-body-secondary">{t('crews.declined')}</span>}
        {!moja && !k.myState && k.canJoin && k.joinPolicy === 'OPEN' && (
          <Button size="sm" disabled={zajete} onClick={() => dzialaj(() => ekipy.dolacz(k.id))}>{t('crews.join')}</Button>
        )}
        {!moja && !k.myState && k.canJoin && k.joinPolicy === 'APPROVAL' && !prosba && (
          <Button size="sm" variant="outline-primary" onClick={() => setProsba(true)}>{t('crews.ask')}</Button>
        )}
        {!moja && !k.myState && !k.canJoin && (pelna || k.closed) && (
          <span className="small text-body-secondary">{k.closed ? t('crews.closed') : t('crews.full')}</span>
        )}
      </div>

      {prosba && (
        <form className="ekipa-prosba" onSubmit={(e) => { e.preventDefault(); dzialaj(() => ekipy.dolacz(k.id, wiadomosc.trim() || null)); }}>
          <Form.Control as="textarea" rows={2} maxLength={200} value={wiadomosc} onChange={(e) => setWiadomosc(e.target.value)}
            placeholder={t('crews.askPlaceholder')} aria-label={t('crews.askLabel')} />
          <div className="d-flex gap-2 mt-1">
            <Button type="submit" size="sm" disabled={zajete}>{t('crews.askSend')}</Button>
            <Button size="sm" variant="outline-secondary" onClick={() => setProsba(false)}>{t('common.cancel')}</Button>
          </div>
        </form>
      )}
      {blad && <div className="small text-danger mt-1" role="alert">{blad}</div>}
    </li>
  );
}
