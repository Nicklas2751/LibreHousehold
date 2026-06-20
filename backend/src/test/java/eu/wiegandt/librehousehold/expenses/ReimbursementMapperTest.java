package eu.wiegandt.librehousehold.expenses;

import eu.wiegandt.librehousehold.model.Reimbursement;
import eu.wiegandt.librehousehold.model.ReimbursementCreate;
import eu.wiegandt.librehousehold.model.ReimbursementUpdate;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ReimbursementMapperTest {

    private final ReimbursementMapper mapper = Mappers.getMapper(ReimbursementMapper.class);

    @Nested
    class toReimbursement {

        @Test
        void entityWithAllFields_mapsAllFieldsCorrectly() {
            // given
            var id = UUID.randomUUID();
            var householdId = UUID.randomUUID();
            var creditorId = UUID.randomUUID();
            var debtorId = UUID.randomUUID();
            var entity = new ReimbursementEntity(id, householdId, BigDecimal.valueOf(50.0),
                    creditorId, debtorId, "CONFIRMED", "Payment received", LocalDateTime.now());
            var expected = new Reimbursement(id, 50.0, creditorId, debtorId, Reimbursement.StatusEnum.CONFIRMED)
                    .notes("Payment received");

            // when
            var result = mapper.toReimbursement(entity);

            // then
            assertThat(result).usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        void entityWithNullNotes_mapsToEmptyOptionalNotes() {
            // given
            var id = UUID.randomUUID();
            var creditorId = UUID.randomUUID();
            var debtorId = UUID.randomUUID();
            var entity = new ReimbursementEntity(id, UUID.randomUUID(),
                    BigDecimal.valueOf(25.0), creditorId, debtorId, "PENDING", null, LocalDateTime.now());
            var expected = new Reimbursement(id, 25.0, creditorId, debtorId, Reimbursement.StatusEnum.PENDING);

            // when
            var result = mapper.toReimbursement(entity);

            // then
            assertThat(result).usingRecursiveComparison().isEqualTo(expected);
        }
    }

    @Nested
    class toEntity {

        @Test
        void createWithAllFields_mapsAllFieldsCorrectly() {
            // given
            var id = UUID.randomUUID();
            var householdId = UUID.randomUUID();
            var creditorId = UUID.randomUUID();
            var debtorId = UUID.randomUUID();
            var create = new ReimbursementCreate(50.0, creditorId, debtorId).notes("Payment");
            var expected = new ReimbursementEntity(id, householdId, BigDecimal.valueOf(50.0),
                    creditorId, debtorId, "PENDING", "Payment", LocalDateTime.now());

            // when
            var result = mapper.toEntity(create, id, householdId);

            // then
            assertThat(result).usingRecursiveComparison()
                    .ignoringFields("isNew", "createdAt")
                    .isEqualTo(expected);
        }

        @Test
        void createWithoutNotes_setsStatusToPendingAndNullNotes() {
            // given
            var id = UUID.randomUUID();
            var householdId = UUID.randomUUID();
            var creditorId = UUID.randomUUID();
            var debtorId = UUID.randomUUID();
            var create = new ReimbursementCreate(25.0, creditorId, debtorId);
            var expected = new ReimbursementEntity(id, householdId, BigDecimal.valueOf(25.0),
                    creditorId, debtorId, "PENDING", null, LocalDateTime.now());

            // when
            var result = mapper.toEntity(create, id, householdId);

            // then
            assertThat(result).usingRecursiveComparison()
                    .ignoringFields("isNew", "createdAt")
                    .isEqualTo(expected);
        }
    }

    @Nested
    class updateEntityFromUpdate {

        @Test
        void presentStatus_updatesEntityStatus() {
            // given
            var entity = new ReimbursementEntity(UUID.randomUUID(), UUID.randomUUID(),
                    BigDecimal.valueOf(25.0), UUID.randomUUID(), UUID.randomUUID(), "PENDING", null, LocalDateTime.now());
            var update = new ReimbursementUpdate().status(ReimbursementUpdate.StatusEnum.CONFIRMED);

            // when
            mapper.updateEntityFromUpdate(update, entity);

            // then
            assertThat(entity.getStatus()).isEqualTo("CONFIRMED");
        }

        @Test
        void emptyStatus_doesNotUpdateEntityStatus() {
            // given
            var entity = new ReimbursementEntity(UUID.randomUUID(), UUID.randomUUID(),
                    BigDecimal.valueOf(25.0), UUID.randomUUID(), UUID.randomUUID(), "PENDING", null, LocalDateTime.now());
            var update = new ReimbursementUpdate();

            // when
            mapper.updateEntityFromUpdate(update, entity);

            // then
            assertThat(entity.getStatus()).isEqualTo("PENDING");
        }

        @Test
        void presentNotes_updatesEntityNotes() {
            // given
            var entity = new ReimbursementEntity(UUID.randomUUID(), UUID.randomUUID(),
                    BigDecimal.valueOf(25.0), UUID.randomUUID(), UUID.randomUUID(), "PENDING", null, LocalDateTime.now());
            var update = new ReimbursementUpdate().notes("All settled");

            // when
            mapper.updateEntityFromUpdate(update, entity);

            // then
            assertThat(entity.getNotes()).isEqualTo("All settled");
        }

        @Test
        void emptyNotes_doesNotUpdateEntityNotes() {
            // given
            var entity = new ReimbursementEntity(UUID.randomUUID(), UUID.randomUUID(),
                    BigDecimal.valueOf(25.0), UUID.randomUUID(), UUID.randomUUID(), "PENDING", "Existing note", LocalDateTime.now());
            var update = new ReimbursementUpdate();

            // when
            mapper.updateEntityFromUpdate(update, entity);

            // then
            assertThat(entity.getNotes()).isEqualTo("Existing note");
        }
    }

    @Nested
    class toStatusEnum {

        @Test
        void pendingString_returnsPendingEnum() {
            assertThat(mapper.toStatusEnum("PENDING")).isEqualTo(Reimbursement.StatusEnum.PENDING);
        }

        @Test
        void confirmedString_returnsConfirmedEnum() {
            assertThat(mapper.toStatusEnum("CONFIRMED")).isEqualTo(Reimbursement.StatusEnum.CONFIRMED);
        }

        @Test
        void rejectedString_returnsRejectedEnum() {
            assertThat(mapper.toStatusEnum("REJECTED")).isEqualTo(Reimbursement.StatusEnum.REJECTED);
        }
    }

    @Nested
    class optionalStatusToString {

        @Test
        void presentStatus_returnsStatusValue() {
            assertThat(mapper.optionalStatusToString(Optional.of(ReimbursementUpdate.StatusEnum.CONFIRMED)))
                    .isEqualTo("CONFIRMED");
        }

        @Test
        void emptyOptional_returnsNull() {
            assertThat(mapper.optionalStatusToString(Optional.empty())).isNull();
        }

        @Test
        void nullOptional_returnsNull() {
            assertThat(mapper.optionalStatusToString(null)).isNull();
        }
    }

    @Nested
    class toOptionalString {

        @Test
        void nonNullValue_returnsOptionalContainingValue() {
            assertThat(mapper.toOptionalString("note")).contains("note");
        }

        @Test
        void nullValue_returnsEmptyOptional() {
            assertThat(mapper.toOptionalString(null)).isEmpty();
        }
    }

    @Nested
    class fromOptionalString {

        @Test
        void presentOptional_returnsValue() {
            assertThat(mapper.fromOptionalString(Optional.of("note"))).isEqualTo("note");
        }

        @Test
        void emptyOptional_returnsNull() {
            assertThat(mapper.fromOptionalString(Optional.empty())).isNull();
        }
    }
}
