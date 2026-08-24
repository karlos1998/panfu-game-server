package it.letscode.panfu.petrace;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import it.letscode.panfu.persistence.petrace.PetRacePet;
import it.letscode.panfu.persistence.petrace.PetRacePetRepository;
import it.letscode.panfu.persistence.petrace.PetRaceProgression;
import it.letscode.panfu.session.PlayerSession;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

@Service
public final class PetRaceProtocolService {

    private static final Logger log = LoggerFactory.getLogger(PetRaceProtocolService.class);
    private static final int MAX_FRAME_LENGTH = 8_192;
    private static final int DEFAULT_ROUND_COUNT = 18;
    private static final Duration DEFAULT_ROUND_INTERVAL = Duration.ofMillis(850);
    private static final Duration DEFAULT_START_DELAY = Duration.ofMillis(250);

    private final PetRaceMatchmakingService matchmaking;
    private final PetRacePetRepository pets;
    private final ObjectMapper json;
    private final int roundCount;
    private final Duration roundInterval;
    private final Duration startDelay;
    private final ConcurrentHashMap<String, StringBuilder> buffers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, RaceClient> clientsByConnection = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, RaceRuntime> runtimes = new ConcurrentHashMap<>();

    @Autowired
    public PetRaceProtocolService(
            PetRaceMatchmakingService matchmaking,
            PetRacePetRepository pets,
            ObjectMapper json) {
        this(matchmaking, pets, json, DEFAULT_ROUND_COUNT, DEFAULT_ROUND_INTERVAL, DEFAULT_START_DELAY);
    }

    PetRaceProtocolService(
            PetRaceMatchmakingService matchmaking,
            PetRacePetRepository pets,
            ObjectMapper json,
            int roundCount,
            Duration roundInterval,
            Duration startDelay) {
        this.matchmaking = matchmaking;
        this.pets = pets;
        this.json = json;
        this.roundCount = roundCount;
        this.roundInterval = roundInterval;
        this.startDelay = startDelay;
    }

    public void accept(String chunk, PlayerSession session) {
        StringBuilder buffer = buffers.computeIfAbsent(session.connection().id(), ignored -> new StringBuilder());
        buffer.append(chunk);
        if (buffer.length() > MAX_FRAME_LENGTH) {
            session.disconnect("");
            return;
        }
        int newline;
        while ((newline = buffer.indexOf("\n")) >= 0) {
            String frame = buffer.substring(0, newline).strip();
            buffer.delete(0, newline + 1);
            if (!frame.isBlank()) {
                handleFrame(frame, session);
            }
        }
    }

    public void disconnected(PlayerSession session) {
        String connectionId = session.connection().id();
        buffers.remove(connectionId);
        RaceClient client = clientsByConnection.remove(connectionId);
        if (client != null) {
            RaceRuntime runtime = runtimes.get(client.ticket());
            if (runtime != null) {
                runtime.remove(connectionId);
            }
        }
    }

    private void handleFrame(String frame, PlayerSession session) {
        try {
            JsonNode payload = json.readTree(frame);
            RaceClient existing = clientsByConnection.get(session.connection().id());
            if (existing == null) {
                authenticate(payload, session);
                return;
            }
            if ("ready".equals(payload.path("message").asText())) {
                RaceRuntime runtime = runtimes.get(existing.ticket());
                if (runtime != null) {
                    runtime.ready(existing);
                }
            } else if (payload.path("message").canConvertToInt()) {
                RaceRuntime runtime = runtimes.get(existing.ticket());
                if (runtime != null) {
                    runtime.useSpecial(existing, payload.path("message").asInt());
                }
            }
        } catch (JacksonException exception) {
            log.warn("Rejected malformed pet race payload connectionId={}", session.connection().id());
            session.disconnect("");
        }
    }

    private void authenticate(JsonNode payload, PlayerSession session) {
        int ticket = payload.path("id").asInt(-1);
        int petId = payload.path("petId").asInt(-1);
        PetRaceMatch match = matchmaking.findMatch(ticket).orElse(null);
        PetRacePet pet = pets.find(petId).orElse(null);
        if (match == null || pet == null || !pet.selected() || pet.health() <= 0 || !match.hasPlayer(pet.ownerId())) {
            session.disconnect("");
            return;
        }

        RaceClient client = new RaceClient(ticket, pet.ownerId(), pet, session);
        clientsByConnection.put(session.connection().id(), client);
        RaceRuntime runtime = runtimes.computeIfAbsent(ticket, ignored -> new RaceRuntime(match));
        runtime.register(client);
        log.info("Pet race client authenticated ticket={} playerId={} petId={}", ticket, pet.ownerId(), pet.id());
    }

    private final class RaceRuntime {
        private final PetRaceMatch match;
        private final ConcurrentHashMap<Integer, RaceClient> clients = new ConcurrentHashMap<>();
        private final Set<Integer> readyPlayers = ConcurrentHashMap.newKeySet();
        private final AtomicBoolean setupSent = new AtomicBoolean();
        private final AtomicBoolean started = new AtomicBoolean();
        private final AtomicBoolean finished = new AtomicBoolean();
        private final ConcurrentHashMap<Integer, AtomicInteger> pendingBoosts = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<Integer, AtomicInteger> totalBoosts = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<Integer, Integer> positions = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<Integer, Set<Integer>> usedSpecials = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<Integer, ConcurrentLinkedQueue<Integer>> pendingSpecials = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<Integer, AtomicInteger> shields = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<Integer, AtomicInteger> boostBlocks = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<Integer, AtomicInteger> mirrors = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<Integer, AtomicInteger> sponges = new ConcurrentHashMap<>();
        private final AtomicInteger specialSequence = new AtomicInteger();
        private volatile int winnerId;
        private volatile Disposable rounds;

        private RaceRuntime(PetRaceMatch match) {
            this.match = match;
        }

        private void register(RaceClient client) {
            RaceClient previous = clients.putIfAbsent(client.playerId(), client);
            if (previous != null) {
                client.session().disconnect("");
                return;
            }
            int expected = (int) match.playerIds().stream().filter(id -> id > 0).count();
            if (clients.size() == expected && setupSent.compareAndSet(false, true)) {
                sendSetup();
            }
        }

        private void sendSetup() {
            List<PetRacePet> racePets = clients.values().stream()
                    .map(RaceClient::pet)
                    .sorted(Comparator.comparingInt(PetRacePet::ownerId))
                    .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
            if (match.hasBot()) {
                racePets.add(botPet());
            }
            winnerId = racePets.stream()
                    .max(Comparator.comparingInt(this::raceScore))
                    .map(PetRacePet::ownerId)
                    .orElse(0);
            Map<String, Object> track = trackPayload();
            clients.values().forEach(client -> {
                send(client, track);
                send(client, Map.of(
                        "classId", "pets",
                        "ownerId", client.pet().id(),
                        "petsList", racePets.stream().map(PetRaceProtocolService.this::petPayload).toList()));
            });
        }

        private void ready(RaceClient client) {
            readyPlayers.add(client.playerId());
            if (setupSent.get() && readyPlayers.size() == clients.size() && started.compareAndSet(false, true)) {
                startRounds();
            }
        }

        private void useSpecial(RaceClient client, int abilityId) {
            if (!started.get() || finished.get()) {
                return;
            }
            List<Integer> available = abilities(client.pet().abilitiesJson());
            if (available.isEmpty()) {
                available = List.of(501);
            }
            if (!available.contains(abilityId)) {
                return;
            }
            if (abilityId != 501) {
                if (abilityId < 502 || abilityId > 523) {
                    return;
                }
                Set<Integer> used = usedSpecials.computeIfAbsent(
                        client.pet().id(), ignored -> ConcurrentHashMap.newKeySet());
                if (used.add(abilityId)) {
                    pendingSpecials.computeIfAbsent(
                                    client.pet().id(), ignored -> new ConcurrentLinkedQueue<>())
                            .add(abilityId);
                }
                return;
            }
            if (consumeEffect(boostBlocks, client.pet().id())) {
                return;
            }
            AtomicInteger used = totalBoosts.computeIfAbsent(client.pet().id(), ignored -> new AtomicInteger());
            int maximum = Math.max(0, client.pet().health());
            while (true) {
                int current = used.get();
                if (current >= maximum) {
                    return;
                }
                if (used.compareAndSet(current, current + 1)) {
                    pendingBoosts.computeIfAbsent(client.pet().id(), ignored -> new AtomicInteger()).incrementAndGet();
                    return;
                }
            }
        }

        private void startRounds() {
            List<PetRacePet> racePets = clients.values().stream()
                    .map(RaceClient::pet)
                    .sorted(Comparator.comparingInt(PetRacePet::ownerId))
                    .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
            if (match.hasBot()) {
                racePets.add(botPet());
            }
            rounds = Flux.interval(startDelay, roundInterval)
                    .take(roundCount)
                    .subscribe(index -> {
                        int round = index.intValue() + 1;
                        broadcast(roundPayload(round, racePets));
                        if (round == roundCount) {
                            Flux.interval(roundInterval).take(1).subscribe(ignored -> finishRace());
                        }
                    });
        }

        private void finishRace() {
            if (!finished.compareAndSet(false, true)) {
                return;
            }
            clients.values().forEach(client -> {
                boolean winner = client.playerId() == winnerId;
                int experienceReward = winner ? 20 : 10;
                PetRacePet updatedPet = pets.applyRaceResult(
                                client.pet().id(), client.playerId(), experienceReward,
                                totalBoosts.getOrDefault(client.pet().id(), new AtomicInteger()).get())
                        .orElse(null);
                if (updatedPet == null) {
                    log.error(
                            "Could not persist pet race result ticket={} playerId={} petId={}",
                            match.ticket(), client.playerId(), client.pet().id());
                    client.session().disconnect("");
                    return;
                }
                send(client, resultPayload(updatedPet, winner, updatedPet.level() > client.pet().level()));
            });
            if (rounds != null) {
                rounds.dispose();
            }
            runtimes.remove(match.ticket(), this);
            matchmaking.finish(match.ticket());
        }

        private void broadcast(Map<String, Object> payload) {
            clients.values().forEach(client -> send(client, payload));
        }

        private void remove(String connectionId) {
            clients.entrySet().removeIf(entry -> entry.getValue().session().connection().id().equals(connectionId));
            if (clients.isEmpty()) {
                if (rounds != null) {
                    rounds.dispose();
                }
                runtimes.remove(match.ticket(), this);
                matchmaking.finish(match.ticket());
            }
        }

        private Map<String, Object> roundPayload(int round, List<PetRacePet> racePets) {
            Map<Integer, Integer> nextPositions = new LinkedHashMap<>();
            Map<Integer, Integer> specialDeltas = new LinkedHashMap<>();
            List<Map<String, Object>> roundSpecials = new ArrayList<>();
            applyPendingSpecials(round, racePets, specialDeltas, roundSpecials);
            for (PetRacePet pet : racePets) {
                boolean currentLeader = pet.ownerId() == winnerId;
                int boost = pendingBoosts.getOrDefault(pet.id(), new AtomicInteger()).getAndSet(0);
                int step = (currentLeader ? 6 : 5) + boost * 3 + specialDeltas.getOrDefault(pet.id(), 0);
                nextPositions.put(pet.id(), Math.max(
                        0, Math.min(108, positions.getOrDefault(pet.id(), 0) + step)));
            }
            positions.putAll(nextPositions);
            if (round == roundCount) {
                winnerId = racePets.stream()
                        .max(Comparator.<PetRacePet>comparingInt(pet -> nextPositions.getOrDefault(pet.id(), 0))
                                .thenComparingInt(this::raceScore))
                        .map(PetRacePet::ownerId)
                        .orElse(0);
            }
            List<Map<String, Object>> sequences = new ArrayList<>();
            for (PetRacePet pet : racePets) {
                boolean winner = pet.ownerId() == winnerId;
                int position = nextPositions.getOrDefault(pet.id(), 0);
                int type = round == roundCount ? (winner ? 4 : 5) : 1;
                sequences.add(Map.of(
                        "classId", "movesequence",
                        "petId", pet.id(),
                        "movements", List.of(Map.of(
                                "classId", "move",
                                "position", position,
                                "typeId", type))));
            }
            return Map.of(
                    "classId", "round",
                    "id", round,
                    "duration", roundInterval.toMillis() / 1000.0,
                    "movementSequences", sequences,
                    "specials", roundSpecials);
        }

        private void applyPendingSpecials(
                int round,
                List<PetRacePet> racePets,
                Map<Integer, Integer> deltas,
                List<Map<String, Object>> roundSpecials) {
            for (PetRacePet source : racePets) {
                ConcurrentLinkedQueue<Integer> queue = pendingSpecials.get(source.id());
                if (queue == null) {
                    continue;
                }
                Integer abilityId;
                while ((abilityId = queue.poll()) != null) {
                    PetRacePet opponent = racePets.stream()
                            .filter(candidate -> candidate.id() != source.id())
                            .findFirst()
                            .orElse(source);
                    PetRacePet affected = offensiveSpecial(abilityId) ? opponent : source;
                    int delta = specialDelta(abilityId);
                    if (defensiveSpecial(abilityId)) {
                        defensiveEffects(abilityId).computeIfAbsent(
                                source.id(), ignored -> new AtomicInteger()).incrementAndGet();
                    } else if (abilityId == 510) {
                        boostBlocks.computeIfAbsent(affected.id(), ignored -> new AtomicInteger()).incrementAndGet();
                    } else if (abilityId == 520) {
                        racePets.forEach(pet -> deltas.merge(pet.id(), -4, Integer::sum));
                    } else if (abilityId == 518) {
                        deltas.merge(source.id(), 2, Integer::sum);
                        deltas.merge(opponent.id(), -2, Integer::sum);
                    } else if (abilityId == 519) {
                        deltas.merge(source.id(), 3, Integer::sum);
                        deltas.merge(opponent.id(), -3, Integer::sum);
                    } else if (offensiveSpecial(abilityId)
                            && consumeEffect(mirrors, affected.id())) {
                        deltas.merge(source.id(), delta, Integer::sum);
                    } else if (offensiveSpecial(abilityId)
                            && consumeEffect(sponges, affected.id())) {
                        deltas.merge(affected.id(), 2, Integer::sum);
                    } else if (offensiveSpecial(abilityId)
                            && consumeEffect(shields, affected.id())) {
                        // The counterspell absorbs this effect.
                    } else {
                        deltas.merge(affected.id(), delta, Integer::sum);
                    }
                    roundSpecials.add(specialPayload(abilityId, round, affected));
                }
            }
        }

        private boolean consumeEffect(ConcurrentHashMap<Integer, AtomicInteger> effects, int petId) {
            AtomicInteger effect = effects.get(petId);
            if (effect == null) {
                return false;
            }
            while (true) {
                int current = effect.get();
                if (current <= 0) {
                    return false;
                }
                if (effect.compareAndSet(current, current - 1)) {
                    return true;
                }
            }
        }

        private ConcurrentHashMap<Integer, AtomicInteger> defensiveEffects(int abilityId) {
            return switch (abilityId) {
                case 521 -> sponges;
                case 522 -> mirrors;
                default -> shields;
            };
        }

        private Map<String, Object> specialPayload(int abilityId, int round, PetRacePet affected) {
            int id = Math.floorMod(
                    match.ticket() * 1_000 + specialSequence.incrementAndGet(), Integer.MAX_VALUE);
            if (Set.of(502, 505, 511, 523).contains(abilityId)) {
                return Map.of(
                        "classId", "obstacle",
                        "id", id,
                        "typeId", abilityId,
                        "activeUntil", round + 1,
                        "position", Math.min(108, positions.getOrDefault(affected.id(), 0) + 4),
                        "affectedPets", List.of(affected.id()));
            }
            if (abilityId == 516) {
                return Map.of(
                        "classId", "teleport",
                        "affectedPets", List.of(affected.id()));
            }
            return Map.of(
                    "classId", "follower",
                    "id", id,
                    "typeId", abilityId,
                    "activeUntil", round + 1,
                    "affectedPets", List.of(affected.id()));
        }

        private boolean offensiveSpecial(int abilityId) {
            return Set.of(502, 504, 505, 507, 508, 510, 511, 513, 515, 523).contains(abilityId);
        }

        private boolean defensiveSpecial(int abilityId) {
            return Set.of(517, 521, 522).contains(abilityId);
        }

        private int specialDelta(int abilityId) {
            if (offensiveSpecial(abilityId) && abilityId != 510) {
                return -3;
            }
            return switch (abilityId) {
                case 506, 509, 512, 516, 519 -> 4;
                case 503, 514, 518 -> 2;
                default -> 0;
            };
        }

        private Map<String, Object> resultPayload(PetRacePet pet, boolean winner, boolean levelIncreased) {
            Map<String, Object> updatedPet = new LinkedHashMap<>(petPayload(pet));
            updatedPet.put("isWinner", winner);
            updatedPet.put("isLevelIncreased", levelIncreased);
            return Map.of(
                    "classId", "raceresults",
                    "roundsCount", roundCount,
                    "isWinner", winner,
                    "pet", updatedPet);
        }

        private int raceScore(PetRacePet pet) {
            int stats = pet.speed() * 5 + pet.agility() * 3 + pet.power() * 2;
            return stats * 1_000 + Math.floorMod(match.ticket() * 31 + pet.id() * 17, 1_000);
        }
    }

    private Map<String, Object> trackPayload() {
        List<Map<String, Object>> tiles = new ArrayList<>();
        for (int index = 0; index < 16; index++) {
            tiles.add(Map.of("classId", "tile", "id", "race-tile-" + index, "typeId", "2"));
        }
        return Map.of(
                "classId", "track",
                "stepsPerTile", 10,
                "tileWidth", 720,
                "numStartTiles", 2,
                "numEndTiles", 4,
                "tiles", tiles,
                "specials", List.of());
    }

    private Map<String, Object> petPayload(PetRacePet pet) {
        return Map.ofEntries(
                Map.entry("classId", "pet"),
                Map.entry("id", pet.id()),
                Map.entry("petTypeId", Integer.toString(pet.type())),
                Map.entry("name", pet.name()),
                Map.entry("health", pet.health()),
                Map.entry("speed", pet.speed()),
                Map.entry("agility", pet.agility()),
                Map.entry("power", pet.power()),
                Map.entry("experience", pet.experience()),
                Map.entry("level", pet.level()),
                Map.entry("abilities", normalizedAbilities(pet.abilitiesJson())),
                Map.entry("percentToNextLevel", PetRaceProgression.percentToNextLevel(pet.experience(), pet.level())),
                Map.entry("pointsForNextLevel", PetRaceProgression.pointsForNextLevel(pet.level())),
                Map.entry("isLevelIncreased", false),
                Map.entry("isWinner", false));
    }

    private PetRacePet botPet() {
        return new PetRacePet(900_000_000, 0, 7, "Tork_42", true, 5, 2, 2, 2, 0, 1, "[]");
    }

    private List<Integer> abilities(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            JsonNode node = json.readTree(value);
            if (!node.isArray()) {
                return List.of();
            }
            List<Integer> result = new ArrayList<>();
            node.forEach(entry -> result.add(entry.asInt()));
            return List.copyOf(result);
        } catch (JacksonException ignored) {
            return List.of();
        }
    }

    private List<Integer> normalizedAbilities(String value) {
        List<Integer> parsed = abilities(value);
        return parsed.isEmpty() ? List.of(501) : parsed;
    }

    private void send(RaceClient client, Map<String, Object> payload) {
        try {
            client.session().sendRaw(json.writeValueAsString(payload) + "\n");
        } catch (JacksonException exception) {
            throw new IllegalStateException("Could not encode pet race payload", exception);
        }
    }

    private record RaceClient(int ticket, int playerId, PetRacePet pet, PlayerSession session) {}
}
