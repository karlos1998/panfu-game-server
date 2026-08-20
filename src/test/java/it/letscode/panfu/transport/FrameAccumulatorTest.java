package it.letscode.panfu.transport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import it.letscode.panfu.protocol.ProtocolException;
import org.junit.jupiter.api.Test;

class FrameAccumulatorTest {

    @Test
    void joinsFragmentsAndReturnsEveryCompleteFrame() {
        FrameAccumulator accumulator = new FrameAccumulator(100);

        assertThat(accumulator.append("20;1;")).isEmpty();
        assertThat(accumulator.append("2|40;hello|partial"))
                .containsExactly("20;1;2|", "40;hello|");
        assertThat(accumulator.pendingCharacters()).isEqualTo("partial".length());
    }

    @Test
    void rejectsAnUnboundedPartialFrame() {
        FrameAccumulator accumulator = new FrameAccumulator(4);

        assertThatThrownBy(() -> accumulator.append("12345"))
                .isInstanceOf(ProtocolException.class);
        assertThat(accumulator.pendingCharacters()).isZero();
    }
}
