package io.github.charlescrtech.invoicenow.suppliers.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SupplierTest {

    private static final Instant CREATED_AT = Instant.parse("2026-07-31T08:00:00Z");
    private static final SupplierId SUPPLIER_ID = new SupplierId(
            UUID.fromString("123e4567-e89b-12d3-a456-426614174001"));

    @Test
    void normalizesSupplierFields() {
        SupplierCode code = SupplierCode.of("  sup_demo-01  ");
        SupplierName name = SupplierName.of("  Orchard Office Supplies  ");
        RegistrationIdentifier registration = RegistrationIdentifier.of("  synth-uen-000001  ");

        assertThat(code.value()).isEqualTo("SUP_DEMO-01");
        assertThat(name.value()).isEqualTo("Orchard Office Supplies");
        assertThat(registration.value()).isEqualTo("SYNTH-UEN-000001");
    }

    @Test
    void rejectsInvalidSupplierCodes() {
        assertThatNullPointerException().isThrownBy(() -> SupplierCode.of(null));
        assertThatIllegalArgumentException().isThrownBy(() -> SupplierCode.of("AB"));
        assertThatIllegalArgumentException().isThrownBy(() -> SupplierCode.of("SUP CODE"));
        assertThatIllegalArgumentException().isThrownBy(() -> SupplierCode.of("-SUPPLIER"));
        assertThatIllegalArgumentException().isThrownBy(() -> SupplierCode.of("A".repeat(33)));
    }

    @Test
    void rejectsInvalidSupplierNames() {
        assertThatNullPointerException().isThrownBy(() -> SupplierName.of(null));
        assertThatIllegalArgumentException().isThrownBy(() -> SupplierName.of("   "));
        assertThatIllegalArgumentException().isThrownBy(() -> SupplierName.of("A".repeat(201)));
    }

    @Test
    void rejectsRealOrMalformedRegistrationIdentifiers() {
        assertThatNullPointerException().isThrownBy(() -> RegistrationIdentifier.of(null));
        assertThatIllegalArgumentException().isThrownBy(() -> RegistrationIdentifier.of("201912345A"));
        assertThatIllegalArgumentException().isThrownBy(() -> RegistrationIdentifier.of("SYNTH-A1"));
        assertThatIllegalArgumentException().isThrownBy(() -> RegistrationIdentifier.of("SYNTH-UEN_001"));
        assertThatIllegalArgumentException().isThrownBy(() -> RegistrationIdentifier.of("SYNTH-" + "A".repeat(59)));
    }

    @Test
    void createsActiveUnpersistedSupplier() {
        Supplier supplier = newSupplier();

        assertThat(supplier.id()).isEqualTo(SUPPLIER_ID);
        assertThat(supplier.code()).isEqualTo(SupplierCode.of("SUP-001"));
        assertThat(supplier.status()).isEqualTo(SupplierStatus.ACTIVE);
        assertThat(supplier.version()).isEmpty();
        assertThat(supplier.createdAt()).isEqualTo(CREATED_AT);
        assertThat(supplier.updatedAt()).isEqualTo(CREATED_AT);
    }

    @Test
    void updatesProfileWithoutChangingIdentityOrCode() {
        Supplier original = newSupplier();
        Instant changedAt = CREATED_AT.plusSeconds(60);

        Supplier updated = original.updateProfile(
                SupplierName.of("Orchard Office Supplies Pte Ltd"),
                RegistrationIdentifier.of("SYNTH-UEN-000002"),
                false,
                changedAt);

        assertThat(updated.id()).isEqualTo(original.id());
        assertThat(updated.code()).isEqualTo(original.code());
        assertThat(updated.displayName()).isEqualTo(SupplierName.of("Orchard Office Supplies Pte Ltd"));
        assertThat(updated.registrationIdentifier()).isEqualTo(RegistrationIdentifier.of("SYNTH-UEN-000002"));
        assertThat(updated.gstRegistered()).isFalse();
        assertThat(updated.updatedAt()).isEqualTo(changedAt);
        assertThat(original.displayName()).isEqualTo(SupplierName.of("Orchard Office Supplies"));
    }

    @Test
    void followsActiveInactiveAndReactivationLifecycle() {
        Supplier inactive = newSupplier().deactivate(CREATED_AT.plusSeconds(1));
        Supplier active = inactive.reactivate(CREATED_AT.plusSeconds(2));

        assertThat(inactive.status()).isEqualTo(SupplierStatus.INACTIVE);
        assertThat(active.status()).isEqualTo(SupplierStatus.ACTIVE);
    }

    @Test
    void archivesFromActiveOrInactiveAndThenBecomesTerminal() {
        Supplier archivedFromActive = newSupplier().archive(CREATED_AT.plusSeconds(1));
        Supplier archivedFromInactive = newSupplier()
                .deactivate(CREATED_AT.plusSeconds(1))
                .archive(CREATED_AT.plusSeconds(2));

        assertThat(archivedFromActive.status()).isEqualTo(SupplierStatus.ARCHIVED);
        assertThat(archivedFromInactive.status()).isEqualTo(SupplierStatus.ARCHIVED);
        assertThatIllegalStateException()
                .isThrownBy(() -> archivedFromActive.updateProfile(
                        SupplierName.of("Changed"),
                        RegistrationIdentifier.of("SYNTH-UEN-000002"),
                        false,
                        CREATED_AT.plusSeconds(3)))
                .withMessage("archived suppliers are immutable");
        assertThatIllegalStateException()
                .isThrownBy(() -> archivedFromActive.archive(CREATED_AT.plusSeconds(3)))
                .withMessage("archived suppliers are immutable");
        assertThatIllegalStateException()
                .isThrownBy(() -> archivedFromActive.reactivate(CREATED_AT.plusSeconds(3)));
    }

    @Test
    void rejectsInvalidLifecycleTransitions() {
        Supplier active = newSupplier();
        Supplier inactive = active.deactivate(CREATED_AT.plusSeconds(1));

        assertThatIllegalStateException()
                .isThrownBy(() -> active.reactivate(CREATED_AT.plusSeconds(1)))
                .withMessage("only an inactive supplier can be reactivated");
        assertThatIllegalStateException()
                .isThrownBy(() -> inactive.deactivate(CREATED_AT.plusSeconds(2)))
                .withMessage("only an active supplier can be deactivated");
    }

    @Test
    void rejectsBackwardOrNullChangeTimes() {
        Supplier supplier = newSupplier();

        assertThatNullPointerException().isThrownBy(() -> supplier.deactivate(null));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> supplier.deactivate(CREATED_AT.minusSeconds(1)))
                .withMessage("changedAt must not be before the current updatedAt");
    }

    @Test
    void restoresPersistenceVersionAndRejectsInvalidState() {
        Supplier restored = Supplier.restore(
                SUPPLIER_ID,
                SupplierCode.of("SUP-001"),
                SupplierName.of("Orchard Office Supplies"),
                RegistrationIdentifier.of("SYNTH-UEN-000001"),
                true,
                SupplierStatus.INACTIVE,
                3,
                CREATED_AT,
                CREATED_AT.plusSeconds(10));

        assertThat(restored.version()).hasValue(3);
        assertThat(restored.status()).isEqualTo(SupplierStatus.INACTIVE);
        assertThatIllegalArgumentException().isThrownBy(() -> Supplier.restore(
                SUPPLIER_ID,
                SupplierCode.of("SUP-001"),
                SupplierName.of("Orchard Office Supplies"),
                RegistrationIdentifier.of("SYNTH-UEN-000001"),
                true,
                SupplierStatus.ACTIVE,
                -1,
                CREATED_AT,
                CREATED_AT));
        assertThatIllegalArgumentException().isThrownBy(() -> Supplier.restore(
                SUPPLIER_ID,
                SupplierCode.of("SUP-001"),
                SupplierName.of("Orchard Office Supplies"),
                RegistrationIdentifier.of("SYNTH-UEN-000001"),
                true,
                SupplierStatus.ACTIVE,
                0,
                CREATED_AT,
                CREATED_AT.minusSeconds(1)));
    }

    @Test
    void aggregateEqualityUsesSupplierIdentity() {
        Supplier original = newSupplier();
        Supplier changed = original.updateProfile(
                SupplierName.of("Different Name"),
                RegistrationIdentifier.of("SYNTH-UEN-000002"),
                false,
                CREATED_AT.plusSeconds(1));
        Supplier other = Supplier.create(
                SupplierId.newId(),
                SupplierCode.of("SUP-002"),
                SupplierName.of("Other Supplier"),
                RegistrationIdentifier.of("SYNTH-UEN-000003"),
                false,
                CREATED_AT);

        assertThat(original).isEqualTo(changed).hasSameHashCodeAs(changed);
        assertThat(original).isNotEqualTo(other);
    }

    private static Supplier newSupplier() {
        return Supplier.create(
                SUPPLIER_ID,
                SupplierCode.of("SUP-001"),
                SupplierName.of("Orchard Office Supplies"),
                RegistrationIdentifier.of("SYNTH-UEN-000001"),
                true,
                CREATED_AT);
    }
}
