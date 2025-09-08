# SpringAI

Project based on Java AI Framework  

## Features

*   **User Authentication:** Secure user registration and login with JWT authentication.
*   **Session Management:** Create, retrieve, update, and delete chat sessions.
*   **AI Chat:** Real-time chat with AI models, with support for different providers.
*   **Extensible AI Tools:** A variety of tools to enhance the AI's capabilities, such as web search, code execution, and more.
*   **Admin Panel:** Manage users and their roles.
*   **Email Verification:** Verify user accounts via email.

## Technologies Used

*   **Java 24**
*   **Spring Boot 3.4.8**
*   **Spring AI 1.0.0**
*   **MongoDB:** NoSQL database for storing user data and chat sessions.
*   **Spring Security:** For authentication and authorization.
*   **JJWT:** For creating and validating JSON Web Tokens.
*   **Maven:** Dependency management.
*   **Selenium & Playwright:** For web scraping and browser automation tools.

## Getting Started

### Prerequisites

*   Java 24 or higher
*   Maven
*   MongoDB instance running

### Installation

1.  Clone the repository:
    ```sh
    git clone https://github.com/your-username/BackendAI.git
    ```
2.  Navigate to the project directory:
    ```sh
    cd BackendAI
    ```
3.  Install the dependencies:
    ```sh
    mvn install
    ```

### Running the Application

1.  Create an `application.properties` file in `src/main/resources` and add the following properties:
    ```properties
    # MongoDB Configuration
    spring.data.mongodb.uri=mongodb://localhost:27017/your-db-name

    # JWT Configuration
    jwt.secret=your-jwt-secret

    # Email Configuration
    spring.mail.host=smtp.gmail.com
    spring.mail.port=587
    spring.mail.username=your-email@gmail.com
    spring.mail.password=your-email-password
    spring.mail.properties.mail.smtp.auth=true
    spring.mail.properties.mail.smtp.starttls.enable=true

    # Spring AI Configuration (choose one)
    # For OpenRouter
    spring.ai.openrouter.api-key=your-openrouter-api-key

    # For LMStudio
    spring.ai.lmstudio.url=http://localhost:1234/v1
    ```
2.  Run the application:
    ```sh
    mvn spring-boot:run
    ```

## API Endpoints

### User Controller

#### Register User

*   **POST** `/api/v1/users/register`

**Request Body:**

```json
{
  "userName": "testuser",
  "password": "password123",
  "gmail": "testuser@example.com"
}
```

**Response Body:**

```json
{
  "success": true,
  "message": "User registered successfully",
  "data": {
    "userId": "60d5f3f7e8a8d82e8c3e8e3e",
    "userName": "testuser",
    "gmail": "testuser@example.com",
    "roles": [
      "USER"
    ],
    "sessionCount": 0,
    "isVerified": false
  }
}
```

#### Login User

*   **POST** `/api/v1/users/login`

**Request Body:**

```json
{
  "userName": "testuser",
  "password": "password123"
}
```

**Response Body:**

```json
{
  "success": true,
  "message": "Login successful",
  "data": {
    "token": "your-jwt-token",
    "tokenType": "Bearer",
    "user": {
      "userId": "60d5f3f7e8a8d82e8c3e8e3e",
      "userName": "testuser",
      "gmail": "testuser@example.com",
      "roles": [
        "USER"
      ],
      "sessionCount": 0,
      "isVerified": false
    }
  }
}
```

#### Get User Profile

*   **GET** `/api/v1/users/profile`

**Response Body:**

```json
{
  "success": true,
  "message": "User profile retrieved successfully",
  "data": {
    "userId": "60d5f3f7e8a8d82e8c3e8e3e",
    "userName": "testuser",
    "gmail": "testuser@example.com",
    "roles": [
      "USER"
    ],
    "sessionCount": 0,
    "isVerified": false
  }
}
```

### Session Controller

#### Create Session

*   **POST** `/api/v1/sessions/create`

**Request Body:**

```json
{
  "nameSession": "My First Session",
  "model": "gpt-3.5-turbo"
}
```

**Response Body:**

```json
{
  "success": true,
  "message": "Session created successfully",
  "data": {
    "sessionId": "60d5f3f7e8a8d82e8c3e8e3f",
    "nameSession": "My First Session",
    "model": "gpt-3.5-turbo",
    "dateTime": "2025-08-23T10:00:00",
    "messages": [],
    "messageCount": 0
  }
}
```

#### Get All Sessions

*   **GET** `/api/v1/sessions`

**Response Body:**

```json
{
  "success": true,
  "message": "Found 1 sessions",
  "data": [
    {
      "sessionId": "60d5f3f7e8a8d82e8c3e8e3f",
      "nameSession": "My First Session",
      "model": "gpt-3.5-turbo",
      "dateTime": "2025-08-23T10:00:00",
      "messageCount": 0
    }
  ]
}
```

### ChatAI Controller

#### Handle Chat

*   **POST** `/api/v1/chat/{sessionId}`

**Request Body:**

```json
{
  "prompt": "Hello, how are you?"
}
```

**Response Body:** (Server-Sent Events)

```
data: Hello! I am doing great.

data: How can I help you today?

```

### Verification Controller

#### Send Verification Code

*   **GET** `/api/v1/verify/send`

**Response Body:**

```json
{
  "success": true,
  "message": "Verification code sent to testuser@example.com",
  "data": null
}
```

#### Verify Code

*   **POST** `/api/v1/verify/check/{code}`

**Response Body:**

```json
{
  "success": true,
  "message": "Verification successful",
  "data": null
}
```

### Admin Controller

#### Make User Admin

*   **POST** `/api/v1/admins/make/{username}`

**Response Body:**

```json
{
  "userId": "60d5f3f7e8a8d82e8c3e8e3e",
  "userName": "testuser",
  "gmail": "testuser@example.com",
  "roles": [
    "USER",
    "ADMIN"
  ],
  "sessionCount": 0,
  "isVerified": false
}
```

#### Get All Users

*   **GET** `/api/v1/admins/users`

**Response Body:**

```json
[
  {
    "userId": "60d5f3f7e8a8d82e8c3e8e3e",
    "userName": "testuser",
    "gmail": "testuser@example.com",
    "roles": [
      "USER",
      "ADMIN"
    ],
    "sessionCount": 0,
    "isVerified": false
  }
]
```

## Configuration

The application uses `application.properties` for configuration. You can create different profiles for different environments (e.g., `application-dev.properties`, `application-prod.properties`).

The application supports two AI providers:

*   **OpenRouter:** To use OpenRouter, activate the `openrouter` profile and set the `spring.ai.openrouter.api-key` property.
*   **LMStudio:** To use LMStudio, activate the `lmstudio` profile and set the `spring.ai.lmstudio.url` property.

## Tools

The application includes a variety of tools that can be used by the AI model:

### Free Tools

*   **ArxivApiTools:** Search for papers on Arxiv.
*   **CodeforcesProblemSetTools:** Get problems from Codeforces.
*   **EmailTools:** Send emails.
*   **PlaywrightBrowserSearchTools:** Search the web using a headless browser (Playwright).
*   **PlaywrightWebScraperTools:** Scrape websites using a headless browser (Playwright).
*   **ReportTools:** Generate reports.
*   **SeleniumBrowserSearchTools:** Search the web using a headless browser (Selenium).
*   **SeleniumWebScraperTools:** Scrape websites using a headless browser (Selenium).
*   **ServerInfoTools:** Get server information.
*   **WeatherTools:** Get weather information.
*   **WebScraperTools:** Scrape websites.
*   **WebSearchTools:** Search the web.
*   **WikipediaTools:** Search for articles on Wikipedia.

### Paid Tools

*   **BraveSearchApiTools:** Search the web using the Brave Search API.
*   **ExchangeRateApiTools:** Get exchange rates.
*   **GNewsApiTools:** Get news from the GNews API.
*   **NewsDataApiTools:** Get news from the NewsData API.
*   **ScoutApiTools:** Search the web using the Scout API.
*   **SerpApiTools:** Search the web using the SerpApi.
*   **TempMailApiTools:** Get temporary email addresses.
*   **YouTubeSummarizerTools:** Summarize YouTube videos.