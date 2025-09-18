package org.kwcrawler;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CourtCodeValidatorTest {
    @Test
    public void shouldReturnTrueForValidDepartment() {
        // assuming "HR" is a valid court in departments.txt
        assertThat(CourtCodeValidator.isValidCourtCode("GL1G")).isTrue();
    }

    @Test
    public void shouldReturnFalseForInvalidDepartment() {
        assertThat(CourtCodeValidator.isValidCourtCode("INVALID")).isFalse();
    }
}