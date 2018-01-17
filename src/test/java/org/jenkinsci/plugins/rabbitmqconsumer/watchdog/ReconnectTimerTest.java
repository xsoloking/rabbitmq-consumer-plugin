package org.jenkinsci.plugins.rabbitmqconsumer.watchdog;

import mockit.Mocked;
import mockit.Expectations;

import static org.junit.Assert.*;

import org.jenkinsci.plugins.rabbitmqconsumer.GlobalRabbitmqConfiguration;
import org.jenkinsci.plugins.rabbitmqconsumer.RMQManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Test for ReconnectTimer class.
 *
 * @author rinrinne a.k.a. rin_ne
 *
 */
public class ReconnectTimerTest {

    @Mocked
    RMQManager manager;

    @Mocked
    GlobalRabbitmqConfiguration config;

    @Mocked
    ConnectionMonitor monitor;

    ReconnectTimer timer = new ReconnectTimer();

    @Before
    public void setUp() throws Exception {
        new Expectations() {{
            RMQManager.getInstance(); result = manager; minTimes = 0;
            GlobalRabbitmqConfiguration.get(); result = config; minTimes = 0;
            ConnectionMonitor.get(); result = monitor; minTimes = 0;
        }};
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void testSetRecurrencePeriod() {
        timer.setRecurrencePeriod(1000);
        assertEquals(1000, timer.getRecurrencePeriod());
    }

    @Test
    public void testIfAllGrean() {
        new Expectations() {{
            manager.isOpen(); result = true; minTimes = 0;
            config.isEnableConsumer(); result = true; minTimes = 0;
            manager.update(); times = 0; minTimes = 0;
        }};

        timer.start();
        timer.doAperiodicRun();
        timer.stop();
    }

    @Test
    public void testIfManagerIsClosed() {
        new Expectations() {{
            manager.isOpen(); result = false; minTimes = 0;
            config.isEnableConsumer(); result = true; minTimes = 0;
            manager.update(); times = 1; minTimes = 0;
        }};

        timer.start();
        timer.doAperiodicRun();
        timer.stop();
    }

    @Test
    public void testIfConsumerIsDisabled() {
        new Expectations() {{
            manager.isOpen(); result = true; minTimes = 0;
            config.isEnableConsumer(); result = false; minTimes = 0;
            manager.update(); times = 0; minTimes = 0;
        }};

        timer.start();
        timer.doAperiodicRun();
        timer.stop();
    }

    @Test
    public void testIfAllNegative() {
        new Expectations() {{
            manager.isOpen(); result = false; minTimes = 0;
            config.isEnableConsumer(); result = false; minTimes = 0;
            manager.update(); times = 0; minTimes = 0;
        }};

        timer.start();
        timer.doAperiodicRun();
        timer.stop();
    }

    @Test
    public void testDoAperiodicRunInShutdown() {
        new Expectations() {{
            manager.isOpen(); result = false; minTimes = 0;
            config.isEnableConsumer(); result = true; minTimes = 0;
            manager.update(); times = 0; minTimes = 0;
        }};

        timer.stop();
        timer.doAperiodicRun();
    }
}
