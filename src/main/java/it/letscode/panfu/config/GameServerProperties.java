package it.letscode.panfu.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("panfu.game-server")
public record GameServerProperties(
        @Positive int serverId,
        @Valid Network network,
        @Valid Security security,
        @Valid Limits limits,
        @Valid Rewards rewards) {

    public record Network(
            @NotBlank String websocketPath,
            @Min(1) @Max(65535) int legacyTcpPort,
            boolean legacyTcpEnabled) {}

    public record Security(
            @NotEmpty List<@NotBlank String> allowedOrigins,
            @NotBlank String internalApiSecret,
            @Positive Duration internalRequestTtl) {}

    public record Limits(
            @Positive int maxFrameBytes,
            @Positive int maxPacketParameters,
            @Positive int maxConnectionsPerIp,
            @Positive Duration loginTimeout,
            @Positive Duration idleTimeout) {}

    public record Rewards(
            @Positive Duration minimumRoundDuration,
            @Positive int maxScorePerRound,
            @Positive int maxCoinsPerRound) {}
}
