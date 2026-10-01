import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import Alert from 'react-bootstrap/Alert';
import Button from 'react-bootstrap/Button';
import Card from 'react-bootstrap/Card';
import Form from 'react-bootstrap/Form';
import { describeError } from '../../api/client';
import * as klany from '../../api/klany';
import { IconFriends } from '../Icons';
import Aktywnosc from './Aktywnosc';
import { Gust } from './KlanMuzyka';

const MAKS_WIADOMOSC = 200;

/**
 * "O klanie" - to, co widzi ktos spoza klanu: wizytowka, poziom aktywnosci, gust (dane zbiorcze)
 * i sposob dolaczenia. Czatu, postow i ankiet tu nie ma - serwer ich takiej osobie nie oddaje.
 */
export default function KlanOKlanie({ klan, onZmiana, onMoj }) {
  const { t } = useTranslation();
  const rekonesans = klan.activityLevel != null;

  return (
    <div className="d-grid gap-3">
      <Card>
        <Card.Body>
          <Card.Title as="h2" className="h6 text-uppercase text-body-secondary">{t('clans.about.title')}</Card.Title>
          <ul className="klan-fakty list-unstyled mb-2">
            <li><IconFriends size={14} /> {t('clans.about.members', { count: klan.memberCount, max: klan.maxMembers })}</li>
            {rekonesans && <li><Aktywnosc poziom={klan.activityLevel} /></li>}
            <li>{t(`clans.card.policyShort.${klan.joinPolicy}`)}</li>
          </ul>
          <p className="small text-body-secondary mb-0">{t('clans.about.private')}</p>
        </Card.Body>
      </Card>

      <Dolaczenie klan={klan} onZmiana={onZmiana} onMoj={onMoj} />

      {rekonesans ? <Gust klan={klan} /> : (
        <Alert variant="secondary" className="mb-0 klan-uwaga">{t('clans.about.hidden')}</Alert>
      )}
    </div>
  );
}

/** Zaproszenie, prosba o dolaczenie albo wyjasnienie, dlaczego nie mozna dolaczyc. */
function Dolaczenie({ klan, onZmiana, onMoj }) {
  const { t } = useTranslation();
  const [wiadomosc, setWiadomosc] = useState('');
  const [zajety, setZajety] = useState(false);
  const [blad, setBlad] = useState(null);

  async function wykonaj(akcja, poOdpowiedzi) {
    setZajety(true);
    setBlad(null);
    try {
      poOdpowiedzi(await akcja());
    } catch (problem) {
      setBlad(describeError(problem).message);
    } finally {
      setZajety(false);
    }
  }

  let tresc;
  if (klan.invitationId != null) {
    tresc = (
      <>
        <p>{t('clans.join.invited')}</p>
        <div className="d-flex gap-2 flex-wrap">
          <Button disabled={zajety} onClick={() => wykonaj(() => klany.przyjmij(klan.invitationId), onMoj)}>
            {t('clans.invitations.accept')}
          </Button>
          <Button variant="outline-secondary" disabled={zajety}
            onClick={() => wykonaj(() => klany.odrzuc(klan.invitationId), onMoj)}>
            {t('clans.invitations.decline')}
          </Button>
        </div>
      </>
    );
  } else if (klan.myRequestStatus === 'PENDING') {
    tresc = (
      <>
        <p>{t('clans.join.pending')}</p>
        <Button variant="outline-secondary" disabled={zajety}
          onClick={() => wykonaj(() => klany.cofnijProsbe(klan.id), onZmiana)}>
          {t('clans.join.withdraw')}
        </Button>
      </>
    );
  } else if (klan.myRequestStatus === 'DECLINED') {
    tresc = <p className="mb-0">{t('clans.join.declined')}</p>;
  } else if (klan.canRequest) {
    tresc = (
      <Form noValidate onSubmit={(e) => {
        e.preventDefault();
        wykonaj(() => klany.poprosODolaczenie(klan.id, wiadomosc.trim()), (nowy) => {
          setWiadomosc('');
          onZmiana(nowy);
        });
      }}>
        <p className="small text-body-secondary">{t('clans.join.hint')}</p>
        <Form.Label htmlFor="klan-prosba">{t('clans.join.message')}</Form.Label>
        <Form.Control id="klan-prosba" as="textarea" rows={3} maxLength={MAKS_WIADOMOSC} value={wiadomosc}
          placeholder={t('clans.join.messagePlaceholder')} onChange={(e) => setWiadomosc(e.target.value)}
          className="mb-1" />
        <Form.Text className="d-block mb-3">{wiadomosc.length}/{MAKS_WIADOMOSC}</Form.Text>
        <Button type="submit" disabled={zajety}>{t('clans.join.send')}</Button>
      </Form>
    );
  } else if (klan.joinPolicy === 'INVITE_ONLY') {
    tresc = <p className="mb-0">{t('clans.inviteOnly')}</p>;
  } else if (klan.memberCount >= klan.maxMembers) {
    tresc = <p className="mb-0">{t('clans.join.full')}</p>;
  } else {
    tresc = <p className="mb-0">{t('clans.join.unavailable')}</p>;
  }

  return (
    <Card>
      <Card.Body>
        <Card.Title as="h2" className="h6 text-uppercase text-body-secondary">{t('clans.join.title')}</Card.Title>
        {blad && <Alert variant="danger">{blad}</Alert>}
        {tresc}
      </Card.Body>
    </Card>
  );
}
