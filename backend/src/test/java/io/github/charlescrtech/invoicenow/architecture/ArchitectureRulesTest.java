package io.github.charlescrtech.invoicenow.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.persistence.Entity;
import org.junit.jupiter.api.Test;

class ArchitectureRulesTest {

    private static final String BASE_PACKAGE = "io.github.charlescrtech.invoicenow";

    private final JavaClasses productionClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(BASE_PACKAGE);

    @Test
    void topLevelModulesMustNotContainDependencyCycles() {
        ArchRule rule = slices()
                .matching(BASE_PACKAGE + ".(*)..")
                .should()
                .beFreeOfCycles();

        rule.check(productionClasses);
    }

    @Test
    void sharedCodeMustNotDependOnBusinessModules() {
        ArchRule rule = noClasses()
                .that()
                .resideInAPackage(BASE_PACKAGE + ".shared..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        BASE_PACKAGE + ".identity..",
                        BASE_PACKAGE + ".suppliers..",
                        BASE_PACKAGE + ".invoices..",
                        BASE_PACKAGE + ".imports..",
                        BASE_PACKAGE + ".controls..",
                        BASE_PACKAGE + ".reconciliation..",
                        BASE_PACKAGE + ".exceptions..",
                        BASE_PACKAGE + ".dashboard..",
                        BASE_PACKAGE + ".audit..",
                        BASE_PACKAGE + ".assistant..");

        rule.check(productionClasses);
    }

    @Test
    void supplierDomainMustNotDependOnFrameworkOrOuterLayers() {
        ArchRule rule = noClasses()
                .that()
                .resideInAPackage(BASE_PACKAGE + ".suppliers.domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        BASE_PACKAGE + ".suppliers.application..",
                        BASE_PACKAGE + ".suppliers.infrastructure..",
                        "jakarta.persistence..",
                        "org.springframework..");

        rule.check(productionClasses);
    }

    @Test
    void invoiceDomainMustNotDependOnFrameworkOrOuterLayers() {
        ArchRule rule = noClasses()
                .that()
                .resideInAPackage(BASE_PACKAGE + ".invoices.domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        BASE_PACKAGE + ".invoices.application..",
                        BASE_PACKAGE + ".invoices.infrastructure..",
                        "jakarta.persistence..",
                        "org.springframework..");

        rule.check(productionClasses);
    }

    @Test
    void reconciliationDomainMustNotDependOnFrameworkOrOuterLayers() {
        ArchRule rule = noClasses()
                .that()
                .resideInAPackage(BASE_PACKAGE + ".reconciliation.domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        BASE_PACKAGE + ".reconciliation.application..",
                        BASE_PACKAGE + ".reconciliation.infrastructure..",
                        "jakarta.persistence..",
                        "org.springframework..");

        rule.check(productionClasses);
    }

    @Test
    void importDomainMustNotDependOnFrameworkOrOuterLayers() {
        ArchRule rule = noClasses()
                .that()
                .resideInAPackage(BASE_PACKAGE + ".imports.domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        BASE_PACKAGE + ".imports.application..",
                        BASE_PACKAGE + ".imports.infrastructure..",
                        "jakarta..",
                        "org.springframework..");

        rule.check(productionClasses);
    }

    @Test
    void importsMustUseModuleServicesInsteadOfForeignRepositories() {
        for (String repository : java.util.List.of(
                "SupplierRepository", "InvoiceRepository", "LedgerEntryRepository")) {
            noClasses()
                    .that()
                    .resideInAPackage(BASE_PACKAGE + ".imports..")
                    .should()
                    .dependOnClassesThat()
                    .haveSimpleName(repository)
                    .check(productionClasses);
        }
    }

    @Test
    void persistenceEntitiesMustNotBePublicApiTypes() {
        ArchRule rule = noClasses()
                .that()
                .areAnnotatedWith(Entity.class)
                .should()
                .bePublic();

        rule.check(productionClasses);
    }

    @Test
    void productionCodeMustNotAccessTheTestOnlyScenarioOracle() {
        ArchRule rule = noClasses()
                .should()
                .dependOnClassesThat()
                .resideInAPackage(BASE_PACKAGE + ".generator.oracle..");

        rule.check(productionClasses);
    }
}
