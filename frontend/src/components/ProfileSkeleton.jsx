import Card from 'react-bootstrap/Card';

/**
 * Szkielet profilu - szare ksztalty w miejscu, gdzie za chwile bedzie tresc.
 *
 * <p><b>Co bylo wczesniej.</b> Cala strona profilu czekala na jedno kolko
 * z napisem „Ładowanie…", a potem tresc pojawiala sie naraz. Wygladalo to
 * inaczej niz tablica, ktora wchodzi kafelkami - i ta niekonsekwencja bila
 * po oczach przy kazdym przejsciu z tablicy na profil.</p>
 *
 * <p><b>Dlaczego szkielet, a nie samo kolko.</b> Kolko mowi tylko „czekaj".
 * Szkielet od razu zajmuje <b>to samo miejsce</b> co gotowa strona, wiec po
 * wczytaniu nic nie podskakuje, a ksztalty z gory mowia, czego sie
 * spodziewac: naglowek z awatarem, sekcje, potem posty.</p>
 *
 * <p>Ma {@code aria-hidden}, bo dla czytnika ekranu to same puste prostokaty -
 * czytanie ich na glos byloby halasem zamiast informacji.</p>
 */
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

      {/*
        Trzy bloki sekcji: „Co Was łączy", ulubieni, playlisty. Nie
        odwzorowujemy ich co do piksela - chodzi o to, zeby strona miala
        juz swoj ksztalt, a nie zeby udawac gotowa tresc.
      */}
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
