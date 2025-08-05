// This script runs after the HTML document has been fully loaded.
document.addEventListener('DOMContentLoaded', function() {
    // Get references to the HTML elements we need to interact with.
    const loginForm = document.getElementById('loginForm');
    const usernameInput = document.getElementById('username');
    const passwordInput = document.getElementById('password');
    const loginButton = loginForm.querySelector('.submit');
    const loginMessage = document.getElementById('loginMessage');
    const statusButton = document.querySelector('.status');

    // Add an event listener to the login form to handle submission.
    loginForm.addEventListener('submit', function(e) {
        // Prevent the default form submission, which would reload the page.
        e.preventDefault();
        // Call the function to handle the login process.
        handleLogin();
    });

    // Add an event listener to the status button to check the system status.
    if (statusButton) {
        statusButton.addEventListener('click', function(e) {
            // Prevent any default button action.
            e.preventDefault();
            // Call the function to check the system status.
            checkSystemStatus();
        });
    }

    // This function handles the user login process.
    async function handleLogin() {
        // Get the trimmed values from the email and password input fields.
        const username = usernameInput.value.trim();
        const password = passwordInput.value.trim();

        // Check if either field is empty.
        if (!username || !password) {
            // If so, show an error message and stop the function.
            showMessage(loginMessage, 'Please fill in all fields', 'error');
            return;
        }

        // Put the login button in a loading state.
        setButtonLoading(loginButton, true);
        // Show an informational message to the user.
        showMessage(loginMessage, 'Logging in...', 'info');

        try {
            // Create the data object to send to the server.
            const loginData = {
                userName: username,
                password: password
            };

            // Send a POST request to the login endpoint with the user's credentials.
            const response = await fetch(API_CONFIG.BASE_URL + API_CONFIG.endpoints.LOGIN, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(loginData)
            });

            const result = await response.json();

            if (!response.ok || !result.success) {
                const errorMessage = result.message || 'Login failed. Please try again.';
                throw new Error(errorMessage);
            }

            // If login is successful, show a success message.
            showMessage(loginMessage, 'Login successful! Redirecting...', 'success');

            // Store the authentication token and user data in the browser's local storage.
            localStorage.setItem('authToken', result.data.token);
            localStorage.setItem('user', JSON.stringify(result.data.user));

            // Redirect the user to the dashboard page after a short delay.
            setTimeout(() => {
                window.location.href = 'dashboard.html';
            }, 1500);

        } catch (error) {
            // If an error occurs, log it to the console and show an error message to the user.
            console.error('Login error:', error);
            showMessage(loginMessage, error.message, 'error');
        } finally {
            // No matter what, remove the loading state from the login button.
            setButtonLoading(loginButton, false);
        }
    }

    // This function checks the health of the backend system.
    async function checkSystemStatus() {
        // Put the status button in a loading state.
        setButtonLoading(statusButton, true);
        // Show an informational message.
        showMessage(loginMessage, 'Checking system status...', 'info');

        try {
            // Send a GET request to the health check endpoint.
            const response = await fetch(API_CONFIG.BASE_URL + API_CONFIG.endpoints.HEALTH_CHECK, {
                method: 'GET',
                headers: {
                    'Content-Type': 'application/json'
                }
            });

            // Get the JSON data from the response.
            const result = await response.json();

            // If the request was successful, show the system status.
            if (response.ok) {
                showMessage(loginMessage, `System Status: ${result.status} - ${result.service} v${result.version}`, 'success');
            } else {
                // Otherwise, show an error message.
                showMessage(loginMessage, 'System status check failed', 'error');
            }
        } catch (error) {
            // If a network error occurs, log it and show an error message.
            console.error('Status check error:', error);
            showMessage(loginMessage, 'Network error. Please try again.', 'error');
        } finally {
            // Always remove the loading state from the status button.
            setButtonLoading(statusButton, false);
        }
    }

    // This function checks if the user is already logged in when the page loads.
    function checkInitialAuthState() {
        // Check if an authentication token exists in local storage.
        const token = localStorage.getItem('authToken');
        if (token) {
            // If a token is found, it suggests the user is already logged in.
            // You could add logic here to automatically redirect to the dashboard.
            console.log('User appears to be logged in');
            // window.location.href = 'dashboard.html';
        }
    }

    // Run the initial authentication check when the script loads.
    checkInitialAuthState();
});