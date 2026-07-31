package io.github.charlescrtech.invoicenow.suppliers.infrastructure.persistence;

import io.github.charlescrtech.invoicenow.suppliers.application.SupplierRepository;
import io.github.charlescrtech.invoicenow.suppliers.domain.Supplier;
import io.github.charlescrtech.invoicenow.suppliers.domain.SupplierCode;
import io.github.charlescrtech.invoicenow.suppliers.domain.SupplierId;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class JpaSupplierRepositoryAdapter implements SupplierRepository {

    private final SpringDataSupplierRepository repository;

    JpaSupplierRepositoryAdapter(SpringDataSupplierRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public Supplier save(Supplier supplier) {
        Objects.requireNonNull(supplier, "supplier must not be null");
        SupplierPersistenceEntity saved = repository.saveAndFlush(
                SupplierPersistenceEntity.fromDomain(supplier));
        return saved.toDomain();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Supplier> findById(SupplierId id) {
        Objects.requireNonNull(id, "supplier ID must not be null");
        return repository.findById(id.value()).map(SupplierPersistenceEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Supplier> findByCode(SupplierCode code) {
        Objects.requireNonNull(code, "supplier code must not be null");
        return repository.findBySupplierCode(code.value()).map(SupplierPersistenceEntity::toDomain);
    }
}
