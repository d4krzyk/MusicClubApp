/**
 * Puste miejsce, ktore cos MOWI.
 *
 * <p><b>Po co osobny komponent na jedno zdanie.</b> Bo wczesniej to nie bylo
 * jedno zdanie, tylko cztery rozne: "Nie ma jeszcze zadnych postow", "Brak
 * znajomych", "Nikogo nie znaleziono", "Nic nowego". Kazde napisane innym
 * stylem i kazde konczace rozmowe z uzytkownikiem w miejscu, w ktorym ma on
 * najwiecej pytan.</p>
 *
 * <p><b>Puste miejsce to nie awaria, tylko poczatek.</b> Nowe konto widzi
 * pusto wszedzie - i wlasnie wtedy aplikacja ma jedyna okazje powiedziec,
 * co dalej. Dlatego kazdy pusty stan sklada sie z trzech czesci: co tu
 * bedzie, dlaczego jeszcze tego nie ma i <b>jedno konkretne dzialanie</b>.</p>
 *
 * @param icon    ikona (komponent z {@code Icons.jsx})
 * @param title   krotkie zdanie - co tu bedzie
 * @param text    wyjasnienie - dlaczego jeszcze pusto
 * @param action  opcjonalny przycisk albo link (przekazany jako element)
 */
export default function EmptyState({ icon: Icon, title, text, action, className = '' }) {
  return (
    <div className={`empty-state ${className}`}>
      {Icon && (
        <span className="empty-state-icon" aria-hidden="true">
          <Icon size={28} />
        </span>
      )}

      <p className="empty-state-title">{title}</p>
      {text && <p className="empty-state-text">{text}</p>}
      {action && <div className="empty-state-action">{action}</div>}
    </div>
  );
}
