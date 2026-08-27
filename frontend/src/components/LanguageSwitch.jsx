import { useTranslation } from 'react-i18next';
import { LANGUAGES, changeLanguage } from '../i18n';

/**
 * Przelacznik jezyka jako jedna pigulka z przesuwajacym sie wskaznikiem.
 *
 * <p><b>Dlaczego nie dwa przyciski.</b> Dwa osobne przyciski nie mowia, ze
 * wybor jest jeden z dwoch - wygladaja jak dwie niezalezne akcje. Pigulka
 * z jednym podswietleniem pokazuje to od razu, a przesuniecie wskaznika przy
 * zmianie samo tlumaczy, co sie wlasnie stalo.</p>
 *
 * <p>Wskaznik jest osobnym elementem pod spodem i przesuwamy go przez
 * {@code transform}. Animowanie tla kazdego przycisku dawaloby dwa niezalezne
 * przejscia zamiast jednego ruchu.</p>
 */
export default function LanguageSwitch() {
  const { t, i18n } = useTranslation();

  const active = Math.max(LANGUAGES.indexOf(i18n.language), 0);

  return (
    <div
      className="lang-switch"
      role="group"
      aria-label={t('menu.language')}
      style={{ '--lang-count': LANGUAGES.length }}
    >
      <span
        className="lang-thumb"
        aria-hidden="true"
        style={{ transform: `translateX(${active * 100}%)` }}
      />

      {LANGUAGES.map((code) => (
        <button
          key={code}
          type="button"
          className={`lang-option${i18n.language === code ? ' is-active' : ''}`}
          onClick={() => changeLanguage(code)}
          aria-pressed={i18n.language === code}
        >
          {code.toUpperCase()}
        </button>
      ))}
    </div>
  );
}
