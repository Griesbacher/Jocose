package org.griesbacher.jocose;

import io.prometheus.client.Gauge;

import static org.griesbacher.jocose.JavaAgent.PROMETHEUS_PREFIX;

/**
 * PrometheusAliveSignal is used to export the last timestamp the exporter was public.
 */
public class PrometheusAliveSignal extends Thread {
    private static final Gauge LAST_ALIVE_SIGNAL = Gauge.build().name(PROMETHEUS_PREFIX + "last_alive")
            .help("Timestamp in seconds, with the last seen timestamp.").register();

    /**
     * Creates a thread to export the last timestamp of the last scrape.
     */
    public PrometheusAliveSignal() {
        this.setDaemon(true);
    }

    @Override
    public void run() {
        while (true) {
            LAST_ALIVE_SIGNAL.set(System.currentTimeMillis() / 1000.0);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                return;
            }
        }
    }
}
