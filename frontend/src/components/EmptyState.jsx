/** Puste miejsce, ktore cos MOWI. */
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
