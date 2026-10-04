import { useCallback, useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import Alert from 'react-bootstrap/Alert';
import { describeError } from '../../api/client';
import * as poznawaj from '../../api/poznawaj';
import { useAuth } from '../../auth/AuthContext';
import { useChat } from '../../chat/ChatContext';
import BlockButton from '../BlockButton';
import EmptyState from '../EmptyState';
import PodpowiedzMiasta from '../PodpowiedzMiasta';
import ReportButton from '../ReportButton';
import Zasieg from '../Zasieg';
import { ZASIEGI } from '../../hooks/useZasieg';
import { IconCards, IconCross, IconHeart, IconUndo } from '../Icons';
import Dopasowanie from './Dopasowanie';
import KartaPoznawaj from './KartaPoznawaj';
import PrzesuwanaKarta from './PrzesuwanaKarta';

/** Ile kart prosimy naraz i od ilu zostalych dociagamy nastepne. */
const PORCJA = 10;
const DOCIAGNIJ_PRZY = 4;
/** Tyle kart lezy na stosie (wierzchnia + dwie pod spodem). */
const NA_STOSIE = 3;
/** Ostatnia decyzje serwer pozwala cofnac przez 10 minut. */
const COFNIJ_MS = 10 * 60 * 1000;

/**
 * Talia trybu Poznawaj: stos kart, przesuwanie, przyciski i klawiatura (strzalki w lewo i prawo), dociaganie
 * kolejnych kart, "cofnij", ekran wzajemnego "tak" i pusta talia z propozycja wiekszego zasiegu.
 */
export default function Talia({ stan, onStan }) {
  const { t } = useTranslation();
  const { user } = useAuth();
  const { openChat } = useChat();

  const [karty, setKarty] = useState([]);
  const [ladowanie, setLadowanie] = useState(true);
  /* Dociaganie w toku - wtedy pusty stol to jeszcze nie "to wszyscy" */
  const [pobieram, setPobieram] = useState(false);
  const [koniec, setKoniec] = useState(false);
  const [blad, setBlad] = useState(null);
  const [zostalo, setZostalo] = useState(stan.swipesLeft);
  const [zasiegDziala, setZasiegDziala] = useState(Boolean(stan.city));
  const [odlot, setOdlot] = useState(null);
  const [para, setPara] = useState(null);
  const [ostatnia, setOstatnia] = useState(null);
  const [komunikat, setKomunikat] = useState('');
  const pobieranie = useRef(false);
  const wierzch = useRef(null);
  const zasieg = stan.radiusKm;

  const dobierz = useCallback(async (obecne, odNowa = false) => {
    if (pobieranie.current) {
      return;
    }
    pobieranie.current = true;
    setPobieram(true);
    try {
      const dane = await poznawaj.talia(obecne.map((k) => k.username), PORCJA);
      setKarty((stare) => {
        const baza = odNowa ? [] : stare;
        const znane = new Set(baza.map((k) => k.username));
        return [...baza, ...dane.cards.filter((k) => !znane.has(k.username))];
      });
      setKoniec(dane.cards.length < PORCJA);
      setZostalo(dane.swipesLeft);
      setZasiegDziala(dane.radiusActive);
      setBlad(null);
    } catch (problem) {
      setBlad(describeError(problem).message);
    } finally {
      pobieranie.current = false;
      setPobieram(false);
      setLadowanie(false);
    }
  }, []);

  /* Pierwsze karty i nowe po zmianie zasiegu */
  useEffect(() => {
    setLadowanie(true);
    setKoniec(false);
    setKarty([]);
    dobierz([], true);
  }, [zasieg, dobierz]);

  /* Koncza sie karty - dociagamy nastepne, zanim ktos dojdzie do pustego stolu */
  useEffect(() => {
    if (!ladowanie && !koniec && karty.length < DOCIAGNIJ_PRZY) {
      dobierz(karty);
    }
  }, [karty, ladowanie, koniec, dobierz]);

  const decyduj = useCallback((rodzaj) => {
    if (karty.length === 0 || odlot || zostalo <= 0) {
      return;
    }
    setOdlot(rodzaj);
  }, [karty.length, odlot, zostalo]);

  /** Karta odleciala - zdejmujemy ja ze stosu i dopiero teraz mowimy serwerowi. */
  const odleciala = useCallback(async (rodzaj) => {
    const karta = karty[0];
    setOdlot(null);
    if (!karta) {
      return;
    }
    setKarty((stare) => stare.filter((k) => k.username !== karta.username));
    setKomunikat(t(rodzaj === 'LIKE' ? 'discover.announceLike' : 'discover.announcePass', { username: karta.username }));
    try {
      const wynik = await poznawaj.decyzja(karta.username, rodzaj);
      setZostalo(wynik.swipesLeft);
      setOstatnia({ username: karta.username, kiedy: Date.now() });
      if (wynik.matched) {
        setOstatnia(null);
        setPara({ username: wynik.username, avatarUrl: wynik.avatarUrl });
      }
    } catch (problem) {
      const status = problem.response?.status;
      if (status === 409) {
        // Ta osoba tymczasem wyszla z talii (wylaczyla tryb, juz jestescie znajomymi) - po prostu dalej
        return;
      }
      // Limit na dzis albo blad sieci - karta wraca na wierzch, nic nie przepada
      setKarty((stare) => [karta, ...stare.filter((k) => k.username !== karta.username)]);
      if (status === 429) {
        setZostalo(0);
      } else {
        setBlad(describeError(problem).message);
      }
    }
  }, [karty, t]);

  async function cofnij() {
    try {
      const karta = await poznawaj.cofnij();
      setKarty((stare) => [karta, ...stare.filter((k) => k.username !== karta.username)]);
      setZostalo((z) => z + 1);
      setKomunikat(t('discover.announceUndo', { username: karta.username }));
    } catch (problem) {
      setBlad(problem.response?.status === 409
        ? t('discover.nothingToUndo') : describeError(problem).message);
    } finally {
      setOstatnia(null);
    }
  }

  function szczegoly() {
    const el = wierzch.current;
    const okladka = el?.querySelector('.pz-okladka');
    if (el && okladka) {
      el.scrollTo({ top: el.scrollTop > 10 ? 0 : okladka.offsetHeight - 56, behavior: 'smooth' });
    }
  }

  /* Klawiatura: strzalki w bok decyduja, w gore/dol - szczegoly. Nie wtedy, gdy ktos pisze w polu. */
  useEffect(() => {
    function klawisz(e) {
      const cel = e.target;
      if (para || e.altKey || e.ctrlKey || e.metaKey
        || cel.closest?.('input, textarea, select, [contenteditable], .modal')) {
        return;
      }
      if (e.key === 'ArrowRight') {
        e.preventDefault();
        decyduj('LIKE');
      } else if (e.key === 'ArrowLeft') {
        e.preventDefault();
        decyduj('PASS');
      }
    }
    window.addEventListener('keydown', klawisz);
    return () => window.removeEventListener('keydown', klawisz);
  }, [decyduj, para]);

  async function zmienZasieg(km) {
    try {
      onStan(await poznawaj.ustawienia(true, km));
    } catch (problem) {
      setBlad(describeError(problem).message);
    }
  }

  /** Nastepny wiekszy zasieg do zaproponowania przy pustej talii (0 = caly kraj). */
  const wiekszy = zasiegDziala && zasieg > 0
    ? ZASIEGI.filter((km) => km > zasieg).concat(0)[0]
    : null;
  const mozeCofnac = ostatnia && Date.now() - ostatnia.kiedy < COFNIJ_MS;
  const naStosie = karty.slice(0, NA_STOSIE);

  return (
    <div className="pz-talia-strona">
      <div className="pz-pasek">
        {stan.city ? (
          <div className="pz-zasieg">
            <span className="pz-zasieg-etykieta">{t('discover.range')}</span>
            <Zasieg wartosc={zasieg} onChange={zmienZasieg} miasto={stan.city} className="form-select-sm" />
          </div>
        ) : <span />}
        <span className={`pz-licznik${zostalo <= 10 ? ' is-malo' : ''}`}>
          {t('discover.swipesLeft', { count: zostalo })}
        </span>
      </div>

      <PodpowiedzMiasta tekst={t('discover.cityNudge')} />
      {blad && <Alert variant="danger" dismissible onClose={() => setBlad(null)} className="py-2">{blad}</Alert>}

      <div className="pz-stol">
        {(ladowanie || (pobieram && karty.length === 0)) && (
          <div className="pz-karta-szkielet" aria-busy="true" aria-label={t('common.loading')} />
        )}

        {!ladowanie && !pobieram && karty.length === 0 && (
          <EmptyState
            icon={IconCards}
            title={t('discover.empty.title')}
            text={zasiegDziala && zasieg > 0
              ? t('discover.empty.textRadius', { km: zasieg })
              : t('discover.empty.textAll')}
            action={(
              <div className="d-flex flex-wrap gap-2 justify-content-center">
                {wiekszy !== null && (
                  <button type="button" className="btn btn-primary" onClick={() => zmienZasieg(wiekszy)}>
                    {wiekszy === 0 ? t('discover.empty.widenAll') : t('discover.empty.widen', { km: wiekszy })}
                  </button>
                )}
                <Link to="/settings#karta" className="btn btn-outline-secondary">{t('discover.empty.improveCard')}</Link>
              </div>
            )}
          />
        )}

        {/* Od spodu do wierzchu: ostatnia w DOM-ie lezy na gorze */}
        {naStosie.map((karta, i) => ({ karta, i })).reverse().map(({ karta, i }) => (
          <PrzesuwanaKarta
            key={karta.username}
            glebokosc={i}
            odlot={i === 0 ? odlot : null}
            onPuszczona={decyduj}
            onOdlecial={odleciala}
          >
            <KartaPoznawaj
              ref={i === 0 ? wierzch : undefined}
              karta={karta}
              onSzczegoly={i === 0 ? szczegoly : undefined}
              przyciski={i === 0 && (
                <>
                  <ReportButton username={karta.username} contexts={['PROFILE']} />
                  <BlockButton
                    username={karta.username}
                    blocked={false}
                    onChange={() => setKarty((stare) => stare.filter((k) => k.username !== karta.username))}
                  />
                </>
              )}
            />
          </PrzesuwanaKarta>
        ))}
      </div>

      {zostalo <= 0 && karty.length > 0 && (
        <Alert variant="info" className="py-2 text-center mt-2">{t('discover.limitReached')}</Alert>
      )}

      <div className="pz-przyciski" role="group" aria-label={t('discover.actions')}>
        <button type="button" className="pz-przycisk is-cofnij" onClick={cofnij} disabled={!mozeCofnac || Boolean(odlot)}
          aria-label={t('discover.undo')} title={t('discover.undo')}>
          <IconUndo size={20} />
        </button>
        <button type="button" className="pz-przycisk is-nie" onClick={() => decyduj('PASS')}
          disabled={karty.length === 0 || Boolean(odlot) || zostalo <= 0}
          aria-label={t('discover.pass')} title={t('discover.pass')}>
          <IconCross size={28} />
        </button>
        <button type="button" className="pz-przycisk is-tak" onClick={() => decyduj('LIKE')}
          disabled={karty.length === 0 || Boolean(odlot) || zostalo <= 0}
          aria-label={t('discover.like')} title={t('discover.like')}>
          <IconHeart size={28} />
        </button>
      </div>
      <p className="pz-skroty">{t('discover.keyboardHint')}</p>

      <div className="visually-hidden" aria-live="polite">{komunikat}</div>

      {para && (
        <Dopasowanie
          ja={{ username: user.username, avatarUrl: user.avatarUrl }}
          on={para}
          onNapisz={() => { const kto = para.username; setPara(null); openChat(kto); }}
          onZamknij={() => setPara(null)}
        />
      )}
    </div>
  );
}
