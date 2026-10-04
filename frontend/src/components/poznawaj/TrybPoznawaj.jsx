import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import Alert from 'react-bootstrap/Alert';
import { describeError } from '../../api/client';
import * as poznawaj from '../../api/poznawaj';
import Zasieg from '../Zasieg';
import { IconCheckCircle, IconCross, IconPlus, IconLock, IconCards } from '../Icons';
import KartaPoznawaj from './KartaPoznawaj';
import Talia from './Talia';

/**
 * Zakladka "Poznawaj" w Znajomych. Kto ma tryb wylaczony, widzi, na czym polega, swoja karte i co na niej
 * warto uzupelnic - i jednym przyciskiem go wlacza. Kto ma wlaczony, dostaje talie.
 */
export default function TrybPoznawaj() {
  const { t } = useTranslation();
  const [stan, setStan] = useState(null);
  const [blad, setBlad] = useState(null);

  useEffect(() => {
    poznawaj.stan().then(setStan).catch((p) => setBlad(describeError(p).message));
  }, []);

  if (blad) {
    return <Alert variant="danger">{blad}</Alert>;
  }
  if (!stan) {
    return <div className="pz-karta-szkielet mx-auto" aria-busy="true" aria-label={t('common.loading')} />;
  }
  return stan.enabled
    ? <Talia stan={stan} onStan={setStan} />
    : <PoznawajStart stan={stan} onStan={setStan} />;
}

/** Ekran przed wlaczeniem: jak to dziala, co widza inni, moja karta i zasieg. */
function PoznawajStart({ stan, onStan }) {
  const { t } = useTranslation();
  const [zasieg, setZasieg] = useState(stan.city ? stan.radiusKm : 0);
  const [wysylanie, setWysylanie] = useState(false);
  const [blad, setBlad] = useState(null);

  async function wlacz() {
    setWysylanie(true);
    setBlad(null);
    try {
      onStan(await poznawaj.ustawienia(true, zasieg));
      // Przycisk jest na dole dlugiego ekranu - talia ma sie zaczac od gory, a nie w polowie karty
      window.scrollTo({ top: 0 });
    } catch (problem) {
      setBlad(describeError(problem).message);
    } finally {
      setWysylanie(false);
    }
  }

  const braki = [
    [stan.photoCount > 0, t('discover.start.todoPhoto')],
    [stan.hasBio, t('discover.start.todoBio')],
    [stan.promptCount > 0, t('discover.start.todoPrompt')],
    [stan.favoriteArtistCount > 0, t('discover.start.todoFavorites')],
  ];

  return (
    <div className="pz-start">
      <section className="pz-start-glowa">
        <div className="pz-start-ilustracja" aria-hidden="true">
          <span className="pz-start-kartka is-tyl" />
          <span className="pz-start-kartka is-srodek" />
          <span className="pz-start-kartka is-przod"><IconCards size={34} /></span>
        </div>
        <h2 className="pz-start-tytul">{t('discover.start.title')}</h2>
        <p className="pz-start-wstep">{t('discover.start.lead')}</p>
      </section>

      <ol className="pz-start-kroki list-unstyled">
        <li>
          <span className="pz-start-ikona is-tak"><IconPlus size={16} /></span>
          <span>{t('discover.start.stepRight')}</span>
        </li>
        <li>
          <span className="pz-start-ikona is-nie"><IconCross size={16} /></span>
          <span>{t('discover.start.stepLeft')}</span>
        </li>
        <li>
          <span className="pz-start-ikona is-para"><IconCheckCircle size={16} /></span>
          <span>{t('discover.start.stepMatch')}</span>
        </li>
        <li>
          <span className="pz-start-ikona is-prywatnosc"><IconLock size={15} /></span>
          <span>{t('discover.start.privacy')}</span>
        </li>
      </ol>

      <div className="pz-start-uklad">
        <div className="pz-start-podglad">
          <p className="pz-naglowek mb-2">{t('discover.start.yourCard')}</p>
          <div className="pz-podglad-ramka">
            <KartaPoznawaj karta={stan.preview} podglad />
          </div>
        </div>

        <div className="pz-start-panel">
          <p className="pz-naglowek mb-2">{t('discover.start.checklist')}</p>
          <ul className="pz-start-lista list-unstyled">
            {braki.map(([gotowe, tekst]) => (
              <li key={tekst} className={gotowe ? 'is-gotowe' : ''}>
                <span className="pz-start-znak" aria-hidden="true">{gotowe ? '✓' : ''}</span>
                <span>{tekst}</span>
              </li>
            ))}
          </ul>
          <Link to="/settings#karta" className="btn btn-outline-primary btn-sm mb-3">{t('discover.start.editCard')}</Link>

          <p className="form-label small fw-semibold mb-1">{t('discover.range')}</p>
          {stan.city ? (
            <Zasieg wartosc={zasieg} onChange={setZasieg} miasto={stan.city} className="mb-1" />
          ) : (
            <p className="small text-body-secondary mb-1">
              {t('discover.start.noCity')} <Link to="/settings#miasto">{t('location.setCity')}</Link>
            </p>
          )}
          <p className="small text-body-secondary">{t('discover.start.rangeHint')}</p>

          {blad && <Alert variant="danger" className="py-2">{blad}</Alert>}
          <button type="button" className="btn btn-primary btn-lg w-100" onClick={wlacz} disabled={wysylanie}>
            <IconCards className="me-2" />{t('discover.start.enable')}
          </button>
          <p className="small text-body-secondary mt-2 mb-0">{t('discover.start.canTurnOff')}</p>
        </div>
      </div>
    </div>
  );
}
