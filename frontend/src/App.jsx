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
import MojeZgloszeniaPage from './pages/MojeZgloszeniaPage';

/** Mapa adresow aplikacji. */
export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        {/*
          Czat obejmuje CALA aplikacje, bo otwiera go kilka miejsc naraz: ikona w pasku, przycisk
          "Napisz" na profilu znajomego.
        */}
        <ChatProvider>
          <Layout>
            <Routes>
              {/* Strona glowna TO tablica. */}
              <Route
                path="/"
                element={
                  <RequireAuth>
                    <FeedPage />
                  </RequireAuth>
                }
              />

              {/*
                Pojedynczy post pod wlasnym adresem - tu prowadza powiadomienia o reakcjach i taki
                link da sie komus wyslac.
              */}
              <Route
                path="/post/:id"
                element={
                  <RequireAuth>
                    <PostPage />
                  </RequireAuth>
                }
              />

              {/* Stary adres tablicy zostaje jako przekierowanie. */}
              <Route path="/feed" element={<Navigate to="/" replace />} />
              {/*
                Dwie sciezki, jedna strona: /profil to skrot do wlasnego profilu (wygodny link z
                menu), /profil/:username to czyjs profil.
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
              {/* Wlasne zgloszenia - kazdy zalogowany widzi tylko swoje */}
              <Route
                path="/moje-zgloszenia"
                element={
                  <RequireAuth>
                    <MojeZgloszeniaPage />
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

              {/* Panel zgloszen. */}
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
