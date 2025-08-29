package com.gong.iot;

import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * MQTT 工厂类，用于创建具有智能重连功能的 MQTT 客户端
 */
public class EnhancedMqttFactory {
    // MQTT 代理地址
    private final String broker;
    // 客户端ID
    private final String clientId;
    // 存储持久化策略
    private final MemoryPersistence persistence;
    // 用户名
    private final String username;
    // 密码
    private final char[] password;
    // 是否使用清洁会话
    private final boolean cleanSession;
    // 自定义回调
    private final MqttCallback customCallback;
    // 重连配置
    private final ReconnectConfig reconnectConfig;

    // 创建日志记录器实例
    private final Logger logger = LoggerFactory.getLogger(EnhancedMqttFactory.class);

    private ThreadPoolExecutor mqttCallBackThreadPoolExecutor;

    /**
     * 构造函数私有化，采用 Builder 模式创建实例
     */
    private EnhancedMqttFactory(Builder builder) {
        this.broker = builder.broker;
        this.clientId = builder.clientId + "_" + System.currentTimeMillis();
        this.persistence = builder.persistence;
        this.username = builder.username;
        this.password = builder.password;
        this.cleanSession = builder.cleanSession;
        this.customCallback = builder.customCallback;
        this.reconnectConfig = builder.reconnectConfig;
        this.mqttCallBackThreadPoolExecutor = builder.mqttThreadPoolExecutor;
    }

    /**
     * Builder 类，用于构建 EnhancedMQTTFactory 实例
     */
    public static class Builder {
        // 必需参数
        private final String broker;

        // 可选参数
        private String clientId = UUID.randomUUID().toString();
        private MemoryPersistence persistence = new MemoryPersistence();
        private String username;
        private char[] password;
        private boolean cleanSession = true;
        private MqttCallback customCallback;
        private ReconnectConfig reconnectConfig = new ReconnectConfig();

        private ThreadPoolExecutor mqttThreadPoolExecutor;

        public Builder(String broker) {
            this.broker = broker;
        }

        public Builder clientId(String clientId) {
            this.clientId = clientId;
            return this;
        }

        public Builder persistence(MemoryPersistence persistence) {
            this.persistence = persistence;
            return this;
        }

        public Builder credentials(String username, char[] password) {
            this.username = username;
            this.password = password;
            return this;
        }

        public Builder cleanSession(boolean cleanSession) {
            this.cleanSession = cleanSession;
            return this;
        }

        public Builder callback(MqttCallback callback) {
            this.customCallback = callback;
            return this;
        }

        public Builder reconnectConfig(ReconnectConfig config) {
            this.reconnectConfig = config;
            return this;
        }

        public EnhancedMqttFactory build() {
            return new EnhancedMqttFactory(this);
        }

        public Builder callbackThreadPool(ThreadPoolExecutor mqttThreadPoolExecutor) {
            this.mqttThreadPoolExecutor = mqttThreadPoolExecutor;
            return this;
        }
    }

    /**
     * 创建并返回一个 MQTT 客户端包装类实例
     * @return MQTT 客户端包装类实例
     * @throws MqttException 如果客户端创建过程中出现错误
     */
    public MqttClientWrapper create() throws MqttException {
        logger.info("创建 MQTT 客户端，broker: {}, clientId: {}", broker, clientId);
        MqttClient client = new MqttClient(broker, clientId, persistence);

        MqttConnectOptions connOpts = buildConnectOptions();
        SmartReconnectCallback callback = buildCallback(client, connOpts);

        client.setCallback(callback);
        performConnect(client, connOpts, callback);

        return new MqttClientWrapper(client, callback);
    }

    /**
     * 构建 MQTT 连接选项
     * @return MQTT 连接选项实例
     */
    private MqttConnectOptions buildConnectOptions() {
        MqttConnectOptions opts = new MqttConnectOptions();
        if (username != null && password != null) {
            opts.setUserName(username);
            opts.setPassword(password);
        }
        opts.setCleanSession(cleanSession);
        return opts;
    }

    /**
     * 构建智能重连回调实例
     * @param client MQTT 客户端
     * @param opts 连接选项
     * @return 智能重连回调实例
     */
    private SmartReconnectCallback buildCallback(MqttClient client, MqttConnectOptions opts) {
        return customCallback != null ?
            new SmartReconnectCallback(client, opts, reconnectConfig, customCallback, mqttCallBackThreadPoolExecutor) :
            new SmartReconnectCallback(client, opts, reconnectConfig, mqttCallBackThreadPoolExecutor);
    }

    /**
     * 执行 MQTT 客户端连接
     * @param client MQTT 客户端
     * @param opts 连接选项
     * @param callback 智能重连回调
     * @throws MqttException 如果连接过程中出现错误
     */
    private void performConnect(MqttClient client, MqttConnectOptions opts,
                              SmartReconnectCallback callback) throws MqttException {
        try {
            logger.info("尝试连接 MQTT 代理");
            client.connect(opts);
            logger.info("成功连接到 MQTT 代理");
        } catch (MqttException e) {
            logger.error("连接 MQTT 代理失败: {}", e.getMessage());
            if (reconnectConfig.autoRetryInitialConnect) {
                logger.info("自动重连功能已启用，将尝试重新连接");
                callback.scheduleReconnectAttempt();
            }
            throw e;
        }
    }

    /**
     * 重连配置参数封装
     */
    public static class ReconnectConfig {
        int maxAttempts = Integer.MAX_VALUE;
        long initialDelay = 5000;
        double backoffFactor = 1.5;
        boolean autoRetryInitialConnect = true;

        public ReconnectConfig maxAttempts(int max) {
            this.maxAttempts = max;
            return this;
        }

        public ReconnectConfig initialDelay(long delayMs) {
            this.initialDelay = delayMs;
            return this;
        }

        public ReconnectConfig backoffFactor(double factor) {
            this.backoffFactor = factor;
            return this;
        }

        public ReconnectConfig autoRetryInitialConnect(boolean enable) {
            this.autoRetryInitialConnect = enable;
            return this;
        }
    }

    /**
     * 智能重连回调（核心实现）
     */
    protected static class SmartReconnectCallback implements MqttCallback {
        private final MqttClient client;
        private final MqttConnectOptions connOpts;
        private final ReconnectConfig config;
        private final MqttCallback userCallback;
        private final Set<String> subscribedTopics = new CopyOnWriteArraySet<>();
        private volatile boolean isShutdown = false;

        private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        private final AtomicInteger reconnectAttempts = new AtomicInteger(0);

        private final ThreadPoolExecutor mqttCallbackThreadPoolExecutor;

        // 创建日志记录器实例
        private final Logger logger = LoggerFactory.getLogger(SmartReconnectCallback.class);

        SmartReconnectCallback(MqttClient client, MqttConnectOptions opts,
                               ReconnectConfig config, ThreadPoolExecutor mqttCallbackThreadPoolExecutor) {
            this(client, opts, config, null, mqttCallbackThreadPoolExecutor);
        }

        SmartReconnectCallback(MqttClient client, MqttConnectOptions opts,
                               ReconnectConfig config, MqttCallback userCallback, ThreadPoolExecutor mqttCallbackThreadPoolExecutor) {
            this.client = client;
            this.connOpts = opts;
            this.config = config;
            this.userCallback = userCallback;
            this.mqttCallbackThreadPoolExecutor = mqttCallbackThreadPoolExecutor;
        }

        @Override
        public void connectionLost(Throwable cause) {
            if (isShutdown) return;
            logger.warn("MQTT 连接丢失: {}", cause.getMessage());
            // 处理连接丢失事件
            handleDisconnection(cause);
            // 转发事件到用户回调
            forwardEvent(() -> userCallback.connectionLost(cause));
        }

        @Override
        public void messageArrived(String topic, MqttMessage message) {
            if(logger.isDebugEnabled()){
                logger.debug("收到来自主题 {} 的消息: {}", topic, new String(message.getPayload()));
            }
            // 转发事件到用户回调
            forwardEvent(() -> {
                try {
                    userCallback.messageArrived(topic, message);
                } catch (Exception e) {
                    logger.error("处理消息时发生错误: {}", e.getMessage());
                    throw new RuntimeException(e);
                }
            });
        }

        @Override
        public void deliveryComplete(IMqttDeliveryToken token) {
            if(logger.isDebugEnabled()){
                logger.debug("消息投递完成: {}", token.isComplete() ? "成功" : "失败");
            }
            // 转发事件到用户回调
            forwardEvent(() -> userCallback.deliveryComplete(token));
        }

        private void handleDisconnection(Throwable cause) {
            // 如果重连次数未达到最大值，则尝试重连
            if (reconnectAttempts.get() < config.maxAttempts) {
                long delay = calculateBackoffDelay();
                logger.info("将在 {}.{}s 后尝试第{}次重连", delay / 1000, delay % 1000 / 100, reconnectAttempts.incrementAndGet());
                scheduler.schedule(this::attemptReconnect, delay, TimeUnit.MILLISECONDS);
            } else {
                logger.error("已达到最大重连次数，停止重连");
            }
        }

        private long calculateBackoffDelay() {
            // 计算重连延迟时间
            return (long) (config.initialDelay * Math.pow(config.backoffFactor, reconnectAttempts.get()));
        }

        private synchronized void attemptReconnect() {
            // 尝试重连
            try {
                if (!client.isConnected()) {
                    client.connect(connOpts);
                    logger.info("重连成功！");
                    reconnectAttempts.set(0);
                    // 重连成功后重新订阅主题
                    resubscribeTopics();
                }
            } catch (MqttException e) {
                logger.error("重连失败: {}", e.getMessage());
                handleDisconnection(e);
            }
        }

        private void resubscribeTopics() {
            if (!subscribedTopics.isEmpty()) {
                try {
                    for (String topic : subscribedTopics) {
                        client.subscribe(topic);
                        logger.info("重新订阅主题: {}", topic);
                    }
                } catch (MqttException e) {
                    logger.error("重新订阅主题失败: {}", e.getMessage());
                }
            }
        }

        void scheduleReconnectAttempt() {
            // 立即尝试重连
            scheduler.schedule(this::attemptReconnect, 0, TimeUnit.MILLISECONDS);
        }

        private void forwardEvent(Runnable action) {
            // 转发事件到用户回调
            if (userCallback != null) {
                if (mqttCallbackThreadPoolExecutor != null) {
                    // 使用线程池异步执行用户回调
                    mqttCallbackThreadPoolExecutor.execute(() -> {
                        try {
                            action.run();
                        } catch (Exception e) {
                            logger.error("用户回调执行异常: {}", e.getMessage());
                        }
                    });
                } else {
                    // 如果没有线程池，则同步执行
                    try {
                        action.run();
                    } catch (Exception e) {
                        logger.error("用户回调执行异常: {}", e.getMessage());
                    }
                }
            }
        }


        public void shutdown() {
            this.isShutdown = true;
            try {
                if (!scheduler.isShutdown()) {
                    scheduler.shutdownNow();
                    if (!scheduler.awaitTermination(3, TimeUnit.SECONDS)) {
                        logger.warn("重连调度器未正常关闭");
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }



        public void addSubscribedTopic(String topic) {
            subscribedTopics.add(topic);
        }
    }

//    public static void main(String[] args) throws Exception {
//        // 创建重连配置
//        EnhancedMQTTFactory.ReconnectConfig config = new EnhancedMQTTFactory.ReconnectConfig()
//                .maxAttempts(10)
//                .initialDelay(3000)
//                .backoffFactor(2);
//
//        // 创建 MQTT 工厂实例
//        EnhancedMQTTFactory factory = new EnhancedMQTTFactory.Builder("tcp://106.15.73.183:1883")
//                .credentials("root", "root".toCharArray())
//                .reconnectConfig(config)
//                .callback(new MqttCallback() {
//                    @Override
//                    public void connectionLost(Throwable cause) {
//                        logger.info("连接断开，建议在此实现重连逻辑");
//                    }
//
//                    @Override
//                    public void messageArrived(String topic, MqttMessage message) {
//                        logger.info("收到消息 [主题: {} Qos: {}] 内容: {}", topic, message.getQos(), new String(message.getPayload()));
//                    }
//
//                    @Override
//                    public void deliveryComplete(IMqttDeliveryToken token) {
//                        logger.info("消息投递完成: {}", token.isComplete() ? "成功" : "失败");
//                    }
//                })
//                .build();
//        // 创建 MQTT 客户端包装类实例
//        MQTTClientWrapper wrapper = factory.create();
//        MqttClient client = wrapper.getClient();
//        // 订阅主题
//        wrapper.subscribe("testtopic/#");
//        // 发布消息
//        client.publish("testtopic/1", new MqttMessage("Hello".getBytes()));
//        // 添加关闭钩子
//        Runtime.getRuntime().addShutdownHook(new Thread(wrapper::shutdown));
//    }
}