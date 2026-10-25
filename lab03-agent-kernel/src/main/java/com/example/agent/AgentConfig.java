package com.example.agent;

/**
 * @author zhangjinshan
 * @description: Agent 配置信息
 * @create: 2026/10/24
 **/
public class AgentConfig {

    private final String systemPrompt;

    private final Double temperature;

    private final Integer maxTokens;

    private final Integer maxRounds;

    private AgentConfig(String systemPrompt, Double temperature, Integer maxTokens, Integer maxRounds) {
        this.systemPrompt = systemPrompt;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.maxRounds = maxRounds;
    }

    public static class Builder {
        private String systemPrompt;
        private Double temperature = 0.7;
        private Integer maxTokens = 2048;
        private Integer maxRounds = 10;

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
            return new AgentConfig(systemPrompt, temperature, maxTokens, maxRounds);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public Double getTemperature() {
        return temperature;
    }

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public Integer getMaxRounds() {
        return maxRounds;
    }
}
