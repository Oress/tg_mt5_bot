import {useEffect, useState} from 'react';
import {useNavigate, useParams} from 'react-router-dom';
import api from '../api';
import {
  Button,
  Card,
  Checkbox,
  Col, Descriptions,
  Divider, Dropdown,
  Flex,
  Form,
  Input,
  InputNumber,
  message,
  Modal, notification, Popconfirm,
  Radio,
  Row,
  Space,
  Spin,
  Statistic,
  Table,
  Tabs, Tag,
  theme,
  Typography
} from 'antd';
import {ArrowLeftOutlined, DownOutlined, EditOutlined, LinkOutlined, PlusOutlined} from "@ant-design/icons";

const formatCurrency = (amount, currency = 'USD', locale = 'en-US') => {
  return new Intl.NumberFormat(locale, {
    style: 'currency',
    currency: currency
  }).format(amount);
};

const getColorByOrderType = (orderType) => {
  switch (orderType) {
    case 'MT5_LONG':
    case 'HOUSE_CLIENT_TRANSFER_LONG':
      return '#52c41a';
    case 'MT5_SHORT':
    case 'HOUSE_CLIENT_TRANSFER_SHORT':
      return '#ff4d4f';
  }
};


function LoadingUserPage() {
  const { token } = theme.useToken();
  return (
    <div style={{ minHeight: '100vh', backgroundColor: token.colorBgLayout }}>
      <Flex align="center" justify="center" style={{ height: '100vh' }}>
        <Spin size="large" />
      </Flex>
    </div>
  );
}

function UserNotFound() {
  const { token } = theme.useToken();
  const navigate = useNavigate();

  const handleBack = () => {
    navigate('/main');
  };

  return (
    <div style={{ minHeight: '100vh', backgroundColor: token.colorBgLayout }}>
      <Flex align="center" justify="center" style={{ height: '100vh' }}>
        <Space direction="vertical" align="center" size="large">
          <Typography.Title level={2} type="danger">
            Пользователь не найден
          </Typography.Title>
          <Button
            type="primary"
            icon={<ArrowLeftOutlined />}
            onClick={handleBack}
          >
            Назад к поиску
          </Button>
        </Space>
      </Flex>
    </div>
  );
}

function UserPageHeader({ onRefresh, isRefreshing, user }) {
  const { token } = theme.useToken();
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
      <div style={{ maxWidth: '1200px', margin: '0 auto', padding: '20px' }}>
        <Flex
          justify="space-between"
          align="center"
          wrap="wrap"
          gap="middle"
        >
          <Typography.Title level={4} style={{ margin: 0 }}>
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
              icon={<ArrowLeftOutlined />}
            >
              Назад к поиску
            </Button>
          </Space>
        </Flex>
      </div>
    </div>
  );
}

function UserInfoSection({user}) {
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
        ? (<Button type="link" href={user.googleSheetId} size="small" target="_blank" icon={<LinkOutlined />}>Открыть</Button>)
        : (<span>-</span>)
      }</>,
    },
    {
      key: 'terminalBalance',
      label: 'Баланс MT5',
      children: <>
        {balanceLoading ? (
          <Spin size="small" />
        ) : balanceError ? (
          <span style={{ color: '#ff4d4f' }}>Ошибка загрузки</span>
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
      <Card style={{ marginBottom: '24px' }}>
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          <div className="flex flex-col sm:flex-row justify-between items-center gap-2">
            <Typography.Title level={3} style={{ margin: 0 }}>
              Информация о пользователе
            </Typography.Title>
            <Button 
              type="default"
              icon={<EditOutlined />}
              onClick={handleOpenModal}
            >
              Изменить аккаунт Герчик
            </Button>
          </div>
          <Divider style={{ margin: 0 }} />
          <Descriptions items={items} />
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
          style={{ marginTop: '20px' }}
        >
          <Form.Item
            label="№ Аккаунта Герчик"
            name="username"
            rules={[
              { required: true, message: 'Пожалуйста, введите имя пользователя' },
            ]}
          >
            <InputNumber style={{width: '100%'}} placeholder="Введите новое имя пользователя" />
          </Form.Item>

          <Form.Item
            label="Пароль Аккаунта Герчик"
            name="password"
            rules={[
              { required: true, message: 'Пожалуйста, введите пароль' },
            ]}
          >
            <Input.Password placeholder="Введите новый пароль" />
          </Form.Item>

          <Form.Item style={{ marginBottom: 0, marginTop: '24px' }}>
            <Space style={{ width: '100%', justifyContent: 'flex-end' }}>
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


function UserDepositsAndWithdrawals({userId, refreshTrigger}) {
  return (
    <Card style={{ marginBottom: '24px' }}>
      <DepositsWithdrawalsSection
        userId={userId}
        refreshTrigger={refreshTrigger}
      />
    </Card>
  );
}

function Mt5DataSection({userId, refreshTrigger}) {
  const [positions, setPositions] = useState([]);
  const [isLoading, setIsLoading] = useState(true);

  const fetchPositions = async () => {
    setIsLoading(true);
    try {
      const response = await api.get(`/users/${userId}/terminal-data`);
      setPositions(response.data.data || []);
    } catch (error) {
      console.error('Error fetching deposits:', error);
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchPositions();
  }, [userId, refreshTrigger]);

  // type 0 - buy, 1 - sell
  const columns = [
    {
      title: 'Тип',
      dataIndex: 'type',
      key: 'type',
      width: 60,
      render: (type) => (
        <Typography.Text
          style={{
            fontWeight: 500,
            color: type === 0 ? '#52c41a' : '#ff4d4f'
          }}
        >
          {type === 0 ? 'Покупка' : 'Продажа'}
        </Typography.Text>
      ),
    },
    {
      title: 'Инструмент',
      key: 'symbol',
      dataIndex: 'symbol',
      width: 60,
    },
    {
      title: 'Цена (на сейчас)',
      key: 'priceCurrent',
      dataIndex: 'priceCurrent',
      width: 60,
    },
    {
      title: 'Нериализованный PnL (на сейчас)',
      key: 'profit',
      dataIndex: 'profit',
      width: 60,
      render: (profit) => (
        <Typography.Text>
          {formatCurrency(profit)}
        </Typography.Text>
      )
    },
    {
      title: 'Лоты',
      key: 'volume',
      dataIndex: 'volume',
      width: 60,
    },
    /*    {
          title: 'Действия',
          key: 'actions',
          width: 120,
          render: (_, transaction) => (
            <Space>
              <Button
                type="text"
                icon={<EditOutlined />}
                onClick={() => handleOpenDialog(transaction)}
              />
              <Button
                type="text"
                danger
                icon={<DeleteOutlined />}
                onClick={() => handleOpenDeleteConfirm(transaction)}
              />
            </Space>
          ),
        }*/,
  ];

  return (
    <>
    <Card style={{ marginBottom: '24px' }}>
      <Space direction="vertical" size="middle" style={{ width: '100%' }}>
        <div className="flex flex-col sm:flex-row justify-between items-center gap-2">
          <Typography.Title level={4} style={{ margin: 0 }}>
            Позиции MT5
          </Typography.Title>
        </div>
        <Divider style={{ margin: 0 }} />

        {isLoading ? (
          <Flex justify="center" style={{ padding: '40px 0' }}>
            <Spin size="large" />
          </Flex>
        ) : positions.length > 0 ? (
          <Table
            style={{maxHeight: '400px', overflow: 'auto'}}
            columns={columns}
            dataSource={positions}
            rowKey="id"
            scroll={{ x: 'max-content' }}
            pagination={false}
            size="small"
          />
        ) : (
          <Flex justify="center" style={{ padding: '40px 0' }}>
            <Typography.Text type="secondary">
              Нет записей о позициях для этого клиента
            </Typography.Text>
          </Flex>
        )}
      </Space>
    </Card>
    </>
  );
}

function formatOrderType(type) {
  switch (type) {
    case 'MT5_LONG':
      return 'Покупка';
    case 'HOUSE_CLIENT_TRANSFER_LONG':
      return 'Покупка (Внутренний перевод)';
    case 'MT5_SHORT':
      return 'Продажа';
    case 'HOUSE_CLIENT_TRANSFER_SHORT':
      return 'Продажа (Внутренний перевод)';
    default:
      return type;
  }
}

function InstrumentToolbar({selectedInstrument, userId, user}) {
  const [notificationApi, contextHolder] = notification.useNotification();

  const recalculateGoogleSheet = async () => {
    try {
      if (selectedInstrument) {
        // Fetch order history for the selected instrument
        const recalculateResponse = await api.post(`/users/${userId}/instruments/${selectedInstrument.id}/recalculate-ggl-sheet`);
        if (recalculateResponse.data.success) {
          notificationApi['success']({
            message: 'Гугл таблица была отправлена на обновление.',
            description:
              'Гугл таблица была отправлена на обновление. Пожалуйста, подождите.',
          });
        } else {
          notificationApi['error']({
            message: 'Ошибка',
            description:
              'Что-то пошло не так. Пожалуйста, попробуйте позже.',
          });
        }
      }
    } catch (error) {
      console.error('Error fetching data:', error);
      // You might want to set an error state here to display to the user
    }
  };

  const liquidatePosition = async () => {
    try {
      if (selectedInstrument) {
        // Fetch order history for the selected instrument
        const liquidateResponse = await api.post(`/users/${userId}/instruments/${selectedInstrument.id}/liquidate`);
        console.log(liquidateResponse.data);
        if (liquidateResponse.data) {
          // TODO: send full refresh signal
        }
      }
    } catch (error) {
      console.error('Error fetching data:', error);
      // You might want to set an error state here to display to the user
    }
  };

  const items = [
    {
      key: 'recalc_ggl_sheet',
      label: (
        <a onClick={recalculateGoogleSheet}>
          Пересоздать гугл лист для инструмента
        </a>
      ),
    },
    {
      key: 'liquidate',
      danger: true,
      label: (
        <Popconfirm
          title="Ликвидация позиций"
          description={`Вы уверены что хотите ликвидировать позиции ${selectedInstrument?.label} для пользователя ${user?.username || user?.firstName}?`}
          onConfirm={liquidatePosition}
          okText="Да"
          cancelText="Нет"
        >
          <a type="primary" danger>
            Ликвидировать позиции
          </a>
        </Popconfirm>
      ),
    },
  ];

  return (
    <div className="pb-4 space-x-2">
      {contextHolder}

      <Dropdown menu={{ items }}>
        <a onClick={(e) => e.preventDefault()}>
          <Space>
            Действия
            <DownOutlined />
          </Space>
        </a>
      </Dropdown>
    </div>
  );
}

function RegularHedgeDetails({selectedInstrument, userId, refreshTrigger, unrealizedPnL, loadingUnrealizedPnL, user}) {
  const [orderHistory, setOrderHistory] = useState([]);

  const fetchData = async () => {
    try {
      // Set default active tab to first instrument if available
      if (selectedInstrument) {
        // Fetch open positions for the selected instrument
        // const positionsResponse = await api.get(`/users/${userId}/orders/${firstInstrument.id}`);
        // setOpenPositions(positionsResponse.data);

        // Fetch order history for the selected instrument
        const historyResponse = await api.get(`/users/${userId}/orders/${selectedInstrument.id}`);
        setOrderHistory(historyResponse.data);
      }
    } catch (error) {
      console.error('Error fetching data:', error);
      // You might want to set an error state here to display to the user
    }
  };

  useEffect(() => {
    fetchData();
  }, [selectedInstrument, userId, refreshTrigger]);

  const columns = [
    {
      title: '# Сделки',
      dataIndex: 'deal',
      key: 'deal',
      width: 100,
    },
    {
      title: 'Тип',
      dataIndex: 'type',
      key: 'type',
      render: (type) => (
        <Typography.Text
          style={{
            fontWeight: 'bold',
            textTransform: 'uppercase',
            color: getColorByOrderType(type)
          }}
        >
          {formatOrderType(type)}
        </Typography.Text>
      ),
    },
    {
      title: 'Статус',
      dataIndex: 'status',
      key: 'status',
    },
    {
      title: 'Цена',
      dataIndex: 'price',
      key: 'price',
    },
    {
      title: 'Дата',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 180,
      render: (date) => new Date(date).toLocaleString(),
    },
    {
      title: '№ Аккаунта',
      dataIndex: 'mt5AccNumber',
      key: 'mt5AccNumber',
      width: 120,
    },
    {
      title: 'Сумма в базовой валюте',
      dataIndex: 'userBaseAmount',
      key: 'userBaseAmount',
      width: 160,
      render: (amount) => (
        <Typography.Text strong>
          {formatCurrency(amount, selectedInstrument.base)}
        </Typography.Text>
      )
    },
    {
      title: 'Лоты',
      dataIndex: 'executedVolume',
      key: 'executedVolume',
    },
    {
      title: 'Сумма ордера в базовой валюте',
      dataIndex: 'executedBaseAmount',
      key: 'executedBaseAmount',
      width: 160,
      render: (amount) => (
        <Typography.Text strong>
          {formatCurrency(amount, selectedInstrument.base)}
        </Typography.Text>
      )
    },
    {
      title: 'Клиент, До, Сумма в базовой валюте',
      dataIndex: 'clientBaseAmountFrom',
      key: 'clientBaseAmountFrom',
      width: 160,
      render: (amount) => (
        <Typography.Text strong>
          {formatCurrency(amount, selectedInstrument.base)}
        </Typography.Text>
      )
    },
    {
      title: 'Клиент, После, Сумма в базовой валюте',
      dataIndex: 'clientBaseAmountTo',
      key: 'clientBaseAmountTo',
      width: 160,
      render: (amount) => (
        <Typography.Text strong>
          {formatCurrency(amount, selectedInstrument.base)}
        </Typography.Text>
      )
    },
    {
      title: 'ID',
      dataIndex: 'id',
      key: 'id',
      width: 300,
    },
  ];

  return (
    <div>
      {/* Ownership Info */}
      <Space size="small" style={{ marginBottom: '24px' }}>
        <Descriptions
          title={<Typography.Title level={4}>Информация об инструменте</Typography.Title>}
          size="small"
        >
          <Descriptions.Item label={`${selectedInstrument.base} Клиента`}>
            {formatCurrency(selectedInstrument.clientOwnedBaseAmount, selectedInstrument.base)}
          </Descriptions.Item>
          <Descriptions.Item label="Баланс USD">
            {selectedInstrument.balance !== null ? formatCurrency(selectedInstrument.balance) : "Нет данных"}
          </Descriptions.Item>
          <Descriptions.Item label="Текущий курс">
            {loadingUnrealizedPnL ? "Загрузка..." : (unrealizedPnL !== null ? unrealizedPnL.rate : "Нет данных")}
          </Descriptions.Item>
          <Descriptions.Item label="Нериализованный PnL Клиента">
            {loadingUnrealizedPnL ? "Загрузка..." : (unrealizedPnL !== null ? formatCurrency(unrealizedPnL.profit) : "Нет данных")}
          </Descriptions.Item>
          <Descriptions.Item label="Нериализованный PnL Сервиса">
            {loadingUnrealizedPnL ? "Загрузка..." : (unrealizedPnL !== null ? formatCurrency(unrealizedPnL.profitHouse) : "Нет данных")}
          </Descriptions.Item>
        </Descriptions>
      </Space>

      {/* Order History */}
      <div>
        <Typography.Title level={4} style={{ marginBottom: '16px' }}>
          История ордеров
        </Typography.Title>
        <Divider style={{ margin: 0 }} />

        {orderHistory.length > 0 ? (
          <Table
            columns={columns}
            dataSource={orderHistory}
            rowKey="id"
            scroll={{ x: 'max-content' }}
            pagination={false}
            size="small"
          />
        ) : (
          <Flex justify="center" style={{ padding: '40px 0' }}>
            <Typography.Text type="secondary">
              Нет истории заказов для этого инструмента
            </Typography.Text>
          </Flex>
        )}
      </div>
    </div>
  )
}

function InverseHedgeDetails({selectedInstrument, userId, refreshTrigger, unrealizedPnL, loadingUnrealizedPnL, servicePnL, loadingServicePnL, user}) {
  const [openPositions, setOpenPositions] = useState([]);
  const [orderHistory, setOrderHistory] = useState([]);
  const [expandedRows, setExpandedRows] = useState({});

  const fetchData = async () => {
    try {
      // Set default active tab to first instrument if available
      if (selectedInstrument) {
        // Fetch open positions for the selected instrument
        const positionsResponse = await api.get(`/users/${userId}/orders/${selectedInstrument.id}`, {
          params: {
            type: "open"
          }
        });
        setOpenPositions(positionsResponse.data);

        // Fetch order history for the selected instrument
        const historyResponse = await api.get(`/users/${userId}/orders/${selectedInstrument.id}`);
        setOrderHistory(historyResponse.data);
      }
    } catch (error) {
      console.error('Error fetching data:', error);
      // You might want to set an error state here to display to the user
    }
  };

  useEffect(() => {
    fetchData();
  }, [selectedInstrument, userId, refreshTrigger]);

  return (
      <div>
        {/* Ownership Info */}
        <Space size="small" style={{ marginBottom: '24px' }}>
          <Descriptions
            title={<Typography.Title level={4}>Информация об инструменте</Typography.Title>}
            size="small"
          >
            <Descriptions.Item label={`${selectedInstrument.base} Клиента`}>
              {formatCurrency(selectedInstrument.clientOwnedBaseAmount, selectedInstrument.base)}
            </Descriptions.Item>
            <Descriptions.Item label={`${selectedInstrument.quote} Клиента`}>
              {formatCurrency(selectedInstrument.clientOwnedQuoteAmount, selectedInstrument.quote)}
            </Descriptions.Item>
            <Descriptions.Item label="Нериализованный PnL Клиента">
              {loadingUnrealizedPnL ? "Загрузка..." : (unrealizedPnL !== null ? formatCurrency(unrealizedPnL.profit) : "Нет данных")}
            </Descriptions.Item>
            <Descriptions.Item label={`${selectedInstrument.base} Сервиса`}>
              {formatCurrency(selectedInstrument.houseOwnedBaseAmount, selectedInstrument.base)}
            </Descriptions.Item>
            <Descriptions.Item label="PnL Сервиса">
              {loadingServicePnL ? "Загрузка..." : (servicePnL !== null ? formatCurrency(servicePnL.profit) : "Нет данных")}
            </Descriptions.Item>
            <Descriptions.Item label="Нериализованный PnL Сервиса">
              {loadingUnrealizedPnL ? "Загрузка..." : (unrealizedPnL !== null ? formatCurrency(unrealizedPnL.profitHouse) : "Нет данных")}
            </Descriptions.Item>
            <Descriptions.Item label="Баланс USD">
              {selectedInstrument.balance !== null ? formatCurrency(selectedInstrument.balance) : "Нет данных"}
            </Descriptions.Item>
            <Descriptions.Item label="Текущий курс">
              {loadingUnrealizedPnL ? "Загрузка..." : (unrealizedPnL !== null ? unrealizedPnL.rate : "Нет данных")}
            </Descriptions.Item>
          </Descriptions>
        </Space>

        {/* Open Positions */}
        {/* No sense to show them for regular hedge */}
        <div style={{ marginBottom: '24px' }}>
          <Typography.Title level={4} style={{ marginBottom: '16px' }}>
            Текущие открытые позиции
          </Typography.Title>
          <Divider style={{ margin: 0 }} />

          {openPositions.length > 0 ? (
            <Table
              columns={[
                {
                  title: '# Сделки',
                  dataIndex: 'deal',
                  key: 'deal',
                  width: 100,
                },
                {
                  title: 'Дата',
                  dataIndex: 'createdAt',
                  key: 'createdAt',
                  width: 180,
                  render: (date) => new Date(date).toLocaleString(),
                },
                {
                  title: 'Цена',
                  dataIndex: 'price',
                  key: 'price',
                  render: (price) => (
                    <Typography.Text strong>{price}</Typography.Text>
                  ),
                },
                {
                  title: 'Остаток для расхеджирования',
                  key: 'unhedge',
                  width: 300,
                  render: (_, order) => (
                    <Typography.Text strong style={{ color: '#52c41a' }}>
                      {formatCurrency(order.remainingQuoteAmount, selectedInstrument.quote)} / {formatCurrency(order.userEnteredQuoteAmount, selectedInstrument.quote)}
                    </Typography.Text>
                  ),
                },
                {
                  title: 'Сумма в котируемой валюте',
                  key: 'userEnteredQuoteAmount',
                  dataIndex: 'userEnteredQuoteAmount',
                  width: 160,
                  render: (amount) => (
                    <Typography.Text strong>
                      {formatCurrency(amount, selectedInstrument.quote)}
                    </Typography.Text>
                  )
                },
                {
                  title: 'Сумма в базовой валюте',
                  key: 'userBaseAmount',
                  dataIndex: 'userBaseAmount',
                  width: 160,
                  render: (amount) => (
                    <Typography.Text strong>
                      {formatCurrency(amount, selectedInstrument.base)}
                    </Typography.Text>
                  )
                },
                {
                  title: 'Сумма ордера в базовой валюте',
                  key: 'executedBaseAmount',
                  dataIndex: 'executedBaseAmount',
                  width: 160,
                  render: (amount) => (
                    <Typography.Text strong>
                      {formatCurrency(amount, selectedInstrument.base)}
                    </Typography.Text>
                  )
                },
                {
                  title: 'ID',
                  dataIndex: 'id',
                  key: 'id',
                  width: 300,
                },
              ]}
              dataSource={openPositions}
              rowKey="id"
              pagination={false}
              scroll={{ x: 'max-content' }}
              size="small"
            />
          ) : (
            <Flex justify="center" style={{ padding: '40px 0' }}>
              <Typography.Text type="secondary">
                Нет открытых позиций для этого инструмента
              </Typography.Text>
            </Flex>
          )}
        </div>

        {/* Order History */}
        <div>
          <Typography.Title level={4} style={{ marginBottom: '16px' }}>
            История заказов
          </Typography.Title>
          <Divider style={{ margin: 0 }} />

          {orderHistory.length > 0 ? (
            <Table
              columns={[
                {
                  title: '# Сделки',
                  dataIndex: 'deal',
                  key: 'deal',
                  width: 100,
                },
                {
                  title: 'Тип',
                  dataIndex: 'type',
                  key: 'type',
                  render: (type, order) => (
                    <Flex align="center" gap="small">
                      <Typography.Text
                        style={{
                          fontWeight: 'bold',
                          textTransform: 'uppercase',
                          color: getColorByOrderType(type)
                        }}
                      >
                        {formatOrderType(type)}
                      </Typography.Text>
                      {(order.systemType === 'LIQUIDATION') && <Tag>Ликвидация</Tag>}
                    </Flex>
                  ),
                },
                {
                  title: 'Статус',
                  dataIndex: 'status',
                  key: 'status',
                },
                {
                  title: 'Цена',
                  dataIndex: 'price',
                  key: 'price',
                },
                {
                  title: 'Дата',
                  dataIndex: 'createdAt',
                  key: 'createdAt',
                  width: 180,
                  render: (date) => new Date(date).toLocaleString(),
                },
                {
                  title: '№ Аккаунта',
                  dataIndex: 'mt5AccNumber',
                  key: 'mt5AccNumber',
                  width: 120,
                },
                {
                  title: 'Сумма в базовой валюте',
                  key: 'userBaseAmount',
                  dataIndex: 'userBaseAmount',
                  width: 160,
                  render: (amount) => (
                    <Typography.Text strong>
                      {formatCurrency(amount, selectedInstrument.base)}
                    </Typography.Text>
                  )
                },
                {
                  title: 'Сумма в котируемой валюте',
                  key: 'userEnteredQuoteAmount',
                  dataIndex: 'userEnteredQuoteAmount',
                  width: 160,
                  render: (amount) => (
                    <Typography.Text strong>
                      {formatCurrency(amount, selectedInstrument.quote)}
                    </Typography.Text>
                  )
                },
                {
                  title: 'Лоты',
                  dataIndex: 'executedVolume',
                  key: 'executedVolume',
                },
                {
                  title: 'Сумма ордера в базовой валюте',
                  key: 'executedBaseAmount',
                  dataIndex: 'executedBaseAmount',
                  width: 160,
                  render: (amount) => (
                    <Typography.Text strong>
                      {formatCurrency(amount, selectedInstrument.base)}
                    </Typography.Text>
                  )
                },
                {
                  title: 'Сервис, До, Сумма в базовой валюте',
                  key: 'houseBaseAmountFrom',
                  dataIndex: 'houseBaseAmountFrom',
                  width: 160,
                  render: (amount) => (
                    <Typography.Text strong>
                      {formatCurrency(amount, selectedInstrument.base)}
                    </Typography.Text>
                  )
                },
                {
                  title: 'Сервис, После, Сумма в базовой валюте',
                  key: 'houseBaseAmountTo',
                  dataIndex: 'houseBaseAmountTo',
                  width: 160,
                  render: (amount) => (
                    <Typography.Text strong>
                      {formatCurrency(amount, selectedInstrument.base)}
                    </Typography.Text>
                  )
                },
                {
                  title: 'Клиент, До, Сумма в базовой валюте',
                  key: 'clientBaseAmountFrom',
                  dataIndex: 'clientBaseAmountFrom',
                  width: 160,
                  render: (amount) => (
                    <Typography.Text strong>
                      {formatCurrency(amount, selectedInstrument.base)}
                    </Typography.Text>
                  )
                },
                {
                  title: 'Клиент, После, Сумма в базовой валюте',
                  key: 'clientBaseAmountTo',
                  dataIndex: 'clientBaseAmountTo',
                  width: 160,
                  render: (amount) => (
                    <Typography.Text strong>
                      {formatCurrency(amount, selectedInstrument.base)}
                    </Typography.Text>
                  )
                },
                {
                  title: 'ID',
                  dataIndex: 'id',
                  key: 'id',
                  width: 290,
                },
              ]}
              dataSource={orderHistory}
              rowKey="id"
              scroll={{ x: 'max-content' }}
              pagination={false}
              size="small"
              onRow={(record) => ({
                onClick: () => {
                  if (record.type === 'MT5_SHORT') {
                    setExpandedRows(prev => ({
                      ...prev,
                      [record.id]: !prev[record.id]
                    }));
                  }
                },
              })}
              expandable={{
                rowExpandable: (record) => record.consumptions && record.consumptions.length > 0,
                expandedRowRender: (record) => (
                  record.consumptions ? (
                    <div style={{ paddingLeft: '16px', paddingTop: '8px', paddingBottom: '8px' }}>
                      <Table
                        columns={[
                          {
                            title: '# Сделки',
                            key: 'deal',
                            width: 200,
                            render: (_, consumedOrder) => consumedOrder.consumedOrder.deal,
                          },
                          {
                            title: `Часть ${selectedInstrument.quote} закрыто`,
                            dataIndex: 'consumedQuoteAmount',
                            key: 'consumedQuoteAmount',
                            width: 200,
                            render: (amount) => (
                              <Typography.Text strong>
                                {formatCurrency(amount, selectedInstrument.quote)}
                              </Typography.Text>
                            )
                          },
                          {
                            title: `Остаток ${selectedInstrument.quote} в сделке`,
                            dataIndex: 'leftQuoteAmount',
                            key: 'leftQuoteAmount',
                            width: 200,
                            render: (amount) => (
                              <Typography.Text strong>
                                {formatCurrency(amount, selectedInstrument.quote)}
                              </Typography.Text>
                            ),                          },
/*                          {
                            title: `Еквивалент в ${selectedInstrument.base}`,
                            dataIndex: 'baseEquivalent',
                            key: 'baseEquivalent',
                            width: 200,
                            render: (amount) => (
                              <Typography.Text strong>
                                {formatCurrency(amount, selectedInstrument.base)}
                              </Typography.Text>
                            ),
                          },*/
                          {
                            title: 'ID',
                            key: 'id',
                            width: 100,
                            render: (_, consumedOrder) => consumedOrder.consumedOrder.id,
                          },
                          {
                            title: ``,
                            key: 'empty',
                          },
                        ]}
                        dataSource={record.consumptions}
                        rowKey={(item, index) => index}
                        pagination={false}
                        scroll={{ x: 'max-content' }}
                        size="small"
                      />
                    </div>
                  ) : null
                )
                // expandedRowKeys: Object.keys(expandedRows).filter(key => expandedRows[key]),
/*                onExpand: (expanded, record) => {
                  if (record.type === 'MT5_SHORT') {
                    setExpandedRows(prev => ({
                      ...prev,
                      [record.id]: expanded
                    }));
                  }
                }*/,
              }}
            />
          ) : (
            <Flex justify="center" style={{ padding: '40px 0' }}>
              <Typography.Text type="secondary">
                Нет истории заказов для этого инструмента
              </Typography.Text>
            </Flex>
          )}
        </div>
      </div>
  )
}

function DepositsWithdrawalsSection({ userId, refreshTrigger }) {
  const [transactions, setTransactions] = useState([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isDialogOpen, setIsDialogOpen] = useState(false);
  const [isDeleteConfirmOpen, setIsDeleteConfirmOpen] = useState(false);
  const [currentTransaction, setCurrentTransaction] = useState(null);
  const [transactionToDelete, setTransactionToDelete] = useState(null);
  const [amount, setAmount] = useState('');
  const [date, setDate] = useState(new Date());
  const [actionType, setActionType] = useState('DEPOSIT');
  const [errors, setErrors] = useState({});

  const fetchTransactions = async () => {
    setIsLoading(true);
    try {
      const response = await api.get(`/users/${userId}/balance-actions`);
      setTransactions(response.data || []);
    } catch (error) {
      console.error('Error fetching deposits:', error);
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchTransactions();
  }, [userId, refreshTrigger]);

  const handleOpenDialog = (transaction = null) => {
    // Reset errors
    setErrors({});

    const now = transaction ? new Date(transaction.date) : new Date();

    if (transaction) {
      // Edit mode
      setCurrentTransaction(transaction);
      setAmount(transaction.amount);
      setDate(now);
      setActionType(transaction.type)
    } else {
      // Add mode
      setCurrentTransaction(null);
      setAmount('');
      setDate(now);
      setActionType('DEPOSIT');
    }
    setIsDialogOpen(true);
  };

  const handleCloseDialog = () => {
    setIsDialogOpen(false);
    setErrors({});
  };

  const handleOpenDeleteConfirm = (transaction) => {
    setTransactionToDelete(transaction);
    setIsDeleteConfirmOpen(true);
  };

  const handleCloseDeleteConfirm = () => {
    setIsDeleteConfirmOpen(false);
    setTransactionToDelete(null);
  };

  const handleDelete = async () => {
    if (!transactionToDelete) return;

    try {
      await api.delete(`/users/${userId}/balance-actions/${transactionToDelete.id}`);

      // Refresh the transactions list
      fetchTransactions();
      handleCloseDeleteConfirm();
    } catch (error) {
      console.error('Error deleting transaction:', error);
    }
  };

  const handleSubmit = async () => {
    // Reset errors
    setErrors({});

    // Validate fields
    const newErrors = {};

    if (!amount || parseFloat(amount) <= 0) {
      newErrors.amount = 'Пожалуйста, введите сумму больше нуля';
    }

    if (!date) {
      newErrors.date = 'Пожалуйста, выберите дату';
    }

    // If there are errors, display them and stop submission
    if (Object.keys(newErrors).length > 0) {
      setErrors(newErrors);
      return;
    }

    try {
      if (currentTransaction) {
        // Update existing transaction
        await api.put(`/users/${userId}/balance-actions/${currentTransaction.id}`, {
          amount: amount,
          type: actionType,
          date: new Date(date)
        });
      } else {
        // Create new transaction
        await api.post(`/users/${userId}/balance-actions`, {
          amount: amount,
          type: actionType,
          date: new Date(date)
        });
      }

      // Refresh the transactions list
      fetchTransactions();
      handleCloseDialog();
    } catch (error) {
      console.error('Error saving transaction:', error);
    }
  };

  const formatDate = (dateString) => {
    const dt = new Date(dateString);
    const hour = dt.getHours().toString().padStart(2, '0');
    const minute = dt.getMinutes().toString().padStart(2, '0');
    const day = dt.getDate().toString().padStart(2, '0');
    const month = (dt.getMonth()+1).toString().padStart(2, '0');
    const year = dt.getFullYear();
    return `${day}.${month}.${year} ${hour}:${minute}`;
  };

  const columns = [
    {
      title: 'Тип',
      dataIndex: 'type',
      key: 'type',
      width: 100,
      render: (type) => (
        <Typography.Text
          style={{
            fontWeight: 500,
            color: type === 'DEPOSIT' ? '#52c41a' : '#ff4d4f'
          }}
        >
          {type === 'DEPOSIT' ? 'Депозит' : 'Вывод'}
        </Typography.Text>
      ),
    },
    {
      title: 'Сумма',
      key: 'amount',
      width: 150,
      render: (_, transaction) => (
        <Typography.Text strong>
          {formatCurrency(transaction.amount, 'USD')}
        </Typography.Text>
      ),
    },
    {
      title: 'Дата',
      dataIndex: 'date',
      key: 'date',
      width: 200,
      render: (date) => formatDate(date),
    },
/*    {
      title: 'Действия',
      key: 'actions',
      width: 120,
      render: (_, transaction) => (
        <Space>
          <Button
            type="text"
            icon={<EditOutlined />}
            onClick={() => handleOpenDialog(transaction)}
          />
          <Button
            type="text"
            danger
            icon={<DeleteOutlined />}
            onClick={() => handleOpenDeleteConfirm(transaction)}
          />
        </Space>
      ),
    }*/,
  ];

  return (
    <>
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <div className="flex flex-col sm:flex-row justify-between items-center gap-2">
        <Typography.Title level={4} style={{ margin: 0 }}>
          Депозиты и выводы
        </Typography.Title>
        <Button
          type="default"
          icon={<PlusOutlined />}
          onClick={() => handleOpenDialog()}
        >
          Добавить запись
        </Button>
      </div>
      <Divider style={{ margin: 0 }} />

      {isLoading ? (
        <Flex justify="center" style={{ padding: '40px 0' }}>
          <Spin size="large" />
        </Flex>
      ) : transactions.length > 0 ? (
        <Table
          style={{maxHeight: '400px', overflow: 'auto'}}
          columns={columns}
          dataSource={transactions}
          rowKey="id"
          scroll={{ x: 'max-content' }}
          pagination={false}
          size="small"
        />
      ) : (
        <Flex justify="center" style={{ padding: '40px 0' }}>
          <Typography.Text type="secondary">
            Нет записей о депозитах и выводах для этого клиента
          </Typography.Text>
        </Flex>
      )}
    </Space>

    <Modal
      title={currentTransaction ? 'Редактировать запись' : 'Добавить новую запись'}
      open={isDialogOpen}
      onCancel={() => setIsDialogOpen(false)}
      footer={[
        <Button key="cancel" onClick={() => setIsDialogOpen(false)}>
          Отмена
        </Button>,
        <Button key="submit" type="primary" onClick={handleSubmit}>
          {currentTransaction ? 'Сохранить' : 'Добавить'}
        </Button>
      ]}
      width={450}
    >
      <Space direction="vertical" size="middle" style={{ width: '100%' }}>
        <Radio.Group
          block
          value={actionType}
          style={{ width: '100%' }}
          options={[
            { label: 'Депозит', value: 'DEPOSIT' },
            { label: 'Вывод', value: 'WITHDRAWAL' }
          ]}
          onChange={(e) => setActionType(e.target.value)}
          defaultValue="DEPOSIT"
          optionType="button"
          buttonStyle="solid"
        />

        <div>
          <InputNumber
            style={{ width: '100%' }}
            placeholder="Сумма"
            formatter={value => `$ ${value}`.replace(/\B(?=(\d{3})+(?!\d))/g, ',')}
            parser={value =>
              value === null || value === void 0 ? void 0 : value.replace(/\$\s?|(,*)/g, '')
            }
            onChange={(e) => setAmount(e || 0)}
            min={0}
            value={amount}
            status={errors.amount ? "error" : undefined}
          />
          {errors.amount && (
            <Typography.Text type="danger" style={{ fontSize: '12px' }}>
              {errors.amount}
            </Typography.Text>
          )}
        </div>

        {/*<div>
              <Space.Compact style={{ width: '100%' }}>
                <DatePicker
                  placeholder="Дата"
                  style={{ width: '100%' }}
                  showTime
                  format="DD.MM.YYYY HH:mm"
                  timeFormat="HH:mm"
                  onChange={(value, dateString) => {
                    console.log('Selected Time: ', value);
                    console.log('Formatted Selected Time: ', dateString);
                    setDate(value);
                  }}
                />
              </Space.Compact>
              {errors.date && (
                <Typography.Text type="danger" style={{ fontSize: '12px' }}>
                  {errors.date}
                </Typography.Text>
              )}
            </div>*/}
      </Space>
    </Modal>

    {/* Modal for confirming deletion */}
    <Modal
      title="Подтверждение удаления"
      open={isDeleteConfirmOpen}
      onCancel={handleCloseDeleteConfirm}
      footer={[
        <Button key="cancel" onClick={handleCloseDeleteConfirm}>
          Отмена
        </Button>,
        <Button key="delete" type="primary" danger onClick={handleDelete}>
          Удалить
        </Button>
      ]}
      width={450}
    >
      <Typography.Text style={{ fontSize: '16px' }}>
        Вы уверены, что хотите удалить эту запись?
      </Typography.Text>
    </Modal>
    </>

  );
}

function UserPage({}) {
  const { token } = theme.useToken();
  const { userId } = useParams();
  const [activeTab, setActiveTab] = useState('');
  const [user, setUser] = useState(null);
  const [instruments, setInstruments] = useState([]);
  const [loading, setLoading] = useState(true);
  const [selectedInstrument, setSelectedInstrument] = useState(null);
  const [refreshCounter, setRefreshCounter] = useState(0);
  const [isInstrumentModalOpen, setIsInstrumentModalOpen] = useState(false);
  const [selectedInstrumentOption, setSelectedInstrumentOption] = useState(['EUR/USD']);
  const [isUpdatingInstruments, setIsUpdatingInstruments] = useState(false);
  const [unrealizedPnL, setUnrealizedPnL] = useState(null);
  const [loadingUnrealizedPnL, setLoadingUnrealizedPnL] = useState(false);
  const [servicePnL, setServicePnL] = useState(null);
  const [loadingServicePnL, setLoadingServicePnL] = useState(false);

  // API endpoints for data fetching

  const fetchUnrealizedPnL = async (telegramId, instrumentId) => {
    if (!telegramId || !instrumentId) return;
    
    setLoadingUnrealizedPnL(true);
    try {
      const response = await api.get(`/users/${telegramId}/instruments/${instrumentId}/unrealized`);
      setUnrealizedPnL(response.data.data);
    } catch (error) {
      console.error('Error fetching unrealized PnL:', error);
      setUnrealizedPnL(null);
    } finally {
      setLoadingUnrealizedPnL(false);
    }
  };

  const fetchServicePnL = async (telegramId, instrumentId) => {
    if (!telegramId || !instrumentId) return;
    
    setLoadingServicePnL(true);
    try {
      const response = await api.get(`/users/${telegramId}/instruments/${instrumentId}/house-pnl`);
      setServicePnL(response.data.data);
    } catch (error) {
      console.error('Error fetching service PnL:', error);
      setServicePnL(null);
    } finally {
      setLoadingServicePnL(false);
    }
  };

  const fetchData = async () => {
    setLoading(true);
    try {
      // Fetch user data
      const userResponse = await api.get(`/users/${userId}`);
      setUser(userResponse.data);

      // Fetch instruments separately using the new endpoint
      const instrumentsResponse = await api.get(`/users/${userResponse.data.telegramId}/instruments`);
      const instrumentsResp = instrumentsResponse.data.data || [];
      instrumentsResp.sort((a, b) => a.label.localeCompare(b.label));
      setInstruments(instrumentsResp);

      // Set default active tab to first instrument if available
      if (instrumentsResp.length > 0) {
        const firstInstrument = instrumentsResp[0];
        setActiveTab(firstInstrument.label);
        setSelectedInstrument(firstInstrument);
        // Fetch unrealized PnL, house unrealized PnL, and service PnL for the first instrument
        await fetchUnrealizedPnL(userResponse.data.telegramId, firstInstrument.id);
        await fetchServicePnL(userResponse.data.telegramId, firstInstrument.id);
      }

      // Increment refresh counter to trigger child component refreshes
      setRefreshCounter(prev => prev + 1);
    } catch (error) {
      console.error('Error fetching data:', error);
      // You might want to set an error state here to display to the user
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchData();
  }, [userId]);

  const handleTabChange = async (instrumentName) => {
    setActiveTab(instrumentName);
    const instrument = instruments.find(i => i.label === instrumentName);
    setSelectedInstrument(instrument);
    
    // Fetch unrealized PnL, house unrealized PnL, and service PnL for the selected instrument
    if (user && instrument) {
      await fetchUnrealizedPnL(user.telegramId, instrument.id);
      await fetchServicePnL(user.telegramId, instrument.id);
    }
    
    // Reload user instruments when tab changes
    setRefreshCounter(prev => prev + 1);
  };

  const handleInstrumentChange = async (selectedInstruments) => {
    try {
      // Map checkbox selections to the correct DTO format
      const instrumentsDto = {
        eurUsd: selectedInstruments.includes('EUR/USD'),
        cadUsd: selectedInstruments.includes('USD/CAD')
      };

      console.log('Updating instruments:', instrumentsDto);

      // Make API call to update available instruments
      await api.post(`/users/${user.telegramId}/instruments/update-available-instruments`, instrumentsDto);

      console.log('Instruments updated successfully');

      // Refresh user data after successful update
      await fetchData();
    } catch (error) {
      console.error('Error updating instruments:', error);
      // You might want to show a notification to the user here
    }
  };

  const handleOpenInstrumentModal = () => {
    // Set initial state based on user's current available instruments
    const currentInstruments = instruments.map(instrument => instrument.label);
    setSelectedInstrumentOption(currentInstruments);
    setIsInstrumentModalOpen(true);
  };

  const handleCloseInstrumentModal = () => {
    setIsInstrumentModalOpen(false);
  };

  const handleInstrumentSelection = async () => {
    if (handleInstrumentChange) {
      setIsUpdatingInstruments(true);
      try {
        await handleInstrumentChange(selectedInstrumentOption);
        message.success('Инструменты успешно обновлены');
        setIsInstrumentModalOpen(false);
      } catch (error) {
        message.error('Ошибка при обновлении инструментов');
      } finally {
        setIsUpdatingInstruments(false);
      }
    }
  };

  if (loading) {
    return <LoadingUserPage />;
  }

  if (!user) {
    return <UserNotFound />;
  }

  let detailsComponent;
  switch (selectedInstrument?.mode) {
    case 'SHORT':
      detailsComponent = <RegularHedgeDetails user={user} selectedInstrument={selectedInstrument} userId={userId} refreshTrigger={refreshCounter}
                                              unrealizedPnL={unrealizedPnL} loadingUnrealizedPnL={loadingUnrealizedPnL}
                                              />;
      break;
    case 'LONG':
      detailsComponent = <InverseHedgeDetails user={user} selectedInstrument={selectedInstrument} userId={userId} refreshTrigger={refreshCounter}
                                              unrealizedPnL={unrealizedPnL} loadingUnrealizedPnL={loadingUnrealizedPnL}
                                              servicePnL={servicePnL} loadingServicePnL={loadingServicePnL} />;
      break;
    default:
      detailsComponent = (
        <Flex justify="center" style={{ padding: '40px 0' }}>
          <Typography.Text type="secondary">
            Выберите инструмент для просмотра информации
          </Typography.Text>
        </Flex>
      );
      break;
  }

  const tabItems = instruments.map((instrument) => ({
    key: instrument.label,
    label: instrument.label,
    children: selectedInstrument?.label === instrument.label ? detailsComponent : null,
  }));

  const operations = <InstrumentToolbar selectedInstrument={selectedInstrument} userId={userId} user={user} />;

  return (
    <div style={{ minHeight: '100vh', backgroundColor: token.colorBgLayout }}>
      <UserPageHeader onRefresh={fetchData} isRefreshing={loading} user={user} />

      <div style={{ maxWidth: '1400px', margin: '0 auto', paddingTop: '104px' }} className="px-2 md:px-4">
        {/* User Info Section */}
        <UserInfoSection user={user} />

        <Mt5DataSection userId={userId} refreshTrigger={refreshCounter}/>

        <UserDepositsAndWithdrawals userId={userId} refreshTrigger={refreshCounter}/>

        {/* Instruments Section */}
        <Card style={{ marginBottom: '24px' }}>
          <Space direction="vertical" size="middle" style={{ width: '100%' }}>
            <div className="flex flex-col sm:flex-row justify-between items-center gap-2">
              <Typography.Title level={3} style={{ margin: 0 }}>
                Доступные инструменты
              </Typography.Title>
              <Button
                onClick={handleOpenInstrumentModal}
                icon={<EditOutlined />}
              >
                Изменить инструменты
              </Button>
            </div>
            <Divider style={{ margin: 0 }} />

            {/* Tabs */}
            <Tabs
              activeKey={activeTab}
              onChange={handleTabChange}
              items={tabItems}
              tabBarExtraContent={operations}
              type="card"
            />
          </Space>
        </Card>
      </div>

      {/* Instrument Selection Modal */}
      <Modal
        title="Изменить доступные инструменты"
        open={isInstrumentModalOpen}
        onCancel={isUpdatingInstruments ? undefined : handleCloseInstrumentModal}
        closable={!isUpdatingInstruments}
        maskClosable={!isUpdatingInstruments}
        footer={[
          <Button key="cancel" onClick={handleCloseInstrumentModal} disabled={isUpdatingInstruments}>
            Отмена
          </Button>,
          <Button key="submit" type="primary" onClick={handleInstrumentSelection} loading={isUpdatingInstruments}>
            Применить
          </Button>
        ]}
        width={450}
      >
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          <Typography.Text>
            Выберите доступные инструменты для пользователя:
          </Typography.Text>
          <Checkbox.Group
            value={selectedInstrumentOption}
            onChange={(checkedValues) => setSelectedInstrumentOption(checkedValues)}
            style={{ width: '100%' }}
          >
            <Space direction="vertical" style={{ width: '100%' }}>
              <Checkbox value="EUR/USD">EUR/USD</Checkbox>
              <Checkbox value="USD/CAD">USD/CAD</Checkbox>
            </Space>
          </Checkbox.Group>
        </Space>
      </Modal>
    </div>
  );
}

export default UserPage;
