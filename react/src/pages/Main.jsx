import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import api from '../api';
import {
  Card,
  Button,
  Input,
  Table,
  Typography,
  Flex,
  Divider,
  Space,
  Spin,
  theme
} from 'antd';
import { SearchOutlined, LogoutOutlined, MoonOutlined, SunOutlined } from '@ant-design/icons';

function Main({ toggleTheme, themeMode }) {
  const { token } = theme.useToken();
  const [searchTerm, setSearchTerm] = useState('');
  const [users, setUsers] = useState([]);
  const [isLoading, setIsLoading] = useState(false);
  const navigate = useNavigate();

  // Load all users when component mounts
  useEffect(() => {
    const fetchUsers = async () => {
      setIsLoading(true);
      try {
        const response = await api.get('/users');
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
        const response = await api.get(`/users?query=${encodeURIComponent(searchTerm)}`);
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

  const handleLogout = async () => {
    try {
      // Call the logout endpoint to invalidate the session
      await api.post('/auth/logout');
      // Navigate to login page
      navigate('/');
    } catch (error) {
      console.error('Error during logout:', error);
      // Even if there's an error, navigate to login page
      navigate('/');
    }
  };

  const columns = [
    {
      title: 'Имя',
      dataIndex: 'firstName',
      key: 'firstName',
    },
    {
      title: 'Имя пользователя',
      dataIndex: 'username',
      key: 'username',
    },
    {
      title: 'Telegram ID',
      dataIndex: 'telegramId',
      key: 'telegramId',
    },
    {
      title: 'Действия',
      key: 'actions',
      render: (_, user) => (
        <Button
          type="link"
          onClick={() => handleUserClick(user.telegramId)}
        >
          Просмотр деталей
        </Button>
      ),
    },
  ];

  return (
    <div style={{ minHeight: '100vh', backgroundColor: token.colorBgLayout }}>
      <div style={{ backgroundColor: token.colorBgContainer, boxShadow: '0 2px 8px rgba(0,0,0,0.1)' }}>
        <div style={{ maxWidth: '1200px', margin: '0 auto', padding: '20px' }}>
          <Flex justify="space-between" align="center">
            <Typography.Title level={4} style={{ margin: 0 }}>
              Панель управления
            </Typography.Title>
            <Space>
              <Button 
                type="text"
                icon={themeMode === 'light' ? <MoonOutlined /> : <SunOutlined />}
                onClick={toggleTheme}
                title={themeMode === 'light' ? 'Переключиться на темную тему' : 'Переключиться на светлую тему'}
              />
              <Button 
                danger 
                icon={<LogoutOutlined />} 
                onClick={handleLogout}
              >
                Выйти
              </Button>
            </Space>
          </Flex>
        </div>
      </div>

      <div className="pt-8 px-2 md:px-8" style={{ maxWidth: '1200px', margin: '0 auto' }}>
        <Card size="small">
          <Space direction="vertical" style={{ width: '100%' }}>
            <Typography.Title level={5} style={{ margin: 0 }}>
              Поиск пользователей
            </Typography.Title>
            <Divider style={{ margin: 0 }} />

            <form onSubmit={handleSearch}>
              <Space.Compact style={{ width: '100%' }}>
                <Input
                  value={searchTerm}
                  onChange={(e) => setSearchTerm(e.target.value)}
                  placeholder="Поиск по имени, имени пользователя или Telegram ID"
                  style={{ flex: 1 }}
                />
                <Button 
                  type="primary" 
                  htmlType="submit" 
                  icon={<SearchOutlined />}
                >
                  Поиск
                </Button>
              </Space.Compact>
            </form>

            {isLoading ? (
              <Flex justify="center" style={{ padding: '40px 0' }}>
                <Spin size="large" />
              </Flex>
            ) : (
              <div>
                {users.length > 0 ? (
                  <Table
                    size="small"
                    columns={columns}
                    dataSource={users}
                    rowKey="telegramId"
                    scroll={{ x: true }}
                    pagination={false}
                  />
                ) : searchTerm ? (
                  <Flex justify="center" style={{ padding: '40px 0' }}>
                    <Typography.Text type="secondary">
                      Не найдено пользователей, соответствующих "{searchTerm}"
                    </Typography.Text>
                  </Flex>
                ) : (
                  <Flex justify="center" style={{ padding: '40px 0' }}>
                    <Typography.Text type="secondary">
                      Нет доступных пользователей
                    </Typography.Text>
                  </Flex>
                )}
              </div>
            )}
          </Space>
        </Card>
      </div>
    </div>
  );
}

export default Main;
