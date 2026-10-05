import { useCallback, useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useLocation, useNavigate } from 'react-router-dom';
import Button from 'react-bootstrap/Button';
import { useAuth } from '../../auth/AuthContext';
import { describeError } from '../../api/client';
import * as ekipy from '../../api/ekipy';
import { IconFriends, IconPlus } from '../Icons';
import EkipaForm from './EkipaForm';
import KartaEkipy from './KartaEkipy';

/**
 * "Ekipy" na stronie wydarzenia - serce aplikacji: z kim jade na ten koncert. Lista ekip (moja pierwsza, potem z mojej
 * okolicy i z wolnymi miejscami), "Zaloz ekipe" i dolaczanie. Po zalozeniu albo dolaczeniu - przejscie do ekipy.
 */
export default function EkipyWydarzenia({ w, onUdzial }) {
  const { t } = useTranslation();
  const { user } = useAuth();
  const navigate = useNavigate();
  const { hash } = useLocation();
  const sekcja = useRef(null);
  const [lista, setLista] = useState(null);
  const [blad, setBlad] = useState(null);
  const [nowa, setNowa] = useState(false);

  const wczytaj = useCallback(() => {
    ekipy.ekipyWydarzenia(w.id).then(setLista).catch((p) => setBlad(describeError(p).message));
  }, [w.id]);

  useEffect(() => { wczytaj(); }, [wczytaj]);

  // Z powiadomienia "nie jestes juz w ekipie" (/wydarzenia/:id#ekipy) - od razu do listy ekip
  const przewiniete = useRef(false);
  useEffect(() => {
    if (lista && hash === '#ekipy' && !przewiniete.current) {
      przewiniete.current = true;
      sekcja.current?.scrollIntoView({ block: 'start' });
    }
  }, [lista, hash]);

  const otwarte = !w.past && !w.withdrawn;
  const wEkipie = (lista ?? []).some((k) => k.myState === 'FOUNDER' || k.myState === 'MEMBER');

  async function zaloz(formularz) {
    const e = await ekipy.zaloz(w.id, formularz);
    onUdzial?.();
    navigate(`/ekipy/${e.crew.id}`);
  }

  function podmien(karta) {
    setLista((l) => l.map((k) => (k.id === karta.id ? karta : k)));
    if (karta.myState === 'MEMBER') {
      onUdzial?.();
      navigate(`/ekipy/${karta.id}`);
    }
  }

  return (
    <section id="ekipy" ref={sekcja} className="mb-4 ekipy-wydarzenia" aria-labelledby="ekipy-tytul">
      <div className="ekipy-glowa">
        <h2 id="ekipy-tytul" className="h6 wydarzenie-sekcja mb-0"><IconFriends size={15} /> {t('crews.title')}</h2>
        {otwarte && !wEkipie && !nowa && lista && (
          <Button size="sm" variant="primary" onClick={() => setNowa(true)}><IconPlus size={12} /> {t('crews.create')}</Button>
        )}
      </div>
      <p className="small text-body-secondary mb-2">{t('crews.lead')}</p>

      {nowa && (
        <div className="ekipa-nowa mc-wejscie">
          <EkipaForm miastoDomyslne={user?.city ?? ''} onZapisz={zaloz} onAnuluj={() => setNowa(false)} idPrefix="nowa-ekipa" />
        </div>
      )}

      {blad && <div className="small text-danger" role="alert">{blad}</div>}
      {lista === null && !blad && <div className="ekipa-szkielet" aria-busy="true" aria-label={t('common.loading')} />}
      {lista && lista.length === 0 && !nowa && (
        <div className="ekipy-pusto">
          <p className="mb-2">{otwarte ? t('crews.empty') : t('crews.emptyPast')}</p>
          {otwarte && <Button size="sm" variant="outline-primary" onClick={() => setNowa(true)}>{t('crews.createFirst')}</Button>}
        </div>
      )}
      {lista && lista.length > 0 && (
        <ul className="ekipy-lista list-unstyled">
          {lista.map((k, i) => <KartaEkipy key={k.id} k={k} i={i} onZmiana={podmien} />)}
        </ul>
      )}
    </section>
  );
}
