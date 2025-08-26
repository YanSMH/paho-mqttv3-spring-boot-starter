package com.gong.iot;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "spring.mqtt")
public class MqttProperties {
    private String broker = "tcp://127.0.0.1:1883";
    private String clientId = "default-g-mqtt-client";
    private String username = "root";
    private boolean enabled = true;
    private char[] password;
    private boolean cleanSession = true;
    private List<String> topics = new ArrayList<>();
    private ReconnectConfig reconnect = new ReconnectConfig();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public static class ReconnectConfig {
        private int maxAttempts = Integer.MAX_VALUE;
        private long initialDelay = 5000;
        private double backoffFactor = 1.5;
        private boolean autoRetryInitialConnect = true;

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public long getInitialDelay() {
            return initialDelay;
        }

        public void setInitialDelay(long initialDelay) {
            this.initialDelay = initialDelay;
        }

        public double getBackoffFactor() {
            return backoffFactor;
        }

        public void setBackoffFactor(double backoffFactor) {
            this.backoffFactor = backoffFactor;
        }

        public boolean isAutoRetryInitialConnect() {
            return autoRetryInitialConnect;
        }

        public void setAutoRetryInitialConnect(boolean autoRetryInitialConnect) {
            this.autoRetryInitialConnect = autoRetryInitialConnect;
        }
    }

    public String getBroker() {
        return broker;
    }

    public void setBroker(String broker) {
        this.broker = broker;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public char[] getPassword() {
        return password;
    }

    public void setPassword(char[] password) {
        this.password = password;
    }

    public boolean isCleanSession() {
        return cleanSession;
    }

    public void setCleanSession(boolean cleanSession) {
        this.cleanSession = cleanSession;
    }

    public ReconnectConfig getReconnect() {
        return reconnect;
    }

    public void setReconnect(ReconnectConfig reconnect) {
        this.reconnect = reconnect;
    }

    public List<String> getTopics() {
        return topics;
    }

    public void setTopics(List<String> topics) {
        this.topics = topics;
    }
}