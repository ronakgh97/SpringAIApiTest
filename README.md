# BackendAI - Java Spring Boot AI Chat Application

A comprehensive Java Spring Boot application providing AI-powered chat functionality with user authentication, session management, and robust error handling.

## 🚀 Features

- **User Authentication**: JWT-based authentication with registration and login
- **Session Management**: Create, read, update, delete chat sessions
- **AI Integration**: OpenAI GPT integration for intelligent conversations
- **Security**: Role-based access control and session isolation
- **Data Transfer Objects**: Clean API contracts with input validation
- **Error Handling**: Comprehensive error responses with detailed messages
- **MongoDB Integration**: Document-based data storage

## 🏗️ Architecture

### Technology Stack
- **Backend**: Java 24, Spring Boot 3.4.8
- **Security**: Spring Security with JWT tokens
- **Database**: MongoDB with Spring Data
- **AI**: Spring AI with OpenAI integration
- **Validation**: Jakarta Validation API
- **Documentation**: OpenAPI/Swagger

### Project Structure
```
📁 BackendAI/
├── 📁 src/main/java/com/AI4Java/BackendAI/
│   ├── 📁 dto/                    # Data Transfer Objects
│   │   ├── 📁 user/              # User-related DTOs
│   │   ├── 📁 session/           # Session management DTOs
│   │   ├── 📁 message/           # Message DTOs
│   │   └── 📄 ApiResponse.java    # Generic API response wrapper
│   │   └── 📄 ErrorResponseDto.java # Structured error responses
│   ├── 📁 entries/               # Database entities
│   ├── 📁 exceptions/            # Custom exception classes
│   ├── 📁 mapper/                # Entity ↔ DTO mappers
│   ├── 📁 MyController/          # REST controllers
│   ├── 📁 services/              # Business logic services
│   ├── 📁 repository/            # Data access layer
│   ├── 📁 config/                # Configuration classes
│   └── 📁 utils/                 # Utility classes
├── 📄 pom.xml                    # Maven dependencies
└── 📄 README.md                  # This file
```

## 🔧 Installation & Setup

### Prerequisites
- Java 24 or higher
- Maven 3.6+
- MongoDB 4.4+
- OpenAI API key

### Setup Steps

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd BackendAI
   ```

2. **Configure environment variables**
   Create a `.env` file in the root directory:
   ```env
   OPENAI_API_KEY=your_openai_api_key_here
   MONGODB_URI=mongodb://localhost:27017/backendai
   JWT_SECRET=your_jwt_secret_key_here
   ```

3. **Install dependencies**
   ```bash
   mvn clean install
   ```

4. **Run the application**
   ```bash
   mvn spring-boot:run
   ```

The application will start on `http://localhost:8080`

## 📚 API Documentation

### Base URL
```
http://localhost:8080/api/v1
```

## 🔐 Authentication Endpoints

### User Registration
```http
POST /users/register
Content-Type: application/json

{
    "userName": "john_doe",
    "password": "securePassword123",
    "gmail": "john@example.com"
}
```

**Success Response (201):**
```json
{
    "success": true,
    "message": "User registered successfully",
    "data": {
        "userId": "60b8d6f5c9e5a12345678901",
        "userName": "john_doe",
        "gmail": "john@example.com",
        "roles": ["USER"],
        "sessionCount": 0
    }
}
```

### User Login
```http
POST /users/login
Content-Type: application/json

{
    "userName": "john_doe",
    "password": "securePassword123"
}
```

**Success Response (200):**
```json
{
    "success": true,
    "message": "Login successful",
    "data": {
        "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
        "tokenType": "Bearer",
        "user": {
            "userId": "60b8d6f5c9e5a12345678901",
            "userName": "john_doe",
            "gmail": "john@example.com",
            "roles": ["USER"],
            "sessionCount": 3
        }
    }
}
```

### Get User Profile
```http
GET /users/profile
Authorization: Bearer <jwt_token>
```

## 🎯 Session Management Endpoints

### Create Session
```http
POST /sessions/create
Authorization: Bearer <jwt_token>
Content-Type: application/json

{
    "nameSession": "AI Research Discussion",
    "model": "gpt-4"
}
```

**Success Response (201):**
```json
{
    "success": true,
    "message": "Session created successfully",
    "data": {
        "sessionId": "60b8d6f5c9e5a12345678902",
        "nameSession": "AI Research Discussion",
        "model": "gpt-4",
        "dateTime": "2025-07-28T18:30:00",
        "messages": [],
        "messageCount": 0
    }
}
```

### Get All Sessions
```http
GET /sessions
Authorization: Bearer <jwt_token>
```

**Success Response (200):**
```json
{
    "success": true,
    "message": "Found 3 sessions",
    "data": [
        {
            "sessionId": "60b8d6f5c9e5a12345678902",
            "nameSession": "AI Research Discussion",
            "model": "gpt-4",
            "dateTime": "2025-07-28T18:30:00",
            "messages": null,
            "messageCount": 5
        }
    ]
}
```

### Get Single Session
```http
GET /sessions/{sessionId}
Authorization: Bearer <jwt_token>
```

### Update Session
```http
PUT /sessions/{sessionId}
Authorization: Bearer <jwt_token>
Content-Type: application/json

{
    "nameSession": "Updated Session Name"
}
```

### Delete Session
```http
DELETE /sessions/{sessionId}
Authorization: Bearer <jwt_token>
```

## 💬 Chat Endpoints

### Send Chat Message
```http
POST /chat
Authorization: Bearer <jwt_token>
Content-Type: application/json

{
    "prompt": "Explain quantum computing in simple terms",
    "model": "gpt-4",
    "sessionId": "60b8d6f5c9e5a12345678902"
}
```

## 🚨 Error Handling

### Error Response Format
All errors follow a consistent structure:

```json
{
    "success": false,
    "message": "Human-readable error message",
    "errorCode": "MACHINE_READABLE_CODE",
    "status": 400,
    "timestamp": "2025-07-28T18:30:00",
    "path": "/api/v1/endpoint",
    "fieldErrors": {
        "fieldName": ["Validation error message"]
    }
}
```

### Common Error Codes

| Error Code | HTTP Status | Description |
|------------|-------------|-------------|
| `VALIDATION_ERROR` | 400 | Input validation failed |
| `USER_NOT_FOUND` | 404 | User does not exist |
| `USER_ALREADY_EXISTS` | 409 | Username already taken |
| `INVALID_CREDENTIALS` | 401 | Login credentials invalid |
| `SESSION_NOT_FOUND` | 404 | Session does not exist |
| `SESSION_ACCESS_DENIED` | 403 | User cannot access session |
| `AUTHENTICATION_FAILED` | 401 | JWT token invalid |
| `ACCESS_DENIED` | 403 | Insufficient permissions |
| `INTERNAL_ERROR` | 500 | Unexpected server error |

### Example Error Responses

**Validation Error (400):**
```json
{
    "success": false,
    "message": "Validation failed",
    "errorCode": "VALIDATION_ERROR",
    "status": 400,
    "timestamp": "2025-07-28T18:30:00",
    "path": "/api/v1/users/register",
    "fieldErrors": {
        "userName": ["Username must be between 3 and 50 characters"],
        "gmail": ["Email should be valid"],
        "password": ["Password is required"]
    }
}
```

**Session Not Found (404):**
```json
{
    "success": false,
    "message": "Session not found: 60b8d6f5c9e5a12345678999",
    "errorCode": "SESSION_NOT_FOUND",
    "status": 404,
    "timestamp": "2025-07-28T18:30:00",
    "path": "/api/v1/sessions/60b8d6f5c9e5a12345678999"
}
```

## 🛡️ Security Features

### Authentication 8 Authorization
- **JWT Tokens**: Stateless authentication with configurable expiration
- **Role-Based Access**: Support for USER and ADMIN roles
- **Session Isolation**: Users can only access their own sessions
- **Password Security**: BCrypt password hashing

### Input Validation
- **DTO Validation**: Jakarta validation annotations on all input DTOs
- **Field-Level Errors**: Detailed validation messages for each field
- **Size Constraints**: Appropriate limits on all text fields
- **Email Validation**: RFC-compliant email format validation

### Data Protection
- **No Sensitive Data Exposure**: Passwords never returned in responses
- **Clean APIs**: DTOs prevent internal data structure exposure
- **Error Information Control**: Structured errors without sensitive details

## 🔧 Configuration

### Application Properties
Key configuration properties in `application.yml`:

```yaml
spring:
  data:
    mongodb:
      uri: ${MONGODB_URI:mongodb://localhost:27017/backendai}
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}

jwt:
  secret: ${JWT_SECRET}
  expiration: 86400000  # 24 hours

server:
  port: 8080
```

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `OPENAI_API_KEY` | OpenAI API key for AI integration | Required |
| `MONGODB_URI` | MongoDB connection string | `mongodb://localhost:27017/backendai` |
| `JWT_SECRET` | Secret key for JWT signing | Required |

## 🧪 Testing

### Running Tests
```bash
# Run all tests
mvn test

# Run with coverage
mvn test jacoco:report
```

### API Testing
Use tools like Postman, Insomnia, or curl to test the API endpoints. 

Example curl command:
```bash
# Register user
curl -X POST http://localhost:8080/api/v1/users/register \
  -H "Content-Type: application/json" \
  -d '{"userName":"testuser","password":"password123","gmail":"test@example.com"}'

# Login
curl -X POST http://localhost:8080/api/v1/users/login \
  -H "Content-Type: application/json" \
  -d '{"userName":"testuser","password":"password123"}'
```

## 📊 Data Models

### User Entity
```java
@Document(collection = "userEntries")
public class UserEntries {
    @Id private ObjectId userId;
    @Indexed(unique = true) private String userName;
    private String password;
    private String gmail;
    @DBRef private ListSessionEntries sessionEntries;
    private ListString roles;
}
```

### Session Entity
```java
@Document(collection = "sessionsEntries")
public class SessionEntries {
    @Id private ObjectId sessionId;
    private String nameSession;
    private String model;
    private LocalDateTime dateTime;
    private ListMessageEntries messages;
}
```

### Message Entity
```java
public class MessageEntries {
    private String role;           // "user" or "assistant"
    private String content;        // Message content
    private LocalDateTime timestamp;
}
```

## 🔄 Development Workflow

### Adding New Endpoints
1. **Create DTOs** for request/response objects
2. **Add validation annotations** to input DTOs
3. **Create mapper classes** for entity ↔ DTO conversion
4. **Update controller methods** to use DTOs and throw custom exceptions
5. **Let GlobalExceptionHandler** handle errors automatically

### Example Pattern
```java
@PostMapping("/endpoint")
public ResponseEntityApiResponseResponseDto method(
    @Valid @RequestBody RequestDto requestDto) {
    
    // Convert DTO to entity
    Entity entity = mapper.toEntity(requestDto);
    
    // Business logic
    Entity savedEntity = service.save(entity);
    
    // Convert back to response DTO
    ResponseDto responseDto = mapper.toResponseDto(savedEntity);
    
    return ResponseEntity.ok(ApiResponse.success("Success message", responseDto));
}
```

## 🚀 Deployment

### Docker Deployment
```dockerfile
FROM openjdk:24-jre-slim
COPY target/BackendAI-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

### Production Considerations
- Set up proper MongoDB cluster
- Configure SSL/TLS certificates
- Set up monitoring and logging
- Configure environment-specific properties
- Set up CI/CD pipeline

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## 📝 License

This project is licensed under the MIT License - see the LICENSE file for details.

## 🙏 Acknowledgments

- Spring Boot team for the excellent framework
- OpenAI for AI integration capabilities
- MongoDB for flexible document storage
- Jakarta Validation for robust input validation

## 📞 Support

For support and questions:
- Create an issue in the repository
- Check the API documentation at `/swagger-ui.html` when running locally
- Review the error response format for troubleshooting

---

**Built with ❤️ using Java Spring Boot**
