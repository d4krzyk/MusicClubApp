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
import client, { opiszBlad } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { sformatujDate } from '../utils/daty';

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
  const { user: zalogowany } = useAuth();

  // Parametry wysylane do backendu
  const [fragment, setFragment] = useState('');
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(5);
  const [sortBy, setSortBy] = useState('username');
  const [direction, setDirection] = useState('asc');

  const [strona, setStrona] = useState(null);
  const [ladowanie, setLadowanie] = useState(true);
  const [blad, setBlad] = useState(null);
  const [komunikat, setKomunikat] = useState(null);
  const [odswiez, setOdswiez] = useState(0);

  /*
   * Odpytujemy backend przy kazdej zmianie parametrow.
   *
   * Wyszukiwanie jest opoznione o 300 ms (debounce) - bez tego kazde
   * nacisniecie klawisza wysylaloby osobne zapytanie i przy szybkim pisaniu
   * odpowiedzi potrafilyby wrocic w zlej kolejnosci.
   */
  useEffect(() => {
    let anulowane = false;
    const licznik = setTimeout(async () => {
      setLadowanie(true);
      setBlad(null);
      try {
        const odpowiedz = await client.get('/users', {
          params: { fragment, page, size, sortBy, direction },
        });
        if (!anulowane) {
          setStrona(odpowiedz.data);
        }
      } catch (error) {
        if (!anulowane) {
          const opis = opiszBlad(error);
          setBlad(opis.message ?? (opis.messageKey ? t(opis.messageKey) : null));
        }
      } finally {
        if (!anulowane) {
          setLadowanie(false);
        }
      }
    }, 300);

    return () => {
      anulowane = true;
      clearTimeout(licznik);
    };
  }, [fragment, page, size, sortBy, direction, odswiez, t]);

  /*
   * Zmiana filtra musi cofac na pierwsza strone. Bez tego przy wejsciu
   * na strone 3 i zawezeniu wyszukiwania do jednego wyniku uzytkownik
   * zobaczylby pusta liste - bo strona 3 wtedy nie istnieje.
   */
  function zmienFiltr(ustawiacz) {
    return (wartosc) => {
      ustawiacz(wartosc);
      setPage(0);
    };
  }

  async function zmienRole(id, nowaRola) {
    setBlad(null);
    setKomunikat(null);
    try {
      await client.patch(`/users/${id}/role`, { role: nowaRola });
      setKomunikat(t('users.roleChanged'));
      // Przeladowujemy liste, zeby pokazac stan faktycznie zapisany w bazie
      setOdswiez((n) => n + 1);
    } catch (error) {
      const opis = opiszBlad(error);
      setBlad(opis.message ?? (opis.messageKey ? t(opis.messageKey) : null));
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
                onChange={(e) => zmienFiltr(setFragment)(e.target.value)}
              />
            </Form.Group>
          </Col>

          {/* Wymaganie nr 3 - uzytkownik wybiera, ile elementow na stronie */}
          <Col md={2}>
            <Form.Group controlId="size">
              <Form.Label className="small text-body-secondary">{t('users.pageSize')}</Form.Label>
              <Form.Select
                value={size}
                onChange={(e) => zmienFiltr(setSize)(Number(e.target.value))}
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
              <Form.Select value={sortBy} onChange={(e) => zmienFiltr(setSortBy)(e.target.value)}>
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
                onChange={(e) => zmienFiltr(setDirection)(e.target.value)}
              >
                <option value="asc">{t('users.asc')}</option>
                <option value="desc">{t('users.desc')}</option>
              </Form.Select>
            </Form.Group>
          </Col>
        </Row>

        {komunikat && (
          <Alert variant="success" dismissible onClose={() => setKomunikat(null)}>
            {komunikat}
          </Alert>
        )}
        {blad && <Alert variant="danger">{blad}</Alert>}

        {ladowanie && (
          <div className="text-center py-3 text-body-secondary">
            <Spinner animation="border" size="sm" className="me-2" />
            {t('common.loading')}
          </div>
        )}

        {!ladowanie && !blad && strona && (
          <>
            {strona.content.length === 0 ? (
              <p className="text-body-secondary">{t('users.empty')}</p>
            ) : (
              <Table responsive hover size="sm" className="align-middle">
                <thead>
                  <tr>
                    <th>{t('users.colUsername')}</th>
                    <th>{t('users.colEmail')}</th>
                    <th>{t('users.colCreatedAt')}</th>
                    <th style={{ width: 180 }}>{t('users.colRole')}</th>
                  </tr>
                </thead>
                <tbody>
                  {strona.content.map((u) => {
                    // Administrator nie moze zmienic wlasnej roli - blokuje to
                    // takze backend (409), tu tylko wygaszamy pole
                    const toJa = u.username === zalogowany.username;

                    return (
                      <tr key={u.id}>
                        <td>{u.username}</td>
                        <td>{u.email}</td>
                        <td>{sformatujDate(u.createdAt, i18n.language)}</td>
                        <td>
                          <Form.Select
                            size="sm"
                            value={u.role}
                            disabled={toJa}
                            title={toJa ? t('users.selfRoleHint') : undefined}
                            onChange={(e) => zmienRole(u.id, e.target.value)}
                            aria-label={t('users.colRole')}
                          >
                            <option value="USER">{t('users.roleUser')}</option>
                            <option value="ADMIN">{t('users.roleAdmin')}</option>
                          </Form.Select>
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
                disabled={strona.first}
              >
                {t('users.previous')}
              </Button>

              <span className="text-body-secondary small">
                {t('users.summary', {
                  // W API strony liczy sie od zera, uzytkownikowi pokazujemy od jedynki
                  page: strona.number + 1,
                  totalPages: Math.max(strona.totalPages, 1),
                  total: strona.totalElements,
                })}
              </span>

              <Button
                variant="outline-secondary"
                size="sm"
                onClick={() => setPage((p) => p + 1)}
                disabled={strona.last}
              >
                {t('users.next')}
              </Button>
            </div>
          </>
        )}
      </Card.Body>
    </Card>
  );
}
