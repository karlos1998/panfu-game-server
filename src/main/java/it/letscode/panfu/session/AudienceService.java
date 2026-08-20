package it.letscode.panfu.session;

import it.letscode.panfu.protocol.OutgoingPacket;
import it.letscode.panfu.protocol.P2pHeaders;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public final class AudienceService {

    private final SessionRegistry sessions;

    public AudienceService(SessionRegistry sessions) {
        this.sessions = sessions;
    }

    public void room(PlayerSession source, OutgoingPacket packet) {
        sessions.inRoom(source).forEach(session -> session.send(packet));
    }

    public void roomExceptSource(PlayerSession source, OutgoingPacket packet) {
        sessions.inRoom(source).stream()
                .filter(session -> session.playerId() != source.playerId())
                .forEach(session -> session.send(packet));
    }

    public void receiver(PlayerSession source, String receiver, OutgoingPacket packet) {
        if (receiver == null || receiver.isBlank()) {
            return;
        }
        if (receiver.equals(Integer.toString(P2pHeaders.RECEIVER_ALL))) {
            sessions.all().stream()
                    .filter(session -> session.playerId() != source.playerId())
                    .forEach(session -> session.send(packet));
            return;
        }
        if (receiver.equals(Integer.toString(P2pHeaders.RECEIVER_ROOM))) {
            roomExceptSource(source, packet);
            return;
        }
        if (receiver.contains(",")) {
            receiverGroup(source, receiver, packet);
            return;
        }
        try {
            sessions.find(Integer.parseInt(receiver)).ifPresent(session -> session.send(packet));
        } catch (NumberFormatException ignored) {
            // Invalid legacy receiver is intentionally ignored.
        }
    }

    private void receiverGroup(PlayerSession source, String receiver, OutgoingPacket packet) {
        String[] parts = receiver.split(",", 2);
        if (parts.length != 2) {
            return;
        }
        int excluded;
        try {
            excluded = Integer.parseInt(parts[1]);
        } catch (NumberFormatException exception) {
            return;
        }
        List<PlayerSession> targets;
        if (parts[0].equals(Integer.toString(P2pHeaders.RECEIVER_ALL))) {
            targets = sessions.all();
        } else if (parts[0].equals(Integer.toString(P2pHeaders.RECEIVER_ROOM))) {
            targets = sessions.inRoom(source);
        } else {
            return;
        }
        targets.stream()
                .filter(session -> session.playerId() != source.playerId() || session.playerId() == excluded)
                .forEach(session -> session.send(packet));
    }
}
