/**
 * Zdjecie profilowe uzytkownika.
 *
 * <p>Gdy ktos nie wgral zdjecia, pokazujemy kolowy zastepnik z pierwsza litera
 * loginu. Dzieki temu lista postow wyglada rowno, zamiast miec dziury tam,
 * gdzie brakuje obrazka.</p>
 *
 * @param avatarUrl adres zdjecia albo {@code null}
 * @param username  login - z niego bierzemy litere do zastepnika
 * @param rozmiar   bok kola w pikselach
 */
export default function Avatar({ avatarUrl, username, rozmiar = 40 }) {
  const styl = { width: rozmiar, height: rozmiar };

  if (avatarUrl) {
    return (
      <img
        src={avatarUrl}
        alt={username}
        className="avatar"
        style={styl}
        /*
         * Gdy plik zniknie z serwera, przegladarka pokazalaby ikonke
         * "zepsuty obrazek". Chowamy go wtedy - lepiej pusto niz brzydko.
         */
        onError={(e) => {
          e.currentTarget.style.visibility = 'hidden';
        }}
      />
    );
  }

  return (
    <span
      className="avatar-zastepnik"
      style={{ ...styl, fontSize: rozmiar * 0.45 }}
      aria-hidden="true"
    >
      {username?.charAt(0) ?? '?'}
    </span>
  );
}
