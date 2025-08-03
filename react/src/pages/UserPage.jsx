import {useState, useEffect} from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import api from '../api';
import {
  Card,
  Button,
  Table,
  Tabs,
  Typography,
  Flex,
  Space,
  Input,
  Segmented,
  Modal,
  Spin,
  Divider,
  Grid,
  Row,
  Col,
  theme, InputNumber, DatePicker, Radio, Collapse, Statistic, Select, Checkbox, message
} from 'antd';
import {
  ArrowLeftOutlined,
  DownOutlined,
  RightOutlined,
  ExportOutlined,
  PlusOutlined,
  EditOutlined,
  DeleteOutlined, DownloadOutlined, LinkOutlined
} from "@ant-design/icons";

const formatCurrency = (amount, currency = 'USD', locale = 'en-US') => {
  return new Intl.NumberFormat(locale, {
    style: 'currency',
    currency: currency
  }).format(amount);
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
  return (
    <Card style={{ marginBottom: '24px' }}>
      <Space direction="vertical" size="middle" style={{ width: '100%' }}>
        <Typography.Title level={3} style={{ margin: 0 }}>
          Информация о пользователе
        </Typography.Title>
        <Divider style={{ margin: 0 }} />
        <Row gutter={[8, 8]}>
          <Col xs={24} sm={12} md={8}>
            <Card size="small">
              <Space direction="vertical" size="small">
                <Statistic title="Telegram ID" value={user.telegramId} formatter={(val)=>val} />
              </Space>
            </Card>
          </Col>
          <Col xs={24} sm={12} md={8}>
            <Card size="small">
              <Space direction="vertical" size="small">
                <Statistic title="Имя" value={user.firstName} />
              </Space>
            </Card>
          </Col>
          <Col xs={24} sm={12} md={8}>
            <Card size="small">
              <Space direction="vertical" size="small">
                <Statistic title="Имя пользователя" value={user.username} />
              </Space>
            </Card>
          </Col>
          <Col xs={24} sm={12} md={8}>
            <Card size="small">
              <Space direction="vertical" size="small">
                <Statistic title="Тип аккаунта Герчик" value={user.accountType} />
              </Space>
            </Card>
          </Col>
          <Col xs={24} sm={12} md={8}>
            <Card size="small">
              <Space direction="vertical" size="small">
                <Statistic title="№ аккаунта Герчик" value={user.mt5Account} />
              </Space>
            </Card>
          </Col>
          <Col xs={24} sm={12} md={8}>
            <Card size="small">
              <Space direction="vertical" size="small">
                <Statistic title="Google Sheet" value={user.googleSheetId}
                formatter={(val) =>
                    val ? (
                    <Button type="link" href={val} size="large" target="_blank"
                    icon={<LinkOutlined />}
                    >Открыть
                    </Button>)
                      : (<span>-</span>)
                }
                />
              </Space>
            </Card>
          </Col>
        </Row>
      </Space>
    </Card>
  );
}

function formatOrderType(type) {
  switch (type) {
    case 'MT5_LONG':
      return 'Покупка';
    case 'MT5_SHORT':
      return 'Продажа';
    default:
      return type;
  }
}

function RegularHedgeDetails({selectedInstrument, userId, refreshTrigger}) {
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
            color: type === 'MT5_LONG' ? '#52c41a' : '#ff4d4f'
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
    <div style={{ paddingTop: '16px' }}>
      {/* Ownership Info */}
      <Row gutter={[8, 8]} style={{ marginBottom: '24px' }}>
        <Col xs={24} sm={12} md={8}>
          <Card size="small">
            <Flex direction="vertical" gap="small">
              <Statistic title={`${selectedInstrument.base}  Клиента`}
                         value={selectedInstrument.clientOwnedBaseAmount} />
            </Flex>
          </Card>
        </Col>
        <Col xs={24} sm={12} md={8}>
          <Card size="small">
            <Flex direction="vertical" gap="small">
              <Statistic title={`Баланс`}
                         value={selectedInstrument.balance} />
            </Flex>
          </Card>
        </Col>
      </Row>

      {/* Deposits and Withdrawals Section */}
      <DepositsWithdrawalsSection
        userId={userId}
        instrumentId={selectedInstrument.id}
        refreshTrigger={refreshTrigger}
        instrumentInfo={selectedInstrument}
      />

      {/* Order History */}
      <div>
        <Typography.Title level={4} style={{ marginBottom: '16px' }}>
          История ордеров
        </Typography.Title>
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

function InverseHedgeDetails({selectedInstrument, userId, refreshTrigger}) {
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
        console.log('positionsResponse.data', positionsResponse.data);
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
      <div style={{ paddingTop: '16px' }}>
        {/* Ownership Info */}
        <Row gutter={[8, 8]} style={{ marginBottom: '24px' }}>
          <Col xs={24} sm={8} md={8}>
            <Card size="small">
              <Flex direction="vertical" gap="small">
                <Statistic title={`${selectedInstrument.quote}  Клиента`}
                           value={selectedInstrument.clientOwnedQuoteAmount} />
              </Flex>
            </Card>
          </Col>
          <Col xs={24} sm={8} md={8}>
            <Card size="small">
              <Flex direction="vertical" gap="small">
                <Statistic title={`${selectedInstrument.base}  Клиента`}
                           value={selectedInstrument.clientOwnedBaseAmount} />
              </Flex>
            </Card>
          </Col>
          <Col xs={24} sm={8} md={8}>
            <Card size="small">
              <Flex direction="vertical" gap="small">
                <Statistic title={`${selectedInstrument.base} Сервиса`}
                           value={selectedInstrument.houseOwnedBaseAmount} />
              </Flex>
            </Card>
          </Col>
          <Col xs={24} sm={8} md={8}>
            <Card size="small">
              <Flex direction="vertical" gap="small">
                <Statistic title="Баланс"
                           value={selectedInstrument.balance} />
              </Flex>
            </Card>
          </Col>
        </Row>

        {/* Deposits and Withdrawals Section */}
        <DepositsWithdrawalsSection
          userId={userId}
          instrumentId={selectedInstrument.id}
          refreshTrigger={refreshTrigger}
          instrumentInfo={selectedInstrument}
        />

        {/* Open Positions */}
        {/* No sense to show them for regular hedge */}
        <div style={{ marginBottom: '24px' }}>
          <Typography.Title level={4} style={{ marginBottom: '16px' }}>
            Текущие открытые позиции
          </Typography.Title>
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
                  width: 200,
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
                          color: type === 'MT5_LONG' ? '#52c41a' : '#ff4d4f'
                        }}
                      >
                        {formatOrderType(type)}
                      </Typography.Text>
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
                          {
                            title: `Еквивалент в ${selectedInstrument.base}`,
                            dataIndex: 'baseEquivalent',
                            key: 'baseEquivalent',
                            width: 200,
                            render: (amount) => (
                              <Typography.Text strong>
                                {formatCurrency(amount, selectedInstrument.base)}
                              </Typography.Text>
                            ),
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

function DepositsWithdrawalsSection({ userId, instrumentId, refreshTrigger, instrumentInfo }) {
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
      const response = await api.get(`/users/${userId}/instruments/${instrumentId}/balance-actions`);
      setTransactions(response.data || []);
    } catch (error) {
      console.error('Error fetching deposits:', error);
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    if (instrumentId) {
      fetchTransactions();
    }
  }, [userId, instrumentId, refreshTrigger]);

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
      await api.delete(`/users/${userId}/instruments/${instrumentId}/balance-actions/${transactionToDelete.id}`);

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
        await api.put(`/users/${userId}/instruments/${instrumentId}/balance-actions/${currentTransaction.id}`, {
          amount: amount,
          type: actionType,
          date: new Date(date)
        });
      } else {
        // Create new transaction
        await api.post(`/users/${userId}/instruments/${instrumentId}/balance-actions`, {
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
          Депозиты и выводы {instrumentInfo && `(${instrumentInfo.label})`}
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
            Нет записей о депозитах и выводах для этого инструмента
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

function UserPage({ toggleTheme, themeMode }) {
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

  // API endpoints for data fetching

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

  const handleTabChange = (instrumentName) => {
    setActiveTab(instrumentName);
    const instrument = instruments.find(i => i.label === instrumentName);
    setSelectedInstrument(instrument);
    
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
      detailsComponent = <RegularHedgeDetails selectedInstrument={selectedInstrument} userId={userId} refreshTrigger={refreshCounter} />;
      break;
    case 'LONG':
      detailsComponent = <InverseHedgeDetails selectedInstrument={selectedInstrument} userId={userId} refreshTrigger={refreshCounter} />;
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

  return (
    <div style={{ minHeight: '100vh', backgroundColor: token.colorBgLayout }}>
      <UserPageHeader onRefresh={fetchData} isRefreshing={loading} user={user} />

      <div style={{ maxWidth: '1400px', margin: '0 auto', padding: '24px', paddingTop: '104px' }}>
        {/* User Info Section */}
        <UserInfoSection user={user} />

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
