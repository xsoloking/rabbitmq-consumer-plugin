package org.jenkinsci.plugins.rabbitmqconsumer.channels;

import hudson.security.ACL;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.jenkinsci.plugins.rabbitmqconsumer.GlobalRabbitmqConfiguration;
import org.jenkinsci.plugins.rabbitmqconsumer.RMQState;
import org.jenkinsci.plugins.rabbitmqconsumer.RabbitmqConsumeItem;
import org.jenkinsci.plugins.rabbitmqconsumer.extensions.MessageQueueListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.ShutdownSignalException;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.AMQP.BasicProperties;

/**
 * Handle class for RabbitMQ consume channel.
 *
 * @author rinrinne a.k.a. rin_ne
 */
public class ConsumeRMQChannel extends AbstractRMQChannel {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConsumeRMQChannel.class);
    private static final int SHUTDOWN_TIMEOUT_SECOND = 10;
    private static final int FORCE_SHUTDOWN_TIMEOUT_SECOND = 60;

    protected final Collection<String> appIds;
    private final String queueName;
    private volatile boolean consumeStarted = false;

    private final boolean debug;

    private final ExecutorService systemService = Executors.newSingleThreadExecutor();

    /**
     * Creates instance with specified parameters.
     *
     * @param queueName
     *            the queue name.
     * @param appIds
     *            the hashset of application id.
     */
    public ConsumeRMQChannel(String queueName, Collection<String> appIds) {
        this.appIds = appIds;
        this.queueName = queueName;
        this.debug = isEnableDebug();
    }

    /**
     * Get hashset of app ids.
     *
     * @return the hashset of app ids.
     */
    public Collection<String> getAppIds() {
        return appIds;
    }

    /**
     * Gets queue name.
     *
     * @return the queue name.
     */
    public String getQueueName() {
        return queueName;
    }

    /**
     * Starts consume.
     */
    public void consume() {
        if (state == RMQState.CONNECTED && channel != null) {
            try {
                channel.basicConsume(queueName, false, new MessageConsumer(channel));
                consumeStarted = true;
                MessageQueueListener.fireOnBind(appIds, queueName);
            } catch (IOException e) {
                LOGGER.warn("Failed to start consumer: ", e);
            }
        }
    }

    /**
     * Gets whether consumer is already started or not.
     *
     * @return true if consumer is already started.
     */
    public boolean isConsumeStarted() {
        return consumeStarted;
    }

    /**
     * Gets whether debug mode is enabled or not.
     *
     * @return true if debug mode is enabled.
     */
    private boolean isEnableDebug() {
        return GlobalRabbitmqConfiguration.get().isEnableDebug();
    }

    /**
     * Handle class that consume message.
     *
     * @author rinrinne a.k.a. rin_ne
     *
     */
    public class MessageConsumer extends DefaultConsumer {

        /**
         * Creates instance with specified parameter.
         *
         * @param channel
         *            the instance of Channel, not RMQChannel.
         */
        public MessageConsumer(Channel channel) {
            super(channel);
        }

        @Override
        public void handleDelivery(String consumerTag, Envelope envelope, BasicProperties properties, byte[] body)
                throws IOException {

            try {
                long deliveryTag = envelope.getDeliveryTag();
                String contentType = properties.getContentType();
                Map<String, Object> headers = properties.getHeaders();

                if (debug) {
                    if (appIds.contains(RabbitmqConsumeItem.DEBUG_APPID)) {
                        systemService.execute(new MessageEventRunner(RabbitmqConsumeItem.DEBUG_APPID,
                                queueName, contentType, headers, body));
                    }
                }

                if (properties.getAppId() != null &&
                        !properties.getAppId().equals(RabbitmqConsumeItem.DEBUG_APPID)) {
                    if (appIds.contains(properties.getAppId())) {
                        systemService.execute(new MessageEventRunner(properties.getAppId(),
                                queueName, contentType, headers, body));
                    }
                }

                channel.basicAck(deliveryTag, false);

            } catch (IOException e) {
                throw e;
            } catch (RuntimeException e) {
                LOGGER.warn("caught exception in delivery handler", e);
            }
        }
    }

    /**
     * A class to perform message event.
     *
     * @author rinrinne a.k.a. rin_ne
     */
    public class MessageEventRunner implements Runnable {
        private final String id;
        private final String queueName;
        private final String contentType;
        private final Map<String, Object> headers;
        private final byte[] body;

        /**
         * Default constructor.
         *
         * @param id
         *      the id.
         * @param queueName
         *      the queue name.
         * @param contentType
         *      the content type.
         * @param headers
         *      the map of headers.
         * @param body
         *      the body.
         */
        public MessageEventRunner(String id, String queueName, String contentType,
                Map<String, Object> headers, byte[] body) {
            this.id = id;
            this.queueName = queueName;
            this.contentType = contentType;
            this.headers = headers;
            this.body = body;
        }

        @Override
        public void run() {
            ACL.impersonate(ACL.SYSTEM);
            MessageQueueListener.fireOnReceive(id, queueName, contentType, headers, body);
        }
    }

    /**
     * @inheritDoc
     * @param shutdownSignalException
     *            the exception.
     */
    public void shutdownCompleted(ShutdownSignalException shutdownSignalException) {
        consumeStarted = false;
        MessageQueueListener.fireOnUnbind(appIds, queueName);
        super.shutdownCompleted(shutdownSignalException);
    }

    /**
     * Shutdown system service executor.
     */
    public void shutdownSystemService() {
        systemService.shutdown(); // Disable new tasks from being submitted
        try {
            // Wait a while for existing tasks to terminate
            if (!systemService.awaitTermination(SHUTDOWN_TIMEOUT_SECOND, TimeUnit.SECONDS)) {
                systemService.shutdownNow(); // Cancel currently executing tasks
                // Wait a while for tasks to respond to being cancelled
                if (!systemService.awaitTermination(FORCE_SHUTDOWN_TIMEOUT_SECOND, TimeUnit.SECONDS))
                    LOGGER.warn("System Service termination was timed out.");
            }
        } catch (InterruptedException ie) {
            // (Re-)Cancel if current thread also interrupted
            systemService.shutdownNow();
            // Preserve interrupt status
            Thread.currentThread().interrupt();
        }
    }
}
