package eu.wiegandt.librehousehold;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

class ApplicationTests {

    @Test
    void writeDocumentationSnippets() {

        var modules = ApplicationModules.of(LibrehouseholdApplication.class).verify();

        new Documenter(modules)
                .writeModulesAsPlantUml()
                .writeIndividualModulesAsPlantUml();
    }
}