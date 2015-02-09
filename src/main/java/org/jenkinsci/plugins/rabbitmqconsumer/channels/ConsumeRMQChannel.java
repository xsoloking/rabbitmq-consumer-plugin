package org.jenkinsci.plugins.rabbitmqconsumer.channels;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;

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

    protected final Collection<String> appIds;
    private final String queueName;
    private volatile boolean consumeStarted = false;

    private final boolean debug;

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
                        MessageQueueListener.fireOnReceive(RabbitmqConsumeItem.DEBUG_APPID,
                                queueName, contentType, headers, body);
                    }
                }

                if (properties.getAppId() != null &&
                        !properties.getAppId().equals(RabbitmqConsumeItem.DEBUG_APPID)) {
                    if (appIds.contains(properties.getAppId())) {
                        MessageQueueListener.fireOnReceive(properties.getAppId(),
                                queueName, contentType, headers, body);
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
     * @inheritDoc
     * @param shutdownSignalException
     *            the exception.
     */
    public void shutdownCompleted(ShutdownSignalException shutdownSignalException) {
        consumeStarted = false;
        MessageQueueListener.fireOnUnbind(appIds, queueName);
        super.shutdownCompleted(shutdownSignalException);
    }
}
