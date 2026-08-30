import { useCallback, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import Alert from 'react-bootstrap/Alert';
import Badge from 'react-bootstrap/Badge';
import Card from 'react-bootstrap/Card';
import Col from 'react-bootstrap/Col';
import Row from 'react-bootstrap/Row';
import { describeError } from '../api/client';
import { mojeZgloszenia } from '../api/moderacja';
import EmptyState from '../components/EmptyState';
import PostSkeleton from '../components/PostSkeleton';
import { IconFlag } from '../components/Icons';
import { formatDateTime, timeAgo } from '../utils/dates';

/** Wlasne zgloszenia uzytkownika razem z decyzja administratora. */
export default function MojeZgloszeniaPage() {
  const { t, i18n } = useTranslation();

  const [reports, setReports] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await mojeZgloszenia();
      setReports(data.content);
    } catch (problem) {
      setError(describeError(problem, 'myReports.loadFailed').message);
      setReports([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  return (
    <Row className="justify-content-center">
      <Col lg={8} className="tiles-in">
        <h1 className="h4 page-title mb-2">{t('myReports.title')}</h1>
        <p className="text-body-secondary small">{t('myReports.intro')}</p>

        {error && <Alert variant="danger">{error}</Alert>}

        {loading && <PostSkeleton count={2} />}

        {!loading && reports.length === 0 && !error && (
          <EmptyState
            icon={IconFlag}
            title={t('myReports.empty')}
            text={t('myReports.emptyHint')}
          />
        )}

        {reports.map((report) => (
          <Card key={report.id} className="mb-3">
            <Card.Body>
              <div className="d-flex align-items-start justify-content-between gap-2 flex-wrap mb-2">
                <div>
                  <span className="fw-semibold">{t('myReports.reported')}: </span>
                  {report.reportedUsername}
                  <div className="text-body-secondary small">
                    {t(`reports.reasons.${report.reason}`)}
                    {' · '}
                    {t('myReports.filed')} {timeAgo(report.createdAt, i18n.language)}
                  </div>
                </div>

                <Stan status={report.status} />
              </div>

              {report.status === 'OPEN' ? (
                <p className="text-body-secondary small mb-0">{t('myReports.openHint')}</p>
              ) : (
                <>
                  <p className="fw-semibold small mb-1">{t('myReports.note')}</p>
                  <p className="mb-2" style={{ whiteSpace: 'pre-wrap' }}>
                    {report.resolutionNote}
                  </p>
                  <p className="text-body-secondary small mb-0">
                    {t('myReports.resolvedAt')}: {formatDateTime(report.resolvedAt, i18n.language)}
                    {' · '}
                    {t('myReports.noPunishmentInfo')}
                  </p>
                </>
              )}
            </Card.Body>
          </Card>
        ))}
      </Col>
    </Row>
  );
}

/** Trzy stany sprawy, kazdy w swoim kolorze. */
function Stan({ status }) {
  const { t } = useTranslation();

  if (status === 'OPEN') {
    return (
      <Badge bg="warning-subtle" text="warning-emphasis">{t('myReports.open')}</Badge>
    );
  }
  return status === 'RESOLVED'
    ? <Badge bg="success">{t('myReports.resolved')}</Badge>
    : <Badge bg="secondary">{t('myReports.dismissed')}</Badge>;
}
