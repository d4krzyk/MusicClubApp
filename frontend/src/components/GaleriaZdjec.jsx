import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import Carousel from 'react-bootstrap/Carousel';
import Modal from 'react-bootstrap/Modal';
import Button from 'react-bootstrap/Button';
import Row from 'react-bootstrap/Row';
import Col from 'react-bootstrap/Col';

/** Powyzej tylu zdjec pokazujemy karuzele zamiast siatki miniatur. */
const PROG_KARUZELI = 4;

/**
 * Zdjecia posta.
 *
 * <p><b>Dwa tryby wyswietlania:</b></p>
 * <ul>
 *   <li><b>do 4 zdjec</b> - siatka miniatur; wszystko widac naraz,</li>
 *   <li><b>wiecej</b> - karuzela, ktora zajmuje tyle miejsca co jedno zdjecie.
 *       Bez tego post z dwunastoma zdjeciami rozpychalby cala tablice.</li>
 * </ul>
 *
 * <p><b>Sterowanie pod zdjeciem, nie na nim.</b> Wbudowane strzalki Bootstrapa
 * sa biale i lezą NA zdjeciu - na jasnym obrazku po prostu znikaja. Dlatego
 * dokladamy pasek pod karuzela: strzalki na tle przycisku, licznik "3 / 6"
 * i kropki. Wbudowane strzalki zostawiamy, bo na ciemnych zdjeciach sa wygodne.</p>
 */
export default function GaleriaZdjec({ adresy, autor }) {
  const { t } = useTranslation();
  const [powiekszone, setPowiekszone] = useState(null);
  const [aktywne, setAktywne] = useState(0);

  if (!adresy || adresy.length === 0) {
    return null;
  }

  const karuzela = adresy.length > PROG_KARUZELI;

  // Modulo pozwala przewijac w kolko - z ostatniego zdjecia wracamy na pierwsze
  const przesun = (o) => setAktywne((i) => (i + o + adresy.length) % adresy.length);

  return (
    <>
      {karuzela ? (
        <>
          <Carousel
            activeIndex={aktywne}
            onSelect={setAktywne}
            interval={null}
            indicators={false}
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

          {/* Pasek sterowania POD zdjeciem - zawsze widoczny */}
          <div className="d-flex align-items-center justify-content-center gap-3 mt-2">
            <Button
              variant="outline-secondary"
              size="sm"
              onClick={() => przesun(-1)}
              aria-label={t('users.previous')}
            >
              ‹
            </Button>

            <div className="d-flex align-items-center gap-2">
              {/* Kropki - klikalne, pokazuja ile jest zdjec i ktore ogladamy */}
              {adresy.map((adres, i) => (
                <button
                  key={adres}
                  type="button"
                  className={`kropka-galerii ${i === aktywne ? 'aktywna' : ''}`}
                  onClick={() => setAktywne(i)}
                  aria-label={`${i + 1} / ${adresy.length}`}
                  aria-current={i === aktywne}
                />
              ))}
            </div>

            <Button
              variant="outline-secondary"
              size="sm"
              onClick={() => przesun(1)}
              aria-label={t('users.next')}
            >
              ›
            </Button>

            <span className="text-body-secondary small ms-1">
              {aktywne + 1} / {adresy.length}
            </span>
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
