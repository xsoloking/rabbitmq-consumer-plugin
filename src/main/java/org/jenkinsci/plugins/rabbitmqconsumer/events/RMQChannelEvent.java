package org.jenkinsci.plugins.rabbitmqconsumer.events;

/**
 * Events for {@link org.jenkinsci.plugins.rabbitmqconsumer.listeners.RMQChannelListener}.
 *
 * @author rinrinne a.k.a. rin_ne
 */
public enum RMQChannelEvent {
    /**
     * OnOpen event.
     */
    OPEN,
    /**
     * OnCloseCompleted event.
     */
    CLOSE_COMPLETED;
}
