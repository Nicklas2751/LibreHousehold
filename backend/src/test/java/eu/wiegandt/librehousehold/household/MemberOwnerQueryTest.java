package eu.wiegandt.librehousehold.household;

import eu.wiegandt.librehousehold.household.repository.MemberRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

@ExtendWith(MockitoExtension.class)
class MemberOwnerQueryTest {

    @Mock
    MemberRepository memberRepository;

    @InjectMocks
    MemberOwnerQuery query;

    @Nested
    class isOwner {

        @Test
        void sameIdAndMemberExists_returnsTrue() {
            // given
            var id = UUID.randomUUID();
            doReturn(true).when(memberRepository).existsById(id);

            // when
            var result = query.isOwner(id, id);

            // then
            assertThat(result).isTrue();
        }

        @Test
        void sameIdButMemberNotExists_returnsFalse() {
            // given
            var id = UUID.randomUUID();
            doReturn(false).when(memberRepository).existsById(id);

            // when
            var result = query.isOwner(id, id);

            // then
            assertThat(result).isFalse();
        }

        @Test
        void differentIds_returnsFalse() {
            // given
            var resourceId = UUID.randomUUID();
            var accountId = UUID.randomUUID();

            // when
            var result = query.isOwner(resourceId, accountId);

            // then
            assertThat(result).isFalse();
        }
    }
}
