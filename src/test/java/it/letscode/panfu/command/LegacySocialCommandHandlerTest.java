package it.letscode.panfu.command;

import static it.letscode.panfu.support.TestSessions.authenticated;
import static org.assertj.core.api.Assertions.assertThat;

import it.letscode.panfu.moderation.WordFilter;
import it.letscode.panfu.persistence.social.LegacySocialRepository;
import it.letscode.panfu.protocol.IncomingPacket;
import it.letscode.panfu.protocol.PacketHeaders;
import it.letscode.panfu.session.AudienceService;
import it.letscode.panfu.session.PlayerSession;
import it.letscode.panfu.session.SessionRegistry;
import it.letscode.panfu.support.RecordingConnection;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LegacySocialCommandHandlerTest {

    private final SessionRegistry sessions = new SessionRegistry();
    private final RecordingRepository repository = new RecordingRepository();
    private final LegacySocialCommandHandler handler = new LegacySocialCommandHandler(
            sessions, new AudienceService(sessions), repository, new WordFilter());

    private RecordingConnection senderConnection;
    private RecordingConnection targetConnection;
    private PlayerSession sender;
    private PlayerSession target;

    @BeforeEach
    void setUp() {
        senderConnection = new RecordingConnection("sender");
        targetConnection = new RecordingConnection("target");
        sender = authenticated(senderConnection, 1, "Sender");
        target = authenticated(targetConnection, 2, "Target");
        sender.joinRoom(5, 100, 100);
        target.joinRoom(6, 220, 330);
        sessions.register(sender);
        sessions.register(target);
    }

    @Test
    void followsAnOnlinePlayerAndReportsUsefulFailureReasons() {
        handler.handle(new IncomingPacket(PacketHeaders.GOTO_PLAYER, List.of("99")), sender);
        handler.handle(new IncomingPacket(PacketHeaders.GOTO_PLAYER, List.of("2")), sender);
        handler.handle(new IncomingPacket(PacketHeaders.GOTO_PLAYER, List.of("2")), sender);

        assertThat(senderConnection.messages()).containsExactly(
                "23;99;offline|",
                "10;6|",
                "23;2;sameRoom|");
        assertThat(sender.roomId()).isEqualTo(6);
        assertThat(sender.x()).isEqualTo(220);
        assertThat(sender.y()).isEqualTo(330);
    }

    @Test
    void chargesForAnEcardAndUsesTheAuthenticatedSenderIdentity() {
        handler.handle(new IncomingPacket(
                PacketHeaders.SEND_ECARD, List.of("2", "17", "Spoofed")), sender);

        assertThat(repository.debits).containsExactly("1:10");
        assertThat(targetConnection.messages()).containsExactly("42;17;1;Sender|");
        assertThat(senderConnection.messages()).containsExactly("35;-10|");
    }

    @Test
    void persistsCleanProfileTextAndRejectsFilteredOrSpoofedUpdates() {
        handler.handle(new IncomingPacket(
                PacketHeaders.PROFILE_TEXT, List.of("1", "9", "  Kocham Panfu  ")), sender);
        handler.handle(new IncomingPacket(
                PacketHeaders.PROFILE_TEXT, List.of("1", "9", "f.u.c.k")), sender);
        handler.handle(new IncomingPacket(
                PacketHeaders.PROFILE_TEXT, List.of("2", "9", "spoof")), sender);

        assertThat(repository.profileUpdates).containsExactly("1:motto:Kocham Panfu");
        assertThat(senderConnection.messages()).containsExactly("93;1;9|", "92;1;9|");
    }

    @Test
    void returnsOnlineAndOfflineLocationsAndTogglesInvincibility() {
        handler.handle(new IncomingPacket(PacketHeaders.GET_PLAYER_LOCATION, List.of("2")), sender);
        handler.handle(new IncomingPacket(PacketHeaders.GET_PLAYER_LOCATION, List.of("99")), sender);
        handler.handle(new IncomingPacket(PacketHeaders.SET_PLAYER_INVINCIBLE, List.of()), sender);

        assertThat(senderConnection.messages()).containsExactly("211;2;6;0;0|", "211;99;-1;0;0|");
        assertThat(sender.invincible()).isTrue();
    }

    private static final class RecordingRepository implements LegacySocialRepository {
        private final List<String> debits = new ArrayList<>();
        private final List<String> profileUpdates = new ArrayList<>();

        @Override
        public boolean debitCoins(int playerId, int amount) {
            debits.add(playerId + ":" + amount);
            return true;
        }

        @Override
        public void updateProfileField(int playerId, String field, String value) {
            profileUpdates.add(playerId + ":" + field + ":" + value);
        }
    }
}
