import { useState, useEffect } from 'react';
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

  // Mock user data - in a real app, this would come from an API
  const mockUsers = [
    { id: 1, telegramId: '12345678', username: 'user1', firstName: 'John' },
    { id: 2, telegramId: '87654321', username: 'user2', firstName: 'Jane' },
    { id: 3, telegramId: '11223344', username: 'admin', firstName: 'Admin' },
    { id: 4, telegramId: '44332211', username: 'trader1', firstName: 'Mike' },
    { id: 5, telegramId: '55667788', username: 'trader2', firstName: 'Sarah' },
  ];

  // Load all users when component mounts
  useEffect(() => {
    setIsLoading(true);
    // Simulate API call with setTimeout
    setTimeout(() => {
      setUsers(mockUsers);
      setIsLoading(false);
    }, 500);
  }, []);

  const handleSearch = (e) => {
    e.preventDefault();
    setIsLoading(true);

    // Simulate API call with setTimeout
    setTimeout(() => {
      const filteredUsers = mockUsers.filter(user =>
        user.username.toLowerCase().includes(searchTerm.toLowerCase()) ||
        user.telegramId.includes(searchTerm) ||
        user.firstName.toLowerCase().includes(searchTerm.toLowerCase())
      );
      setUsers(filteredUsers);
      setIsLoading(false);
    }, 500);
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
            <Heading size="6">Dashboard</Heading>
            <Flex gap="3" align="center">
              <IconButton 
                size="2" 
                variant="ghost" 
                onClick={toggleTheme}
                aria-label={themeMode === 'light' ? 'Switch to dark theme' : 'Switch to light theme'}
              >
                {themeMode === 'light' ? <MoonIcon /> : <SunIcon />}
              </IconButton>
              <Button color="red" variant="soft" onClick={handleLogout}>
                <ExitIcon />
                Logout
              </Button>
            </Flex>
          </Flex>
        </Container>
      </Box>

      <Container size="3" py="6">
        <Card size="2" mb="6">
          <Flex direction="column" gap="4">
            <Heading size="4">Search Users</Heading>
            <Separator size="4" />

            <form onSubmit={handleSearch}>
              <Flex gap="2">
                <TextField.Root size="2" style={{ flexGrow: 1 }}
                                value={searchTerm}
                                onChange={(e) => setSearchTerm(e.target.value)}
                                placeholder="Search by first name, username, or Telegram ID"
                >
                </TextField.Root>
                <Button type="submit" size="2" color="cyan">
                  <MagnifyingGlassIcon />
                  Search
                </Button>
              </Flex>
            </form>

            {isLoading ? (
              <Flex justify="center" py="4">
                <Text color="gray">Loading...</Text>
              </Flex>
            ) : (
              <Box>
                {users.length > 0 ? (
                  <ScrollArea scrollbars="horizontal" style={{ maxWidth: '100%' }}>
                    <Table.Root variant="surface">
                      <Table.Header>
                        <Table.Row>
                          <Table.ColumnHeaderCell>Telegram ID</Table.ColumnHeaderCell>
                          <Table.ColumnHeaderCell>First Name</Table.ColumnHeaderCell>
                          <Table.ColumnHeaderCell>Username</Table.ColumnHeaderCell>
                          <Table.ColumnHeaderCell>Actions</Table.ColumnHeaderCell>
                        </Table.Row>
                      </Table.Header>
                      <Table.Body>
                        {users.map((user) => (
                          <Table.Row key={user.id}>
                            <Table.Cell>{user.telegramId}</Table.Cell>
                            <Table.Cell>{user.firstName}</Table.Cell>
                            <Table.Cell>{user.username}</Table.Cell>
                            <Table.Cell>
                              <Button
                                size="1"
                                variant="soft"
                                color="cyan"
                                onClick={() => handleUserClick(user.id)}
                              >
                                View Details
                              </Button>
                            </Table.Cell>
                          </Table.Row>
                        ))}
                      </Table.Body>
                    </Table.Root>
                  </ScrollArea>
                ) : searchTerm ? (
                  <Flex justify="center" py="4">
                    <Text color="gray">No users found matching "{searchTerm}"</Text>
                  </Flex>
                ) : (
                  <Flex justify="center" py="4">
                    <Text color="gray">No users available</Text>
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
