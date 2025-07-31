import axios from 'axios';

// Create a custom instance of axios
const api = axios.create({
  baseURL: '/api',
  withCredentials: true, // Important for cookies to be sent with requests
});

// Add a response interceptor to handle 401 and 403 errors
api.interceptors.response.use(
  (response) => {
    // Any status code within the range of 2xx causes this function to trigger
    return response;
  },
  (error) => {
    // Any status codes outside the range of 2xx cause this function to trigger
    if (error.response && (error.response.status === 401 || error.response.status === 403)) {
      // If we receive a 401 Unauthorized or 403 Forbidden response, redirect to login page
      console.log('Authentication error: Redirecting to login page');
      
      // Clear any authentication data
      localStorage.removeItem('isAuthenticated');

      if (window.location.pathname !== '/login') {
        // Redirect to login page
        window.location.href = '/login';
      }
    }
    
    // Return the error for further handling
    return Promise.reject(error);
  }
);

export default api;