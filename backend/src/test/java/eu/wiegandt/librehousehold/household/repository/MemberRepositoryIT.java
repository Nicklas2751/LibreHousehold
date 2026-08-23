package eu.wiegandt.librehousehold.household.repository;

import eu.wiegandt.librehousehold.household.model.HouseholdEntity;
import eu.wiegandt.librehousehold.household.model.MemberEntity;
import org.instancio.Instancio;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.instancio.Select.field;

@DataJdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@TestPropertySource(properties = {
        "spring.flyway.locations=classpath:db/migration"
})
class MemberRepositoryIT {

    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:latest"));

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private HouseholdRepository householdRepository;

    @Nested
    class existsByIdAndHouseholdId {

        @Test
        void memberBelongsToHousehold_true() {
            // given
            var household = householdRepository.save(Instancio.create(HouseholdEntity.class));
            var member = memberRepository.save(Instancio.of(MemberEntity.class)
                    .set(field(MemberEntity::householdId), household.id())
                    .create());

            // when
            var result = memberRepository.existsByIdAndHouseholdId(member.getId(), household.id());

            // then
            assertThat(result).isTrue();
        }

        @Test
        void memberBelongsToDifferentHousehold_false() {
            // given
            var household = householdRepository.save(Instancio.create(HouseholdEntity.class));
            var member = memberRepository.save(Instancio.of(MemberEntity.class)
                    .set(field(MemberEntity::householdId), household.id())
                    .create());
            var otherHouseholdId = UUID.randomUUID();

            // when
            var result = memberRepository.existsByIdAndHouseholdId(member.getId(), otherHouseholdId);

            // then
            assertThat(result).isFalse();
        }
    }
}
