package org.jenkinsci.plugins.rabbitmqconsumer;

import hudson.util.Secret;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import org.jenkinsci.plugins.rabbitmqconsumer.channels.PublishRMQChannel;
import org.jenkinsci.plugins.rabbitmqconsumer.listeners.RMQConnectionListener;

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
    private static final Logger LOGGER = Logger.getLogger(RMQManager.class.getName());

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
                if (rmqConnection != null) {
                    shutdownWithWait();
                    rmqConnection = null;
                }
            }

            if (enableConsumer) {
                if (rmqConnection == null) {
                    rmqConnection = new RMQConnection(uri, user, pass);
                    rmqConnection.addRMQConnectionListener(this);
                    try {
                        rmqConnection.open();
                    } catch (IOException e) {
                        LOGGER.warning("Cannot open connection.");
                        return;
                    }
                }
                rmqConnection.updateChannels(GlobalRabbitmqConfiguration.get().getConsumeItems());
            }
        } catch (InterruptedException e) {
            LOGGER.warning("Interrupted when waiting to close connection.");
        }
    }

    /**
     * Shutdown connection.
     */
    public void shutdown() {
        if (rmqConnection != null) {
            rmqConnection.close();
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
        if (rmqConnection == null) {
            return false;
        } else {
            return rmqConnection.getConsumeChannelStatus(queueName);
        }
    }

    /**
     * Gets instance of {@link PublishRMQChannel}.
     *
     * @return instance.
     */
    public PublishRMQChannel getPublishChannel() {
        Set<PublishRMQChannel> channels = rmqConnection.getPublishRMQChannels();
        if (!channels.isEmpty()) {
            return (PublishRMQChannel)(channels.toArray()[0]);
        }
        return null;
    }

    /**
     * @inheritDoc
     * @param rmqConnection
     *            the connection.
     */
    public void onOpen(RMQConnection rmqConnection) {
        LOGGER.info("Open RabbitMQ connection.");
        statusOpen = true;
    }

    /**
     * @inheritDoc
     * @param rmqConnection
     *            the connection.
     */
    public void onCloseCompleted(RMQConnection rmqConnection) {
        LOGGER.info("Closed RabbitMQ connection.");
        statusOpen = false;
        rmqConnection.removeRMQConnectionListener(this);
        rmqConnection = null;
        if (closeLatch != null) {
            closeLatch.countDown();
        }
    }

    //CS IGNORE LineLength FOR NEXT 8 LINES. REASON: Auto generated code.
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((rmqConnection == null) ? 0 : rmqConnection.hashCode());
        result = prime * result + (statusOpen ? 1231 : 1237);
        return result;
    }

    //CS IGNORE LineLength FOR NEXT 18 LINES. REASON: Auto generated code.
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        RMQManager other = (RMQManager) obj;
        if (rmqConnection == null) {
            if (other.rmqConnection != null)
                return false;
        } else if (!rmqConnection.equals(other.rmqConnection))
            return false;
        if (statusOpen != other.statusOpen)
            return false;
        return true;
    }

    /**
     * Creates instance.
     */
    private RMQManager() {
    }
}
