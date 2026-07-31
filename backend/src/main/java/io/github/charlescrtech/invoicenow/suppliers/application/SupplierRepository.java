package io.github.charlescrtech.invoicenow.suppliers.application;

import io.github.charlescrtech.invoicenow.suppliers.domain.Supplier;
import io.github.charlescrtech.invoicenow.suppliers.domain.SupplierCode;
import io.github.charlescrtech.invoicenow.suppliers.domain.SupplierId;
import java.util.Optional;

/** Application-facing supplier persistence port. */
public interface SupplierRepository {

    Supplier save(Supplier supplier);

    Optional<Supplier> findById(SupplierId id);

    Optional<Supplier> findByCode(SupplierCode code);
}
