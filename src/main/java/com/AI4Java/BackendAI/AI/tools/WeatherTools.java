package com.AI4Java.BackendAI.AI.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class WeatherTools {
    
    private static final Logger log = LoggerFactory.getLogger(WeatherTools.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WebClient webClient = WebClient.builder()
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(1024 * 1024))
            .build();
    
    @Tool(name = "get_current_weather", description = "Gets current weather conditions for any city worldwide. " +
            "Parameters: city (required), country (optional)")
    public String get_current_weather(String city, String country) {
        try {
            log.info("Getting current weather for city: {}, country: {}", city, country);
            
            // Step 1: Get coordinates from city name using Open-Meteo Geocoding (free)
            String geoUrl = String.format(
                "https://geocoding-api.open-meteo.com/v1/search?name=%s&count=1&language=en&format=json",
                URLEncoder.encode(city, StandardCharsets.UTF_8)
            );
            
            String geoResponse = webClient.get()
                    .uri(geoUrl)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();
            
            JsonNode geoRoot = objectMapper.readTree(geoResponse);
            if (!geoRoot.has("results") || geoRoot.get("results").isEmpty()) {
                return String.format("❌ City '%s' not found. Please check spelling and try again.", city);
            }
            
            JsonNode location = geoRoot.get("results").get(0);
            double lat = location.get("latitude").asDouble();
            double lon = location.get("longitude").asDouble();
            String foundCity = location.get("name").asText();
            String foundCountry = location.has("country") ? location.get("country").asText() : "";
            
            // Step 2: Get weather data using coordinates (completely free)
            String weatherUrl = String.format(
                "https://api.open-meteo.com/v1/forecast?latitude=%.6f&longitude=%.6f&current=" +
                "temperature_2m,relative_humidity_2m,apparent_temperature,precipitation," +
                "weather_code,cloud_cover,pressure_msl,surface_pressure,wind_speed_10m," +
                "wind_direction_10m&timezone=auto",
                lat, lon
            );
            
            String weatherResponse = webClient.get()
                    .uri(weatherUrl)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();
            
            return parseOpenMeteoCurrentWeather(weatherResponse, foundCity, foundCountry);
            
        } catch (Exception e) {
            log.error("Failed to get current weather for city '{}': {}", city, e.getMessage());
            return String.format("❌ Failed to get weather for '%s'. Please try again later.", city);
        }
    }
    
    @Tool(name = "get_weather_forecast", description = "Gets weather forecast for any city worldwide. " +
            "Parameters: city (required), country (optional), days (optional, 1-7 days, default 3)")
    public String get_weather_forecast(String city, String country, String days) {
        try {
            int forecastDays = Math.min(7, Math.max(1, days != null ? Integer.parseInt(days) : 3));
            
            log.info("Getting {}-day forecast for city: {}", forecastDays, city);
            
            // Get coordinates
            String geoUrl = String.format(
                "https://geocoding-api.open-meteo.com/v1/search?name=%s&count=1&language=en&format=json",
                URLEncoder.encode(city, StandardCharsets.UTF_8)
            );
            
            String geoResponse = webClient.get()
                    .uri(geoUrl)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();
            
            JsonNode geoRoot = objectMapper.readTree(geoResponse);
            if (!geoRoot.has("results") || geoRoot.get("results").isEmpty()) {
                return String.format("❌ City '%s' not found for forecast.", city);
            }
            
            JsonNode location = geoRoot.get("results").get(0);
            double lat = location.get("latitude").asDouble();
            double lon = location.get("longitude").asDouble();
            String foundCity = location.get("name").asText();
            
            // Get forecast data
            String forecastUrl = String.format(
                "https://api.open-meteo.com/v1/forecast?latitude=%.6f&longitude=%.6f&daily=" +
                "weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum," +
                "wind_speed_10m_max,wind_direction_10m_dominant&timezone=auto&forecast_days=%d",
                lat, lon, forecastDays
            );
            
            String forecastResponse = webClient.get()
                    .uri(forecastUrl)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();
            
            return parseOpenMeteoForecast(forecastResponse, foundCity, forecastDays);
            
        } catch (Exception e) {
            log.error("Failed to get weather forecast: {}", e.getMessage());
            return String.format("❌ Failed to get forecast for '%s'.", city);
        }
    }
    
    @Tool(name = "compare_weather", description = "Compares current weather between two cities. " +
            "Parameters: city1 (required), city2 (required)")
    public String compare_weather(String city1, String city2) {
        try {
            log.info("Comparing weather between {} and {}", city1, city2);
            
            String weather1 = get_current_weather(city1, null);
            String weather2 = get_current_weather(city2, null);
            
            if (weather1.startsWith("❌") || weather2.startsWith("❌")) {
                return "❌ Failed to compare weather. One or both cities not found.";
            }
            
            // For a simple comparison, we'll extract key data and compare
            // In a more sophisticated version, you could parse the responses and create a detailed comparison
            return String.format(
                "🆚 **Weather Comparison**\n\n" +
                "**%s:**\n%s\n\n" +
                "**vs**\n\n" +
                "**%s:**\n%s\n\n" +
                "💡 Use individual city weather commands for detailed comparisons.",
                city1.toUpperCase(), weather1.substring(0, Math.min(200, weather1.length())) + "...",
                city2.toUpperCase(), weather2.substring(0, Math.min(200, weather2.length())) + "..."
            );
            
        } catch (Exception e) {
            log.error("Failed to compare weather: {}", e.getMessage());
            return "❌ Failed to compare weather between cities.";
        }
    }
    
    // Parsing methods for Open-Meteo API
    private String parseOpenMeteoCurrentWeather(String jsonResponse, String city, String country) {
        try {
            JsonNode root = objectMapper.readTree(jsonResponse);
            JsonNode current = root.get("current");
            
            double temp = current.get("temperature_2m").asDouble();
            double feelsLike = current.get("apparent_temperature").asDouble();
            int humidity = current.get("relative_humidity_2m").asInt();
            double windSpeed = current.get("wind_speed_10m").asDouble();
            int windDirection = current.get("wind_direction_10m").asInt();
            double pressure = current.get("pressure_msl").asDouble();
            int cloudCover = current.get("cloud_cover").asInt();
            double precipitation = current.get("precipitation").asDouble();
            int weatherCode = current.get("weather_code").asInt();
            
            String condition = getWeatherCondition(weatherCode);
            String weatherEmoji = getWeatherEmojiFromCode(weatherCode);
            String windDir = getWindDirection(windDirection);
            
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
                city, country.isEmpty() ? "" : ", " + country,
                weatherEmoji, temp, feelsLike, condition, humidity, 
                pressure, windSpeed, windDir, cloudCover,
                precipitation > 0 ? String.format("🌧️ **Precipitation:** %.1f mm\n", precipitation) : ""
            );
            
        } catch (Exception e) {
            log.error("Failed to parse Open-Meteo weather response: {}", e.getMessage());
            return "❌ Received weather data but couldn't parse it properly.";
        }
    }
    
    private String parseOpenMeteoForecast(String jsonResponse, String city, int days) {
        try {
            JsonNode root = objectMapper.readTree(jsonResponse);
            JsonNode daily = root.get("daily");
            
            StringBuilder result = new StringBuilder();
            result.append(String.format("📅 **%d-Day Weather Forecast for %s**\n\n", days, city));
            
            JsonNode dates = daily.get("time");
            JsonNode maxTemps = daily.get("temperature_2m_max");
            JsonNode minTemps = daily.get("temperature_2m_min");
            JsonNode weatherCodes = daily.get("weather_code");
            JsonNode precipitation = daily.get("precipitation_sum");
            JsonNode windSpeeds = daily.get("wind_speed_10m_max");
            
            for (int i = 0; i < Math.min(days, dates.size()); i++) {
                String date = dates.get(i).asText();
                double maxTemp = maxTemps.get(i).asDouble();
                double minTemp = minTemps.get(i).asDouble();
                int weatherCode = weatherCodes.get(i).asInt();
                double precip = precipitation.get(i).asDouble();
                double windSpeed = windSpeeds.get(i).asDouble();
                
                String condition = getWeatherCondition(weatherCode);
                String weatherEmoji = getWeatherEmojiFromCode(weatherCode);
                
                LocalDateTime dateTime = LocalDateTime.parse(date + "T00:00:00");
                String dayName = dateTime.format(DateTimeFormatter.ofPattern("EEEE, MMM dd"));
                
                result.append(String.format(
                    "**%s**\n" +
                    "%s %s\n" +
                    "🌡️ %.1f°C - %.1f°C\n" +
                    "💨 %.1f km/h wind\n" +
                    "%s\n",
                    dayName, weatherEmoji, condition, minTemp, maxTemp, windSpeed,
                    precip > 0 ? String.format("🌧️ %.1f mm precipitation", precip) : "☀️ No precipitation"
                ));
            }
            
            return result.toString();
            
        } catch (Exception e) {
            log.error("Failed to parse forecast: {}", e.getMessage());
            return "❌ Received forecast data but couldn't parse it properly.";
        }
    }
    
    // Weather condition mapping for Open-Meteo weather codes
    private String getWeatherCondition(int code) {
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
    
    private String getWeatherEmojiFromCode(int code) {
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
    
    private String getWindDirection(int degrees) {
        String[] directions = {"N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", 
                              "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"};
        int index = (int) Math.round(degrees / 22.5) % 16;
        return directions[index];
    }
}

