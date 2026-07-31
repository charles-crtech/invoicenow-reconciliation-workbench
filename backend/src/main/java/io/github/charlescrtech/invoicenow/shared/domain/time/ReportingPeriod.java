package io.github.charlescrtech.invoicenow.shared.domain.time;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/** A reporting interval represented as [startInclusive, endExclusive). */
public record ReportingPeriod(LocalDate startInclusive, LocalDate endExclusive) {

    public ReportingPeriod {
        Objects.requireNonNull(startInclusive, "startInclusive must not be null");
        Objects.requireNonNull(endExclusive, "endExclusive must not be null");
        if (!startInclusive.isBefore(endExclusive)) {
            throw new IllegalArgumentException("startInclusive must be before endExclusive");
        }
    }

    public static ReportingPeriod monthly(YearMonth month) {
        Objects.requireNonNull(month, "month must not be null");
        return new ReportingPeriod(month.atDay(1), month.plusMonths(1).atDay(1));
    }

    public boolean contains(LocalDate date) {
        Objects.requireNonNull(date, "date must not be null");
        return !date.isBefore(startInclusive) && date.isBefore(endExclusive);
    }

    public boolean overlaps(ReportingPeriod other) {
        Objects.requireNonNull(other, "other period must not be null");
        return startInclusive.isBefore(other.endExclusive)
                && other.startInclusive.isBefore(endExclusive);
    }

    public boolean isAdjacentTo(ReportingPeriod other) {
        Objects.requireNonNull(other, "other period must not be null");
        return endExclusive.equals(other.startInclusive)
                || other.endExclusive.equals(startInclusive);
    }

    public long lengthInDays() {
        return ChronoUnit.DAYS.between(startInclusive, endExclusive);
    }
}
