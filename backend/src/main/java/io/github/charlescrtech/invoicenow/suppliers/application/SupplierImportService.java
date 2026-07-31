package io.github.charlescrtech.invoicenow.suppliers.application;

import io.github.charlescrtech.invoicenow.suppliers.domain.Supplier;
import io.github.charlescrtech.invoicenow.suppliers.domain.SupplierCode;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Explicit supplier-module API used by transactional import orchestration. */
@Service
public class SupplierImportService {

    private final SupplierRepository repository;

    SupplierImportService(SupplierRepository repository) {
        this.repository = repository;
    }

    public Supplier save(Supplier supplier) {
        return repository.save(supplier);
    }

    public Optional<Supplier> findByCode(SupplierCode code) {
        return repository.findByCode(code);
    }
}
