package it.letscode.panfu.transport;

public interface ClientConnection {

    String id();

    String remoteIp();

    void send(String payload);

    void close();
}
