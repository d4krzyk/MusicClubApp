import { useCallback, useEffect, useRef } from 'react';
import client from '../api/client';

/** Co ile odswiezamy liczniki, gdy karta jest na wierzchu. */
const DEFAULT_INTERVAL_MS = 45_000;

/**
 * Odswieza liczniki reakcji pod postami, ktore uzytkownik ma na ekranie.
 *
 * <p><b>Dlaczego nie WebSocket.</b> Prawdziwie "na zywo" wymagaloby stalego
 * polaczenia, a po stronie serwera - rozglaszania zdarzen do wszystkich
 * zainteresowanych. To spory kawalek maszynerii, ktory trzeba potem utrzymac,
 * a chodzi o kilka liczb pod postami. Jedno zapytanie na cala widoczna liste
 * daje ten sam efekt przy zerowym koszcie stalym.</p>
 *
 * <p><b>Dlaczego nie pobrac po prostu tablicy od nowa.</b> Bo przestawiloby
 * widok: doszlyby nowe posty, kolejnosc by sie zmienila i uzytkownik
 * stracilby miejsce, w ktorym czytal. Tutaj podmieniamy WYLACZNIE liczniki
 * przy postach, ktore juz sa na ekranie.</p>
 *
 * <p><b>Odswiezamy tylko wtedy, gdy karta jest widoczna.</b> Zapytania
 * wysylane do zminimalizowanego okna nikomu niczego nie pokazuja, a zuzywaja
 * baterie i lacze. Dlatego zegar chodzi wylacznie przy widocznej karcie,
 * a powrot do niej odswieza liczniki od razu - to najczestszy moment,
 * w ktorym cos zdazylo sie zmienic.</p>
 *
 * @param posts     lista postow na ekranie (potrzebne sa z niej same numery)
 * @param onCounts  wolane ze slownikiem {@code {postId: podsumowanie}};
 *                  MUSI byc stabilne (useCallback), inaczej zegar
 *                  przestawialby sie przy kazdym renderze
 * @param intervalMs co ile odswiezac przy widocznej karcie
 */
export default function useLiveReactions(posts, onCounts, intervalMs = DEFAULT_INTERVAL_MS) {
  /*
   * Lista postow zmienia sie przy kazdej reakcji, a nie chcemy z tego powodu
   * zdejmowac i zakladac nasluchow od nowa. Dlatego biezaca liste trzymamy
   * w referencji, a efekt nizej nie zalezy od niej.
   */
  const postsRef = useRef(posts);
  postsRef.current = posts;

  const refresh = useCallback(async () => {
    const ids = postsRef.current.map((post) => post.id);
    if (ids.length === 0) {
      return;
    }

    try {
      const { data } = await client.get('/posts/reactions', {
        params: { ids: ids.join(',') },
      });
      onCounts(data);
    } catch {
      // Odswiezenie licznikow to dodatek - gdy sie nie uda, na ekranie
      // zostaja poprzednie wartosci i nikomu nic sie nie psuje
    }
  }, [onCounts]);

  useEffect(() => {
    let timer = null;

    function stop() {
      if (timer !== null) {
        clearInterval(timer);
        timer = null;
      }
    }

    function start() {
      stop();
      timer = setInterval(refresh, intervalMs);
    }

    function onVisibility() {
      if (document.visibilityState === 'visible') {
        refresh();
        start();
      } else {
        stop();
      }
    }

    if (document.visibilityState === 'visible') {
      start();
    }

    document.addEventListener('visibilitychange', onVisibility);
    window.addEventListener('focus', refresh);

    return () => {
      stop();
      document.removeEventListener('visibilitychange', onVisibility);
      window.removeEventListener('focus', refresh);
    };
  }, [refresh, intervalMs]);

  return refresh;
}
