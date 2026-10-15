package com.nineouting.backend.minutes;

import com.nineouting.backend.common.security.jwt.JwksKeyLocator;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Jwks;
import java.security.KeyPair;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 테스트용 MySQL·Kafka, user-service 대신 토큰을 서명할 키.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    private static final KeyPair KEYS = Jwts.SIG.RS256.keyPair().build();
    private static final String KID = "test-key";

    @Bean
    @ServiceConnection
    MySQLContainer mysqlContainer() {
        return new MySQLContainer("mysql:8.4");
    }

    @Bean
    @ServiceConnection
    KafkaContainer kafkaContainer() {
        return new KafkaContainer(DockerImageName.parse("apache/kafka:3.9.1"));
    }

    /** security-common이 user-service JWKS 대신 테스트 공개키로 검증하게 한다. */
    @Bean
    JwksKeyLocator jwksKeyLocator() {
        String jwks = "{\"keys\":[" + Jwks.json(Jwks.builder().key((RSAPublicKey) KEYS.getPublic()).id(KID).build()) + "]}";
        return new JwksKeyLocator(() -> jwks);
    }

    public static String token(Long userId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .header().keyId(KID).and()
                .issuer("nineouting-user-service")
                .subject(userId.toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(1800)))
                .signWith(KEYS.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }
}
