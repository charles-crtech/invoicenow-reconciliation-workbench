package io.github.charlescrtech.invoicenow.reconciliation.application;

import io.github.charlescrtech.invoicenow.reconciliation.domain.LedgerEntry;
import io.github.charlescrtech.invoicenow.reconciliation.domain.LedgerEntryId;
import io.github.charlescrtech.invoicenow.shared.domain.source.SourceRecordId;
import io.github.charlescrtech.invoicenow.shared.domain.source.SourceSystemCode;
import java.util.Optional;

public interface LedgerEntryRepository {

    LedgerEntry save(LedgerEntry entry);

    Optional<LedgerEntry> findById(LedgerEntryId id);

    Optional<LedgerEntry> findBySourceIdentity(SourceSystemCode sourceSystem, SourceRecordId sourceRecordId);
}
