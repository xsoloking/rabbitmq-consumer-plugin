package org.jenkinsci.plugins.rabbitmqconsumer.extensions;

import hudson.security.ACL;

import java.util.Collection;
import java.util.Map;

import org.acegisecurity.context.SecurityContext;
import org.acegisecurity.context.SecurityContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import jenkins.model.Jenkins;

/**
 * Listener for message queue.
 *
 * @author rinrinne a.k.a. rin_ne
 */
public abstract class MessageQueueListener implements ExtensionPoint {
    private static final Logger LOGGER = LoggerFactory.getLogger(MessageQueueListener.class);

    /**
     * Gets name.
     *
     * @return the name.
     */
    public abstract String getName();

    /**
     * Gets application id.
     *
     * @return the application id.
     */
    public abstract String getAppId();

    /**
     * Calls when binds to queue.
     *
     * @param queueName
     *            the queue name.
     */
    public abstract void onBind(String queueName);

    /**
     * Calls when unbinds from queue.
     *
     * @param queueName
     *            the queue name.
     */
    public abstract void onUnbind(String queueName);

    /**
     * Calls when message arrives.
     *
     * @param queueName
     *            the queue name.
     * @param contentType
     *            the type of content.
     * @param headers
     *            the map of headers.
     * @param body
     *            the content body.
     */
    public abstract void onReceive(String queueName, String contentType, Map<String, Object> headers, byte[] body);

    /**
     * Fires OnReceive event.
     *
     * @param appId
     *            the application id.
     * @param queueName
     *            the queue name.
     * @param contentType
     *            the type of content.
     * @param headers
     *            the map of headers.
     * @param body
     *            the message body.
     */
    public static void fireOnReceive(String appId,
            String queueName,
            String contentType,
            Map<String, Object> headers,
            byte[] body) {
        LOGGER.trace("MessageQueueListener", "fireOnReceive");
        SecurityContext old = ACL.impersonate(ACL.SYSTEM);
        try {
            for (MessageQueueListener l : all()) {
                if (appId.equals(l.getAppId())) {
                    try {
                        l.onReceive(queueName, contentType, headers, body);
                    } catch (Exception ex) {
                        LOGGER.warn("Caught exception during calling onReceive()", ex);
                    }
                }
            }
        }
        finally {
            SecurityContextHolder.setContext(old);
        }
    }

    /**
     * Fires OnBind event.
     *
     * @param appIds
     *            the hashset of application ids.
     * @param queueName
     *            the queue name.
     */
    public static void fireOnBind(Collection<String> appIds, String queueName) {
        LOGGER.trace("MessageQueueListener", "fireOnBind");
        SecurityContext old = ACL.impersonate(ACL.SYSTEM);
        try {
            for (MessageQueueListener l : all()) {
                if (appIds.contains(l.getAppId())) {
                    try {
                        l.onBind(queueName);
                    } catch (Exception ex) {
                        LOGGER.warn("Caught exception during calling onBind()", ex);
                    }
                }
            }
        }
        finally {
            SecurityContextHolder.setContext(old);
        }
    }

    /**
     * Fires OnUnbind event.
     *
     * @param appIds
     *            the hashset of application ids.
     * @param queueName
     *            the queue name.
     */
    public static void fireOnUnbind(Collection<String> appIds, String queueName) {
        LOGGER.trace("MessageQueueListener", "fireOnUnbind");
        SecurityContext old = ACL.impersonate(ACL.SYSTEM);
        try {
            for (MessageQueueListener l : all()) {
                if (appIds.contains(l.getAppId())) {
                    try {
                        l.onUnbind(queueName);
                    } catch (Exception ex) {
                        LOGGER.warn("Caught exception during calling onUnbind()", ex);
                    }
                }
            }
        }
        finally {
            SecurityContextHolder.setContext(old);
        }
    }

    /**
     * Gets all listeners.
     *
     * @return the extension list.
     */
    public static ExtensionList<MessageQueueListener> all() {
        return Jenkins.getInstance().getExtensionList(MessageQueueListener.class);
    }
}
