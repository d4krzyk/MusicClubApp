import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import Alert from 'react-bootstrap/Alert';
import Row from 'react-bootstrap/Row';
import Col from 'react-bootstrap/Col';
import Spinner from 'react-bootstrap/Spinner';
import client, { describeError } from '../api/client';
import Post from '../components/Post';
import { IconArrowLeft } from '../components/Icons';

/**
 * Jeden post na osobnej stronie.
 *
 * <p><b>Po co, skoro jest tablica.</b> Dla powiadomien: "ktos zareagowal na
 * Twoj post" ma prowadzic do <b>tego</b> wpisu, a nie na tablice, gdzie
 * moze byc setny od gory. Przy okazji daje adres, ktory da sie komus
 * wyslac.</p>
 */
export default function PostPage() {
  const { t } = useTranslation();
  const { id } = useParams();
  const navigate = useNavigate();

  const [post, setPost] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const { data } = await client.get(`/posts/${id}`);
      setPost(data);
    } catch (problem) {
      const details = describeError(problem);
      setError(details.message ?? (details.messageKey ? t(details.messageKey) : null));
    } finally {
      setLoading(false);
    }
  }, [id, t]);

  useEffect(() => {
    load();
  }, [load]);

  async function remove(postId) {
    if (!window.confirm(t('common.confirmDelete'))) {
      return;
    }
    await client.delete(`/posts/${postId}`);
    // Post juz nie istnieje, wiec zostanie na tej stronie nie ma sensu
    navigate('/', { replace: true });
  }

  return (
    <Row className="justify-content-center">
      <Col lg={8} className="feed-page">
        <Link to="/" className="btn btn-outline-secondary btn-sm mb-3">
          <IconArrowLeft /> {t('posts.backToFeed')}
        </Link>

        {loading && (
          <div className="text-center py-4 text-body-secondary">
            <Spinner animation="border" size="sm" className="me-2" />
            {t('common.loading')}
          </div>
        )}

        {error && <Alert variant="danger">{error}</Alert>}

        {!loading && !error && post && (
          <Post
            post={post}
            onDelete={remove}
            onUpdate={setPost}
            onReaction={setPost}
          />
        )}
      </Col>
    </Row>
  );
}
