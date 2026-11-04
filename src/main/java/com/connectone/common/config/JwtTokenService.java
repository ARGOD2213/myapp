package com.connectone.common.config;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.connectone.common.exception.ApiException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class JwtTokenService {

    private static final long ACCESS_TOKEN_SECONDS = 900;
    private final byte[] signingKey;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    public JwtTokenService(
            @Value("${connectone.jwt.secret:}") String configuredSecret,
            ObjectMapper objectMapper,
            JdbcTemplate jdbcTemplate) {
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
        if (configuredSecret == null || configuredSecret.isBlank()) {
            this.signingKey = new byte[32];
            new SecureRandom().nextBytes(this.signingKey);
        } else {
            this.signingKey = configuredSecret.getBytes(StandardCharsets.UTF_8);
            if (this.signingKey.length < 32) {
                throw new IllegalStateException("CONNECTONE_JWT_SECRET must contain at least 32 bytes");
            }
        }
    }

    public String issueAccessToken(long userId, String email, List<String> roles) {
        try {
            String header = encode(objectMapper.writeValueAsString(Map.of("alg", "HS256", "typ", "JWT"))
                    .getBytes(StandardCharsets.UTF_8));
            long now = Instant.now().getEpochSecond();
            String tokenId = java.util.UUID.randomUUID().toString();
            List<String> permissions = jdbcTemplate.query(
                    "SELECT DISTINCT p.permission_code FROM app_permission p "
                            + "JOIN role_permission rp ON rp.permission_id = p.id "
                            + "JOIN user_role ur ON ur.role_id = rp.role_id "
                            + "WHERE ur.user_id = ? ORDER BY p.permission_code",
                    (result, row) -> result.getString(1), userId);
            String payload = encode(objectMapper.writeValueAsString(Map.of(
                    "sub", Long.toString(userId),
                    "email", email,
                    "roles", roles,
                    "permissions", permissions,
                    "jti", tokenId,
                    "iat", now,
                    "exp", now + ACCESS_TOKEN_SECONDS)).getBytes(StandardCharsets.UTF_8));
            String unsigned = header + "." + payload;
            String token = unsigned + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(sign(unsigned));
            jdbcTemplate.update(
                    "INSERT INTO access_token_revocation (token_id, user_id, expires_at) VALUES (?, ?, ?)",
                    tokenId, userId, java.sql.Timestamp.from(Instant.ofEpochSecond(now + ACCESS_TOKEN_SECONDS)));
            return token;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to issue access token", exception);
        }
    }

    public ConnectOnePrincipal validateAccessToken(String token) {
        try {
            String[] parts = token.split("\\.", -1);
            if (parts.length != 3 || !MessageDigest.isEqual(
                    sign(parts[0] + "." + parts[1]),
                    Base64.getUrlDecoder().decode(parts[2]))) {
                throw unauthorized();
            }

            JsonNode header = objectMapper.readTree(Base64.getUrlDecoder().decode(parts[0]));
            JsonNode payload = objectMapper.readTree(Base64.getUrlDecoder().decode(parts[1]));
            if (!"HS256".equals(header.path("alg").asText())
                    || payload.path("exp").asLong(0) <= Instant.now().getEpochSecond()) {
                throw unauthorized();
            }
            JsonNode rolesNode = payload.path("roles");
            if (!rolesNode.isArray()
                    || payload.path("sub").asText().isBlank()
                    || payload.path("email").asText().isBlank()
                    || payload.path("jti").asText().isBlank()) {
                throw unauthorized();
            }
            long userId = Long.parseLong(payload.path("sub").asText());
            Integer validToken = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM access_token_revocation t JOIN app_user u ON u.id = t.user_id "
                            + "WHERE t.token_id = ? AND t.user_id = ? AND t.revoked_at IS NULL "
                            + "AND t.expires_at > CURRENT_TIMESTAMP AND u.account_status = 'ACTIVE'",
                    Integer.class, payload.path("jti").asText(), userId);
            if (validToken == null || validToken != 1) {
                throw unauthorized();
            }
            List<String> currentRoles = jdbcTemplate.query(
                    "SELECT r.role_code FROM app_role r JOIN user_role ur ON ur.role_id = r.id "
                            + "WHERE ur.user_id = ? ORDER BY r.role_code",
                    (result, row) -> result.getString(1), userId);
            List<String> currentPermissions = jdbcTemplate.query(
                    "SELECT p.permission_code FROM app_permission p "
                            + "JOIN role_permission rp ON rp.permission_id = p.id "
                            + "JOIN user_role ur ON ur.role_id = rp.role_id "
                            + "WHERE ur.user_id = ? ORDER BY p.permission_code",
                    (result, row) -> result.getString(1), userId);
            List<String> roles = new ArrayList<>();
            for (JsonNode role : rolesNode) {
                if (!role.isTextual() || role.asText().isBlank()) {
                    throw unauthorized();
                }
                roles.add(role.asText());
            }
            if (!new java.util.HashSet<>(roles).equals(new java.util.HashSet<>(currentRoles))) {
                throw unauthorized();
            }
            JsonNode permissionsNode = payload.path("permissions");
            if (!permissionsNode.isArray()) {
                throw unauthorized();
            }
            List<String> permissions = new ArrayList<>();
            for (JsonNode permission : permissionsNode) {
                if (!permission.isTextual() || permission.asText().isBlank()) {
                    throw unauthorized();
                }
                permissions.add(permission.asText());
            }
            if (!new java.util.HashSet<>(permissions).equals(new java.util.HashSet<>(currentPermissions))) {
                throw unauthorized();
            }
            return new ConnectOnePrincipal(
                    userId,
                    payload.path("email").asText(),
                    roles,
                    permissions);
        } catch (ApiException exception) {
            throw exception;
        } catch (org.springframework.dao.DataAccessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw unauthorized();
        }
    }

    @org.springframework.scheduling.annotation.Scheduled(
            fixedDelayString = "${connectone.security.token-cleanup-ms:3600000}")
    public void purgeExpiredAccessTokens() {
        jdbcTemplate.update("DELETE FROM access_token_revocation WHERE expires_at <= CURRENT_TIMESTAMP");
    }

    private byte[] sign(String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(signingKey, "HmacSHA256"));
        return mac.doFinal(value.getBytes(StandardCharsets.US_ASCII));
    }

    private String encode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private ApiException unauthorized() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "Invalid or expired access token");
    }
}
