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
const RODZAJE = [
  { kod: 'FIRE', emotka: '🔥' },
  { kod: 'MID', emotka: '😐' },
  // Ziewniecie zamiast lapki w dol - "nie porwalo mnie", a nie "to jest zle"
  { kod: 'MEH', emotka: '🥱' },
];

export default function Reakcje({ post, onZmiana }) {
  const { t } = useTranslation();
  const [wysylanie, setWysylanie] = useState(false);
  const [blad, setBlad] = useState(false);

  const reakcje = post.reactions;

  async function klik(kod) {
    if (wysylanie) {
      return;
    }
    setWysylanie(true);
    setBlad(false);

    try {
      /*
       * Klikniecie we WLASNA reakcje ja cofa, klikniecie w inna - podmienia.
       * Decyzje podejmujemy tutaj, bo to przegladarka wie, co jest aktualnie
       * zaznaczone. Backend zostaje prosty: PUT ustawia, DELETE kasuje,
       * i oba mozna wyslac dwa razy bez niespodzianek.
       */
      const odpowiedz = reakcje.mine === kod
        ? await client.delete(`/posts/${post.id}/reaction`)
        : await client.put(`/posts/${post.id}/reaction`, { type: kod });

      // Serwer odsyla caly post z przeliczonymi licznikami - nie dodajemy +1 sami
      onZmiana(odpowiedz.data);
    } catch {
      setBlad(true);
    } finally {
      setWysylanie(false);
    }
  }

  return (
    <div className="d-flex align-items-center gap-2 flex-wrap mt-2">
      {RODZAJE.map(({ kod, emotka }) => {
        const moja = reakcje.mine === kod;
        const ile = reakcje.counts[kod] ?? 0;

        return (
          <Button
            key={kod}
            type="button"
            size="sm"
            variant={moja ? 'primary' : 'outline-secondary'}
            disabled={wysylanie}
            onClick={() => klik(kod)}
            aria-pressed={moja}
            title={t(`reactions.${kod}`)}
            className="przycisk-reakcji"
          >
            <span aria-hidden="true">{emotka}</span>
            <span className="visually-hidden">{t(`reactions.${kod}`)}</span>
            {ile > 0 && <span className="ms-1">{ile}</span>}
          </Button>
        );
      })}

      {reakcje.total > 0 && (
        <span className="text-body-secondary small ms-1">
          {t('reactions.total', { count: reakcje.total })}
        </span>
      )}

      {blad && <span className="text-danger small">{t('reactions.error')}</span>}
    </div>
  );
}
