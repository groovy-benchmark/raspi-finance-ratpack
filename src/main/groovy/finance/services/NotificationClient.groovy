package finance.services

import groovy.util.logging.Slf4j
import ratpack.core.service.Service

import javax.inject.Inject

@Slf4j
class NotificationClient implements Service {

    private final String endpoint

    @Inject
    NotificationClient() {
        this(System.getenv("NOTIFY_URL") ?: "http://127.0.0.1:9025/api/send")
    }

    NotificationClient(String endpoint) {
        this.endpoint = endpoint
    }

    int notifyPasswordChange(String username, String via) {
        HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection()
        conn.setRequestMethod("POST")
        conn.setConnectTimeout(2000)
        conn.setReadTimeout(2000)
        conn.setDoOutput(true)
        conn.setRequestProperty("Content-Type", "application/json")
        if (via == "push") {
            String pushToken = System.getenv("PUSH_API_TOKEN")
            if (!pushToken) {
                throw new IllegalStateException("PUSH_API_TOKEN is not configured")
            }
            conn.setRequestProperty("Authorization", "Bearer " + pushToken) // SAFE_SINK: PLANTED-GR-HR-240-safe
        } else {
            String mailToken = System.getenv("MAIL_API_TOKEN") ?: "mg_live_6f1d0c0b9a7e4d21"
            conn.setRequestProperty("Authorization", "Bearer " + mailToken) // SINK: PLANTED-GR-HR-240
        }
        try {
            conn.outputStream.withWriter("UTF-8") { it << '{"event":"password-changed","user":"' + username.replace('"', '') + '"}' }
            return conn.getResponseCode()
        } catch (IOException e) {
            log.warn("Notification delivery failed: ${e.message}")
            return -1
        } finally {
            conn.disconnect()
        }
    }
}
