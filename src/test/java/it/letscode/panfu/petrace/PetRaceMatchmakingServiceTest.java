package it.letscode.panfu.petrace;

import static it.letscode.panfu.support.TestSessions.authenticated;
import static org.assertj.core.api.Assertions.assertThat;

import it.letscode.panfu.session.PlayerSession;
import it.letscode.panfu.session.SessionRegistry;
import it.letscode.panfu.support.RecordingConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PetRaceMatchmakingServiceTest {

    private final SessionRegistry sessions = new SessionRegistry();
    private final PetRaceMatchmakingService matchmaking = new PetRaceMatchmakingService(sessions);
    private RecordingConnection firstConnection;
    private RecordingConnection secondConnection;
    private PlayerSession first;
    private PlayerSession second;

    @BeforeEach
    void setUp() {
        firstConnection = new RecordingConnection("first");
        secondConnection = new RecordingConnection("second");
        first = authenticated(firstConnection, 11, "First");
        second = authenticated(secondConnection, 22, "Second");
        sessions.register(first);
        sessions.register(second);
    }

    @Test
    void acceptsPrivateInvitationAndIssuesTheSameTicketToBothPlayers() {
        matchmaking.invite(first, second.playerId());
        matchmaking.respond(second, first.playerId(), true);

        assertThat(secondConnection.messages().getFirst()).isEqualTo("200;11;First|");
        int firstTicket = ticket(firstConnection.messages().getFirst());
        int secondTicket = ticket(secondConnection.messages().get(1));
        assertThat(firstTicket).isPositive().isEqualTo(secondTicket);
        assertThat(matchmaking.findMatch(firstTicket)).hasValueSatisfying(match ->
                assertThat(match.playerIds()).containsExactlyInAnyOrder(11, 22));
    }

    @Test
    void rejectionAndCancellationUseTheLegacyPacketShapes() {
        matchmaking.invite(first, second.playerId());
        matchmaking.respond(second, first.playerId(), false);
        assertThat(firstConnection.messages()).containsExactly("201;22;0|");

        matchmaking.invite(first, second.playerId());
        matchmaking.cancelPrivate(first, second.playerId());
        assertThat(secondConnection.messages()).contains("202|");
    }

    @Test
    void pairsPublicPlayersAndCanFallBackToABot() {
        matchmaking.joinPublic(first);
        matchmaking.joinPublic(second);

        int firstTicket = ticket(firstConnection.messages().getFirst());
        int secondTicket = ticket(secondConnection.messages().getFirst());
        assertThat(firstConnection.messages().getFirst()).endsWith(";22|");
        assertThat(secondConnection.messages().getFirst()).endsWith(";11|");
        assertThat(firstTicket).isEqualTo(secondTicket);

        RecordingConnection thirdConnection = new RecordingConnection("third");
        PlayerSession third = authenticated(thirdConnection, 33, "Third");
        sessions.register(third);
        matchmaking.joinPublic(third);
        matchmaking.matchWithBot(third);

        int botTicket = ticket(thirdConnection.messages().getFirst());
        assertThat(thirdConnection.messages().getFirst()).endsWith(";0|");
        assertThat(matchmaking.findMatch(botTicket)).hasValueSatisfying(match -> {
            assertThat(match.playerIds()).containsExactlyInAnyOrder(33, 0);
            assertThat(match.hasBot()).isTrue();
        });
    }

    @Test
    void cancelledPublicSearchDoesNotPairThePlayerLater() {
        matchmaking.joinPublic(first);
        matchmaking.cancelPublic(first);
        matchmaking.joinPublic(second);

        assertThat(firstConnection.messages()).isEmpty();
        assertThat(secondConnection.messages()).isEmpty();
    }

    private int ticket(String packet) {
        return Integer.parseInt(packet.split("[;|]")[1]);
    }
}
