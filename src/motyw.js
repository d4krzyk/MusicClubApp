// Motyw: "auto" (jak system), "light", "dark". Zapis w localStorage, zmiana z krotkim przejsciem kolorow.
export function czytajMotyw() {
  try { return localStorage.getItem('motyw') ?? 'auto'; } catch { return 'auto'; }
}

export function aktualnyMotyw() {
  return document.documentElement.getAttribute('data-theme') ?? 'light';
}

export function ustawMotyw(m) {
  try { if (m === 'auto') localStorage.removeItem('motyw'); else localStorage.setItem('motyw', m); } catch { /* nic */ }
  const wybrany = m === 'auto' ? (window.matchMedia('(prefers-color-scheme: light)').matches ? 'light' : 'dark') : m;
  document.documentElement.classList.add('mc-zmiana-motywu');
  document.documentElement.setAttribute('data-theme', wybrany);
  setTimeout(() => document.documentElement.classList.remove('mc-zmiana-motywu'), 400);
  window.dispatchEvent(new CustomEvent('mc-motyw', { detail: m }));
}
