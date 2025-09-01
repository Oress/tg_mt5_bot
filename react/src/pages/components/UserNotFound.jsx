import {Button, Flex, Space, theme, Typography} from "antd";
import {useNavigate} from "react-router-dom";
import {ArrowLeftOutlined} from "@ant-design/icons";

export function UserNotFound() {
  const {token} = theme.useToken();
  const navigate = useNavigate();

  const handleBack = () => {
    navigate('/main');
  };

  return (
    <div style={{minHeight: '100vh', backgroundColor: token.colorBgLayout}}>
      <Flex align="center" justify="center" style={{height: '100vh'}}>
        <Space direction="vertical" align="center" size="large">
          <Typography.Title level={2} type="danger">
            Пользователь не найден
          </Typography.Title>
          <Button
            type="primary"
            icon={<ArrowLeftOutlined/>}
            onClick={handleBack}
          >
            Назад к поиску
          </Button>
        </Space>
      </Flex>
    </div>
  );
}