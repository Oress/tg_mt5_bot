
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import { useState, useEffect } from 'react';
import { Theme } from '@radix-ui/themes';
import Login from './pages/Login';
import Main from './pages/Main';
import UserPage from './pages/UserPage';

function App() {
  const [themeMode, setThemeMode] = useState('light');

  useEffect(() => {
    // Check for saved theme preference
    const savedTheme = localStorage.getItem('themeMode');
    if (savedTheme) {
      setThemeMode(savedTheme);
    }
  }, []);

  const toggleTheme = () => {
    const newTheme = themeMode === 'light' ? 'dark' : 'light';
    setThemeMode(newTheme);
    localStorage.setItem('themeMode', newTheme);
  };

  return (
    <Theme appearance={themeMode} accentColor="blue" radius="medium">
      <Router>
        <Routes>
          <Route path="/" element={<Login />} />
          <Route 
            path="/main" 
            element={
              <Main toggleTheme={toggleTheme} themeMode={themeMode} />
            }
          />
          <Route 
            path="/user/:userId" 
            element={
              <UserPage />
            }
          />
          <Route path="*" element={<Navigate to="/" />} />
        </Routes>
      </Router>
    </Theme>
  );
}

export default App;
