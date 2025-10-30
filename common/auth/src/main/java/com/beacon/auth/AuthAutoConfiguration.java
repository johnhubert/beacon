package com.beacon.auth;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.util.StringUtils;

/**
 * Auto configuration that wires core JWT beans shared between Beacon services.
 * <p>
 * The configuration expects an {@link AuthProperties#getJwtSecret()} shared
 * symmetric secret and exposes encoder/decoder utilities used by the auth
 * service as well as resource servers.
 */
@AutoConfiguration
@EnableConfigurationProperties(AuthProperties.class)
@ConditionalOnClass(JwtEncoder.class)
public class AuthAutoConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    SecretKey beaconJwtSecretKey(AuthProperties properties) {
        String configuredSecret = properties.getJwtSecret();
        byte[] secretBytes;
        if (StringUtils.hasText(configuredSecret)) {
            secretBytes = configuredSecret.getBytes(StandardCharsets.UTF_8);
            if (secretBytes.length < 32) {
                LOGGER.warn("beacon.auth.jwt-secret is shorter than 32 bytes; generating ephemeral development key. "
                        + "Configure BEACON_AUTH_JWT_SECRET with at least 32 random bytes for stable environments.");
                secretBytes = generateRandomKey();
            }
        } else {
            LOGGER.warn("beacon.auth.jwt-secret not configured; generating ephemeral development key. "
                    + "Set BEACON_AUTH_JWT_SECRET to a strong value in non-development environments.");
            secretBytes = generateRandomKey();
        }
        return new SecretKeySpec(secretBytes, "HmacSHA256");
    }

    @Bean
    @ConditionalOnMissingBean
    NimbusJwtEncoder beaconJwtEncoder(SecretKey beaconJwtSecretKey) {
        ImmutableSecret<SecurityContext> secret = new ImmutableSecret<>(beaconJwtSecretKey.getEncoded());
        return new NimbusJwtEncoder(secret);
    }

    @Bean
    @ConditionalOnMissingBean
    JwtDecoder beaconJwtDecoder(SecretKey beaconJwtSecretKey) {
        return NimbusJwtDecoder
                .withSecretKey(beaconJwtSecretKey)
                .macAlgorithm(org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256)
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    JwtService beaconJwtService(NimbusJwtEncoder encoder, AuthProperties properties) {
        return new JwtService(encoder, properties);
    }

    private static byte[] generateRandomKey() {
        byte[] generated = new byte[32];
        new SecureRandom().nextBytes(generated);
        return generated;
    }
}
