package com.enterpriseai.hub.security;

import com.enterpriseai.hub.config.AppProperties;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "a-test-secret-that-is-long-enough-to-satisfy-hmac-sha256";

    private JwtService jwtService;
    private AppUserPrincipal principal;

    private static AppProperties propertiesWith(String secret, Duration accessTtl) {
        AppProperties properties = new AppProperties();
        properties.getJwt().setSecret(secret);
        properties.getJwt().setIssuer("enterprise-ai-hub");
        properties.getJwt().setAccessTokenTtl(accessTtl);
        properties.getJwt().setRefreshTokenTtl(Duration.ofDays(14));
        return properties;
    }

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(propertiesWith(SECRET, Duration.ofMinutes(30)));
        principal = new AppUserPrincipal(42L, "jane@acme.test", "Jane", "", true, Set.of("ROLE_USER"));
    }

    @Test
    @DisplayName("an access token round-trips subject, user id and authorities")
    void accessTokenCarriesIdentity() {
        String token = jwtService.generateAccessToken(principal);

        Optional<Claims> claims = jwtService.parseAccessToken(token);

        assertThat(claims).isPresent();
        assertThat(claims.get().getSubject()).isEqualTo("jane@acme.test");
        assertThat(claims.get().get(JwtService.CLAIM_USER_ID, Number.class).longValue()).isEqualTo(42L);

        AppUserPrincipal restored = jwtService.toPrincipal(claims.get());
        assertThat(restored.getId()).isEqualTo(42L);
        assertThat(restored.getAuthorityNames()).containsExactly("ROLE_USER");
    }

    @Test
    @DisplayName("a refresh token is not accepted where an access token is required")
    void tokenTypesAreNotInterchangeable() {
        String refreshToken = jwtService.generateRefreshToken(principal);

        assertThat(jwtService.parseAccessToken(refreshToken)).isEmpty();
        assertThat(jwtService.parseRefreshToken(refreshToken)).isPresent();
    }

    @Test
    @DisplayName("a token signed with a different secret is rejected")
    void rejectsForeignSignature() {
        String token = jwtService.generateAccessToken(principal);
        JwtService otherInstance = new JwtService(
                propertiesWith("a-completely-different-secret-of-sufficient-length", Duration.ofMinutes(30)));

        assertThat(otherInstance.parse(token)).isEmpty();
    }

    @Test
    @DisplayName("an expired token is rejected rather than throwing")
    void rejectsExpiredToken() {
        JwtService shortLived = new JwtService(propertiesWith(SECRET, Duration.ofSeconds(-1)));
        String token = shortLived.generateAccessToken(principal);

        assertThat(shortLived.parseAccessToken(token)).isEmpty();
    }

    @Test
    @DisplayName("garbage input is rejected rather than throwing")
    void rejectsMalformedToken() {
        assertThat(jwtService.parse("not.a.jwt")).isEmpty();
        assertThat(jwtService.parse("")).isEmpty();
    }

    @Test
    @DisplayName("a secret shorter than 256 bits fails fast at construction")
    void rejectsWeakSecret() {
        assertThatThrownBy(() -> new JwtService(propertiesWith("too-short", Duration.ofMinutes(30))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("256 bits");
    }
}
