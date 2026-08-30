import { useCallback, useRef } from 'react';
import { licznikiReakcji } from '../api/posty';
import useOdswiezanie, { DOMYSLNY_ODSTEP_MS } from './useOdswiezanie';

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
 * <p><b>Kiedy odpytujemy</b> - a wiec i to, ze zminimalizowane okno nie
 * wysyla niczego - ustala {@link useOdswiezanie}. Tutaj zostaje samo
 * pytanie: ktore posty i co z odpowiedzia zrobic.</p>
 *
 * @param posts     lista postow na ekranie (potrzebne sa z niej same numery)
 * @param onCounts  wolane ze slownikiem {@code {postId: podsumowanie}}
 * @param odstepMs  co ile odswiezac przy widocznej karcie
 */
export default function useLiveReactions(posts, onCounts, odstepMs = DOMYSLNY_ODSTEP_MS) {
  /*
   * Lista postow zmienia sie przy kazdej reakcji, a nie chcemy z tego powodu
   * budowac zapytania od nowa. Dlatego biezaca liste trzymamy w referencji.
   */
  const postsRef = useRef(posts);
  postsRef.current = posts;

  const refresh = useCallback(async () => {
    const ids = postsRef.current.map((post) => post.id);
    if (ids.length === 0) {
      return;
    }

    try {
      onCounts(await licznikiReakcji(ids));
    } catch {
      // Odswiezenie licznikow to dodatek - gdy sie nie uda, na ekranie
      // zostaja poprzednie wartosci i nikomu nic sie nie psuje
    }
  }, [onCounts]);

  useOdswiezanie(refresh, odstepMs);

  return refresh;
}
