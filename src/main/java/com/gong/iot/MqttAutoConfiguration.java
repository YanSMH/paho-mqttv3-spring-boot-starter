package com.gong.iot;

import org.eclipse.paho.client.mqttv3.*;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.util.UUID;

@Configuration
@ConditionalOnClass(MqttClient.class)
@ConditionalOnProperty(prefix = "spring.mqtt", name = "broker")
@EnableConfigurationProperties(MqttProperties.class)
@Import(MqttHandlerRegistry.class)
public class MqttAutoConfiguration {

    private final Logger logger = org.slf4j.LoggerFactory.getLogger(MqttAutoConfiguration.class);

    private final MqttProperties properties;

    public MqttAutoConfiguration(MqttProperties properties) {
        this.properties = properties;
        logger.info("MQTT 自动配置初始化，broker: {}", properties.getBroker());
    }

    @Bean
    @ConditionalOnMissingBean
    public EnhancedMqttFactory enhancedMQTTFactory(@Autowired(required = false) MqttCallback mqttCallback) {
        logger.info("创建 EnhancedMQTTFactory 实例");
        EnhancedMqttFactory.ReconnectConfig reconnectConfig = new EnhancedMqttFactory.ReconnectConfig()
                .maxAttempts(properties.getReconnect().getMaxAttempts())
                .initialDelay(properties.getReconnect().getInitialDelay())
                .backoffFactor(properties.getReconnect().getBackoffFactor())
                .autoRetryInitialConnect(properties.getReconnect().isAutoRetryInitialConnect());

        EnhancedMqttFactory.Builder builder = new EnhancedMqttFactory.Builder(properties.getBroker())
                .clientId(properties.getClientId() != null ? properties.getClientId() : UUID.randomUUID().toString())
                .cleanSession(properties.isCleanSession())
                .reconnectConfig(reconnectConfig);

        if (properties.getUsername() != null && properties.getPassword() != null) {
            logger.info("设置 MQTT 客户端认证信息");
            builder.credentials(properties.getUsername(), properties.getPassword());
        }

        if (mqttCallback != null) {
            logger.info("设置自定义 MQTT 回调");
            builder.callback(mqttCallback);
        }

        return builder.build();
    }


    @Bean
    @ConditionalOnMissingBean
    public MqttClientWrapper mqttClientWrapper(
            EnhancedMqttFactory factory,
            MqttProperties properties,
            MqttHandlerRegistry registry) throws Exception {

        logger.info("创建 MQTT 客户端包装类实例");
        MqttClientWrapper wrapper = factory.create();
        
        // 自动订阅配置的 Topic
        if (!properties.getTopics().isEmpty()) {
            logger.info("自动订阅配置的主题: {}", properties.getTopics());
            for (String topic : properties.getTopics()) {
                wrapper.subscribe(topic);
            }
        }
        // 自动订阅所有处理器关注的Topic
        registry.getSubscribedTopics().forEach(topic -> {
            try {
                wrapper.subscribe(topic);
                logger.info("自动订阅MQTT主题: {}", topic);
            } catch (MqttException e) {
                logger.error("订阅主题失败: {}", topic, e);
            }
        });

        return wrapper;
    }


    @Bean
    @ConditionalOnMissingBean
    public MqttCallback defaultMqttCallback(MqttHandlerRegistry registry) {
        logger.info("创建默认 MQTT 回调实例");
        return new MqttCallback() {
            @Override
            public void connectionLost(Throwable cause) {
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) {
                // 默认处理逻辑
                //mqttMessageHandlers
                registry.processMessage(topic, message.getPayload());
                logger.info("收到消息 [主题: {} Qos: {}] 内容: {}", topic, message.getQos(), new String(message.getPayload()));
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
            }
        };
    }

}