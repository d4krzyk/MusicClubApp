import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { AuthProvider } from './auth/AuthContext';
import { TylkoNiezalogowany, TylkoZalogowany } from './auth/RouteGuards';
import Layout from './components/Layout';
import HomePage from './pages/HomePage';
import LoginPage from './pages/LoginPage';
import RegisterPage from './pages/RegisterPage';
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
              path="/users"
              element={
                <TylkoZalogowany>
                  <UsersPage />
                </TylkoZalogowany>
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
