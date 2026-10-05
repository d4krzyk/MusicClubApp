import { useTranslation } from 'react-i18next';
import { IconCalendar, IconFriends, IconPin } from './Icons';

const KROKI = [
  { klucz: 'find', Ikona: IconCalendar },
  { klucz: 'crew', Ikona: IconFriends },
  { klucz: 'meet', Ikona: IconPin },
];

/**
 * Do czego jest ta aplikacja - w trzech krokach, nad logowaniem i rejestracja. Ktos, kto trafil tu z linku, ma od razu
 * wiedziec, ze chodzi o chodzenie na koncerty z ludzmi, a nie o kolejny serwis z postami.
 */
export default function OAplikacji() {
  const { t } = useTranslation();
  return (
    <section className="o-aplikacji mx-auto mb-3" aria-labelledby="o-aplikacji">
      <h2 id="o-aplikacji" className="o-aplikacji-haslo">{t('about.headline')}</h2>
      <ol className="o-aplikacji-kroki list-unstyled">
        {KROKI.map(({ klucz, Ikona }, i) => (
          <li key={klucz} className="o-aplikacji-krok mc-wejscie" style={{ '--i': i }}>
            <span className="o-aplikacji-ikona" aria-hidden="true"><Ikona size={18} /></span>
            <span>
              <strong className="d-block">{t(`about.${klucz}.title`)}</strong>
              <span className="small text-body-secondary">{t(`about.${klucz}.text`)}</span>
            </span>
          </li>
        ))}
      </ol>
    </section>
  );
}
