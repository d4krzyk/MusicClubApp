import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import Form from 'react-bootstrap/Form';
import Spinner from 'react-bootstrap/Spinner';
import { szukaj } from '../api/muzyka';

/**
 * Wyszukiwarka katalogu Deezera - do dodawania ulubionych artystow i utworow.
 *
 * <p><b>Wybor z listy zamiast wpisywania z reki.</b> Gdyby uzytkownik wpisywal
 * nazwe sam, w bazie wyladowaloby "Radiohead", "radiohead" i "Radiohed" jako
 * trzy rozne byty - a wtedy dopasowywanie ludzi po wspolnych artystach
 * przestaloby dzialac. Do tego kazdy moglby wpisac wykonawce, ktory nie
 * istnieje. Tutaj mozna tylko kliknac w cos, co Deezer naprawde ma.</p>
 *
 * <p><b>Odczekujemy z zapytaniem (debounce).</b> Bez tego kazde nacisniecie
 * klawisza wysylaloby osobne zapytanie - wpisanie "radiohead" to nie jedno
 * pytanie, tylko dziewiec. Odliczamy 350 ms od ostatniego znaku: tyle, zeby
 * nie gonic za pisaniem, i na tyle malo, zeby nie bylo czuc opoznienia.</p>
 *
 * <p><b>Wyniki starsze niz ostatnie zapytanie sa odrzucane.</b> Odpowiedzi
 * potrafia wrocic w innej kolejnosci niz zostaly wyslane, wiec bez tego
 * na liscie mogloby wyladowac cos, czego uzytkownik juz nie szuka.</p>
 *
 * @param rodzaj   'artists' albo 'tracks' - decyduje, ktory endpoint pytamy
 * @param onWybor  wywolywane z wybrana pozycja
 * @param wylaczone gdy true, pole jest zablokowane (np. osiagnieto limit)
 */
export default function CatalogSearch({ kind, onWybor, disabled = false }) {
  const { t } = useTranslation();

  const [phrase, setPhrase] = useState('');
  const [results, setResults] = useState([]);
  const [searching, setSearching] = useState(false);
  const [searched, setSearched] = useState(false);

  /* Numer ostatniego wyslanego zapytania - patrz opis komponentu. */
  const requestNumber = useRef(0);

  useEffect(() => {
    const searchPhrase = phrase.trim();

    if (searchPhrase.length < 2) {
      // Jedna litera pasuje do wszystkiego - takie zapytanie tylko obciaza
      setResults([]);
      setSearched(false);
      return undefined;
    }

    setSearching(true);
    const counter = ++requestNumber.current;

    const timer = setTimeout(async () => {
      try {
        const wyniki = await szukaj(kind, searchPhrase);
        // Odpowiedz na juz nieaktualne zapytanie - ignorujemy
        if (counter === requestNumber.current) {
          setResults(wyniki);
          setSearched(true);
        }
      } catch {
        // Katalog jest dodatkiem: gdy Deezer nie odpowie, pokazujemy
        // "brak wynikow" zamiast wywracac cala strone profilu
        if (counter === requestNumber.current) {
          setResults([]);
          setSearched(true);
        }
      } finally {
        if (counter === requestNumber.current) {
          setSearching(false);
        }
      }
    }, 350);

    // Nowy znak przed uplywem 350 ms kasuje poprzednie odliczanie
    return () => clearTimeout(timer);
  }, [phrase, kind]);

  function choose(item) {
    onWybor(item);
    // Czyscimy pole - inaczej lista podpowiedzi zostaje otwarta nad
    // wlasnie dodana pozycja i wyglada, jakby nic sie nie stalo
    setPhrase('');
    setResults([]);
    setSearched(false);
  }

  return (
    <div className="position-relative-fix">
      <Form.Control
        type="search"
        value={phrase}
        onChange={(e) => setPhrase(e.target.value)}
        disabled={disabled}
        placeholder={t(`favorites.search.${kind}`)}
        aria-label={t(`favorites.search.${kind}`)}
      />

      {searching && (
        <div className="text-body-secondary small mt-1">
          <Spinner animation="border" size="sm" className="me-2" />
          {t('common.loading')}
        </div>
      )}

      {!searching && searched && results.length === 0 && (
        <div className="text-body-secondary small mt-1">{t('favorites.noResults')}</div>
      )}

      {results.length > 0 && (
        <ul className="suggestion-list list-unstyled mt-1 mb-0">
          {results.map((item) => (
            <li key={item.externalId}>
              <button
                type="button"
                className="suggestion-item w-100 text-start d-flex align-items-center gap-2"
                onClick={() => choose(item)}
              >
                {item.imageUrl && (
                  <img
                    src={item.imageUrl}
                    alt=""
                    className="catalog-thumb rounded"
                    onError={(e) => { e.currentTarget.style.visibility = 'hidden'; }}
                  />
                )}
                <span className="flex-grow-1 text-truncate">
                  {item.name ?? item.title}
                  {item.artistName && (
                    <span className="text-body-secondary"> · {item.artistName}</span>
                  )}
                </span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
