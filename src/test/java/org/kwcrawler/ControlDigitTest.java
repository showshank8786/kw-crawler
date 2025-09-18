package org.kwcrawler;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

public class ControlDigitTest {
    @ParameterizedTest
    @CsvSource(delimiter = '/', textBlock = """
        GL1G/00052948/3
        GL1G/00140264/8
        GL1G/00043002/4
        GL1G/00110551/8
        GL1G/00112446/3
        GL1G/00112947/5
        GL1G/00113511/7
        GL1G/00006766/6""")
    public void shouldCalculateControlDigit(String courtCode, String ledgerNumber, byte expectedControlDigit) {
        // when
        var controlDigit = ControlDigit.calculate(courtCode, ledgerNumber);

        // then
        assertThat(controlDigit).isEqualTo(expectedControlDigit);
    }
}
