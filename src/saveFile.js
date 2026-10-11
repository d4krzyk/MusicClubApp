import { Capacitor } from '@capacitor/core';
import { Filesystem, Directory, Encoding } from '@capacitor/filesystem';
import { Share } from '@capacitor/share';

// Zapis pliku dla uzytkownika (kalendarz .ics, kopia ustawien). W przegladarce zwykle pobranie; w aplikacji na Androidzie
// WebView nie pobiera blobow, wiec plik trafia do pamieci podrecznej i otwiera sie systemowe "Udostepnij / Zapisz".
export async function saveFile(nazwa, mime, tekst) {
  if (Capacitor.isNativePlatform()) {
    const zapis = await Filesystem.writeFile({ path: nazwa, data: tekst, directory: Directory.Cache, encoding: Encoding.UTF8 });
    await Share.share({ title: nazwa, url: zapis.uri, dialogTitle: nazwa });
    return;
  }
  const a = document.createElement('a');
  a.href = URL.createObjectURL(new Blob([tekst], { type: `${mime};charset=utf-8` }));
  a.download = nazwa;
  a.click();
  setTimeout(() => URL.revokeObjectURL(a.href), 1000);
}
