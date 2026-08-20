package it.letscode.panfu.protocol;

import java.util.List;

public record IncomingPacket(int header, List<String> parameters) {

    public IncomingPacket {
        parameters = List.copyOf(parameters);
    }

    public PacketReader reader() {
        return new PacketReader(parameters);
    }
}
