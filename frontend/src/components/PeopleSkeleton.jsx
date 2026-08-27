/**
 * Szkielet poziomego paska z ludzmi - propozycje znajomych i lista znajomych.
 *
 * <p><b>Skad sie wzial.</b> Oba paski pokazywaly w czasie ladowania jedna
 * linijke z kolkiem ("Ladowanie…"), a potem podmienialy ja na rzad kafelkow
 * wysokich na kilkanascie razy tyle. Cala strona podskakiwala w tym momencie
 * dwa razy - raz przy propozycjach, raz przy znajomych - i wygladalo to jak
 * drganie interfejsu, a nie jak ladowanie.</p>
 *
 * <p>Szkielet zajmuje <b>dokladnie tyle miejsca, co gotowy pasek</b>, wiec
 * wstawienie prawdziwych kafelkow nie zmienia juz ukladu strony.</p>
 *
 * @param count ile kafelkow narysowac
 * @param variant "suggestion" (karta z przyciskiem) albo "friend" (sam awatar)
 */
export default function PeopleSkeleton({ count = 5, variant = 'friend' }) {
  const suggestion = variant === 'suggestion';

  return (
    <div className="people-skeleton" aria-hidden="true">
      {Array.from({ length: count }, (unused, i) => (
        <div
          key={i}
          className={suggestion ? 'suggestion-card' : 'friend-card'}
          /* Kafelki rozjasniaja sie po kolei - tak samo jak wchodza posty */
          style={{ '--i': i }}
        >
          <span
            className="skeleton skeleton-avatar"
            style={{ width: suggestion ? 56 : 64, height: suggestion ? 56 : 64 }}
          />
          <span className="skeleton skeleton-line" style={{ width: '70%' }} />
          {suggestion && (
            <>
              <span className="skeleton skeleton-line skeleton-line-sm" style={{ width: '90%' }} />
              <span className="skeleton skeleton-pill" style={{ width: '100%' }} />
            </>
          )}
        </div>
      ))}
    </div>
  );
}
