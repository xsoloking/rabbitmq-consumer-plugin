package org.jenkinsci.plugins.rabbitmqconsumer.watchdog;

import hudson.Extension;

import java.util.concurrent.TimeUnit;

import hudson.model.AdministrativeMonitor;

@Extension
public class ConnectionMonitor extends AdministrativeMonitor {

    private boolean activate = false;
    private long lastMeanTime = System.currentTimeMillis();

    @Override
    public boolean isActivated() {
        return activate;
    }

    public void setActivate(boolean activate) {
        this.activate = activate;
    }

    public void setLastMeanTime(long lastMeanTime) {
        this.lastMeanTime = lastMeanTime;
    }

    public String getSpentTime() {
        long spentTime = System.currentTimeMillis() - lastMeanTime;
        return String.format(
                "%02d:%02d:%02d",
                TimeUnit.MILLISECONDS.toHours(spentTime) % 24,
                TimeUnit.MILLISECONDS.toMinutes(spentTime) % 60,
                TimeUnit.MILLISECONDS.toSeconds(spentTime) % 60
                );
    }

    public String getSpentDays() {
        long spentTime = System.currentTimeMillis() - lastMeanTime;
        return String.valueOf(TimeUnit.MILLISECONDS.toDays(spentTime));
    }

    public static ConnectionMonitor get() {
        return AdministrativeMonitor.all().get(ConnectionMonitor.class);
    }
}
