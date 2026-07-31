package io.github.charlescrtech.invoicenow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.charlescrtech.invoicenow.suppliers.application.SupplierRepository;
import io.github.charlescrtech.invoicenow.suppliers.domain.RegistrationIdentifier;
import io.github.charlescrtech.invoicenow.suppliers.domain.Supplier;
import io.github.charlescrtech.invoicenow.suppliers.domain.SupplierCode;
import io.github.charlescrtech.invoicenow.suppliers.domain.SupplierId;
import io.github.charlescrtech.invoicenow.suppliers.domain.SupplierName;
import io.github.charlescrtech.invoicenow.suppliers.domain.SupplierStatus;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Transactional
class SupplierRepositoryIntegrationTest {

    private static final Instant CREATED_AT = Instant.parse("2026-07-31T08:00:00Z");

    @Autowired
    private SupplierRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void migrationCreatesSupplierTableAndNamedConstraints() {
        Integer migrationCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.flyway_schema_history WHERE version = '2' AND success",
                Integer.class);
        List<String> constraints = jdbcTemplate.queryForList(
                """
                SELECT constraint_name
                FROM information_schema.table_constraints
                WHERE table_schema = 'app' AND table_name = 'suppliers'
                """,
                String.class);

        assertThat(migrationCount).isEqualTo(1);
        assertThat(constraints).contains(
                "suppliers_pkey",
                "uq_suppliers_supplier_code",
                "uq_suppliers_registration_identifier",
                "ck_suppliers_supplier_code_format",
                "ck_suppliers_display_name_format",
                "ck_suppliers_registration_identifier_format",
                "ck_suppliers_status",
                "ck_suppliers_version",
                "ck_suppliers_timestamps");
    }

    @Test
    void savesFindsAndUpdatesSupplierWithOptimisticVersion() {
        Supplier created = supplier("SUP-001", "SYNTH-UEN-000001");

        Supplier saved = repository.save(created);

        assertThat(saved.version()).hasValue(0);
        assertThat(repository.findById(saved.id())).contains(saved);
        assertThat(repository.findByCode(SupplierCode.of("sup-001"))).contains(saved);

        Supplier changed = saved.updateProfile(
                SupplierName.of("Updated Supplier"),
                RegistrationIdentifier.of("SYNTH-UEN-000002"),
                false,
                CREATED_AT.plusSeconds(60));
        Supplier updated = repository.save(changed);

        assertThat(updated.version()).hasValue(1);
        assertThat(updated.code()).isEqualTo(SupplierCode.of("SUP-001"));
        assertThat(updated.displayName()).isEqualTo(SupplierName.of("Updated Supplier"));
        assertThat(updated.registrationIdentifier()).isEqualTo(RegistrationIdentifier.of("SYNTH-UEN-000002"));
        assertThat(updated.gstRegistered()).isFalse();
        assertThat(updated.updatedAt()).isEqualTo(CREATED_AT.plusSeconds(60));
    }

    @Test
    void returnsEmptyForUnknownSupplier() {
        assertThat(repository.findById(SupplierId.newId())).isEmpty();
        assertThat(repository.findByCode(SupplierCode.of("MISSING"))).isEmpty();
    }

    @Test
    void databaseRejectsDuplicateSupplierCode() {
        repository.save(supplier("SUP-001", "SYNTH-UEN-000001"));

        assertThatThrownBy(() -> repository.save(supplier("SUP-001", "SYNTH-UEN-000002")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsDuplicateRegistrationIdentifier() {
        repository.save(supplier("SUP-001", "SYNTH-UEN-000001"));

        assertThatThrownBy(() -> repository.save(supplier("SUP-002", "SYNTH-UEN-000001")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsRawInvalidSupplierCode() {
        assertThatThrownBy(() -> insertRaw(
                        "lowercase code",
                        "Valid Name",
                        "SYNTH-UEN-100001",
                        "ACTIVE",
                        CREATED_AT,
                        CREATED_AT))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsRawNonSyntheticRegistrationIdentifier() {
        assertThatThrownBy(() -> insertRaw(
                        "SUP-RAW-01",
                        "Valid Name",
                        "201912345A",
                        "ACTIVE",
                        CREATED_AT,
                        CREATED_AT))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsUnknownStatus() {
        assertThatThrownBy(() -> insertRaw(
                        "SUP-RAW-02",
                        "Valid Name",
                        "SYNTH-UEN-100002",
                        "UNKNOWN",
                        CREATED_AT,
                        CREATED_AT))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsBackwardTimestamps() {
        assertThatThrownBy(() -> insertRaw(
                        "SUP-RAW-03",
                        "Valid Name",
                        "SYNTH-UEN-100003",
                        "ACTIVE",
                        CREATED_AT,
                        CREATED_AT.minusSeconds(1)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void persistsLifecycleState() {
        Supplier inactive = supplier("SUP-003", "SYNTH-UEN-000003")
                .deactivate(CREATED_AT.plusSeconds(1));
        Supplier saved = repository.save(inactive);

        assertThat(saved.status()).isEqualTo(SupplierStatus.INACTIVE);
        assertThat(repository.findById(saved.id())).get().extracting(Supplier::status)
                .isEqualTo(SupplierStatus.INACTIVE);
    }

    private static Supplier supplier(String code, String registrationIdentifier) {
        return Supplier.create(
                SupplierId.newId(),
                SupplierCode.of(code),
                SupplierName.of("Orchard Office Supplies"),
                RegistrationIdentifier.of(registrationIdentifier),
                true,
                CREATED_AT);
    }

    private void insertRaw(
            String supplierCode,
            String displayName,
            String registrationIdentifier,
            String status,
            Instant createdAt,
            Instant updatedAt) {
        jdbcTemplate.update(
                """
                INSERT INTO app.suppliers (
                    id,
                    supplier_code,
                    display_name,
                    registration_identifier,
                    gst_registered,
                    status,
                    version,
                    created_at,
                    updated_at
                ) VALUES (?, ?, ?, ?, TRUE, ?, 0, ?, ?)
                """,
                UUID.randomUUID(),
                supplierCode,
                displayName,
                registrationIdentifier,
                status,
                Timestamp.from(createdAt),
                Timestamp.from(updatedAt));
    }
}
