package it.letscode.panfu.transport;

import it.letscode.panfu.protocol.ProtocolException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class FrameAccumulator {

    private final int maxBytes;
    private final StringBuilder pending = new StringBuilder();

    public FrameAccumulator(int maxBytes) {
        this.maxBytes = maxBytes;
    }

    public synchronized List<String> append(String chunk) {
        if (chunk == null || chunk.isEmpty()) {
            return List.of();
        }
        pending.append(chunk);
        if (pending.toString().getBytes(StandardCharsets.UTF_8).length > maxBytes) {
            pending.setLength(0);
            throw new ProtocolException("Accumulated frame exceeds configured limit");
        }
        List<String> complete = new ArrayList<>();
        int delimiter;
        while ((delimiter = pending.indexOf("|")) >= 0) {
            complete.add(pending.substring(0, delimiter + 1));
            pending.delete(0, delimiter + 1);
        }
        return List.copyOf(complete);
    }

    public synchronized int pendingCharacters() {
        return pending.length();
    }
}
