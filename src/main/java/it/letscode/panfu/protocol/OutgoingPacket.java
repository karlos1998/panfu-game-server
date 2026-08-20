package it.letscode.panfu.protocol;

import java.util.ArrayList;
import java.util.List;

public final class OutgoingPacket {

    private final int header;
    private final List<String> parameters = new ArrayList<>();

    private OutgoingPacket(int header) {
        this.header = header;
    }

    public static OutgoingPacket header(int header) {
        return new OutgoingPacket(header);
    }

    public OutgoingPacket writeInt(int value) {
        parameters.add(Integer.toString(value));
        return this;
    }

    public OutgoingPacket writeString(String value) {
        parameters.add(value == null ? "" : value);
        return this;
    }

    public int header() {
        return header;
    }

    public List<String> parameters() {
        return List.copyOf(parameters);
    }
}
