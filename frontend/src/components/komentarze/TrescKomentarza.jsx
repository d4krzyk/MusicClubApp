import { Link } from 'react-router-dom';

/** To samo, co serwer uznaje za oznaczenie: @login (3-50 znakow, litery, cyfry, _ . -). */
const OZNACZENIE = /(@[A-Za-z0-9_.-]{3,50})/g;

/** Kropka i myslnik na koncu to znak zdania ("dzieki @ala."), a nie koniec loginu - jak na serwerze. */
const ZNAK_ZDANIA = /[.-]+$/;

/**
 * Tresc komentarza: oznaczenia osob, ktore serwer potwierdzil (lista `oznaczeni`), sa odnosnikami do
 * profilu; reszta - tekstem, tez gdy wyglada jak @login (nikt nie zostal oznaczony, wiec nie ma dokad
 * prowadzic). Zwykly tekst - bez HTML-a - wiec nic z tresci nie trafia do DOM jako znaczniki.
 */
export default function TrescKomentarza({ tresc, oznaczeni = [] }) {
  const znani = new Set(oznaczeni);
  const czesci = tresc.split(OZNACZENIE);
  return (
    <span className="komentarz-tresc">
      {czesci.map((czesc, i) => {
        // parzyste indeksy to tekst miedzy oznaczeniami, nieparzyste - same oznaczenia
        if (i % 2 === 1) {
          const login = czesc.slice(1).replace(ZNAK_ZDANIA, '');
          if (znani.has(login)) {
            return (
              // eslint-disable-next-line react/no-array-index-key
              <span key={i}>
                <Link to={`/profil/${encodeURIComponent(login)}`} className="komentarz-oznaczenie">
                  @{login}
                </Link>
                {czesc.slice(1 + login.length)}
              </span>
            );
          }
        }
        // eslint-disable-next-line react/no-array-index-key
        return <span key={i}>{czesc}</span>;
      })}
    </span>
  );
}
