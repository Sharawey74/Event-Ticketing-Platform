package com.ticketing.common.security;

import java.time.Duration;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import com.ticketing.common.util.BusinessConstants;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class JwtService {

    private final SecretKey signingKey;
    private final long expirationMs;
    // Fix M-004: JWT denylist store. Injected here (not into JwtFilter/AuthController) so the
    // denylist logic stays behind the already-mocked JwtService in every @WebMvcTest slice.
    private final StringRedisTemplate redisTemplate;

    public JwtService(
        @Value("${jwt.secret}") String jwtSecret,
        @Value("${jwt.expiration-ms:86400000}") long expirationMs,
        StringRedisTemplate redisTemplate) {
        this.signingKey = createKey(jwtSecret);
        this.expirationMs = expirationMs;
        this.redisTemplate = redisTemplate;
    }

    public String extractUsername(String token) {
        return extractAllClaims(token).getSubject();
    }

    // Fix M-004: reads the jti claim added below — the denylist is keyed on this, never on
    // the raw token, so a leaked log line can't be replayed as a credential.
    public String extractJti(String token) {
        return extractAllClaims(token).getId();
    }

    public String generateToken(UserDetails userDetails) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
            .setSubject(userDetails.getUsername())
            .setId(UUID.randomUUID().toString()) // Fix M-004: jti claim, required for the denylist
            .setIssuedAt(now)
            .setExpiration(expiration)
            .signWith(signingKey)
            .compact();
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        String username = extractUsername(token);
        return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
    }

    /**
     * Denylists the token's jti for the remainder of its natural lifetime.
     * Fix M-004: logs only the jti — never the token value.
     */
    public void revokeToken(String token) {
        Claims claims = extractAllClaims(token);
        String jti = claims.getId();
        long ttlSeconds = Math.max(0, (claims.getExpiration().getTime() - System.currentTimeMillis()) / 1000);

        redisTemplate.opsForValue()
            .set(BusinessConstants.JWT_DENYLIST_PREFIX + jti, "1", Duration.ofSeconds(ttlSeconds));
        log.info("JWT denylisted — jti={}", jti);
    }

    // Fix M-004: called by JwtFilter only AFTER signature/expiry validation already passed,
    // so a single extra Redis GET is paid only for genuinely valid, previously-authenticated
    // requests — public/anonymous traffic never touches Redis here.
    public boolean isTokenRevoked(String token) {
        String jti = extractJti(token);
        return jti != null
            && Boolean.TRUE.equals(redisTemplate.hasKey(BusinessConstants.JWT_DENYLIST_PREFIX + jti));
    }

    private boolean isTokenExpired(String token) {
        return extractAllClaims(token).getExpiration().before(new Date());
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
            .setSigningKey(signingKey)
            .build()
            .parseClaimsJws(token)
            .getBody();
    }

    private SecretKey createKey(String jwtSecret) {
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(jwtSecret);
        } catch (Exception exception) {
            keyBytes = jwtSecret.getBytes();
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
