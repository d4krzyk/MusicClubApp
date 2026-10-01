import { useCallback, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import Alert from 'react-bootstrap/Alert';
import Button from 'react-bootstrap/Button';
import Card from 'react-bootstrap/Card';
import Form from 'react-bootstrap/Form';
import Spinner from 'react-bootstrap/Spinner';
import { describeError } from '../../api/client';
import * as klany from '../../api/klany';
import useOdswiezanie from '../../hooks/useOdswiezanie';
import { formatDateTime } from '../../utils/dates';
import { IconPlus, IconTrash } from '../Icons';

const MAKS_PYTANIE = 150;
const MAKS_ODPOWIEDZ = 80;
const MIN_ODPOWIEDZI = 2;
const MAKS_ODPOWIEDZI = 6;
const DNI = [1, 3, 7, 14];

/**
 * Ankiety klanu ("jaki koncert w przyszlym miesiacu?", "ktora plyta tygodnia?"). Zaklada ja kazdy
 * czlonek, glosuje sie raz i mozna zmienic zdanie do konca. Wyniki widac na biezaco, ale bez
 * wskazywania, kto na co glosowal.
 */
export default function KlanAnkiety({ klan }) {
  const { t } = useTranslation();
  const [lista, setLista] = useState(null);
  const [blad, setBlad] = useState(null);

  const wczytaj = useCallback(async () => {
    try {
      setLista(await klany.ankiety(klan.id));
    } catch (problem) {
      setBlad(describeError(problem).message);
    }
  }, [klan.id]);

  useEffect(() => { wczytaj(); }, [wczytaj]);
  // Wyniki zmieniaja sie, gdy inni glosuja - odswiezamy tylko, gdy karta jest na wierzchu
  useOdswiezanie(wczytaj, 30_000, true);

  if (!lista && !blad) {
    return (
      <div className="text-center py-4 text-body-secondary">
        <Spinner animation="border" size="sm" className="me-2" />{t('common.loading')}
      </div>
    );
  }

  const otwarte = (lista ?? []).filter((a) => a.open);
  const zamkniete = (lista ?? []).filter((a) => !a.open);

  return (
    <div className="d-grid gap-3">
      {blad && <Alert variant="danger" className="mb-0">{blad}</Alert>}
      <NowaAnkieta klan={klan} onDodana={wczytaj} />

      {lista && otwarte.length === 0 && zamkniete.length === 0 && (
        <p className="text-body-secondary text-center mb-0">{t('clans.polls.none')}</p>
      )}
      {otwarte.map((a) => <Ankieta key={a.id} klan={klan} ankieta={a} onZmiana={wczytaj} />)}
      {zamkniete.length > 0 && (
        <>
          <h2 className="h6 text-uppercase text-body-secondary mb-0">{t('clans.polls.closed')}</h2>
          {zamkniete.map((a) => <Ankieta key={a.id} klan={klan} ankieta={a} onZmiana={wczytaj} />)}
        </>
      )}
    </div>
  );
}

function Ankieta({ klan, ankieta, onZmiana }) {
  const { t, i18n } = useTranslation();
  const [zajety, setZajety] = useState(false);
  const [blad, setBlad] = useState(null);

  async function wykonaj(akcja) {
    setZajety(true);
    setBlad(null);
    try {
      await akcja();
      await onZmiana();
    } catch (problem) {
      setBlad(describeError(problem).message);
    } finally {
      setZajety(false);
    }
  }

  const prowadzaca = ankieta.options.reduce((maks, o) => Math.max(maks, o.votes), 0);

  return (
    <Card className="klan-ankieta">
      <Card.Body>
        <h3 className="h6 klan-ankieta-pytanie">{ankieta.question}</h3>
        <p className="small text-body-secondary mb-2">
          {t('clans.polls.by', { username: ankieta.authorUsername })}
          {' · '}
          {ankieta.open
            ? t('clans.polls.endsAt', { when: formatDateTime(ankieta.closesAt, i18n.language) })
            : t('clans.polls.ended')}
        </p>
        {blad && <Alert variant="danger" className="py-1">{blad}</Alert>}

        <ul className="list-unstyled klan-ankieta-odpowiedzi mb-2">
          {ankieta.options.map((o) => {
            const procent = ankieta.totalVotes > 0 ? Math.round((100 * o.votes) / ankieta.totalVotes) : 0;
            const moja = ankieta.myOption === o.id;
            return (
              <li key={o.id}>
                <button
                  type="button"
                  className={`klan-ankieta-opcja${moja ? ' is-moja' : ''}${!ankieta.open && o.votes === prowadzaca && prowadzaca > 0 ? ' is-wygrana' : ''}`}
                  disabled={zajety || !ankieta.open}
                  aria-pressed={moja}
                  onClick={() => wykonaj(() => (moja
                    ? klany.cofnijGlosAnkiety(klan.id, ankieta.id)
                    : klany.glosujWAnkiecie(klan.id, ankieta.id, o.id)))}
                >
                  <span className="klan-ankieta-pasek" style={{ width: `${procent}%` }} aria-hidden="true" />
                  <span className="klan-ankieta-tekst">{o.text}</span>
                  <span className="klan-ankieta-liczby">{t('clans.polls.votes', { count: o.votes })} · {procent}%</span>
                </button>
              </li>
            );
          })}
        </ul>

        <div className="d-flex align-items-center flex-wrap gap-2">
          <span className="small text-body-secondary me-auto">
            {t('clans.polls.total', { count: ankieta.totalVotes })}
            {ankieta.open && ankieta.myOption != null && ` · ${t('clans.polls.voteHint')}`}
          </span>
          {ankieta.canManage && ankieta.open && (
            <Button size="sm" variant="outline-secondary" disabled={zajety}
              onClick={() => wykonaj(() => klany.zamknijAnkiete(klan.id, ankieta.id))}>
              {t('clans.polls.close')}
            </Button>
          )}
          {ankieta.canManage && (
            <Button size="sm" variant="outline-danger" disabled={zajety}
              aria-label={t('clans.polls.delete')} title={t('clans.polls.delete')}
              onClick={() => {
                if (window.confirm(t('clans.polls.deleteConfirm'))) {
                  wykonaj(() => klany.usunAnkiete(klan.id, ankieta.id));
                }
              }}>
              <IconTrash size={14} />
            </Button>
          )}
        </div>
      </Card.Body>
    </Card>
  );
}

function NowaAnkieta({ klan, onDodana }) {
  const { t } = useTranslation();
  const [otwarta, setOtwarta] = useState(false);
  const [pytanie, setPytanie] = useState('');
  const [odpowiedzi, setOdpowiedzi] = useState(['', '']);
  const [dni, setDni] = useState(7);
  const [zajety, setZajety] = useState(false);
  const [blad, setBlad] = useState(null);

  const gotowa = pytanie.trim() && odpowiedzi.filter((o) => o.trim()).length >= MIN_ODPOWIEDZI;

  async function zaloz(e) {
    e.preventDefault();
    setZajety(true);
    setBlad(null);
    try {
      await klany.zalozAnkiete(klan.id, {
        question: pytanie.trim(),
        options: odpowiedzi.map((o) => o.trim()).filter(Boolean),
        days: Number(dni),
      });
      setPytanie('');
      setOdpowiedzi(['', '']);
      setOtwarta(false);
      await onDodana();
    } catch (problem) {
      setBlad(describeError(problem).message);
    } finally {
      setZajety(false);
    }
  }

  if (!otwarta) {
    return (
      <div>
        <Button onClick={() => setOtwarta(true)}><IconPlus size={14} className="me-1" />{t('clans.polls.new')}</Button>
      </div>
    );
  }

  return (
    <Card>
      <Card.Body>
        <Card.Title as="h2" className="h6 text-uppercase text-body-secondary">{t('clans.polls.new')}</Card.Title>
        {blad && <Alert variant="danger">{blad}</Alert>}
        <Form onSubmit={zaloz} noValidate>
          <Form.Label htmlFor="ankieta-pytanie">{t('clans.polls.question')}</Form.Label>
          <Form.Control id="ankieta-pytanie" value={pytanie} maxLength={MAKS_PYTANIE} className="mb-3"
            placeholder={t('clans.polls.questionPlaceholder')} onChange={(e) => setPytanie(e.target.value)} />

          <span className="form-label d-block">{t('clans.polls.options')}</span>
          {odpowiedzi.map((o, i) => (
            // eslint-disable-next-line react/no-array-index-key
            <div key={i} className="d-flex gap-2 mb-2">
              <Form.Control value={o} maxLength={MAKS_ODPOWIEDZ} aria-label={t('clans.polls.optionN', { n: i + 1 })}
                placeholder={t('clans.polls.optionN', { n: i + 1 })}
                onChange={(e) => setOdpowiedzi(odpowiedzi.map((x, j) => (j === i ? e.target.value : x)))} />
              {odpowiedzi.length > MIN_ODPOWIEDZI && (
                <Button type="button" variant="outline-secondary" aria-label={t('clans.polls.removeOption')}
                  onClick={() => setOdpowiedzi(odpowiedzi.filter((_, j) => j !== i))}>
                  ×
                </Button>
              )}
            </div>
          ))}
          {odpowiedzi.length < MAKS_ODPOWIEDZI && (
            <Button type="button" variant="link" size="sm" className="px-0 mb-3"
              onClick={() => setOdpowiedzi([...odpowiedzi, ''])}>
              {t('clans.polls.addOption')}
            </Button>
          )}

          <Form.Label htmlFor="ankieta-dni">{t('clans.polls.duration')}</Form.Label>
          <Form.Select id="ankieta-dni" value={dni} onChange={(e) => setDni(e.target.value)} className="mb-3 w-auto">
            {DNI.map((d) => <option key={d} value={d}>{t('clans.polls.days', { count: d })}</option>)}
          </Form.Select>

          <div className="d-flex gap-2">
            <Button type="submit" disabled={zajety || !gotowa}>{t('clans.polls.publish')}</Button>
            <Button variant="outline-secondary" disabled={zajety} onClick={() => setOtwarta(false)}>
              {t('common.cancel')}
            </Button>
          </div>
        </Form>
      </Card.Body>
    </Card>
  );
}
