import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';

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
    { id: 1, telegramId: '12345678', username: 'user1' },
    { id: 2, telegramId: '87654321', username: 'user2' },
    { id: 3, telegramId: '11223344', username: 'admin' },
    { id: 4, telegramId: '44332211', username: 'trader1' },
    { id: 5, telegramId: '55667788', username: 'trader2' },
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
      <div className="flex min-h-screen items-center justify-center bg-gray-100">
        <p className="text-xl text-gray-600">Loading...</p>
      </div>
    );
  }

  if (!user) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-gray-100">
        <div className="text-center">
          <p className="mb-4 text-xl text-red-600">User not found</p>
          <button
            onClick={handleBack}
            className="rounded-md bg-blue-600 px-4 py-2 text-white hover:bg-blue-700"
          >
            Back to Search
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-100">
      <header className="bg-white shadow">
        <div className="mx-auto flex max-w-7xl items-center justify-between px-4 py-6 sm:px-6 lg:px-8">
          <h1 className="text-3xl font-bold text-gray-900">User Details</h1>
          <button
            onClick={handleBack}
            className="rounded-md bg-blue-600 px-4 py-2 text-white hover:bg-blue-700"
          >
            Back to Search
          </button>
        </div>
      </header>
      
      <main className="mx-auto max-w-7xl px-4 py-6 sm:px-6 lg:px-8">
        {/* User Info Section */}
        <div className="mb-8 rounded-lg bg-white p-6 shadow">
          <h2 className="mb-4 text-xl font-semibold text-gray-800">User Information</h2>
          <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
            <div>
              <p className="text-sm text-gray-500">Telegram ID</p>
              <p className="text-lg font-medium">{user.telegramId}</p>
            </div>
            <div>
              <p className="text-sm text-gray-500">Username</p>
              <p className="text-lg font-medium">{user.username}</p>
            </div>
          </div>
        </div>
        
        {/* Recent Activity Section */}
        <div className="mb-8 rounded-lg bg-white p-6 shadow">
          <h2 className="mb-4 text-xl font-semibold text-gray-800">Recent Activity</h2>
          {activities.length > 0 ? (
            <div className="overflow-x-auto">
              <table className="min-w-full divide-y divide-gray-200">
                <thead className="bg-gray-50">
                  <tr>
                    <th className="px-6 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">
                      Action
                    </th>
                    <th className="px-6 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">
                      Details
                    </th>
                    <th className="px-6 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">
                      Timestamp
                    </th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-200 bg-white">
                  {activities.map((activity) => (
                    <tr key={activity.id} className="hover:bg-gray-50">
                      <td className="whitespace-nowrap px-6 py-4">
                        {activity.action}
                      </td>
                      <td className="whitespace-nowrap px-6 py-4">
                        {activity.details}
                      </td>
                      <td className="whitespace-nowrap px-6 py-4">
                        {new Date(activity.timestamp).toLocaleString()}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <p className="text-center text-gray-600">No recent activity found</p>
          )}
        </div>
        
        {/* Instruments Section */}
        <div className="rounded-lg bg-white p-6 shadow">
          <h2 className="mb-4 text-xl font-semibold text-gray-800">Available Instruments</h2>
          
          {/* Tabs */}
          <div className="mb-4 border-b border-gray-200">
            <nav className="-mb-px flex space-x-8">
              {instruments.map((instrument) => (
                <button
                  key={instrument.id}
                  onClick={() => handleTabChange(instrument.name)}
                  className={`whitespace-nowrap border-b-2 px-1 py-4 text-sm font-medium ${
                    activeTab === instrument.name
                      ? 'border-blue-500 text-blue-600'
                      : 'border-transparent text-gray-500 hover:border-gray-300 hover:text-gray-700'
                  }`}
                >
                  {instrument.name}
                </button>
              ))}
            </nav>
          </div>
          
          {/* Tab Content */}
          {selectedInstrument && (
            <div>
              {/* Ownership Info */}
              <div className="mb-6 grid grid-cols-1 gap-4 md:grid-cols-3">
                <div className="rounded-lg bg-blue-50 p-4">
                  <p className="text-sm text-gray-500">Client Base Owned</p>
                  <p className="text-2xl font-bold text-blue-600">{selectedInstrument.clientBaseOwned.toLocaleString()}</p>
                </div>
                <div className="rounded-lg bg-green-50 p-4">
                  <p className="text-sm text-gray-500">House Base Owned</p>
                  <p className="text-2xl font-bold text-green-600">{selectedInstrument.houseBaseOwned.toLocaleString()}</p>
                </div>
                <div className="rounded-lg bg-purple-50 p-4">
                  <p className="text-sm text-gray-500">Total Base Owned</p>
                  <p className="text-2xl font-bold text-purple-600">{selectedInstrument.totalBaseOwned.toLocaleString()}</p>
                </div>
              </div>
              
              {/* Open Positions */}
              <div className="mb-6">
                <h3 className="mb-3 text-lg font-medium text-gray-800">Current Open Positions</h3>
                {openPositions.length > 0 ? (
                  <div className="overflow-x-auto">
                    <table className="min-w-full divide-y divide-gray-200">
                      <thead className="bg-gray-50">
                        <tr>
                          <th className="px-6 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">Type</th>
                          <th className="px-6 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">Amount</th>
                          <th className="px-6 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">Open Price</th>
                          <th className="px-6 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">Current Price</th>
                          <th className="px-6 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">Profit</th>
                          <th className="px-6 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">Open Date</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-gray-200 bg-white">
                        {openPositions.map((position) => (
                          <tr key={position.id} className="hover:bg-gray-50">
                            <td className={`whitespace-nowrap px-6 py-4 font-medium ${position.type === 'Buy' ? 'text-green-600' : 'text-red-600'}`}>
                              {position.type}
                            </td>
                            <td className="whitespace-nowrap px-6 py-4">{position.amount.toLocaleString()}</td>
                            <td className="whitespace-nowrap px-6 py-4">{position.openPrice}</td>
                            <td className="whitespace-nowrap px-6 py-4">{position.currentPrice}</td>
                            <td className={`whitespace-nowrap px-6 py-4 font-medium ${position.profit >= 0 ? 'text-green-600' : 'text-red-600'}`}>
                              {position.profit >= 0 ? '+' : ''}{position.profit}
                            </td>
                            <td className="whitespace-nowrap px-6 py-4">{new Date(position.openDate).toLocaleString()}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                ) : (
                  <p className="text-center text-gray-600">No open positions for this instrument</p>
                )}
              </div>
              
              {/* Order History */}
              <div>
                <h3 className="mb-3 text-lg font-medium text-gray-800">Order History</h3>
                {orderHistory.length > 0 ? (
                  <div className="overflow-x-auto">
                    <table className="min-w-full divide-y divide-gray-200">
                      <thead className="bg-gray-50">
                        <tr>
                          <th className="px-6 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">Type</th>
                          <th className="px-6 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">Amount</th>
                          <th className="px-6 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">Open Price</th>
                          <th className="px-6 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">Close Price</th>
                          <th className="px-6 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">Profit</th>
                          <th className="px-6 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">Open Date</th>
                          <th className="px-6 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">Close Date</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-gray-200 bg-white">
                        {orderHistory.map((order) => (
                          <tr key={order.id} className="hover:bg-gray-50">
                            <td className={`whitespace-nowrap px-6 py-4 font-medium ${order.type === 'Buy' ? 'text-green-600' : 'text-red-600'}`}>
                              {order.type}
                            </td>
                            <td className="whitespace-nowrap px-6 py-4">{order.amount.toLocaleString()}</td>
                            <td className="whitespace-nowrap px-6 py-4">{order.openPrice}</td>
                            <td className="whitespace-nowrap px-6 py-4">{order.closePrice}</td>
                            <td className={`whitespace-nowrap px-6 py-4 font-medium ${order.profit >= 0 ? 'text-green-600' : 'text-red-600'}`}>
                              {order.profit >= 0 ? '+' : ''}{order.profit}
                            </td>
                            <td className="whitespace-nowrap px-6 py-4">{new Date(order.openDate).toLocaleString()}</td>
                            <td className="whitespace-nowrap px-6 py-4">{new Date(order.closeDate).toLocaleString()}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                ) : (
                  <p className="text-center text-gray-600">No order history for this instrument</p>
                )}
              </div>
            </div>
          )}
        </div>
      </main>
    </div>
  );
}

export default UserPage;