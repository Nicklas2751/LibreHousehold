package eu.wiegandt.librehousehold.household;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HouseholdApiDelegateImplTest {

    private final HouseholdApiDelegateImpl householdApiDelegate = new HouseholdApiDelegateImpl();

    @Test
    void setupHousehold_empty_thors_problemException() {
        // when
        assertThatThrownBy(() -> householdApiDelegate.setupHousehold(Optional.empty()))
                // then
                .isInstanceOf(HouseholdSetupIsRequiredException.class)
                .hasMessageContaining("The body of setup must be set!");
    }

}