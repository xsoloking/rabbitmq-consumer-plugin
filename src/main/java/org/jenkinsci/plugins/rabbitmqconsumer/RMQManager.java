package org.jenkinsci.plugins.rabbitmqconsumer;

import hudson.util.Secret;

import java.io.IOException;
import java.net.ConnectException;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.jenkinsci.plugins.rabbitmqconsumer.channels.PublishRMQChannel;
import org.jenkinsci.plugins.rabbitmqconsumer.extensions.ServerOperator;
import org.jenkinsci.plugins.rabbitmqconsumer.listeners.RMQConnectionListener;
import org.jenkinsci.plugins.rabbitmqconsumer.watchdog.ConnectionMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rabbitmq.client.Channel;

/**
 * Manager class for RabbitMQ connection.
 *
 * @author rinrinne a.k.a. rin_ne
 */
public final class RMQManager implements RMQConnectionListener {

    /**
     * Intance holder class for {@link RMQManager}.
     *
     * @author rinrinne a.k.a. rin_ne
     */
    private static class InstanceHolder {
        private static final RMQManager INSTANCE = new RMQManager();
    }

    private static final long TIMEOUT_CLOSE = 300000;
    private static final Logger LOGGER = LoggerFactory.getLogger(RMQManager.class);

    private RMQConnection rmqConnection;
    private volatile boolean statusOpen = false;
    private CountDownLatch closeLatch = null;

    /**
     * Gets instance.
     *
     * @return the instance.
     */
    public static RMQManager getInstance() {
        return InstanceHolder.INSTANCE;
    }

    /**
     * Updates RabbitMQ connection.
     */
    public void update() {
        LOGGER.info("Start to update connections...");
        GlobalRabbitmqConfiguration conf = GlobalRabbitmqConfiguration.get();
        String uri = conf.getServiceUri();
        String user = conf.getUserName();
        Secret pass = conf.getUserPassword();
        long watchdog = conf.getWatchdogPeriod();

        boolean enableConsumer = conf.isEnableConsumer();

        try {
            if (!enableConsumer || uri == null) {
                if (rmqConnection != null) {
                    shutdownWithWait();
                    rmqConnection = null;
                }
            }
            if (rmqConnection != null &&
                    !uri.equals(rmqConnection.getServiceUri()) &&
                    !user.equals(rmqConnection.getUserName()) &&
                    !pass.equals(rmqConnection.getUserPassword())) {
                shutdownWithWait();
                rmqConnection = null;
            }

            if (enableConsumer) {
                if (rmqConnection == null) {
                    rmqConnection = new RMQConnection(uri, user, pass, watchdog);
                    rmqConnection.addRMQConnectionListener(this);
                    try {
                        rmqConnection.open();
                    } catch (IOException e) {
                        if (e.getCause() instanceof ConnectException) {
                            LOGGER.warn("Cannot open connection: {}", e.getCause().getMessage());
                        } else {
                            LOGGER.warn("Cannot open connection!", e);
                        }
                        rmqConnection.removeRMQConnectionListener(this);
                        rmqConnection = null;
                    }
                } else {
                    rmqConnection.updateChannels(GlobalRabbitmqConfiguration.get().getConsumeItems());
                }
            }
        } catch (InterruptedException e) {
            LOGGER.warn("Interrupted when waiting to close connection.");
        }
    }

    /**
     * Shutdown connection.
     */
    public void shutdown() {
        if (rmqConnection != null && rmqConnection.isOpen()) {
            try {
                statusOpen = false;
                rmqConnection.close();
            } catch(Exception ex) {
                onCloseCompleted(rmqConnection);
            }
        }
    }

    /**
     * Shutdown connection then wait to close connection.
     *
     * @throws InterruptedException
     *             throw if wait process is interrupted.
     */
    public synchronized void shutdownWithWait() throws InterruptedException {
        if (rmqConnection != null && rmqConnection.isOpen()) {
            try {
                closeLatch = new CountDownLatch(1);
                shutdown();
                if (!closeLatch.await(TIMEOUT_CLOSE, TimeUnit.MILLISECONDS)) {
                    onCloseCompleted(rmqConnection);
                    throw new InterruptedException("Wait timeout");
                }
            } finally {
                closeLatch = null;
            }
        }
    }

    /**
     * Gets whether connection is established or not.
     *
     * @return true if connection is already established.
     */
    public boolean isOpen() {
        return statusOpen;
    }

    /**
     * Gets status of channel for specified queue.
     *
     * @param queueName
     *            the queue name.
     * @return true if channel for specified queue is already established.
     */
    public boolean getChannelStatus(String queueName) {
        if (statusOpen) {
            if (rmqConnection != null && rmqConnection.isOpen()) {
                return rmqConnection.getConsumeChannelStatus(queueName);
            }
        }
        return false;
    }

    /**
     * Gets channel.
     * Note that returned channel is not managed in any own classes.
     *
     * @return the channel.
     */
    public Channel getChannel() {
        Channel ch = null;
        if (statusOpen) {
            if (rmqConnection != null) {
                ch = rmqConnection.createPureChannel();
            }
        }
        return ch;
    }

    /**
     * Gets instance of {@link PublishRMQChannel}.
     *
     * @return instance.
     */
    public PublishRMQChannel getPublishChannel() {
        if (statusOpen) {
            if (rmqConnection != null) {
                Collection<PublishRMQChannel> channels = rmqConnection.getPublishRMQChannels();
                if (!channels.isEmpty()) {
                    return (PublishRMQChannel)(channels.toArray()[0]);
                }
            }
        }
        return null;
    }

    /**
     * @inheritDoc
     * @param rmqConnection
     *            the connection.
     */
    public void onOpen(RMQConnection rmqConnection) {
        if (this.rmqConnection.equals(rmqConnection)) {
            LOGGER.info("Open RabbitMQ connection: {}", rmqConnection.getServiceUri());
            ConnectionMonitor.get().setActivate(false);
            ConnectionMonitor.get().setLastMeanTime(System.currentTimeMillis());
            ServerOperator.fireOnOpen(rmqConnection);
            rmqConnection.updateChannels(GlobalRabbitmqConfiguration.get().getConsumeItems());
            statusOpen = true;
        }
    }

    /**
     * @inheritDoc
     * @param rmqConnection
     *            the connection.
     */
    public void onCloseCompleted(RMQConnection rmqConnection) {
        if (this.rmqConnection != null && this.rmqConnection.equals(rmqConnection)) {
            this.rmqConnection = null;
            LOGGER.info("Closed RabbitMQ connection: {}",rmqConnection.getServiceUri());
            rmqConnection.removeRMQConnectionListener(this);
            ServerOperator.fireOnCloseCompleted(rmqConnection);
            statusOpen = false;
            if (closeLatch != null) {
                closeLatch.countDown();
            }
        }
    }

    /**
     * Creates instance.
     */
    private RMQManager() {
    }
}
