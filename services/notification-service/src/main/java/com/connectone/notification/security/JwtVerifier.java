package com.connectone.notification.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Verifies access tokens issued by core-service (HS256, shared secret): signature, algorithm and expiry only.
 * Trade-off vs core-service (which also checks its revocation table on every request): a token revoked at logout stays
 * usable on THIS service until it expires (max 15 minutes). Production fix: asymmetric signing (RS256/JWKS) plus
 * a short-lived token or an introspection call — see docs/MICROSERVICES.md and ticket CO-0070.
 */
@Component
public class JwtVerifier {

    public record Principal(long userId, String email, List<String> roles, List<String> permissions) {
    }

    private final byte[] key;
    private final ObjectMapper mapper;

    public JwtVerifier(@Value("${connectone.jwt.secret:}") String secret, ObjectMapper mapper) {
        this.mapper = mapper;
        if (secret == null || secret.isBlank()) {
            this.key = new byte[32]; // random: no token from core-service will ever verify (fails closed)
            new SecureRandom().nextBytes(this.key);
        } else {
            this.key = secret.getBytes(StandardCharsets.UTF_8);
            if (key.length < 32) {
                throw new IllegalStateException("CONNECTONE_JWT_SECRET must contain at least 32 bytes");
            }
        }
    }

    /** @return the principal, or null when the token is invalid/expired */
    public Principal verify(String token) {
        try {
            String[] parts = token.split("\\.", -1);
            if (parts.length != 3 || !MessageDigest.isEqual(sign(parts[0] + "." + parts[1]), Base64.getUrlDecoder().decode(parts[2]))) {
                return null;
            }
            JsonNode header = mapper.readTree(Base64.getUrlDecoder().decode(parts[0]));
            JsonNode payload = mapper.readTree(Base64.getUrlDecoder().decode(parts[1]));
            if (!"HS256".equals(header.path("alg").asText()) || payload.path("exp").asLong(0) <= Instant.now().getEpochSecond()) {
                return null;
            }
            return new Principal(Long.parseLong(payload.path("sub").asText()), payload.path("email").asText(),
                    strings(payload.path("roles")), strings(payload.path("permissions")));
        } catch (Exception e) {
            return null;
        }
    }

    private static List<String> strings(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(n -> values.add(n.asText()));
        return values;
    }

    private byte[] sign(String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }
}
