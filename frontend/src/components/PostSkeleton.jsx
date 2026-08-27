import Card from 'react-bootstrap/Card';

/**
 * Szkielet posta - szare ksztalty w miejscu, gdzie za chwile bedzie tresc.
 *
 * <p><b>Dlaczego to jest lepsze niz kreccace sie kolko.</b> Kolko mowi
 * "czekaj" i nic wiecej; strona pozostaje pusta, a po wczytaniu tresc
 * wskakuje i przesuwa wszystko w dol. Szkielet od razu zajmuje <i>to samo
 * miejsce</i>, co prawdziwe posty: nic nie skacze, a ksztalt bloczkow mowi,
 * czego sie spodziewac.</p>
 *
 * <p><b>Pokazujemy go tylko przy PIERWSZYM ladowaniu.</b> Przy doladowywaniu
 * kolejnych postow na dole jest juz co ogladac, wiec wystarcza trzy kropki -
 * pol ekranu szarych prostokatow wygladaloby wtedy jak awaria.</p>
 *
 * @param count ile szkieletow narysowac
 */
export default function PostSkeleton({ count = 3 }) {
  return (
    <div aria-hidden="true">
      {Array.from({ length: count }, (unused, i) => (
        <Card key={i} className="mb-3 post-card skeleton-card" style={{ '--i': i }}>
          <Card.Body>
            <div className="d-flex align-items-center gap-2 mb-3">
              <span className="skeleton skeleton-avatar" />
              <div className="flex-grow-1">
                <span className="skeleton skeleton-line" style={{ width: '32%' }} />
                <span className="skeleton skeleton-line skeleton-line-sm" style={{ width: '20%' }} />
              </div>
            </div>

            <span className="skeleton skeleton-line" style={{ width: '92%' }} />
            <span className="skeleton skeleton-line" style={{ width: '78%' }} />

            {/* Co drugi szkielet z "odtwarzaczem" - tablica tez nie jest jednolita */}
            {i % 2 === 1 && <span className="skeleton skeleton-player" />}

            <div className="d-flex gap-2 mt-3">
              <span className="skeleton skeleton-pill" />
              <span className="skeleton skeleton-pill" />
              <span className="skeleton skeleton-pill" />
            </div>
          </Card.Body>
        </Card>
      ))}
    </div>
  );
}
