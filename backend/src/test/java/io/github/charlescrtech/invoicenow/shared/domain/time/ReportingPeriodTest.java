package io.github.charlescrtech.invoicenow.shared.domain.time;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

class ReportingPeriodTest {

    @Test
    void constructsCalendarMonthAsHalfOpenRange() {
        ReportingPeriod february = ReportingPeriod.monthly(YearMonth.of(2024, 2));

        assertThat(february.startInclusive()).isEqualTo(LocalDate.of(2024, 2, 1));
        assertThat(february.endExclusive()).isEqualTo(LocalDate.of(2024, 3, 1));
        assertThat(february.lengthInDays()).isEqualTo(29);
    }

    @Test
    void includesStartAndExcludesEnd() {
        ReportingPeriod period = new ReportingPeriod(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 4, 1));

        assertThat(period.contains(LocalDate.of(2026, 1, 1))).isTrue();
        assertThat(period.contains(LocalDate.of(2026, 3, 31))).isTrue();
        assertThat(period.contains(LocalDate.of(2026, 4, 1))).isFalse();
        assertThat(period.contains(LocalDate.of(2025, 12, 31))).isFalse();
    }

    @Test
    void rejectsEmptyOrReversedRanges() {
        LocalDate date = LocalDate.of(2026, 1, 1);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ReportingPeriod(date, date))
                .withMessage("startInclusive must be before endExclusive");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ReportingPeriod(date.plusDays(1), date))
                .withMessage("startInclusive must be before endExclusive");
    }

    @Test
    void distinguishesOverlapFromAdjacency() {
        ReportingPeriod january = ReportingPeriod.monthly(YearMonth.of(2026, 1));
        ReportingPeriod february = ReportingPeriod.monthly(YearMonth.of(2026, 2));
        ReportingPeriod quarter = new ReportingPeriod(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 4, 1));

        assertThat(january.overlaps(february)).isFalse();
        assertThat(january.isAdjacentTo(february)).isTrue();
        assertThat(january.overlaps(quarter)).isTrue();
        assertThat(january.isAdjacentTo(quarter)).isFalse();
    }

    @Test
    void rejectsNullValuesAndOperations() {
        LocalDate date = LocalDate.of(2026, 1, 1);
        ReportingPeriod period = ReportingPeriod.monthly(YearMonth.of(2026, 1));

        assertThatNullPointerException().isThrownBy(() -> new ReportingPeriod(null, date));
        assertThatNullPointerException().isThrownBy(() -> new ReportingPeriod(date, null));
        assertThatNullPointerException().isThrownBy(() -> ReportingPeriod.monthly(null));
        assertThatNullPointerException().isThrownBy(() -> period.contains(null));
        assertThatNullPointerException().isThrownBy(() -> period.overlaps(null));
        assertThatNullPointerException().isThrownBy(() -> period.isAdjacentTo(null));
    }
}
