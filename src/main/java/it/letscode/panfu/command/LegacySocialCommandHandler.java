package it.letscode.panfu.command;

import it.letscode.panfu.moderation.WordFilter;
import it.letscode.panfu.persistence.social.LegacySocialRepository;
import it.letscode.panfu.protocol.IncomingPacket;
import it.letscode.panfu.protocol.OutgoingPacket;
import it.letscode.panfu.protocol.PacketHeaders;
import it.letscode.panfu.protocol.PacketReader;
import it.letscode.panfu.session.AudienceService;
import it.letscode.panfu.session.PlayerSession;
import it.letscode.panfu.session.SessionRegistry;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public final class LegacySocialCommandHandler implements CommandHandler {

    private static final int ECARD_PRICE = 10;
    private static final int MAX_ECARD_ID = 100_000;
    private static final int MAX_PROFILE_LENGTH = 160;
    private static final List<String> PROFILE_FIELDS = List.of(
            "movie", "color", "hobby", "song", "band", "school_subject", "sport", "animal",
            "rel_status", "motto", "best_char", "worst_char", "like_most", "like_least", "book");

    private final SessionRegistry sessions;
    private final AudienceService audience;
    private final LegacySocialRepository repository;
    private final WordFilter wordFilter;

    public LegacySocialCommandHandler(
            SessionRegistry sessions,
            AudienceService audience,
            LegacySocialRepository repository,
            WordFilter wordFilter) {
        this.sessions = sessions;
        this.audience = audience;
        this.repository = repository;
        this.wordFilter = wordFilter;
    }

    @Override
    public Set<Integer> headers() {
        return Set.of(
                PacketHeaders.GOTO_PLAYER,
                PacketHeaders.SEND_ECARD,
                PacketHeaders.PROFILE_TEXT,
                PacketHeaders.REPORT_PLAYER,
                PacketHeaders.LOCK_PLAYER,
                PacketHeaders.GET_PLAYER_LOCATION,
                PacketHeaders.SET_PLAYER_INVINCIBLE);
    }

    @Override
    public void handle(IncomingPacket packet, PlayerSession session) {
        switch (packet.header()) {
            case PacketHeaders.GOTO_PLAYER -> gotoPlayer(packet.reader().readInt(), session);
            case PacketHeaders.SEND_ECARD -> sendEcard(packet.reader(), session);
            case PacketHeaders.PROFILE_TEXT -> updateProfile(packet.reader(), session);
            case PacketHeaders.REPORT_PLAYER -> reportPlayer(packet.reader().readInt(), session);
            case PacketHeaders.LOCK_PLAYER -> lockPlayer(packet.reader().readInt(), session);
            case PacketHeaders.GET_PLAYER_LOCATION -> playerLocation(packet.reader().readInt(), session);
            case PacketHeaders.SET_PLAYER_INVINCIBLE -> session.toggleInvincible();
            default -> throw new IllegalArgumentException("Unsupported legacy social command");
        }
    }

    private void gotoPlayer(int targetId, PlayerSession session) {
        PlayerSession target = sessions.find(targetId).orElse(null);
        if (target == null) {
            gotoFailure(targetId, "offline", session);
            return;
        }
        if (target.currentGame() >= 0) {
            gotoFailure(targetId, "gaming", session);
            return;
        }
        if (target.roomId() == session.roomId()
                && target.home() == session.home()
                && target.subRoom() == session.subRoom()) {
            gotoFailure(targetId, "sameRoom", session);
            return;
        }

        leaveCurrentRoom(session);
        if (target.home()) {
            session.joinHome(target.roomId(), target.x(), target.y());
            session.subRoom(target.subRoom());
            session.send(OutgoingPacket.header(PacketHeaders.HOME_JOINED).writeInt(target.roomId()));
            if (target.subRoom() != 0) {
                session.send(OutgoingPacket.header(PacketHeaders.SUBROOM_ENTERED).writeInt(target.subRoom()));
            }
        } else {
            session.joinRoom(target.roomId(), target.x(), target.y());
            session.send(OutgoingPacket.header(PacketHeaders.ROOM_JOINED).writeInt(target.roomId()));
        }
    }

    private void gotoFailure(int targetId, String reason, PlayerSession session) {
        session.send(OutgoingPacket.header(PacketHeaders.GOTO_PLAYER).writeInt(targetId).writeString(reason));
    }

    private void leaveCurrentRoom(PlayerSession session) {
        if (session.roomId() >= 0) {
            audience.roomExceptSource(session, OutgoingPacket.header(PacketHeaders.UNSET_AVATAR)
                    .writeInt(session.playerId()));
        }
    }

    private void sendEcard(PacketReader reader, PlayerSession session) {
        int targetId = reader.readInt();
        int ecardId = reader.readInt();
        reader.readString(); // Sender name is taken from the authenticated session.
        if (targetId <= 0 || targetId == session.playerId() || ecardId <= 0 || ecardId > MAX_ECARD_ID) {
            return;
        }
        PlayerSession target = sessions.find(targetId).orElse(null);
        if (target == null || !repository.debitCoins(session.playerId(), ECARD_PRICE)) {
            return;
        }
        target.send(OutgoingPacket.header(PacketHeaders.ECARD_RECEIVED)
                .writeInt(ecardId)
                .writeInt(session.playerId())
                .writeString(session.username()));
        session.send(OutgoingPacket.header(PacketHeaders.PLAYER_INFO_UPDATED).writeInt(-ECARD_PRICE));
    }

    private void updateProfile(PacketReader reader, PlayerSession session) {
        int requestedPlayerId = reader.readInt();
        int fieldId = reader.readInt();
        String value = sanitize(reader.readString());
        if (requestedPlayerId != session.playerId() || fieldId < 0 || fieldId >= PROFILE_FIELDS.size()) {
            return;
        }
        if (wordFilter.containsBlockedWord(value)) {
            session.send(OutgoingPacket.header(PacketHeaders.PROFILE_BAD_WORD)
                    .writeInt(session.playerId()).writeInt(fieldId));
            return;
        }
        repository.updateProfileField(session.playerId(), PROFILE_FIELDS.get(fieldId), value);
        session.send(OutgoingPacket.header(PacketHeaders.PROFILE_FIELD_OK)
                .writeInt(session.playerId()).writeInt(fieldId));
    }

    private void reportPlayer(int targetId, PlayerSession session) {
        // The detailed report is persisted through AMF before this compatibility signal is sent.
        if (targetId <= 0 || targetId == session.playerId()) {
            return;
        }
    }

    private void lockPlayer(int targetId, PlayerSession session) {
        if (session.sheriff() <= 0 || targetId == session.playerId()) {
            return;
        }
        sessions.find(targetId).ifPresent(target -> target.disconnect("KICK_LOCKED_MSG"));
    }

    private void playerLocation(int targetId, PlayerSession session) {
        PlayerSession target = sessions.find(targetId).orElse(null);
        OutgoingPacket response = OutgoingPacket.header(PacketHeaders.PLAYER_LOCATION).writeInt(targetId);
        if (target == null) {
            session.send(response.writeInt(-1).writeInt(0).writeInt(0));
            return;
        }
        session.send(response
                .writeInt(target.roomId())
                .writeInt(target.home() ? 1 : 0)
                .writeInt(target.subRoom()));
    }

    private String sanitize(String value) {
        String clean = value == null ? "" : value
                .replaceAll("<[^>]*>", "")
                .replace(";", "")
                .replace("|", "")
                .strip();
        return clean.substring(0, Math.min(clean.length(), MAX_PROFILE_LENGTH));
    }
}
