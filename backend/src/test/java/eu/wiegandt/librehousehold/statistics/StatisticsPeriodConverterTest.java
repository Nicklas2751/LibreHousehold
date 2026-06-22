package eu.wiegandt.librehousehold.statistics;

import eu.wiegandt.librehousehold.model.StatisticsPeriod;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalDate;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class StatisticsPeriodConverterTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 22);

    @ParameterizedTest(name = "{0}")
    @MethodSource("periodRanges")
    void convert_period_returnsExpectedDateRange(StatisticsPeriod period, StatisticsPeriodConverter.DateRange expected) {
        // when
        var result = StatisticsPeriodConverter.convert(period, TODAY);

        // then
        assertThat(result).usingRecursiveComparison().isEqualTo(expected);
    }


    static Stream<Arguments> periodRanges() {
        return Stream.of(
                Arguments.of(StatisticsPeriod.LAST_7_DAYS,
                        new StatisticsPeriodConverter.DateRange(LocalDate.of(2026, 6, 16), TODAY)),
                Arguments.of(StatisticsPeriod.LAST_14_DAYS,
                        new StatisticsPeriodConverter.DateRange(LocalDate.of(2026, 6, 9), TODAY)),
                Arguments.of(StatisticsPeriod.THIS_MONTH,
                        new StatisticsPeriodConverter.DateRange(LocalDate.of(2026, 6, 1), TODAY)),
                Arguments.of(StatisticsPeriod.LAST_3_MONTHS,
                        new StatisticsPeriodConverter.DateRange(LocalDate.of(2026, 4, 1), TODAY)),
                Arguments.of(StatisticsPeriod.LAST_6_MONTHS,
                        new StatisticsPeriodConverter.DateRange(LocalDate.of(2026, 1, 1), TODAY)),
                Arguments.of(StatisticsPeriod.THIS_YEAR,
                        new StatisticsPeriodConverter.DateRange(LocalDate.of(2026, 1, 1), TODAY)),
                Arguments.of(StatisticsPeriod.LAST_YEAR,
                        new StatisticsPeriodConverter.DateRange(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31)))
        );
    }
}
