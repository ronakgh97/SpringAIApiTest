package com.AI4Java.BackendAI.AI.tools;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.i18n.LocaleContextHolder;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class BasicToolsTest {

    private BasicTools basicTools;

    @BeforeEach
    void setUp() {
        basicTools = new BasicTools();
    }

    @Test
    void getCurrentDateTime_ShouldReturnCurrentDateTime() {
        // When
        String result = basicTools.getCurrentDateTime();

        // Then
        assertThat(result).isNotNull();
        assertThat(result).isNotEmpty();
        
        // Should be in ISO format with timezone
        assertThat(result).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.*");
    }

    @Test
    void getCurrentDateTime_ShouldIncludeTimezone() {
        // When
        String result = basicTools.getCurrentDateTime();

        // Then
        assertThat(result).isNotNull();
        // Should contain timezone information (+ or - or Z for UTC)
        assertThat(result).matches(".*[\\+\\-Z].*");
    }

    @Test
    void getCurrentDateTime_ShouldBeRecentTime() {
        // Given
        LocalDateTime before = LocalDateTime.now().minusMinutes(1);
        LocalDateTime after = LocalDateTime.now().plusMinutes(1);

        // When
        String result = basicTools.getCurrentDateTime();

        // Then
        assertThat(result).isNotNull();
        
        // Parse the result to verify it's within the expected time range
        // The result includes timezone, so we need to handle that
        String timestampPart = result.substring(0, 19); // Get just the datetime part
        LocalDateTime resultTime = LocalDateTime.parse(timestampPart, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        
        assertThat(resultTime).isAfter(before);
        assertThat(resultTime).isBefore(after);
    }

    @Test
    void getCurrentDateTime_WithDifferentTimezones_ShouldRespectLocale() {
        // Test with different timezones
        TimeZone[] timezones = {
            TimeZone.getTimeZone("UTC"),
            TimeZone.getTimeZone("America/New_York"),
            TimeZone.getTimeZone("Europe/London"),
            TimeZone.getTimeZone("Asia/Tokyo")
        };

        for (TimeZone timezone : timezones) {
            // Given
            LocaleContextHolder.setTimeZone(timezone);

            // When
            String result = basicTools.getCurrentDateTime();

            // Then
            assertThat(result).isNotNull();
            assertThat(result).isNotEmpty();
            
            // Should contain timezone information
            assertThat(result).matches(".*[\\+\\-Z].*");
        }

        // Reset to default
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void getCurrentDateTime_MultipleCallsInSequence_ShouldReturnProgressive() throws InterruptedException {
        // When - Make multiple calls with small delays
        String first = basicTools.getCurrentDateTime();
        Thread.sleep(100); // Small delay
        String second = basicTools.getCurrentDateTime();
        Thread.sleep(100); // Small delay
        String third = basicTools.getCurrentDateTime();

        // Then
        assertThat(first).isNotNull();
        assertThat(second).isNotNull();
        assertThat(third).isNotNull();

        // All should be different (or at least not identical due to millisecond precision)
        assertThat(first).isNotEqualTo(second);
        assertThat(second).isNotEqualTo(third);
        assertThat(first).isNotEqualTo(third);
    }

    @Test
    void getCurrentDateTime_ShouldHandleSystemTimezoneChanges() {
        // Given - Store original timezone
        TimeZone originalTimezone = TimeZone.getDefault();
        
        try {
            // Test with UTC
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            LocaleContextHolder.setTimeZone(TimeZone.getTimeZone("UTC"));
            String utcResult = basicTools.getCurrentDateTime();

            // Test with different timezone
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
            LocaleContextHolder.setTimeZone(TimeZone.getTimeZone("America/Los_Angeles"));
            String pstResult = basicTools.getCurrentDateTime();

            // Then
            assertThat(utcResult).isNotNull();
            assertThat(pstResult).isNotNull();
            assertThat(utcResult).isNotEqualTo(pstResult);

        } finally {
            // Reset to original timezone
            TimeZone.setDefault(originalTimezone);
            LocaleContextHolder.resetLocaleContext();
        }
    }

    @Test
    void getCurrentDateTime_ShouldReturnValidISO8601Format() {
        // When
        String result = basicTools.getCurrentDateTime();

        // Then
        assertThat(result).isNotNull();
        
        // Should match ISO 8601 format with timezone
        // Format: YYYY-MM-DDTHH:mm:ss.nnnnnnnnn+HH:MM or similar
        assertThat(result).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.*[\\+\\-Z].*");
    }

    @Test
    void getCurrentDateTime_PerformanceTest_ShouldBeQuick() {
        // Given
        int iterations = 1000;
        long startTime = System.currentTimeMillis();

        // When - Call multiple times to test performance
        for (int i = 0; i < iterations; i++) {
            String result = basicTools.getCurrentDateTime();
            assertThat(result).isNotNull();
        }

        // Then
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        // Should complete 1000 calls in reasonable time (less than 1 second)
        assertThat(duration).isLessThan(1000);
    }

    @Test
    void getCurrentDateTime_ConcurrentCalls_ShouldHandleCorrectly() throws InterruptedException {
        // Given
        int threadCount = 10;
        Thread[] threads = new Thread[threadCount];
        String[] results = new String[threadCount];

        // When - Make concurrent calls
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                results[index] = basicTools.getCurrentDateTime();
            });
            threads[i].start();
        }

        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join();
        }

        // Then
        for (int i = 0; i < threadCount; i++) {
            assertThat(results[i]).isNotNull();
            assertThat(results[i]).isNotEmpty();
            assertThat(results[i]).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.*");
        }
    }

    @Test
    void getCurrentDateTime_WithDifferentLocales_ShouldWork() {
        // Test with different locales
        Locale[] locales = {
            Locale.US,
            Locale.UK,
            Locale.JAPAN,
            Locale.GERMANY,
            Locale.FRANCE
        };

        for (Locale locale : locales) {
            // Given
            LocaleContextHolder.setLocale(locale);

            // When
            String result = basicTools.getCurrentDateTime();

            // Then
            assertThat(result).isNotNull();
            assertThat(result).isNotEmpty();
            assertThat(result).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.*");
        }

        // Reset
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void getCurrentDateTime_ShouldHandleTimezoneTransitions() {
        // This test is more theoretical as we can't easily trigger DST transitions
        // But we can test that the method works during different times of year
        
        // Given various timezone scenarios
        String[] timezoneIds = {
            "America/New_York",    // Has DST
            "Europe/London",       // Has DST
            "Australia/Sydney",    // Has DST (opposite hemisphere)
            "Asia/Tokyo",          // No DST
            "UTC"                  // No DST
        };

        for (String timezoneId : timezoneIds) {
            // Given
            LocaleContextHolder.setTimeZone(TimeZone.getTimeZone(timezoneId));

            // When
            String result = basicTools.getCurrentDateTime();

            // Then
            assertThat(result).isNotNull();
            assertThat(result).isNotEmpty();
            assertThat(result).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.*");
        }

        // Reset
        LocaleContextHolder.resetLocaleContext();
    }
}
