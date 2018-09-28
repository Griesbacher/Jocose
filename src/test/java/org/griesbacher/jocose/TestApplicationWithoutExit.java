package org.griesbacher.jocose;

import io.prometheus.client.Gauge;

import java.io.IOException;

/**
 * Based on: https://github.com/prometheus/jmx_exporter/blob/master/jmx_prometheus_javaagent/src/test/java/io/prometheus/jmx/TestApplication.java
 */

public class TestApplicationWithoutExit {
    public static void main(String[] args) throws IOException {
        final Gauge fooBar = Gauge.build().name("foo_bar").help("Test_Metric").register();
        fooBar.set(84);
        System.out.println();
        System.out.flush();
        System.in.read();
    }
}
