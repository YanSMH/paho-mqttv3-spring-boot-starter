package com.gong.iot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * MQTT 消息处理器注册
 */
public class MqttHandlerRegistry {
    private static final Logger logger = LoggerFactory.getLogger(MqttHandlerRegistry.class);
    private final Map<Pattern, HandlerWrapper<?>> handlerMap = new ConcurrentHashMap<>();
    private final TypeFactory typeFactory = TypeFactory.defaultInstance();

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private Environment env;
    @Autowired
    public void registerHandlers(ObjectProvider<MqttMessageHandler<?>> handlers) {
        handlers.forEach(handler -> {
            MqttTopic annotation = handler.getClass().getAnnotation(MqttTopic.class);
            if (annotation != null) {
                Class<?> payloadType = resolvePayloadType(handler);
                registerHandler(handler, payloadType, annotation);
            }
        });
    }

    private void registerHandler(
            MqttMessageHandler<?> handler,
            Class<?> payloadType,
            MqttTopic annotation
    ) {
        //如果是spring ${}, 则替换为环境变量的值
        String value = env.resolvePlaceholders(annotation.value());
        String patternStr = value
                .replace("+", "[^/]+")
                .replace("#", ".*");

        Pattern pattern = Pattern.compile(patternStr);
        handlerMap.put(pattern, new HandlerWrapper<>(
                handler,
                payloadType,
                value,
                annotation.qos()
        ));

        logger.info("注册处理器 [主题: {}] => {}", value, handler.getClass());
    }

    @SuppressWarnings("unchecked")
    private <T> Class<T> resolvePayloadType(MqttMessageHandler<?> handler) {
        Type[] interfaces = handler.getClass().getGenericInterfaces();
        ParameterizedType type = (ParameterizedType) interfaces[0];
        Type actualType = type.getActualTypeArguments()[0];
        return (Class<T>) typeFactory.constructType(actualType).getRawClass();
    }

    public void processMessage(String topic, byte[] payload) {
        handlerMap.entrySet().stream()
                .filter(entry -> entry.getKey().matcher(topic).matches())
                .forEach(entry -> {
                    HandlerWrapper<?> wrapper = entry.getValue();
                    try {
                        Object message = objectMapper.readValue(payload, wrapper.payloadType);
                        handleMessageSafely(wrapper, message,topic);
                    } catch (IOException e) {
                        if(logger.isDebugEnabled()){
                            logger.debug("反序列化失败 [主题: {}]", topic, e);
                        }
                    }
                });
    }

    @SuppressWarnings("unchecked")
    private <T> void handleMessageSafely(HandlerWrapper<?> wrapper, Object message, String topic) {
        try {
            T typedMessage = (T) wrapper.payloadType.cast(message);
            ((MqttMessageHandler<T>) wrapper.handler).handle(topic,typedMessage);
        } catch (ClassCastException e) {
            logger.error("类型转换失败 [预期类型: {}]", wrapper.payloadType.getName(), e);
        }
    }

    private static class HandlerWrapper<T> {
        final MqttMessageHandler<T> handler;
        final Class<?> payloadType; // 修改为 Class<?>
        final String originalTopic;
        final int qos;

        HandlerWrapper(MqttMessageHandler<T> handler,
                       Class<?> payloadType, // 修改为 Class<?>
                       String originalTopic,
                       int qos) {
            this.handler = handler;
            this.payloadType = payloadType;
            this.originalTopic = originalTopic;
            this.qos = qos;
        }
    }

    // 获取所有需要订阅的主题
    public List<String> getSubscribedTopics() {
        return handlerMap.values().stream()
                .map(wrapper -> wrapper.originalTopic)
                .distinct()
                .collect(Collectors.toList());
    }
}