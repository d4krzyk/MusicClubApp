import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import Carousel from 'react-bootstrap/Carousel';
import Modal from 'react-bootstrap/Modal';
import Button from 'react-bootstrap/Button';
import Row from 'react-bootstrap/Row';
import Col from 'react-bootstrap/Col';

/** Powyzej tylu zdjec pokazujemy karuzele zamiast siatki miniatur. */
const CAROUSEL_THRESHOLD = 4;

/** Zdjecia posta. */
export default function ImageGallery({ urls, author }) {
  const { t } = useTranslation();
  const [enlarged, setEnlarged] = useState(null);
  const [activeIndex, setActiveIndex] = useState(0);

  if (!urls || urls.length === 0) {
    return null;
  }

  const carousel = urls.length > CAROUSEL_THRESHOLD;

  // Modulo pozwala przewijac w kolko - z ostatniego zdjecia wracamy na pierwsze
  const scrollBy = (o) => setActiveIndex((i) => (i + o + urls.length) % urls.length);

  return (
    <>
      {carousel ? (
        <>
          <Carousel
            activeIndex={activeIndex}
            onSelect={setActiveIndex}
            interval={null}
            indicators={false}
            className="rounded overflow-hidden border"
            aria-label={t('posts.gallery', { count: urls.length })}
          >
            {urls.map((url, i) => (
              <Carousel.Item key={url}>
                <img
                  src={url}
                  alt={`${author} ${i + 1}/${urls.length}`}
                  className="d-block w-100 carousel-image"
                  onClick={() => setEnlarged(url)}
                  style={{ cursor: 'zoom-in' }}
                />
              </Carousel.Item>
            ))}
          </Carousel>

          {/* Pasek sterowania POD zdjeciem - zawsze widoczny */}
          <div className="d-flex align-items-center justify-content-center gap-3 mt-2">
            <Button
              variant="outline-secondary"
              size="sm"
              onClick={() => scrollBy(-1)}
              aria-label={t('users.previous')}
            >
              ‹
            </Button>

            <div className="d-flex align-items-center gap-2">
              {/* Kropki - klikalne, pokazuja ile jest zdjec i ktore ogladamy */}
              {urls.map((url, i) => (
                <button
                  key={url}
                  type="button"
                  className={`gallery-dot ${i === activeIndex ? 'is-active' : ''}`}
                  onClick={() => setActiveIndex(i)}
                  aria-label={`${i + 1} / ${urls.length}`}
                  aria-current={i === activeIndex}
                />
              ))}
            </div>

            <Button
              variant="outline-secondary"
              size="sm"
              onClick={() => scrollBy(1)}
              aria-label={t('users.next')}
            >
              ›
            </Button>

            <span className="text-body-secondary small ms-1">
              {activeIndex + 1} / {urls.length}
            </span>
          </div>
        </>
      ) : (
        <Row className="g-2">
          {urls.map((url, i) => (
            // Przy 1 zdjeciu cala szerokosc, przy 2-4 po dwa w rzedzie
            <Col key={url} xs={urls.length === 1 ? 12 : 6}>
              <img
                src={url}
                alt={`${author} ${i + 1}`}
                className="post-thumb rounded border"
                onClick={() => setEnlarged(url)}
              />
            </Col>
          ))}
        </Row>
      )}

      {/* Podglad w powiekszeniu */}
      <Modal show={Boolean(enlarged)} onHide={() => setEnlarged(null)} size="lg" centered>
        <Modal.Header closeButton closeLabel={t('common.close')} />
        <Modal.Body className="p-0 text-center bg-black">
          {enlarged && (
            <img src={enlarged} alt={author} className="img-fluid" style={{ maxHeight: '80vh' }} />
          )}
        </Modal.Body>
      </Modal>
    </>
  );
}
