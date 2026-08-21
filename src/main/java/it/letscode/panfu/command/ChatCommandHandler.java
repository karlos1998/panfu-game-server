package it.letscode.panfu.command;

import it.letscode.panfu.persistence.chat.ChatMessageRepository;
import it.letscode.panfu.protocol.IncomingPacket;
import it.letscode.panfu.protocol.OutgoingPacket;
import it.letscode.panfu.protocol.PacketHeaders;
import it.letscode.panfu.session.AudienceService;
import it.letscode.panfu.session.PlayerSession;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public final class ChatCommandHandler implements CommandHandler {

    private static final Pattern TAG = Pattern.compile("<[^>]*>");
    private static final long CHAT_INTERVAL_MILLIS = 1_000;
    private static final long HARD_FLOOD_MILLIS = 100;
    private static final int MAX_MESSAGE_LENGTH = 120;
    private final AudienceService audience;
    private final ChatMessageRepository messages;

    public ChatCommandHandler(AudienceService audience, ChatMessageRepository messages) {
        this.audience = audience;
        this.messages = messages;
    }

    @Override
    public Set<Integer> headers() {
        return Set.of(PacketHeaders.CHAT, PacketHeaders.SAFE_CHAT, PacketHeaders.EMOTE);
    }

    @Override
    public void handle(IncomingPacket packet, PlayerSession session) {
        if (packet.header() == PacketHeaders.EMOTE) {
            emote(packet.reader().readInt(), session);
            return;
        }
        chat(packet.reader().readString(), session);
    }

    private void chat(String rawMessage, PlayerSession session) {
        long now = System.currentTimeMillis();
        long elapsed = now - session.lastChatAt();
        if (session.lastChatAt() > 0 && elapsed < HARD_FLOOD_MILLIS) {
            session.disconnect("CMD_CHAT > You are chatting too fast!");
            return;
        }
        if (session.lastChatAt() > 0 && elapsed < CHAT_INTERVAL_MILLIS) {
            return;
        }
        String message = sanitize(rawMessage);
        if (message.isBlank()) {
            return;
        }
        if (message.length() > MAX_MESSAGE_LENGTH) {
            session.disconnect("KICK_SHUTDOWN_MSG");
            return;
        }
        if (message.startsWith("#")) {
            int space = message.indexOf(' ');
            message = space >= 0 ? message.substring(space + 1).strip() : "";
        }
        if (message.isBlank()) {
            return;
        }
        session.lastChatAt(now);
        messages.record(session.playerId(), session.username(), session.roomId(), session.home(), message);
        String broadcastMessage = session.sheriff() > 0 ? "#FF0000 " + message : message;
        audience.room(session, OutgoingPacket.header(PacketHeaders.CHAT_MESSAGE)
                .writeInt(session.playerId())
                .writeString(broadcastMessage));
    }

    private void emote(int emoteId, PlayerSession session) {
        long now = System.currentTimeMillis();
        if (emoteId < 0 || emoteId > 10_000) {
            return;
        }
        if (session.lastEmoteAt() > 0 && now - session.lastEmoteAt() < 500) {
            session.disconnect("CMD_EMOTE > You are sending emotes too fast!");
            return;
        }
        session.lastEmoteAt(now);
        audience.room(session, OutgoingPacket.header(PacketHeaders.EMOTE_MESSAGE)
                .writeInt(session.playerId())
                .writeInt(emoteId));
    }

    private String sanitize(String value) {
        String withoutTags = TAG.matcher(value == null ? "" : value).replaceAll("");
        return withoutTags.replace(";", "").replace("|", "").strip();
    }
}
