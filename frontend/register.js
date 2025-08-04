document.addEventListener('DOMContentLoaded', function() {
    // Get references to the HTML elements we need to interact with.
    const registerForm = document.getElementById('registerForm');
    const nameInput = document.getElementById('name');
    const emailInput = document.getElementById('email');
    const passwordInput = document.getElementById('password');
    const registerButton = registerForm.querySelector('.submit');
    const registerMessage = document.getElementById('registerMessage');

    // Add an event listener to the registration form to handle submission.
    registerForm.addEventListener('submit', function(e) {
        // Prevent the default form submission, which would reload the page.
        e.preventDefault();
        // Call the function to handle the registration process.
        handleRegistration();
    });

    // This function handles the user registration process.
    async function handleRegistration() {
        // Get the trimmed values from the input fields.
        const name = nameInput.value.trim();
        const email = emailInput.value.trim();
        const password = passwordInput.value.trim();

        // Validate that all fields are filled out.
        if (!name || !email || !password) {
            showMessage(registerMessage, 'Please fill in all fields', 'error');
            return;
        }

        // Validate the email format.
        if (!isValidEmail(email)) {
            showMessage(registerMessage, 'Please enter a valid email address', 'error');
            return;
        }

        // Validate the password length.
        if (password.length < 6) {
            showMessage(registerMessage, 'Password must be at least 6 characters long', 'error');
            return;
        }

        // Validate the username length.
        if (name.length < 3) {
            showMessage(registerMessage, 'Username must be at least 3 characters long', 'error');
            return;
        }

        // Put the register button in a loading state.
        setButtonLoading(registerButton, true);
        // Show an informational message to the user.
        showMessage(registerMessage, 'Registering...', 'info');

        try {
            // Create the data object to send to the server.
            const registrationData = {
                userName: name,
                gmail: email,
                password: password
            };

            // Send a POST request to the register endpoint with the user's details.
            const response = await fetch(API_CONFIG.BASE_URL + API_CONFIG.endpoints.REGISTER, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(registrationData)
            });

            // Process the server's response.
            await handleApiResponse(response);

            // If registration is successful, show a success message.
            showMessage(registerMessage, 'Registration successful! Redirecting to login...', 'success');

            // Clear the form fields.
            registerForm.reset();

            // Redirect the user to the login page after a short delay.
            setTimeout(() => {
                window.location.href = 'login.html';
            }, 2000);

        } catch (error) {
            // If an error occurs, log it to the console and show an error message to the user.
            console.error('Registration error:', error);
            showMessage(registerMessage, error.message, 'error');
        } finally {
            // No matter what, remove the loading state from the register button.
            setButtonLoading(registerButton, false);
        }
    }

    // This utility function validates the format of an email address using a regular expression.
    function isValidEmail(email) {
        const emailRegex = /^[^
@]+@[^
@]+\.[^
@]+$/;
        return emailRegex.test(email);
    }
});