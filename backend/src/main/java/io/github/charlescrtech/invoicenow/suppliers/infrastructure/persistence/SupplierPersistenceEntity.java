package io.github.charlescrtech.invoicenow.suppliers.infrastructure.persistence;

import io.github.charlescrtech.invoicenow.suppliers.domain.RegistrationIdentifier;
import io.github.charlescrtech.invoicenow.suppliers.domain.Supplier;
import io.github.charlescrtech.invoicenow.suppliers.domain.SupplierCode;
import io.github.charlescrtech.invoicenow.suppliers.domain.SupplierId;
import io.github.charlescrtech.invoicenow.suppliers.domain.SupplierName;
import io.github.charlescrtech.invoicenow.suppliers.domain.SupplierStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "suppliers", schema = "app")
class SupplierPersistenceEntity {

    @Id
    private UUID id;

    @Column(name = "supplier_code", nullable = false, length = 32, unique = true)
    private String supplierCode;

    @Column(name = "display_name", nullable = false, length = SupplierName.MAX_LENGTH)
    private String displayName;

    @Column(name = "registration_identifier", nullable = false, length = 64, unique = true)
    private String registrationIdentifier;

    @Column(name = "gst_registered", nullable = false)
    private boolean gstRegistered;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SupplierStatus status;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SupplierPersistenceEntity() {
    }

    private SupplierPersistenceEntity(
            UUID id,
            String supplierCode,
            String displayName,
            String registrationIdentifier,
            boolean gstRegistered,
            SupplierStatus status,
            Long version,
            Instant createdAt,
            Instant updatedAt) {
        this.id = id;
        this.supplierCode = supplierCode;
        this.displayName = displayName;
        this.registrationIdentifier = registrationIdentifier;
        this.gstRegistered = gstRegistered;
        this.status = status;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    static SupplierPersistenceEntity fromDomain(Supplier supplier) {
        Long persistenceVersion = supplier.version().isPresent()
                ? supplier.version().getAsLong()
                : null;
        return new SupplierPersistenceEntity(
                supplier.id().value(),
                supplier.code().value(),
                supplier.displayName().value(),
                supplier.registrationIdentifier().value(),
                supplier.gstRegistered(),
                supplier.status(),
                persistenceVersion,
                supplier.createdAt(),
                supplier.updatedAt());
    }

    Supplier toDomain() {
        return Supplier.restore(
                new SupplierId(id),
                new SupplierCode(supplierCode),
                new SupplierName(displayName),
                new RegistrationIdentifier(registrationIdentifier),
                gstRegistered,
                status,
                version,
                createdAt,
                updatedAt);
    }
}
