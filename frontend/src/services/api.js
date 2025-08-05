// This object centralizes all API configuration for the application.
const API_CONFIG = {
    // The base URL for all API requests.
    // This makes it easy to change the API server address in one place.
    BASE_URL: 'http://localhost:8080/api/v1',

    // This object defines the specific endpoints for different API actions.
    // Using a structure like this helps avoid typos and makes the code more readable.
    endpoints: {
        // Endpoint for user login.
        LOGIN: '/users/login',
        // Endpoint for user registration.
        REGISTER: '/users/register',
        // Endpoint to get user profile information (used for token validation).
        PROFILE: '/users/profile',
        // Endpoint for checking the health status of the backend server.
        HEALTH_CHECK: '/health'
    }
};

export default API_CONFIG;
