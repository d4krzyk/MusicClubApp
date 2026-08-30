import { useCallback, useRef } from 'react';
import { licznikiReakcji } from '../api/posty';
import useOdswiezanie, { DOMYSLNY_ODSTEP_MS } from './useOdswiezanie';

/** Odswieza liczniki reakcji pod postami, ktore uzytkownik ma na ekranie. */
export default function useLiveReactions(posts, onCounts, odstepMs = DOMYSLNY_ODSTEP_MS) {
  /*
   * Lista postow zmienia sie przy kazdej reakcji, a nie chcemy z tego powodu budowac zapytania od
   * nowa.
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
