package it.letscode.panfu.petrace;

import static it.letscode.panfu.support.TestSessions.authenticated;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import it.letscode.panfu.persistence.petrace.PetRacePet;
import it.letscode.panfu.persistence.petrace.PetRacePetRepository;
import it.letscode.panfu.protocol.PacketCodec;
import it.letscode.panfu.session.PlayerSession;
import it.letscode.panfu.session.SessionRegistry;
import it.letscode.panfu.support.RecordingConnection;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class PetRaceProtocolServiceTest {

    private final ObjectMapper json = new ObjectMapper();
    private final SessionRegistry sessions = new SessionRegistry();
    private final PetRaceMatchmakingService matchmaking = new PetRaceMatchmakingService(sessions);
    private final PetRacePet pet = new PetRacePet(77, 11, 2, "Bambus", true, 5, 3, 2, 1, 40, 2, "[101]");
    private final AtomicReference<PetRacePet> storedPet = new AtomicReference<>(pet);
    private final AtomicInteger appliedResults = new AtomicInteger();
    private final PetRacePetRepository pets = new PetRacePetRepository() {
        @Override
        public Optional<PetRacePet> find(int petId) {
            return petId == pet.id() ? Optional.of(storedPet.get()) : Optional.empty();
        }

        @Override
        public Optional<PetRacePet> applyRaceResult(int petId, int ownerId, int experienceReward) {
            if (petId != pet.id() || ownerId != pet.ownerId()) {
                return Optional.empty();
            }
            appliedResults.incrementAndGet();
            return Optional.of(storedPet.updateAndGet(current -> new PetRacePet(
                    current.id(), current.ownerId(), current.type(), current.name(), current.selected(),
                    Math.max(0, current.health() - 1), current.speed(), current.agility(), current.power(),
                    current.experience() + experienceReward, current.level(), current.abilitiesJson())));
        }
    };
    private PetRaceProtocolService protocol;
    private PlayerSession mainSession;
    private RecordingConnection raceConnection;
    private PlayerSession raceSession;
    private int ticket;

    @BeforeEach
    void setUp() {
        protocol = new PetRaceProtocolService(
                matchmaking, pets, json, 3, Duration.ofMillis(10), Duration.ofMillis(1));
        RecordingConnection mainConnection = new RecordingConnection("main");
        mainSession = authenticated(mainConnection, 11, "First");
        sessions.register(mainSession);
        matchmaking.matchWithBot(mainSession);
        ticket = Integer.parseInt(mainConnection.messages().getFirst().split("[;|]")[1]);
        raceConnection = new RecordingConnection("race");
        raceSession = new PlayerSession(raceConnection, testCodec());
    }

    @Test
    void authenticatesSplitHandshakeAndRunsABotRaceToCompletion() throws Exception {
        String handshake = "{\"id\":" + ticket + ",\"petId\":77}\n";
        protocol.accept(handshake.substring(0, 9), raceSession);
        assertThat(raceConnection.messages()).isEmpty();
        protocol.accept(handshake.substring(9), raceSession);

        assertThat(raceConnection.messages()).hasSize(2);
        JsonNode track = json.readTree(raceConnection.messages().getFirst());
        JsonNode racePets = json.readTree(raceConnection.messages().get(1));
        assertThat(track.path("classId").asText()).isEqualTo("track");
        assertThat(track.path("tiles").size()).isEqualTo(16);
        assertThat(racePets.path("ownerId").asInt()).isEqualTo(77);
        assertThat(racePets.path("petsList").size()).isEqualTo(2);

        protocol.accept("{\"message\":\"ready\"}\n", raceSession);

        await().atMost(Duration.ofSeconds(1)).untilAsserted(() -> {
            assertThat(raceConnection.messages()).hasSize(6);
            JsonNode result = json.readTree(raceConnection.messages().getLast());
            assertThat(result.path("classId").asText()).isEqualTo("raceresults");
            assertThat(result.path("roundsCount").asInt()).isEqualTo(3);
            assertThat(result.path("pet").path("health").asInt()).isEqualTo(4);
            assertThat(result.path("pet").path("experience").asInt()).isIn(50, 60);
        });
        assertThat(matchmaking.findMatch(ticket)).isEmpty();
        assertThat(appliedResults).hasValue(1);
        assertThat(storedPet.get().health()).isEqualTo(4);
        assertThat(storedPet.get().experience()).isIn(50, 60);
    }

    @Test
    void rejectsUnknownUnselectedEmptyAndMalformedRaceClients() {
        assertRejected("{\"id\":999,\"petId\":77}\n");

        RecordingConnection malformedConnection = new RecordingConnection("malformed");
        PlayerSession malformedSession = new PlayerSession(malformedConnection, testCodec());
        protocol.accept("not-json\n", malformedSession);
        assertThat(malformedConnection.closed()).isTrue();

        PetRacePetRepository unavailablePet = new PetRacePetRepository() {
            @Override
            public Optional<PetRacePet> find(int ignored) {
                return Optional.of(new PetRacePet(77, 11, 2, "Bambus", false, 0, 3, 2, 1, 40, 2, "[]"));
            }

            @Override
            public Optional<PetRacePet> applyRaceResult(int petId, int ownerId, int experienceReward) {
                return Optional.empty();
            }
        };
        PetRaceProtocolService unavailableProtocol = new PetRaceProtocolService(
                matchmaking, unavailablePet, json, 1, Duration.ofMillis(1), Duration.ZERO);
        RecordingConnection unavailableConnection = new RecordingConnection("unavailable");
        PlayerSession unavailableSession = new PlayerSession(unavailableConnection, testCodec());
        unavailableProtocol.accept("{\"id\":" + ticket + ",\"petId\":77}\n", unavailableSession);
        assertThat(unavailableConnection.closed()).isTrue();
    }

    @Test
    void disconnectCleansUpAnUnusedRace() {
        protocol.accept("{\"id\":" + ticket + ",\"petId\":77}\n", raceSession);
        protocol.disconnected(raceSession);

        assertThat(matchmaking.findMatch(ticket)).isEmpty();
    }

    private void assertRejected(String payload) {
        RecordingConnection rejectedConnection = new RecordingConnection("rejected-" + payload.hashCode());
        PlayerSession rejected = new PlayerSession(rejectedConnection, testCodec());
        protocol.accept(payload, rejected);
        assertThat(rejectedConnection.closed()).isTrue();
    }

    private PacketCodec testCodec() {
        return new PacketCodec(new it.letscode.panfu.config.GameServerProperties(
                1,
                new it.letscode.panfu.config.GameServerProperties.Network("/game", 9595, true),
                new it.letscode.panfu.config.GameServerProperties.Security(
                        java.util.List.of("http://localhost"), "secret", Duration.ofSeconds(30)),
                new it.letscode.panfu.config.GameServerProperties.Limits(
                        8192, 64, 10, Duration.ofSeconds(30), Duration.ofMinutes(5)),
                new it.letscode.panfu.config.GameServerProperties.Rewards(
                        true, Duration.ofSeconds(2), 100_000, 500)));
    }
}
