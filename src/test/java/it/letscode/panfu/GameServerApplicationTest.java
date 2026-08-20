package it.letscode.panfu;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GameServerApplicationTest {

    @Test
    void applicationClassUsesExpectedNamespace() {
        assertThat(GameServerApplication.class.getPackageName()).isEqualTo("it.letscode.panfu");
    }
}
