package com.enterpriseai.hub.service;

import com.enterpriseai.hub.domain.RefreshToken;
import com.enterpriseai.hub.domain.User;
import com.enterpriseai.hub.exception.ApiException;
import com.enterpriseai.hub.exception.ErrorCode;
import com.enterpriseai.hub.repository.RefreshTokenRepository;
import com.enterpriseai.hub.security.AppUserPrincipal;
import com.enterpriseai.hub.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Server-side half of the refresh token scheme.
 *
 * <p>A refresh token is only accepted if it verifies cryptographically <i>and</i> a
 * matching, unrevoked row exists. Every successful refresh rotates the token: the old row
 * is revoked and a new one issued. Rotation means a stolen refresh token has a short
 * useful life, and re-use of an already-rotated token is visible in the data.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;

    @Transactional
    public String issue(User user, AppUserPrincipal principal, String userAgent) {
        String token = jwtService.generateRefreshToken(principal);

        RefreshToken record = new RefreshToken();
        record.setUser(user);
        record.setTokenHash(hash(token));
        record.setIssuedAt(Instant.now());
        record.setExpiresAt(Instant.now().plus(jwtService.getRefreshTokenTtl()));
        record.setUserAgent(truncate(userAgent));
        refreshTokenRepository.save(record);

        return token;
    }

    @Transactional
    public RefreshToken consume(String token) {
        RefreshToken record = refreshTokenRepository.findByTokenHash(hash(token))
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_REFRESH_TOKEN,
                        "This refresh token is not recognised"));

        if (!record.isActive(Instant.now())) {
            log.warn("Rejected refresh token for user {}: revoked={} expired={}",
                    record.getUser().getId(), record.isRevoked(), record.getExpiresAt().isBefore(Instant.now()));
            throw new ApiException(ErrorCode.INVALID_REFRESH_TOKEN,
                    "This refresh token has expired or been revoked. Please sign in again.");
        }

        record.setRevoked(true);
        refreshTokenRepository.save(record);
        return record;
    }

    @Transactional
    public void revoke(String token) {
        refreshTokenRepository.findByTokenHash(hash(token)).ifPresent(record -> {
            record.setRevoked(true);
            refreshTokenRepository.save(record);
        });
    }

    @Transactional
    public void revokeAllForUser(Long userId) {
        int revoked = refreshTokenRepository.revokeAllForUser(userId);
        if (revoked > 0) {
            log.info("Revoked {} active session(s) for user {}", revoked, userId);
        }
    }

    /**
     * Housekeeping: revoked and expired rows have no further purpose and would otherwise
     * grow without bound on a busy deployment.
     */
    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void purgeExpiredTokens() {
        int deleted = refreshTokenRepository.deleteExpiredOrRevoked(Instant.now());
        if (deleted > 0) {
            log.info("Purged {} expired or revoked refresh token(s)", deleted);
        }
    }

    static String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available in this JVM", ex);
        }
    }

    private String truncate(String userAgent) {
        if (userAgent == null) {
            return null;
        }
        return userAgent.length() <= 255 ? userAgent : userAgent.substring(0, 255);
    }
}
