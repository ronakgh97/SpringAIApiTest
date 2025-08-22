package com.AI4Java.BackendAI.AI.tools.Free;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class WeatherTools {

    private static final Logger logger = LoggerFactory.getLogger(WeatherTools.class);

    // API Configuration
    private static final String GEOCODING_API_BASE = "https://geocoding-api.open-meteo.com/v1/search";
    private static final String WEATHER_API_BASE = "https://api.open-meteo.com/v1/forecast";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final int MAX_MEMORY_SIZE = 8192 * 8192;

    // Weather Configuration
    private static final int MIN_FORECAST_DAYS = 1;
    private static final int MAX_FORECAST_DAYS = 7;
    private static final int DEFAULT_FORECAST_DAYS = 3;
    private static final int COMPARISON_PREVIEW_LENGTH = 500;

    // Validation Limits
    private static final int MAX_CITY_NAME_LENGTH = 100;
    private static final int MAX_COUNTRY_CODE_LENGTH = 5;

    // Weather API Parameters
    private static final String CURRENT_WEATHER_PARAMS =
            "temperature_2m,relative_humidity_2m,apparent_temperature,precipitation," +
                    "weather_code,cloud_cover,pressure_msl,surface_pressure,wind_speed_10m," +
                    "wind_direction_10m";

    private static final String DAILY_WEATHER_PARAMS =
            "weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum," +
                    "wind_speed_10m_max,wind_direction_10m_dominant";

    // Wind direction mapping
    private static final String[] WIND_DIRECTIONS = {
            "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
            "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"
    };

    // Instance variables
    private WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicLong requestCount = new AtomicLong(0);

    @PostConstruct
    public void initialize() {
        logger.info("Initializing Weather Tools service");
        webClient = WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(MAX_MEMORY_SIZE))
                .defaultHeader("User-Agent", "AI4Java-WeatherTool/1.0")
                .build();
        logger.info("Weather Tools service initialized successfully");
    }

    @PreDestroy
    public void cleanup() {
        logger.info("Shutting down Weather Tools service");
        logger.info("Weather Tools cleaned up successfully. Total requests: {}", requestCount.get());
    }

    @Tool(name = "get_current_weather",
            description = "Gets current weather conditions for any city worldwide using Open-Meteo API. " +
                    "Provides temperature, humidity, wind, pressure, and precipitation data.")
    public String get_current_weather(
            @ToolParam(description = "City name") String city,
            @ToolParam(description = "2-letter country code (optional, e.g: us, jp, gb)", required = false) String country) {

        long requestId = requestCount.incrementAndGet();
        logger.debug("Starting current weather request #{} for city: '{}', country: '{}'", requestId, city, country);

        // Validate input
        WeatherRequest request = validateWeatherRequest(city, country);
        if (!request.isValid()) {
            logger.warn("Invalid weather request #{}: {}", requestId, request.getErrorMessage());
            return "❌ " + request.getErrorMessage();
        }

        try {
            LocationData location = getLocationCoordinates(request.getCity(), requestId);
            WeatherData currentWeather = getCurrentWeatherData(location, requestId);
            String result = formatCurrentWeather(currentWeather, location);

            logger.info("Current weather request #{} completed successfully for {}, {}",
                    requestId, location.getName(), location.getCountry());
            return result;

        } catch (WeatherApiException e) {
            logger.error("Weather request #{} failed: {}", requestId, e.getMessage());
            return "❌ " + e.getMessage();
        } catch (Exception e) {
            logger.error("Unexpected error during weather request #{}", requestId, e);
            return String.format("❌ Failed to get weather for '%s'. Please try again later.", city);
        }
    }

    @Tool(name = "get_weather_forecast",
            description = "Gets detailed weather forecast for any city worldwide. " +
                    "Supports 1-7 day forecasts with daily temperature ranges and conditions.")
    public String get_weather_forecast(
            @ToolParam(description = "City name") String city,
            @ToolParam(description = "2-letter country code (optional)", required = false) String country,
            @ToolParam(description = "Number of forecast days (1-7, default: 3)", required = false) String days) {

        long requestId = requestCount.incrementAndGet();
        logger.debug("Starting forecast request #{} for city: '{}', days: '{}'", requestId, city, days);

        // Validate input
        ForecastRequest request = validateForecastRequest(city, country, days);
        if (!request.isValid()) {
            logger.warn("Invalid forecast request #{}: {}", requestId, request.getErrorMessage());
            return "❌ " + request.getErrorMessage();
        }

        try {
            LocationData location = getLocationCoordinates(request.getCity(), requestId);
            ForecastData forecast = getForecastData(location, request.getDays(), requestId);
            String result = formatForecast(forecast, location, request.getDays());

            logger.info("Forecast request #{} completed successfully for {}, {} days",
                    requestId, location.getName(), request.getDays());
            return result;

        } catch (WeatherApiException e) {
            logger.error("Forecast request #{} failed: {}", requestId, e.getMessage());
            return "❌ " + e.getMessage();
        } catch (Exception e) {
            logger.error("Unexpected error during forecast request #{}", requestId, e);
            return String.format("❌ Failed to get forecast for '%s'.", city);
        }
    }

    @Tool(name = "compare_weather",
            description = "Compares current weather conditions between two cities. " +
                    "Shows side-by-side weather comparison with key metrics.")
    public String compare_weather(
            @ToolParam(description = "First city name") String city1,
            @ToolParam(description = "Second city name") String city2) {

        long requestId = requestCount.incrementAndGet();
        logger.debug("Starting weather comparison #{} between '{}' and '{}'", requestId, city1, city2);

        // Validate input
        ComparisonRequest request = validateComparisonRequest(city1, city2);
        if (!request.isValid()) {
            logger.warn("Invalid comparison request #{}: {}", requestId, request.getErrorMessage());
            return "❌ " + request.getErrorMessage();
        }

        try {
            String weather1 = get_current_weather(city1, null);
            String weather2 = get_current_weather(city2, null);

            if (weather1.startsWith("❌") || weather2.startsWith("❌")) {
                throw new WeatherApiException("Failed to get weather data for one or both cities");
            }

            String result = formatWeatherComparison(city1, city2, weather1, weather2);

            logger.info("Weather comparison #{} completed successfully between {} and {}",
                    requestId, city1, city2);
            return result;

        } catch (WeatherApiException e) {
            logger.error("Weather comparison #{} failed: {}", requestId, e.getMessage());
            return "❌ " + e.getMessage();
        } catch (Exception e) {
            logger.error("Unexpected error during weather comparison #{}", requestId, e);
            return "❌ Failed to compare weather between cities.";
        }
    }

    private LocationData getLocationCoordinates(String city, long requestId) throws WeatherApiException {
        try {
            String encodedCity = URLEncoder.encode(city, StandardCharsets.UTF_8);
            String geoUrl = String.format("%s?name=%s&count=1&language=en&format=json",
                    GEOCODING_API_BASE, encodedCity);

            logger.debug("Fetching coordinates for request #{}: {}", requestId, city);

            String response = webClient.get()
                    .uri(geoUrl)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(REQUEST_TIMEOUT)
                    .block();

            return parseLocationResponse(response, city);

        } catch (WebClientRequestException e) {
            throw new WeatherApiException("Network error while getting location data: " + e.getMessage());
        } catch (WebClientResponseException e) {
            throw new WeatherApiException("Location service error: HTTP " + e.getStatusCode());
        } catch (Exception e) {
            throw new WeatherApiException("Failed to get location coordinates for: " + city);
        }
    }

    private LocationData parseLocationResponse(String response, String originalCity) throws WeatherApiException, JsonProcessingException {
        try {
            JsonNode root = objectMapper.readTree(response);

            if (!root.has("results") || root.get("results").isEmpty()) {
                throw new WeatherApiException(String.format("City '%s' not found. Please check spelling and try again.", originalCity));
            }

            JsonNode location = root.get("results").get(0);
            double lat = location.get("latitude").asDouble();
            double lon = location.get("longitude").asDouble();
            String name = location.get("name").asText();
            String country = location.has("country") ? location.get("country").asText() : "";

            return new LocationData(name, country, lat, lon);

        } catch (Exception e) {
            if (e instanceof WeatherApiException) {
                throw e;
            }
            throw new WeatherApiException("Failed to parse location data");
        }
    }

    private WeatherData getCurrentWeatherData(LocationData location, long requestId) throws WeatherApiException {
        try {
            String weatherUrl = String.format(
                    "%s?latitude=%.6f&longitude=%.6f&current=%s&timezone=auto",
                    WEATHER_API_BASE, location.getLatitude(), location.getLongitude(), CURRENT_WEATHER_PARAMS
            );

            logger.debug("Fetching current weather for request #{}", requestId);

            String response = webClient.get()
                    .uri(weatherUrl)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(REQUEST_TIMEOUT)
                    .block();

            return parseCurrentWeatherResponse(response);

        } catch (WebClientRequestException e) {
            throw new WeatherApiException("Network error while getting weather data: " + e.getMessage());
        } catch (WebClientResponseException e) {
            throw new WeatherApiException("Weather service error: HTTP " + e.getStatusCode());
        } catch (Exception e) {
            throw new WeatherApiException("Failed to get current weather data");
        }
    }

    private WeatherData parseCurrentWeatherResponse(String response) throws WeatherApiException {
        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode current = root.get("current");

            return new WeatherData(
                    current.get("temperature_2m").asDouble(),
                    current.get("apparent_temperature").asDouble(),
                    current.get("relative_humidity_2m").asInt(),
                    current.get("wind_speed_10m").asDouble(),
                    current.get("wind_direction_10m").asInt(),
                    current.get("pressure_msl").asDouble(),
                    current.get("cloud_cover").asInt(),
                    current.get("precipitation").asDouble(),
                    current.get("weather_code").asInt()
            );

        } catch (Exception e) {
            throw new WeatherApiException("Failed to parse weather data");
        }
    }

    private ForecastData getForecastData(LocationData location, int days, long requestId) throws WeatherApiException {
        try {
            String forecastUrl = String.format(
                    "%s?latitude=%.6f&longitude=%.6f&daily=%s&timezone=auto&forecast_days=%d",
                    WEATHER_API_BASE, location.getLatitude(), location.getLongitude(),
                    DAILY_WEATHER_PARAMS, days
            );

            logger.debug("Fetching {}-day forecast for request #{}", days, requestId);

            String response = webClient.get()
                    .uri(forecastUrl)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(REQUEST_TIMEOUT)
                    .block();

            return parseForecastResponse(response, days);

        } catch (WebClientRequestException e) {
            throw new WeatherApiException("Network error while getting forecast data: " + e.getMessage());
        } catch (WebClientResponseException e) {
            throw new WeatherApiException("Forecast service error: HTTP " + e.getStatusCode());
        } catch (Exception e) {
            throw new WeatherApiException("Failed to get forecast data");
        }
    }

    private ForecastData parseForecastResponse(String response, int days) throws WeatherApiException {
        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode daily = root.get("daily");

            return new ForecastData(
                    daily.get("time"),
                    daily.get("temperature_2m_max"),
                    daily.get("temperature_2m_min"),
                    daily.get("weather_code"),
                    daily.get("precipitation_sum"),
                    daily.get("wind_speed_10m_max"),
                    days
            );

        } catch (Exception e) {
            throw new WeatherApiException("Failed to parse forecast data");
        }
    }

    // Validation methods
    private WeatherRequest validateWeatherRequest(String city, String country) {
        if (city == null || city.trim().isEmpty()) {
            return WeatherRequest.invalid("City name cannot be empty.");
        }

        String trimmedCity = city.trim();
        if (trimmedCity.length() > MAX_CITY_NAME_LENGTH) {
            return WeatherRequest.invalid("City name is too long (maximum " + MAX_CITY_NAME_LENGTH + " characters).");
        }

        String trimmedCountry = null;
        if (country != null && !country.trim().isEmpty()) {
            trimmedCountry = country.trim();
            if (trimmedCountry.length() > MAX_COUNTRY_CODE_LENGTH) {
                return WeatherRequest.invalid("Country code is too long (maximum " + MAX_COUNTRY_CODE_LENGTH + " characters).");
            }
        }

        return WeatherRequest.valid(trimmedCity, trimmedCountry);
    }

    private ForecastRequest validateForecastRequest(String city, String country, String days) {
        WeatherRequest baseRequest = validateWeatherRequest(city, country);
        if (!baseRequest.isValid()) {
            return ForecastRequest.invalid(baseRequest.getErrorMessage());
        }

        int forecastDays = DEFAULT_FORECAST_DAYS;
        if (days != null && !days.trim().isEmpty()) {
            try {
                int parsedDays = Integer.parseInt(days.trim());
                if (parsedDays < MIN_FORECAST_DAYS || parsedDays > MAX_FORECAST_DAYS) {
                    return ForecastRequest.invalid(String.format("Forecast days must be between %d and %d.",
                            MIN_FORECAST_DAYS, MAX_FORECAST_DAYS));
                }
                forecastDays = parsedDays;
            } catch (NumberFormatException e) {
                return ForecastRequest.invalid("Invalid number of days. Please provide a number between 1 and 7.");
            }
        }

        return ForecastRequest.valid(baseRequest.getCity(), baseRequest.getCountry(), forecastDays);
    }

    private ComparisonRequest validateComparisonRequest(String city1, String city2) {
        if (city1 == null || city1.trim().isEmpty()) {
            return ComparisonRequest.invalid("First city name cannot be empty.");
        }
        if (city2 == null || city2.trim().isEmpty()) {
            return ComparisonRequest.invalid("Second city name cannot be empty.");
        }

        String trimmedCity1 = city1.trim();
        String trimmedCity2 = city2.trim();

        if (trimmedCity1.length() > MAX_CITY_NAME_LENGTH || trimmedCity2.length() > MAX_CITY_NAME_LENGTH) {
            return ComparisonRequest.invalid("City names are too long (maximum " + MAX_CITY_NAME_LENGTH + " characters).");
        }

        if (trimmedCity1.equalsIgnoreCase(trimmedCity2)) {
            return ComparisonRequest.invalid("Cannot compare weather for the same city.");
        }

        return ComparisonRequest.valid(trimmedCity1, trimmedCity2);
    }

    // Formatting methods
    private String formatCurrentWeather(WeatherData weather, LocationData location) {
        String condition = getWeatherCondition(weather.getWeatherCode());
        String weatherEmoji = getWeatherEmojiFromCode(weather.getWeatherCode());
        String windDir = getWindDirection(weather.getWindDirection());

        return String.format(
                "🌍 **Weather in %s%s**\n\n" +
                        "%s **Current Conditions**\n" +
                        "🌡️ **Temperature:** %.1f°C (feels like %.1f°C)\n" +
                        "☁️ **Condition:** %s\n" +
                        "💧 **Humidity:** %d%%\n" +
                        "🔽 **Pressure:** %.0f hPa\n" +
                        "💨 **Wind:** %.1f km/h %s\n" +
                        "☁️ **Cloud Cover:** %d%%\n" +
                        "%s" +
                        "🕐 **Updated:** Just now",
                location.getName(),
                location.getCountry().isEmpty() ? "" : ", " + location.getCountry(),
                weatherEmoji, weather.getTemperature(), weather.getFeelsLike(),
                condition, weather.getHumidity(), weather.getPressure(),
                weather.getWindSpeed(), windDir, weather.getCloudCover(),
                weather.getPrecipitation() > 0 ?
                        String.format("🌧️ **Precipitation:** %.1f mm\n", weather.getPrecipitation()) : ""
        );
    }

    private String formatForecast(ForecastData forecast, LocationData location, int days) {
        StringBuilder result = new StringBuilder();
        result.append(String.format("📅 **%d-Day Weather Forecast for %s**\n\n", days, location.getName()));

        for (int i = 0; i < Math.min(days, forecast.getDates().size()); i++) {
            String date = forecast.getDates().get(i).asText();
            double maxTemp = forecast.getMaxTemps().get(i).asDouble();
            double minTemp = forecast.getMinTemps().get(i).asDouble();
            int weatherCode = forecast.getWeatherCodes().get(i).asInt();
            double precip = forecast.getPrecipitation().get(i).asDouble();
            double windSpeed = forecast.getWindSpeeds().get(i).asDouble();

            String condition = getWeatherCondition(weatherCode);
            String weatherEmoji = getWeatherEmojiFromCode(weatherCode);

            LocalDateTime dateTime = LocalDateTime.parse(date + "T00:00:00");
            String dayName = dateTime.format(DateTimeFormatter.ofPattern("EEEE, MMM dd"));

            result.append(String.format(
                    "**%s**\n" +
                            "%s %s\n" +
                            "🌡️ %.1f°C - %.1f°C\n" +
                            "💨 %.1f km/h wind\n" +
                            "%s\n\n",
                    dayName, weatherEmoji, condition, minTemp, maxTemp, windSpeed,
                    precip > 0 ? String.format("🌧️ %.1f mm precipitation", precip) : "☀️ No precipitation"
            ));
        }

        return result.toString();
    }

    private String formatWeatherComparison(String city1, String city2, String weather1, String weather2) {
        return String.format(
                "🆚 **Weather Comparison**\n\n" +
                        "**%s:**\n%s\n\n" +
                        "**vs**\n\n" +
                        "**%s:**\n%s\n\n" +
                        "💡 Use individual city weather commands for detailed comparisons.",
                city1.toUpperCase(),
                weather1.substring(0, Math.min(COMPARISON_PREVIEW_LENGTH, weather1.length())) + "...",
                city2.toUpperCase(),
                weather2.substring(0, Math.min(COMPARISON_PREVIEW_LENGTH, weather2.length())) + "..."
        );
    }

    // Weather condition and emoji mapping
    private static String getWeatherCondition(int code) {
        return switch (code) {
            case 0 -> "Clear sky";
            case 1 -> "Mainly clear";
            case 2 -> "Partly cloudy";
            case 3 -> "Overcast";
            case 45, 48 -> "Foggy";
            case 51, 53, 55 -> "Light drizzle";
            case 56, 57 -> "Freezing drizzle";
            case 61, 63, 65 -> "Rain";
            case 66, 67 -> "Freezing rain";
            case 71, 73, 75 -> "Snow";
            case 77 -> "Snow grains";
            case 80, 81, 82 -> "Rain showers";
            case 85, 86 -> "Snow showers";
            case 95 -> "Thunderstorm";
            case 96, 99 -> "Thunderstorm with hail";
            default -> "Unknown condition";
        };
    }

    private static String getWeatherEmojiFromCode(int code) {
        return switch (code) {
            case 0, 1 -> "☀️";
            case 2 -> "⛅";
            case 3 -> "☁️";
            case 45, 48 -> "🌫️";
            case 51, 53, 55, 56, 57 -> "🌦️";
            case 61, 63, 65, 66, 67 -> "🌧️";
            case 71, 73, 75, 77, 85, 86 -> "❄️";
            case 80, 81, 82 -> "🌦️";
            case 95, 96, 99 -> "⛈️";
            default -> "🌤️";
        };
    }

    private static String getWindDirection(int degrees) {
        int index = (int) Math.round(degrees / 22.5) % 16;
        return WIND_DIRECTIONS[index];
    }

    // Helper classes
    private static class WeatherRequest {
        private final boolean valid;
        private final String errorMessage;
        private final String city;
        private final String country;

        private WeatherRequest(boolean valid, String errorMessage, String city, String country) {
            this.valid = valid;
            this.errorMessage = errorMessage;
            this.city = city;
            this.country = country;
        }

        static WeatherRequest valid(String city, String country) {
            return new WeatherRequest(true, null, city, country);
        }

        static WeatherRequest invalid(String errorMessage) {
            return new WeatherRequest(false, errorMessage, null, null);
        }

        boolean isValid() { return valid; }
        String getErrorMessage() { return errorMessage; }
        String getCity() { return city; }
        String getCountry() { return country; }
    }

    private static class ForecastRequest {
        private final boolean valid;
        private final String errorMessage;
        private final String city;
        private final String country;
        private final int days;

        private ForecastRequest(boolean valid, String errorMessage, String city, String country, int days) {
            this.valid = valid;
            this.errorMessage = errorMessage;
            this.city = city;
            this.country = country;
            this.days = days;
        }

        static ForecastRequest valid(String city, String country, int days) {
            return new ForecastRequest(true, null, city, country, days);
        }

        static ForecastRequest invalid(String errorMessage) {
            return new ForecastRequest(false, errorMessage, null, null, 0);
        }

        boolean isValid() { return valid; }
        String getErrorMessage() { return errorMessage; }
        String getCity() { return city; }
        String getCountry() { return country; }
        int getDays() { return days; }
    }

    private static class ComparisonRequest {
        private final boolean valid;
        private final String errorMessage;
        private final String city1;
        private final String city2;

        private ComparisonRequest(boolean valid, String errorMessage, String city1, String city2) {
            this.valid = valid;
            this.errorMessage = errorMessage;
            this.city1 = city1;
            this.city2 = city2;
        }

        static ComparisonRequest valid(String city1, String city2) {
            return new ComparisonRequest(true, null, city1, city2);
        }

        static ComparisonRequest invalid(String errorMessage) {
            return new ComparisonRequest(false, errorMessage, null, null);
        }

        boolean isValid() { return valid; }
        String getErrorMessage() { return errorMessage; }
        String getCity1() { return city1; }
        String getCity2() { return city2; }
    }

    private static class LocationData {
        private final String name;
        private final String country;
        private final double latitude;
        private final double longitude;

        LocationData(String name, String country, double latitude, double longitude) {
            this.name = name;
            this.country = country;
            this.latitude = latitude;
            this.longitude = longitude;
        }

        String getName() { return name; }
        String getCountry() { return country; }
        double getLatitude() { return latitude; }
        double getLongitude() { return longitude; }
    }

    private static class WeatherData {
        private final double temperature;
        private final double feelsLike;
        private final int humidity;
        private final double windSpeed;
        private final int windDirection;
        private final double pressure;
        private final int cloudCover;
        private final double precipitation;
        private final int weatherCode;

        WeatherData(double temperature, double feelsLike, int humidity, double windSpeed,
                    int windDirection, double pressure, int cloudCover, double precipitation, int weatherCode) {
            this.temperature = temperature;
            this.feelsLike = feelsLike;
            this.humidity = humidity;
            this.windSpeed = windSpeed;
            this.windDirection = windDirection;
            this.pressure = pressure;
            this.cloudCover = cloudCover;
            this.precipitation = precipitation;
            this.weatherCode = weatherCode;
        }

        double getTemperature() { return temperature; }
        double getFeelsLike() { return feelsLike; }
        int getHumidity() { return humidity; }
        double getWindSpeed() { return windSpeed; }
        int getWindDirection() { return windDirection; }
        double getPressure() { return pressure; }
        int getCloudCover() { return cloudCover; }
        double getPrecipitation() { return precipitation; }
        int getWeatherCode() { return weatherCode; }
    }

    private static class ForecastData {
        private final JsonNode dates;
        private final JsonNode maxTemps;
        private final JsonNode minTemps;
        private final JsonNode weatherCodes;
        private final JsonNode precipitation;
        private final JsonNode windSpeeds;
        private final int days;

        ForecastData(JsonNode dates, JsonNode maxTemps, JsonNode minTemps, JsonNode weatherCodes,
                     JsonNode precipitation, JsonNode windSpeeds, int days) {
            this.dates = dates;
            this.maxTemps = maxTemps;
            this.minTemps = minTemps;
            this.weatherCodes = weatherCodes;
            this.precipitation = precipitation;
            this.windSpeeds = windSpeeds;
            this.days = days;
        }

        JsonNode getDates() { return dates; }
        JsonNode getMaxTemps() { return maxTemps; }
        JsonNode getMinTemps() { return minTemps; }
        JsonNode getWeatherCodes() { return weatherCodes; }
        JsonNode getPrecipitation() { return precipitation; }
        JsonNode getWindSpeeds() { return windSpeeds; }
        int getDays() { return days; }
    }

    // Custom exception
    private static class WeatherApiException extends Exception {
        WeatherApiException(String message) {
            super(message);
        }
    }
}


