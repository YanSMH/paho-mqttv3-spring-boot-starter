package com.gong.iot;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttPersistenceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;


public class MqttClientWrapper implements DisposableBean {

    private final Logger logger = LoggerFactory.getLogger(MqttClientWrapper.class);
    private final MqttClient client;
    private final EnhancedMqttFactory.SmartReconnectCallback callback;

    public MqttClientWrapper(MqttClient client, EnhancedMqttFactory.SmartReconnectCallback callback) {
        this.client = client;
        this.callback = callback;
    }

    public MqttClient getClient() {
        return client;
    }

    public EnhancedMqttFactory.SmartReconnectCallback getCallback() {
        return callback;
    }

    public void subscribe(String topic) throws MqttException {
        callback.addSubscribedTopic(topic);
        client.subscribe(topic);
    }

    public void publish(String topic, byte[] payload, int qos, boolean retained) throws MqttException {
        if(logger.isDebugEnabled()){
            logger.debug("发布消息到主题: {}, Qos: {}, Retained: {}", topic, qos, retained);
        }
        client.publish(topic, payload, 0, false);
    }

    public void publish(String topic, MqttMessage message) throws MqttException, MqttPersistenceException {
        if(logger.isDebugEnabled()){
            logger.debug("发布消息到主题: {}, Qos: {}", topic, message.getQos());
        }
        client.publish(topic, message);
    }

    public synchronized void shutdown() {
        try {
            String clientId = client.getClientId();
            if (client.isConnected()) {
                logger.info("MQTTClientWrapper clientId:{} closing...",clientId);
                client.disconnect(); // 优雅断开连接
            }
            logger.info("MQTTClientWrapper clientId:{} closed.", clientId);
            client.close(true);      // 强制关闭客户端
            if (callback != null) {
                logger.info("MQTTClientWrapper clientId:{} callback shutdown.",clientId);
                callback.shutdown();     // 关闭回调线程池
            }
        } catch (MqttException e) {
            logger.error("客户端关闭异常: {}", e.getMessage());
        }
    }

    @Override
    public void destroy() throws Exception {
        this.shutdown();
    }
}