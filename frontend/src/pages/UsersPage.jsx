import { useEffect, useState } from 'react';
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
import { describeError } from '../api/client';
import * as moderacja from '../api/moderacja';
import { useAuth } from '../auth/AuthContext';
import { IconBan } from '../components/Icons';
import PanelSieciowy from '../components/PanelSieciowy';
import { formatDate, formatDateTime, isForever } from '../utils/dates';
import { OKRESY_KARY, BEZTERMINOWO, ZDEJMIJ, trescKary } from '../moderacja/kary';

/** Napisy zalezne od rodzaju kary. */
const BAN_LABELS = {
  POSTING: {
    column: 'users.colPostingBan',
    set: 'users.banSet',
    lifted: 'users.banLifted',
    forever: 'users.bannedForever',
  },
  MESSAGING: {
    column: 'users.colMessagingBan',
    set: 'users.muted',
    lifted: 'users.muteLifted',
    forever: 'users.mutedForever',
  },
};

/** Panel administratora: lista kont ze stronicowaniem, sortowaniem i zmiana rol. */
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

  /* Konto wybrane do usuniecia. */
  const [toDelete, setToDelete] = useState(null);

  /** Okienko "powiazane konta" - null, gdy zamkniete. */
  const [related, setRelated] = useState(null);


  /* Odpytujemy backend przy kazdej zmianie parametrow. */
  useEffect(() => {
    let cancelled = false;
    const counter = setTimeout(async () => {
      setLoading(true);
      setError(null);
      try {
        const strona = await moderacja.konta({
          fragment, strona: page, rozmiar: size, sortujPo: sortBy, kolejnosc: direction,
        });
        if (!cancelled) {
          setPageData(strona);
        }
      } catch (error) {
        if (!cancelled) {
          const details = describeError(error);
          setError(details.message);
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

  /* Zmiana filtra musi cofac na pierwsza strone. */
  function changeFilter(setter) {
    return (value) => {
      setter(value);
      setPage(0);
    };
  }



  /** Pyta o potwierdzenie i mowi wprost, co zaraz sie stanie. */
  function confirmed(username, opis) {
    return window.confirm(t('users.confirmAction', { username, action: opis }));
  }

  /** Opis kary do pytania o potwierdzenie - ten sam tekst co na liscie. */
  function describeChoice(value) {
    if (value === ZDEJMIJ || value === '') {
      return t('users.banLift');
    }
    return value === BEZTERMINOWO
      ? t('users.banForever')
      : t('users.banFor', { count: Number(value) });
  }

  /** Naklada albo zdejmuje kare - jedna funkcja na oba rodzaje. */
  async function setBan(id, kind, value, username) {
    const napisy = BAN_LABELS[kind];
    if (!value || !confirmed(username, `${t(napisy.column)}: ${describeChoice(value)}`)) {
      return;
    }
    setError(null);
    setMessage(null);

    const payload = trescKary(value);
    try {
      await moderacja.ustawZakaz(id, kind, payload);
      setMessage(banMessage(napisy, payload));
      setRefresh((n) => n + 1);
    } catch (problem) {
      setError(describeError(problem).message);
    }
  }

  /** Potwierdzenie po nalozeniu albo zdjeciu kary. */
  function banMessage(napisy, payload) {
    if (payload.forever) {
      return t(napisy.forever);
    }
    return payload.hours == null
      ? t(napisy.lifted)
      : t(napisy.set, { count: payload.hours });
  }




  async function deleteUser() {
    const target = toDelete;
    setToDelete(null);
    setError(null);
    setMessage(null);
    try {
      await moderacja.usunKonto(target.id);
      setMessage(t('users.deleted', { username: target.username }));
      setRefresh((n) => n + 1);
    } catch (error) {
      const details = describeError(error);
      setError(details.message);
    }
  }

  async function changeRole(id, newRole, username) {
    const opis = t('users.colRole') + ': '
      + t(newRole === 'ADMIN' ? 'users.roleAdmin' : 'users.roleUser');
    if (!confirmed(username, opis)) {
      setRefresh((n) => n + 1);   // przywraca liste do stanu z bazy
      return;
    }
    setError(null);
    setMessage(null);
    try {
      await moderacja.zmienRole(id, newRole);
      setMessage(t('users.roleChanged'));
      // Przeladowujemy liste, zeby pokazac stan faktycznie zapisany w bazie
      setRefresh((n) => n + 1);
    } catch (error) {
      const details = describeError(error);
      setError(details.message);
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
                              onChange={(e) => changeRole(u.id, e.target.value, u.username)}
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
                              onChange={(e) => setBan(u.id, 'POSTING', e.target.value, u.username)}
                              aria-label={t('users.colPostingBan')}
                            >
                              {/*
                                Pusta pozycja to WYLACZNIE stan neutralny ("nic nie wybrano"), do
                                ktorego lista wraca po kazdej akcji.
                              */}
                              <option value="">{t('users.chooseAction')}</option>
                              {banned && <option value={ZDEJMIJ}>{t('users.banLift')}</option>}
                              {OKRESY_KARY.map((h) => (
                                <option key={h} value={h}>
                                  {t('users.banFor', { count: h })}
                                </option>
                              ))}
                              {/* "Na zawsze" stoi na koncu, za wszystkimi terminami. */}
                              <option value={BEZTERMINOWO}>{t('users.banForever')}</option>
                            </Form.Select>

                            {banned && (
                              <div className="small text-danger mt-1">
                                {isForever(u.postingBannedUntil)
                                  ? t('users.bannedForever')
                                  : t('users.bannedUntil', {
                                    date: formatDateTime(u.postingBannedUntil, i18n.language),
                                  })}
                              </div>
                            )}
                          </td>

                          {/* Zakaz WIADOMOSCI - osobny od zakazu publikowania. */}
                          <td>
                            <Form.Select
                              size="sm"
                              value=""
                              disabled={mySelf}
                              onChange={(e) => setBan(u.id, 'MESSAGING', e.target.value, u.username)}
                              aria-label={t('users.colMessagingBan')}
                            >
                              <option value="">{t('users.chooseAction')}</option>
                              {mutedNow && <option value={ZDEJMIJ}>{t('users.banLift')}</option>}
                              {OKRESY_KARY.map((h) => (
                                <option key={h} value={h}>
                                  {t('users.banFor', { count: h })}
                                </option>
                              ))}
                              <option value={BEZTERMINOWO}>{t('users.banForever')}</option>
                            </Form.Select>

                            {mutedNow && (
                              <div className="small text-danger mt-1">
                                {isForever(u.messagingBannedUntil)
                                  ? t('users.mutedForever')
                                  : t('users.mutedUntil', {
                                    date: formatDateTime(u.messagingBannedUntil, i18n.language),
                                  })}
                              </div>
                            )}
                          </td>

                          {/* Liczba ZASADNYCH zgloszen. */}
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
                                onClick={() => setRelated(u)}
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
          Usuniecie konta jest nieodwracalne i zabiera ze soba posty, zdjecia i znajomosci -
          dlatego pytamy, zamiast kasowac od razu po klikniecu.
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

      {/*
        Powiazania sieciowe (multikonta, blokada adresu) maja wlasny komponent razem ze swoim
        stanem i zapytaniami.
      */}
      <PanelSieciowy
        target={related}
        onClose={() => setRelated(null)}
        onMessage={setMessage}
        onError={setError}
      />

    </>
  );
}
