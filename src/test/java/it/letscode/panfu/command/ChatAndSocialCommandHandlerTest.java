package it.letscode.panfu.command;

import static it.letscode.panfu.support.TestSessions.authenticated;
import static org.assertj.core.api.Assertions.assertThat;

import it.letscode.panfu.protocol.IncomingPacket;
import it.letscode.panfu.protocol.P2pHeaders;
import it.letscode.panfu.protocol.PacketHeaders;
import it.letscode.panfu.session.AudienceService;
import it.letscode.panfu.session.PlayerSession;
import it.letscode.panfu.session.SessionRegistry;
import it.letscode.panfu.support.RecordingConnection;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChatAndSocialCommandHandlerTest {

    @Test
    void sanitizesChatAndUsesAuthenticatedIdentityForBuddyInvites() {
        SessionRegistry registry = new SessionRegistry();
        AudienceService audience = new AudienceService(registry);
        RecordingConnection senderConnection = new RecordingConnection("sender");
        RecordingConnection receiverConnection = new RecordingConnection("receiver");
        PlayerSession sender = authenticated(senderConnection, 1, "RealName");
        PlayerSession receiver = authenticated(receiverConnection, 2, "Buddy");
        sender.joinRoom(5, 0, 0);
        receiver.joinRoom(5, 0, 0);
        registry.register(sender);
        registry.register(receiver);

        new ChatCommandHandler(audience).handle(
                new IncomingPacket(PacketHeaders.CHAT, List.of("<b>Hello</b>;|")), sender);
        new SocialCommandHandler(registry, audience).handle(
                new IncomingPacket(PacketHeaders.ADD_BUDDY, List.of("2", "SpoofedName")), sender);

        assertThat(receiverConnection.messages())
                .contains("40;1;Hello|")
                .contains("60;1;RealName|");
    }

    @Test
    void scopesNonModeratorGlobalP2pMessagesToCurrentRoom() {
        SessionRegistry registry = new SessionRegistry();
        AudienceService audience = new AudienceService(registry);
        RecordingConnection senderConnection = new RecordingConnection("sender");
        RecordingConnection roomConnection = new RecordingConnection("room");
        RecordingConnection outsideConnection = new RecordingConnection("outside");
        PlayerSession sender = authenticated(senderConnection, 1, "Sender");
        PlayerSession room = authenticated(roomConnection, 2, "Room");
        PlayerSession outside = authenticated(outsideConnection, 3, "Outside");
        sender.joinRoom(5, 0, 0);
        room.joinRoom(5, 0, 0);
        outside.joinRoom(6, 0, 0);
        registry.register(sender);
        registry.register(room);
        registry.register(outside);

        new SocialCommandHandler(registry, audience).handle(new IncomingPacket(
                PacketHeaders.PLAYER_TO_PLAYER,
                List.of(Integer.toString(P2pHeaders.RECEIVER_ALL), Integer.toString(P2pHeaders.SHOW_STATUS),
                        "Online", "Hello")), sender);

        assertThat(roomConnection.messages()).containsExactly("113;1;14;Online;Hello|");
        assertThat(outsideConnection.messages()).isEmpty();
    }

    @Test
    void replaysTheCompleteBenchPositionDirectionAndLayerToTheJoiningPlayer() {
        SessionRegistry registry = new SessionRegistry();
        AudienceService audience = new AudienceService(registry);
        RecordingConnection seatedConnection = new RecordingConnection("seated");
        RecordingConnection joiningConnection = new RecordingConnection("joining");
        RecordingConnection outsideConnection = new RecordingConnection("outside");
        PlayerSession seated = authenticated(seatedConnection, 1, "Seated");
        PlayerSession joining = authenticated(joiningConnection, 2, "Joining");
        PlayerSession outside = authenticated(outsideConnection, 3, "Outside");
        seated.joinRoom(5, 100, 100);
        joining.joinRoom(5, 200, 200);
        outside.joinRoom(6, 300, 300);
        registry.register(seated);
        registry.register(joining);
        registry.register(outside);
        SocialCommandHandler handler = new SocialCommandHandler(registry, audience);

        seated.interactingWith(0);
        handler.handle(new IncomingPacket(
                PacketHeaders.PLAYER_TO_PLAYER,
                List.of(Integer.toString(P2pHeaders.RECEIVER_ROOM), Integer.toString(P2pHeaders.USE_SHARED_ITEM),
                        "241", "335", "sit", "down_right", "355")), seated);
        handler.handle(new IncomingPacket(
                PacketHeaders.PLAYER_TO_PLAYER,
                List.of("2", Integer.toString(P2pHeaders.REPLAY_AVATAR_ACTION), "sit")), seated);
        handler.handle(new IncomingPacket(
                PacketHeaders.PLAYER_TO_PLAYER,
                List.of("3", Integer.toString(P2pHeaders.REPLAY_AVATAR_ACTION), "sit")), seated);
        handler.handle(new IncomingPacket(
                PacketHeaders.PLAYER_TO_PLAYER,
                List.of("2", Integer.toString(P2pHeaders.REPLAY_AVATAR_ACTION), "dance")), seated);

        assertThat(joiningConnection.messages()).containsExactly(
                "113;1;12;241;335;sit;down_right;355|",
                "113;1;12;241;335;sit;down_right;355|");
        assertThat(seated.x()).isEqualTo(241);
        assertThat(seated.y()).isEqualTo(335);
        assertThat(outsideConnection.messages()).isEmpty();
    }
}
