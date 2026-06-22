package eu.wiegandt.librehousehold.statistics;

import eu.wiegandt.librehousehold.model.StatisticsPeriod;

import java.time.LocalDate;

public class StatisticsPeriodConverter {

    public record DateRange(LocalDate from, LocalDate to) {}

    public static DateRange convert(StatisticsPeriod period, LocalDate today) {
        return switch (period) {
            case LAST_7_DAYS -> new DateRange(today.minusDays(6), today);
            case LAST_14_DAYS -> new DateRange(today.minusDays(13), today);
            case THIS_MONTH -> new DateRange(today.withDayOfMonth(1), today);
            case LAST_3_MONTHS -> new DateRange(today.minusMonths(2).withDayOfMonth(1), today);
            case LAST_6_MONTHS -> new DateRange(today.minusMonths(5).withDayOfMonth(1), today);
            case THIS_YEAR -> new DateRange(LocalDate.of(today.getYear(), 1, 1), today);
            case LAST_YEAR -> new DateRange(
                    LocalDate.of(today.getYear() - 1, 1, 1),
                    LocalDate.of(today.getYear() - 1, 12, 31));
        };
    }
}
