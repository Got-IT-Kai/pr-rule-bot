package com.code.platform.correlation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdTest {

    @Test
    void generate_returnsValidUUID() {
        // When
        String correlationId = CorrelationId.generate();

        // Then
        assertThat(correlationId).isNotNull();
        assertThat(correlationId).isNotBlank();

        // Verify it's a valid UUID by parsing
        UUID uuid = UUID.fromString(correlationId);
        assertThat(uuid).isNotNull();
    }

    @Test
    void generate_returnsUniqueValues() {
        // Given
        Set<String> generatedIds = new HashSet<>();
        int iterations = 1000;

        // When
        for (int i = 0; i < iterations; i++) {
            generatedIds.add(CorrelationId.generate());
        }

        // Then - all generated IDs should be unique
        assertThat(generatedIds).hasSize(iterations);
    }

    @Test
    void generate_followsUUIDv4Format() {
        // When
        String correlationId = CorrelationId.generate();

        // Then
        // UUID v4 format: xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx
        String[] parts = correlationId.split("-");
        assertThat(parts).hasSize(5);
        assertThat(parts[0]).hasSize(8);
        assertThat(parts[1]).hasSize(4);
        assertThat(parts[2]).hasSize(4).startsWith("4"); // Version 4
        assertThat(parts[3]).hasSize(4);
        assertThat(parts[4]).hasSize(12);
    }

    @Test
    void isValid_validUUID_returnsTrue() {
        // Given
        String validUuid = "123e4567-e89b-42d3-a456-556642440000";

        // When
        boolean result = CorrelationId.isValid(validUuid);

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void isValid_generatedCorrelationId_returnsTrue() {
        // Given
        String correlationId = CorrelationId.generate();

        // When
        boolean result = CorrelationId.isValid(correlationId);

        // Then
        assertThat(result).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   ", "\t", "\n"})
    void isValid_nullOrBlank_returnsFalse(String input) {
        // When
        boolean result = CorrelationId.isValid(input);

        // Then
        assertThat(result).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "not-a-uuid",
        "12345",
        "123e4567-e89b-42d3-a456",  // Too short
        "123e4567-e89b-42d3-a456-556642440000-extra", // Too long
        "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx", // Invalid hex
        "123e4567e89b42d3a456556642440000", // Missing hyphens
        "123e4567-e89b-42d3-a456-55664244000g", // Invalid hex character
    })
    void isValid_invalidFormat_returnsFalse(String invalidUuid) {
        // When
        boolean result = CorrelationId.isValid(invalidUuid);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void isValid_uppercaseUUID_returnsTrue() {
        // Given - UUID with uppercase letters
        String uppercaseUuid = "123E4567-E89B-42D3-A456-556642440000";

        // When
        boolean result = CorrelationId.isValid(uppercaseUuid);

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void isValid_mixedCaseUUID_returnsTrue() {
        // Given
        String mixedCaseUuid = "123e4567-E89B-42d3-A456-556642440000";

        // When
        boolean result = CorrelationId.isValid(mixedCaseUuid);

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void generate_performanceTest_completesQuickly() {
        // When
        long startTime = System.nanoTime();

        // Keep iterations modest so CI variance doesn't fail the build.
        for (int i = 0; i < 5000; i++) {
            CorrelationId.generate();
        }

        long endTime = System.nanoTime();
        long durationMs = (endTime - startTime) / 1_000_000;

        // Then - should complete in under 200ms for 5k iterations (CI-safe threshold)
        assertThat(durationMs).isLessThan(200);
    }
}
