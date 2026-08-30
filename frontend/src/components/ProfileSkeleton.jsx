import Card from 'react-bootstrap/Card';

/** Szkielet profilu - szare ksztalty w miejscu, gdzie za chwile bedzie tresc. */
export default function ProfileSkeleton() {
  return (
    <div className="profile-skeleton tiles-in" aria-hidden="true">
      {/* Naglowek: awatar, login, dwie linijki drobnych danych */}
      <Card className="mb-4">
        <Card.Body className="d-flex align-items-center gap-3">
          <span className="skeleton skeleton-avatar-lg" />
          <div className="flex-grow-1">
            <span className="skeleton skeleton-line" style={{ width: '35%' }} />
            <span className="skeleton skeleton-line skeleton-line-sm" style={{ width: '55%' }} />
            <span className="skeleton skeleton-line skeleton-line-sm" style={{ width: '45%' }} />
          </div>
        </Card.Body>
      </Card>

      {/* Trzy bloki sekcji: „Co Was łączy", ulubieni, playlisty. */}
      {[0, 1, 2].map((i) => (
        <Card key={i} className="mb-3">
          <Card.Body>
            <span className="skeleton skeleton-line" style={{ width: '28%' }} />
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
