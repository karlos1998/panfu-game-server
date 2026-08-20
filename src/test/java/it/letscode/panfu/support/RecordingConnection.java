package it.letscode.panfu.support;

import it.letscode.panfu.transport.ClientConnection;
import java.util.ArrayList;
import java.util.List;

public final class RecordingConnection implements ClientConnection {

    private final String id;
    private final List<String> messages = new ArrayList<>();
    private boolean closed;

    public RecordingConnection(String id) {
        this.id = id;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String remoteIp() {
        return "127.0.0.1";
    }

    @Override
    public void send(String payload) {
        messages.add(payload);
    }

    @Override
    public void close() {
        closed = true;
    }

    public List<String> messages() {
        return List.copyOf(messages);
    }

    public boolean closed() {
        return closed;
    }
}
