import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import Button from 'react-bootstrap/Button';
import Modal from 'react-bootstrap/Modal';
import { describeError } from '../api/client';
import { odblokuj, zablokuj } from '../api/prywatnosc';
import { IconBan } from './Icons';

/**
 * Zablokuj / odblokuj na cudzym profilu.
 *
 * Blokada ma powazne skutki (znika znajomosc, znikaja posty w obie strony),
 * wiec przed nia okno z wyjasnieniem. Odblokowanie jest nieszkodliwe - bez okna.
 */
export default function BlockButton({ username, blocked, onChange }) {
  const { t } = useTranslation();
  const [pytanie, setPytanie] = useState(false);
  const [wysylanie, setWysylanie] = useState(false);
  const [blad, setBlad] = useState(null);

  async function wykonaj(akcja) {
    setWysylanie(true);
    setBlad(null);
    try {
      await akcja(username);
      setPytanie(false);
      onChange?.();
    } catch (problem) {
      setBlad(describeError(problem).message);
    } finally {
      setWysylanie(false);
    }
  }

  if (blocked) {
    return (
      <Button variant="outline-secondary" size="sm" disabled={wysylanie} onClick={() => wykonaj(odblokuj)}>
        {t('block.unblock')}
      </Button>
    );
  }

  return (
    <>
      <Button
        variant="link"
        size="sm"
        className="text-body-secondary text-decoration-none p-0"
        onClick={() => setPytanie(true)}
      >
        <IconBan size={13} className="me-1" />
        {t('block.block')}
      </Button>

      <Modal show={pytanie} onHide={() => setPytanie(false)} centered>
        <Modal.Header closeButton>
          <Modal.Title as="h2" className="h5">{t('block.confirmTitle', { username })}</Modal.Title>
        </Modal.Header>
        <Modal.Body>
          <ul className="small mb-2 ps-3">
            <li>{t('block.effectFriends')}</li>
            <li>{t('block.effectContent')}</li>
            <li>{t('block.effectInvite')}</li>
            <li>{t('block.effectSilent', { username })}</li>
          </ul>
          <p className="small text-body-secondary mb-0">{t('block.effectUndo')}</p>
          {blad && <div className="text-danger small mt-2">{blad}</div>}
        </Modal.Body>
        <Modal.Footer>
          <Button variant="outline-secondary" onClick={() => setPytanie(false)}>{t('common.cancel')}</Button>
          <Button variant="danger" disabled={wysylanie} onClick={() => wykonaj(zablokuj)}>
            {t('block.confirm')}
          </Button>
        </Modal.Footer>
      </Modal>
    </>
  );
}
