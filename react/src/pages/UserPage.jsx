import {useState, useEffect} from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import api from '../api';
import {
  Container,
  Box,
  Heading,
  Text,
  Button,
  Card,
  Flex,
  Table,
  Tabs,
  Grid,
  ScrollArea,
  Separator,
  Link,
  TextField,
  SegmentedControl,
  Dialog,
  IconButton,
} from '@radix-ui/themes';
import {
  ArrowLeftIcon,
  ChevronDownIcon,
  ChevronRightIcon,
  OpenInNewWindowIcon,
  PlusIcon,
  Pencil1Icon,
  TrashIcon
} from '@radix-ui/react-icons';

function LoadingUserPage() {
  return (
      <Box style={{ minHeight: '100vh', backgroundColor: 'var(--gray-2)' }}>
        <Flex align="center" justify="center" style={{ height: '100vh' }}>
          <Text size="5" color="gray">Загрузка...</Text>
        </Flex>
      </Box>
  );
}

function UserNotFound() {
  const navigate = useNavigate();

  const handleBack = () => {
    navigate('/main');
  };

  return (
      <Box style={{ minHeight: '100vh', backgroundColor: 'var(--gray-2)' }}>
        <Flex align="center" justify="center" style={{ height: '100vh' }}>
          <Flex direction="column" align="center" gap="4">
            <Text size="5" color="red" weight="bold">Пользователь не найден</Text>
            <Button onClick={handleBack} size="3" color="cyan">
              <ArrowLeftIcon />
              Назад к поиску
            </Button>
          </Flex>
        </Flex>
      </Box>
  );
}

function UserPageHeader({ onRefresh, isRefreshing, user }) {
  const navigate = useNavigate();

  const handleBack = () => {
    navigate('/main');
  };

  // Determine which user identifier to display
  const userIdentifier = user ? (user.username || user.firstName || user.telegramId) : '';

  return (
      <Box style={{
        backgroundColor: 'var(--color-panel-solid)',
        boxShadow: 'var(--shadow-2)',
        position: 'fixed',
        top: 0,
        left: 0,
        width: '100%',
        zIndex: 999
      }}>
        <Container size="3">
          <Flex
              justify={{ initial: "center", sm: "between" }}
              align="center"
              py="5"
              direction={{ initial: "column", sm: "row" }}
              gap={{ initial: "4", sm: "0" }}
          >
            <Heading size={{ initial: "5", sm: "6" }}>
              Детали пользователя {userIdentifier && `- ${userIdentifier}`}
            </Heading>
            <Flex gap="3">
              <Button
                onClick={onRefresh}
                size="3"
                color="green"
                disabled={isRefreshing}
              >
                <Text size={{ initial: "2", sm: "3" }}>
                  {isRefreshing ? 'Обновление...' : 'Обновить'}
                </Text>
              </Button>
              <Button onClick={handleBack} size="3" color="cyan">
                <ArrowLeftIcon />
                <Text size={{ initial: "2", sm: "3" }}>Назад к поиску</Text>
              </Button>
            </Flex>
          </Flex>
        </Container>
      </Box>
  );
}

function UserInfoSection({user}) {
  return (
      <Card size="2" mb="6">
        <Flex direction="column" gap="3">
          <Heading size="4">Информация о пользователе</Heading>
          <Separator size="4" />
          {/* Responsive grid that changes from 1 column on mobile to 3 columns on desktop */}
          <Grid columns={{ initial: "1", sm: "2", md: "3" }} gap="4">
            <Card size="1">
              <Flex direction="column" gap="1" p="2">
                <Text size="2" color="gray">Telegram ID</Text>
                <Text size="4" weight="medium">{user.telegramId}</Text>
              </Flex>
            </Card>
            <Card size="1">
              <Flex direction="column" gap="1" p="2">
                <Text size="2" color="gray">Имя</Text>
                <Text size="4" weight="medium">{user.firstName}</Text>
              </Flex>
            </Card>
            <Card size="1">
              <Flex direction="column" gap="1" p="2">
                <Text size="2" color="gray">Имя пользователя</Text>
                <Text size="4" weight="medium">{user.username}</Text>
              </Flex>
            </Card>
            <Card size="1">
              <Flex direction="column" gap="1" p="2">
                <Text size="2" color="gray">Тип аккаунта Герчик</Text>
                <Text size="4" weight="medium">{user.accountType}</Text>
              </Flex>
            </Card>
            <Card size="1">
              <Flex direction="column" gap="1" p="2">
                <Text size="2" color="gray">№ аккаунта Герчик</Text>
                <Text size="4" weight="medium">{user.mt5Account}</Text>
              </Flex>
            </Card>
            <Card size="1">
              <Flex direction="column" gap="1" p="2">
                <Text size="2" color="gray">Google Sheet</Text>
                {user.googleSheetId && (
                    <Link href={user.googleSheetId} target="_blank">
                      <Flex align="center" gap="1">
                        <Text size="4" weight="medium">Открыть</Text>
                        <OpenInNewWindowIcon />
                      </Flex>
                    </Link>
                )}
              </Flex>
            </Card>
          </Grid>
        </Flex>
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

  return (
      <Box pt="4" >
        {/* Ownership Info */}
        <Grid gap="4" mb="6">
          <Card size="1">
            <Flex direction="column" gap="1" p="3" align="center" justify="center">
              <Text size="2" color="gray">{selectedInstrument.base} Клиента</Text>
              <Text size={{ initial: "5", md: "6" }} weight="bold" color="blue">{selectedInstrument.clientOwnedBaseAmount}</Text>
            </Flex>
          </Card>
        </Grid>

        {/* Deposits and Withdrawals Section */}
        <DepositsWithdrawalsSection
          userId={userId}
          instrumentId={selectedInstrument.id}
          refreshTrigger={refreshTrigger}
          instrumentInfo={selectedInstrument}
        />

        {/* Order History */}
        <Box>
          <Heading size="3" mb="3">История ордеров</Heading>
          {orderHistory.length > 0 ? (
              <Box>
                <ScrollArea scrollbars="horizontal" style={{ maxWidth: '100%' }}>
                  <Table.Root variant="surface" layout="auto">
                    <Table.Header>
                      <Table.Row>
                        <Table.ColumnHeaderCell># Сделки</Table.ColumnHeaderCell>
                        <Table.ColumnHeaderCell>Тип</Table.ColumnHeaderCell>
                        <Table.ColumnHeaderCell>Статус</Table.ColumnHeaderCell>
                        <Table.ColumnHeaderCell>Цена</Table.ColumnHeaderCell>
                        <Table.ColumnHeaderCell>Дата</Table.ColumnHeaderCell>
                        <Table.ColumnHeaderCell>№ Аккаунта</Table.ColumnHeaderCell>
                        <Table.ColumnHeaderCell>Сумма в базовой валюте</Table.ColumnHeaderCell>
                        <Table.ColumnHeaderCell>Лоты</Table.ColumnHeaderCell>
                        <Table.ColumnHeaderCell>Сумма ордера в базовой валюте</Table.ColumnHeaderCell>
                        <Table.ColumnHeaderCell>Клиент, До, Сумма в базовой валюте</Table.ColumnHeaderCell>
                        <Table.ColumnHeaderCell>Клиент, После, Сумма в базовой валюте</Table.ColumnHeaderCell>
                        <Table.ColumnHeaderCell>ID</Table.ColumnHeaderCell>
                      </Table.Row>
                    </Table.Header>
                    <Table.Body>
                      {orderHistory.map((order) => (
                          <Table.Row key={order.id}>
                            <Table.Cell minWidth="100px">{order.deal}</Table.Cell>
                            <Table.Cell weight="medium">
                              <Text color={order.type === 'MT5_LONG' ? 'green' : 'red'} style={{ fontWeight: 'bold', textTransform: 'uppercase' }}>
                                {formatOrderType(order.type)}
                              </Text>
                            </Table.Cell>
                            <Table.Cell>{order.status}</Table.Cell>
                            <Table.Cell>{order.price}</Table.Cell>
                            <Table.Cell minWidth="180px">{(new Date(order.createdAt).toLocaleString())}</Table.Cell>
                            <Table.Cell minWidth="120px">{order.mt5AccNumber}</Table.Cell>
                            <Table.Cell minWidth="160px">{order.userBaseAmount} {selectedInstrument.base}</Table.Cell>
                            <Table.Cell>{order.executedVolume}</Table.Cell>
                            <Table.Cell minWidth="160px">{order.executedBaseAmount} {selectedInstrument.base}</Table.Cell>
                            <Table.Cell minWidth="160px">{order.clientBaseAmountFrom} {selectedInstrument.base}</Table.Cell>
                            <Table.Cell minWidth="160px">{order.clientBaseAmountTo} {selectedInstrument.base}</Table.Cell>
                            <Table.Cell minWidth="300px">{order.id}</Table.Cell>
                          </Table.Row>
                      ))}
                    </Table.Body>
                  </Table.Root>
                </ScrollArea>
              </Box>
          ) : (
              <Flex justify="center" py="4">
                <Text color="gray">Нет истории заказов для этого инструмента</Text>
              </Flex>
          )}
        </Box>
      </Box>
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
      <Box pt="4" >
        {/* Ownership Info */}
        <Grid columns={{ initial: "1", sm: "2", md: "3" }} gap="4" mb="6">
          <Card size="1">
            <Flex direction="column" gap="1" p="3" align="center" justify="center">
              <Text size="2" color="gray">{selectedInstrument.quote} Клиента</Text>
              <Text size={{ initial: "5", md: "6" }} weight="bold" color="purple">{selectedInstrument.clientOwnedQuoteAmount}</Text>
            </Flex>
          </Card>
          <Card size="1">
            <Flex direction="column" gap="1" p="3" align="center" justify="center">
              <Text size="2" color="gray">{selectedInstrument.base} Клиента</Text>
              <Text size={{ initial: "5", md: "6" }} weight="bold" color="blue">{selectedInstrument.clientOwnedBaseAmount}</Text>
            </Flex>
          </Card>
          <Card size="1">
            <Flex direction="column" gap="1" p="3" align="center" justify="center">
              <Text size="2" color="gray">{selectedInstrument.base} Димы</Text>
              <Text size={{ initial: "5", md: "6" }} weight="bold" color="green">{selectedInstrument.houseOwnedBaseAmount}</Text>
            </Flex>
          </Card>
        </Grid>

        {/* Deposits and Withdrawals Section */}
        <DepositsWithdrawalsSection
          userId={userId}
          instrumentId={selectedInstrument.id}
          refreshTrigger={refreshTrigger}
          instrumentInfo={selectedInstrument}
        />

        {/* Open Positions */}
        {/* No sense to show them for regular hedge */}
        <Box mb="6">
          <Heading size="3" mb="3">Текущие открытые позиции</Heading>
          {openPositions.length > 0 ? (
              <Box>
                <ScrollArea scrollbars="horizontal" style={{ maxWidth: '100%' }}>
                  <Table.Root variant="surface">
                    <Table.Header>
                      <Table.Row>
                        <Table.ColumnHeaderCell># Сделки</Table.ColumnHeaderCell>
                        <Table.ColumnHeaderCell>Дата</Table.ColumnHeaderCell>
                        <Table.ColumnHeaderCell>Цена</Table.ColumnHeaderCell>
                        <Table.ColumnHeaderCell>Остаток для расхеджирования</Table.ColumnHeaderCell>
                        <Table.ColumnHeaderCell>Сумма в котируемой валюте </Table.ColumnHeaderCell>
                        <Table.ColumnHeaderCell>Сумма в базовой валюте</Table.ColumnHeaderCell>
                        <Table.ColumnHeaderCell>Сумма ордера в базовой валюте</Table.ColumnHeaderCell>

                        <Table.ColumnHeaderCell>ID</Table.ColumnHeaderCell>
                      </Table.Row>
                    </Table.Header>
                    <Table.Body>
                      {openPositions.map((order) => (
                          <Table.Row key={order.id}>
                            <Table.Cell minWidth="100px">{order.deal}</Table.Cell>
                            <Table.Cell minWidth="180px">{(new Date(order.createdAt).toLocaleString())}</Table.Cell>
                            <Table.Cell>
                              <Text weight="medium">
                                {order.price}
                              </Text>
                              </Table.Cell>
                            <Table.Cell minWidth="160px">
                              <Text weight="medium" color='green'>
                                {order.quoteAmountLeftToUnhedge} / {order.userEnteredQuoteAmount}
                              </Text>
                            </Table.Cell>
                            <Table.Cell minWidth="160px">{order.userEnteredQuoteAmount} {selectedInstrument.quote}</Table.Cell>
                            <Table.Cell minWidth="160px">{order.userBaseAmount} {selectedInstrument.base}</Table.Cell>
                            <Table.Cell minWidth="160px">{order.executedBaseAmount} {selectedInstrument.base}</Table.Cell>
                            <Table.Cell minWidth="300px">{order.id}</Table.Cell>
                          </Table.Row>
                      ))}
                    </Table.Body>
                  </Table.Root>
                </ScrollArea>
              </Box>
          ) : (
              <Flex justify="center" py="4">
                <Text color="gray">Нет открытых позиций для этого инструмента</Text>
              </Flex>
          )}
        </Box>

        {/* Order History */}
        <Box>
          <Heading size="3" mb="3">История заказов</Heading>
          {orderHistory.length > 0 ? (
              <Box>
                <ScrollArea scrollbars="horizontal" style={{ maxWidth: '100%' }}>
                  <Table.Root variant="surface" layout="auto">
                    <Table.Header>
                      <Table.Row>
                        <Table.ColumnHeaderCell># Сделки</Table.ColumnHeaderCell>
                        <Table.ColumnHeaderCell>Тип</Table.ColumnHeaderCell>
                        <Table.ColumnHeaderCell>Статус</Table.ColumnHeaderCell>
                        <Table.ColumnHeaderCell>Цена</Table.ColumnHeaderCell>
                        <Table.ColumnHeaderCell>Дата</Table.ColumnHeaderCell>
                        <Table.ColumnHeaderCell>№ Аккаунта</Table.ColumnHeaderCell>
                        <Table.ColumnHeaderCell>Сумма в базовой валюте</Table.ColumnHeaderCell>
                        <Table.ColumnHeaderCell>Сумма в котируемой валюте </Table.ColumnHeaderCell>

                        <Table.ColumnHeaderCell>Лоты</Table.ColumnHeaderCell>
                        <Table.ColumnHeaderCell>Сумма ордера в базовой валюте</Table.ColumnHeaderCell>
                        <Table.ColumnHeaderCell>Дима, До, Сумма в базовой валюте</Table.ColumnHeaderCell>
                        <Table.ColumnHeaderCell>Дима, После, Сумма в базовой валюте</Table.ColumnHeaderCell>
                        <Table.ColumnHeaderCell>Клиент, До, Сумма в базовой валюте</Table.ColumnHeaderCell>
                        <Table.ColumnHeaderCell>Клиент, После, Сумма в базовой валюте</Table.ColumnHeaderCell>

                        <Table.ColumnHeaderCell>ID</Table.ColumnHeaderCell>
                      </Table.Row>
                    </Table.Header>
                    <Table.Body>
                      {orderHistory.map((order) => (
                        <>
                          <Table.Row
                            key={order.id}
                            onClick={() => {
                              if (order.type === 'MT5_SHORT') {
                                setExpandedRows(prev => ({
                                  ...prev,
                                  [order.id]: !prev[order.id]
                                }));
                              }
                            }}
                          >
                            <Table.Cell minWidth="100px">{order.deal}</Table.Cell>
                            <Table.Cell weight="medium">
                              <Flex align="center" gap="1">
                                {(order.type === 'MT5_SHORT') && (
                                  expandedRows[order.id] ? <ChevronDownIcon /> : <ChevronRightIcon />
                                )}
                                <Text color={order.type === 'MT5_LONG' ? 'green' : 'red'} style={{ fontWeight: 'bold', textTransform: 'uppercase' }}>
                                  {formatOrderType(order.type)}
                                </Text>
                              </Flex>
                            </Table.Cell>
                            <Table.Cell>{order.status}</Table.Cell>
                            <Table.Cell>{order.price}</Table.Cell>
                            <Table.Cell minWidth="180px">{(new Date(order.createdAt).toLocaleString())}</Table.Cell>
                            <Table.Cell minWidth="120px">{order.mt5AccNumber}</Table.Cell>
                            <Table.Cell minWidth="160px">{order.userBaseAmount} {selectedInstrument.base}</Table.Cell>
                            <Table.Cell minWidth="160px">{order.userEnteredQuoteAmount} {selectedInstrument.quote}</Table.Cell>

                            <Table.Cell>{order.executedVolume}</Table.Cell>
                            <Table.Cell minWidth="160px">{order.executedBaseAmount} {selectedInstrument.base}</Table.Cell>
                            {/*<Table.Cell minWidth="160px">{order.quoteAmountLeftToUnhedge}</Table.Cell>*/}
                            <Table.Cell minWidth="160px">{order.houseBaseAmountFrom} {selectedInstrument.base}</Table.Cell>
                            <Table.Cell minWidth="160px">{order.houseBaseAmountTo} {selectedInstrument.base}</Table.Cell>
                            <Table.Cell minWidth="160px">{order.clientBaseAmountFrom} {selectedInstrument.base}</Table.Cell>
                            <Table.Cell minWidth="160px">{order.clientBaseAmountTo} {selectedInstrument.base}</Table.Cell>

                            <Table.Cell minWidth="290px">{order.id}</Table.Cell>
                          </Table.Row>

                          {expandedRows[order.id] && order.type === 'MT5_SHORT' && (
                            <Table.Row>
                              <Table.Cell colSpan="16">
                                <Box pl="2" py="2">
                                  <Table.Root variant="surface" layout="auto" size="1">
                                      <Table.Header>
                                        <Table.Row>
                                          <Table.ColumnHeaderCell># Сделки</Table.ColumnHeaderCell>
                                          <Table.ColumnHeaderCell>Часть {selectedInstrument.quote} закрыто</Table.ColumnHeaderCell>
                                          <Table.ColumnHeaderCell>Остаток {selectedInstrument.quote} в сделке</Table.ColumnHeaderCell>
                                          <Table.ColumnHeaderCell>Еквивалент в {selectedInstrument.base}</Table.ColumnHeaderCell>
                                          <Table.ColumnHeaderCell></Table.ColumnHeaderCell>
                                        </Table.Row>
                                      </Table.Header>
                                      <Table.Body>
                                        {order.consumptions.map(consumedOrder => (
                                          <Table.Row>
                                            <Table.Cell width="200px">{consumedOrder.consumedOrder.deal}</Table.Cell>
                                            <Table.Cell width="200px">{consumedOrder.consumedQuoteAmount}</Table.Cell>
                                            <Table.Cell width="200px">{consumedOrder.leftQuoteAmount}</Table.Cell>
                                            <Table.Cell width="200px">{consumedOrder.baseEquivalent}</Table.Cell>
                                          </Table.Row>
                                        ))}
                                      </Table.Body>
                                    </Table.Root>
                                </Box>
                              </Table.Cell>
                            </Table.Row>
                          )}
                        </>
                      ))}
                    </Table.Body>
                  </Table.Root>
                </ScrollArea>
              </Box>
          ) : (
              <Flex justify="center" py="4">
                <Text color="gray">Нет истории заказов для этого инструмента</Text>
              </Flex>
          )}
        </Box>
      </Box>
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
  const [time, setTime] = useState(new Date());
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
    const day = now.getDate().toString().padStart(2, '0');
    const month = (now.getMonth()+1).toString().padStart(2, '0');
    const hours = now.getHours().toString().padStart(2, '0');
    const minutes = now.getMinutes().toString().padStart(2, '0');
    const year = now.getFullYear();

    if (transaction) {
      // Edit mode
      setCurrentTransaction(transaction);
      setAmount(transaction.amount);
      setDate(`${year}-${month}-${day}`);
      setTime(`${hours}:${minutes}`);
      setActionType(transaction.type)
    } else {
      // Add mode
      setCurrentTransaction(null);
      setAmount('');
      setDate(`${year}-${month}-${day}`);
      setTime(`${hours}:${minutes}`);
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
          date: new Date(date + ' ' + time)
        });
      } else {
        // Create new transaction
        await api.post(`/users/${userId}/instruments/${instrumentId}/balance-actions`, {
          amount: amount,
          type: actionType,
          date: new Date(date + ' ' + time)
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

  return (
    <Card size="2" mb="6">
      <Flex direction="column" gap="3">
        <Flex justify="between" align="center">
          <Heading size="4">Депозиты и выводы {instrumentInfo && `(${instrumentInfo.label})`}</Heading>
          <Button onClick={() => handleOpenDialog()} color="green">
            <PlusIcon />
            Добавить запись
          </Button>
        </Flex>
        <Separator size="4" />

        {isLoading ? (
          <Flex justify="center" py="4">
            <Text color="gray">Загрузка...</Text>
          </Flex>
        ) : transactions.length > 0 ? (
          <ScrollArea scrollbars="horizontal" style={{ maxWidth: '100%' }}>
            <Table.Root variant="surface">
              <Table.Header>
                <Table.Row>
                  <Table.ColumnHeaderCell>Тип</Table.ColumnHeaderCell>
                  <Table.ColumnHeaderCell>Сумма</Table.ColumnHeaderCell>
                  <Table.ColumnHeaderCell>Дата</Table.ColumnHeaderCell>
                  <Table.ColumnHeaderCell>Действия</Table.ColumnHeaderCell>
                </Table.Row>
              </Table.Header>
              <Table.Body>
                {transactions.map((transaction) => (
                  <Table.Row key={transaction.id}>
                    <Table.Cell width="20px">
                      <Text color={transaction.type === 'DEPOSIT' ? 'green' : 'red'} weight="medium">
                        {transaction.type === 'DEPOSIT' ? 'Депозит' : 'Вывод'}
                      </Text>
                    </Table.Cell>
                    <Table.Cell minWidth="150px">
                      <Text weight="medium">
                        {transaction.amount} {instrumentInfo.quote}
                      </Text>
                    </Table.Cell>
                    <Table.Cell minWidth="150px" width="200px">{formatDate(transaction.date)}</Table.Cell>
                    <Table.Cell width="60px">
                      <Flex gap="4">
                        <IconButton
                          variant="ghost"
                          color="gray"
                          onClick={() => handleOpenDialog(transaction)}
                        >
                          <Pencil1Icon />
                        </IconButton>
                          <IconButton
                            variant="ghost"
                            color="red"
                            onClick={() => handleOpenDeleteConfirm(transaction)}
                          >
                            <TrashIcon />
                          </IconButton>
                      </Flex>
                    </Table.Cell>
                  </Table.Row>
                ))}
              </Table.Body>
            </Table.Root>
          </ScrollArea>
        ) : (
          <Flex justify="center" py="4">
            <Text color="gray">Нет записей о депозитах и выводах для этого инструмента</Text>
          </Flex>
        )}
      </Flex>

      {/* Dialog for adding/editing transactions */}
      <Dialog.Root open={isDialogOpen} onOpenChange={setIsDialogOpen}>
        <Dialog.Content style={{ maxWidth: 450 } }>
          <Dialog.Title>
            {currentTransaction ? 'Редактировать запись' : 'Добавить новую запись'}
          </Dialog.Title>

          <Flex direction="column" gap="3" mt="4">
            <SegmentedControl.Root value={actionType} onValueChange={(e) => setActionType(e)}>
              <SegmentedControl.Item value="DEPOSIT">Депозит</SegmentedControl.Item>
              <SegmentedControl.Item value="WITHDRAWAL">Вывод</SegmentedControl.Item>
            </SegmentedControl.Root>

            <Flex gap="1" direction="column">
              <TextField.Root
                  placeholder="Сумма"
                  type="number"
                  min={0}
                  value={amount}
                  onChange={(e) => setAmount(e.target.value)}
                  color={errors.amount ? "red" : undefined}>
              </TextField.Root>

              {errors.amount && (
                  <Text size="1" color="red" as="div">
                    {errors.amount}
                  </Text>
              )}
            </Flex>

            <Flex gap="1" direction="column">
              <Flex gap="1">
                <TextField.Root
                    className="w-full"
                    placeholder="Дата"
                    type="date"
                    value={date}
                    onChange={(e) => setDate(e.target.value)}
                    color={errors.date ? "red" : undefined}>
                </TextField.Root>

                <TextField.Root 
                    placeholder="Время"
                    type="time"
                    value={time}
                    onChange={(e) => setTime(e.target.value)}>
                </TextField.Root>
              </Flex>

              {errors.date && (
                <Text size="1" color="red" as="div">
                  {errors.date}
                </Text>
              )}
            </Flex>
          </Flex>

          <Flex gap="3" mt="4" justify="end">
            <Dialog.Close>
              <Button variant="soft" color="gray">
                Отмена
              </Button>
            </Dialog.Close>
            <Button onClick={handleSubmit}>
              {currentTransaction ? 'Сохранить' : 'Добавить'}
            </Button>
          </Flex>
        </Dialog.Content>
      </Dialog.Root>

      {/* Dialog for confirming deletion */}
      <Dialog.Root open={isDeleteConfirmOpen} onOpenChange={setIsDeleteConfirmOpen}>
        <Dialog.Content style={{ maxWidth: 450 }}>
          <Dialog.Title>Подтверждение удаления</Dialog.Title>

          <Text size="3" mt="4">
            Вы уверены, что хотите удалить эту запись?
          </Text>

          <Flex gap="3" mt="4" justify="end">
            <Dialog.Close>
              <Button variant="soft" color="gray" onClick={handleCloseDeleteConfirm}>
                Отмена
              </Button>
            </Dialog.Close>
            <Button color="red" onClick={handleDelete}>
              Удалить
            </Button>
          </Flex>
        </Dialog.Content>
      </Dialog.Root>
    </Card>
  );
}

function UserPage() {
  const { userId } = useParams();
  const [activeTab, setActiveTab] = useState('');
  const [user, setUser] = useState(null);
  const [instruments, setInstruments] = useState([]);
  const [loading, setLoading] = useState(true);
  const [selectedInstrument, setSelectedInstrument] = useState(null);
  const [refreshCounter, setRefreshCounter] = useState(0);

  // API endpoints for data fetching

  const fetchData = async () => {
    setLoading(true);
    try {
      // Fetch user data
      const userResponse = await api.get(`/users/${userId}`);
      setUser(userResponse.data);

      // Fetch instruments
      // const instrumentsResponse = await api.get('/instruments');
      // setInstruments(instrumentsResponse.data);
      const instrumentsResp = userResponse.data.instruments || [];
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
          <Flex justify="center" py="4">
            <Text color="gray">Выберите инструмент для просмотра информации</Text>
          </Flex>
      );
      break;
  }

  return (
    <Box style={{ minHeight: '100vh', backgroundColor: 'var(--gray-2)' }}>

      <UserPageHeader onRefresh={fetchData} isRefreshing={loading} user={user} />

      <Container size="4" py="6" style={{ paddingTop: '80px' }}>
        {/* User Info Section */}
        <UserInfoSection user={user} />

        {/* Instruments Section */}
        <Card size="2" mb="6">
          <Flex direction="column" gap="3">
            <Heading size="4">Доступные инструменты</Heading>
            <Separator size="4" />

            {/* Tabs */}
            <Tabs.Root defaultValue={activeTab} onValueChange={handleTabChange}>
              <Tabs.List>
                {instruments.map((instrument) => (
                  <Tabs.Trigger key={instrument.label} value={instrument.label}>
                    {instrument.label}
                  </Tabs.Trigger>
                ))}
              </Tabs.List>

              {/* Tab Content */}
              {detailsComponent}
            </Tabs.Root>
          </Flex>
        </Card>
      </Container>
    </Box>
  );
}

export default UserPage;
