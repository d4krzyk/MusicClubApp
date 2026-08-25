import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import client, { opiszBlad } from '../api/client';
import { sformatujDate } from '../utils/daty';

/**
 * Lista uzytkownikow ze stronicowaniem i sortowaniem.
 *
 * <p>Ta strona istnieje po to, zeby bylo WIDAC dzialanie wymagan nr 3
 * (stronicowanie + wybor liczby elementow) i nr 5 (sortowanie). Cala praca
 * dzieje sie po stronie backendu - tutaj tylko wysylamy parametry
 * i rysujemy to, co przyszlo. Na obronie mozna kliknac i pokazac,
 * jak zmienia sie zapytanie.</p>
 */
export default function UsersPage() {
  const { t, i18n } = useTranslation();

  // Parametry wysylane do backendu
  const [fragment, setFragment] = useState('');
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(5);
  const [sortBy, setSortBy] = useState('username');
  const [direction, setDirection] = useState('asc');

  const [strona, setStrona] = useState(null);
  const [ladowanie, setLadowanie] = useState(true);
  const [blad, setBlad] = useState(null);

  /*
   * Odpytujemy backend przy kazdej zmianie parametrow.
   *
   * Wyszukiwanie jest opoznione o 300 ms (debounce) - bez tego kazde
   * nacisniecie klawisza wysylaloby osobne zapytanie i przy szybkim pisaniu
   * odpowiedzi potrafilyby wrocic w zlej kolejnosci.
   */
  useEffect(() => {
    let anulowane = false;
    const licznik = setTimeout(async () => {
      setLadowanie(true);
      setBlad(null);
      try {
        const odpowiedz = await client.get('/users', {
          params: { fragment, page, size, sortBy, direction },
        });
        // Nie ruszamy stanu, jesli w miedzyczasie zmienily sie parametry
        if (!anulowane) {
          setStrona(odpowiedz.data);
        }
      } catch (error) {
        if (!anulowane) {
          const opis = opiszBlad(error);
          setBlad(opis.message ?? (opis.messageKey ? t(opis.messageKey) : null));
        }
      } finally {
        if (!anulowane) {
          setLadowanie(false);
        }
      }
    }, 300);

    return () => {
      anulowane = true;
      clearTimeout(licznik);
    };
  }, [fragment, page, size, sortBy, direction, t]);

  /*
   * Zmiana filtra musi cofac na pierwsza strone. Bez tego przy wejsciu
   * na strone 3 i zawezeniu wyszukiwania do jednego wyniku uzytkownik
   * zobaczylby pusta liste - bo strona 3 wtedy nie istnieje.
   */
  function zmienFiltr(ustawiacz) {
    return (wartosc) => {
      ustawiacz(wartosc);
      setPage(0);
    };
  }

  return (
    <div className="karta">
      <h1>{t('users.title')}</h1>

      <div className="filtry">
        <div className="pole">
          <label htmlFor="fragment">{t('users.search')}</label>
          <input
            id="fragment"
            className="input"
            value={fragment}
            placeholder={t('users.searchPlaceholder')}
            onChange={(e) => zmienFiltr(setFragment)(e.target.value)}
          />
        </div>

        {/* Wymaganie nr 3 - uzytkownik wybiera, ile elementow na stronie */}
        <div className="pole">
          <label htmlFor="size">{t('users.pageSize')}</label>
          <select
            id="size"
            className="input"
            value={size}
            onChange={(e) => zmienFiltr(setSize)(Number(e.target.value))}
          >
            {[5, 10, 20].map((n) => (
              <option key={n} value={n}>
                {n}
              </option>
            ))}
          </select>
        </div>

        {/* Wymaganie nr 5 - sortowanie po stronie backendu */}
        <div className="pole">
          <label htmlFor="sortBy">{t('users.sortBy')}</label>
          <select
            id="sortBy"
            className="input"
            value={sortBy}
            onChange={(e) => zmienFiltr(setSortBy)(e.target.value)}
          >
            <option value="username">{t('users.sortUsername')}</option>
            <option value="email">{t('users.sortEmail')}</option>
            <option value="createdAt">{t('users.sortCreatedAt')}</option>
          </select>
        </div>

        <div className="pole">
          <label htmlFor="direction">{t('users.direction')}</label>
          <select
            id="direction"
            className="input"
            value={direction}
            onChange={(e) => zmienFiltr(setDirection)(e.target.value)}
          >
            <option value="asc">{t('users.asc')}</option>
            <option value="desc">{t('users.desc')}</option>
          </select>
        </div>
      </div>

      {blad && <p className="blad-ogolny" role="alert">{blad}</p>}
      {ladowanie && <p className="info">{t('common.loading')}</p>}

      {!ladowanie && !blad && strona && (
        <>
          {strona.content.length === 0 ? (
            <p className="info">{t('users.empty')}</p>
          ) : (
            <table className="tabela">
              <thead>
                <tr>
                  <th>{t('users.colUsername')}</th>
                  <th>{t('users.colEmail')}</th>
                  <th>{t('users.colCreatedAt')}</th>
                </tr>
              </thead>
              <tbody>
                {strona.content.map((u) => (
                  <tr key={u.id}>
                    <td>{u.username}</td>
                    <td>{u.email}</td>
                    <td>{sformatujDate(u.createdAt, i18n.language)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}

          <div className="stronicowanie">
            <button
              type="button"
              className="przycisk maly"
              onClick={() => setPage((p) => p - 1)}
              disabled={strona.first}
            >
              {t('users.previous')}
            </button>

            <span className="info">
              {t('users.summary', {
                // W API strony liczy sie od zera, uzytkownikowi pokazujemy od jedynki
                page: strona.number + 1,
                totalPages: Math.max(strona.totalPages, 1),
                total: strona.totalElements,
              })}
            </span>

            <button
              type="button"
              className="przycisk maly"
              onClick={() => setPage((p) => p + 1)}
              disabled={strona.last}
            >
              {t('users.next')}
            </button>
          </div>
        </>
      )}
    </div>
  );
}
