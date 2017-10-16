package org.jenkinsci.plugins.rabbitmqconsumer.events;

/**
 * Events for {@link org.jenkinsci.plugins.rabbitmqconsumer.listeners.RMQConnectionListener}.
 *
 * @author nobuhiro
 */
public enum RMQConnectionEvent {
    /**
     * OnOpen event.
     */
    OPEN,
    /**
     * OnCloseCompleted event.
     */
    CLOSE_COMPLETED;
}
