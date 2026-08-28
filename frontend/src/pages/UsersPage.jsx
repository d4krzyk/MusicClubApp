import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import Card from 'react-bootstrap/Card';
import Form from 'react-bootstrap/Form';
import Table from 'react-bootstrap/Table';
import Button from 'react-bootstrap/Button';
import Alert from 'react-bootstrap/Alert';
import Row from 'react-bootstrap/Row';
import Col from 'react-bootstrap/Col';
import Spinner from 'react-bootstrap/Spinner';
import Modal from 'react-bootstrap/Modal';
import client, { describeError } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { IconBan } from '../components/Icons';
import { formatDate, isForever } from '../utils/dates';

/**
 * Wartosc pozycji "na zawsze" na liscie kar.
 *
 * <p>Tekst, a nie liczba - bo to nie jest skrajnie duza liczba godzin, tylko
 * inny rodzaj decyzji. Umowna liczba (np. 999999) predzej czy pozniej
 * trafilaby do walidacji godzin i zostala odrzucona jako "poza zakresem".</p>
 */
const FOREVER = 'forever';

/**
 * Panel administratora: lista kont ze stronicowaniem, sortowaniem
 * i zmiana rol.
 *
 * <p>Widoczne sterowanie stronicowaniem i sortowaniem pokazuje dzialanie
 * wymagan nr 3 i 5 - cala praca dzieje sie po stronie backendu, tutaj tylko
 * wysylamy parametry i rysujemy to, co przyszlo.</p>
 */
export default function UsersPage() {
  const { t, i18n } = useTranslation();
  const { user: loggedIn } = useAuth();

  // Parametry wysylane do backendu
  const [fragment, setFragment] = useState('');
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(5);
  const [sortBy, setSortBy] = useState('username');
  const [direction, setDirection] = useState('asc');

  const [pageData, setPageData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [message, setMessage] = useState(null);
  const [refresh, setRefresh] = useState(0);

  /*
   * Konto wybrane do usuniecia. Trzymamy caly obiekt, a nie samo id, zeby
   * w oknie z pytaniem pokazac login - "czy na pewno usunac?" bez nazwy
   * konta to zaproszenie do pomylki.
   */
  const [toDelete, setToDelete] = useState(null);

  /** Okienko "powiazane konta" - null, gdy zamkniete. */
  const [related, setRelated] = useState(null);

  /** Lista zablokowanych adresow, pobierana raz i po kazdej zmianie. */
  const [blocked, setBlocked] = useState([]);

  /*
   * Odpytujemy backend przy kazdej zmianie parametrow.
   *
   * Wyszukiwanie jest opoznione o 300 ms (debounce) - bez tego kazde
   * nacisniecie klawisza wysylaloby osobne zapytanie i przy szybkim pisaniu
   * odpowiedzi potrafilyby wrocic w zlej kolejnosci.
   */
  useEffect(() => {
    let cancelled = false;
    const counter = setTimeout(async () => {
      setLoading(true);
      setError(null);
      try {
        const response = await client.get('/users', {
          params: { fragment, page, size, sortBy, direction },
        });
        if (!cancelled) {
          setPageData(response.data);
        }
      } catch (error) {
        if (!cancelled) {
          const details = describeError(error);
          setError(details.message ?? (details.messageKey ? t(details.messageKey) : null));
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    }, 300);

    return () => {
      cancelled = true;
      clearTimeout(counter);
    };
  }, [fragment, page, size, sortBy, direction, refresh, t]);

  /*
   * Zmiana filtra musi cofac na pierwsza strone. Bez tego przy wejsciu
   * na strone 3 i zawezeniu wyszukiwania do jednego wyniku uzytkownik
   * zobaczylby pusta liste - bo strona 3 wtedy nie istnieje.
   */
  function changeFilter(setter) {
    return (value) => {
      setter(value);
      setPage(0);
    };
  }

  /*
   * Do wyboru gotowe okresy, a nie pole na dowolna liczbe godzin. Moderacja
   * to decyzja podejmowana w pospiechu - lista "godzina / dzien / tydzien /
   * miesiac" prowadzi do niej szybciej i nie da sie w niej przypadkiem
   * wpisac 8760 zamiast 24.
   */
  const BAN_OPTIONS = [1, 24, 168, 720];

  /**
   * Wartosc z listy -> tresc zapytania do serwera.
   *
   * <p>Lista oddaje tekst, bo ma trzy rodzaje pozycji, a nie same liczby:
   * pusta ("zdejmij"), {@code 'forever'} ("na zawsze") i liczba godzin.
   * Zamiana na {@code {hours, forever}} siedzi tutaj, w jednym miejscu -
   * obie kary wysylaja dokladnie to samo i nie da sie ich rozjechac.</p>
   */
  function banPayload(value) {
    if (value === FOREVER) {
      return { hours: null, forever: true };
    }
    return { hours: value === '' ? null : Number(value), forever: false };
  }

  async function setPostingBan(id, value) {
    setError(null);
    setMessage(null);
    const payload = banPayload(value);
    try {
      await client.patch(`/users/${id}/posting-ban`, payload);
      setMessage(t(payload.hours == null && !payload.forever
        ? 'users.banLifted'
        : 'users.banSet'));
      setRefresh((n) => n + 1);
    } catch (error) {
      const details = describeError(error);
      setError(details.message ?? (details.messageKey ? t(details.messageKey) : null));
    }
  }

  /** Zakaz wysylania wiadomosci - osobna kara od zakazu publikowania. */
  async function setMessagingBan(id, value) {
    setError(null);
    setMessage(null);
    const payload = banPayload(value);
    try {
      await client.patch(`/users/${id}/messaging-ban`, payload);
      if (payload.forever) {
        setMessage(t('users.mutedForever'));
      } else {
        setMessage(payload.hours == null
          ? t('users.muteLifted')
          : t('users.muted', { count: payload.hours }));
      }
      setRefresh((n) => n + 1);
    } catch (problem) {
      const details = describeError(problem);
      setError(details.message ?? (details.messageKey ? t(details.messageKey) : null));
    }
  }

  /**
   * Pokazuje konta logujace sie z tych samych adresow.
   *
   * <p>Pobieramy DWIE listy: adresy tego konta (do skopiowania w blokade)
   * i konta, ktore ich uzywaja. Osobne zapytania, bo to dwie rozne rzeczy -
   * konto moze miec adresy, z ktorych nikt wiecej sie nie logowal.</p>
   */
  async function showRelated(target) {
    setRelated({ user: target, addresses: [], accounts: [], loading: true });
    try {
      const [addresses, accounts] = await Promise.all([
        client.get(`/users/${target.id}/addresses`),
        client.get(`/users/${target.id}/related`),
      ]);
      setRelated({
        user: target,
        addresses: addresses.data,
        accounts: accounts.data,
        loading: false,
      });
    } catch {
      setRelated({ user: target, addresses: [], accounts: [], loading: false });
    }
  }

  /** Blokuje adres. Powod jest obowiazkowy - patrz encja BlockedIp. */
  async function blockAddress(address) {
    const reason = window.prompt(t('users.blockReasonPrompt', { address }));
    if (!reason || !reason.trim()) {
      return;
    }
    setError(null);
    try {
      await client.post('/users/blocked-ips', { address, reason: reason.trim() });
      setMessage(t('users.addressBlocked', { address }));
      loadBlocked();
    } catch (problem) {
      const details = describeError(problem);
      setError(details.message ?? (details.messageKey ? t(details.messageKey) : null));
    }
  }

  async function unblockAddress(id) {
    await client.delete(`/users/blocked-ips/${id}`).catch(() => {});
    loadBlocked();
  }

  async function deleteUser() {
    const target = toDelete;
    setToDelete(null);
    setError(null);
    setMessage(null);
    try {
      await client.delete(`/users/${target.id}`);
      setMessage(t('users.deleted', { username: target.username }));
      setRefresh((n) => n + 1);
    } catch (error) {
      const details = describeError(error);
      setError(details.message ?? (details.messageKey ? t(details.messageKey) : null));
    }
  }

  async function changeRole(id, newRole) {
    setError(null);
    setMessage(null);
    try {
      await client.patch(`/users/${id}/role`, { role: newRole });
      setMessage(t('users.roleChanged'));
      // Przeladowujemy liste, zeby pokazac stan faktycznie zapisany w bazie
      setRefresh((n) => n + 1);
    } catch (error) {
      const details = describeError(error);
      setError(details.message ?? (details.messageKey ? t(details.messageKey) : null));
    }
  }

  return (
    <>
      <Card>
        <Card.Body>
          <Card.Title as="h1" className="h4 mb-3">
            {t('users.title')}
          </Card.Title>

          <Row className="g-2 mb-3">
            <Col md={4}>
              <Form.Group controlId="fragment">
                <Form.Label className="small text-body-secondary">{t('users.search')}</Form.Label>
                <Form.Control
                  value={fragment}
                  placeholder={t('users.searchPlaceholder')}
                  onChange={(e) => changeFilter(setFragment)(e.target.value)}
                />
              </Form.Group>
            </Col>

            {/* Wymaganie nr 3 - uzytkownik wybiera, ile elementow na stronie */}
            <Col md={2}>
              <Form.Group controlId="size">
                <Form.Label className="small text-body-secondary">{t('users.pageSize')}</Form.Label>
                <Form.Select
                  value={size}
                  onChange={(e) => changeFilter(setSize)(Number(e.target.value))}
                >
                  {[5, 10, 20].map((n) => (
                    <option key={n} value={n}>
                      {n}
                    </option>
                  ))}
                </Form.Select>
              </Form.Group>
            </Col>

            {/* Wymaganie nr 5 - sortowanie po stronie backendu */}
            <Col md={3}>
              <Form.Group controlId="sortBy">
                <Form.Label className="small text-body-secondary">{t('users.sortBy')}</Form.Label>
                <Form.Select value={sortBy} onChange={(e) => changeFilter(setSortBy)(e.target.value)}>
                  <option value="username">{t('users.sortUsername')}</option>
                  <option value="email">{t('users.sortEmail')}</option>
                  <option value="createdAt">{t('users.sortCreatedAt')}</option>
                </Form.Select>
              </Form.Group>
            </Col>

            <Col md={3}>
              <Form.Group controlId="direction">
                <Form.Label className="small text-body-secondary">{t('users.direction')}</Form.Label>
                <Form.Select
                  value={direction}
                  onChange={(e) => changeFilter(setDirection)(e.target.value)}
                >
                  <option value="asc">{t('users.asc')}</option>
                  <option value="desc">{t('users.desc')}</option>
                </Form.Select>
              </Form.Group>
            </Col>
          </Row>

          {message && (
            <Alert variant="success" dismissible onClose={() => setMessage(null)}>
              {message}
            </Alert>
          )}
          {error && <Alert variant="danger">{error}</Alert>}

          {loading && (
            <div className="text-center py-3 text-body-secondary">
              <Spinner animation="border" size="sm" className="me-2" />
              {t('common.loading')}
            </div>
          )}

          {!loading && !error && pageData && (
            <>
              {pageData.content.length === 0 ? (
                <p className="text-body-secondary">{t('users.empty')}</p>
              ) : (
                <Table responsive hover size="sm" className="align-middle">
                  <thead>
                    <tr>
                      <th>{t('users.colUsername')}</th>
                      <th>{t('users.colEmail')}</th>
                      <th>{t('users.colCreatedAt')}</th>
                      <th style={{ width: 180 }}>{t('users.colRole')}</th>
                      <th style={{ width: 190 }}>{t('users.colPostingBan')}</th>
                      <th style={{ width: 190 }}>{t('users.colMessagingBan')}</th>
                      <th style={{ width: 110 }}>{t('users.colReports')}</th>
                      <th style={{ width: 150 }}></th>
                    </tr>
                  </thead>
                  <tbody>
                    {pageData.content.map((u) => {
                      // Administrator nie moze zmienic wlasnej roli - blokuje to
                      // takze backend (409), tu tylko wygaszamy pole
                      const mySelf = u.username === loggedIn.username;

                      // Termin w przeszlosci znaczy "kara juz minela" - tak samo
                      // jak po stronie serwera, zeby panel nie klamal
                      const banned = u.postingBannedUntil != null
                        && new Date(u.postingBannedUntil) > new Date();

                      const mutedNow = u.messagingBannedUntil != null
                        && new Date(u.messagingBannedUntil) > new Date();

                      return (
                        <tr key={u.id}>
                          <td>{u.username}</td>
                          <td>{u.email}</td>
                          <td>{formatDate(u.createdAt, i18n.language)}</td>
                          <td>
                            <Form.Select
                              size="sm"
                              value={u.role}
                              disabled={mySelf}
                              title={mySelf ? t('users.selfRoleHint') : undefined}
                              onChange={(e) => changeRole(u.id, e.target.value)}
                              aria-label={t('users.colRole')}
                            >
                              <option value="USER">{t('users.roleUser')}</option>
                              <option value="ADMIN">{t('users.roleAdmin')}</option>
                            </Form.Select>
                          </td>

                          <td>
                            <Form.Select
                              size="sm"
                              value=""
                              disabled={mySelf}
                              onChange={(e) => setPostingBan(u.id, e.target.value)}
                              aria-label={t('users.colPostingBan')}
                            >
                              {/*
                                Pusta pozycja jest jednoczesnie stanem "nic nie
                                wybrano" i poleceniem "zdejmij zakaz" - dzieki temu
                                lista wraca po kazdej zmianie do stanu neutralnego
                                i nie sugeruje, ze cos jest teraz ustawione.
                              */}
                              <option value="">
                                {banned ? t('users.banLift') : t('users.banNone')}
                              </option>
                              {BAN_OPTIONS.map((h) => (
                                <option key={h} value={h}>
                                  {t('users.banFor', { count: h })}
                                </option>
                              ))}
                              {/*
                                "Na zawsze" stoi na koncu, za wszystkimi
                                terminami. To najciezsza z kar w tej kolumnie,
                                wiec nie ma prawa byc pierwsza pod kursorem.
                              */}
                              <option value={FOREVER}>{t('users.banForever')}</option>
                            </Form.Select>

                            {banned && (
                              <div className="small text-danger mt-1">
                                {isForever(u.postingBannedUntil)
                                  ? t('users.bannedForever')
                                  : t('users.bannedUntil', {
                                    date: formatDate(u.postingBannedUntil, i18n.language),
                                  })}
                              </div>
                            )}
                          </td>

                          {/*
                            Zakaz WIADOMOSCI - osobny od zakazu publikowania.
                            Ktos moze zasmiecac tablice, nie dokuczajac nikomu
                            prywatnie, i odwrotnie; jeden przelacznik na oba
                            przypadki nie pozwalalby wyrazic zadnego z nich.
                          */}
                          <td>
                            <Form.Select
                              size="sm"
                              value=""
                              disabled={mySelf}
                              onChange={(e) => setMessagingBan(u.id, e.target.value)}
                              aria-label={t('users.colMessagingBan')}
                            >
                              <option value="">
                                {mutedNow ? t('users.banLift') : t('users.banNone')}
                              </option>
                              {BAN_OPTIONS.map((h) => (
                                <option key={h} value={h}>
                                  {t('users.banFor', { count: h })}
                                </option>
                              ))}
                              <option value={FOREVER}>{t('users.banForever')}</option>
                            </Form.Select>

                            {mutedNow && (
                              <div className="small text-danger mt-1">
                                {isForever(u.messagingBannedUntil)
                                  ? t('users.mutedForever')
                                  : t('users.mutedUntil', {
                                    date: formatDate(u.messagingBannedUntil, i18n.language),
                                  })}
                              </div>
                            )}
                          </td>

                          {/*
                            Liczba ZASADNYCH zgloszen. Najwazniejsza liczba
                            w calym panelu: jedno zgloszenie moze byc
                            nieporozumieniem, piate to juz wzorzec zachowania.
                          */}
                          <td>
                            {u.resolvedReports > 0 ? (
                              <Link to="/zgloszenia" className="badge text-bg-warning text-decoration-none">
                                {u.resolvedReports}
                              </Link>
                            ) : (
                              <span className="text-body-secondary small">—</span>
                            )}
                          </td>

                          <td className="text-end">
                            <div className="d-flex gap-1 justify-content-end">
                              <Button
                                size="sm"
                                variant="outline-secondary"
                                title={t('users.relatedHint')}
                                onClick={() => showRelated(u)}
                              >
                                <IconBan />
                              </Button>

                              <Button
                                size="sm"
                                variant="outline-danger"
                                disabled={mySelf}
                                title={mySelf ? t('users.selfDeleteHint') : undefined}
                                onClick={() => setToDelete(u)}
                              >
                                {t('common.delete')}
                              </Button>
                            </div>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </Table>
              )}

              <div className="d-flex align-items-center justify-content-between flex-wrap gap-2">
                <Button
                  variant="outline-secondary"
                  size="sm"
                  onClick={() => setPage((p) => p - 1)}
                  disabled={pageData.first}
                >
                  {t('users.previous')}
                </Button>

                <span className="text-body-secondary small">
                  {t('users.summary', {
                    // W API strony liczy sie od zera, uzytkownikowi pokazujemy od jedynki
                    page: pageData.number + 1,
                    totalPages: Math.max(pageData.totalPages, 1),
                    total: pageData.totalElements,
                  })}
                </span>

                <Button
                  variant="outline-secondary"
                  size="sm"
                  onClick={() => setPage((p) => p + 1)}
                  disabled={pageData.last}
                >
                  {t('users.next')}
                </Button>
              </div>
            </>
          )}
        </Card.Body>

        {/*
          Usuniecie konta jest nieodwracalne i zabiera ze soba posty, zdjecia
          i znajomosci - dlatego pytamy, zamiast kasowac od razu po klikniecu.
          Okno wymienia wprost, co zniknie: "czy na pewno?" bez tej listy nie
          daje podstawy do decyzji.
        */}
        <Modal show={toDelete != null} onHide={() => setToDelete(null)} centered>
          <Modal.Header closeButton>
            <Modal.Title as="h2" className="h5">{t('users.deleteTitle')}</Modal.Title>
          </Modal.Header>
          <Modal.Body>
            <p className="mb-2">
              {t('users.deleteQuestion', { username: toDelete?.username })}
            </p>
            <p className="text-body-secondary small mb-0">{t('users.deleteWhatGoes')}</p>
          </Modal.Body>
          <Modal.Footer>
            <Button variant="secondary" onClick={() => setToDelete(null)}>
              {t('common.cancel')}
            </Button>
            <Button variant="danger" onClick={deleteUser}>
              {t('users.deleteConfirm')}
            </Button>
          </Modal.Footer>
        </Modal>
      </Card>

      {/* --- Powiazane konta i blokada adresu ------------------------- */}
      <Modal show={related != null} onHide={() => setRelated(null)} size="lg" centered>
        <Modal.Header closeButton>
          <Modal.Title as="h5">
            {t('users.relatedTitle', { username: related?.user?.username })}
          </Modal.Title>
        </Modal.Header>
        <Modal.Body>
          {/*
            Ostrzezenie stoi NAD danymi, a nie pod nimi. Wspolny adres to
            poszlaka, a nie dowod: pod jednym adresem siedzi cala rodzina,
            akademik albo tysiace klientow operatora komorkowego. Zdanie
            przeczytane po obejrzeniu listy juz na nic sie nie zda.
          */}
          <Alert variant="warning" className="small">{t('users.relatedWarning')}</Alert>

          {related?.loading && <p className="text-body-secondary">{t('common.loading')}</p>}

          {!related?.loading && (
            <>
              <h6>{t('users.addressesOf', { username: related?.user?.username })}</h6>
              {related?.addresses.length === 0 ? (
                <p className="text-body-secondary small">{t('users.noAddresses')}</p>
              ) : (
                <ul className="list-unstyled small">
                  {related?.addresses.map((entry) => (
                    <li key={entry.address} className="d-flex align-items-center gap-2 mb-1 flex-wrap">
                      <code>{entry.address}</code>
                      <span className="text-body-secondary">
                        {t('users.loginCount', { count: entry.loginCount })}
                        {' \u00b7 '}
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
              {related?.accounts.length === 0 ? (
                <p className="text-body-secondary small mb-0">{t('users.noRelated')}</p>
              ) : (
                <ul className="list-unstyled small mb-0">
                  {related?.accounts.map((entry) => (
                    <li key={`${entry.userId}-${entry.address}`} className="mb-1">
                      <Link to={`/profil/${entry.username}`}>{entry.username}</Link>
                      {' \u2014 '}
                      <code>{entry.address}</code>
                      {' \u00b7 '}
                      {t('users.loginCount', { count: entry.loginCount })}
                    </li>
                  ))}
                </ul>
              )}
            </>
          )}
        </Modal.Body>
        <Modal.Footer>
          <Button variant="outline-secondary" onClick={() => setRelated(null)}>
            {t('common.close')}
          </Button>
        </Modal.Footer>
      </Modal>

      {/* --- Zablokowane adresy --------------------------------------- */}
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
