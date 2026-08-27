import { useTranslation } from 'react-i18next';
import { IconGlobe, IconLock } from './Icons';

/** Kolejnosc na ekranie - od najszerszej widocznosci. */
const OPTIONS = [
  { value: 'PUBLIC', icon: IconGlobe },
  { value: 'FRIENDS', icon: IconLock },
];

/**
 * Wybor: post publiczny czy tylko dla znajomych.
 *
 * <p><b>Dwa widoczne przyciski, a nie lista rozwijana ani przelacznik.</b>
 * Lista chowa drugie mozliwosc za klknieciem, a przelacznik ("tylko dla
 * znajomych: wl./wyl.") wymaga przeczytania podpisu, zeby wiedziec, co
 * znaczy jego biezacy stan. Tutaj oba warianty sa nazwane i widac, ktory
 * jest wybrany.</p>
 *
 * <p>Pod spodem jest jedno zdanie o skutkach wyboru. Sama nazwa "tylko dla
 * znajomych" nie mowi przeciez, czy chodzi o dzisiejszych znajomych,
 * czy takze przyszlych.</p>
 */
export default function VisibilityPicker({ value = 'PUBLIC', onChange, id = 'visibility' }) {
  const { t } = useTranslation();

  return (
    <fieldset className="mb-3">
      <legend className="form-label mb-1">{t('posts.visibility.label')}</legend>

      <div className="segmented" role="group" aria-label={t('posts.visibility.label')}>
        {OPTIONS.map(({ value: option, icon: Icon }) => (
          <button
            key={option}
            type="button"
            id={`${id}-${option}`}
            className={`segmented-option${value === option ? ' is-active' : ''}`}
            aria-pressed={value === option}
            onClick={() => onChange(option)}
          >
            <Icon size={14} />
            <span>{t(`posts.visibility.${option}`)}</span>
          </button>
        ))}
      </div>

      <p className="form-text mb-0">{t(`posts.visibility.${value}Hint`)}</p>
    </fieldset>
  );
}
