package it.letscode.panfu.protocol;

import it.letscode.panfu.config.GameServerProperties;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public final class PacketCodec {

    private final int maxFrameBytes;
    private final int maxParameters;

    public PacketCodec(GameServerProperties properties) {
        this.maxFrameBytes = properties.limits().maxFrameBytes();
        this.maxParameters = properties.limits().maxPacketParameters();
    }

    public List<IncomingPacket> decodeCompleteFrames(String payload) {
        if (payload == null || payload.isEmpty()) {
            return List.of();
        }
        if (payload.getBytes(StandardCharsets.UTF_8).length > maxFrameBytes) {
            throw new ProtocolException("Payload exceeds the configured frame limit");
        }

        List<IncomingPacket> packets = new ArrayList<>();
        for (String frame : payload.replace("\r", "").replace("\n", "").split("\\|")) {
            if (frame.isEmpty()) {
                continue;
            }
            String[] parts = frame.split(";", -1);
            if (parts.length - 1 > maxParameters) {
                throw new ProtocolException("Packet contains too many parameters");
            }
            int header;
            try {
                header = Integer.parseInt(parts[0]);
            } catch (NumberFormatException exception) {
                throw new ProtocolException("Packet header is not an integer", exception);
            }
            packets.add(new IncomingPacket(header, Arrays.asList(parts).subList(1, parts.length)));
        }
        return List.copyOf(packets);
    }

    public String encode(OutgoingPacket packet) {
        StringBuilder encoded = new StringBuilder(Integer.toString(packet.header()));
        for (String parameter : packet.parameters()) {
            encoded.append(';').append(parameter);
        }
        return encoded.append('|').toString();
    }
}
