/** Ile miejsca na ekranie potrzebuje odtwarzacz danego serwisu. */
export function playerHeight(provider, kind) {
  if (provider === 'YOUTUBE') {
    return null;                       // proporcja 16:9
  }
  if (provider === 'APPLE_MUSIC') {
    return kind === 'TRACK' ? 175 : 450;
  }
  return kind === 'TRACK' ? 152 : 352; // Spotify
}
