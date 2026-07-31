package io.github.charlescrtech.invoicenow.generator;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Currency;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public record GeneratorProfile(
        String profileVersion,
        String profileName,
        String datasetId,
        long seed,
        int supplierCount,
        int invoiceCount,
        int maxLinesPerInvoice,
        LocalDate reportingPeriodStart,
        LocalDate reportingPeriodEnd,
        Currency currency,
        BigDecimal taxRate) {

    public static final int MAX_SUPPLIERS = 10_000;
    public static final int MAX_INVOICES = 100_000;
    public static final int MAX_LINES_PER_INVOICE = 10;

    private static final Pattern PROFILE_NAME = Pattern.compile("[a-z][a-z0-9-]{2,31}");
    private static final Pattern DATASET_ID = Pattern.compile("[a-z0-9][a-z0-9._-]{2,63}");
    private static final Set<String> FIELDS = Set.of(
            "profile_version",
            "profile_name",
            "dataset_id",
            "seed",
            "supplier_count",
            "invoice_count",
            "max_lines_per_invoice",
            "reporting_period_start",
            "reporting_period_end",
            "currency",
            "tax_rate");
    private static final ObjectMapper JSON = new ObjectMapper();

    public GeneratorProfile {
        Objects.requireNonNull(profileVersion, "profile version must not be null");
        Objects.requireNonNull(profileName, "profile name must not be null");
        Objects.requireNonNull(datasetId, "dataset ID must not be null");
        Objects.requireNonNull(reportingPeriodStart, "reporting period start must not be null");
        Objects.requireNonNull(reportingPeriodEnd, "reporting period end must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        Objects.requireNonNull(taxRate, "tax rate must not be null");
        if (!profileVersion.equals("1.0")) {
            throw new IllegalArgumentException("profile version must be 1.0");
        }
        if (!PROFILE_NAME.matcher(profileName).matches()) {
            throw new IllegalArgumentException("profile name must be a supported lowercase identifier");
        }
        if (!DATASET_ID.matcher(datasetId).matches()) {
            throw new IllegalArgumentException("dataset ID must satisfy source contract v1");
        }
        if (supplierCount < 1 || supplierCount > MAX_SUPPLIERS) {
            throw new IllegalArgumentException("supplier count must be between 1 and " + MAX_SUPPLIERS);
        }
        if (invoiceCount < 1 || invoiceCount > MAX_INVOICES) {
            throw new IllegalArgumentException("invoice count must be between 1 and " + MAX_INVOICES);
        }
        if (maxLinesPerInvoice < 1 || maxLinesPerInvoice > MAX_LINES_PER_INVOICE) {
            throw new IllegalArgumentException(
                    "maximum lines per invoice must be between 1 and " + MAX_LINES_PER_INVOICE);
        }
        if (ChronoUnit.DAYS.between(reportingPeriodStart, reportingPeriodEnd) < 4) {
            throw new IllegalArgumentException("reporting period must contain at least four days");
        }
        if (currency.getDefaultFractionDigits() < 0) {
            throw new IllegalArgumentException("currency must define an ISO minor-unit scale");
        }
        try {
            taxRate = taxRate.setScale(4, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("tax rate must have at most four decimal places", exception);
        }
        if (taxRate.signum() < 0 || taxRate.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("tax rate must be between zero and one");
        }
    }

    public static GeneratorProfile load(Path path) throws IOException {
        Objects.requireNonNull(path, "profile path must not be null");
        JsonNode root = JSON.readTree(Files.readString(path));
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("generator profile must be a JSON object");
        }
        if (!root.propertyNames().equals(FIELDS)) {
            throw new IllegalArgumentException("generator profile must contain exactly the supported fields");
        }
        return new GeneratorProfile(
                requiredString(root, "profile_version"),
                requiredString(root, "profile_name"),
                requiredString(root, "dataset_id"),
                requiredLong(root, "seed"),
                requiredInt(root, "supplier_count"),
                requiredInt(root, "invoice_count"),
                requiredInt(root, "max_lines_per_invoice"),
                LocalDate.parse(requiredString(root, "reporting_period_start")),
                LocalDate.parse(requiredString(root, "reporting_period_end")),
                Currency.getInstance(requiredString(root, "currency")),
                requiredDecimal(root, "tax_rate"));
    }

    public int monetaryScale() {
        return currency.getDefaultFractionDigits();
    }

    private static String requiredString(JsonNode root, String name) {
        JsonNode value = root.get(name);
        if (value == null || !value.isString()) {
            throw new IllegalArgumentException(name + " must be a string");
        }
        return value.stringValue();
    }

    private static long requiredLong(JsonNode root, String name) {
        JsonNode value = root.get(name);
        if (value == null || !value.isIntegralNumber()) {
            throw new IllegalArgumentException(name + " must be an integer");
        }
        try {
            return value.bigIntegerValue().longValueExact();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(name + " must fit in a signed 64-bit integer", exception);
        }
    }

    private static int requiredInt(JsonNode root, String name) {
        long value = requiredLong(root, name);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(name + " must fit in a signed 32-bit integer");
        }
        return Math.toIntExact(value);
    }

    private static BigDecimal requiredDecimal(JsonNode root, String name) {
        JsonNode value = root.get(name);
        if (value == null || !value.isNumber()) {
            throw new IllegalArgumentException(name + " must be a number");
        }
        return value.decimalValue();
    }
}
