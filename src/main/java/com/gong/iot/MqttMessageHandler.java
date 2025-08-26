package com.gong.iot;

public interface MqttMessageHandler <T>{
    void handle(String topic,T message);
}