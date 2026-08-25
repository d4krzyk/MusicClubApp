import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import Carousel from 'react-bootstrap/Carousel';
import Modal from 'react-bootstrap/Modal';
import Row from 'react-bootstrap/Row';
import Col from 'react-bootstrap/Col';

/** Powyzej tylu zdjec pokazujemy karuzele zamiast siatki miniatur. */
const PROG_KARUZELI = 4;

/**
 * Zdjecia posta.
 *
 * <p><b>Dwa tryby wyswietlania</b> - o to prosil pomysl na aplikacje:</p>
 * <ul>
 *   <li><b>do {@value #PROG_KARUZELI} zdjec</b> - siatka miniatur; wszystko
 *       widac naraz, bez klikania,</li>
 *   <li><b>wiecej</b> - karuzela, ktora zajmuje tyle miejsca co jedno zdjecie.
 *       Bez tego post z dwunastoma zdjeciami rozpychalby cala tablice
 *       i przewijanie do nastepnego wpisu trwaloby wieki.</li>
 * </ul>
 *
 * <p>Klikniecie dowolnego zdjecia otwiera je w powiekszeniu.</p>
 */
export default function GaleriaZdjec({ adresy, autor }) {
  const { t } = useTranslation();
  const [powiekszone, setPowiekszone] = useState(null);

  if (!adresy || adresy.length === 0) {
    return null;
  }

  const karuzela = adresy.length > PROG_KARUZELI;

  return (
    <>
      {karuzela ? (
        <>
          <Carousel
            interval={null}
            className="rounded overflow-hidden border"
            aria-label={t('posts.gallery', { count: adresy.length })}
          >
            {adresy.map((adres, i) => (
              <Carousel.Item key={adres}>
                <img
                  src={adres}
                  alt={`${autor} ${i + 1}/${adresy.length}`}
                  className="d-block w-100 karuzela-obraz"
                  onClick={() => setPowiekszone(adres)}
                  style={{ cursor: 'zoom-in' }}
                />
              </Carousel.Item>
            ))}
          </Carousel>
          <div className="text-body-secondary small mt-1">
            {t('posts.gallery', { count: adresy.length })}
          </div>
        </>
      ) : (
        <Row className="g-2">
          {adresy.map((adres, i) => (
            // Przy 1 zdjeciu cala szerokosc, przy 2-4 po dwa w rzedzie
            <Col key={adres} xs={adresy.length === 1 ? 12 : 6}>
              <img
                src={adres}
                alt={`${autor} ${i + 1}`}
                className="miniatura-postu rounded border"
                onClick={() => setPowiekszone(adres)}
              />
            </Col>
          ))}
        </Row>
      )}

      {/* Podglad w powiekszeniu */}
      <Modal show={Boolean(powiekszone)} onHide={() => setPowiekszone(null)} size="lg" centered>
        <Modal.Header closeButton closeLabel={t('common.close')} />
        <Modal.Body className="p-0 text-center bg-black">
          {powiekszone && (
            <img src={powiekszone} alt={autor} className="img-fluid" style={{ maxHeight: '80vh' }} />
          )}
        </Modal.Body>
      </Modal>
    </>
  );
}
