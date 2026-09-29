import { useEffect, useState } from 'react';
import client from '../api/client';

/**
 * Co frontend musi wiedziec o serwerze przed zalogowaniem - np. czy dziala
 * poczta (bez niej "Nie pamietasz hasla?" nie mialoby jak wyslac linku).
 *
 * Pytamy raz na otwarcie aplikacji: obietnica jest wspolna dla wszystkich
 * komponentow, wiec kilka stron naraz nie wysle kilku takich samych zapytan.
 */
let wspolne = null;

function pobierz() {
  if (!wspolne) {
    wspolne = client.get('/public/info')
      .then(({ data }) => data)
      .catch(() => {
        // Nie wiemy - lepiej nie pokazywac funkcji, ktora moze nie dzialac.
        // Nastepne wywolanie sprobuje jeszcze raz.
        wspolne = null;
        return { mailEnabled: false };
      });
  }
  return wspolne;
}

export default function useInfoSerwera() {
  const [info, setInfo] = useState(null);

  useEffect(() => {
    let aktualne = true;
    pobierz().then((dane) => {
      if (aktualne) {
        setInfo(dane);
      }
    });
    return () => {
      aktualne = false;
    };
  }, []);

  return info;
}
