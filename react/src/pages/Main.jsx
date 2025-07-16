import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';
import {
  Container,
  Box,
  Heading,
  Text,
  TextField,
  Button,
  Card,
  Flex,
  Table,
  Separator,
  ScrollArea,
  IconButton
} from '@radix-ui/themes';
import { MagnifyingGlassIcon, ExitIcon, MoonIcon, SunIcon } from '@radix-ui/react-icons';

function Main({ toggleTheme, themeMode }) {
  const [searchTerm, setSearchTerm] = useState('');
  const [users, setUsers] = useState([]);
  const [isLoading, setIsLoading] = useState(false);
  const navigate = useNavigate();

  // Load all users when component mounts
  useEffect(() => {
    const fetchUsers = async () => {
      setIsLoading(true);
      try {
        const response = await axios.get('/api/users');
        setUsers(response.data);
      } catch (error) {
        console.error('Error fetching users:', error);
        setUsers([]);
      } finally {
        setIsLoading(false);
      }
    };

    fetchUsers();
  }, []);

  const handleSearch = (e) => {
    e.preventDefault();
    setIsLoading(true);

    const fetchSearchResults = async () => {
      try {
        const response = await axios.get(`/api/users?query=${encodeURIComponent(searchTerm)}`);
        setUsers(response.data);
      } catch (error) {
        console.error('Error searching users:', error);
        setUsers([]);
      } finally {
        setIsLoading(false);
      }
    };

    fetchSearchResults();
  };

  const handleUserClick = (userId) => {
    navigate(`/user/${userId}`);
  };

  const handleLogout = () => {
    localStorage.removeItem('isAuthenticated');
    navigate('/');
  };

  return (
    <Box style={{ minHeight: '100vh', backgroundColor: 'var(--gray-2)' }}>
      <Box style={{ backgroundColor: 'var(--color-panel-solid)', boxShadow: 'var(--shadow-2)' }}>
        <Container size="3">
          <Flex justify="between" align="center" py="5">
            <Heading size="6">Панель управления</Heading>
            <Flex gap="3" align="center">
              <IconButton 
                size="2" 
                variant="ghost" 
                onClick={toggleTheme}
                aria-label={themeMode === 'light' ? 'Переключиться на темную тему' : 'Переключиться на светлую тему'}
              >
                {themeMode === 'light' ? <MoonIcon /> : <SunIcon />}
              </IconButton>
              <Button color="red" variant="soft" onClick={handleLogout}>
                <ExitIcon />
                Выйти
              </Button>
            </Flex>
          </Flex>
        </Container>
      </Box>

      <Container size="3" py="6">
        <Card size="2" mb="6">
          <Flex direction="column" gap="4">
            <Heading size="4">Поиск пользователей</Heading>
            <Separator size="4" />

            <form onSubmit={handleSearch}>
              <Flex gap="2">
                <TextField.Root size="2" style={{ flexGrow: 1 }}
                                value={searchTerm}
                                onChange={(e) => setSearchTerm(e.target.value)}
                                placeholder="Поиск по имени, имени пользователя или Telegram ID"
                >
                </TextField.Root>
                <Button type="submit" size="2" color="cyan">
                  <MagnifyingGlassIcon />
                  Поиск
                </Button>
              </Flex>
            </form>

            {isLoading ? (
              <Flex justify="center" py="4">
                <Text color="gray">Загрузка...</Text>
              </Flex>
            ) : (
              <Box>
                {users.length > 0 ? (
                  <ScrollArea scrollbars="horizontal" style={{ maxWidth: '100%' }}>
                    <Table.Root variant="surface">
                      <Table.Header>
                        <Table.Row>
                          <Table.ColumnHeaderCell>Telegram ID</Table.ColumnHeaderCell>
                          <Table.ColumnHeaderCell>Имя</Table.ColumnHeaderCell>
                          <Table.ColumnHeaderCell>Имя пользователя</Table.ColumnHeaderCell>
                          <Table.ColumnHeaderCell>Действия</Table.ColumnHeaderCell>
                        </Table.Row>
                      </Table.Header>
                      <Table.Body>
                        {users.map((user) => (
                          <Table.Row key={user.telegramId}>
                            <Table.Cell>{user.telegramId}</Table.Cell>
                            <Table.Cell>{user.firstName}</Table.Cell>
                            <Table.Cell>{user.username}</Table.Cell>
                            <Table.Cell>
                              <Button
                                size="1"
                                variant="soft"
                                color="cyan"
                                onClick={() => handleUserClick(user.telegramId)}
                              >
                                Просмотр деталей
                              </Button>
                            </Table.Cell>
                          </Table.Row>
                        ))}
                      </Table.Body>
                    </Table.Root>
                  </ScrollArea>
                ) : searchTerm ? (
                  <Flex justify="center" py="4">
                    <Text color="gray">Не найдено пользователей, соответствующих "{searchTerm}"</Text>
                  </Flex>
                ) : (
                  <Flex justify="center" py="4">
                    <Text color="gray">Нет доступных пользователей</Text>
                  </Flex>
                )}
              </Box>
            )}
          </Flex>
        </Card>
      </Container>
    </Box>
  );
}

export default Main;
