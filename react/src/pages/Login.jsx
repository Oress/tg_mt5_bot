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

export default function Login() {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const navigate = useNavigate();

  const handleSubmit = (e) => {
    e.preventDefault();

    // Simple validation
    if (!username || !password) {
      setError('Пожалуйста, введите имя пользователя и пароль');
      return;
    }

    // In a real app, you would make an API call to authenticate
    // For now, we'll just simulate a successful login
    // and redirect to the main page

    // Simulating authentication
    if (username === 'admin' && password === 'password') {
      // Store authentication state (in a real app, you'd use a token)
      localStorage.setItem('isAuthenticated', 'true');
      navigate('/main');
    } else {
      setError('Неверные учетные данные');
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
