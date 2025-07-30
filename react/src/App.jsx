
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import { useState, useEffect } from 'react';
import { ConfigProvider, theme, Spin } from 'antd';
import Login from './pages/Login';
import Main from './pages/Main';
import UserPage from './pages/UserPage';
import api from './api';

function App() {
  const [themeMode, setThemeMode] = useState('light');
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    // Check for saved theme preference
    const savedTheme = localStorage.getItem('themeMode');
    if (savedTheme) {
      setThemeMode(savedTheme);
    }

    // Check authentication status
    const checkAuth = async () => {
      try {
        // Try to make a request to a protected endpoint to verify authentication
        await api.get('/users');
        setIsAuthenticated(true);
      } catch {
        // If the request fails (401/403), user is not authenticated
        setIsAuthenticated(false);
      } finally {
        setIsLoading(false);
      }
    };

    checkAuth();
  }, []);

  const toggleTheme = () => {
    const newTheme = themeMode === 'light' ? 'dark' : 'light';
    setThemeMode(newTheme);
    localStorage.setItem('themeMode', newTheme);
  };

  // Show loading spinner while checking authentication
  if (isLoading) {
    return (
      <ConfigProvider
        theme={{
          algorithm: themeMode === 'dark' ? theme.darkAlgorithm : theme.defaultAlgorithm,
          token: {
            colorPrimary: '#1677ff',
            borderRadius: 6,
          },
        }}
      >
        <div style={{ 
          display: 'flex', 
          justifyContent: 'center', 
          alignItems: 'center', 
          height: '100vh' 
        }}>
          <Spin size="large" />
        </div>
      </ConfigProvider>
    );
  }

  return (
    <ConfigProvider
      theme={{
        algorithm: themeMode === 'dark' ? theme.darkAlgorithm : theme.defaultAlgorithm,
        token: {
          colorPrimary: '#1677ff',
          borderRadius: 6,
        },
      }}
    >
      <Router>
        <Routes>
          <Route 
            path="/login" 
            element={
              isAuthenticated ? 
                <Navigate to="/main" /> : 
                <Login />
            } 
          />
          <Route 
            path="/" 
            element={
              isAuthenticated ? 
                <Navigate to="/main" /> : 
                <Navigate to="/login" />
            } 
          />
          <Route
            path="/main" 
            element={
              isAuthenticated ? 
                <Main toggleTheme={toggleTheme} themeMode={themeMode} /> :
                <Navigate to="/login" />
            }
          />
          <Route 
            path="/user/:userId" 
            element={
              isAuthenticated ? 
                <UserPage toggleTheme={toggleTheme} themeMode={themeMode} /> :
                <Navigate to="/login" />
            }
          />
          <Route path="*" element={<Navigate to="/" />} />
        </Routes>
      </Router>
    </ConfigProvider>
  );
}

export default App;
