package io.github.charlescrtech.invoicenow.suppliers.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataSupplierRepository extends JpaRepository<SupplierPersistenceEntity, UUID> {

    Optional<SupplierPersistenceEntity> findBySupplierCode(String supplierCode);
}
