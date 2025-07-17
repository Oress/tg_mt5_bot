import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import axios from 'axios';
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
  Separator, Link,
} from '@radix-ui/themes';
import {ArrowLeftIcon, ChevronDownIcon, ChevronRightIcon, OpenInNewWindowIcon} from '@radix-ui/react-icons';

function RecentActivitySection({userId}) {
  const [activities, setActivities] = useState([]);

  useEffect(() => {
    const fetchData = async () => {
      try {
        const activitiesResponse = await axios.get(`/api/users/${userId}/activities`);
        setActivities(activitiesResponse.data);
      } catch (error) {
        console.error('Error fetching data:', error);
        // You might want to set an error state here to display to the user
      }
    };

    fetchData();
  }, [userId])

  return (
      <Card size="2" mb="6">
        <Flex direction="column" gap="3">
          <Heading size="4">Недавняя активность</Heading>
          <Separator size="4" />

          {activities.length > 0 ? (
              <Box>
                <ScrollArea scrollbars="horizontal" style={{ maxWidth: '100%' }}>
                  <Table.Root variant="surface">
                    <Table.Header>
                      <Table.Row>
                        <Table.ColumnHeaderCell>Дата</Table.ColumnHeaderCell>
                        <Table.ColumnHeaderCell>Состояние</Table.ColumnHeaderCell>
                        <Table.ColumnHeaderCell>Тип</Table.ColumnHeaderCell>
                        <Table.ColumnHeaderCell>Данные</Table.ColumnHeaderCell>
                      </Table.Row>
                    </Table.Header>
                    <Table.Body>
                      {activities.map((activity) => (
                          <Table.Row key={activity.id}>
                            <Table.Cell>{new Date(activity.createdAt).toLocaleString()}</Table.Cell>
                            <Table.Cell>{activity.currentUserState}</Table.Cell>
                            <Table.Cell>{activity.type}</Table.Cell>
                            <Table.Cell>{activity.data}</Table.Cell>
                          </Table.Row>
                      ))}
                    </Table.Body>
                  </Table.Root>
                </ScrollArea>
              </Box>
          ) : (
              <Flex justify="center" py="4">
                <Text color="gray">Недавняя активность не найдена</Text>
              </Flex>
          )}
        </Flex>
      </Card>
  );
}

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

function UserPageHeader() {
  const navigate = useNavigate();

  const handleBack = () => {
    navigate('/main');
  };

  return (
      <Box style={{ backgroundColor: 'var(--color-panel-solid)', boxShadow: 'var(--shadow-2)' }}>
        <Container size="3">
          <Flex
              justify={{ initial: "center", sm: "between" }}
              align="center"
              py="5"
              direction={{ initial: "column", sm: "row" }}
              gap={{ initial: "4", sm: "0" }}
          >
            <Heading size={{ initial: "5", sm: "6" }}>Детали пользователя</Heading>
            <Button onClick={handleBack} size="3" color="cyan">
              <ArrowLeftIcon />
              <Text size={{ initial: "2", sm: "3" }}>Назад к поиску</Text>
            </Button>
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

function RegularHedgeDetails({selectedInstrument, userId}) {
  const [orderHistory, setOrderHistory] = useState([]);

  useEffect(() => {
    const fetchData = async () => {
      try {
        // Set default active tab to first instrument if available
        if (selectedInstrument) {
          // Fetch open positions for the selected instrument
          // const positionsResponse = await axios.get(`/api/users/${userId}/orders/${firstInstrument.id}`);
          // setOpenPositions(positionsResponse.data);

          // Fetch order history for the selected instrument
          const historyResponse = await axios.get(`/api/users/${userId}/orders/${selectedInstrument.id}`);
          setOrderHistory(historyResponse.data);
        }
      } catch (error) {
        console.error('Error fetching data:', error);
        // You might want to set an error state here to display to the user
      }
    };

    fetchData();
  }, [selectedInstrument, userId]);

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

function InverseHedgeDetails({selectedInstrument, userId}) {
  const [openPositions, setOpenPositions] = useState([]);
  const [orderHistory, setOrderHistory] = useState([]);
  const [expandedRows, setExpandedRows] = useState({});

  useEffect(() => {
    const fetchData = async () => {
      try {
        // Set default active tab to first instrument if available
        if (selectedInstrument) {
          // Fetch open positions for the selected instrument
          const positionsResponse = await axios.get(`/api/users/${userId}/orders/${selectedInstrument.id}`, {
            params: {
              type: "open"
            }
          });
          setOpenPositions(positionsResponse.data);

          // Fetch order history for the selected instrument
          const historyResponse = await axios.get(`/api/users/${userId}/orders/${selectedInstrument.id}`);
          setOrderHistory(historyResponse.data);
        }
      } catch (error) {
        console.error('Error fetching data:', error);
        // You might want to set an error state here to display to the user
      }
    };

    fetchData();
  }, [selectedInstrument, userId]);

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

function UserPage() {
  const { userId } = useParams();
  const [activeTab, setActiveTab] = useState('');
  const [user, setUser] = useState(null);
  const [instruments, setInstruments] = useState([]);
  const [loading, setLoading] = useState(true);
  const [selectedInstrument, setSelectedInstrument] = useState(null);

  // API endpoints for data fetching

  useEffect(() => {
    const fetchData = async () => {
      setLoading(true);
      try {
        // Fetch user data
        const userResponse = await axios.get(`/api/users/${userId}`);
        setUser(userResponse.data);

        // Fetch instruments
        // const instrumentsResponse = await axios.get('/api/instruments');
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
      } catch (error) {
        console.error('Error fetching data:', error);
        // You might want to set an error state here to display to the user
      } finally {
        setLoading(false);
      }
    };

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
      detailsComponent = <RegularHedgeDetails selectedInstrument={selectedInstrument} userId={userId} />;
      break;
    case 'LONG':
      detailsComponent = <InverseHedgeDetails selectedInstrument={selectedInstrument} userId={userId}/>;
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

      <UserPageHeader />

      <Container size="4" py="6">
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
