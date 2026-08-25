import { useTranslation } from 'react-i18next';
import Card from 'react-bootstrap/Card';
import Button from 'react-bootstrap/Button';
import Badge from 'react-bootstrap/Badge';
import Avatar from './Avatar';
import GaleriaZdjec from './GaleriaZdjec';
import { sformatujDate } from '../utils/daty';
import { naMinuty } from '../utils/czas';

/**
 * Pojedynczy post na tablicy: autor, tresc, zdjecia i odtwarzacz Spotify.
 */
export default function Post({ post, onDelete }) {
  const { t, i18n } = useTranslation();

  return (
    <Card className="mb-3">
      <Card.Body>
        <div className="d-flex align-items-center gap-2 mb-3">
          <Avatar
            avatarUrl={post.authorAvatarUrl}
            username={post.authorUsername}
            rozmiar={40}
          />
          <div className="flex-grow-1">
            <div className="fw-semibold">{post.authorUsername}</div>
            <div className="text-body-secondary small">
              {sformatujDate(post.createdAt, i18n.language)}
            </div>
          </div>

          {/*
            Przycisk widac tylko wtedy, gdy backend przyslal canDelete=true.
            To pole wylicza serwer - przegladarka niczego tu nie decyduje.
          */}
          {post.canDelete && (
            <Button
              variant="outline-danger"
              size="sm"
              onClick={() => onDelete(post.id)}
              aria-label={t('common.delete')}
            >
              {t('common.delete')}
            </Button>
          )}
        </div>

        {/* tresc-postu zachowuje przejscia do nowej linii wpisane przez autora */}
        <Card.Text className="tresc-postu">{post.content}</Card.Text>

        {post.imageUrls.length > 0 && (
          <div className="mb-3">
            <GaleriaZdjec adresy={post.imageUrls} autor={post.authorUsername} />
          </div>
        )}

        {post.spotifyEmbedUrl && (
          <div>
            <iframe
              src={post.spotifyEmbedUrl}
              title={`Spotify - ${post.authorUsername}`}
              width="100%"
              height="152"
              frameBorder="0"
              allow="autoplay; clipboard-write; encrypted-media; fullscreen; picture-in-picture"
              loading="lazy"
              className="rounded"
            />

            {/*
              Wybrana sekunda dolatuje do adresu odtwarzacza, ale Spotify
              nie zawsze ja uwzglednia. Pokazujemy ja wiec takze jako etykiete -
              dzieki temu informacja "sluchaj od 1:23" nie przepada,
              nawet gdy odtwarzacz zacznie od poczatku.
            */}
            {post.spotifyStartSeconds > 0 && (
              <Badge bg="secondary" className="mt-1">
                {t('posts.startAtBadge', { time: naMinuty(post.spotifyStartSeconds) })}
              </Badge>
            )}
          </div>
        )}
      </Card.Body>
    </Card>
  );
}
