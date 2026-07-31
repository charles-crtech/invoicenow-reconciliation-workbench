package io.github.charlescrtech.invoicenow.shared.domain.identifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import io.github.charlescrtech.invoicenow.invoices.domain.InvoiceId;
import io.github.charlescrtech.invoicenow.reconciliation.domain.LedgerEntryId;
import io.github.charlescrtech.invoicenow.suppliers.domain.SupplierId;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class DomainIdentifierTest {

    private static final String CANONICAL = "123e4567-e89b-12d3-a456-426614174000";

    @ParameterizedTest
    @MethodSource("identifierParsers")
    void parsesCanonicalUuidAndRendersItStably(Function<String, UuidIdentifier> parser) {
        UuidIdentifier identifier = parser.apply(CANONICAL.toUpperCase());

        assertThat(identifier.value()).isEqualTo(UUID.fromString(CANONICAL));
        assertThat(identifier.canonicalValue()).isEqualTo(CANONICAL);
        assertThat(identifier.toString()).isEqualTo(CANONICAL);
    }

    @ParameterizedTest
    @MethodSource("identifierParsers")
    void rejectsNullBlankPaddedMalformedAndNonCanonicalText(Function<String, UuidIdentifier> parser) {
        assertThatNullPointerException().isThrownBy(() -> parser.apply(null));
        assertThatIllegalArgumentException().isThrownBy(() -> parser.apply(""));
        assertThatIllegalArgumentException().isThrownBy(() -> parser.apply("   "));
        assertThatIllegalArgumentException().isThrownBy(() -> parser.apply(" " + CANONICAL));
        assertThatIllegalArgumentException().isThrownBy(() -> parser.apply("not-a-uuid"));
        assertThatIllegalArgumentException().isThrownBy(() -> parser.apply("1-1-1-1-1"));
    }

    @Test
    void identifierTypesRemainDistinctForTheSameUuid() {
        UUID value = UUID.fromString(CANONICAL);

        SupplierId supplierId = new SupplierId(value);
        InvoiceId invoiceId = new InvoiceId(value);
        LedgerEntryId ledgerEntryId = new LedgerEntryId(value);

        assertThat((Object) supplierId).isNotEqualTo(invoiceId).isNotEqualTo(ledgerEntryId);
        assertThat((Object) invoiceId).isNotEqualTo(ledgerEntryId);
    }

    @Test
    void generatedIdentifiersHaveValuesAndRejectNullConstruction() {
        assertThat(SupplierId.newId().value()).isNotNull();
        assertThat(InvoiceId.newId().value()).isNotNull();
        assertThat(LedgerEntryId.newId().value()).isNotNull();

        assertThatNullPointerException().isThrownBy(() -> new SupplierId(null));
        assertThatNullPointerException().isThrownBy(() -> new InvoiceId(null));
        assertThatNullPointerException().isThrownBy(() -> new LedgerEntryId(null));
    }

    private static Stream<Arguments> identifierParsers() {
        return Stream.of(
                Arguments.of((Function<String, UuidIdentifier>) SupplierId::parse),
                Arguments.of((Function<String, UuidIdentifier>) InvoiceId::parse),
                Arguments.of((Function<String, UuidIdentifier>) LedgerEntryId::parse));
    }
}
