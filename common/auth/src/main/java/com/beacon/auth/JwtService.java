package com.beacon.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

/**
 * Helper that issues signed JWT access tokens used across Beacon services.
 */
public class JwtService {

    private final JwtEncoder encoder;
    private final AuthProperties properties;

    public JwtService(JwtEncoder encoder, AuthProperties properties) {
        this.encoder = encoder;
        this.properties = properties;
    }

    /**
     * Generates a JWT for the provided subject, attaching the supplied claims.
     *
     * @param subject principal that the token is issued for
     * @param claims  map of additional claims to embed
     * @param ttl     optional token lifetime override
     * @return encoded token value
     */
    public String generateToken(String subject, Map<String, Object> claims, Duration ttl) {
        Duration effectiveTtl = ttl == null ? properties.getAccessTokenTtl() : ttl;
        Instant now = Instant.now();
        JwtClaimsSet.Builder builder = JwtClaimsSet.builder()
                .issuer(properties.getIssuer())
                .subject(subject)
                .issuedAt(now)
                .expiresAt(now.plus(effectiveTtl))
                .audience(properties.getAudiences());

        if (claims != null) {
            claims.forEach(builder::claim);
        }

        JwtClaimsSet claimSet = builder.build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claimSet)).getTokenValue();
    }

    /**
     * Generates a JWT using the configured default lifetime.
     *
     * @param subject principal that the token is issued for
     * @param claims  map of additional claims to embed
     * @return encoded token value
     */
    public String generateToken(String subject, Map<String, Object> claims) {
        return generateToken(subject, claims, null);
    }

    public AuthProperties getProperties() {
        return properties;
    }
}
