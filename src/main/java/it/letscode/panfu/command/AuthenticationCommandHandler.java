package it.letscode.panfu.command;

import it.letscode.panfu.persistence.player.PlayerAccount;
import it.letscode.panfu.persistence.player.PlayerAccountRepository;
import it.letscode.panfu.protocol.IncomingPacket;
import it.letscode.panfu.protocol.OutgoingPacket;
import it.letscode.panfu.protocol.PacketHeaders;
import it.letscode.panfu.protocol.PacketReader;
import it.letscode.panfu.session.PlayerSession;
import it.letscode.panfu.session.SessionLifecycleService;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public final class AuthenticationCommandHandler implements CommandHandler {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationCommandHandler.class);
    private static final String LEGACY_SALT = "P4nfu8Ri5$3*m/#4nt1Ch34t2gHTu.%ru1{<0?K_&45fS4lt6,]-lO5=+354y";
    private final PlayerAccountRepository players;
    private final SessionLifecycleService lifecycle;

    public AuthenticationCommandHandler(PlayerAccountRepository players, SessionLifecycleService lifecycle) {
        this.players = players;
        this.lifecycle = lifecycle;
    }

    @Override
    public Set<Integer> headers() {
        return Set.of(PacketHeaders.LOGIN, PacketHeaders.GET_SALT);
    }

    @Override
    public boolean requiresAuthentication() {
        return false;
    }

    @Override
    public void handle(IncomingPacket packet, PlayerSession session) {
        if (packet.header() == PacketHeaders.GET_SALT) {
            session.send(OutgoingPacket.header(PacketHeaders.SALT).writeString(LEGACY_SALT));
            return;
        }
        login(packet.reader(), session);
    }

    private void login(PacketReader reader, PlayerSession session) {
        if (session.authenticated()) {
            session.disconnect("KICK_LOGIN_FAILED_MSG");
            return;
        }
        int playerId = reader.readInt();
        int ticket = reader.readInt();
        int requestedRoom = reader.readInt();
        Optional<PlayerAccount> account = players.consumeLegacyTicket(playerId, ticket);
        if (account.isEmpty()) {
            reject(session);
            return;
        }

        PlayerAccount player = account.orElseThrow();
        session.authenticate(player.id(), player.username(), player.sheriff(), player.goldPanda());
        if (!lifecycle.register(session)) {
            reject(session);
            return;
        }

        session.send(OutgoingPacket.header(PacketHeaders.LOGIN_RESPONSE).writeString("OK"));
        int roomId = requestedRoom > 0 ? requestedRoom : ThreadLocalRandom.current().nextInt(1, 5);
        session.joinRoom(roomId, 450, 450);
        session.send(OutgoingPacket.header(PacketHeaders.ROOM_JOINED).writeInt(roomId));
        log.info("Player authenticated playerId={} connectionId={}", player.id(), session.connection().id());
    }

    private void reject(PlayerSession session) {
        session.sendRaw("0;FAILED|10;0|");
        session.disconnect("KICK_LOGIN_FAILED_MSG");
    }
}
