import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import Modal from 'react-bootstrap/Modal';
import { IconCamera, IconFriends, IconLock } from '../Icons';

/**
 * Karta na profilu: zdjecia z galerii (klikniecie powieksza), "o mnie", "szukam" i pytania muzyczne. Serwer
 * oddaje ja tylko przy pelnym widoku profilu i wedlug ustawienia karty (wszyscy / znajomi / tylko Poznawaj).
 * Pusta karta na wlasnym profilu zacheca do uzupelnienia. Wlasna karta, ktorej inni na profilu nie widza (albo
 * widza tylko znajomi), ma o tym podpis - inaczej latwo zapomniec, ze ktos jej tu nie zobaczy.
 */
export default function KartaNaProfilu({ karta, wlasna = false, login }) {
  const { t } = useTranslation();
  const [duze, setDuze] = useState(null);

  if (!karta) {
    return null;
  }
  const pusta = !karta.bio && karta.lookingFor.length === 0 && karta.prompts.length === 0 && karta.photos.length === 0;
  if (pusta) {
    return wlasna ? (
      <Link to="/settings#karta" className="profil-karta-zacheta mb-4">
        <IconCamera size={20} />
        <span>{t('card.emptyOwn')}</span>
      </Link>
    ) : null;
  }

  const ograniczona = wlasna && karta.visibility && karta.visibility !== 'EVERYONE';

  return (
    <section className="profil-panel profil-karta mb-4" aria-label={t('card.onProfile', { username: login })}>
      {ograniczona && (
        <p className="profil-karta-widocznosc">
          {karta.visibility === 'FRIENDS' ? <IconFriends size={14} /> : <IconLock size={14} />}
          <span>
            {t(`card.visibility.ownNote.${karta.visibility}`)}
            {' '}
            <Link to="/settings#karta">{t('card.visibility.change')}</Link>
          </span>
        </p>
      )}
      {karta.photos.length > 0 && (
        <ul className="profil-karta-zdjecia list-unstyled">
          {karta.photos.map((p, i) => (
            <li key={p.id}>
              <button type="button" onClick={() => setDuze(p.url)}
                aria-label={t('card.enlarge', { n: i + 1, count: karta.photos.length })}>
                <img src={p.url} alt="" loading="lazy" />
              </button>
            </li>
          ))}
        </ul>
      )}

      {karta.bio && <p className="profil-karta-bio">{karta.bio}</p>}

      {karta.lookingFor.length > 0 && (
        <div className="pz-szukam mb-3">
          <span className="pz-naglowek me-1">{t('card.lookingFor')}</span>
          {karta.lookingFor.map((l) => <span key={l} className="pz-szukam-chip">{t(`card.looking.${l}`)}</span>)}
        </div>
      )}

      {karta.prompts.length > 0 && (
        <div className="profil-karta-pytania">
          {karta.prompts.map((p) => (
            <div key={p.prompt} className="pz-pytanie">
              <h3 className="pz-pytanie-tresc">{t(`card.prompt.${p.prompt}`)}</h3>
              <p className="pz-odpowiedz mb-0">{p.answer}</p>
            </div>
          ))}
        </div>
      )}

      {wlasna && <Link to="/settings#karta" className="small">{t('card.edit')}</Link>}

      <Modal show={Boolean(duze)} onHide={() => setDuze(null)} centered size="lg">
        <Modal.Header closeButton />
        <Modal.Body className="text-center">
          {duze && <img src={duze} alt={login} className="img-fluid rounded" />}
        </Modal.Body>
      </Modal>
    </section>
  );
}
