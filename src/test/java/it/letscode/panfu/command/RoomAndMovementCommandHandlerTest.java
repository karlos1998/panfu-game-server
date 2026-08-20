package it.letscode.panfu.command;

import static it.letscode.panfu.support.TestSessions.authenticated;
import static org.assertj.core.api.Assertions.assertThat;

import it.letscode.panfu.protocol.IncomingPacket;
import it.letscode.panfu.protocol.PacketHeaders;
import it.letscode.panfu.session.AudienceService;
import it.letscode.panfu.session.PlayerSession;
import it.letscode.panfu.session.SessionRegistry;
import it.letscode.panfu.support.RecordingConnection;
import java.util.List;
import org.junit.jupiter.api.Test;

class RoomAndMovementCommandHandlerTest {

    @Test
    void returnsRoomSnapshotAndBroadcastsMovementWithLegacyFormat() {
        SessionRegistry registry = new SessionRegistry();
        AudienceService audience = new AudienceService(registry);
        RoomCommandHandler rooms = new RoomCommandHandler(registry, audience);
        MovementCommandHandler movement = new MovementCommandHandler(audience);
        RecordingConnection firstConnection = new RecordingConnection("first");
        RecordingConnection secondConnection = new RecordingConnection("second");
        PlayerSession first = authenticated(firstConnection, 1, "First");
        PlayerSession second = authenticated(secondConnection, 2, "Second");
        first.joinRoom(10, 100, 100);
        second.joinRoom(10, 200, 200);
        registry.register(first);
        registry.register(second);

        rooms.handle(new IncomingPacket(PacketHeaders.GET_ROOM_ATTENDEES, List.of()), first);
        movement.handle(new IncomingPacket(PacketHeaders.MOVE, List.of("120", "140", "0")), first);

        assertThat(firstConnection.messages())
                .contains("70;10;1:100:100:First:0:0:0;2:200:200:Second:0:0:0|")
                .contains("20;1;447;120;140;0|");
        assertThat(secondConnection.messages())
                .contains("30;10;1;100;100;First|")
                .contains("20;1;447;120;140;0|");
    }

    @Test
    void lateJoinSnapshotKeepsCurrentPositionAndMapsStatusSeparatelyFromRotation() {
        SessionRegistry registry = new SessionRegistry();
        AudienceService audience = new AudienceService(registry);
        RoomCommandHandler rooms = new RoomCommandHandler(registry, audience);
        MovementCommandHandler movement = new MovementCommandHandler(audience);
        RecordingConnection existingConnection = new RecordingConnection("existing");
        RecordingConnection joiningConnection = new RecordingConnection("joining");
        PlayerSession existing = authenticated(existingConnection, 1, "Existing");
        PlayerSession joining = authenticated(joiningConnection, 2, "Joining");
        existing.joinRoom(10, 100, 100);
        existing.storeAvatar(100, 100, "sit", 1, "", "1001");
        joining.joinRoom(10, 200, 200);
        registry.register(existing);
        registry.register(joining);

        movement.handle(new IncomingPacket(PacketHeaders.MOVE, List.of("320", "410", "0")), existing);
        movement.handle(new IncomingPacket(PacketHeaders.ROTATE, List.of("6")), existing);
        movement.handle(new IncomingPacket(PacketHeaders.SET_PLAYER_STATUS, List.of("3")), existing);
        existing.interactingWith(0);
        existing.storeSharedItemAction(241, 335, "sit", "down_right", 355);
        rooms.handle(new IncomingPacket(PacketHeaders.GET_ROOM_ATTENDEES, List.of()), joining);

        assertThat(joiningConnection.messages())
                .contains("70;10;1:241:335:Existing:0:3:6;2:200:200:Joining:0:0:0|")
                .contains("30;10;1;241;335;Existing|")
                .contains("113;1;10;241;335;sit;6;;0;Existing,1001|")
                .contains("113;1;14;Shopping;|")
                .contains("113;1;12;241;335;sit;down_right;355|");
    }

    @Test
    void rejectsOutOfRangeMovementWithoutBroadcastingIt() {
        SessionRegistry registry = new SessionRegistry();
        RecordingConnection connection = new RecordingConnection("player");
        PlayerSession player = authenticated(connection, 1, "Panda");
        player.joinRoom(1, 0, 0);
        registry.register(player);

        new MovementCommandHandler(new AudienceService(registry)).handle(
                new IncomingPacket(PacketHeaders.MOVE, List.of("999999", "0", "0")), player);

        assertThat(connection.closed()).isTrue();
        assertThat(connection.messages()).containsExactly("2;Error: CMD_MOVE, invalid movement.|");
    }

    @Test
    void movingAwayClearsTheStoredSharedItemAction() {
        SessionRegistry registry = new SessionRegistry();
        RecordingConnection connection = new RecordingConnection("player");
        PlayerSession player = authenticated(connection, 1, "Panda");
        player.joinRoom(1, 241, 335);
        player.interactingWith(0);
        player.storeSharedItemAction(241, 335, "sit", "down_right", 355);
        registry.register(player);

        new MovementCommandHandler(new AudienceService(registry)).handle(
                new IncomingPacket(PacketHeaders.MOVE, List.of("260", "350", "0")), player);

        assertThat(player.interactingWith()).isEqualTo(-1);
        assertThat(player.sharedItemActionPacket()).isNull();
    }
}
