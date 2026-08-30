import { useTranslation } from 'react-i18next';
import { LANGUAGES, changeLanguage } from '../i18n';

/** Przelacznik jezyka jako jedna pigulka z przesuwajacym sie wskaznikiem. */
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
