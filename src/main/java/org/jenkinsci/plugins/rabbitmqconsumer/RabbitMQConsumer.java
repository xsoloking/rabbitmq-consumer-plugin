package org.jenkinsci.plugins.rabbitmqconsumer;

import java.util.LinkedList;
import java.util.List;

import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import hudson.Plugin;
import hudson.model.Api;

/**
 * A plugin class
 *
 * @author rinrinne (rinrin.ne@gmail.com)
 */
@ExportedBean
public class RabbitMQConsumer extends Plugin {

    /**
     * Gets api
     * @return the api.
     */
    public Api getApi() {
        return new Api(this);
    }

    /**
     * Gets configuration is enabled or not.
     *
     * @return true if so.
     */
    @Exported
    public boolean isEnabled() {
        return GlobalRabbitmqConfiguration.get().isEnableConsumer();
    }

    /**
     * Gets connection is established or not.
     *
     * @return true if so.
     */
    @Exported
    public boolean isConnected() {
        return GlobalRabbitmqConfiguration.get().isOpen();
    }

    /**
     * Gets configured service URI.
     *
     * @return the URI.
     */
    @Exported
    public String getServiceUri() {
        return GlobalRabbitmqConfiguration.get().getServiceUri();
    }

    /**
     * Gets configured username.
     *
     * @return the username.
     */
    @Exported
    public String getUserName() {
        return GlobalRabbitmqConfiguration.get().getUserName();
    }

    /**
     * Gets the list of configured queues.
     *
     * @return the list of queues.
     */
    @Exported
    public List<QueueState> getQueues() {
        List<QueueState> stats = new LinkedList<QueueState>();
        List<RabbitmqConsumeItem> queues = GlobalRabbitmqConfiguration.get().getConsumeItems();
        for (RabbitmqConsumeItem queue : queues) {
            QueueState state = new QueueState(queue.getQueueName(), queue.getAppId(),
                    RMQManager.getInstance().getChannelStatus(queue.getQueueName()));
            stats.add(state);
        }
        return stats;
    }

    /**
     * An inner class to represent the status of queue.
     *
     * @author rinrinne (rinrin.ne@gmail.com)
     */
    @ExportedBean(defaultVisibility=2)
    public static final class QueueState {

        @Exported
        public final String name;
        @Exported
        public final String appId;
        @Exported
        public final boolean consumed;

        /**
         * Constructor.
         *
         * @param name the name.
         * @param appId the application id.
         * @param consumed true if consumed.
         */
        QueueState(String name, String appId, boolean consumed) {
            this.name = name;
            this.appId = appId;
            this.consumed = consumed;
        }
    }
}
