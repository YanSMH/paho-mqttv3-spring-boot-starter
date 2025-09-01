# Paho MQTTv3 Spring Boot Starter

[![Spring Boot](https://img.shields.io/badge/spring--boot-2.5.15-blue.svg)](https://spring.io/projects/spring-boot)

一个基于 Eclipse Paho MQTT v3 客户端的 Spring Boot Starter，用于简化 MQTT 客户端在 Spring Boot 项目中的集成和使用。

## 功能特性

- 🚀 **自动配置**: 自动配置 MQTT 客户端连接
- 🔁 **智能重连**: 支持断线重连和连接失败重试机制
- 📝 **注解驱动**: 通过 [@MqttTopic](file://D:\paho-mqttv3-spring-boot-starter\src\main\java\com\gong\iot\MqttTopic.java#L5-L12) 注解轻松订阅主题
- ⚙️ **线程池管理**: 可配置的线程池用于处理 MQTT 消息回调
- 📦 **开箱即用**: 简单的配置即可在 Spring Boot 项目中使用 MQTT
- 🛠️ **灵活扩展**: 支持自定义消息处理器和回调

## 安装

添加 Maven 依赖到您的项目中：

```xml
<dependency>
    <groupId>com.gong.iot</groupId>
    <artifactId>paho-mqttv3-spring-boot-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

## 配置

在 `application.yml` 或 `application.properties` 中添加以下配置：

### 基础配置

```yaml
spring:
  mqtt:
    # 是否启用 MQTT (默认: true)
    enabled: true
    
    # MQTT Broker 地址 (默认: tcp://127.0.0.1:1883)
    broker: tcp://127.0.0.1:1883
    
    # 客户端 ID (默认: default-g-mqtt-client)
    client-id: my-mqtt-client
    
    # 用户名 (默认: root)
    username: root
    
    # 密码
    password: password
    
    # 是否使用清洁会话 (默认: true)
    clean-session: true
```

### 主题订阅配置

```yaml
spring:
  mqtt:
    # 自动订阅的主题列表
    topics:
      - topic1
      - topic2
```

### 重连配置

```yaml
spring:
  mqtt:
    # 重连配置
    reconnect:
      # 最大重连尝试次数 (默认: Integer.MAX_VALUE)
      max-attempts: 100
      
      # 初始重连延迟（毫秒）(默认: 5000)
      initial-delay: 5000
      
      # 重连延迟退避因子 (默认: 1.5)
      backoff-factor: 1.5
      
      # 是否自动重试初始连接 (默认: true)
      auto-retry-initial-connect: true
```

### 线程池配置

```yaml
spring:
  mqtt:
    # 线程池配置
    thread-pool:
      # 核心线程数 (默认: 10)
      core-pool-size: 10
      
      # 最大线程数 (默认: 200)
      max-pool-size: 200
      
      # 队列容量 (默认: 400)
      queue-capacity: 400
      
      # 线程名称前缀 (默认: mqtt-callback-)
      thread-name-prefix: mqtt-callback-
      
      # 线程保持存活时间（毫秒）(默认: 60)
      keep-alive-time: 60
```

## 使用方法

### 1. 消息处理

创建消息处理器处理特定主题的消息：

```java
import com.gong.iot.MqttMessageHandler;
import com.gong.iot.MqttTopic;
import org.springframework.stereotype.Component;

@Component
@MqttTopic("sensor/temperature")
public class TemperatureHandler implements MqttMessageHandler<String> {
    @Override
    public void handle(String topic, String message) {
        System.out.println("Received temperature: " + message);
    }
}
```

支持通配符主题：

```java
@Component
@MqttTopic("sensor/+/temperature")
public class TemperatureHandler implements MqttMessageHandler<Map<String, Object>> {
    @Override
    public void handle(String topic, Map<String, Object> message) {
        System.out.println("Received temperature data: " + message);
    }
}
```

支持 Spring 占位符：

```java
@Component
@MqttTopic("${mqtt.topic.device}")
public class DeviceMessageHandler implements MqttMessageHandler<String> {
    @Override
    public void handle(String topic, String message) {
        System.out.println("Received device message: " + message);
    }
}
```

### 2. 发布消息

注入 [MqttClientWrapper](file://D:\paho-mqttv3-spring-boot-starter\src\main\java\com\gong\iot\MqttClientWrapper.java#L11-L71) 来发布消息：

```java
import com.gong.iot.MqttClientWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.eclipse.paho.client.mqttv3.MqttMessage;

@Service
public class MessageService {
    
    @Autowired
    private MqttClientWrapper mqttClient;
    
    public void publishMessage(String topic, String payload) throws Exception {
        MqttMessage message = new MqttMessage(payload.getBytes());
        message.setQos(1);
        mqttClient.publish(topic, message);
    }
}
```

或者使用字节数组方式：

```java
public void publishMessage(String topic, byte[] payload) throws Exception {
    mqttClient.publish(topic, payload, 1, false);
}
```

### 3. 手动订阅主题

```java
@Autowired
private MqttClientWrapper mqttClient;

public void subscribeTopic(String topic) throws Exception {
    mqttClient.subscribe(topic);
}
```

## 核心组件

- [MqttClientWrapper](file://D:\paho-mqttv3-spring-boot-starter\src\main\java\com\gong\iot\MqttClientWrapper.java#L11-L71): MQTT 客户端包装类，提供发布、订阅等操作
- [MqttMessageHandler](file://D:\paho-mqttv3-spring-boot-starter\src\main\java\com\gong\iot\MqttMessageHandler.java#L2-L4): 消息处理器接口，用于处理特定主题的消息
- [MqttTopic](file://D:\paho-mqttv3-spring-boot-starter\src\main\java\com\gong\iot\MqttTopic.java#L5-L12): 注解，用于标记消息处理器关注的主题
- [MqttProperties](file://D:\paho-mqttv3-spring-boot-starter\src\main\java\com\gong\iot\MqttProperties.java#L7-L178): 配置属性类，映射所有 MQTT 相关配置
- [EnhancedMqttFactory](file://D:\paho-mqttv3-spring-boot-starter\src\main\java\com\gong\iot\EnhancedMqttFactory.java#L15-L414): 增强的 MQTT 工厂类，支持智能重连功能

## 自定义回调

如需自定义 MQTT 回调，可以创建一个 `MqttCallback` bean：

```java
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MqttConfig {
    
    @Bean
    public MqttCallback customMqttCallback() {
        return new MqttCallback() {
            @Override
            public void connectionLost(Throwable cause) {
                // 连接丢失处理
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                // 消息到达处理
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                // 消息发送完成处理
            }
        };
    }
}
```

## 架构设计

本项目采用以下设计模式和架构：

1. **工厂模式**: [EnhancedMqttFactory](file://D:\paho-mqttv3-spring-boot-starter\src\main\java\com\gong\iot\EnhancedMqttFactory.java#L15-L414) 负责创建 MQTT 客户端实例
2. **装饰器模式**: [MqttClientWrapper](file://D:\paho-mqttv3-spring-boot-starter\src\main\java\com\gong\iot\MqttClientWrapper.java#L11-L71) 包装原始 MQTT 客户端，提供更便捷的操作接口
3. **观察者模式**: 通过 [MqttHandlerRegistry](file://D:\paho-mqttv3-spring-boot-starter\src\main\java\com\gong\iot\MqttHandlerRegistry.java#L15-L123) 注册和管理消息处理器
4. **策略模式**: [MqttMessageHandler](file://D:\paho-mqttv3-spring-boot-starter\src\main\java\com\gong\iot\MqttMessageHandler.java#L2-L4) 定义不同的消息处理策略

## License

请查看 [LICENSE](LICENSE) 文件了解详细信息。