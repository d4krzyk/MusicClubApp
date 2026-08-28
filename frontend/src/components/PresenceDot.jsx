import { useTranslation } from 'react-i18next';
import { timeAgo } from '../utils/dates';

/**
 * Kropka "online" / "offline" z podpowiedzia, kiedy ktos byl ostatnio.
 *
 * <p><b>O tym, czy ktos jest online, decyduje SERWER</b> - tutaj tylko
 * rysujemy pole {@code online} z odpowiedzi. Gdyby przegladarka wyliczala to
 * sama z daty, wynik zalezalby od zegara komputera uzytkownika, ktory bywa
 * przestawiony o godziny.</p>
 *
 * <p><b>Sama kropka nie wystarczy.</b> Kolor mowi "teraz albo nie teraz",
 * a to za malo: roznica miedzy "byl 5 minut temu" a "byl w zeszlym miesiacu"
 * jest dla rozmowy zasadnicza, a obie sytuacje daja szara kropke. Dlatego
 * tekst obok (albo przynajmniej w dymku {@code title}) podaje date.</p>
 *
 * @param presence obiekt {@code {online, lastSeenAt}} z serwera
 * @param withLabel czy dopisac tekst obok kropki
 */
export default function PresenceDot({ presence, withLabel = false }) {
  const { t, i18n } = useTranslation();

  if (!presence) {
    return null;
  }

  const label = presence.online
    ? t('presence.online')
    : presence.lastSeenAt
      ? t('presence.lastSeen', { when: timeAgo(presence.lastSeenAt, i18n.language) })
      : t('presence.never');

  return (
    <span className={`presence${presence.online ? ' is-online' : ''}`} title={label}>
      {/*
        Kropka jest ozdobna dla czytnika ekranu - cala tresc niesie tekst
        obok albo etykieta. Bez tego czytnik oglaszalby pusty element.
      */}
      <span className="presence-dot" aria-hidden="true" />

      {withLabel
        ? <span className="presence-label">{label}</span>
        : <span className="visually-hidden">{label}</span>}
    </span>
  );
}
