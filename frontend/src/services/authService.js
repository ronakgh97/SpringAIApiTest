import API_CONFIG from './api';

const login = async (username, password) => {
    const response = await fetch(API_CONFIG.BASE_URL + API_CONFIG.endpoints.LOGIN, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({ userName: username, password: password })
    });
    return handleApiResponse(response);
};

const register = async (username, email, password) => {
    const response = await fetch(API_CONFIG.BASE_URL + API_CONFIG.endpoints.REGISTER, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({ userName: username, gmail: email, password: password })
    });
    return handleApiResponse(response);
};

const validateToken = async (token) => {
    const response = await fetch(API_CONFIG.BASE_URL + API_CONFIG.endpoints.PROFILE, {
        method: 'GET',
        headers: {
            'Authorization': `Bearer ${token}`,
            'Content-Type': 'application/json'
        }
    });
    return response.ok;
};

const checkSystemStatus = async () => {
    const response = await fetch(API_CONFIG.BASE_URL + API_CONFIG.endpoints.HEALTH_CHECK, {
        method: 'GET',
        headers: {
            'Content-Type': 'application/json'
        }
    });
    if (!response.ok) {
        const errorData = await response.json().catch(() => ({ message: 'System status check failed' }));
        throw new Error(errorData.message || 'System status check failed');
    }
    return response.json();
};

const handleApiResponse = async (response) => {
    const result = await response.json();
    if (response.ok && result.success) {
        return result;
    } else {
        const errorMessage = result.message || result.error || 'An unknown error occurred.';
        throw new Error(errorMessage);
    }
};

export { login, register, validateToken, checkSystemStatus };
