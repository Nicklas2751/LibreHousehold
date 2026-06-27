package eu.wiegandt.librehousehold.auth;

import eu.wiegandt.librehousehold.auth.model.FederatedIdentityEntity;
import eu.wiegandt.librehousehold.auth.repository.FederatedIdentityRepository;
import eu.wiegandt.librehousehold.household.MemberQuery;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
class TokenCustomizer implements OAuth2TokenCustomizer<JwtEncodingContext> {

    private final MemberQuery memberQuery;
    private final FederatedIdentityRepository federatedIdentityRepository;

    TokenCustomizer(MemberQuery memberQuery, FederatedIdentityRepository federatedIdentityRepository) {
        this.memberQuery = memberQuery;
        this.federatedIdentityRepository = federatedIdentityRepository;
    }

    @Override
    public void customize(JwtEncodingContext context) {
        if (!OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
            return;
        }
        var principal = context.getPrincipal();
        var accountId = resolveAccountId(principal);
        var provider = resolveProvider(principal);
        var householdId = memberQuery.findHouseholdIdByMemberId(accountId);
        var admin = memberQuery.isAdmin(accountId);

        context.getClaims()
                .subject(accountId.toString())
                .claim("provider", provider)
                .claim("role", admin ? "admin" : "member")
                // claims(consumer) accesses the raw map and allows null values,
                // unlike claim(name, value) which throws on null
                .claims(map -> map.put("household_id", householdId.map(UUID::toString).orElse(null)));
    }

    private UUID resolveAccountId(Authentication principal) {
        return switch (principal) {
            case OAuth2AuthenticationToken token -> federatedIdentityRepository
                    .findByProviderAndProviderSub(token.getAuthorizedClientRegistrationId(), token.getName())
                    .map(FederatedIdentityEntity::accountId)
                    .orElseThrow();
            default -> UUID.fromString(principal.getName());
        };
    }

    private String resolveProvider(Authentication principal) {
        return switch (principal) {
            case OAuth2AuthenticationToken token -> token.getAuthorizedClientRegistrationId();
            default -> "local";
        };
    }
}
