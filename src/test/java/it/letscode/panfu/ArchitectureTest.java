package it.letscode.panfu;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "it.letscode.panfu", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule namespaceIsStable = classes()
            .should().resideInAPackage("it.letscode.panfu..");

    @ArchTest
    static final ArchRule domainDoesNotDependOnTransport = noClasses()
            .that().resideInAnyPackage("..minigame..", "..persistence..", "..session..")
            .should().dependOnClassesThat().resideInAPackage("..transport.websocket..");
}
