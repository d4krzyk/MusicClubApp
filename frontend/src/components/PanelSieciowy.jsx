import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import Alert from 'react-bootstrap/Alert';
import Button from 'react-bootstrap/Button';
import Card from 'react-bootstrap/Card';
import Modal from 'react-bootstrap/Modal';
import { describeError } from '../api/client';
import * as moderacja from '../api/moderacja';
import { formatDate } from '../utils/dates';

/**
 * Powiazania sieciowe kont: <b>multikonta i blokada adresu</b>.
 *
 * <p><b>Dlaczego to wyjechalo z {@code UsersPage}.</b> Tamta strona miala
 * 722 linie i trzymala trzy niezalezne rzeczy naraz: tabele kont
 * (stronicowanie, sortowanie, role, kary), okno usuwania konta oraz to -
 * czyli podglad adresow, wykrywanie multikont i liste blokad. Trzynascie
 * {@code useState} w jednym komponencie to nie jest kwestia stylu: zeby
 * poprawic cokolwiek w karach, trzeba bylo przewijac przez kod o adresach IP
 * i odwrotnie.</p>
 *
 * <p>Ten komponent ma <b>wlasny stan i wlasne zapytania</b>. Strona kont nie
 * musi juz o nich nic wiedziec - podaje tylko konto, ktorego powiazania ma
 * pokazac.</p>
 *
 * <p><b>Blokada adresu to poszlaka, nie dowod</b> - i dlatego ostrzezenie
 * stoi NAD danymi, a nie pod nimi. Pod jednym adresem siedzi cala rodzina,
 * akademik albo tysiace klientow operatora komorkowego; zdanie przeczytane
 * po obejrzeniu listy juz na nic sie nie zda.</p>
 *
 * @param target      konto, ktorego powiazania ogladamy ({@code null} = okno zamkniete)
 * @param onClose     zamkniecie okna
 * @param onMessage   komunikat o powodzeniu do pokazania na stronie kont
 * @param onError     komunikat o bledzie
 */
export default function PanelSieciowy({ target, onClose, onMessage, onError }) {
  const { t, i18n } = useTranslation();

  const [addresses, setAddresses] = useState([]);
  const [accounts, setAccounts] = useState([]);
  const [loading, setLoading] = useState(false);

  /** Lista zablokowanych adresow - niezalezna od otwartego okna. */
  const [blocked, setBlocked] = useState([]);

  const loadBlocked = useCallback(async () => {
    try {
      setBlocked(await moderacja.zablokowaneAdresy());
    } catch {
      // Lista blokad to dodatek do panelu - gdy padnie, reszta ma dzialac
      setBlocked([]);
    }
  }, []);

  useEffect(() => {
    loadBlocked();
  }, [loadBlocked]);

  /*
   * Pobieramy DWIE listy: adresy tego konta (do skopiowania w blokade)
   * i konta, ktore ich uzywaja. Osobne zapytania, bo to dwie rozne rzeczy -
   * konto moze miec adresy, z ktorych nikt wiecej sie nie logowal.
   *
   * Ida rownolegle: nie zaleza od siebie, a szeregowo okno czekaloby
   * na sume obu czasow zamiast na dluzszy z nich.
   */
  useEffect(() => {
    if (!target) {
      return;
    }
    let porzucone = false;

    (async () => {
      setLoading(true);
      try {
        const [adresy, konta] = await Promise.all([
          moderacja.adresyKonta(target.id),
          moderacja.powiazaneKonta(target.id),
        ]);
        if (!porzucone) {
          setAddresses(adresy);
          setAccounts(konta);
        }
      } catch {
        if (!porzucone) {
          setAddresses([]);
          setAccounts([]);
        }
      } finally {
        if (!porzucone) {
          setLoading(false);
        }
      }
    })();

    return () => {
      porzucone = true;
    };
  }, [target]);

  /** Blokuje adres. Powod jest obowiazkowy - patrz encja BlockedIp. */
  async function blockAddress(address) {
    const reason = window.prompt(t('users.blockReasonPrompt', { address }));
    if (!reason || !reason.trim()) {
      return;
    }
    try {
      await moderacja.zablokujAdres(address, reason.trim());
      onMessage(t('users.addressBlocked', { address }));
      loadBlocked();
    } catch (problem) {
      onError(describeError(problem).message);
    }
  }

  async function unblockAddress(id) {
    await moderacja.odblokujAdres(id).catch(() => {});
    loadBlocked();
  }

  return (
    <>
      <Modal show={target != null} onHide={onClose} size="lg" centered>
        <Modal.Header closeButton>
          <Modal.Title as="h5">
            {t('users.relatedTitle', { username: target?.username })}
          </Modal.Title>
        </Modal.Header>

        <Modal.Body>
          <Alert variant="warning" className="small">{t('users.relatedWarning')}</Alert>

          {loading && <p className="text-body-secondary">{t('common.loading')}</p>}

          {!loading && (
            <>
              <h6>{t('users.addressesOf', { username: target?.username })}</h6>
              {addresses.length === 0 ? (
                <p className="text-body-secondary small">{t('users.noAddresses')}</p>
              ) : (
                <ul className="list-unstyled small">
                  {addresses.map((entry) => (
                    <li
                      key={entry.address}
                      className="d-flex align-items-center gap-2 mb-1 flex-wrap"
                    >
                      <code>{entry.address}</code>
                      <span className="text-body-secondary">
                        {t('users.loginCount', { count: entry.loginCount })}
                        {' · '}
                        {formatDate(entry.lastSeenAt, i18n.language)}
                      </span>
                      <Button
                        size="sm"
                        variant="outline-danger"
                        onClick={() => blockAddress(entry.address)}
                      >
                        {t('users.blockAddress')}
                      </Button>
                    </li>
                  ))}
                </ul>
              )}

              <h6 className="mt-3">{t('users.otherAccounts')}</h6>
              {accounts.length === 0 ? (
                <p className="text-body-secondary small mb-0">{t('users.noRelated')}</p>
              ) : (
                <ul className="list-unstyled small mb-0">
                  {accounts.map((entry) => (
                    <li key={`${entry.userId}-${entry.address}`} className="mb-1">
                      <Link to={`/profil/${entry.username}`}>{entry.username}</Link>
                      {' — '}
                      <code>{entry.address}</code>
                      {' · '}
                      {t('users.loginCount', { count: entry.loginCount })}
                    </li>
                  ))}
                </ul>
              )}
            </>
          )}
        </Modal.Body>

        <Modal.Footer>
          <Button variant="outline-secondary" onClick={onClose}>
            {t('common.close')}
          </Button>
        </Modal.Footer>
      </Modal>

      {blocked.length > 0 && (
        <Card className="mt-4">
          <Card.Body>
            <h2 className="h6">{t('users.blockedTitle')}</h2>
            <p className="text-body-secondary small">{t('users.blockedHint')}</p>

            <ul className="list-unstyled small mb-0">
              {blocked.map((entry) => (
                <li key={entry.id} className="d-flex align-items-center gap-2 mb-1 flex-wrap">
                  <code>{entry.address}</code>
                  <span className="text-body-secondary">{entry.reason}</span>
                  <span className="text-body-secondary">
                    {t('users.blockedBy', {
                      username: entry.blockedBy,
                      date: formatDate(entry.createdAt, i18n.language),
                    })}
                  </span>
                  <Button
                    size="sm"
                    variant="outline-secondary"
                    onClick={() => unblockAddress(entry.id)}
                  >
                    {t('users.unblock')}
                  </Button>
                </li>
              ))}
            </ul>
          </Card.Body>
        </Card>
      )}
    </>
  );
}
