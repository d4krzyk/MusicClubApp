import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import Avatar from '../Avatar';
import { czyEmojiDziala } from '../../utils/emoji';
import { IconChat, IconFriends } from '../Icons';

/** Przytulajace sie ludziki - to aplikacja do poznawania ludzi, nie randkowa, wiec bez serduszek. */
const PRZYTULAS = '\u{1FAC2}';

/**
 * "Nowa znajomosc!" - wzajemne "tak". Od tej chwili jestescie znajomymi, wiec mozna od razu napisac.
 * Miedzy awatarami przytulajace sie ludziki; gdzie system ich nie ma (Windows 10, stare Androidy) -
 * ikona dwoch osob zamiast pustego prostokata.
 * Okienko zamyka Esc, klikniecie w tlo i "Przegladaj dalej".
 */
export default function Dopasowanie({ ja, on, onNapisz, onZamknij }) {
  const { t } = useTranslation();
  const glowny = useRef(null);
  const [emoji] = useState(() => czyEmojiDziala(PRZYTULAS));

  useEffect(() => {
    glowny.current?.focus();
    function klawisz(e) {
      if (e.key === 'Escape') {
        onZamknij();
      }
    }
    window.addEventListener('keydown', klawisz);
    return () => window.removeEventListener('keydown', klawisz);
  }, [onZamknij]);

  return (
    <div className="pz-dopasowanie" role="dialog" aria-modal="true" aria-labelledby="pz-dopasowanie-tytul"
      onClick={onZamknij}>
      <div className="pz-dopasowanie-okno" onClick={(e) => e.stopPropagation()}>
        <div className="pz-dopasowanie-awatary" aria-hidden="true">
          <span className="pz-dopasowanie-awatar is-lewy"><Avatar avatarUrl={ja.avatarUrl} username={ja.username} size={96} /></span>
          <span className={`pz-dopasowanie-znak${emoji ? ' is-emoji' : ''}`}>
            {emoji ? PRZYTULAS : <IconFriends size={24} />}
          </span>
          <span className="pz-dopasowanie-awatar is-prawy"><Avatar avatarUrl={on.avatarUrl} username={on.username} size={96} /></span>
        </div>
        <h2 id="pz-dopasowanie-tytul" className="pz-dopasowanie-tytul">{t('discover.match.title')}</h2>
        <p className="pz-dopasowanie-tekst">{t('discover.match.text', { username: on.username })}</p>
        <div className="d-grid gap-2">
          <button ref={glowny} type="button" className="btn btn-primary btn-lg" onClick={onNapisz}>
            <IconChat className="me-2" />{t('discover.match.write')}
          </button>
          <Link to={`/profil/${encodeURIComponent(on.username)}`} className="btn btn-outline-light">
            {t('discover.match.profile')}
          </Link>
          <button type="button" className="btn btn-link pz-dopasowanie-dalej" onClick={onZamknij}>
            {t('discover.match.continue')}
          </button>
        </div>
      </div>
    </div>
  );
}
