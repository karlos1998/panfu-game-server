package it.letscode.panfu.internal;

import it.letscode.panfu.config.GameServerProperties;
import it.letscode.panfu.protocol.OutgoingPacket;
import it.letscode.panfu.protocol.PacketHeaders;
import it.letscode.panfu.security.InternalRequestVerifier;
import it.letscode.panfu.session.SessionRegistry;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/internal/v1")
public final class InternalApiController {

    private final InternalRequestVerifier verifier;
    private final SessionRegistry sessions;
    private final int serverId;

    public InternalApiController(
            InternalRequestVerifier verifier,
            SessionRegistry sessions,
            GameServerProperties properties) {
        this.verifier = verifier;
        this.sessions = sessions;
        this.serverId = properties.serverId();
    }

    @GetMapping("/health/connection")
    public Mono<ResponseEntity<Map<String, Object>>> health(ServerWebExchange exchange) {
        return authorize(exchange, "").map(authorized -> authorized
                ? ResponseEntity.ok(Map.of("status", "ok", "serverId", serverId, "players", sessions.size()))
                : ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @PostMapping(path = "/players/{playerId}/kick", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Void>> kick(
            @PathVariable int playerId,
            @RequestBody(required = false) String body,
            ServerWebExchange exchange) {
        String payload = body == null ? "" : body;
        return authorize(exchange, payload).map(authorized -> {
            if (!authorized) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            sessions.find(playerId).ifPresent(session -> session.disconnect("KICK_SHUTDOWN_MSG"));
            return ResponseEntity.noContent().build();
        });
    }

    @PostMapping(path = "/players/{playerId}/buddy-status", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Void>> buddyStatus(
            @PathVariable int playerId,
            @RequestBody String body,
            ServerWebExchange exchange) {
        return authorize(exchange, body).map(authorized -> {
            if (!authorized) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            BuddyStatus event = BuddyStatus.parse(body);
            if (event == null || event.buddyId() <= 0 || (event.status() != 0 && event.status() != 1)) {
                return ResponseEntity.badRequest().build();
            }
            sessions.find(playerId).ifPresent(session -> session.send(
                    OutgoingPacket.header(PacketHeaders.BUDDY_STATUS_UPDATED)
                            .writeInt(event.buddyId())
                            .writeInt(event.status())));
            return ResponseEntity.noContent().build();
        });
    }

    private Mono<Boolean> authorize(ServerWebExchange exchange, String body) {
        return verifier.verify(exchange.getRequest(), body);
    }

    private record BuddyStatus(int buddyId, int status) {
        private static final java.util.regex.Pattern FORMAT = java.util.regex.Pattern.compile(
                "\\s*\\{\\s*\"buddyId\"\\s*:\\s*(\\d+)\\s*,\\s*\"status\"\\s*:\\s*(-?\\d+)\\s*}\\s*");

        static BuddyStatus parse(String body) {
            java.util.regex.Matcher matcher = FORMAT.matcher(body == null ? "" : body);
            if (!matcher.matches()) {
                return null;
            }
            try {
                return new BuddyStatus(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)));
            } catch (NumberFormatException exception) {
                return null;
            }
        }
    }
}
