/** Zdjecie profilowe uzytkownika. */
export default function Avatar({ avatarUrl, username, size = 40 }) {
  const styl = { width: size, height: size };

  if (avatarUrl) {
    return (
      <img
        src={avatarUrl}
        alt={username}
        className="avatar"
        style={styl}
        /* Gdy plik zniknie z serwera, przegladarka pokazalaby ikonke "zepsuty obrazek". */
        onError={(e) => {
          e.currentTarget.style.visibility = 'hidden';
        }}
      />
    );
  }

  return (
    <span
      className="avatar-placeholder"
      style={{ ...styl, fontSize: size * 0.45 }}
      aria-hidden="true"
    >
      {username?.charAt(0) ?? '?'}
    </span>
  );
}
