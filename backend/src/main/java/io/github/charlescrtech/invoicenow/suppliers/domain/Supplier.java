package io.github.charlescrtech.invoicenow.suppliers.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.OptionalLong;

/** Immutable supplier aggregate. Persistence and HTTP concerns stay outside this type. */
public final class Supplier {

    private final SupplierId id;
    private final SupplierCode code;
    private final SupplierName displayName;
    private final RegistrationIdentifier registrationIdentifier;
    private final boolean gstRegistered;
    private final SupplierStatus status;
    private final Long version;
    private final Instant createdAt;
    private final Instant updatedAt;

    private Supplier(
            SupplierId id,
            SupplierCode code,
            SupplierName displayName,
            RegistrationIdentifier registrationIdentifier,
            boolean gstRegistered,
            SupplierStatus status,
            Long version,
            Instant createdAt,
            Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "supplier ID must not be null");
        this.code = Objects.requireNonNull(code, "supplier code must not be null");
        this.displayName = Objects.requireNonNull(displayName, "display name must not be null");
        this.registrationIdentifier = Objects.requireNonNull(
                registrationIdentifier,
                "registration identifier must not be null");
        this.status = Objects.requireNonNull(status, "supplier status must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        if (version != null && version < 0) {
            throw new IllegalArgumentException("supplier version must not be negative");
        }
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not be before createdAt");
        }
        this.gstRegistered = gstRegistered;
        this.version = version;
    }

    public static Supplier create(
            SupplierCode code,
            SupplierName displayName,
            RegistrationIdentifier registrationIdentifier,
            boolean gstRegistered,
            Instant createdAt) {
        return create(SupplierId.newId(), code, displayName, registrationIdentifier, gstRegistered, createdAt);
    }

    public static Supplier create(
            SupplierId id,
            SupplierCode code,
            SupplierName displayName,
            RegistrationIdentifier registrationIdentifier,
            boolean gstRegistered,
            Instant createdAt) {
        return new Supplier(
                id,
                code,
                displayName,
                registrationIdentifier,
                gstRegistered,
                SupplierStatus.ACTIVE,
                null,
                createdAt,
                createdAt);
    }

    public static Supplier restore(
            SupplierId id,
            SupplierCode code,
            SupplierName displayName,
            RegistrationIdentifier registrationIdentifier,
            boolean gstRegistered,
            SupplierStatus status,
            long version,
            Instant createdAt,
            Instant updatedAt) {
        return new Supplier(
                id,
                code,
                displayName,
                registrationIdentifier,
                gstRegistered,
                status,
                version,
                createdAt,
                updatedAt);
    }

    public Supplier updateProfile(
            SupplierName newDisplayName,
            RegistrationIdentifier newRegistrationIdentifier,
            boolean newGstRegistered,
            Instant changedAt) {
        ensureNotArchived();
        validateChangeTime(changedAt);
        return new Supplier(
                id,
                code,
                newDisplayName,
                newRegistrationIdentifier,
                newGstRegistered,
                status,
                version,
                createdAt,
                changedAt);
    }

    public Supplier deactivate(Instant changedAt) {
        requireStatus(SupplierStatus.ACTIVE, "only an active supplier can be deactivated");
        return transitionTo(SupplierStatus.INACTIVE, changedAt);
    }

    public Supplier reactivate(Instant changedAt) {
        requireStatus(SupplierStatus.INACTIVE, "only an inactive supplier can be reactivated");
        return transitionTo(SupplierStatus.ACTIVE, changedAt);
    }

    public Supplier archive(Instant changedAt) {
        ensureNotArchived();
        return transitionTo(SupplierStatus.ARCHIVED, changedAt);
    }

    private Supplier transitionTo(SupplierStatus newStatus, Instant changedAt) {
        validateChangeTime(changedAt);
        return new Supplier(
                id,
                code,
                displayName,
                registrationIdentifier,
                gstRegistered,
                newStatus,
                version,
                createdAt,
                changedAt);
    }

    private void ensureNotArchived() {
        if (status == SupplierStatus.ARCHIVED) {
            throw new IllegalStateException("archived suppliers are immutable");
        }
    }

    private void requireStatus(SupplierStatus expected, String message) {
        if (status != expected) {
            throw new IllegalStateException(message);
        }
    }

    private void validateChangeTime(Instant changedAt) {
        Objects.requireNonNull(changedAt, "changedAt must not be null");
        if (changedAt.isBefore(updatedAt)) {
            throw new IllegalArgumentException("changedAt must not be before the current updatedAt");
        }
    }

    public SupplierId id() {
        return id;
    }

    public SupplierCode code() {
        return code;
    }

    public SupplierName displayName() {
        return displayName;
    }

    public RegistrationIdentifier registrationIdentifier() {
        return registrationIdentifier;
    }

    public boolean gstRegistered() {
        return gstRegistered;
    }

    public SupplierStatus status() {
        return status;
    }

    public OptionalLong version() {
        return version == null ? OptionalLong.empty() : OptionalLong.of(version);
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof Supplier supplier && id.equals(supplier.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Supplier[id=" + id + ", code=" + code + ", status=" + status + "]";
    }
}
