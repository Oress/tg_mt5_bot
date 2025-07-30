
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import { useState, useEffect } from 'react';
import { ConfigProvider, theme } from 'antd';
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
          {/*<Route path="/" element={<Login />} />*/}
          <Route path="/" element={<Main toggleTheme={toggleTheme} themeMode={themeMode} />} />
          <Route
            path="/main" 
            element={
              <Main toggleTheme={toggleTheme} themeMode={themeMode} />
            }
          />
          <Route 
            path="/user/:userId" 
            element={
              <UserPage toggleTheme={toggleTheme} themeMode={themeMode} />
            }
          />
          <Route path="*" element={<Navigate to="/" />} />
        </Routes>
      </Router>
    </ConfigProvider>
  );
}

export default App;
