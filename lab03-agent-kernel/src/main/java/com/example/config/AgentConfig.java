package com.example.config;

import lombok.Getter;

/**
 * @author zhangjinshan
 * @description: Agent 配置信息
 * @create: 2026/10/24
 **/
@Getter
public class AgentConfig {

    private final String systemPrompt;

    private final Double temperature;

    private final Integer maxTokens;

    private final Integer maxRounds;

    private final Long connectTimeoutMs;   // 默认 5_000(连接要快,5s 还连不上就是网络有问题)

    private final Long requestTimeoutMs;   // 默认 60_000(生成慢,给足)

    private final Long silentTimeoutMs;    // 默认 15_000(慢思考模型给 15~30s)

    private AgentConfig(String systemPrompt, Double temperature, Integer maxTokens, Integer maxRounds) {
        this(systemPrompt, temperature, maxTokens, maxRounds, 5_000L, 60_000L, 15_000L);
    }

    private AgentConfig(String systemPrompt, Double temperature, Integer maxTokens, Integer maxRounds, Long connectTimeoutMs, Long requestTimeoutMs, Long silentTimeoutMs) {
        this.systemPrompt = systemPrompt;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.maxRounds = maxRounds;
        this.connectTimeoutMs = connectTimeoutMs;
        this.requestTimeoutMs = requestTimeoutMs;
        this.silentTimeoutMs = silentTimeoutMs;
    }

    public static class Builder {
        private String systemPrompt;
        private Double temperature = 0.7;
        private Integer maxTokens = 2048;
        private Integer maxRounds = 10;
        private Long connectTimeoutMs = 5_000L;
        private Long requestTimeoutMs = 60_000L;
        private Long silentTimeoutMs = 15_000L;

        public Builder systemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return this;
        }
        public Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }
        public Builder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }
        public Builder maxRounds(Integer maxRounds) {
            this.maxRounds = maxRounds;
            return this;
        }

        public Builder connectTimeoutMs(Long connectTimeoutMs) {
            this.connectTimeoutMs = connectTimeoutMs;
            return this;
        }

        public Builder requestTimeoutMs(Long requestTimeoutMs) {
            this.requestTimeoutMs = requestTimeoutMs;
            return this;
        }

        public Builder silentTimeoutMs(Long silentTimeoutMs) {
            this.silentTimeoutMs = silentTimeoutMs;
            return this;
        }

        public AgentConfig build() {
            if (systemPrompt == null || systemPrompt.isEmpty()) {
                throw new IllegalArgumentException("systemPrompt 不能为空");
            }
            if (temperature == null || temperature < 0 || temperature > 2) {
                throw new IllegalArgumentException("temperature 必须在 0-2 之间");
            }
            if (maxRounds == null || maxRounds <= 0) {
                throw new IllegalArgumentException("maxRounds 必须大于0");
            }
            if (maxTokens == null || maxTokens <= 0) {
                throw new IllegalArgumentException("maxTokens 必须大于0");
            }
            if (connectTimeoutMs < 0) {
                throw new IllegalArgumentException("connectTimeoutMs 必须大于0");
            }
            if (requestTimeoutMs < 0) {
                throw new IllegalArgumentException("requestTimeoutMs 必须大于0");
            }
            if (silentTimeoutMs < 0) {
                throw new IllegalArgumentException("silentTimeoutMs 必须大于0");
            }
            if (silentTimeoutMs > requestTimeoutMs) {
                throw new IllegalArgumentException("silentTimeoutMs 不能大于 requestTimeoutMs");
            }
            return new AgentConfig(systemPrompt, temperature, maxTokens, maxRounds,  connectTimeoutMs, requestTimeoutMs, silentTimeoutMs);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
