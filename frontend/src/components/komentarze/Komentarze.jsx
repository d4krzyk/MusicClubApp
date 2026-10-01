import { useCallback, useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import Alert from 'react-bootstrap/Alert';
import { describeError } from '../../api/client';
import * as komentarze from '../../api/komentarze';
import useOdswiezanie from '../../hooks/useOdswiezanie';
import DoladujWiecej from '../DoladujWiecej';
import Komentarz from './Komentarz';
import KomentarzForm from './KomentarzForm';

const ROZMIAR_STRONY = 10;
const ROZMIAR_ODPOWIEDZI = 20;

/** Co ile sekund dociagamy nowe komentarze, gdy sekcja jest otwarta i karta na wierzchu. */
const ODSWIEZANIE_MS = 20_000;

/**
 * Komentarze pod jednym postem: pole na gorze (nowy komentarz ma sie pojawic tuz pod nim), pod nim
 * komentarze od najnowszych, a odpowiedzi - od najstarszej, po rozwinieciu. `onLiczba` dostaje wypadkowa
 * liczbe komentarzy (do licznika w poscie), `fokus` to komentarz, na ktory prowadzi powiadomienie.
 */
export default function Komentarze({ idPosta, onLiczba = null, fokus = null }) {
  const { t } = useTranslation();
  const [lista, setLista] = useState([]);
  const [strona, setStrona] = useState(null);
  const [ladowanie, setLadowanie] = useState(true);
  const [doladowanie, setDoladowanie] = useState(false);
  const [blad, setBlad] = useState(null);
  const [watki, setWatki] = useState({});
  const [przypiety, setPrzypiety] = useState(null);
  /* Wszystkie komentarze pod postem, z odpowiedziami - to idzie do licznika; strona.totalElements to same nadrzedne. */
  const [liczba, setLiczba] = useState(null);
  const ostatnie = useRef(0);

  function zmienLiczbe(roznica) {
    const nowa = Math.max(0, (liczba ?? 0) + roznica);
    setLiczba(nowa);
    onLiczba?.(nowa);
  }

  const wczytaj = useCallback(async (numerStrony, { cicho = false } = {}) => {
    const numer = ++ostatnie.current;
    if (!cicho) {
      if (numerStrony === 0) {
        setLadowanie(true);
      } else {
        setDoladowanie(true);
      }
    }
    try {
      const dane = await komentarze.lista(idPosta, { strona: numerStrony, rozmiar: ROZMIAR_STRONY });
      if (numer !== ostatnie.current) {
        return;
      }
      setBlad(null);
      /* Ciche odswiezenie pierwszej strony nie cofa przewiniecia: zmienia sie tylko suma, nie "gdzie jestem" */
      setStrona((s) => (cicho && s ? { ...s, totalElements: dane.totalElements } : dane));
      setLista((obecne) => {
        if (numerStrony > 0) {
          const znane = new Set(obecne.map((k) => k.id));
          return [...obecne, ...dane.content.filter((k) => !znane.has(k.id))];
        }
        // pierwsza strona: nowe i zmienione na gore, a to, co dociagnieto nizej, zostaje
        const nowe = new Set(dane.content.map((k) => k.id));
        return [...dane.content, ...obecne.filter((k) => !nowe.has(k.id))];
      });
      setLiczba(dane.totalComments);
      onLiczba?.(dane.totalComments);
    } catch (problem) {
      if (numer === ostatnie.current && !cicho) {
        setBlad(describeError(problem).message);
      }
    } finally {
      if (numer === ostatnie.current) {
        setLadowanie(false);
        setDoladowanie(false);
      }
    }
    // onLiczba zmienia sie przy kazdym renderze rodzica - nie ma byc powodem do ponownego wczytania
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [idPosta]);

  useEffect(() => { wczytaj(0); }, [wczytaj]);
  useOdswiezanie(() => wczytaj(0, { cicho: true }), ODSWIEZANIE_MS, true);

  /* Komentarz z powiadomienia: dociagamy go razem z jego watkiem, nawet gdy jest daleko na liscie. */
  useEffect(() => {
    if (!fokus) {
      return undefined;
    }
    let aktualne = true;
    (async () => {
      try {
        const k = await komentarze.jeden(fokus);
        const korzen = k.parentId ? await komentarze.jeden(k.parentId) : k;
        const odp = korzen.replyCount > 0
          ? await komentarze.odpowiedzi(korzen.id, { strona: 0, rozmiar: ROZMIAR_ODPOWIEDZI })
          : { content: [] };
        if (aktualne) {
          setPrzypiety({ korzen, odpowiedzi: odp.content, fokus: Number(fokus) });
        }
      } catch {
        // komentarza juz nie ma albo nie mamy do niego dostepu - zostaje zwykla lista
      }
    })();
    return () => { aktualne = false; };
  }, [fokus]);

  useEffect(() => {
    if (przypiety) {
      document.getElementById(`komentarz-${przypiety.fokus}`)?.scrollIntoView({ block: 'center' });
    }
  }, [przypiety]);

  async function pokazOdpowiedzi(korzen, numerStrony = 0) {
    setWatki((w) => ({ ...w, [korzen.id]: { ...(w[korzen.id] ?? {}), ladowanie: true } }));
    try {
      const dane = await komentarze.odpowiedzi(korzen.id, { strona: numerStrony, rozmiar: ROZMIAR_ODPOWIEDZI });
      setWatki((w) => {
        const stare = numerStrony > 0 ? (w[korzen.id]?.lista ?? []) : [];
        const znane = new Set(stare.map((k) => k.id));
        return {
          ...w,
          [korzen.id]: {
            lista: [...stare, ...dane.content.filter((k) => !znane.has(k.id))],
            wiecej: !dane.last, strona: numerStrony, ladowanie: false,
          },
        };
      });
    } catch (problem) {
      setWatki((w) => ({ ...w, [korzen.id]: { ...(w[korzen.id] ?? {}), ladowanie: false } }));
      setBlad(describeError(problem).message);
    }
  }

  function dodano(nowy) {
    setLista((obecne) => [nowy, ...obecne]);
    setStrona((s) => (s ? { ...s, totalElements: s.totalElements + 1 } : s));
    zmienLiczbe(1);
  }

  /* Odpowiedz dodana: pod komentarzem nadrzednym (rozwinietym albo nie), z aktualnym licznikiem. */
  function odpowiedzDodana(idKorzenia, nowa) {
    const zwieksz = (k) => (k.id === idKorzenia ? { ...k, replyCount: k.replyCount + 1 } : k);
    setLista((obecne) => obecne.map(zwieksz));
    setPrzypiety((p) => (p && p.korzen.id === idKorzenia
      ? { ...p, korzen: zwieksz(p.korzen), odpowiedzi: [...p.odpowiedzi, nowa] } : p));
    setWatki((w) => ({
      ...w,
      [idKorzenia]: { lista: [...(w[idKorzenia]?.lista ?? []), nowa], wiecej: false, ladowanie: false },
    }));
    zmienLiczbe(1);
  }

  async function usun(k) {
    if (!window.confirm(t('comments.confirmDelete'))) {
      return;
    }
    try {
      await komentarze.usun(k.id);
    } catch (problem) {
      setBlad(describeError(problem).message);
      return;
    }
    const razem = 1 + (k.parentId ? 0 : k.replyCount);
    if (k.parentId) {
      const odejmij = (kom) => (kom.id === k.parentId ? { ...kom, replyCount: Math.max(0, kom.replyCount - 1) } : kom);
      setLista((obecne) => obecne.map(odejmij));
      setPrzypiety((p) => (p ? { ...p, korzen: odejmij(p.korzen), odpowiedzi: p.odpowiedzi.filter((o) => o.id !== k.id) } : p));
      setWatki((w) => (w[k.parentId]
        ? { ...w, [k.parentId]: { ...w[k.parentId], lista: w[k.parentId].lista.filter((o) => o.id !== k.id) } } : w));
    } else {
      setLista((obecne) => obecne.filter((kom) => kom.id !== k.id));
      setPrzypiety((p) => (p && p.korzen.id === k.id ? null : p));
    }
    if (!k.parentId) {
      setStrona((s) => (s ? { ...s, totalElements: Math.max(0, s.totalElements - 1) } : s));
    }
    zmienLiczbe(-razem);
  }

  const wspolne = {
    idPosta,
    onUsun: usun,
    onOdpowiedzDodana: odpowiedzDodana,
    onPokazOdpowiedzi: (k) => pokazOdpowiedzi(k, 0),
    onWiecejOdpowiedzi: (k) => pokazOdpowiedzi(k, (watki[k.id]?.strona ?? 0) + 1),
  };
  const bezPrzypietego = przypiety ? lista.filter((k) => k.id !== przypiety.korzen.id) : lista;

  return (
    <section className="komentarze" aria-label={t('comments.title')} id={`komentarze-${idPosta}`}>
      <KomentarzForm idPosta={idPosta} onDodano={dodano} />

      {blad && <Alert variant="danger" className="py-2 small mt-2 mb-0">{blad}</Alert>}

      {przypiety && (
        <ul className="komentarze-lista list-unstyled komentarze-przypiety">
          <Komentarz
            komentarz={przypiety.korzen}
            watek={{ lista: przypiety.odpowiedzi, wiecej: false, ladowanie: false }}
            podswietlony={przypiety.fokus === przypiety.korzen.id}
            {...wspolne}
            onPokazOdpowiedzi={() => {}}
          />
        </ul>
      )}

      {ladowanie && <p className="small text-body-secondary mt-2 mb-0">{t('common.loading')}</p>}

      {!ladowanie && bezPrzypietego.length === 0 && !przypiety && !blad && (
        <p className="small text-body-secondary mt-2 mb-0">{t('comments.empty')}</p>
      )}

      {bezPrzypietego.length > 0 && (
        <ul className="komentarze-lista list-unstyled">
          {bezPrzypietego.map((k) => (
            <Komentarz key={k.id} komentarz={k} watek={watki[k.id]} {...wspolne} />
          ))}
        </ul>
      )}

      {strona && strona.totalElements > 0 && (
        <DoladujWiecej
          etykieta={t('comments.older')}
          pokazano={lista.length}
          wszystkich={strona.totalElements}
          ostatnia={strona.last || lista.length >= strona.totalElements}
          ladowanie={doladowanie}
          rozmiar="sm"
          onClick={() => wczytaj((strona.number ?? 0) + 1)}
        />
      )}
    </section>
  );
}
