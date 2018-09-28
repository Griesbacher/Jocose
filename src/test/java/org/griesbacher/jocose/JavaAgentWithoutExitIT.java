package org.griesbacher.jocose;

import org.junit.Test;

import java.io.*;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * Based on: https://github.com/prometheus/jmx_exporter/blob/master/jmx_prometheus_javaagent/src/test/java/io/prometheus/jmx/JavaAgentIT.java
 */

public class JavaAgentWithoutExitIT {
    private List<URL> getClassloaderUrls() {
        return getClassloaderUrls(getClass().getClassLoader());
    }

    private static List<URL> getClassloaderUrls(ClassLoader classLoader) {
        if (classLoader == null) {
            return Collections.emptyList();
        }
        if (!(classLoader instanceof URLClassLoader)) {
            return getClassloaderUrls(classLoader.getParent());
        }
        URLClassLoader u = (URLClassLoader) classLoader;
        List<URL> result = new ArrayList<URL>(Arrays.asList(u.getURLs()));
        result.addAll(getClassloaderUrls(u.getParent()));
        return result;
    }

    private String buildClasspath() {
        StringBuilder sb = new StringBuilder();
        for (URL url : getClassloaderUrls()) {
            if (!url.getProtocol().equals("file")) {
                continue;
            }
            if (sb.length() != 0) {
                sb.append(File.pathSeparatorChar);
            }
            sb.append(url.getPath());
        }
        return sb.toString();
    }

    @Test
    public void agentLoads() throws IOException, InterruptedException {
        // If not starting the testcase via Maven, set the buildDirectory and finalName system properties manually.
        final String buildDirectory = (String) System.getProperties().get("buildDirectory");
        final String finalName = (String) System.getProperties().get("finalName");
        final String config = getClass().getClassLoader().getResource("none_consul_example_config.yml").getFile();
        final String port = "9877";
        final String javaagent = String.format("-javaagent:%s%s%s.jar=,-p%s,-cfile://%s", buildDirectory, File.separator, finalName, port, config);
        final String javaHome = System.getenv("JAVA_HOME");
        final String java;
        if (javaHome != null && javaHome.equals("")) {
            java = javaHome + "/bin/java";
        } else {
            java = "java";
        }

        final Process app = new ProcessBuilder()
                .command(java, javaagent, "-cp", buildClasspath(), "org.griesbacher.jocose.TestApplicationWithoutExit")
                .start();
        try {
            // Wait for application to start
            app.getInputStream().read();

            InputStream stream = new URL("http://localhost:" + port + "/metrics").openStream();
            BufferedReader contents = new BufferedReader(new InputStreamReader(stream));
            boolean jmx_scrape_duration_seconds_found = false;
            boolean foo_bar_found = false;
            String line;
            while ((line = contents.readLine()) != null) {
                if (line.contains("jmx_scrape_duration_seconds")) {
                    jmx_scrape_duration_seconds_found = true;
                }
                if (line.contains("foo_bar 84.0")) {
                    foo_bar_found = true;
                }
            }
            assertTrue("Expected metric 'jmx_scrape_duration_seconds' not found", jmx_scrape_duration_seconds_found);
            assertTrue("Expected metric 'foo_bar' not found", foo_bar_found);

            // Tell application to stop
            app.getOutputStream().write('\n');
            try {
                app.getOutputStream().flush();
            } catch (IOException ignored) {
            }
        } finally {
            final int exitcode = app.waitFor();
            // Log any errors printed
            int len;
            byte[] buffer = new byte[100];
            while ((len = app.getErrorStream().read(buffer)) != -1) {
                System.out.write(buffer, 0, len);
            }

            assertThat("Application did not exit cleanly. Returncode: " + exitcode, exitcode == 0);
        }
    }

}
