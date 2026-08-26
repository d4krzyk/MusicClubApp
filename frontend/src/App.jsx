import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { AuthProvider } from './auth/AuthContext';
import { TylkoAdmin, TylkoNiezalogowany, TylkoZalogowany } from './auth/RouteGuards';
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
                <TylkoZalogowany>
                  <HomePage />
                </TylkoZalogowany>
              }
            />
            <Route
              path="/feed"
              element={
                <TylkoZalogowany>
                  <FeedPage />
                </TylkoZalogowany>
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
                <TylkoZalogowany>
                  <ProfilePage />
                </TylkoZalogowany>
              }
            />
            <Route
              path="/profil/:username"
              element={
                <TylkoZalogowany>
                  <ProfilePage />
                </TylkoZalogowany>
              }
            />
            <Route
              path="/znajomi"
              element={
                <TylkoZalogowany>
                  <FriendsPage />
                </TylkoZalogowany>
              }
            />
            <Route
              path="/settings"
              element={
                <TylkoZalogowany>
                  <SettingsPage />
                </TylkoZalogowany>
              }
            />

            {/* Lista wszystkich kont - tylko administrator */}
            <Route
              path="/users"
              element={
                <TylkoAdmin>
                  <UsersPage />
                </TylkoAdmin>
              }
            />
            <Route
              path="/login"
              element={
                <TylkoNiezalogowany>
                  <LoginPage />
                </TylkoNiezalogowany>
              }
            />
            <Route
              path="/register"
              element={
                <TylkoNiezalogowany>
                  <RegisterPage />
                </TylkoNiezalogowany>
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
