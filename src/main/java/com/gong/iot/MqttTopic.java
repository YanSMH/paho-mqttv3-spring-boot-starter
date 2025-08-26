package com.gong.iot;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface MqttTopic {
    String value();
    /**
     * 消息质量等级（默认0）
     */
    int qos() default 0;
}
