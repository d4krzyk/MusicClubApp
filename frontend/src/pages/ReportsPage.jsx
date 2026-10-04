import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import Card from 'react-bootstrap/Card';
import Button from 'react-bootstrap/Button';
import ButtonGroup from 'react-bootstrap/ButtonGroup';
import Form from 'react-bootstrap/Form';
import Alert from 'react-bootstrap/Alert';
import Badge from 'react-bootstrap/Badge';
import { describeError } from '../api/client';
import * as moderacja from '../api/moderacja';
import { useAuth } from '../auth/AuthContext';
import Avatar from '../components/Avatar';
import DoladujWiecej from '../components/DoladujWiecej';
import EmptyState from '../components/EmptyState';
import PostSkeleton from '../components/PostSkeleton';
import { IconCheckCircle, IconFlag, IconShieldAlert } from '../components/Icons';
import { formatDateTime, timeAgo } from '../utils/dates';
import { OKRESY_KARY, BEZTERMINOWO } from '../moderacja/kary';

/** Filtry w kolejnosci przydatnosci: najpierw to, co czeka na decyzje. */
const FILTERS = ['OPEN', 'RESOLVED', 'DISMISSED', 'ALL'];

/** Ile zgloszen pobieramy naraz. */
const ROZMIAR_STRONY = 30;

/** Panel zgloszen - tylko dla administratora. */
export default function ReportsPage() {
  const { t, i18n } = useTranslation();
  const { user: loggedIn } = useAuth();

  const [filter, setFilter] = useState('OPEN');
  const [reports, setReports] = useState([]);
  const [strona, setStrona] = useState(0);
  const [ostatnia, setOstatnia] = useState(true);
  const [wszystkich, setWszystkich] = useState(0);
  const [loading, setLoading] = useState(true);
  const [doladowywanie, setDoladowywanie] = useState(false);
  const [error, setError] = useState(null);

  /* "dolacz" odroznia doladowanie starszych od pobrania listy od nowa. */
  const load = useCallback(async (numer, dolacz) => {
    if (dolacz) {
      setDoladowywanie(true);
    } else {
      setLoading(true);
    }
    setError(null);
    try {
      const data = await moderacja.zgloszenia({
        status: filter === 'ALL' ? undefined : filter,
        strona: numer,
        rozmiar: ROZMIAR_STRONY,
      });

      setReports((poprzednie) => (dolacz ? [...poprzednie, ...data.content] : data.content));
      setStrona(data.number);
      setOstatnia(data.last);
      setWszystkich(data.totalElements);
    } catch (problem) {
      const details = describeError(problem);
      setError(details.message);
      // Nieudane doladowanie nie moze skasowac tego, co juz widac
      if (!dolacz) {
        setReports([]);
      }
    } finally {
      setLoading(false);
      setDoladowywanie(false);
    }
    // Bez "t" w zaleznosciach: describeError siega do i18n samo, a przelaczenie jezyka
    // pobieraloby liste od nowa i gubilo doladowane strony
  }, [filter]);

  /* Zmiana filtra to inny zbior spraw, wiec zaczynamy od pierwszej strony. */
  useEffect(() => {
    load(0, false);
  }, [load]);

  return (
    <div className="reports-page">
      {/* Tytul w osobnym wierszu. */}
      <div>
        <h1 className="page-title">{t('reports.panelTitle')}</h1>
      </div>

      <ButtonGroup size="sm" className="mb-3">
        {FILTERS.map((code) => (
          <Button
            key={code}
            variant={filter === code ? 'primary' : 'outline-secondary'}
            onClick={() => setFilter(code)}
            aria-pressed={filter === code}
          >
            {t(`reports.filters.${code}`)}
          </Button>
        ))}
      </ButtonGroup>

      {error && <Alert variant="danger">{error}</Alert>}

      {loading && <PostSkeleton count={3} />}

      {!loading && reports.length === 0 && (
        <EmptyState
          icon={filter === 'OPEN' ? IconCheckCircle : IconShieldAlert}
          title={filter === 'OPEN' ? t('reports.emptyOpen') : t('reports.emptyOther')}
          text={filter === 'OPEN' ? t('reports.emptyOpenHint') : null}
        />
      )}

      {!loading && reports.map((report) => (
        <ReportCard
          key={report.id}
          report={report}
          language={i18n.language}
          t={t}
          /*
           * Zgloszenie moze dotyczyc samego administratora, ktory je oglada - wtedy nie wolno mu
           * przy okazji ukarac wlasnego konta.
           */
          me={loggedIn?.username}
          /*
           * Po decyzji wracamy na pierwsza strone, bo sprawa wypada z filtra OPEN i cala lista
           * przesuwa sie o jedna pozycje - doklejanie kolejnej strony pomineloby wtedy jedno
           * zgloszenie.
           */
          onResolved={() => load(0, false)}
        />
      ))}

      {!loading && reports.length > 0 && wszystkich > ROZMIAR_STRONY && (
        <DoladujWiecej
          etykieta={t('reports.loadMore')}
          pokazano={reports.length}
          wszystkich={wszystkich}
          ostatnia={ostatnia}
          ladowanie={doladowywanie}
          onClick={() => load(strona + 1, true)}
        />
      )}
    </div>
  );
}

/** Jedno zgloszenie: naglowek, opis, dowody i decyzja. */
function ReportCard({ report, language, t, me, onResolved }) {
  const [expanded, setExpanded] = useState(false);
  const [details, setDetails] = useState(null);
  const [note, setNote] = useState('');
  const [sending, setSending] = useState(false);
  const [error, setError] = useState(null);

  /* Domyslnie NIC nie robimy z kontem. */
  const [action, setAction] = useState('NONE');
  const [duration, setDuration] = useState('24');

  const open = report.status === 'OPEN';

  /* Czas trwania dotyczy tylko zakazow - usuniecie konta trwa zawsze */
  const needsDuration = action === 'BAN_POSTING' || action === 'BAN_MESSAGING';

  /* Zgloszenie NA SAMEGO SIEBIE. */
  const aboutMe = !!me && report.reportedUsername === me;

  /* Kasowanie posta pokazujemy WYLACZNIE przy zgloszeniu posta. */
  const actions = ['NONE',
    ...(report.postId && !report.commentId ? ['DELETE_POST'] : []),
    ...(report.commentId ? ['DELETE_COMMENT'] : []),
    /* Karta profilu (zdjecia, opis, pytania) - przy zgloszeniu samego profilu */
    ...(report.context === 'PROFILE' ? ['CLEAR_CARD'] : []),
    ...(aboutMe ? [] : ['BAN_POSTING', 'BAN_MESSAGING', 'DELETE_ACCOUNT'])];

  /** Dowody pobieramy dopiero przy rozwinieciu karty. */
  async function toggle() {
    const next = !expanded;
    setExpanded(next);

    if (next && !details) {
      try {
        setDetails(await moderacja.zgloszenie(report.id));
      } catch {
        setDetails({ ...report, evidence: [] });
      }
    }
  }

  async function decide(decision) {
    /*
     * Usuniecie konta jest nieodwracalne, wiec pytamy jeszcze raz - i to PRZED wyslaniem, a nie
     * po.
     */
    if (action === 'DELETE_ACCOUNT'
      && !window.confirm(t('reports.confirmDeleteAccount', { username: report.reportedUsername }))) {
      return;
    }

    setSending(true);
    setError(null);
    try {
      await moderacja.rozpatrz(report.id, {
        decision,
        note: note.trim(),
        action,
        // Godziny wysylamy TYLKO przy karze czasowej - przy pozostalych
        // dzialaniach nie znacza nic i tylko myliłyby w zapisie zapytania
        hours: needsDuration && duration !== BEZTERMINOWO ? Number(duration) : null,
        forever: needsDuration && duration === BEZTERMINOWO,
      });
      onResolved();
    } catch (problem) {
      const problems = describeError(problem, 'reports.failed');
      setError(problems.message);
    } finally {
      setSending(false);
    }
  }

  /** Otwiera sprawe z powrotem, zeby dalo sie zdecydowac inaczej. */
  async function reopen() {
    setError(null);
    try {
      await moderacja.otworzPonownie(report.id);
      onResolved();
    } catch (problem) {
      const problems = describeError(problem, 'reports.failed');
      setError(problems.message);
    }
  }

  const evidence = details?.evidence ?? [];

  return (
    <Card className={`mb-3 report-card${open ? ' is-open' : ''}`}>
      <Card.Body>
        <div className="d-flex align-items-start gap-3 flex-wrap">
          <Link to={`/profil/${report.reportedUsername}`} className="text-decoration-none">
            <Avatar
              avatarUrl={report.reportedAvatarUrl}
              username={report.reportedUsername}
              size={48}
            />
          </Link>

          <div className="flex-grow-1 min-width-0">
            <div className="d-flex align-items-center gap-2 flex-wrap">
              <Link
                to={`/profil/${report.reportedUsername}`}
                className="fw-semibold text-decoration-none text-body"
              >
                {report.reportedUsername}
              </Link>

              <Badge bg="danger-subtle" text="danger-emphasis">
                {t(`reports.reasons.${report.reason}`)}
              </Badge>
              <Badge bg="secondary-subtle" text="secondary-emphasis">
                {t(`reports.contexts.${report.context}`)}
              </Badge>

              {/* Historia konta. */}
              {report.priorResolved > 0 && (
                <Badge bg="warning-subtle" text="warning-emphasis">
                  {t('reports.priorResolved', { count: report.priorResolved })}
                </Badge>
              )}
            </div>

            <div className="text-body-secondary small mt-1">
              {t('reports.filedBy', { username: report.reporterUsername })}
              {' · '}
              {timeAgo(report.createdAt, language)}
            </div>

            <p className="mt-2 mb-0 report-description">{report.description}</p>
          </div>

          {!open && (
            <Badge bg={report.status === 'RESOLVED' ? 'success' : 'secondary'}>
              {t(`reports.statuses.${report.status}`)}
            </Badge>
          )}
        </div>

        {/* Zgloszony klan: odnosnik do jego strony (wejscie administratora zostaje w logu) albo informacja, ze go juz nie ma */}
        {report.context === 'CLAN' && (
          <div className="mt-2 small">
            {report.clanId
              ? <Link to={`/klany/${report.clanId}`}>{t('reports.openClan')}</Link>
              : <span className="text-body-secondary">{t('reports.clanGone')}</span>}
          </div>
        )}

        {/* Post prowadzi wprost do tresci - administrator nie musi go szukac */}
        {report.postId && (
          <div className="mt-2">
            <Link to={`/post/${report.postId}${report.commentId ? `?komentarz=${report.commentId}` : ''}`} className="small">
              {report.commentId ? t('reports.openComment') : t('reports.openPost')}
            </Link>
          </div>
        )}

        <div className="mt-3">
          <Button variant="outline-secondary" size="sm" onClick={toggle}>
            {expanded ? t('reports.hideDetails') : t('reports.showDetails')}
          </Button>
        </div>

        {expanded && (
          <div className="mt-3">
            {report.context === 'CONVERSATION' && (
              <p className="text-body-secondary small">{t('reports.evidenceHint')}</p>
            )}
            {report.context === 'CLAN' && (
              <p className="text-body-secondary small">{t('reports.evidenceHintClan')}</p>
            )}

            {evidence.length === 0 ? (
              <p className="text-body-secondary small mb-0">{t('reports.noEvidence')}</p>
            ) : (
              <div className="evidence">
                {evidence.map((line, index) => (
                  <div
                    key={`${line.author}-${index}`}
                    className={`evidence-line${
                      line.author === report.reportedUsername ? ' is-reported' : ''}`}
                  >
                    <span className="evidence-author">{line.author}</span>
                    <span className="evidence-text">{line.text}</span>
                    {line.sentAt && (
                      <span className="evidence-time">
                        {formatDateTime(line.sentAt, language)}
                      </span>
                    )}
                  </div>
                ))}
              </div>
            )}

            {!open && (
              <div className="mt-3 small">
                <div className="fw-semibold">
                  {t('reports.decisionBy', {
                    username: report.resolvedBy,
                    when: formatDateTime(report.resolvedAt, language),
                  })}
                </div>
                <div className="text-body-secondary">{report.resolutionNote}</div>

                {/* Decyzje da sie zmienic. */}
                <div className="mt-2">
                  <Button variant="outline-secondary" size="sm" onClick={reopen}>
                    {t('reports.reopen')}
                  </Button>
                  <span className="ms-2 text-body-secondary">{t('reports.reopenHint')}</span>
                </div>
              </div>
            )}
          </div>
        )}

        {open && (
          <div className="mt-3 report-decision">
            {error && <Alert variant="danger" className="py-2">{error}</Alert>}

            <Form.Label htmlFor={`note-${report.id}`} className="small fw-semibold">
              {t('reports.note')}
            </Form.Label>
            <Form.Control
              id={`note-${report.id}`}
              as="textarea"
              rows={2}
              value={note}
              onChange={(e) => setNote(e.target.value)}
              placeholder={t('reports.notePlaceholder')}
              className="mb-2"
            />
            <p className="text-body-secondary small">{t('reports.noteHint')}</p>

            {/* Dzialanie wybieramy TUTAJ, razem z decyzja - a nie osobno w panelu kont. */}
            <div className="d-flex gap-2 flex-wrap align-items-end mb-3">
              <div style={{ minWidth: '15rem' }}>
                <Form.Label htmlFor={`action-${report.id}`} className="small fw-semibold">
                  {t('reports.action')}
                </Form.Label>
                <Form.Select
                  id={`action-${report.id}`}
                  size="sm"
                  value={action}
                  onChange={(e) => setAction(e.target.value)}
                >
                  {actions.map((code) => (
                    <option key={code} value={code}>{t(`reports.actions.${code}`)}</option>
                  ))}
                </Form.Select>
              </div>

              {/* Czas trwania pokazuje sie tylko wtedy, gdy cokolwiek znaczy */}
              {needsDuration && (
                <div style={{ minWidth: '11rem' }}>
                  <Form.Label htmlFor={`duration-${report.id}`} className="small fw-semibold">
                    {t('reports.duration')}
                  </Form.Label>
                  <Form.Select
                    id={`duration-${report.id}`}
                    size="sm"
                    value={duration}
                    onChange={(e) => setDuration(e.target.value)}
                  >
                    {OKRESY_KARY.map((h) => (
                      <option key={h} value={h}>{t('users.banFor', { count: h })}</option>
                    ))}
                    <option value={BEZTERMINOWO}>{t('users.banForever')}</option>
                  </Form.Select>
                </div>
              )}
            </div>

            {aboutMe && (
              <Alert variant="secondary" className="py-2 small">
                {t('reports.selfReportHint')}
              </Alert>
            )}

            {action === 'DELETE_ACCOUNT' && (
              <Alert variant="danger" className="py-2 small">
                {t('reports.deleteAccountWarning')}
              </Alert>
            )}

            <div className="d-flex gap-2 flex-wrap">
              <Button
                variant="success"
                size="sm"
                disabled={sending || !note.trim()}
                onClick={() => decide('RESOLVED')}
              >
                <IconCheckCircle className="me-1" /> {t('reports.resolve')}
              </Button>
              <Button
                variant="outline-secondary"
                size="sm"
                disabled={sending || !note.trim()}
                onClick={() => decide('DISMISSED')}
              >
                <IconFlag className="me-1" /> {t('reports.dismiss')}
              </Button>

              {/* Skrot do panelu kont. */}
              <Link to="/users" className="btn btn-outline-danger btn-sm">
                {t('reports.goToPanel')}
              </Link>
            </div>
          </div>
        )}
      </Card.Body>
    </Card>
  );
}
