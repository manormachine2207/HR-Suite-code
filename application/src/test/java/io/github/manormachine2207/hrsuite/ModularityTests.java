package io.github.manormachine2207.hrsuite;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTests {

    private static final ApplicationModules MODULES =
            ApplicationModules.of(HrSuiteApplication.class);

    /** Fails on cyclic module dependencies or illegal access into a closed module's internals. */
    @Test
    void verifiesModuleStructure() {
        MODULES.verify();
    }
}
