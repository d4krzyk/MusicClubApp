/** Szkielet poziomego paska z ludzmi - propozycje znajomych i lista znajomych. */
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
