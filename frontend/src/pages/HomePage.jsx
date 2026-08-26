import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import Card from 'react-bootstrap/Card';
import Row from 'react-bootstrap/Row';
import Col from 'react-bootstrap/Col';
import { useAuth } from '../auth/AuthContext';
import Avatar from '../components/Avatar';
import { formatDate } from '../utils/dates';

export default function HomePage() {
  const { t, i18n } = useTranslation();
  const { user } = useAuth();

  return (
    <Row className="justify-content-center">
      <Col lg={8}>
        <Card>
          <Card.Body className="p-4">
            <div className="d-flex align-items-center gap-3 mb-4">
              <Avatar avatarUrl={user.avatarUrl} username={user.username} size={72} />
              <div>
                <Card.Title as="h1" className="h4 mb-1">
                  {t('home.welcome', { username: user.username })}
                </Card.Title>
                <div className="text-body-secondary small">
                  {t('home.memberSince')}: {formatDate(user.createdAt, i18n.language)}
                </div>
              </div>
            </div>

            {/*
              Celowo NIE pokazujemy tu roli. Napis "USER" nic zwyklemu
              uzytkownikowi nie mowi, a tylko ujawnia, ze aplikacja dzieli
              konta na rodzaje. Backend zreszta w ogole nie przysyla juz
              tego pola - patrz UserResponse.
            */}
            <h2 className="h6 text-body-secondary text-uppercase">{t('home.yourAccount')}</h2>
            <dl className="row mb-4">
              <dt className="col-sm-3 fw-normal text-body-secondary">{t('home.email')}</dt>
              <dd className="col-sm-9">{user.email}</dd>
            </dl>

            <div className="d-flex flex-wrap gap-3">
              <Link to="/feed">{t('home.goToFeed')}</Link>
              <Link to="/settings">{t('home.goToSettings')}</Link>
            </div>

            <h2 className="h6 text-body-secondary text-uppercase mt-4">{t('home.nextSteps')}</h2>
            <p className="text-body-secondary mb-0">{t('home.nextStepsText')}</p>
          </Card.Body>
        </Card>
      </Col>
    </Row>
  );
}
