import {useEffect, useState} from 'react';
import {useParams} from 'react-router-dom';
import api from '../api';
import {
  Button,
  Card,
  Checkbox,
  Descriptions,
  Divider,
  Dropdown,
  Flex,
  InputNumber,
  message,
  Modal,
  notification,
  Popconfirm,
  Space,
  Table,
  Tabs,
  Tag,
  theme,
  Typography
} from 'antd';
import {DownOutlined, EditOutlined} from "@ant-design/icons";
import {LoadingUserPage} from "./components/LoadingUserPage.jsx";
import {UserNotFound} from "./components/UserNotFound.jsx";
import {UserPageHeader} from "./components/UserPageHeader.jsx";
import {UserInfoSection} from "./components/UserInfoSection.jsx";
import {Mt5DataSection} from "./components/Mt5DataSection.jsx";
import {formatCurrency} from "../utils.js";
import {DepositsWithdrawalsSection} from "./components/DepositsWithdrawalsSection.jsx";
import {CommissionWithdrawalsSection} from "./components/ComissionWithdrawalsSection.jsx";


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
  const [commissionPercentage, setCommissionPercentage] = useState(0.1);
  const [isCommissionModalVisible, setIsCommissionModalVisible] = useState(false);

  const recalculateInnerGoogleSheet = async () => {
    try {
      if (selectedInstrument) {
        // Fetch order history for the selected instrument
        const recalculateResponse = await api.post(`/users/${userId}/instruments/${selectedInstrument.id}/recalculate-inner-ggl-sheet`);
        if (recalculateResponse.data.success) {
          notificationApi['success']({
            message: 'Внутрение расчеты отправлены на обновление.',
            description:
              'Внутрение расчеты отправлены на обновление. Пожалуйста, подождите.',
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

  const recalculateServicePnl = async () => {
    try {
      if (selectedInstrument) {
        // Fetch order history for the selected instrument
        const recalculateResponse = await api.post(`/users/${userId}/instruments/${selectedInstrument.id}/recalculate-service-pnl`);
        if (recalculateResponse.data.success) {
          notificationApi['success']({
            message: 'PnL сервиса отправлен на обновление.',
            description:
              'PnL сервиса отправлен на обновление. Пожалуйста, подождите.',
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

  const handleCommissionChange = async (newPercentage) => {
    try {
      if (selectedInstrument && newPercentage >= 0 && newPercentage <= 100) {
        // API call to update commission percentage for the instrument
          const commissionResponse = await api.post(`/users/${userId}/instruments/${selectedInstrument.id}/commission`, {
          percentage: newPercentage.toString()
        });
        if (commissionResponse.data.success) {
          setCommissionPercentage(newPercentage);
          notificationApi['success']({
            message: 'Комиссия обновлена',
            description: `Процент комиссии установлен на ${newPercentage}%`,
          });
        } else {
          notificationApi['error']({
            message: 'Ошибка',
            description: 'Не удалось обновить процент комиссии.',
          });
        }
      }
    } catch (error) {
      console.error('Error updating commission:', error);
      notificationApi['error']({
        message: 'Ошибка',
        description: 'Что-то пошло не так при обновлении комиссии.',
      });
    }
  };

  const showCommissionModal = () => {
    setCommissionPercentage(selectedInstrument.commissionPercentage);
    setIsCommissionModalVisible(true);
  };

  const handleCommissionModalOk = () => {
    handleCommissionChange(commissionPercentage);
    setIsCommissionModalVisible(false);
  };

  const handleCommissionModalCancel = () => {
    setIsCommissionModalVisible(false);
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
      key: 'recalc_inner_ggl_sheet',
      label: (
        <a onClick={recalculateInnerGoogleSheet}>
          Выполнить пересчет внутренней таблицы
        </a>
      ),
    },
    {
      key: 'recalc_service_pnl',
      label: (
        <a onClick={recalculateServicePnl}>
          Выполнить пересчет pnl сервиса
        </a>
      ),
    },
    {
      key: 'configure_commission',
      label: (
        <a onClick={showCommissionModal}>
          Настроить процент комиссии
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

      <Modal
        title="Настроить процент комиссии"
        open={isCommissionModalVisible}
        onOk={handleCommissionModalOk}
        onCancel={handleCommissionModalCancel}
        okText="Сохранить"
        cancelText="Отмена"
      >
        <p>Текущий инструмент: {selectedInstrument?.label}</p>
        <div style={{ marginTop: '16px' }}>
          <label>Процент комиссии (%): </label>
          <InputNumber
            min={0}
            max={100}
            step={0.01}
            value={commissionPercentage}
            onChange={setCommissionPercentage}
            style={{ width: '120px' }}
          />
        </div>
      </Modal>
    </div>
  );
}

function RegularHedgeDetails({selectedInstrument, userId, refreshTrigger, unrealizedPnL, loadingUnrealizedPnL, servicePnL, loadingServicePnL}) {
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
      title: 'Реализ. курсовая',
      dataIndex: 'pnlExcel',
      key: 'pnlExcel',
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
      title: 'Коммиссия Герчик',
      dataIndex: 'commission',
      key: 'commission',
      render: (commission) => (formatCurrency(commission))
    },
    {
      title: 'Swap',
      dataIndex: 'swap',
      key: 'swap',
      render: (swap) => (formatCurrency(swap))
    },
    {
      title: 'MT5 прибыль',
      dataIndex: 'mt5profit',
      key: 'mt5profit',
      render: (mt5profit) => (formatCurrency(mt5profit))
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
      width: 300,
    },
  ];

  return (
    <div>
      {/* Ownership Info */}
      <Space size="small" style={{ marginBottom: '24px' }}>
        {/*Mobile version*/}
        <Descriptions className="sm:hidden"
          title={<Typography.Title level={4}>Информация об инструменте</Typography.Title>}
          size="small"
        >
          <Descriptions.Item label={`${selectedInstrument.base} Клиента`}>
            {formatCurrency(selectedInstrument.clientOwnedBaseAmount, selectedInstrument.base)}
          </Descriptions.Item>
          <Descriptions.Item label="Баланс USD">
            {selectedInstrument.balance !== null ? formatCurrency(selectedInstrument.balance) : "Нет данных"}
          </Descriptions.Item>
          <Descriptions.Item label="Нериализованный PnL Клиента">
            {loadingUnrealizedPnL ? "Загрузка..." : (unrealizedPnL !== null ? formatCurrency(unrealizedPnL.profit) : "Нет данных")}
          </Descriptions.Item>

          <Divider />

          <Descriptions.Item label={`Swap`}>
            {formatCurrency(selectedInstrument.swap)}
          </Descriptions.Item>
          <Descriptions.Item label="Разница с МТ5 (PnL сервиса)">
            {formatCurrency(selectedInstrument.diffWithMt5, 'USD', 'en-US', 4)}
          </Descriptions.Item>
          <Descriptions.Item label={`${selectedInstrument.base} Сервиса`}>
            {formatCurrency(selectedInstrument.houseOwnedBaseAmount, selectedInstrument.base)}
          </Descriptions.Item>

          <Divider />

          <Descriptions.Item label="Процент комиссии">
            {selectedInstrument.commissionPercentage}%
          </Descriptions.Item>
          <Descriptions.Item label="Текущий курс">
            {loadingUnrealizedPnL ? "Загрузка..." : (unrealizedPnL !== null ? unrealizedPnL.rate : "Нет данных")}
          </Descriptions.Item>
        </Descriptions>

        {/*Desktop version*/}
        <Descriptions className="hidden sm:block"
          title={<Typography.Title level={4}>Информация об инструменте</Typography.Title>}
          size="small"
        >
          <Descriptions.Item label={`${selectedInstrument.base} Клиента`}>
            {formatCurrency(selectedInstrument.clientOwnedBaseAmount, selectedInstrument.base)}
          </Descriptions.Item>
          <Descriptions.Item label={`Swap`}>
            {formatCurrency(selectedInstrument.swap)}
          </Descriptions.Item>
          <Descriptions.Item label="Процент комиссии">
            {selectedInstrument.commissionPercentage}%
          </Descriptions.Item>


          <Descriptions.Item label="Баланс USD">
            {selectedInstrument.balance !== null ? formatCurrency(selectedInstrument.balance) : "Нет данных"}
          </Descriptions.Item>
          <Descriptions.Item label="Разница с МТ5 (PnL сервиса)">
            {formatCurrency(selectedInstrument.diffWithMt5,'USD', 'en-US', 4)}
          </Descriptions.Item>
          <Descriptions.Item label="Текущий курс">
            {loadingUnrealizedPnL ? "Загрузка..." : (unrealizedPnL !== null ? unrealizedPnL.rate : "Нет данных")}
          </Descriptions.Item>


          <Descriptions.Item label="Нериализованный PnL Клиента">
            {loadingUnrealizedPnL ? "Загрузка..." : (unrealizedPnL !== null ? formatCurrency(unrealizedPnL.profit) : "Нет данных")}
          </Descriptions.Item>
          <Descriptions.Item label={`${selectedInstrument.base} Сервиса`}>
            {formatCurrency(selectedInstrument.houseOwnedBaseAmount, selectedInstrument.base)}
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

          {/* Mobile version */}
          <Descriptions className="sm:hidden"
                        title={<Typography.Title level={4}>Информация об инструменте</Typography.Title>}
                        size="small"
          >
            <Descriptions.Item label={`${selectedInstrument.quote} Клиента`}>
              {formatCurrency(selectedInstrument.clientOwnedQuoteAmount, selectedInstrument.quote)}
            </Descriptions.Item>
            <Descriptions.Item label={`${selectedInstrument.base} эквивалент Клиента`}>
              {formatCurrency(selectedInstrument.clientOwnedBaseAmount, selectedInstrument.base)}
            </Descriptions.Item>
            <Descriptions.Item label="Баланс USD">
              {selectedInstrument.balance !== null ? formatCurrency(selectedInstrument.balance) : "Нет данных"}
            </Descriptions.Item>
            <Descriptions.Item label="Нериализованный PnL Клиента">
              {loadingUnrealizedPnL ? "Загрузка..." : (unrealizedPnL !== null ? formatCurrency(unrealizedPnL.profit) : "Нет данных")}
            </Descriptions.Item>

            <Divider />

            <Descriptions.Item label={`Swap`}>
              {formatCurrency(selectedInstrument.swap)}
            </Descriptions.Item>
            <Descriptions.Item label="Разница с МТ5 (PnL сервиса)">
              {formatCurrency(selectedInstrument.diffWithMt5,'USD', 'en-US', 4)}
            </Descriptions.Item>
            <Descriptions.Item label={`${selectedInstrument.base} Сервиса`}>
              {formatCurrency(selectedInstrument.houseOwnedBaseAmount, selectedInstrument.base)}
            </Descriptions.Item>

            <Divider />

            <Descriptions.Item label="Процент комиссии">
              {selectedInstrument.commissionPercentage}%
            </Descriptions.Item>
            <Descriptions.Item label="Текущий курс">
              {loadingUnrealizedPnL ? "Загрузка..." : (unrealizedPnL !== null ? unrealizedPnL.rate : "Нет данных")}
            </Descriptions.Item>
          </Descriptions>

          {/* Desktop version */}
          <Descriptions className="hidden sm:block"
            title={<Typography.Title level={4}>Информация об инструменте</Typography.Title>}
            size="small"
          >
            <Descriptions.Item label={`${selectedInstrument.quote} Клиента`}>
              {formatCurrency(selectedInstrument.clientOwnedQuoteAmount, selectedInstrument.quote)}
            </Descriptions.Item>
            <Descriptions.Item label={`Swap`}>
              {formatCurrency(selectedInstrument.swap)}
            </Descriptions.Item>
            <Descriptions.Item label="Процент комиссии">
              {selectedInstrument.commissionPercentage}%
            </Descriptions.Item>

            <Descriptions.Item label={`${selectedInstrument.base} эквивалент Клиента`}>
              {formatCurrency(selectedInstrument.clientOwnedBaseAmount, selectedInstrument.base)}
            </Descriptions.Item>
            <Descriptions.Item label="Разница с МТ5 (PnL сервиса)">
              {formatCurrency(selectedInstrument.diffWithMt5,'USD', 'en-US', 4)}
            </Descriptions.Item>
            <Descriptions.Item label="Текущий курс">
              {loadingUnrealizedPnL ? "Загрузка..." : (unrealizedPnL !== null ? unrealizedPnL.rate : "Нет данных")}
            </Descriptions.Item>

            
            <Descriptions.Item label="Баланс USD">
              {selectedInstrument.balance !== null ? formatCurrency(selectedInstrument.balance) : "Нет данных"}
            </Descriptions.Item>
            <Descriptions.Item label={`${selectedInstrument.base} Сервиса`}>
              {formatCurrency(selectedInstrument.houseOwnedBaseAmount, selectedInstrument.base)}
            </Descriptions.Item>
            <Descriptions.Item></Descriptions.Item>


            <Descriptions.Item label="Нериализованный PnL Клиента">
              {loadingUnrealizedPnL ? "Загрузка..." : (unrealizedPnL !== null ? formatCurrency(unrealizedPnL.profit) : "Нет данных")}
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
                  title: 'Реализ. курсовая',
                  dataIndex: 'pnlExcel',
                  key: 'pnlExcel',
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
                  title: 'Коммиссия Герчик',
                  dataIndex: 'commission',
                  key: 'commission',
                  render: (commission) => (formatCurrency(commission))
                },
                {
                  title: 'Swap',
                  dataIndex: 'swap',
                  key: 'swap',
                  render: (swap) => (formatCurrency(swap))
                },
                {
                  title: 'MT5 прибыль',
                  dataIndex: 'mt5profit',
                  key: 'mt5profit',
                  render: (mt5profit) => (formatCurrency(mt5profit))
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

function UserPage() {
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
        // await fetchUnrealizedPnL(userResponse.data.telegramId, firstInstrument.id);
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

  // Refresh urealized PnL every 5 sec.
  useEffect(() => {
    if (!userId || !selectedInstrument) return;
    fetchUnrealizedPnL(userId, selectedInstrument.id);
    const id = setInterval(() => {
      fetchUnrealizedPnL(userId, selectedInstrument.id);
    }, 15000);

    return () => clearInterval(id);
  }, [userId, selectedInstrument]);

  const handleTabChange = async (instrumentName) => {
    setActiveTab(instrumentName);
    const instrument = instruments.find(i => i.label === instrumentName);
    setSelectedInstrument(instrument);
    
    if (user && instrument) {
      // await fetchUnrealizedPnL(user.telegramId, instrument.id);
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
                                              servicePnL={servicePnL} loadingServicePnL={loadingServicePnL}
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

        <Card style={{ marginBottom: '24px' }}>
          <CommissionWithdrawalsSection userId={userId} refreshTrigger={refreshCounter} />
        </Card>

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
