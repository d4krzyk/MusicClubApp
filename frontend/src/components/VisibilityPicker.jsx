import { useRef } from 'react';
import { useTranslation } from 'react-i18next';
import useWskaznik from '../hooks/useWskaznik';
import { IconGlobe, IconLock } from './Icons';

/** Kolejnosc na ekranie - od najszerszej widocznosci. */
const OPTIONS = [
  { value: 'PUBLIC', icon: IconGlobe },
  { value: 'FRIENDS', icon: IconLock },
];

/** Wybor: post publiczny czy tylko dla znajomych. */
export default function VisibilityPicker({ value = 'PUBLIC', onChange, id = 'visibility' }) {
  const { t } = useTranslation();
  const przelacznik = useRef(null);
  useWskaznik(przelacznik, value);

  return (
    <fieldset className="mb-3">
      <legend className="form-label mb-1">{t('posts.visibility.label')}</legend>

      <div ref={przelacznik} className="segmented" role="group" aria-label={t('posts.visibility.label')}>
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
