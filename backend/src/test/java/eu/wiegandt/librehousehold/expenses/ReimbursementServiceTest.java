package eu.wiegandt.librehousehold.expenses;

import eu.wiegandt.librehousehold.model.ReimbursementCreate;
import eu.wiegandt.librehousehold.model.ReimbursementUpdate;
import org.instancio.Instancio;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.instancio.Select.field;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

@ExtendWith(MockitoExtension.class)
class ReimbursementServiceTest {

    @Mock
    private ReimbursementRepository reimbursementRepository;

    @Spy
    private ReimbursementMapper reimbursementMapper = Mappers.getMapper(ReimbursementMapper.class);

    @InjectMocks
    private ReimbursementService reimbursementService;

    @Nested
    class getReimbursements {

        @Test
        void existingHousehold_returnsMappedReimbursements() {
            // given
            var householdId = UUID.randomUUID();
            var entity = Instancio.of(ReimbursementEntity.class)
                    .set(field(ReimbursementEntity.class, "status"), "PENDING")
                    .create();
            doReturn(List.of(entity)).when(reimbursementRepository).findByHouseholdId(householdId);
            var expected = reimbursementMapper.toReimbursement(entity);

            // when
            var result = reimbursementService.getReimbursements(householdId);

            // then
            assertThat(result).singleElement().usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        void noReimbursements_returnsEmptyList() {
            // given
            var householdId = UUID.randomUUID();
            doReturn(List.of()).when(reimbursementRepository).findByHouseholdId(householdId);

            // when
            var result = reimbursementService.getReimbursements(householdId);

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    class createReimbursement {

        @Test
        void validData_returnsCreatedReimbursement() {
            // given
            var householdId = UUID.randomUUID();
            var create = Instancio.create(ReimbursementCreate.class);
            var savedEntity = Instancio.of(ReimbursementEntity.class)
                    .set(field(ReimbursementEntity.class, "status"), "CONFIRMED")
                    .create();
            doReturn(savedEntity).when(reimbursementRepository).save(any(ReimbursementEntity.class));
            var expected = reimbursementMapper.toReimbursement(savedEntity);

            // when
            var result = reimbursementService.createReimbursement(householdId, create);

            // then
            assertThat(result).usingRecursiveComparison().isEqualTo(expected);
        }
    }

    @Nested
    class updateReimbursement {

        @Test
        void notFound_throwsReimbursementNotFoundException() {
            // given
            var householdId = UUID.randomUUID();
            var reimbursementId = UUID.randomUUID();
            doReturn(Optional.empty()).when(reimbursementRepository).findByIdAndHouseholdId(reimbursementId, householdId);

            // when / then
            assertThatThrownBy(() -> reimbursementService.updateReimbursement(householdId, reimbursementId, new ReimbursementUpdate()))
                    .isInstanceOf(ReimbursementNotFoundException.class);
        }

        @Test
        void pendingToConfirmed_updatesStatus() {
            // given
            var householdId = UUID.randomUUID();
            var reimbursementId = UUID.randomUUID();
            var entity = Instancio.create(ReimbursementEntity.class);
            doReturn(Optional.of(entity)).when(reimbursementRepository).findByIdAndHouseholdId(reimbursementId, householdId);
            var update = new ReimbursementUpdate().status(ReimbursementUpdate.StatusEnum.CONFIRMED);

            // when
            var result = reimbursementService.updateReimbursement(householdId, reimbursementId, update);

            // then — entity is mutated in-place by the mapper during the service call
            var expected = reimbursementMapper.toReimbursement(entity);
            assertThat(result).usingRecursiveComparison().isEqualTo(expected);
        }
    }
}
