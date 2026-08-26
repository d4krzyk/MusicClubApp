import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { AuthProvider } from './auth/AuthContext';
import { RequireAdmin, RequireAnonymous, RequireAuth } from './auth/RouteGuards';
import Layout from './components/Layout';
import HomePage from './pages/HomePage';
import LoginPage from './pages/LoginPage';
import RegisterPage from './pages/RegisterPage';
import FeedPage from './pages/FeedPage';
import FriendsPage from './pages/FriendsPage';
import ProfilePage from './pages/ProfilePage';
import SettingsPage from './pages/SettingsPage';
import UsersPage from './pages/UsersPage';

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
        <Layout>
          <Routes>
            <Route
              path="/"
              element={
                <RequireAuth>
                  <HomePage />
                </RequireAuth>
              }
            />
            <Route
              path="/feed"
              element={
                <RequireAuth>
                  <FeedPage />
                </RequireAuth>
              }
            />
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
      </AuthProvider>
    </BrowserRouter>
  );
}
