package it.letscode.panfu.session;

import it.letscode.panfu.protocol.OutgoingPacket;
import it.letscode.panfu.protocol.P2pHeaders;
import it.letscode.panfu.protocol.PacketCodec;
import it.letscode.panfu.protocol.PacketHeaders;
import it.letscode.panfu.transport.ClientConnection;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class PlayerSession {

    private final ClientConnection connection;
    private final PacketCodec codec;
    private final Instant connectedAt = Instant.now();
    private volatile boolean authenticated;
    private volatile boolean disconnected;
    private volatile int playerId = -1;
    private volatile String username = "";
    private volatile int sheriff;
    private volatile int goldPanda;
    private volatile int roomId = -1;
    private volatile boolean home;
    private volatile int subRoom;
    private volatile int x;
    private volatile int y;
    private volatile int rotation;
    private volatile int status;
    private volatile int interactingWith = -1;
    private volatile int currentGame = -1;
    private volatile UUID currentRound;
    private volatile Instant roundStartedAt;
    private volatile Integer multiplayerPartnerId;
    private volatile long lastChatAt;
    private volatile long lastEmoteAt;
    private volatile long lastActionAt;
    private volatile String lastAction = "";
    private volatile AvatarSnapshot avatarSnapshot;
    private volatile AvatarUpdateSnapshot avatarUpdateSnapshot;

    public PlayerSession(ClientConnection connection, PacketCodec codec) {
        this.connection = Objects.requireNonNull(connection);
        this.codec = Objects.requireNonNull(codec);
    }

    public synchronized void authenticate(int id, String name, int sheriff, int goldPanda) {
        if (authenticated) {
            throw new IllegalStateException("Session is already authenticated");
        }
        this.playerId = id;
        this.username = sanitize(name, ':', ',', ';', '|');
        this.sheriff = sheriff;
        this.goldPanda = goldPanda;
        this.authenticated = true;
    }

    public synchronized void joinRoom(int destinationRoom, int destinationX, int destinationY) {
        this.roomId = destinationRoom;
        this.x = destinationX;
        this.y = destinationY;
        this.home = false;
        this.subRoom = 0;
        this.interactingWith = -1;
    }

    public synchronized void joinHome(int ownerId, int destinationX, int destinationY) {
        this.roomId = ownerId;
        this.x = destinationX;
        this.y = destinationY;
        this.home = true;
        this.subRoom = 0;
        this.interactingWith = -1;
    }

    public synchronized UUID startGame(int gameId) {
        this.currentGame = gameId;
        this.currentRound = UUID.randomUUID();
        this.roundStartedAt = Instant.now();
        return currentRound;
    }

    public synchronized void finishGame() {
        this.currentGame = -1;
        this.currentRound = null;
        this.roundStartedAt = null;
        this.multiplayerPartnerId = null;
    }

    public void send(OutgoingPacket packet) {
        if (!disconnected) {
            connection.send(codec.encode(packet));
        }
    }

    public void sendRaw(String payload) {
        if (!disconnected) {
            connection.send(payload);
        }
    }

    public synchronized void disconnect(String reason) {
        if (disconnected) {
            return;
        }
        if (reason != null && !reason.isBlank()) {
            connection.send(codec.encode(OutgoingPacket.header(PacketHeaders.DISCONNECT_RESPONSE).writeString(reason)));
        }
        disconnected = true;
        connection.close();
    }

    public OutgoingPacket setAvatarPacket() {
        return OutgoingPacket.header(PacketHeaders.SET_AVATAR)
                .writeInt(roomId)
                .writeInt(playerId)
                .writeInt(x)
                .writeInt(y)
                .writeString(username);
    }

    public OutgoingPacket createAvatarPacket() {
        AvatarSnapshot snapshot = avatarSnapshot;
        if (snapshot == null) {
            return null;
        }
        return OutgoingPacket.header(PacketHeaders.PLAYER_TO_PLAYER_RESPONSE)
                .writeInt(playerId)
                .writeInt(P2pHeaders.CREATE_AVATAR)
                .writeInt(snapshot.x())
                .writeInt(snapshot.y())
                .writeString(snapshot.action())
                .writeInt(snapshot.rotation())
                .writeString(snapshot.petType())
                .writeInt(sheriff)
                .writeString(playerInfo(snapshot.clothes()));
    }

    public OutgoingPacket updateAvatarPacket() {
        AvatarUpdateSnapshot snapshot = avatarUpdateSnapshot;
        if (snapshot == null) {
            return null;
        }
        return OutgoingPacket.header(PacketHeaders.PLAYER_TO_PLAYER_RESPONSE)
                .writeInt(playerId)
                .writeInt(P2pHeaders.UPDATE_AVATAR)
                .writeString(snapshot.pet())
                .writeInt(sheriff)
                .writeString(playerInfo(snapshot.playerString()));
    }

    public String playerString() {
        return "%d:%d:%d:%s:%d:%d:0".formatted(playerId, x, y, username, status, rotation);
    }

    public synchronized void storeAvatar(int x, int y, String action, int rotation, String petType, String clothes) {
        this.x = x;
        this.y = y;
        this.avatarSnapshot = new AvatarSnapshot(
                x,
                y,
                sanitize(action, ';', '|'),
                rotation,
                sanitize(petType, ';', '|'),
                sanitize(clothes, ';', '|'));
    }

    public synchronized void updateAvatar(String pet, String playerString) {
        this.avatarUpdateSnapshot = new AvatarUpdateSnapshot(
                sanitize(pet, ';', '|'),
                sanitize(playerString, ';', '|'));
    }

    public String playerInfo(String clothes) {
        String safeClothes = sanitize(clothes, ';', '|');
        if (safeClothes.isBlank() || safeClothes.equals("-1") || safeClothes.equals(username)) {
            return username;
        }
        return safeClothes.startsWith(username + ",") ? safeClothes : username + "," + safeClothes;
    }

    private String sanitize(String value, char... forbidden) {
        String sanitized = value == null ? "" : value;
        for (char character : forbidden) {
            sanitized = sanitized.replace(Character.toString(character), "");
        }
        return sanitized;
    }

    public ClientConnection connection() { return connection; }
    public Instant connectedAt() { return connectedAt; }
    public boolean authenticated() { return authenticated; }
    public boolean disconnected() { return disconnected; }
    public int playerId() { return playerId; }
    public String username() { return username; }
    public int sheriff() { return sheriff; }
    public int goldPanda() { return goldPanda; }
    public int roomId() { return roomId; }
    public boolean home() { return home; }
    public int subRoom() { return subRoom; }
    public void subRoom(int value) { this.subRoom = value; }
    public int x() { return x; }
    public void x(int value) { this.x = value; }
    public int y() { return y; }
    public void y(int value) { this.y = value; }
    public int rotation() { return rotation; }
    public void rotation(int value) { this.rotation = value; }
    public int status() { return status; }
    public void status(int value) { this.status = value; }
    public int interactingWith() { return interactingWith; }
    public void interactingWith(int value) { this.interactingWith = value; }
    public int currentGame() { return currentGame; }
    public UUID currentRound() { return currentRound; }
    public Instant roundStartedAt() { return roundStartedAt; }
    public Integer multiplayerPartnerId() { return multiplayerPartnerId; }
    public void multiplayerPartnerId(Integer value) { this.multiplayerPartnerId = value; }
    public long lastChatAt() { return lastChatAt; }
    public void lastChatAt(long value) { this.lastChatAt = value; }
    public long lastEmoteAt() { return lastEmoteAt; }
    public void lastEmoteAt(long value) { this.lastEmoteAt = value; }
    public long lastActionAt() { return lastActionAt; }
    public void lastActionAt(long value) { this.lastActionAt = value; }
    public String lastAction() { return lastAction; }
    public void lastAction(String value) { this.lastAction = value; }

    public record AvatarSnapshot(int x, int y, String action, int rotation, String petType, String clothes) {}

    public record AvatarUpdateSnapshot(String pet, String playerString) {}
}
