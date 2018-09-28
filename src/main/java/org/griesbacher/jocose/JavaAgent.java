package org.griesbacher.jocose;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Gauge;
import io.prometheus.client.hotspot.DefaultExports;
import io.prometheus.jmx.JmxCollector;

import javax.management.MalformedObjectNameException;
import java.io.*;
import java.lang.instrument.Instrumentation;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.HashMap;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * This JavaAgent will export jmx metrics and register at a consul if needed.
 */
public class JavaAgent {
    static final String PROMETHEUS_PREFIX = "jocose_";
    static final Logger LOGGER = Logger.getLogger(JavaAgent.class.getName());
    private static final String SERVICE_NAME = "serviceName";
    private static final String CONFIG = "config";
    private static final Gauge WEB_SERVER_CREATION = Gauge.build().name(PROMETHEUS_PREFIX + "web_server_creation_seconds")
            .help("Time in seconds to start the web server").register();
    private static final Gauge START_UP = Gauge.build().name(PROMETHEUS_PREFIX + "startup_seconds")
            .help("Time in seconds to get everything running").register();
    private static final Gauge CONFIG_HANDLING = Gauge.build().name("config_handling_seconds")
            .help("Time in seconds to read and parse the config").register();
    private static final Gauge PROMETHEUS_REGISTRATION = Gauge.build().name(PROMETHEUS_PREFIX + "prometheus_registration_seconds")
            .help("Time in seconds to start the jmx / default exporter").register();


    static PrometheusHTTPServer server;
    static InetSocketAddress address;
    static Config ymlConfig;

    private JavaAgent() {
    }

    /**
     * Returns with an error code 1 and the given message.
     *
     * @param s       the format string for the message
     * @param objects the params for the format string
     */
    static void errorExit(String s, Object... objects) {
        if (!s.endsWith("\n")) {
            s += "\n";
        }
        System.err.printf(s, objects);
        System.exit(1);
    }

    private static String readConfig(Reader reader) throws IOException {
        char[] arr = new char[1024];
        StringBuilder buffer = new StringBuilder();
        int numCharsRead;
        while ((numCharsRead = reader.read(arr, 0, arr.length)) != -1) {
            buffer.append(arr, 0, numCharsRead);
        }
        reader.close();
        return buffer.toString();
    }


    private static void startWebServer(String host) throws IOException {
        if (ymlConfig.ownPort == -1) {
            if (!ymlConfig.enabled) {
                errorExit("No valid port has been given, but the consul ymlConfig section is disabled");
            }

            //Trying to find an open port
            for (int currentPort = ymlConfig.startPort; currentPort < ymlConfig.endPort; currentPort++) {
                if ("".equals(host)) {
                    address = new InetSocketAddress(currentPort);
                } else {
                    address = new InetSocketAddress(host, currentPort);
                }
                try {
                    server = new PrometheusHTTPServer(address, CollectorRegistry.defaultRegistry, true);
                    break;
                } catch (IOException e) {
                    LOGGER.fine(e.getMessage());
                }
            }

            if (server == null) {
                errorExit("No free port could be found");
            }
        } else {
            //Use the given host:port combination
            address = new InetSocketAddress(ymlConfig.ownHost, ymlConfig.ownPort);
            server = new PrometheusHTTPServer(address, CollectorRegistry.defaultRegistry, true);
        }
    }

    static float nanoSecondsToSeconds(long nanoSconds) {
        return ((float) nanoSconds) / 1000000000;
    }

    /**
     * The main method of this agent
     *
     * @param agentArgument   commandline arguments are expected here
     * @param instrumentation this is never used
     * @throws IOException                  if config can not be found or consul is not available
     * @throws MalformedObjectNameException if prometheus does so
     */
    public static void premain(String agentArgument, Instrumentation instrumentation) throws IOException, MalformedObjectNameException {
        final long overallStart = System.nanoTime();
        final String usageText = "Usage: -javaagent:/path/to/JavaAgent.jar=-h[host],-p[port],-c<yaml configuration file>\n" +
                "-H [host] host to start exporter. If empty 0.0.0.0 will be used\n" +
                "-p [port] port to start exporter. If empty a free port will be used, this requires a configuration file\n" +
                "-c <path to your configuration>. Possibilities:\n" +
                "\tfile:///path/to/your/ymlConfig.yml\n" +
                "\thttp://https://url/to/your/ymlConfig.yml\n" +
                "-s serviceName, this will win against the value from the configuration file\n" +
                "-h this message" +
                "Example:\n" +
                "-javaagent:/path/to/JavaAgent.jar=-h127.0.0.1,-cfile:///tmp/ymlConfig.yml";
        String[] args = agentArgument.split(",");
        HashMap<String, String> argMap = new HashMap<String, String>() {
        };
        for (String arg : args) {
            if (arg.length() > 2) {
                String prefix = arg.substring(0, 2);
                if ("-h".equals(prefix)) {
                    errorExit(usageText);
                } else if ("-H".equals(prefix)) {
                    argMap.put("host", arg.substring(2).trim());
                } else if ("-p".equals(prefix)) {
                    argMap.put("port", arg.substring(2).trim());
                } else if ("-c".equals(prefix)) {
                    argMap.put(CONFIG, arg.substring(2).trim());
                } else if ("-s".equals(prefix)) {
                    argMap.put(SERVICE_NAME, arg.substring(2).trim());
                }
            }
        }

        //Parse the given host
        final String host;
        if (argMap.containsKey("host")) {
            host = argMap.get("host");
        } else {
            host = "";
        }

        //Parse the given port
        int port;
        if (argMap.containsKey("port")) {
            try {
                port = Integer.parseInt(argMap.get("port"));
            } catch (NumberFormatException e) {
                port = -1;
            }
        } else {
            port = -1;
        }


        //Check file argument
        final long configStart = System.nanoTime();
        final String configString;
        if (argMap.containsKey(CONFIG)) {
            String[] fileSplitted = argMap.get(CONFIG).split("://", 2);
            if (fileSplitted.length != 2) {
                errorExit("The given file argument is not valid: '%s'", argMap.get(CONFIG));
            }
            final String fileType = fileSplitted[0];
            final String file = fileSplitted[1];
            final Reader configReader;

            //Check file depending on type
            if ("file".equals(fileType)) {
                File f = new File(file);
                if (!f.exists() || f.isDirectory()) {
                    errorExit("File: '%s' does not exists or is a directory", args[1]);
                }
                configReader = new FileReader(f);
            } else if ("http".equals(fileType)) {
                configReader = new InputStreamReader(new URL(file).openStream());
            } else {
                errorExit("This type '%s' is not supported.", fileType);
                return;
            }
            //Read ymlConfig
            configString = readConfig(configReader);
        } else {
            if (port == -1) {
                errorExit("No ymlConfig has been given nor an port.");
            }
            configString = "";
        }

        //Parse ymlConfig
        ymlConfig = ConfigHandler.parseConfig(new StringReader(configString));
        if (argMap.containsKey(SERVICE_NAME)) {
            ymlConfig.serviceName = argMap.get(SERVICE_NAME);
        }
        CONFIG_HANDLING.set(nanoSecondsToSeconds(System.nanoTime() - configStart));

        //Add host and port
        ymlConfig.ownHost = host;
        ymlConfig.ownPort = port;

        //Start collectors
        final long webPrometheus = System.nanoTime();
        new JmxCollector(configString).register();
        DefaultExports.initialize();
        PROMETHEUS_REGISTRATION.set(nanoSecondsToSeconds(System.nanoTime() - webPrometheus));

        //Start metric webservice
        final long webStart = System.nanoTime();
        startWebServer(host);
        WEB_SERVER_CREATION.set(nanoSecondsToSeconds(System.nanoTime() - webStart));

        // If consul is disabled only the exporter will be started
        if (!ymlConfig.enabled) {
            return;
        }

        // Start alive signal
        PrometheusAliveSignal prometheusAliveSignal = new PrometheusAliveSignal();
        prometheusAliveSignal.start();

        // From now on only consul interactions follow

        // Find a hostname for the consul entry
        final String addressForConsul;
        if (ymlConfig.exporterAddress != null) {
            addressForConsul = ymlConfig.exporterAddress;
        } else if ("0.0.0.0".equals(address.getHostName())) {
            addressForConsul = InetAddress.getLocalHost().getHostName();
        } else {
            addressForConsul = address.getHostName();
        }

        final String id = UUID.randomUUID().toString();
        final String name;
        if (ymlConfig.serviceName == null || "".equals(ymlConfig.serviceName.trim())) {
            name = id;
        } else {
            name = ymlConfig.serviceName;
        }

        // register at consul
        final long consulStart = System.nanoTime();
        Consul consul = new Consul(
                id, name, ymlConfig.tags, addressForConsul, address.getPort(),
                ymlConfig.consulAddress, ymlConfig.check
        );

        // provide website for consul health check
        server.addUUIDContext(id);

        // deregister at consul if registration went well
        Runtime.getRuntime().addShutdownHook(new Consul.Deregister(consul));

        // start consul registration alive check
        new ConsulRegistrationCheck(consul).start();

        START_UP.set(nanoSecondsToSeconds(System.nanoTime() - overallStart));
    }
}