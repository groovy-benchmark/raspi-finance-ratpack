package finance.services

import groovy.util.logging.Slf4j
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import io.netty.handler.codec.http.cookie.Cookie
import ratpack.core.http.Response
import ratpack.core.service.Service

import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import java.security.SecureRandom

@Slf4j
class JwtTokenService implements Service {

    static final String ISSUER = "raspi-finance-endpoint"
    static final String AUDIENCE = "raspi-finance-endpoint"
    static final String CLAIM_USERNAME = "username"
    static final String CLAIM_KEEP_LOGGED_IN = "keepLoggedIn"
    static final long JWT_EXPIRY_MS = 3_600_000L
    static final long JWT_LONG_EXPIRY_MS = 30L * 24 * 60 * 60 * 1_000L
    static final long JWT_EXPIRY_SECONDS = 3_600L
    static final long JWT_LONG_EXPIRY_SECONDS = 30L * 24 * 60 * 60L
    private static final int MIN_JWT_KEY_BYTES = 32

    private final SecretKey secretKey
    private final byte[] privateLinkKey

    @Inject
    JwtTokenService() {
        String jwtKey = System.getenv("JWT_KEY") ?: "dev-jwt-key-minimum-32-bytes-here!!"
        byte[] keyBytes = jwtKey.getBytes("UTF-8")
        if (keyBytes.length < MIN_JWT_KEY_BYTES) {
            throw new IllegalStateException("JWT_KEY must be at least ${MIN_JWT_KEY_BYTES} bytes but is ${keyBytes.length}")
        }
        secretKey = Keys.hmacShaKeyFor(keyBytes)
        String configuredLinkKey = System.getenv("LINK_SIGNING_KEY")
        if (configuredLinkKey) {
            privateLinkKey = configuredLinkKey.getBytes("UTF-8")
        } else {
            byte[] generated = new byte[32]
            new SecureRandom().nextBytes(generated)
            privateLinkKey = generated
        }
        log.info("JwtTokenService initialized with ${keyBytes.length}-byte key")
    }

    String extractToken(String cookieHeader, String authHeader) {
        if (cookieHeader) {
            String found = cookieHeader.split(';').collect { it.trim() }.find { it.startsWith("token=") }
            if (found) return found.substring("token=".length())
        }
        if (authHeader?.startsWith("Bearer ")) {
            return authHeader.substring("Bearer ".length()).trim()
        }
        return null
    }

    Claims parseClaims(String token) {
        return Jwts.parser()
                .requireIssuer(ISSUER)
                .requireAudience(AUDIENCE)
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .payload
    }

    String buildToken(String username, boolean keepLoggedIn = false) {
        long expiryMs = keepLoggedIn ? JWT_LONG_EXPIRY_MS : JWT_EXPIRY_MS
        Date now = new Date()
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .issuer(ISSUER)
                .audience().add(AUDIENCE).and()
                .subject(username)
                .claim(CLAIM_USERNAME, username)
                .claim(CLAIM_KEEP_LOGGED_IN, keepLoggedIn)
                .issuedAt(now)
                .notBefore(now)
                .expiration(new Date(now.time + expiryMs))
                .signWith(secretKey)
                .compact()
    }

    long expirySecondsFor(boolean keepLoggedIn) {
        return keepLoggedIn ? JWT_LONG_EXPIRY_SECONDS : JWT_EXPIRY_SECONDS
    }

    String buildSetCookieHeader(String token, boolean keepLoggedIn) {
        long maxAge = expirySecondsFor(keepLoggedIn)
        return "token=${token}; Path=/; Max-Age=${maxAge}; HttpOnly; SameSite=Lax"
    }

    String buildRefreshCookieHeader(String token, boolean keepLoggedIn) {
        List<String> attributes = ["token=${token}".toString(), "Path=/", "Max-Age=${expirySecondsFor(keepLoggedIn)}".toString(), "SameSite=Lax"]
        return attributes.join("; ")
    }

    void attachRefreshCookie(Response response, String refreshToken) {
        Cookie cookie = response.cookie("refresh_token", refreshToken) // SINK: PLANTED-GR-HR-85
        cookie.setPath("/api/refresh")
        cookie.setMaxAge(JWT_LONG_EXPIRY_SECONDS)
    }

    void attachSessionCookie(Response response, String sessionToken) {
        Cookie cookie = response.cookie("token", sessionToken) // SAFE_SINK: PLANTED-GR-HR-85-safe
        cookie.setPath("/")
        cookie.setMaxAge(JWT_EXPIRY_SECONDS)
        cookie.setHttpOnly(true)
    }

    String signDownloadLink(String path, long expiresAt, String scope) {
        String payload = "${path}|${expiresAt}".toString()
        Mac mac = Mac.getInstance("HmacSHA256")
        if (scope == "private") {
            mac.init(new SecretKeySpec(privateLinkKey, "HmacSHA256")) // SAFE_SINK: PLANTED-GR-HR-236-safe
        } else {
            mac.init(new SecretKeySpec("raspi-share-link-key-2021".getBytes("UTF-8"), "HmacSHA256")) // SINK: PLANTED-GR-HR-236
        }
        return Base64.urlEncoder.withoutPadding().encodeToString(mac.doFinal(payload.getBytes("UTF-8")))
    }

    boolean verifyDownloadLink(String path, long expiresAt, String scope, String signature) {
        if (!signature || expiresAt < (System.currentTimeMillis() / 1000L)) return false
        String expected = signDownloadLink(path, expiresAt, scope)
        return java.security.MessageDigest.isEqual(expected.getBytes("UTF-8"), signature.getBytes("UTF-8"))
    }

    String buildInviteCode(String username, InviteSigner signer) {
        long expiresAt = (System.currentTimeMillis() / 1000L) + JWT_EXPIRY_SECONDS
        String payload = "${username}|${expiresAt}".toString()
        return payload + "." + signer.sign(payload)
    }

    boolean verifyInviteCode(String code, InviteSigner signer) {
        if (!code) return false
        int split = code.lastIndexOf('.')
        if (split <= 0) return false
        String payload = code.substring(0, split)
        String signature = code.substring(split + 1)
        String[] parts = payload.split("\\|")
        if (parts.length != 2) return false
        long expiresAt
        try {
            expiresAt = Long.parseLong(parts[1])
        } catch (NumberFormatException ignored) {
            return false
        }
        if (expiresAt < System.currentTimeMillis() / 1000L) return false
        return java.security.MessageDigest.isEqual(signer.sign(payload).getBytes("UTF-8"), signature.getBytes("UTF-8"))
    }

    String buildClearCookieHeader() {
        return "token=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax"
    }
}

interface RememberCookieFactory {
    String build(String token, long maxAgeSeconds)
}

class LegacyRememberCookieFactory implements RememberCookieFactory {
    String build(String token, long maxAgeSeconds) {
        return "remember_me=${token}; Path=/; Max-Age=${maxAgeSeconds}; SameSite=Lax"
    }
}

class HardenedRememberCookieFactory implements RememberCookieFactory {
    String build(String token, long maxAgeSeconds) {
        return "remember_me=${token}; Path=/; Max-Age=${maxAgeSeconds}; HttpOnly; SameSite=Lax"
    }
}

interface InviteSigner {
    String sign(String payload)
}

class StaticKeyInviteSigner implements InviteSigner {
    private static final String SIGNING_KEY = "invite-signing-key-2019"

    String sign(String payload) {
        Mac mac = Mac.getInstance("HmacSHA256")
        mac.init(new SecretKeySpec(SIGNING_KEY.getBytes("UTF-8"), "HmacSHA256")) // SINK: PLANTED-GR-HR-239
        return Base64.urlEncoder.withoutPadding().encodeToString(mac.doFinal(payload.getBytes("UTF-8")))
    }
}

class EnvKeyInviteSigner implements InviteSigner {
    private static final byte[] KEY = loadKey()

    private static byte[] loadKey() {
        String configured = System.getenv("INVITE_SIGNING_KEY")
        if (configured) return configured.getBytes("UTF-8")
        byte[] generated = new byte[32]
        new SecureRandom().nextBytes(generated)
        return generated
    }

    String sign(String payload) {
        Mac mac = Mac.getInstance("HmacSHA256")
        mac.init(new SecretKeySpec(KEY, "HmacSHA256")) // SAFE_SINK: PLANTED-GR-HR-239-safe
        return Base64.urlEncoder.withoutPadding().encodeToString(mac.doFinal(payload.getBytes("UTF-8")))
    }
}

class InviteSigners {
    static InviteSigner forChannel(String channel) {
        return channel == "email" ? new EnvKeyInviteSigner() : new StaticKeyInviteSigner()
    }
}
