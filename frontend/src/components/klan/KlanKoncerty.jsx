import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import Alert from 'react-bootstrap/Alert';
import Button from 'react-bootstrap/Button';
import { describeError } from '../../api/client';
import * as klany from '../../api/klany';
import * as posty from '../../api/posty';
import Avatar from '../Avatar';
import EmptyState from '../EmptyState';
import { IconCalendar, IconClock, IconPin } from '../Icons';
import { godzina, pelnaData, plakietka } from '../../utils/wydarzenia';

/**
 * "Klan idzie na koncert": nadchodzace wydarzenia, na ktore zapisali sie czlonkowie klanu, oraz
 * jedno klikniecie, ktore zadaje klanowi pytanie "kto jedzie?" jako post klanu pod tym wydarzeniem
 * (zwykly post - z zapisami i przypomnieniami dziala to tak samo jak przy pojedynczej osobie).
 */
export default function KlanKoncerty({ klan, onPost }) {
  const { t, i18n } = useTranslation();
  const [lista, setLista] = useState(null);
  const [blad, setBlad] = useState(null);
  const [info, setInfo] = useState(null);
  const [zajety, setZajety] = useState(null);
  const mozePytac = klan.myRole != null;

  const wczytaj = useCallback(async () => {
    try {
      setLista(await klany.koncerty(klan.id));
    } catch (problem) {
      setBlad(describeError(problem).message);
    }
  }, [klan.id]);

  useEffect(() => { wczytaj(); }, [wczytaj]);

  async function zapytaj(wydarzenie) {
    setZajety(wydarzenie.id);
    setBlad(null);
    setInfo(null);
    try {
      const tresc = t('clans.events.askText', {
        name: wydarzenie.name, date: pelnaData(wydarzenie.startDate, i18n.language),
      });
      await posty.dodaj({
        content: tresc, visibility: 'PUBLIC', eventId: wydarzenie.id, clanId: klan.id,
      }, []);
      setInfo(t('clans.events.askSent'));
      onPost?.();
      await wczytaj();
    } catch (problem) {
      setBlad(describeError(problem).message);
    } finally {
      setZajety(null);
    }
  }

  return (
    <section className="d-grid gap-3" aria-label={t('clans.events.title')}>
      <div>
        <h2 className="h6 text-uppercase text-body-secondary mb-1">{t('clans.events.title')}</h2>
        <p className="small text-body-secondary mb-0">{t('clans.events.intro')}</p>
      </div>
      {blad && <Alert variant="danger" className="mb-0">{blad}</Alert>}
      {info && <Alert variant="success" className="mb-0" dismissible onClose={() => setInfo(null)}>{info}</Alert>}

      {lista && lista.length === 0 && (
        <EmptyState icon={IconCalendar} title={t('clans.events.emptyTitle')} text={t('clans.events.emptyText')} />
      )}

      {lista && lista.map((w) => {
        const { dzien, miesiac } = plakietka(w.startDate, i18n.language);
        const ukryci = w.goingCount - w.going.length;
        return (
          <article key={w.id} className="klan-koncert">
            <div className="klan-koncert-data" aria-hidden="true">
              <span className="klan-koncert-dzien">{dzien}</span>
              <span className="klan-koncert-miesiac">{miesiac}</span>
            </div>
            <div className="klan-koncert-tresc">
              <Link to={`/wydarzenia/${w.id}`} className="klan-koncert-nazwa">{w.name}</Link>
              <div className="klan-koncert-meta">
                <span><IconClock size={12} />{w.startTime ? godzina(w.startTime) : t('events.timeUnknown')}</span>
                <span><IconPin size={12} /><span className="klan-koncert-miejsce">{[w.venueName, w.city].filter(Boolean).join(' · ')}</span></span>
              </div>

              <div className="klan-koncert-osoby">
                {w.going.length > 0 && (
                  <span className="klan-koncert-avatary">
                    {w.going.slice(0, 8).map((o) => (
                      <Avatar key={o.username} avatarUrl={o.avatarUrl} username={o.username} size={24} />
                    ))}
                  </span>
                )}
                <span className="small">
                  {w.goingCount > 0 && t('clans.events.going', { count: w.goingCount })}
                  {w.goingCount > 0 && w.interestedCount > 0 && ' · '}
                  {w.interestedCount > 0 && t('clans.events.interested', { count: w.interestedCount })}
                </span>
              </div>
              {w.going.length > 0 && (
                <p className="small text-body-secondary mb-0 klan-koncert-lista">
                  {w.going.map((o) => o.username).join(', ')}
                  {ukryci > 0 && ` · ${t('clans.events.hiddenNote')}`}
                </p>
              )}

              <div className="klan-koncert-akcje">
                {w.mine && <span className="klan-koncert-moj">{t(`clans.events.mine.${w.mine}`)}</span>}
                {mozePytac && (w.askedPostId ? (
                  <Button variant="outline-secondary" size="sm" disabled>{t('clans.events.asked')}</Button>
                ) : (
                  <Button size="sm" disabled={zajety === w.id} onClick={() => zapytaj(w)}>{t('clans.events.ask')}</Button>
                ))}
              </div>
            </div>
          </article>
        );
      })}
    </section>
  );
}
