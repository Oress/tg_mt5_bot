import { useState } from 'react';
import { useNavigate } from 'react-router-dom';

function Main() {
  const [searchTerm, setSearchTerm] = useState('');
  const [users, setUsers] = useState([]);
  const [isLoading, setIsLoading] = useState(false);
  const navigate = useNavigate();

  // Mock user data - in a real app, this would come from an API
  const mockUsers = [
    { id: 1, telegramId: '12345678', username: 'user1' },
    { id: 2, telegramId: '87654321', username: 'user2' },
    { id: 3, telegramId: '11223344', username: 'admin' },
    { id: 4, telegramId: '44332211', username: 'trader1' },
    { id: 5, telegramId: '55667788', username: 'trader2' },
  ];

  const handleSearch = (e) => {
    e.preventDefault();
    setIsLoading(true);
    
    // Simulate API call with setTimeout
    setTimeout(() => {
      const filteredUsers = mockUsers.filter(user => 
        user.username.toLowerCase().includes(searchTerm.toLowerCase()) ||
        user.telegramId.includes(searchTerm)
      );
      setUsers(filteredUsers);
      setIsLoading(false);
    }, 500);
  };

  const handleUserClick = (userId) => {
    navigate(`/user/${userId}`);
  };

  const handleLogout = () => {
    localStorage.removeItem('isAuthenticated');
    navigate('/');
  };

  return (
    <div className="min-h-screen bg-gray-100">
      <header className="bg-white shadow">
        <div className="mx-auto flex max-w-7xl items-center justify-between px-4 py-6 sm:px-6 lg:px-8">
          <h1 className="text-3xl font-bold text-gray-900">Dashboard</h1>
          <button
            onClick={handleLogout}
            className="rounded-md bg-red-600 px-4 py-2 text-white hover:bg-red-700"
          >
            Logout
          </button>
        </div>
      </header>
      
      <main className="mx-auto max-w-7xl px-4 py-6 sm:px-6 lg:px-8">
        <div className="mb-8 rounded-lg bg-white p-6 shadow">
          <h2 className="mb-4 text-xl font-semibold text-gray-800">Search Users</h2>
          
          <form onSubmit={handleSearch} className="mb-6 flex">
            <input
              type="text"
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              placeholder="Search by username or Telegram ID"
              className="flex-grow rounded-l-md border border-gray-300 px-4 py-2 focus:border-blue-500 focus:outline-none focus:ring-blue-500"
            />
            <button
              type="submit"
              className="rounded-r-md bg-blue-600 px-4 py-2 text-white hover:bg-blue-700"
            >
              Search
            </button>
          </form>
          
          {isLoading ? (
            <div className="text-center">
              <p className="text-gray-600">Loading...</p>
            </div>
          ) : (
            <div>
              {users.length > 0 ? (
                <div className="overflow-x-auto">
                  <table className="min-w-full divide-y divide-gray-200">
                    <thead className="bg-gray-50">
                      <tr>
                        <th className="px-6 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">
                          Telegram ID
                        </th>
                        <th className="px-6 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">
                          Username
                        </th>
                        <th className="px-6 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">
                          Actions
                        </th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-gray-200 bg-white">
                      {users.map((user) => (
                        <tr key={user.id} className="hover:bg-gray-50">
                          <td className="whitespace-nowrap px-6 py-4">
                            {user.telegramId}
                          </td>
                          <td className="whitespace-nowrap px-6 py-4">
                            {user.username}
                          </td>
                          <td className="whitespace-nowrap px-6 py-4">
                            <button
                              onClick={() => handleUserClick(user.id)}
                              className="rounded bg-blue-100 px-3 py-1 text-blue-800 hover:bg-blue-200"
                            >
                              View Details
                            </button>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              ) : searchTerm ? (
                <p className="text-center text-gray-600">No users found matching "{searchTerm}"</p>
              ) : (
                <p className="text-center text-gray-600">Enter a search term to find users</p>
              )}
            </div>
          )}
        </div>
      </main>
    </div>
  );
}

export default Main;