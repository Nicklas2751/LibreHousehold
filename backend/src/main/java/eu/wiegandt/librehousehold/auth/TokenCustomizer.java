package eu.wiegandt.librehousehold.auth;

import eu.wiegandt.librehousehold.household.MemberQuery;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
class TokenCustomizer implements OAuth2TokenCustomizer<JwtEncodingContext> {

    private final MemberQuery memberQuery;

    TokenCustomizer(MemberQuery memberQuery) {
        this.memberQuery = memberQuery;
    }

    @Override
    public void customize(JwtEncodingContext context) {
        if (!OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
            return;
        }
        var memberId = UUID.fromString(context.getPrincipal().getName());
        var householdId = memberQuery.findHouseholdIdByMemberId(memberId);
        var admin = memberQuery.isAdmin(memberId);

        context.getClaims()
                .subject(memberId.toString())
                .claim("provider", "local")
                .claim("role", admin ? "admin" : "member")
                // claims(consumer) accesses the raw map and allows null values,
                // unlike claim(name, value) which throws on null
                .claims(map -> map.put("household_id", householdId.map(UUID::toString).orElse(null)));
    }
}
