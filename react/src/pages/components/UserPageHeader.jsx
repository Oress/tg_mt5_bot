import {Button, Flex, Space, theme, Typography} from "antd";
import {useNavigate} from "react-router-dom";
import {ArrowLeftOutlined} from "@ant-design/icons";

export function UserPageHeader({onRefresh, isRefreshing, user}) {
  const {token} = theme.useToken();
  const navigate = useNavigate();

  const handleBack = () => {
    navigate('/main');
  };

  // Determine which user identifier to display
  const userIdentifier = user ? (user.username || user.firstName || user.telegramId) : '';

  return (
    <div style={{
      backgroundColor: token.colorBgContainer,
      boxShadow: '0 2px 8px rgba(0,0,0,0.1)',
      position: 'fixed',
      top: 0,
      left: 0,
      width: '100%',
      zIndex: 999
    }}>
      <div style={{maxWidth: '1200px', margin: '0 auto', padding: '20px'}}>
        <Flex
          justify="space-between"
          align="center"
          wrap="wrap"
          gap="middle"
        >
          <Typography.Title level={4} style={{margin: 0}}>
            Детали пользователя {userIdentifier && `- ${userIdentifier}`}
          </Typography.Title>
          <Space>
            <Button
              type="primary"
              onClick={onRefresh}
              loading={isRefreshing}
              disabled={isRefreshing}
            >
              {isRefreshing ? 'Обновление...' : 'Обновить'}
            </Button>
            <Button
              onClick={handleBack}
              icon={<ArrowLeftOutlined/>}
            >
              Назад к поиску
            </Button>
          </Space>
        </Flex>
      </div>
    </div>
  );
}