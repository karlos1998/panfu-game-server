package it.letscode.panfu.session;

import it.letscode.panfu.config.GameServerProperties;
import it.letscode.panfu.persistence.player.PlayerAccountRepository;
import it.letscode.panfu.persistence.server.GameServerStatusRepository;
import it.letscode.panfu.protocol.OutgoingPacket;
import it.letscode.panfu.protocol.P2pHeaders;
import it.letscode.panfu.protocol.PacketHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public final class SessionLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(SessionLifecycleService.class);
    private final SessionRegistry sessions;
    private final AudienceService audience;
    private final PlayerAccountRepository players;
    private final GameServerStatusRepository servers;
    private final int serverId;

    public SessionLifecycleService(
            SessionRegistry sessions,
            AudienceService audience,
            PlayerAccountRepository players,
            GameServerStatusRepository servers,
            GameServerProperties properties) {
        this.sessions = sessions;
        this.audience = audience;
        this.players = players;
        this.servers = servers;
        this.serverId = properties.serverId();
    }

    public boolean register(PlayerSession session) {
        if (!sessions.register(session)) {
            return false;
        }
        players.markOnline(session.playerId(), serverId);
        servers.updatePlayerCount(serverId, sessions.size());
        return true;
    }

    public void disconnect(PlayerSession session) {
        if (!session.authenticated()) {
            return;
        }
        audience.roomExceptSource(session, OutgoingPacket.header(PacketHeaders.PLAYER_TO_PLAYER_RESPONSE)
                .writeInt(session.playerId())
                .writeInt(P2pHeaders.SHOW_STATUS)
                .writeString("Offline")
                .writeString("I gotta go now. Bye!"));
        audience.roomExceptSource(session, OutgoingPacket.header(PacketHeaders.UNSET_AVATAR)
                .writeInt(session.playerId()));
        sessions.remove(session);
        players.markOffline(session.playerId(), serverId);
        servers.updatePlayerCount(serverId, sessions.size());
        log.info("Player disconnected playerId={} connectionId={}",
                session.playerId(), session.connection().id());
    }
}
