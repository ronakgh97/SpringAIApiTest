# Frontend Login System

This is a complete frontend login and registration system that integrates with a Spring Boot backend using JWT authentication.

## Features

- User login with JWT token authentication
- User registration with validation
- System health status check
- Responsive design that works on all device sizes
- Error handling and user feedback
- Dashboard for authenticated users
- Token storage in localStorage
- Profile retrieval for authenticated users

## Files

- `login.html` - Login page
- `register.html` - Registration page
- `dashboard.html` - User dashboard (redirected after successful login)
- `login.js` - JavaScript for login functionality
- `register.js` - JavaScript for registration functionality
- `style.css` - Shared styles for all pages
- `test-api.html` - API testing page

## API Integration

The frontend integrates with the following Spring Boot API endpoints:

### Login
```
POST /api/v1/users/login
Content-Type: application/json

{
  "userName": "string",
  "password": "string"
}
```

### Registration
```
POST /api/v1/users/register
Content-Type: application/json

{
  "userName": "string",
  "gmail": "string",
  "password": "string"
}
```

### Get User Profile
```
GET /api/v1/users/profile
Authorization: Bearer <JWT_TOKEN>
```

### System Health Check
```
GET /api/v1/health
```

## JWT Token Handling

- Tokens are stored in `localStorage` after successful login
- Tokens are automatically included in the Authorization header for authenticated requests
- Tokens are cleared from `localStorage` on logout

## Usage

1. Start the Spring Boot backend server
2. Open `login.html` in a web browser
3. Register a new user account using `register.html` if you don't have one
4. Login with your credentials
5. You will be redirected to the dashboard after successful authentication
6. Use the "Check Status" button to verify system health

## Error Handling

The system provides user-friendly error messages for:
- Invalid credentials
- Network errors
- Validation errors
- Expired or invalid tokens
- System health issues

## Responsive Design

The UI is fully responsive and works on:
- Mobile devices
- Tablets
- Desktop computers

Media queries adjust the layout for different screen sizes.

## Testing

Use `test-api.html` to test the API endpoints directly and verify integration with the backend.

## Security Notes

- Passwords are handled securely (hashed) by the backend
- JWT tokens are stored in localStorage (consider using httpOnly cookies for production)
- All API communication should use HTTPS in production