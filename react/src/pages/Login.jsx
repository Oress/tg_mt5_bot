import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { 
  Card, 
  Button, 
  Input, 
  Alert, 
  Typography, 
  Flex, 
  Form,
  theme
} from 'antd';
import { ExclamationCircleOutlined } from '@ant-design/icons';
import api from '../api';

export default function Login() {
  const { token } = theme.useToken();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const navigate = useNavigate();

  const handleSubmit = async (e) => {
    e.preventDefault();

    // Simple validation
    if (!username || !password) {
      setError('Пожалуйста, введите имя пользователя и пароль');
      return;
    }

    try {
      // Make a real API call to authenticate
      await api.post('/auth/login', {
        username,
        password
      });

      // If we get here, authentication was successful
      // No need to store anything in localStorage as we're using HTTP session cookies
      navigate('/main');
    } catch (error) {
      console.error('Login error:', error);

      if (error.response) {
        // Handle different error status codes
        if (error.response.status === 401) {
          setError('Неверные учетные данные');
        } else {
          setError('Ошибка входа. Пожалуйста, попробуйте позже.');
        }
      } else {
        setError('Ошибка сети. Пожалуйста, проверьте подключение.');
      }
    }
  };

  return (
    <div style={{ minHeight: '100vh', backgroundColor: token.colorBgLayout, padding: '20px' }}>
      <div style={{ height: '100vh', maxWidth: '400px', margin: '0 auto' }}>
        <Flex align="center" justify="center" style={{ height: '100%' }}>
          <Card style={{ width: '100%', maxWidth: '400px' }}>
            <Flex vertical gap="large">
              <Typography.Title level={2} style={{ textAlign: 'center', margin: 0 }}>
                Вход в панель управления
              </Typography.Title>

              {error && (
                <Alert
                  message={error}
                  type="error"
                  icon={<ExclamationCircleOutlined />}
                  showIcon
                />
              )}

              <form onSubmit={handleSubmit}>
                <Flex vertical gap="middle">
                  <div>
                    <Typography.Text strong style={{ display: 'block', marginBottom: '8px' }}>
                      Имя пользователя
                    </Typography.Text>
                    <Input
                      size="large"
                      id="username"
                      value={username}
                      onChange={(e) => setUsername(e.target.value)}
                      placeholder="Введите имя пользователя"
                    />
                  </div>

                  <div>
                    <Typography.Text strong style={{ display: 'block', marginBottom: '8px' }}>
                      Пароль
                    </Typography.Text>
                    <Input.Password
                      size="large"
                      id="password"
                      value={password}
                      placeholder="Введите пароль"
                      onChange={(e) => setPassword(e.target.value)}
                    />
                  </div>

                  <Button type="primary" htmlType="submit" size="large" block>
                    Войти
                  </Button>
                </Flex>
              </form>
            </Flex>
          </Card>
        </Flex>
      </div>
    </div>
  );
}
