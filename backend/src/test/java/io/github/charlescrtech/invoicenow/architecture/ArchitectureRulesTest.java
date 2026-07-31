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
    void persistenceEntitiesMustNotBePublicApiTypes() {
        ArchRule rule = noClasses()
                .that()
                .areAnnotatedWith(Entity.class)
                .should()
                .bePublic();

        rule.check(productionClasses);
    }
}
