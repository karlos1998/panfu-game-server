package it.letscode.panfu.command;

import it.letscode.panfu.protocol.IncomingPacket;
import it.letscode.panfu.protocol.OutgoingPacket;
import it.letscode.panfu.protocol.PacketHeaders;
import it.letscode.panfu.protocol.PacketReader;
import it.letscode.panfu.session.AudienceService;
import it.letscode.panfu.session.PlayerSession;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public final class MovementCommandHandler implements CommandHandler {

    private static final int MAX_COORDINATE = 10_000;
    private final AudienceService audience;

    public MovementCommandHandler(AudienceService audience) {
        this.audience = audience;
    }

    @Override
    public Set<Integer> headers() {
        return Set.of(
                PacketHeaders.MOVE,
                PacketHeaders.FORCE_COORDINATES,
                PacketHeaders.ROTATE,
                PacketHeaders.SET_PLAYER_STATUS);
    }

    @Override
    public void handle(IncomingPacket packet, PlayerSession session) {
        PacketReader reader = packet.reader();
        switch (packet.header()) {
            case PacketHeaders.MOVE -> move(packet.parameters().size(), reader, session);
            case PacketHeaders.FORCE_COORDINATES -> forceCoordinates(reader, session);
            case PacketHeaders.ROTATE -> session.rotation(clamp(reader.readInt(), 0, 360));
            case PacketHeaders.SET_PLAYER_STATUS -> session.status(clamp(reader.readInt(), 0, 6));
            default -> throw new IllegalArgumentException("Unsupported movement command");
        }
    }

    private void move(int parameterCount, PacketReader reader, PlayerSession session) {
        int fromX = session.x();
        int fromY = session.y();
        if (parameterCount >= 5) {
            fromX = reader.readInt();
            fromY = reader.readInt();
        }
        int toX = reader.readInt();
        int toY = reader.readInt();
        int type = reader.readInt();
        if (!validCoordinate(fromX) || !validCoordinate(fromY)
                || !validCoordinate(toX) || !validCoordinate(toY)
                || type < 0 || type > 8) {
            session.disconnect("Error: CMD_MOVE, invalid movement.");
            return;
        }
        session.x(toX);
        session.y(toY);
        session.interactingWith(-1);
        int duration = type == 0 ? walkingDuration(fromX, fromY, toX, toY) : 1000;
        audience.room(session, OutgoingPacket.header(PacketHeaders.AVATAR_MOVED)
                .writeInt(session.playerId())
                .writeInt(duration)
                .writeInt(toX)
                .writeInt(toY)
                .writeInt(type));
    }

    private void forceCoordinates(PacketReader reader, PlayerSession session) {
        int x = reader.readInt();
        int y = reader.readInt();
        if (validCoordinate(x) && validCoordinate(y)) {
            session.x(x);
            session.y(y);
        }
    }

    private int walkingDuration(int fromX, int fromY, int toX, int toY) {
        double x = (long) toX - fromX;
        double y = (long) toY - fromY;
        return Math.max(1, (int) (Math.sqrt(x * x + y * y) / 0.1));
    }

    private boolean validCoordinate(int value) {
        return value >= -MAX_COORDINATE && value <= MAX_COORDINATE;
    }

    private int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(value, maximum));
    }
}
