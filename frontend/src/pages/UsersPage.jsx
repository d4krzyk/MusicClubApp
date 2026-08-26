import { useEffect, useState } from 'react';
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
import { formatDate } from '../utils/dates';

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

  async function setPostingBan(id, hours) {
    setError(null);
    setMessage(null);
    try {
      await client.patch(`/users/${id}/posting-ban`, { hours });
      setMessage(t(hours == null ? 'users.banLifted' : 'users.banSet'));
      setRefresh((n) => n + 1);
    } catch (error) {
      const details = describeError(error);
      setError(details.message ?? (details.messageKey ? t(details.messageKey) : null));
    }
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
                    <th style={{ width: 210 }}>{t('users.colPostingBan')}</th>
                    <th style={{ width: 90 }}></th>
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
                            onChange={(e) => setPostingBan(
                              u.id,
                              e.target.value === '' ? null : Number(e.target.value))}
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
                          </Form.Select>

                          {banned && (
                            <div className="small text-danger mt-1">
                              {t('users.bannedUntil', {
                                date: formatDate(u.postingBannedUntil, i18n.language),
                              })}
                            </div>
                          )}
                        </td>

                        <td className="text-end">
                          <Button
                            size="sm"
                            variant="outline-danger"
                            disabled={mySelf}
                            title={mySelf ? t('users.selfDeleteHint') : undefined}
                            onClick={() => setToDelete(u)}
                          >
                            {t('common.delete')}
                          </Button>
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
  );
}
