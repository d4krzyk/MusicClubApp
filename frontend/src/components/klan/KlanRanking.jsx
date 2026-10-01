import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import Alert from 'react-bootstrap/Alert';
import Button from 'react-bootstrap/Button';
import Card from 'react-bootstrap/Card';
import Spinner from 'react-bootstrap/Spinner';
import { describeError } from '../../api/client';
import * as klany from '../../api/klany';
import useOdswiezanie from '../../hooks/useOdswiezanie';
import Avatar from '../Avatar';

/**
 * Ranking aktywnosci i cel tygodnia. Punkty: wiadomosc 1, post 4, propozycja utworu 3, glos 1,
 * reakcja 1. Poziom (0-4) liczy sie z punktow z calego czasu, wiec nie spada, gdy tydzien jest spokojny.
 * Cel tygodnia dotyczy calego klanu - to zabawa we wspolny wynik, a nie wyscig.
 */
export default function KlanRanking({ klan }) {
  const { t } = useTranslation();
  const [okres, setOkres] = useState('WEEK');
  const [dane, setDane] = useState(null);
  const [blad, setBlad] = useState(null);

  const wczytaj = useCallback(async () => {
    try {
      setDane(await klany.aktywnosc(klan.id, okres));
      setBlad(null);
    } catch (problem) {
      setBlad(describeError(problem).message);
    }
  }, [klan.id, okres]);

  useEffect(() => { setDane(null); wczytaj(); }, [wczytaj]);
  useOdswiezanie(wczytaj, 60_000, true);

  if (!dane && !blad) {
    return (
      <div className="text-center py-4 text-body-secondary">
        <Spinner animation="border" size="sm" className="me-2" />{t('common.loading')}
      </div>
    );
  }

  const podsumowanie = dane?.summary;
  const procent = podsumowanie ? Math.min(100, Math.round((100 * podsumowanie.points) / podsumowanie.goal)) : 0;
  const osiagniety = podsumowanie && podsumowanie.points >= podsumowanie.goal;

  return (
    <div className="d-grid gap-3">
      {blad && <Alert variant="danger" className="mb-0">{blad}</Alert>}

      {podsumowanie && (
        <Card>
          <Card.Body>
            <Card.Title as="h2" className="h6 text-uppercase text-body-secondary">{t('clans.ranking.goalTitle')}</Card.Title>
            <div className="klan-cel" role="progressbar" aria-valuemin={0} aria-valuemax={podsumowanie.goal}
              aria-valuenow={Math.min(podsumowanie.points, podsumowanie.goal)}
              aria-label={t('clans.ranking.goalTitle')}>
              <span className={`klan-cel-pasek${osiagniety ? ' is-gotowy' : ''}`} style={{ width: `${procent}%` }} />
            </div>
            <p className="mb-1 mt-2">
              <strong>{podsumowanie.points}</strong> / {podsumowanie.goal} {t('clans.ranking.points')}
              {osiagniety && <span className="klan-cel-brawo"> · {t('clans.ranking.goalDone')}</span>}
            </p>
            <p className="small text-body-secondary mb-0">
              {t('clans.ranking.breakdown', {
                messages: podsumowanie.messages, posts: podsumowanie.posts, tracks: podsumowanie.tracks,
                votes: podsumowanie.votes, reactions: podsumowanie.reactions,
              })}
            </p>
          </Card.Body>
        </Card>
      )}

      <Card>
        <Card.Body>
          <div className="d-flex align-items-center flex-wrap gap-2 mb-2">
            <Card.Title as="h2" className="h6 text-uppercase text-body-secondary mb-0 me-auto">
              {t('clans.ranking.title')}
            </Card.Title>
            <div className="btn-group btn-group-sm" role="group" aria-label={t('clans.ranking.period')}>
              {['WEEK', 'ALL'].map((o) => (
                <Button key={o} variant={okres === o ? 'primary' : 'outline-secondary'} aria-pressed={okres === o}
                  onClick={() => setOkres(o)}>
                  {t(`clans.ranking.periods.${o}`)}
                </Button>
              ))}
            </div>
          </div>

          {dane && dane.ranking.length === 0 && <p className="mb-0">{t('clans.ranking.empty')}</p>}

          <ol className="klan-ranking list-unstyled mb-0">
            {dane?.ranking.map((r, i) => (
              <li key={r.username} className={`klan-ranking-wiersz${r.me ? ' is-ja' : ''}`}>
                <span className="klan-ranking-miejsce" aria-label={t('clans.ranking.place', { n: i + 1 })}>{i + 1}</span>
                <Avatar avatarUrl={r.avatarUrl} username={r.username} size={36} />
                <span className="klan-ranking-osoba">
                  <Link to={`/profil/${r.username}`} className="klan-ranking-nazwa">
                    {r.username}{r.me ? ` (${t('clans.members.you')})` : ''}
                  </Link>
                  <span className="small text-body-secondary klan-ranking-poziom">
                    {t('clans.ranking.level', { n: r.level })} · {t(`clans.ranking.levels.${r.level}`)}
                    {r.nextLevelAt != null && ` · ${t('clans.ranking.toNext', { count: r.nextLevelAt })}`}
                  </span>
                </span>
                <span className="klan-ranking-punkty">
                  <strong>{r.points}</strong>
                  <span className="small text-body-secondary">{t('clans.ranking.pointsShort')}</span>
                </span>
              </li>
            ))}
          </ol>

          <details className="klan-wiecej mt-3">
            <summary>{t('clans.ranking.howTitle')}</summary>
            <p className="small text-body-secondary mt-2 mb-0">{t('clans.ranking.how')}</p>
          </details>
        </Card.Body>
      </Card>
    </div>
  );
}
