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

class ActionCommandHandlerTest {

    @Test
    void broadcastsActionsAndNeverTrustsTheClaimedInvitationPlayer() {
        SessionRegistry registry = new SessionRegistry();
        AudienceService audience = new AudienceService(registry);
        RecordingConnection senderConnection = new RecordingConnection("sender");
        RecordingConnection targetConnection = new RecordingConnection("target");
        PlayerSession sender = authenticated(senderConnection, 7, "Sender");
        PlayerSession target = authenticated(targetConnection, 9, "Target");
        sender.joinRoom(3, 0, 0);
        target.joinRoom(3, 0, 0);
        registry.register(sender);
        registry.register(target);
        ActionCommandHandler handler = new ActionCommandHandler(audience, registry);

        handler.handle(new IncomingPacket(PacketHeaders.ACTION, List.of(
                "gameInvite", "123", "999", "41", "9")), sender);

        assertThat(targetConnection.messages()).containsExactly("50;7;gameInvite;0;0;41;9;false|");
    }

    @Test
    void validatesThrownItemsAndVictims() {
        SessionRegistry registry = new SessionRegistry();
        AudienceService audience = new AudienceService(registry);
        RecordingConnection senderConnection = new RecordingConnection("sender");
        RecordingConnection targetConnection = new RecordingConnection("target");
        PlayerSession sender = authenticated(senderConnection, 7, "Sender");
        PlayerSession target = authenticated(targetConnection, 9, "Target");
        sender.joinRoom(3, 0, 0);
        target.joinRoom(3, 0, 0);
        sender.lastAction("waterbomb");
        registry.register(sender);
        registry.register(target);

        new ActionCommandHandler(audience, registry).handle(new IncomingPacket(
                PacketHeaders.ACTION, List.of("throw", "10", "20", "waterbomb", "9")), sender);

        assertThat(targetConnection.messages()).containsExactly("50;7;throw;10;20;waterbomb;9;false|");
    }
}
