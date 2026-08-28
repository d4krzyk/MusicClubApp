import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { AuthProvider } from './auth/AuthContext';
import { ChatProvider } from './chat/ChatContext';
import { RequireAdmin, RequireAnonymous, RequireAuth } from './auth/RouteGuards';
import Layout from './components/Layout';
import LoginPage from './pages/LoginPage';
import RegisterPage from './pages/RegisterPage';
import FeedPage from './pages/FeedPage';
import PostPage from './pages/PostPage';
import FriendsPage from './pages/FriendsPage';
import ProfilePage from './pages/ProfilePage';
import SettingsPage from './pages/SettingsPage';
import UsersPage from './pages/UsersPage';
import ReportsPage from './pages/ReportsPage';

/**
 * Mapa adresow aplikacji.
 *
 * <p>Kazda sciezka jest owinieta straznikiem, ktory decyduje, kto moze ja
 * zobaczyc. Dzieki temu regula "trzeba byc zalogowanym" jest w JEDNYM
 * miejscu, a nie sprawdzana osobno w kazdym komponencie.</p>
 */
export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        {/*
          Czat obejmuje CALA aplikacje, bo otwiera go kilka miejsc naraz:
          ikona w pasku, przycisk "Napisz" na profilu znajomego. Gdyby stan
          siedzial w samym panelu, kazde z tych miejsc musialoby przekazywac
          "otworz rozmowe z ta osoba" przez wszystkie komponenty po drodze.
        */}
        <ChatProvider>
          <Layout>
            <Routes>
              {/*
                Strona glowna TO tablica. Wczesniej byla tu osobna strona
                powitalna z zapowiedzia kolejnych krokow - a po wejsciu do
                aplikacji chce sie zobaczyc, co nowego u innych, a nie wlasny
                adres e-mail.
              */}
              <Route
                path="/"
                element={
                  <RequireAuth>
                    <FeedPage />
                  </RequireAuth>
                }
              />

              {/*
                Pojedynczy post pod wlasnym adresem - tu prowadza powiadomienia
                o reakcjach i taki link da sie komus wyslac.
              */}
              <Route
                path="/post/:id"
                element={
                  <RequireAuth>
                    <PostPage />
                  </RequireAuth>
                }
              />

              {/*
                Stary adres tablicy zostaje jako przekierowanie. Ktos moze go
                miec w zakladkach albo w wyslanym komus linku - pusta strona
                bylaby tu najgorsza mozliwa odpowiedzia.
              */}
              <Route path="/feed" element={<Navigate to="/" replace />} />
              {/*
                Dwie sciezki, jedna strona: /profil to skrot do wlasnego profilu
                (wygodny link z menu), /profil/:username to czyjs profil.
                Rozne adresy sa wazne - dzieki nim da sie wyslac komus link
                do konkretnego profilu.
              */}
              <Route
                path="/profil"
                element={
                  <RequireAuth>
                    <ProfilePage />
                  </RequireAuth>
                }
              />
              <Route
                path="/profil/:username"
                element={
                  <RequireAuth>
                    <ProfilePage />
                  </RequireAuth>
                }
              />
              <Route
                path="/znajomi"
                element={
                  <RequireAuth>
                    <FriendsPage />
                  </RequireAuth>
                }
              />
              <Route
                path="/settings"
                element={
                  <RequireAuth>
                    <SettingsPage />
                  </RequireAuth>
                }
              />

              {/* Lista wszystkich kont - tylko administrator */}
              <Route
                path="/users"
                element={
                  <RequireAdmin>
                    <UsersPage />
                  </RequireAdmin>
                }
              />

              {/*
                Panel zgloszen. Osobna strona od panelu kont, bo to dwie rozne
                prace: tu sie CZYTA i decyduje, tam DZIALA. Powiadomienia
                o zgloszeniach prowadza wprost tutaj.
              */}
              <Route
                path="/zgloszenia"
                element={
                  <RequireAdmin>
                    <ReportsPage />
                  </RequireAdmin>
                }
              />
              <Route
                path="/login"
                element={
                  <RequireAnonymous>
                    <LoginPage />
                  </RequireAnonymous>
                }
              />
              <Route
                path="/register"
                element={
                  <RequireAnonymous>
                    <RegisterPage />
                  </RequireAnonymous>
                }
              />

              {/* Nieznany adres - zamiast pustej strony wracamy na glowna */}
              <Route path="*" element={<Navigate to="/" replace />} />
            </Routes>
          </Layout>
        </ChatProvider>
      </AuthProvider>
    </BrowserRouter>
  );
}
