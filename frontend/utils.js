
// This function displays a message to the user in a specified element.
// It's used to show feedback like success or error messages.
function showMessage(element, message, type) {
    // If the element to display the message in doesn't exist, do nothing.
    if (!element) return;

    // Clear any previous styling from the message element.
    element.className = '';

    // Set the message text and add the appropriate CSS class for styling (e.g., 'success', 'error').
    element.textContent = message;
    element.classList.add('message', type);

    // This part dynamically adds the CSS styles for the messages to the document's head.
    // It only does this once to avoid duplicating the styles.
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

// This function handles the loading state of a button.
// It disables the button and shows a "Loading..." text to prevent multiple clicks during an operation.
function setButtonLoading(button, loading) {
    if (loading) {
        // Add a 'loading' class for styling and disable the button.
        button.classList.add('loading');
        button.disabled = true;
        // Save the button's original text if it hasn't been saved already.
        if (!button.dataset.originalText) {
            button.dataset.originalText = button.textContent;
        }
        // Change the button text to indicate loading.
        button.textContent = 'Loading...';
    } else {
        // Remove the 'loading' class and re-enable the button.
        button.classList.remove('loading');
        button.disabled = false;
        // Restore the button's original text.
        if (button.dataset.originalText) {
            button.textContent = button.dataset.originalText;
        }
    }
}

// This function centralizes the handling of API responses.
// It checks if the response was successful and parses the JSON data.
// If the response is not okay, it throws an error with a message from the server.
async function handleApiResponse(response) {
    // Get the JSON data from the response.
    const result = await response.json();
    // Check if the HTTP response is successful (e.g., status 200) and if the business logic was successful.
    if (response.ok && result.success) {
        // If everything is okay, return the data.
        return result;
    } else {
        // If there was an error, create a descriptive error message.
        const errorMessage = result.message || result.error || 'An unknown error occurred.';
        // Throw an error to be caught by the calling function's catch block.
        throw new Error(errorMessage);
    }
}
