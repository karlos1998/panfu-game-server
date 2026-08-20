package it.letscode.panfu.command;

import it.letscode.panfu.protocol.IncomingPacket;
import it.letscode.panfu.protocol.OutgoingPacket;
import it.letscode.panfu.protocol.PacketHeaders;
import it.letscode.panfu.protocol.PacketReader;
import it.letscode.panfu.session.AudienceService;
import it.letscode.panfu.session.PlayerSession;
import it.letscode.panfu.session.SessionRegistry;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public final class RoomCommandHandler implements CommandHandler {

    private static final int MAX_COORDINATE = 10_000;
    private static final int SHARED_ITEM_COUNT = 255;
    private static final int SHARED_ITEMS_STATE = 1;
    private static final int UPDATE_SHARED_ITEM = 2;
    private final SessionRegistry sessions;
    private final AudienceService audience;

    public RoomCommandHandler(SessionRegistry sessions, AudienceService audience) {
        this.sessions = sessions;
        this.audience = audience;
    }

    @Override
    public Set<Integer> headers() {
        return Set.of(
                PacketHeaders.JOIN_ROOM,
                PacketHeaders.JOIN_HOME,
                PacketHeaders.CHANGE_HOME_ROOM,
                PacketHeaders.GET_ALL_HOUSES,
                PacketHeaders.GET_ROOM_ATTENDEES,
                PacketHeaders.QUERY_SHARED_ITEMS,
                PacketHeaders.UPDATE_HOME_ROOM,
                PacketHeaders.UPDATE_HOME_SOUND);
    }

    @Override
    public void handle(IncomingPacket packet, PlayerSession session) {
        switch (packet.header()) {
            case PacketHeaders.JOIN_ROOM -> joinRoom(packet.reader(), session);
            case PacketHeaders.JOIN_HOME -> joinHome(packet.reader(), session);
            case PacketHeaders.CHANGE_HOME_ROOM -> changeHomeRoom(packet.reader(), session);
            case PacketHeaders.GET_ALL_HOUSES -> allHouses(session);
            case PacketHeaders.GET_ROOM_ATTENDEES -> roomAttendees(session);
            case PacketHeaders.QUERY_SHARED_ITEMS -> sharedItems(packet.reader(), session);
            case PacketHeaders.UPDATE_HOME_ROOM -> audience.roomExceptSource(
                    session, OutgoingPacket.header(PacketHeaders.UPDATE_HOME_ROOM));
            case PacketHeaders.UPDATE_HOME_SOUND -> updateSound(packet.reader(), session);
            default -> throw new IllegalArgumentException("Unsupported room command");
        }
    }

    private void joinRoom(PacketReader reader, PlayerSession session) {
        int roomId = reader.readInt();
        int x = reader.readInt();
        int y = reader.readInt();
        if (roomId < 0 || !validCoordinate(x) || !validCoordinate(y)) {
            return;
        }
        leaveCurrentRoom(session);
        session.joinRoom(roomId, x, y);
        session.send(OutgoingPacket.header(PacketHeaders.ROOM_JOINED).writeInt(roomId));
    }

    private void joinHome(PacketReader reader, PlayerSession session) {
        int ownerId = reader.readInt();
        int x = reader.readInt();
        int y = reader.readInt();
        if (ownerId <= 0 || !validCoordinate(x) || !validCoordinate(y)) {
            return;
        }
        leaveCurrentRoom(session);
        session.joinHome(ownerId, x, y);
        session.send(OutgoingPacket.header(PacketHeaders.HOME_JOINED).writeInt(ownerId));
    }

    private void changeHomeRoom(PacketReader reader, PlayerSession session) {
        int ownerId = reader.readInt();
        int destination = reader.readInt();
        int requestedX = reader.readInt();
        int requestedY = reader.readInt();
        if (!session.home() || session.roomId() != ownerId || destination < 0
                || !validCoordinate(requestedX) || !validCoordinate(requestedY)) {
            return;
        }
        audience.roomExceptSource(session, OutgoingPacket.header(PacketHeaders.UNSET_AVATAR)
                .writeInt(session.playerId()));
        SpawnPoint spawn = spawnPoint(session.subRoom(), destination, requestedX, requestedY);
        session.subRoom(destination);
        session.x(spawn.x());
        session.y(spawn.y());
        session.send(OutgoingPacket.header(PacketHeaders.SUBROOM_ENTERED).writeInt(destination));
    }

    private void allHouses(PlayerSession session) {
        OutgoingPacket response = OutgoingPacket.header(PacketHeaders.ALL_HOUSES);
        sessions.all().forEach(player -> response.writeString("%d:%s:%d".formatted(
                player.playerId(), player.username(), player.sheriff() > 1 ? 1 : 0)));
        session.send(response);
    }

    private void roomAttendees(PlayerSession session) {
        List<PlayerSession> room = sessions.inRoom(session);
        StringBuilder roomString = new StringBuilder(Integer.toString(session.roomId()));
        room.forEach(player -> roomString.append(';').append(player.playerString()));
        session.send(OutgoingPacket.header(PacketHeaders.ROOM_ATTENDEES).writeString(roomString.toString()));

        room.stream().filter(player -> player.playerId() != session.playerId()).forEach(player -> {
            session.send(player.setAvatarPacket());
            OutgoingPacket create = player.createAvatarPacket();
            if (create != null) {
                session.send(create);
            }
            OutgoingPacket update = player.updateAvatarPacket();
            if (update != null) {
                session.send(update);
            }
        });
        audience.roomExceptSource(session, session.setAvatarPacket());
        session.send(OutgoingPacket.header(PacketHeaders.SAFE_CHAT_TOGGLED).writeInt(0));
    }

    private void sharedItems(PacketReader reader, PlayerSession session) {
        int type = reader.readInt();
        int requestedRoom = reader.readInt();
        OutgoingPacket response = OutgoingPacket.header(PacketHeaders.SHARED_ITEMS)
                .writeInt(type)
                .writeInt(requestedRoom);
        if (type == SHARED_ITEMS_STATE) {
            int[] items = new int[SHARED_ITEM_COUNT];
            Arrays.fill(items, -1);
            sessions.inRoom(requestedRoom, session.home(), session.subRoom()).forEach(player -> {
                int item = player.interactingWith();
                if (item >= 0 && item < SHARED_ITEM_COUNT) {
                    items[item] = player.playerId();
                }
            });
            Arrays.stream(items).forEach(response::writeInt);
            session.send(response);
            return;
        }
        if (type == UPDATE_SHARED_ITEM) {
            int itemId = reader.readInt();
            int occupyingPlayer = reader.readInt();
            if (itemId < 0 || itemId >= SHARED_ITEM_COUNT) {
                return;
            }
            response.writeInt(itemId).writeInt(occupyingPlayer == -1 ? -1 : session.playerId());
            session.interactingWith(occupyingPlayer == -1 ? -1 : itemId);
            audience.room(session, response);
            return;
        }
        session.send(response);
    }

    private void updateSound(PacketReader reader, PlayerSession session) {
        String song = sanitize(reader.readString(), 80);
        if (session.home() && session.roomId() == session.playerId() && !song.isBlank()) {
            audience.room(session, OutgoingPacket.header(PacketHeaders.UPDATE_HOME_SOUND).writeString(song));
        }
    }

    private void leaveCurrentRoom(PlayerSession session) {
        if (session.roomId() < 0) {
            return;
        }
        audience.roomExceptSource(session, OutgoingPacket.header(PacketHeaders.UNSET_AVATAR)
                .writeInt(session.playerId()));
    }

    private boolean validCoordinate(int value) {
        return value >= -MAX_COORDINATE && value <= MAX_COORDINATE;
    }

    private String sanitize(String value, int maxLength) {
        String clean = value == null ? "" : value.replace(";", "").replace("|", "");
        return clean.substring(0, Math.min(clean.length(), maxLength));
    }

    private SpawnPoint spawnPoint(int previous, int destination, int x, int y) {
        return switch (destination) {
            case 100870 -> new SpawnPoint(135, 331);
            case 100871 -> new SpawnPoint(635, 322);
            case 100938 -> new SpawnPoint(610, 271);
            case 100939 -> new SpawnPoint(105, 311);
            case 0 -> switch (previous) {
                case 100870 -> new SpawnPoint(120, 347);
                case 100871 -> new SpawnPoint(635, 334);
                case 100938 -> new SpawnPoint(120, 290);
                case 100939 -> new SpawnPoint(620, 316);
                default -> new SpawnPoint(x, y);
            };
            default -> new SpawnPoint(x, y);
        };
    }

    private record SpawnPoint(int x, int y) {}
}
