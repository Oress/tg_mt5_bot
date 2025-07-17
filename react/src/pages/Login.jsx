import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { 
  Container, 
  Box,
  Heading,
  Text, 
  TextField, 
  Button, 
  Card, 
  Flex, 
  Callout 
} from '@radix-ui/themes';
import { ExclamationTriangleIcon } from '@radix-ui/react-icons';
import api from '../api';

export default function Login() {
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
    <Box style={{ minHeight: '100vh', backgroundColor: 'var(--gray-2)' }}>
      <Container size="1" style={{ height: '100vh' }}>
        <Flex align="center" justify="center" style={{ height: '100%' }}>
          <Card size="3" style={{ width: '100%', maxWidth: '400px' }}>
            <Flex direction="column" gap="5">
              <Heading size="6" align="center">Вход в панель управления</Heading>

              {error && (
                <Callout.Root color="red" size="1">
                  <Callout.Icon>
                    <ExclamationTriangleIcon />
                  </Callout.Icon>
                  <Callout.Text>{error}</Callout.Text>
                </Callout.Root>
              )}

              <form onSubmit={handleSubmit}>
                <Flex direction="column" gap="4">
                  <Box>
                    <Text as="label" size="2" weight="medium" htmlFor="username">
                      Имя пользователя
                    </Text>
                    <TextField.Root size="3"
                                    id="username"
                                    value={username}
                                    onChange={(e) => setUsername(e.target.value)}
                                    placeholder="Введите имя пользователя">
                    </TextField.Root>
                  </Box>

                  <Box>
                    <Text as="label" size="2" weight="medium" htmlFor="password">
                      Пароль
                    </Text>
                    <TextField.Root size="3" id="password" type="password" value={password}
                                    placeholder="Введите пароль"
                                    onChange={(e) => setPassword(e.target.value)}>
                    </TextField.Root>
                  </Box>

                  <Button type="submit" size="3" color="cyan">
                    Войти
                  </Button>
                </Flex>
              </form>
            </Flex>
          </Card>
        </Flex>
      </Container>
    </Box>
  );
}
