package io.github.charlescrtech.invoicenow.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

class ArchitectureRulesTest {

    private static final String BASE_PACKAGE = "io.github.charlescrtech.invoicenow";

    private final JavaClasses productionClasses = new ClassFileImporter()
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
}
