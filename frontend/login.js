document.addEventListener('DOMContentLoaded', function() {
    // Get form elements
    const loginForm = document.getElementById('loginForm');
    const emailInput = document.getElementById('email');
    const passwordInput = document.getElementById('password');
    const loginButton = loginForm.querySelector('.submit');
    const loginMessage = document.getElementById('loginMessage');
    const statusButton = document.querySelector('.status');
    
    // Base API URLs - adjust these to match your Spring Boot server
    const API_BASE_URL = 'http://localhost:8080/api/v1/users';
    const HEALTH_CHECK_URL = 'http://localhost:8080/api/v1/health';
    
    // Add event listener for login form submission
    loginForm.addEventListener('submit', function(e) {
        e.preventDefault();
        handleLogin();
    });
    
    // Add event listener for status check button
    if (statusButton) {
        statusButton.addEventListener('click', function(e) {
            e.preventDefault();
            checkSystemStatus();
        });
    }
    
    // Handle login functionality
    async function handleLogin() {
        // Get input values
        const email = emailInput.value.trim();
        const password = passwordInput.value.trim();
        
        // Validate inputs
        if (!email || !password) {
            showMessage('Please fill in all fields', 'error');
            return;
        }
        
        // Show loading state
        setButtonLoading(loginButton, true);
        showMessage('Logging in...', 'info');
        
        try {
            // Prepare login data (using userName as email based on backend DTO)
            const loginData = {
                userName: email,
                password: password
            };
            
            // Make API call to login endpoint
            const response = await fetch(`${API_BASE_URL}/login`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(loginData)
            });
            
            // Parse response
            const result = await response.json();
            
            if (response.ok && result.success) {
                // Login successful
                showMessage('Login successful! Redirecting...', 'success');
                
                // Store JWT token in localStorage
                localStorage.setItem('authToken', result.data.token);
                localStorage.setItem('user', JSON.stringify(result.data.user));
                
                // Redirect to a dashboard or home page (you can customize this)
                setTimeout(() => {
                    window.location.href = 'dashboard.html'; // Or wherever you want to redirect
                }, 1500);
            } else {
                // Login failed
                const errorMessage = result.message || result.error || 'Login failed';
                showMessage(errorMessage, 'error');
            }
        } catch (error) {
            // Network or other errors
            console.error('Login error:', error);
            showMessage('Network error. Please try again.', 'error');
        } finally {
            // Reset loading state
            setButtonLoading(loginButton, false);
        }
    }
    
    // Check system health status
    async function checkSystemStatus() {
        setButtonLoading(statusButton, true);
        showMessage('Checking system status...', 'info');
        
        try {
            // Make API call to health check endpoint
            const response = await fetch(HEALTH_CHECK_URL, {
                method: 'GET',
                headers: {
                    'Content-Type': 'application/json'
                }
            });
            
            const result = await response.json();
            
            if (response.ok) {
                showMessage(`System Status: ${result.status} - ${result.service} v${result.version}`, 'success');
            } else {
                showMessage('System status check failed', 'error');
            }
        } catch (error) {
            console.error('Status check error:', error);
            showMessage('Network error. Please try again.', 'error');
        } finally {
            setButtonLoading(statusButton, false);
        }
    }
    
    // Utility function to show messages
    function showMessage(message, type) {
        if (!loginMessage) return;
        
        // Clear previous classes
        loginMessage.className = '';
        
        // Set message and type class
        loginMessage.textContent = message;
        loginMessage.classList.add('message', type);
        
        // Add CSS for different message types if not already present
        if (!document.querySelector('#message-styles')) {
            const style = document.createElement('style');
            style.id = 'message-styles';
            style.textContent = `
                .message {
                    padding: 10px;
                    border-radius: 4px;
                    margin: 10px 0;
                    text-align: center;
                    font-weight: 500;
                }
                .message.success {
                    background-color: #4CAF50;
                    color: white;
                }
                .message.error {
                    background-color: #f44336;
                    color: white;
                }
                .message.info {
                    background-color: #2196F3;
                    color: white;
                }
            `;
            document.head.appendChild(style);
        }
    }
    
    // Utility function to set button loading state
    function setButtonLoading(button, loading) {
        if (loading) {
            button.classList.add('loading');
            button.disabled = true;
            // Store original text if not already stored
            if (!button.dataset.originalText) {
                button.dataset.originalText = button.textContent;
            }
            button.textContent = 'Loading...';
        } else {
            button.classList.remove('loading');
            button.disabled = false;
            // Restore original text
            if (button.dataset.originalText) {
                button.textContent = button.dataset.originalText;
            }
        }
    }
    
    // Check if user is already logged in on page load
    function checkInitialAuthState() {
        const token = localStorage.getItem('authToken');
        if (token) {
            // User appears to be logged in, check if token is still valid
            // You might want to implement token validation here
            console.log('User appears to be logged in');
        }
    }
    
    // Run initial auth state check
    checkInitialAuthState();
});