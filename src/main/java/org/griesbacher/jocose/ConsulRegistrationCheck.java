package org.griesbacher.jocose;

import io.prometheus.client.Gauge;

/**
 * ConsulRegistrationCheck will check if the service is still registered at consul.
 */
class ConsulRegistrationCheck extends Thread {
    private static final Gauge CONSUL_REGISTRATION_TEST = Gauge.build().name(JavaAgent.PROMETHEUS_PREFIX + "consul_registration_check_duration")
            .help("Duration in seconds to check the consul database.").register();
    private static final Gauge CONSUL_GOT_DEREGISTERED = Gauge.build().name(JavaAgent.PROMETHEUS_PREFIX + "consul_got_deregistered")
            .help("Amount of times the service got falsely deregistered and had to reregister.").register();

    private final Consul consul;
    private final long interval;

    /**
     * Generates a new ConsulRegistrationCheck thread.
     *
     * @param consul   the Consul configuration object
     * @param interval in seconds to check the consul server
     */
    ConsulRegistrationCheck(Consul consul, long interval) {
        this.setDaemon(true);
        this.consul = consul;
        this.interval = interval;
        CONSUL_GOT_DEREGISTERED.set(0);
    }

    /**
     * Generates a new ConsulRegistrationCheck thread. The interval will be 30s.
     *
     * @param consul the Consul configuration object
     */
    ConsulRegistrationCheck(Consul consul) {
        this(consul, 30);
    }

    @Override
    public void run() {
        while (true) {
            final long start = System.nanoTime();
            if (!consul.isServiceRegistered()) {
                CONSUL_GOT_DEREGISTERED.inc();
                JavaAgent.LOGGER.fine("Tried to reregister: " + consul.registerService());
            }
            CONSUL_REGISTRATION_TEST.set(JavaAgent.nanoSecondsToSeconds(System.nanoTime() - start));

            try {
                Thread.sleep(interval * 1000);
            } catch (InterruptedException e) {
                return;
            }
        }
    }
}