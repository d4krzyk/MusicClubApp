import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import Button from 'react-bootstrap/Button';
import { cofnijReakcje, ustawReakcje } from '../api/posty';
import ReactionAuthors from './ReactionAuthors';

/**
 * Trzy summary pod postem: ogien, "mid" i "meh".
 *
 * <p><b>Emotki sa TYLKO tutaj.</b> Backend zna wylacznie nazwy
 * ({@code FIRE}, {@code MID}, {@code MEH}) - dzieki temu podmiana obrazka
 * albo dodanie tlumaczenia nie wymaga ruszania bazy ani serwera.</p>
 *
 * <p><b>Skad wiadomo, co jest zaznaczone?</b> Z pola {@code reactions.mine},
 * ktore wylicza serwer. Nie zgadujemy tego lokalnie - inaczej po odswiezeniu
 * strony podswietlenie mogloby sie rozjechac z tym, co naprawde jest w bazie.</p>
 */

/** Kolejnosc na ekranie - od najbardziej pozytywnej. */
const KINDS = [
  { code: 'FIRE', emoji: '🔥' },
  { code: 'MID', emoji: '😐' },
  // Ziewniecie zamiast lapki w dol - "nie porwalo mnie", a nie "to jest zle"
  { code: 'MEH', emoji: '🥱' },
];

export default function Reactions({ post, onChange }) {
  const { t } = useTranslation();
  const [sending, setSending] = useState(false);
  const [error, setError] = useState(false);
  const [showAuthors, setShowAuthors] = useState(false);

  const summary = post.reactions;

  async function react(code) {
    if (sending) {
      return;
    }
    setSending(true);
    setError(false);

    try {
      /*
       * Klikniecie we WLASNA summary ja cofa, klikniecie w inna - podmienia.
       * Decyzje podejmujemy tutaj, bo to przegladarka wie, co jest aktualnie
       * zaznaczone. Backend zostaje prosty: PUT ustawia, DELETE kasuje,
       * i oba mozna wyslac dwa razy bez niespodzianek.
       */
      const zmieniony = summary.mine === code
        ? await cofnijReakcje(post.id)
        : await ustawReakcje(post.id, code);

      // Serwer odsyla caly post z przeliczonymi licznikami - nie dodajemy +1 sami
      onChange(zmieniony);
    } catch {
      setError(true);
    } finally {
      setSending(false);
    }
  }

  return (
    <div className="d-flex align-items-center gap-2 flex-wrap mt-2">
      {KINDS.map(({ code, emoji }) => {
        const mine = summary.mine === code;
        const count = summary.counts[code] ?? 0;

        return (
          <Button
            key={code}
            type="button"
            size="sm"
            variant={mine ? 'primary' : 'outline-secondary'}
            disabled={sending}
            onClick={() => react(code)}
            aria-pressed={mine}
            title={t(`reactions.${code}`)}
            className="reaction-button"
          >
            <span aria-hidden="true">{emoji}</span>
            <span className="visually-hidden">{t(`reactions.${code}`)}</span>
            {count > 0 && <span className="ms-1">{count}</span>}
          </Button>
        );
      })}

      {/*
        Podsumowanie jest przyciskiem, a nie napisem. Liczba mowi ILE osob,
        ale nie mowi KTO - a przy paru reakcjach to wlasnie druga rzecz jest
        ciekawa. Okienko otwiera sie na zadanie, wiec lista osob nie jest
        pobierana dla kazdego posta na tablicy z osobna.
      */}
      {summary.total > 0 && (
        <button
          type="button"
          className="reaction-total"
          onClick={() => setShowAuthors(true)}
        >
          {t('reactions.total', { count: summary.total })}
        </button>
      )}

      {error && <span className="text-danger small">{t('reactions.error')}</span>}

      <ReactionAuthors
        postId={post.id}
        show={showAuthors}
        onHide={() => setShowAuthors(false)}
      />
    </div>
  );
}
