package com.enterpriseai.hub.security;

import com.enterpriseai.hub.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Issues and verifies the platform's JWTs.
 *
 * <p>Access tokens are self-contained (subject, user id, authorities) so the API stays
 * stateless and horizontally scalable. Refresh tokens are opaque-by-design: they are JWTs
 * as well, but they are additionally recorded server side as a SHA-256 hash by
 * {@link com.enterpriseai.hub.service.RefreshTokenService}, which gives the platform the
 * ability to revoke a session - something a purely stateless scheme cannot do.</p>
 */
@Slf4j
@Service
public class JwtService {

    public static final String CLAIM_USER_ID = "uid";
    public static final String CLAIM_AUTHORITIES = "authorities";
    public static final String CLAIM_TOKEN_TYPE = "typ";
    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    private final SecretKey signingKey;
    private final String issuer;
    private final Duration accessTokenTtl;
    private final Duration refreshTokenTtl;

    public JwtService(AppProperties properties) {
        this.signingKey = buildKey(properties.getJwt().getSecret());
        this.issuer = properties.getJwt().getIssuer();
        this.accessTokenTtl = properties.getJwt().getAccessTokenTtl();
        this.refreshTokenTtl = properties.getJwt().getRefreshTokenTtl();
    }

    /**
     * Accepts either a base64 encoded secret or a raw string. HMAC-SHA256 requires at
     * least 256 bits of key material; a shorter secret fails fast at startup rather than
     * silently weakening every token.
     */
    private static SecretKey buildKey(String configuredSecret) {
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(configuredSecret);
        } catch (IllegalArgumentException notBase64) {
            keyBytes = configuredSecret.getBytes(StandardCharsets.UTF_8);
        }
        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                    "app.jwt.secret must provide at least 256 bits (32 bytes) of key material; "
                            + "configure the JWT_SECRET environment variable with a longer value");
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateAccessToken(AppUserPrincipal principal) {
        return buildToken(principal, TYPE_ACCESS, accessTokenTtl);
    }

    public String generateRefreshToken(AppUserPrincipal principal) {
        return buildToken(principal, TYPE_REFRESH, refreshTokenTtl);
    }

    private String buildToken(AppUserPrincipal principal, String type, Duration ttl) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .issuer(issuer)
                .subject(principal.getEmail())
                .claim(CLAIM_USER_ID, principal.getId())
                .claim(CLAIM_AUTHORITIES, List.copyOf(principal.getAuthorityNames()))
                .claim(CLAIM_TOKEN_TYPE, type)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(signingKey)
                .compact();
    }

    /**
     * @return the verified claims, or {@link Optional#empty()} when the token is expired,
     * tampered with, issued by someone else or simply malformed. Verification failures are
     * never surfaced to the caller as exceptions: an invalid token is an ordinary,
     * expected condition on a public endpoint.
     */
    public Optional<Claims> parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(issuer)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(claims);
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("Rejected JWT: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    public Optional<Claims> parseAccessToken(String token) {
        return parse(token).filter(claims -> TYPE_ACCESS.equals(claims.get(CLAIM_TOKEN_TYPE, String.class)));
    }

    public Optional<Claims> parseRefreshToken(String token) {
        return parse(token).filter(claims -> TYPE_REFRESH.equals(claims.get(CLAIM_TOKEN_TYPE, String.class)));
    }

    public AppUserPrincipal toPrincipal(Claims claims) {
        Long userId = claims.get(CLAIM_USER_ID, Number.class).longValue();
        @SuppressWarnings("unchecked")
        List<String> authorities = claims.get(CLAIM_AUTHORITIES, List.class);
        Set<String> authoritySet = authorities == null
                ? Set.of()
                : new LinkedHashSet<>(authorities.stream().map(String::valueOf).toList());
        return new AppUserPrincipal(userId, claims.getSubject(), claims.getSubject(), "", true, authoritySet);
    }

    public Duration getAccessTokenTtl() {
        return accessTokenTtl;
    }

    public Duration getRefreshTokenTtl() {
        return refreshTokenTtl;
    }
}
