import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
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
  Inset
} from '@radix-ui/themes';
import { ArrowLeftIcon } from '@radix-ui/react-icons';

function UserPage() {
  const { userId } = useParams();
  const navigate = useNavigate();
  const [activeTab, setActiveTab] = useState('');
  const [user, setUser] = useState(null);
  const [activities, setActivities] = useState([]);
  const [instruments, setInstruments] = useState([]);
  const [loading, setLoading] = useState(true);
  const [selectedInstrument, setSelectedInstrument] = useState(null);
  const [openPositions, setOpenPositions] = useState([]);
  const [orderHistory, setOrderHistory] = useState([]);

  // Mock data - in a real app, this would come from API calls
  const mockUsers = [
    { id: 1, telegramId: '12345678', username: 'user1', firstName: 'John' },
    { id: 2, telegramId: '87654321', username: 'user2', firstName: 'Jane' },
    { id: 3, telegramId: '11223344', username: 'admin', firstName: 'Admin' },
    { id: 4, telegramId: '44332211', username: 'trader1', firstName: 'Mike' },
    { id: 5, telegramId: '55667788', username: 'trader2', firstName: 'Sarah' },
  ];

  const mockActivities = [
    { id: 1, userId: 1, action: 'Menu Click', details: 'Main Menu', timestamp: '2025-07-14T10:15:30Z' },
    { id: 2, userId: 1, action: 'View Balance', details: 'Account Balance Check', timestamp: '2025-07-14T10:16:45Z' },
    { id: 3, userId: 1, action: 'Place Order', details: 'Buy USD/CAD', timestamp: '2025-07-14T10:20:15Z' },
    { id: 4, userId: 1, action: 'Text Input', details: 'Search for EUR/USD', timestamp: '2025-07-14T10:25:00Z' },
    { id: 5, userId: 1, action: 'Place Order', details: 'Sell EUR/USD', timestamp: '2025-07-14T10:30:22Z' },
    { id: 6, userId: 2, action: 'Menu Click', details: 'Settings Menu', timestamp: '2025-07-14T09:10:30Z' },
    { id: 7, userId: 3, action: 'View Balance', details: 'Account Balance Check', timestamp: '2025-07-14T11:05:15Z' },
  ];

  const mockInstruments = [
    { id: 1, name: 'USD/CAD', clientBaseOwned: 10000, houseBaseOwned: 5000, totalBaseOwned: 15000 },
    { id: 2, name: 'EUR/USD', clientBaseOwned: 8000, houseBaseOwned: 7000, totalBaseOwned: 15000 },
    { id: 3, name: 'GBP/JPY', clientBaseOwned: 12000, houseBaseOwned: 3000, totalBaseOwned: 15000 },
    { id: 4, name: 'AUD/NZD', clientBaseOwned: 6000, houseBaseOwned: 9000, totalBaseOwned: 15000 },
  ];

  const mockOpenPositions = {
    'USD/CAD': [
      { id: 1, type: 'Buy', amount: 1000, openPrice: 1.3450, currentPrice: 1.3500, profit: 50, openDate: '2025-07-13T14:30:00Z' },
      { id: 2, type: 'Sell', amount: 500, openPrice: 1.3550, currentPrice: 1.3500, profit: 25, openDate: '2025-07-14T09:15:00Z' },
    ],
    'EUR/USD': [
      { id: 3, type: 'Buy', amount: 2000, openPrice: 1.0950, currentPrice: 1.0975, profit: 50, openDate: '2025-07-12T10:45:00Z' },
      { id: 4, type: 'Sell', amount: 1500, openPrice: 1.1000, currentPrice: 1.0975, profit: 37.5, openDate: '2025-07-13T16:20:00Z' },
    ],
  };

  const mockOrderHistory = {
    'USD/CAD': [
      { id: 101, type: 'Buy', amount: 800, openPrice: 1.3400, closePrice: 1.3450, profit: 40, openDate: '2025-07-10T11:30:00Z', closeDate: '2025-07-11T15:45:00Z' },
      { id: 102, type: 'Sell', amount: 1200, openPrice: 1.3600, closePrice: 1.3550, profit: 60, openDate: '2025-07-08T09:20:00Z', closeDate: '2025-07-09T14:10:00Z' },
      { id: 103, type: 'Buy', amount: 500, openPrice: 1.3350, closePrice: 1.3400, profit: 25, openDate: '2025-07-05T10:15:00Z', closeDate: '2025-07-06T16:30:00Z' },
    ],
    'EUR/USD': [
      { id: 104, type: 'Buy', amount: 1000, openPrice: 1.0900, closePrice: 1.0950, profit: 50, openDate: '2025-07-09T13:45:00Z', closeDate: '2025-07-10T11:20:00Z' },
      { id: 105, type: 'Sell', amount: 1500, openPrice: 1.1050, closePrice: 1.1000, profit: 75, openDate: '2025-07-07T14:30:00Z', closeDate: '2025-07-08T10:15:00Z' },
      { id: 106, type: 'Buy', amount: 700, openPrice: 1.0850, closePrice: 1.0900, profit: 35, openDate: '2025-07-04T09:10:00Z', closeDate: '2025-07-05T15:40:00Z' },
    ],
  };

  useEffect(() => {
    // Simulate API calls to fetch data
    setLoading(true);

    // Find user
    const foundUser = mockUsers.find(u => u.id === parseInt(userId));

    if (foundUser) {
      setUser(foundUser);

      // Get user activities
      const userActivities = mockActivities.filter(a => a.userId === parseInt(userId));
      setActivities(userActivities);

      // Set instruments
      setInstruments(mockInstruments);

      // Set default active tab to first instrument if available
      if (mockInstruments.length > 0) {
        const firstInstrument = mockInstruments[0];
        setActiveTab(firstInstrument.name);
        setSelectedInstrument(firstInstrument);

        // Set open positions and order history for the selected instrument
        setOpenPositions(mockOpenPositions[firstInstrument.name] || []);
        setOrderHistory(mockOrderHistory[firstInstrument.name] || []);
      }
    }

    setLoading(false);
  }, [userId]);

  const handleTabChange = (instrumentName) => {
    setActiveTab(instrumentName);
    const instrument = instruments.find(i => i.name === instrumentName);
    setSelectedInstrument(instrument);

    // Update open positions and order history based on selected instrument
    setOpenPositions(mockOpenPositions[instrumentName] || []);
    setOrderHistory(mockOrderHistory[instrumentName] || []);
  };

  const handleBack = () => {
    navigate('/main');
  };

  if (loading) {
    return (
      <Box style={{ minHeight: '100vh', backgroundColor: 'var(--gray-2)' }}>
        <Flex align="center" justify="center" style={{ height: '100vh' }}>
          <Text size="5" color="gray">Loading...</Text>
        </Flex>
      </Box>
    );
  }

  if (!user) {
    return (
      <Box style={{ minHeight: '100vh', backgroundColor: 'var(--gray-2)' }}>
        <Flex align="center" justify="center" style={{ height: '100vh' }}>
          <Flex direction="column" align="center" gap="4">
            <Text size="5" color="red" weight="bold">User not found</Text>
            <Button onClick={handleBack} size="3" color="cyan">
              <ArrowLeftIcon />
              Back to Search
            </Button>
          </Flex>
        </Flex>
      </Box>
    );
  }

  return (
    <Box style={{ minHeight: '100vh', backgroundColor: 'var(--gray-2)' }}>
      <Box style={{ backgroundColor: 'var(--color-panel-solid)', boxShadow: 'var(--shadow-2)' }}>
        <Container size="3">
          <Flex 
            justify={{ initial: "center", sm: "between" }} 
            align="center" 
            py="5"
            direction={{ initial: "column", sm: "row" }}
            gap={{ initial: "4", sm: "0" }}
          >
            <Heading size={{ initial: "5", sm: "6" }}>User Details</Heading>
            <Button onClick={handleBack} size="3" color="cyan">
              <ArrowLeftIcon />
              <Text size={{ initial: "2", sm: "3" }}>Back to Search</Text>
            </Button>
          </Flex>
        </Container>
      </Box>

      <Container size="3" py="6">
        {/* User Info Section */}
        <Card size="2" mb="6">
          <Flex direction="column" gap="3">
            <Heading size="4">User Information</Heading>
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
                  <Text size="2" color="gray">First Name</Text>
                  <Text size="4" weight="medium">{user.firstName}</Text>
                </Flex>
              </Card>
              <Card size="1">
                <Flex direction="column" gap="1" p="2">
                  <Text size="2" color="gray">Username</Text>
                  <Text size="4" weight="medium">{user.username}</Text>
                </Flex>
              </Card>
            </Grid>
          </Flex>
        </Card>

        {/* Recent Activity Section */}
        <Card size="2" mb="6">
          <Flex direction="column" gap="3">
            <Heading size="4">Recent Activity</Heading>
            <Separator size="4" />

            {activities.length > 0 ? (
              <Box>
                {/* Desktop view - Table */}
                <Box display={{ initial: 'none', md: 'block' }}>
                  <ScrollArea scrollbars="horizontal" style={{ maxWidth: '100%' }}>
                    <Table.Root variant="surface">
                      <Table.Header>
                        <Table.Row>
                          <Table.ColumnHeaderCell>Action</Table.ColumnHeaderCell>
                          <Table.ColumnHeaderCell>Details</Table.ColumnHeaderCell>
                          <Table.ColumnHeaderCell>Timestamp</Table.ColumnHeaderCell>
                        </Table.Row>
                      </Table.Header>
                      <Table.Body>
                        {activities.map((activity) => (
                          <Table.Row key={activity.id}>
                            <Table.Cell>{activity.action}</Table.Cell>
                            <Table.Cell>{activity.details}</Table.Cell>
                            <Table.Cell>{new Date(activity.timestamp).toLocaleString()}</Table.Cell>
                          </Table.Row>
                        ))}
                      </Table.Body>
                    </Table.Root>
                  </ScrollArea>
                </Box>

                {/* Mobile view - Cards */}
                <Flex direction="column" gap="3" display={{ initial: 'flex', md: 'none' }}>
                  {activities.map((activity) => (
                    <Card key={activity.id} size="1">
                      <Flex direction="column" gap="2" p="3">
                        <Flex justify="between" align="center">
                          <Text weight="bold">{activity.action}</Text>
                          <Text size="1" color="gray">{new Date(activity.timestamp).toLocaleString()}</Text>
                        </Flex>
                        <Text>{activity.details}</Text>
                      </Flex>
                    </Card>
                  ))}
                </Flex>
              </Box>
            ) : (
              <Flex justify="center" py="4">
                <Text color="gray">No recent activity found</Text>
              </Flex>
            )}
          </Flex>
        </Card>

        {/* Instruments Section */}
        <Card size="2">
          <Flex direction="column" gap="3">
            <Heading size="4">Available Instruments</Heading>
            <Separator size="4" />

            {/* Tabs */}
            <Tabs.Root defaultValue={activeTab} onValueChange={handleTabChange}>
              <Tabs.List>
                {instruments.map((instrument) => (
                  <Tabs.Trigger key={instrument.id} value={instrument.name}>
                    {instrument.name}
                  </Tabs.Trigger>
                ))}
              </Tabs.List>

              {/* Tab Content */}
              {selectedInstrument && (
                <Box pt="4">
                  {/* Ownership Info */}
                  <Grid columns={{ initial: "1", sm: "2", md: "3" }} gap="4" mb="6">
                    <Card size="1">
                      <Flex direction="column" gap="1" p="3" align="center" justify="center">
                        <Text size="2" color="gray">Client Base Owned</Text>
                        <Text size={{ initial: "5", md: "6" }} weight="bold" color="blue">{selectedInstrument.clientBaseOwned.toLocaleString()}</Text>
                      </Flex>
                    </Card>
                    <Card size="1">
                      <Flex direction="column" gap="1" p="3" align="center" justify="center">
                        <Text size="2" color="gray">House Base Owned</Text>
                        <Text size={{ initial: "5", md: "6" }} weight="bold" color="green">{selectedInstrument.houseBaseOwned.toLocaleString()}</Text>
                      </Flex>
                    </Card>
                    <Card size="1">
                      <Flex direction="column" gap="1" p="3" align="center" justify="center">
                        <Text size="2" color="gray">Total Base Owned</Text>
                        <Text size={{ initial: "5", md: "6" }} weight="bold" color="purple">{selectedInstrument.totalBaseOwned.toLocaleString()}</Text>
                      </Flex>
                    </Card>
                  </Grid>

                  {/* Open Positions */}
                  <Box mb="6">
                    <Heading size="3" mb="3">Current Open Positions</Heading>
                    {openPositions.length > 0 ? (
                      <Box>
                        {/* Desktop view - Table */}
                        <Box display={{ initial: 'none', md: 'block' }}>
                          <ScrollArea scrollbars="horizontal" style={{ maxWidth: '100%' }}>
                            <Table.Root variant="surface">
                              <Table.Header>
                                <Table.Row>
                                  <Table.ColumnHeaderCell>Type</Table.ColumnHeaderCell>
                                  <Table.ColumnHeaderCell>Amount</Table.ColumnHeaderCell>
                                  <Table.ColumnHeaderCell>Open Price</Table.ColumnHeaderCell>
                                  <Table.ColumnHeaderCell>Current Price</Table.ColumnHeaderCell>
                                  <Table.ColumnHeaderCell>Profit</Table.ColumnHeaderCell>
                                  <Table.ColumnHeaderCell>Open Date</Table.ColumnHeaderCell>
                                </Table.Row>
                              </Table.Header>
                              <Table.Body>
                                {openPositions.map((position) => (
                                  <Table.Row key={position.id}>
                                    <Table.Cell>
                                      <Text color={position.type === 'Buy' ? 'green' : 'red'} weight="medium">
                                        {position.type}
                                      </Text>
                                    </Table.Cell>
                                    <Table.Cell>{position.amount.toLocaleString()}</Table.Cell>
                                    <Table.Cell>{position.openPrice}</Table.Cell>
                                    <Table.Cell>{position.currentPrice}</Table.Cell>
                                    <Table.Cell>
                                      <Text color={position.profit >= 0 ? 'green' : 'red'} weight="medium">
                                        {position.profit >= 0 ? '+' : ''}{position.profit}
                                      </Text>
                                    </Table.Cell>
                                    <Table.Cell>{new Date(position.openDate).toLocaleString()}</Table.Cell>
                                  </Table.Row>
                                ))}
                              </Table.Body>
                            </Table.Root>
                          </ScrollArea>
                        </Box>

                        {/* Mobile view - Cards */}
                        <Flex direction="column" gap="3" display={{ initial: 'flex', md: 'none' }}>
                          {openPositions.map((position) => (
                            <Card key={position.id} size="1">
                              <Flex direction="column" gap="2" p="3">
                                <Flex justify="between" align="center">
                                  <Text color={position.type === 'Buy' ? 'green' : 'red'} weight="bold">
                                    {position.type}
                                  </Text>
                                  <Text color={position.profit >= 0 ? 'green' : 'red'} weight="bold">
                                    {position.profit >= 0 ? '+' : ''}{position.profit}
                                  </Text>
                                </Flex>
                                <Grid columns="2" gap="2">
                                  <Box>
                                    <Text size="1" color="gray">Amount</Text>
                                    <Text>{position.amount.toLocaleString()}</Text>
                                  </Box>
                                  <Box>
                                    <Text size="1" color="gray">Open Date</Text>
                                    <Text>{new Date(position.openDate).toLocaleString()}</Text>
                                  </Box>
                                  <Box>
                                    <Text size="1" color="gray">Open Price</Text>
                                    <Text>{position.openPrice}</Text>
                                  </Box>
                                  <Box>
                                    <Text size="1" color="gray">Current Price</Text>
                                    <Text>{position.currentPrice}</Text>
                                  </Box>
                                </Grid>
                              </Flex>
                            </Card>
                          ))}
                        </Flex>
                      </Box>
                    ) : (
                      <Flex justify="center" py="4">
                        <Text color="gray">No open positions for this instrument</Text>
                      </Flex>
                    )}
                  </Box>

                  {/* Order History */}
                  <Box>
                    <Heading size="3" mb="3">Order History</Heading>
                    {orderHistory.length > 0 ? (
                      <Box>
                        {/* Desktop view - Table */}
                        <Box display={{ initial: 'none', md: 'block' }}>
                          <ScrollArea scrollbars="horizontal" style={{ maxWidth: '100%' }}>
                            <Table.Root variant="surface">
                              <Table.Header>
                                <Table.Row>
                                  <Table.ColumnHeaderCell>Type</Table.ColumnHeaderCell>
                                  <Table.ColumnHeaderCell>Amount</Table.ColumnHeaderCell>
                                  <Table.ColumnHeaderCell>Open Price</Table.ColumnHeaderCell>
                                  <Table.ColumnHeaderCell>Close Price</Table.ColumnHeaderCell>
                                  <Table.ColumnHeaderCell>Profit</Table.ColumnHeaderCell>
                                  <Table.ColumnHeaderCell>Open Date</Table.ColumnHeaderCell>
                                  <Table.ColumnHeaderCell>Close Date</Table.ColumnHeaderCell>
                                </Table.Row>
                              </Table.Header>
                              <Table.Body>
                                {orderHistory.map((order) => (
                                  <Table.Row key={order.id}>
                                    <Table.Cell>
                                      <Text color={order.type === 'Buy' ? 'green' : 'red'} weight="medium">
                                        {order.type}
                                      </Text>
                                    </Table.Cell>
                                    <Table.Cell>{order.amount.toLocaleString()}</Table.Cell>
                                    <Table.Cell>{order.openPrice}</Table.Cell>
                                    <Table.Cell>{order.closePrice}</Table.Cell>
                                    <Table.Cell>
                                      <Text color={order.profit >= 0 ? 'green' : 'red'} weight="medium">
                                        {order.profit >= 0 ? '+' : ''}{order.profit}
                                      </Text>
                                    </Table.Cell>
                                    <Table.Cell>{new Date(order.openDate).toLocaleString()}</Table.Cell>
                                    <Table.Cell>{new Date(order.closeDate).toLocaleString()}</Table.Cell>
                                  </Table.Row>
                                ))}
                              </Table.Body>
                            </Table.Root>
                          </ScrollArea>
                        </Box>

                        {/* Mobile view - Cards */}
                        <Flex direction="column" gap="3" display={{ initial: 'flex', md: 'none' }}>
                          {orderHistory.map((order) => (
                            <Card key={order.id} size="1">
                              <Flex direction="column" gap="2" p="3">
                                <Flex justify="between" align="center">
                                  <Text color={order.type === 'Buy' ? 'green' : 'red'} weight="bold">
                                    {order.type}
                                  </Text>
                                  <Text color={order.profit >= 0 ? 'green' : 'red'} weight="bold">
                                    {order.profit >= 0 ? '+' : ''}{order.profit}
                                  </Text>
                                </Flex>
                                <Grid columns="2" gap="2">
                                  <Box>
                                    <Text size="1" color="gray">Amount</Text>
                                    <Text>{order.amount.toLocaleString()}</Text>
                                  </Box>
                                  <Box>
                                    <Text size="1" color="gray">Open Price</Text>
                                    <Text>{order.openPrice}</Text>
                                  </Box>
                                  <Box>
                                    <Text size="1" color="gray">Close Price</Text>
                                    <Text>{order.closePrice}</Text>
                                  </Box>
                                </Grid>
                                <Separator size="4" my="1" />
                                <Grid columns="2" gap="2">
                                  <Box>
                                    <Text size="1" color="gray">Open Date</Text>
                                    <Text>{new Date(order.openDate).toLocaleString()}</Text>
                                  </Box>
                                  <Box>
                                    <Text size="1" color="gray">Close Date</Text>
                                    <Text>{new Date(order.closeDate).toLocaleString()}</Text>
                                  </Box>
                                </Grid>
                              </Flex>
                            </Card>
                          ))}
                        </Flex>
                      </Box>
                    ) : (
                      <Flex justify="center" py="4">
                        <Text color="gray">No order history for this instrument</Text>
                      </Flex>
                    )}
                  </Box>
                </Box>
              )}
            </Tabs.Root>
          </Flex>
        </Card>
      </Container>
    </Box>
  );
}

export default UserPage;
