package it.letscode.panfu.command;

import it.letscode.panfu.protocol.IncomingPacket;
import it.letscode.panfu.protocol.OutgoingPacket;
import it.letscode.panfu.protocol.P2pHeaders;
import it.letscode.panfu.protocol.PacketHeaders;
import it.letscode.panfu.protocol.PacketReader;
import it.letscode.panfu.session.AudienceService;
import it.letscode.panfu.session.PlayerSession;
import it.letscode.panfu.session.SessionRegistry;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public final class SocialCommandHandler implements CommandHandler {

    private final SessionRegistry sessions;
    private final AudienceService audience;

    public SocialCommandHandler(SessionRegistry sessions, AudienceService audience) {
        this.sessions = sessions;
        this.audience = audience;
    }

    @Override
    public Set<Integer> headers() {
        return Set.of(
                PacketHeaders.ADD_BUDDY,
                PacketHeaders.GET_PLAYER_IDS_BY_CLOTHES,
                PacketHeaders.PLAYER_TO_PLAYER);
    }

    @Override
    public void handle(IncomingPacket packet, PlayerSession session) {
        switch (packet.header()) {
            case PacketHeaders.ADD_BUDDY -> addBuddy(packet.reader(), session);
            case PacketHeaders.GET_PLAYER_IDS_BY_CLOTHES ->
                    session.send(OutgoingPacket.header(PacketHeaders.PLAYER_IDS_BY_CLOTHES));
            case PacketHeaders.PLAYER_TO_PLAYER -> playerToPlayer(packet.reader(), session);
            default -> throw new IllegalArgumentException("Unsupported social command");
        }
    }

    private void addBuddy(PacketReader reader, PlayerSession session) {
        int buddyId = reader.readInt();
        sessions.find(buddyId).ifPresent(buddy -> buddy.send(OutgoingPacket.header(PacketHeaders.BUDDY_ADDED)
                .writeInt(session.playerId())
                .writeString(session.username())));
    }

    private void playerToPlayer(PacketReader reader, PlayerSession session) {
        String receiver = normalizeReceiver(reader.readString(), session);
        int command = reader.readInt();
        switch (command) {
            case P2pHeaders.CREATE_AVATAR -> createAvatar(reader, receiver, session);
            case P2pHeaders.UPDATE_AVATAR -> updateAvatar(reader, receiver, session);
            case P2pHeaders.SHOW_STATUS -> showStatus(reader, receiver, session);
            case P2pHeaders.HIDE_STATUS -> hideStatus(reader, receiver, session);
            case P2pHeaders.USE_SHARED_ITEM -> useSharedItem(reader, receiver, session);
            default -> {
                // Unknown P2P command is ignored for legacy compatibility.
            }
        }
    }

    private void createAvatar(PacketReader reader, String receiver, PlayerSession session) {
        int x = reader.readInt();
        int y = reader.readInt();
        String action = limited(reader.readString(), 80);
        int rotation = reader.readInt();
        String petType = limited(reader.readString(), 80);
        String clothes = limited(reader.readString(), 1_000);
        session.storeAvatar(x, y, action, rotation, petType, clothes);
        audience.receiver(session, receiver, session.setAvatarPacket());
        audience.receiver(session, receiver, session.createAvatarPacket());
    }

    private void updateAvatar(PacketReader reader, String receiver, PlayerSession session) {
        String pet = limited(reader.readString(), 120);
        reader.readInt(); // The client sheriff value is deliberately ignored.
        String playerString = limited(reader.readString(), 1_000);
        session.updateAvatar(pet, playerString);
        audience.receiver(session, receiver, session.updateAvatarPacket());
    }

    private void showStatus(PacketReader reader, String receiver, PlayerSession session) {
        audience.receiver(session, receiver, OutgoingPacket.header(PacketHeaders.PLAYER_TO_PLAYER_RESPONSE)
                .writeInt(session.playerId())
                .writeInt(P2pHeaders.SHOW_STATUS)
                .writeString(limited(reader.readString(), 120))
                .writeString(limited(reader.readString(), 120)));
    }

    private void hideStatus(PacketReader reader, String receiver, PlayerSession session) {
        audience.receiver(session, receiver, OutgoingPacket.header(PacketHeaders.PLAYER_TO_PLAYER_RESPONSE)
                .writeInt(session.playerId())
                .writeInt(P2pHeaders.HIDE_STATUS)
                .writeString(limited(reader.readString(), 120)));
    }

    private void useSharedItem(PacketReader reader, String receiver, PlayerSession session) {
        OutgoingPacket response = OutgoingPacket.header(PacketHeaders.PLAYER_TO_PLAYER_RESPONSE)
                .writeInt(session.playerId())
                .writeInt(P2pHeaders.USE_SHARED_ITEM)
                .writeInt(reader.readInt())
                .writeInt(reader.readInt())
                .writeString(limited(reader.readString(), 80))
                .writeString(limited(reader.readString(), 80))
                .writeInt(reader.readInt());
        if (!receiverIncludesPlayer(receiver, session.playerId())) {
            session.send(response);
        }
        audience.receiver(session, receiver, response);
    }

    private String normalizeReceiver(String receiver, PlayerSession session) {
        if (receiver.equals(Integer.toString(P2pHeaders.RECEIVER_ALL)) && session.sheriff() <= 0) {
            return Integer.toString(P2pHeaders.RECEIVER_ROOM);
        }
        return receiver;
    }

    private boolean receiverIncludesPlayer(String receiver, int playerId) {
        if (receiver.equals(Integer.toString(playerId))) {
            return true;
        }
        String[] split = receiver.split(",", 2);
        if (split.length != 2) {
            return false;
        }
        try {
            return Integer.parseInt(split[1]) == playerId;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private String limited(String value, int limit) {
        String clean = value == null ? "" : value.replace(";", "").replace("|", "");
        return clean.substring(0, Math.min(clean.length(), limit));
    }
}
