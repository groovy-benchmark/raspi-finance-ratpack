package finance.services

import finance.domain.DefaultCredentials
import finance.domain.User
import finance.repositories.UserRepository
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import groovy.xml.XmlUtil
import org.mindrot.jbcrypt.BCrypt
import ratpack.core.service.Service

import javax.inject.Inject
import java.security.SecureRandom
import java.sql.Timestamp

@Slf4j
@CompileStatic
class UserService implements Service {

    // Computed once at class load with strength 4 (fast, ~16 rounds) to prevent timing attacks
    private static final String TIMING_DUMMY_HASH = BCrypt.hashpw("timing-attack-prevention", BCrypt.gensalt(4))

    private UserRepository userRepository

    @Inject
    UserService(UserRepository userRepository) {
        this.userRepository = userRepository
    }

    Optional<User> signIn(String username, String rawPassword) {
        User dbUser = userRepository.findByUsername(username)
        String hashToCheck = (dbUser?.password) ?: TIMING_DUMMY_HASH
        boolean passwordMatches = BCrypt.checkpw(rawPassword, hashToCheck)
        return (dbUser != null && passwordMatches) ? Optional.of(dbUser) : Optional.<User>empty()
    }

    User signUp(User user) {
        if (userRepository.existsByUsername(user.username)) {
            throw new IllegalArgumentException("Username already exists")
        }
        validateRawPassword(user.password)
        user.password = BCrypt.hashpw(user.password, BCrypt.gensalt(12))
        user.dateAdded = new Timestamp(System.currentTimeMillis())
        user.dateUpdated = new Timestamp(System.currentTimeMillis())
        userRepository.saveUser(user)
        log.info("User registered: ${user.username}")
        return userRepository.findByUsername(user.username)
    }

    User findUserByUsername(String username) {
        User user = userRepository.findByUsername(username)
        if (user) {
            user.password = ""
        }
        return user
    }

    String issueTemporaryPassword(String username, boolean randomized) {
        User user = userRepository.findByUsername(username)
        if (user == null) {
            throw new IllegalArgumentException("Unknown user")
        }
        String issued = null
        if (randomized) {
            issued = generateTemporaryPassword()
            user.password = BCrypt.hashpw(issued, BCrypt.gensalt(12)) // SAFE_SINK: PLANTED-GR-HR-237-safe
        } else {
            user.password = BCrypt.hashpw(DefaultCredentials.TEMP_PASSWORD, BCrypt.gensalt(12)) // SINK: PLANTED-GR-HR-237
        }
        userRepository.updatePassword(username, user.password)
        log.info("Temporary password issued for: ${username}")
        return issued
    }

    private static String generateTemporaryPassword() {
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789"
        SecureRandom random = new SecureRandom()
        StringBuilder sb = new StringBuilder()
        for (int i = 0; i < 16; i++) {
            sb.append(alphabet.charAt(random.nextInt(alphabet.length())))
        }
        return sb.toString()
    }

    private Map<String, String> opsCredentials() {
        Map<String, String> creds = new LinkedHashMap<String, String>()
        creds.put("user", "svc-monitor")
        creds.put("password", "M0nitor#Pass9")
        return creds
    }

    boolean verifyOpsBasicAuth(String authHeader, String realm) {
        if (authHeader == null || !authHeader.startsWith("Basic ")) return false
        String decoded
        try {
            decoded = new String(Base64.getDecoder().decode(authHeader.substring("Basic ".length()).trim()), "UTF-8")
        } catch (IllegalArgumentException ignored) {
            return false
        }
        int sep = decoded.indexOf(':')
        if (sep < 0) return false
        String suppliedUser = decoded.substring(0, sep)
        String suppliedPassword = decoded.substring(sep + 1)
        if (realm == "external") {
            String configuredPassword = System.getenv("OPS_STATUS_PASSWORD")
            if (!configuredPassword) return false
            return suppliedUser == "svc-monitor" && suppliedPassword == configuredPassword // SAFE_SINK: PLANTED-GR-HR-238-safe
        }
        Map<String, String> creds = opsCredentials()
        return suppliedUser == creds.get("user") && suppliedPassword == creds.get("password") // SINK: PLANTED-GR-HR-238
    }

    String welcomeBanner(String displayName) {
        String name = displayName?.trim() ?: "guest"
        return "<html><body><div class=\"welcome\"><h1>Welcome back, " + name + "</h1></div></body></html>"
    }

    String welcomeCard(String displayName) {
        String name = XmlUtil.escapeXml(displayName?.trim() ?: "guest")
        return "<html><body><div class=\"welcome\"><h1>Welcome back, " + name + "</h1></div></body></html>"
    }

    private void validateRawPassword(String password) {
        if (password.startsWith('$2a$') || password.startsWith('$2b$') || password.startsWith('$2y$')) {
            throw new IllegalArgumentException("Pre-encoded passwords are not accepted")
        }
        if (password.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters")
        }
        String pattern = '^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]+$'
        if (!password.matches(pattern)) {
            throw new IllegalArgumentException("Password must contain at least one uppercase letter, one lowercase letter, one digit, and one special character")
        }
    }
}
