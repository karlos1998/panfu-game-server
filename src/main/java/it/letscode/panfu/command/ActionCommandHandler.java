package it.letscode.panfu.command;

import it.letscode.panfu.protocol.IncomingPacket;
import it.letscode.panfu.protocol.OutgoingPacket;
import it.letscode.panfu.protocol.PacketHeaders;
import it.letscode.panfu.protocol.PacketReader;
import it.letscode.panfu.session.AudienceService;
import it.letscode.panfu.session.PlayerSession;
import it.letscode.panfu.session.SessionRegistry;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Component;

@Component
public final class ActionCommandHandler implements CommandHandler {

    private static final Set<String> THROWABLES = Set.of(
            "waterbomb", "slimebomb", "slimebombSprite", "sendFlyingCup", "flyingCup",
            "sendFlyingBottle", "flyingBottle", "sendPancake", "pancake", "flyingPillow",
            "sendFlyingBottle2", "flyingBottle2", "sendCake", "cake", "blueSlimebombSprite",
            "pinkSlimebombSprite", "icecubeSpell", "masterOfIce", "hole", "teleportation",
            "gameInvite", "gameInvite_41_");
    private static final Set<String> INVITATION_ACTIONS = Set.of(
            "gameInvite", "gameInviteAccepted", "invitedPlayerLoadGame", "gameInviteDenied");
    private final AudienceService audience;
    private final SessionRegistry sessions;

    public ActionCommandHandler(AudienceService audience, SessionRegistry sessions) {
        this.audience = audience;
        this.sessions = sessions;
    }

    @Override
    public Set<Integer> headers() {
        return Set.of(PacketHeaders.ACTION);
    }

    @Override
    public void handle(IncomingPacket packet, PlayerSession session) {
        long now = System.currentTimeMillis();
        if (session.lastActionAt() > 0 && now - session.lastActionAt() < 500) {
            return;
        }
        PacketReader reader = packet.reader();
        String action = limited(reader.readString(), 80);
        if (action.isBlank()) {
            return;
        }
        if (action.equals("throw")) {
            throwItem(reader, session, now);
            return;
        }
        if (INVITATION_ACTIONS.contains(action)) {
            invitation(action, reader, session, now);
            return;
        }
        if (action.equals("doSlideAnimation") && session.roomId() == 3) {
            slide(reader, session, now);
            return;
        }
        if (action.equals("doDivingAnimation") && session.roomId() == 3) {
            dive(reader, session, now);
            return;
        }
        if (action.equals("doSlideLakeAnimation") && session.roomId() == 39) {
            lakeSlide(reader, session, now);
            return;
        }

        OutgoingPacket response = base(session).writeString(action);
        session.lastAction(action);
        session.lastActionAt(now);
        if (THROWABLES.contains(action)) {
            session.send(response);
        } else {
            audience.room(session, response);
        }
    }

    private void throwItem(PacketReader reader, PlayerSession session, long now) {
        if (!THROWABLES.contains(session.lastAction())) {
            return;
        }
        int x = reader.readInt();
        int y = reader.readInt();
        String item = limited(reader.readString(), 80);
        int victim = reader.readInt();
        if (!THROWABLES.contains(item) || (victim > 0 && sessions.find(victim).isEmpty())) {
            return;
        }
        OutgoingPacket response = base(session)
                .writeString("throw")
                .writeInt(x)
                .writeInt(y)
                .writeString(item);
        if (victim == -1) {
            response.writeString("false");
        } else {
            response.writeInt(victim).writeString("false");
        }
        session.lastAction("throw");
        session.lastActionAt(now);
        audience.room(session, response);
    }

    private void invitation(String action, PacketReader reader, PlayerSession session, long now) {
        reader.readInt();
        int clientPlayerId = reader.readInt();
        int gameId = reader.readInt();
        int targetId = reader.readInt();
        if (targetId <= 0 || sessions.find(targetId).filter(target ->
                target.roomId() == session.roomId() && target.home() == session.home()).isEmpty()) {
            return;
        }
        int playerParameter = action.equals("gameInvite") ? 0 : session.playerId();
        OutgoingPacket response = base(session)
                .writeString(action)
                .writeInt(0)
                .writeInt(playerParameter)
                .writeInt(gameId)
                .writeInt(targetId)
                .writeString("false");
        session.lastActionAt(now);
        audience.room(session, response);
    }

    private void slide(PacketReader reader, PlayerSession session, long now) {
        reader.readInt();
        reader.readInt();
        reader.readString();
        int unknown = reader.readInt();
        session.lastActionAt(now);
        audience.room(session, base(session)
                .writeString("doSlideAnimation")
                .writeInt(ThreadLocalRandom.current().nextInt(134, 525))
                .writeInt(ThreadLocalRandom.current().nextInt(206, 296))
                .writeString("")
                .writeInt(unknown)
                .writeString("false"));
    }

    private void dive(PacketReader reader, PlayerSession session, long now) {
        int x = reader.readInt();
        int y = reader.readInt();
        int plank = reader.readInt();
        session.lastActionAt(now);
        audience.room(session, base(session)
                .writeString("doDivingAnimation")
                .writeInt(x)
                .writeInt(y)
                .writeInt(plank)
                .writeString("false"));
    }

    private void lakeSlide(PacketReader reader, PlayerSession session, long now) {
        session.lastActionAt(now);
        audience.room(session, base(session)
                .writeString("doSlideLakeAnimation")
                .writeInt(reader.readInt())
                .writeInt(reader.readInt())
                .writeInt(reader.readInt())
                .writeString(limited(reader.readString(), 80)));
    }

    private OutgoingPacket base(PlayerSession session) {
        return OutgoingPacket.header(PacketHeaders.ACTION_PERFORMED).writeInt(session.playerId());
    }

    private String limited(String value, int limit) {
        String clean = value == null ? "" : value.replace(";", "").replace("|", "");
        return clean.substring(0, Math.min(clean.length(), limit));
    }
}
