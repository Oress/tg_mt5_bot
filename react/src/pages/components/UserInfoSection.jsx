import {
  Button,
  Card,
  Descriptions,
  Divider,
  Form,
  Input,
  InputNumber,
  Modal,
  notification,
  Space,
  Spin,
  Typography
} from "antd";
import {useEffect, useState} from "react";
import api from "../../api.js";
import {EditOutlined, LinkOutlined} from "@ant-design/icons";
import {formatCurrency} from "../../utils.js";

export function UserInfoSection({user}) {
  const [notificationApi, contextHolder] = notification.useNotification();
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const [balance, setBalance] = useState(null);
  const [balanceLoading, setBalanceLoading] = useState(false);
  const [balanceError, setBalanceError] = useState(null);

  // Fetch user balance
  useEffect(() => {
    const fetchBalance = async () => {
      if (!user?.telegramId) return;

      setBalanceLoading(true);
      setBalanceError(null);

      try {
        const response = await api.get(`/users/${user.telegramId}/terminal-balance`);
        setBalance(response.data.data.balance || null);
      } catch (error) {
        console.error('Error fetching user balance:', error);
        setBalanceError(error);
      } finally {
        setBalanceLoading(false);
      }
    };

    fetchBalance();
  }, [user?.telegramId]);

  const items = [
    {
      key: 'tgId',
      label: 'Telegram ID',
      children: <p>{user.telegramId}</p>,
    },
    {
      key: 'name',
      label: 'Имя',
      children: <p>{user.firstName}</p>,
    },
    {
      key: 'username',
      label: 'Имя пользователя',
      children: <p>{user.username}</p>,
    },
    {
      key: 'accountType',
      label: 'Тип аккаунта Герчик',
      children: <p>{user.accountType}</p>,
    },
    {
      key: 'accountNumber',
      label: '№ аккаунта Герчик',
      children: <p>{user.mt5Account}</p>,
    },
    {
      key: 'googleSheetUrl',
      label: 'Google Sheet',
      children: <>{
        user.googleSheetId
          ? (<Button type="link" href={user.googleSheetId} size="small" target="_blank"
                     icon={<LinkOutlined/>}>Открыть</Button>)
          : (<span>-</span>)
      }</>,
    },
    {
      key: 'terminalBalance',
      label: 'Баланс MT5',
      children: <>
        {balanceLoading ? (
          <Spin size="small"/>
        ) : balanceError ? (
          <span style={{color: '#ff4d4f'}}>Ошибка загрузки</span>
        ) : balance !== null ? (
          <p>{formatCurrency(balance)}</p>
        ) : (
          <span>-</span>
        )}
      </>,
    },
  ];

  const handleOpenModal = () => {
    setIsModalOpen(true);
    form.resetFields();
  };

  const handleCloseModal = () => {
    setIsModalOpen(false);
    form.resetFields();
  };

  const handleSubmit = async (values) => {
    setLoading(true);
    try {
      await api.post(`/users/${user.telegramId}/change-credentials`, {
        accountNumber: values.username,
        accountPassword: values.password
      });

      notificationApi.success('Учетные данные успешно изменены');
      handleCloseModal();
    } catch (error) {
      console.error('Error changing credentials:', error);

      if (error.response) {
        if (error.response.status === 400) {
          notificationApi['error']({
            message: 'Ошибка',
            description: error.response.data.error
          });
        } else {
          notificationApi['error']({
            message: 'Ошибка',
            description:
              'Ошибка при изменении учетных данных. Попробуйте позже.',
          });
        }
      } else {
        notificationApi['error']({
          message: 'Ошибка',
          description:
            'Ошибка сети. Проверьте подключение.',
        });
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <>
      {contextHolder}
      <Card size="small" style={{marginBottom: '24px'}}>
        <Space direction="vertical" size="middle" style={{width: '100%'}}>
          <div className="flex flex-col sm:flex-row justify-between items-center gap-2">
            <Typography.Title level={3} style={{margin: 0}}>
              Информация о пользователе
            </Typography.Title>
            <Button
              type="default"
              icon={<EditOutlined/>}
              onClick={handleOpenModal}
            >
              Изменить аккаунт Герчик
            </Button>
          </div>
          <Divider style={{margin: 0}}/>
          <Descriptions items={items}/>
        </Space>
      </Card>

      <Modal
        title="Изменить аккаунт Герчик"
        open={isModalOpen}
        onCancel={handleCloseModal}
        footer={null}
        width={400}
      >
        <Form
          form={form}
          layout="vertical"
          onFinish={handleSubmit}
          style={{marginTop: '20px'}}
        >
          <Form.Item
            label="№ Аккаунта Герчик"
            name="username"
            rules={[
              {required: true, message: 'Пожалуйста, введите имя пользователя'},
            ]}
          >
            <InputNumber style={{width: '100%'}} placeholder="Введите новое имя пользователя"/>
          </Form.Item>

          <Form.Item
            label="Пароль Аккаунта Герчик"
            name="password"
            rules={[
              {required: true, message: 'Пожалуйста, введите пароль'},
            ]}
          >
            <Input.Password placeholder="Введите новый пароль"/>
          </Form.Item>

          <Form.Item style={{marginBottom: 0, marginTop: '24px'}}>
            <Space style={{width: '100%', justifyContent: 'flex-end'}}>
              <Button onClick={handleCloseModal}>
                Отмена
              </Button>
              <Button type="primary" htmlType="submit" loading={loading}>
                Сохранить
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
}