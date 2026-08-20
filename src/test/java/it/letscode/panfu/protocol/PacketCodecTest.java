package it.letscode.panfu.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import it.letscode.panfu.config.GameServerProperties;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class PacketCodecTest {

    private final PacketCodec codec = new PacketCodec(new GameServerProperties(
            1,
            new GameServerProperties.Network("/game", 9595, true),
            new GameServerProperties.Security(List.of("http://localhost"), "secret", Duration.ofSeconds(30)),
            new GameServerProperties.Limits(1000, 8, 10, Duration.ofSeconds(30), Duration.ofMinutes(5)),
            new GameServerProperties.Rewards(Duration.ofSeconds(2), 100000, 500)));

    @Test
    void decodesAllFramesAndPreservesEmptyParameters() {
        List<IncomingPacket> packets = codec.decodeCompleteFrames("20;1;2;0|40;;hello|\r\n");

        assertThat(packets).containsExactly(
                new IncomingPacket(20, List.of("1", "2", "0")),
                new IncomingPacket(40, List.of("", "hello")));
    }

    @Test
    void readsLegacyIntegerFormatsAndMissingValues() {
        PacketReader reader = new IncomingPacket(20, List.of("12.75", "bad")).reader();

        assertThat(reader.readInt()).isEqualTo(12);
        assertThat(reader.readInt()).isEqualTo(-1);
        assertThat(reader.readInt()).isEqualTo(-1);
        assertThat(reader.remaining()).isZero();
    }

    @Test
    void encodesExactLegacyWireFormat() {
        OutgoingPacket packet = OutgoingPacket.header(61).writeInt(7).writeInt(1).writeString("online");

        assertThat(codec.encode(packet)).isEqualTo("61;7;1;online|");
    }

    @Test
    void rejectsInvalidHeadersAndConfiguredLimits() {
        assertThatThrownBy(() -> codec.decodeCompleteFrames("login;1|"))
                .isInstanceOf(ProtocolException.class);
        assertThatThrownBy(() -> codec.decodeCompleteFrames("20;1;2;3;4;5;6;7;8;9|"))
                .isInstanceOf(ProtocolException.class);
        assertThatThrownBy(() -> codec.decodeCompleteFrames("20;" + "x".repeat(1000) + "|"))
                .isInstanceOf(ProtocolException.class);
    }
}
