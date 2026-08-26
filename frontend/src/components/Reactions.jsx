import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import Button from 'react-bootstrap/Button';
import client from '../api/client';

/**
 * Trzy reakcje pod postem: ogien, "mid" i "meh".
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
  { code: 'FIRE', emotka: '🔥' },
  { code: 'MID', emotka: '😐' },
  // Ziewniecie zamiast lapki w dol - "nie porwalo mnie", a nie "to jest zle"
  { code: 'MEH', emotka: '🥱' },
];

export default function Reactions({ post, onChange }) {
  const { t } = useTranslation();
  const [wysylanie, setWysylanie] = useState(false);
  const [error, setError] = useState(false);

  const reakcje = post.reactions;

  async function react(code) {
    if (wysylanie) {
      return;
    }
    setWysylanie(true);
    setError(false);

    try {
      /*
       * Klikniecie we WLASNA reakcje ja cofa, klikniecie w inna - podmienia.
       * Decyzje podejmujemy tutaj, bo to przegladarka wie, co jest aktualnie
       * zaznaczone. Backend zostaje prosty: PUT ustawia, DELETE kasuje,
       * i oba mozna wyslac dwa razy bez niespodzianek.
       */
      const response = reakcje.mine === code
        ? await client.delete(`/posts/${post.id}/reaction`)
        : await client.put(`/posts/${post.id}/reaction`, { type: code });

      // Serwer odsyla caly post z przeliczonymi licznikami - nie dodajemy +1 sami
      onChange(response.data);
    } catch {
      setError(true);
    } finally {
      setWysylanie(false);
    }
  }

  return (
    <div className="d-flex align-items-center gap-2 flex-wrap mt-2">
      {KINDS.map(({ code, emotka }) => {
        const moja = reakcje.mine === code;
        const ile = reakcje.counts[code] ?? 0;

        return (
          <Button
            key={code}
            type="button"
            size="sm"
            variant={moja ? 'primary' : 'outline-secondary'}
            disabled={wysylanie}
            onClick={() => react(code)}
            aria-pressed={moja}
            title={t(`reactions.${code}`)}
            className="reaction-button"
          >
            <span aria-hidden="true">{emotka}</span>
            <span className="visually-hidden">{t(`reactions.${code}`)}</span>
            {ile > 0 && <span className="ms-1">{ile}</span>}
          </Button>
        );
      })}

      {reakcje.total > 0 && (
        <span className="text-body-secondary small ms-1">
          {t('reactions.total', { count: reakcje.total })}
        </span>
      )}

      {error && <span className="text-danger small">{t('reactions.error')}</span>}
    </div>
  );
}
