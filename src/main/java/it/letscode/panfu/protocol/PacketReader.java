package it.letscode.panfu.protocol;

import java.util.List;

public final class PacketReader {

    private final List<String> parameters;
    private int position;

    PacketReader(List<String> parameters) {
        this.parameters = parameters;
    }

    public int readInt() {
        String value = readString();
        int decimalSeparator = value.indexOf('.');
        if (decimalSeparator >= 0) {
            value = value.substring(0, decimalSeparator);
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    public String readString() {
        if (!hasRemaining()) {
            position++;
            return "";
        }
        return parameters.get(position++);
    }

    public boolean hasRemaining() {
        return position < parameters.size();
    }

    public int remaining() {
        return Math.max(0, parameters.size() - position);
    }
}
