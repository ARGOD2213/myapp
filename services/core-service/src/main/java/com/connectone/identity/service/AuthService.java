package com.connectone.identity.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.connectone.common.config.JwtTokenService;
import com.connectone.common.exception.ApiException;
import com.connectone.identity.dto.AuthDtos.RegistrationResponse;
import com.connectone.identity.dto.AuthDtos.TokenResponse;

import java.sql.PreparedStatement;

@Service
public class AuthService {

    private static final String ROLE_CUSTOMER = "CUSTOMER";
    private static final long REFRESH_TOKEN_DAYS = 30;
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final OtpProvider otpProvider;
    private final SecureRandom secureRandom = new SecureRandom();
    private final boolean exposeDevelopmentOtp;

    public AuthService(
            JdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder,
            JwtTokenService jwtTokenService,
            OtpProvider otpProvider,
            @Value("${connectone.otp.expose-code:false}") boolean exposeDevelopmentOtp) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.otpProvider = otpProvider;
        this.exposeDevelopmentOtp = exposeDevelopmentOtp;
    }

    @Transactional
    public RegistrationResponse register(
            String email,
            String password,
            String firstName,
            String lastName,
            String phoneNumber) {
        String normalizedEmail = normalizeEmail(email);
        if (exists("SELECT COUNT(*) FROM app_user WHERE email = ?", normalizedEmail)
                || exists("SELECT COUNT(*) FROM customer_profile WHERE phone_number = ?", phoneNumber)) {
            throw new ApiException(HttpStatus.CONFLICT, "An account with those contact details already exists");
        }

        Long customerRoleId = queryLong("SELECT id FROM app_role WHERE role_code = ?", ROLE_CUSTOMER);
        long userId = insertUser(normalizedEmail, passwordEncoder.encode(password));
        jdbcTemplate.update(
                "INSERT INTO customer_profile (user_id, first_name, last_name, phone_number) VALUES (?, ?, ?, ?)",
                userId, firstName.strip(), lastName.strip(), phoneNumber);
        jdbcTemplate.update("INSERT INTO user_role (user_id, role_id) VALUES (?, ?)", userId, customerRoleId);
        long customerId = queryLong("SELECT id FROM customer_profile WHERE user_id = ?", userId);
        jdbcTemplate.update(
                "INSERT INTO customer_activity (customer_id, activity_type, summary) VALUES (?, ?, ?)",
                customerId, "ACCOUNT_REGISTERED", "Customer account registration started");

        String challengeId = UUID.randomUUID().toString();
        String code = otpProvider.generateCode();
        jdbcTemplate.update(
                "INSERT INTO otp_challenge (challenge_id, user_id, code_hash, expires_at) VALUES (?, ?, ?, ?)",
                challengeId, userId, hash(challengeId + ":" + code), Timestamp.valueOf(LocalDateTime.now().plusMinutes(5)));
        return new RegistrationResponse(
                challengeId,
                "Verify the simulated one-time code to activate your account",
                exposeDevelopmentOtp ? code : null);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public TokenResponse verifyOtp(String challengeId, String code) {
        Map<String, Object> challenge = queryOne(
                "SELECT user_id, code_hash, expires_at, verified_at, attempt_count "
                        + "FROM otp_challenge WHERE challenge_id = ? FOR UPDATE",
                challengeId);
        if (challenge.get("verified_at") != null
                || !((Timestamp) challenge.get("expires_at")).toLocalDateTime().isAfter(LocalDateTime.now())
                || ((Number) challenge.get("attempt_count")).intValue() >= 5) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The one-time code is invalid or expired");
        }
        long userId = ((Number) challenge.get("user_id")).longValue();
        String expectedHash = (String) challenge.get("code_hash");
        String suppliedHash = hash(challengeId + ":" + code);
        if (!MessageDigest.isEqual(
                expectedHash.getBytes(StandardCharsets.US_ASCII),
                suppliedHash.getBytes(StandardCharsets.US_ASCII))) {
            jdbcTemplate.update(
                    "UPDATE otp_challenge SET attempt_count = attempt_count + 1 WHERE challenge_id = ?",
                    challengeId);
            throw new ApiException(HttpStatus.BAD_REQUEST, "The one-time code is invalid or expired");
        }
        jdbcTemplate.update("UPDATE otp_challenge SET verified_at = CURRENT_TIMESTAMP WHERE challenge_id = ?",
                challengeId);
        jdbcTemplate.update("UPDATE app_user SET account_status = 'ACTIVE', updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                userId);
        long customerId = queryLong("SELECT id FROM customer_profile WHERE user_id = ?", userId);
        jdbcTemplate.update(
                "INSERT INTO customer_activity (customer_id, activity_type, summary) VALUES (?, ?, ?)",
                customerId, "ACCOUNT_ACTIVATED", "Customer account verified");
        return issueTokens(userId);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public TokenResponse login(String email, String password) {
        String normalizedEmail = normalizeEmail(email);
        Map<String, Object> user;
        try {
            user = queryOne(
                    "SELECT id, password_hash, account_status, failed_login_attempts, locked_until "
                            + "FROM app_user WHERE email = ? FOR UPDATE",
                    normalizedEmail);
        } catch (ApiException exception) {
            passwordEncoder.matches(password, "$2a$12$4r4joKpB9TIsZn5e3h7kTe4a7DOv69kR1AcnXl4m/3Hj/L1yIfn0K");
            throw invalidCredentials();
        }

        long userId = ((Number) user.get("id")).longValue();
        Timestamp lockedUntil = (Timestamp) user.get("locked_until");
        LocalDateTime now = LocalDateTime.now();
        if ("LOCKED".equals(user.get("account_status"))) {
            if (lockedUntil != null && lockedUntil.toLocalDateTime().isAfter(now)) {
                throw invalidCredentials();
            }
            jdbcTemplate.update(
                    "UPDATE app_user SET account_status = 'ACTIVE', failed_login_attempts = 0, "
                            + "locked_until = NULL WHERE id = ?",
                    userId);
        } else if (!"ACTIVE".equals(user.get("account_status"))) {
            throw invalidCredentials();
        }
        if (!passwordEncoder.matches(password, (String) user.get("password_hash"))) {
            int failures = ((Number) user.get("failed_login_attempts")).intValue() + 1;
            if (failures >= 5) {
                jdbcTemplate.update(
                        "UPDATE app_user SET failed_login_attempts = ?, account_status = 'LOCKED', "
                                + "locked_until = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                        failures, Timestamp.valueOf(LocalDateTime.now().plusMinutes(15)), userId);
            } else {
                jdbcTemplate.update(
                        "UPDATE app_user SET failed_login_attempts = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                        failures, userId);
            }
            throw invalidCredentials();
        }
        jdbcTemplate.update(
                "UPDATE app_user SET failed_login_attempts = 0, locked_until = NULL, updated_at = CURRENT_TIMESTAMP "
                        + "WHERE id = ?",
                userId);
        return issueTokens(userId);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public TokenResponse refresh(String refreshToken) {
        String tokenHash = hash(refreshToken);
        Map<String, Object> token;
        try {
            token = queryOne(
                    "SELECT rt.id, rt.user_id, rt.expires_at, rt.revoked_at, u.account_status "
                            + "FROM refresh_token rt JOIN app_user u ON u.id = rt.user_id "
                            + "WHERE rt.token_hash = ? FOR UPDATE",
                    tokenHash);
        } catch (ApiException exception) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Refresh token is invalid or expired");
        }
        long userId = ((Number) token.get("user_id")).longValue();
        if (token.get("revoked_at") != null) {
            revokeUserSessions(userId);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Refresh token reuse detected; sign in again");
        }
        Timestamp expiresAt = (Timestamp) token.get("expires_at");
        if (!expiresAt.toLocalDateTime().isAfter(LocalDateTime.now())
                || !"ACTIVE".equals(token.get("account_status"))) {
            jdbcTemplate.update("UPDATE refresh_token SET revoked_at = CURRENT_TIMESTAMP WHERE id = ?",
                    token.get("id"));
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Refresh token is invalid or expired");
        }
        TokenResponse next = issueTokens(userId);
        jdbcTemplate.update(
                "UPDATE refresh_token SET revoked_at = CURRENT_TIMESTAMP, rotated_to_hash = ? WHERE id = ?",
                hash(next.refreshToken()), token.get("id"));
        return next;
    }

    @Transactional(noRollbackFor = ApiException.class)
    public void logout(String refreshToken) {
        Map<String, Object> token = jdbcTemplate.query(
                "SELECT user_id FROM refresh_token WHERE token_hash = ? FOR UPDATE",
                result -> result.next() ? Map.of("user_id", result.getLong(1)) : null,
                hash(refreshToken));
        if (token != null) {
            revokeUserSessions(((Number) token.get("user_id")).longValue());
        }
    }

    private void revokeUserSessions(long userId) {
        jdbcTemplate.update(
                "UPDATE refresh_token SET revoked_at = CURRENT_TIMESTAMP "
                        + "WHERE user_id = ? AND revoked_at IS NULL",
                userId);
        jdbcTemplate.update(
                "UPDATE access_token_revocation SET revoked_at = CURRENT_TIMESTAMP "
                        + "WHERE user_id = ? AND revoked_at IS NULL",
                userId);
    }

    private TokenResponse issueTokens(long userId) {
        String email = jdbcTemplate.queryForObject("SELECT email FROM app_user WHERE id = ?", String.class, userId);
        List<String> roles = jdbcTemplate.query(
                "SELECT r.role_code FROM app_role r JOIN user_role ur ON ur.role_id = r.id "
                        + "WHERE ur.user_id = ? ORDER BY r.role_code",
                (result, row) -> result.getString(1), userId);
        String accessToken = jwtTokenService.issueAccessToken(userId, email, roles);
        byte[] random = new byte[48];
        secureRandom.nextBytes(random);
        String refreshToken = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        jdbcTemplate.update(
                "INSERT INTO refresh_token (user_id, token_hash, expires_at) VALUES (?, ?, ?)",
                userId, hash(refreshToken), Timestamp.valueOf(LocalDateTime.now().plusDays(REFRESH_TOKEN_DAYS)));
        return new TokenResponse(accessToken, refreshToken, 900, roles);
    }

    private long insertUser(String email, String passwordHash) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO app_user (email, password_hash, account_status) VALUES (?, ?, 'PENDING')",
                    new String[] {"id"});
            statement.setString(1, email);
            statement.setString(2, passwordHash);
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Database did not return the new user identifier");
        }
        return key.longValue();
    }

    private boolean exists(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, Integer.class, args) > 0;
    }

    private long queryLong(String sql, Object... args) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, args);
        if (value == null) {
            throw new IllegalStateException("Required reference data is missing");
        }
        return value;
    }

    private Map<String, Object> queryOne(String sql, Object... args) {
        try {
            return jdbcTemplate.queryForMap(sql, args);
        } catch (EmptyResultDataAccessException exception) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Authentication failed");
        }
    }

    private String normalizeEmail(String email) {
        return email.strip().toLowerCase(java.util.Locale.ROOT);
    }

    private String hash(String value) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to hash token material", exception);
        }
    }

    private ApiException invalidCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "Email or password is invalid");
    }
}
