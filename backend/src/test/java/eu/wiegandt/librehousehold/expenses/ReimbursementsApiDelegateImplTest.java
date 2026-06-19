package eu.wiegandt.librehousehold.expenses;

import eu.wiegandt.librehousehold.model.Reimbursement;
import eu.wiegandt.librehousehold.model.ReimbursementCreate;
import eu.wiegandt.librehousehold.model.ReimbursementUpdate;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
class ReimbursementsApiDelegateImplTest {

    @Mock
    private ReimbursementService reimbursementService;

    @InjectMocks
    private ReimbursementsApiDelegateImpl delegate;

    private Reimbursement buildReimbursement(UUID id) {
        return new Reimbursement(id, 25.0, UUID.randomUUID(), UUID.randomUUID(), Reimbursement.StatusEnum.PENDING);
    }

    @Nested
    class getReimbursements {

        @Test
        void validHousehold_returnsOk() {
            // given
            var householdId = UUID.randomUUID();
            var reimbursement = buildReimbursement(UUID.randomUUID());
            doReturn(List.of(reimbursement)).when(reimbursementService).getReimbursements(householdId);

            // when
            var result = delegate.getReimbursements(householdId);

            // then
            assertThat(result.getStatusCode().value()).isEqualTo(200);
            assertThat(result.getBody()).containsExactly(reimbursement);
        }
    }

    @Nested
    class createReimbursement {

        @Test
        void validInput_returnsCreated() {
            // given
            var householdId = UUID.randomUUID();
            var create = new ReimbursementCreate(25.0, UUID.randomUUID(), UUID.randomUUID());
            var reimbursement = buildReimbursement(UUID.randomUUID());
            doReturn(reimbursement).when(reimbursementService).createReimbursement(householdId, create);

            // when
            var result = delegate.createReimbursement(householdId, Optional.of(create));

            // then
            assertThat(result.getStatusCode().value()).isEqualTo(201);
            assertThat(result.getBody()).isEqualTo(reimbursement);
        }
    }

    @Nested
    class updateReimbursement {

        @Test
        void validInput_returnsOk() {
            // given
            var householdId = UUID.randomUUID();
            var reimbursementId = UUID.randomUUID();
            var update = new ReimbursementUpdate().status(ReimbursementUpdate.StatusEnum.CONFIRMED);
            var updated = buildReimbursement(reimbursementId);
            doReturn(updated).when(reimbursementService).updateReimbursement(householdId, reimbursementId, update);

            // when
            var result = delegate.updateReimbursement(householdId, reimbursementId, Optional.of(update));

            // then
            assertThat(result.getStatusCode().value()).isEqualTo(200);
            assertThat(result.getBody()).isEqualTo(updated);
        }

        @Test
        void notFound_throwsReimbursementNotFoundException() {
            // given
            var householdId = UUID.randomUUID();
            var reimbursementId = UUID.randomUUID();
            var update = new ReimbursementUpdate();
            doThrow(ReimbursementNotFoundException.class).when(reimbursementService)
                    .updateReimbursement(householdId, reimbursementId, update);

            // when / then
            assertThatThrownBy(() -> delegate.updateReimbursement(householdId, reimbursementId, Optional.of(update)))
                    .isInstanceOf(ReimbursementNotFoundException.class);
        }
    }
}
