document.addEventListener('DOMContentLoaded', function() {
    // Get form elements
    const registerForm = document.getElementById('registerForm');
    const nameInput = document.getElementById('name');
    const emailInput = document.getElementById('email');
    const passwordInput = document.getElementById('password');
    const registerButton = registerForm.querySelector('.submit');
    const registerMessage = document.getElementById('registerMessage');
    
    // Base API URL - adjust this to match your Spring Boot server
    const API_BASE_URL = 'http://localhost:8080/api/v1/users';
    
    // Add event listener for registration form submission
    registerForm.addEventListener('submit', function(e) {
        e.preventDefault();
        handleRegistration();
    });
    
    // Handle registration functionality
    async function handleRegistration() {
        // Get input values
        const name = nameInput.value.trim();
        const email = emailInput.value.trim();
        const password = passwordInput.value.trim();
        
        // Validate inputs
        if (!name || !email || !password) {
            showMessage('Please fill in all fields', 'error');
            return;
        }
        
        // Basic email validation
        if (!isValidEmail(email)) {
            showMessage('Please enter a valid email address', 'error');
            return;
        }
        
        // Password length validation (matching backend requirements)
        if (password.length < 6) {
            showMessage('Password must be at least 6 characters long', 'error');
            return;
        }
        
        // Username length validation (matching backend requirements)
        if (name.length < 3) {
            showMessage('Username must be at least 3 characters long', 'error');
            return;
        }
        
        // Show loading state
        setButtonLoading(registerButton, true);
        showMessage('Registering...', 'info');
        
        try {
            // Prepare registration data
            const registrationData = {
                userName: name,
                gmail: email,
                password: password
            };
            
            // Make API call to register endpoint
            const response = await fetch(`${API_BASE_URL}/register`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(registrationData)
            });
            
            // Parse response
            const result = await response.json();
            
            if (response.ok && result.success) {
                // Registration successful
                showMessage('Registration successful! Redirecting to login...', 'success');
                
                // Clear form
                registerForm.reset();
                
                // Redirect to login page after a short delay
                setTimeout(() => {
                    window.location.href = 'login.html';
                }, 2000);
            } else {
                // Registration failed
                const errorMessage = result.message || result.error || 'Registration failed';
                showMessage(errorMessage, 'error');
            }
        } catch (error) {
            // Network or other errors
            console.error('Registration error:', error);
            showMessage('Network error. Please try again.', 'error');
        } finally {
            // Reset loading state
            setButtonLoading(registerButton, false);
        }
    }
    
    // Utility function to validate email format
    function isValidEmail(email) {
        const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
        return emailRegex.test(email);
    }
    
    // Utility function to show messages
    function showMessage(message, type) {
        if (!registerMessage) return;
        
        // Clear previous classes
        registerMessage.className = '';
        
        // Set message and type class
        registerMessage.textContent = message;
        registerMessage.classList.add('message', type);
        
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
});