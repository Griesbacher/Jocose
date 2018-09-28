package org.griesbacher.jocose;

import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;


public class ConfigHandlerTest {
    public static PrintStream oldErr = System.err;
    public static PrintStream dummyErr = new PrintStream(new ByteArrayOutputStream());

    public static void disableStdErr() {
        System.setErr(dummyErr);
    }

    public static void enableStdErr() {
        System.setErr(oldErr);
    }

    @Rule
    public final ExpectedSystemExit exit = ExpectedSystemExit.none();

    @Rule
    public final EnvironmentVariables environmentVariables = new EnvironmentVariables();

    @Test
    public void emptyConfigTest() throws Exception {
        exit.expectSystemExitWithStatus(1);
        String configString = "";
        disableStdErr();
        ConfigHandler.parseConfig(new StringReader(configString));
        enableStdErr();
    }

    @Test
    public void invalidConfigTest() throws Exception {
        exit.expectSystemExitWithStatus(1);
        String configString = "- 1\n- 2\n- 3";
        disableStdErr();
        ConfigHandler.parseConfig(new StringReader(configString));
        enableStdErr();
    }

    @Test
    public void invalidYAMLTest() throws Exception {
        exit.expectSystemExitWithStatus(1);
        String configString = "%123";
        disableStdErr();
        ConfigHandler.parseConfig(new StringReader(configString));
        enableStdErr();
    }

    @Test
    public void invalidAddressEnabledTest() throws Exception {
        exit.expectSystemExitWithStatus(1);
        String configString = "consul:\n" +
                "  enabled: true\n";
        disableStdErr();
        ConfigHandler.parseConfig(new StringReader(configString));
        enableStdErr();
    }

    @Test
    public void invalidPortTest() throws Exception {
        exit.expectSystemExitWithStatus(1);
        String configString = "consul:\n" +
                "  enabled: true\n" +
                "  address: 127.0.0.1:123\n";
        disableStdErr();
        ConfigHandler.parseConfig(new StringReader(configString));
        enableStdErr();
    }

    @Test
    public void invalidPortEnabledTest() throws Exception {
        exit.expectSystemExitWithStatus(1);
        String configString = "consul:\n" +
                "  address: 127:123\n" +
                "  portRange: 123a\n" +
                "  enabled: true\n";
        disableStdErr();
        ConfigHandler.parseConfig(new StringReader(configString));
        enableStdErr();
    }

    @Test
    public void pureJMXConfigTest() throws Exception {
        String configString = "---\n" +
                "startDelaySeconds: 0\n" +
                "hostPort: 127.0.0.1:1234\n" +
                "jmxUrl: service:jmx:rmi:///jndi/rmi://127.0.0.1:1234/jmxrmi\n" +
                "ssl: false\n" +
                "lowercaseOutputName: false\n" +
                "lowercaseOutputLabelNames: false\n" +
                "whitelistObjectNames: [\"org.apache.cassandra.metrics:*\"]\n" +
                "blacklistObjectNames: [\"org.apache.cassandra.metrics:type=ColumnFamily,*\"]\n" +
                "rules:\n" +
                "  - pattern: 'org.apache.cassandra.metrics<type=(\\w+), name=(\\w+)><>Value: (\\d+)'\n" +
                "    name: cassandra_$1_$2\n" +
                "    value: $3\n" +
                "    valueFactor: 0.001\n" +
                "    labels: {}\n" +
                "    help: \"Cassandra metric $1 $2\"\n" +
                "    type: GAUGE\n" +
                "    attrNameSnakeCase: false";
        Config config = ConfigHandler.parseConfig(new StringReader(configString));
        assertNotNull(config);
    }

    @Test
    public void jmxConfigWithConsulTest() throws Exception {
        String configString = "---\n" +
                "startDelaySeconds: 0\n" +
                "hostPort: 127.0.0.1:1234\n" +
                "jmxUrl: service:jmx:rmi:///jndi/rmi://127.0.0.1:1234/jmxrmi\n" +
                "ssl: false\n" +
                "lowercaseOutputName: false\n" +
                "lowercaseOutputLabelNames: false\n" +
                "whitelistObjectNames: [\"org.apache.cassandra.metrics:*\"]\n" +
                "blacklistObjectNames: [\"org.apache.cassandra.metrics:type=ColumnFamily,*\"]\n" +
                "rules:\n" +
                "  - pattern: 'org.apache.cassandra.metrics<type=(\\w+), name=(\\w+)><>Value: (\\d+)'\n" +
                "    name: cassandra_$1_$2\n" +
                "    value: $3\n" +
                "    valueFactor: 0.001\n" +
                "    labels: {}\n" +
                "    help: \"Cassandra metric $1 $2\"\n" +
                "    type: GAUGE\n" +
                "    attrNameSnakeCase: false\n" +
                "consul:\n" +
                "  enabled: true\n" +
                "  serviceName: Test Client\n" +
                "  address: http://127.0.0.1:8500\n" +
                "  portRange: 5000-6000\n" +
                "  exporterAddress: host.example.com\n" +
                "  tags:\n" +
                "    - foo\n" +
                "    - bar";

        Config config = ConfigHandler.parseConfig(new StringReader(configString));
        assertNotNull(config);
        assertTrue(config.enabled);
        assertTrue(config.consulAddress.equals("http://127.0.0.1:8500"));
        assertTrue(config.startPort == 5000);
        assertTrue(config.endPort == 6000);
        assertTrue(config.tags.containsAll(Arrays.asList("foo", "bar")));
        assertNull(config.ownHost);
        assertNotNull(config.check);
        assertTrue(config.ownPort == -1);
        assertTrue(config.serviceName.equals("Test Client"));
        assertTrue(config.exporterAddress.equals("host.example.com"));

        // Test toString
        final String toString = "Config{enabled=true, consulAddress='http://127.0.0.1:8500'," +
                " startPort=5000, endPort=6000, serviceName='Test Client'," +
                " exporterAddress='host.example.com', ownHost='null', ownPort=-1," +
                " check=Check{enabled=false, checkInterval='null', deregisterPeriod='null'}}";
        assertTrue(config.toString().equals(toString));
    }

    @Test
    public void envVariablesTest() throws Exception {
        String configString = "---\n" +
                "consul:\n" +
                "  enabled: true\n" +
                "  serviceName: Test Client\n" +
                "  address: http://127.0.0.1:8500\n" +
                "  portRange: 5000-6000\n" +
                "  exporterAddress: host.example.com\n" +
                "  tags:\n" +
                "    - $ENV(\"SECRET_KEY\")-123\n" +
                "    - $ENV(\"SECRET_KEY\")-123-$ENV(\"SECRET_KEY2\")\n" +
                "    - $ENV(\"FOO\")\n" +
                "    - bar";

        environmentVariables.set("SECRET_KEY", "42");
        environmentVariables.set("SECRET_KEY2", "84");
        Config config = ConfigHandler.parseConfig(new StringReader(configString));
        final List<String> expected = Arrays.asList("42-123", "42-123-84", "$ENV(\"FOO\")", "bar");
        assertTrue(String.format("Expected: '%s' Got: '%s'", expected, config.tags), config.tags.containsAll(expected));
    }

    @Test
    public void systemPropertiesTest() throws Exception {
        String configString = "---\n" +
                "consul:\n" +
                "  enabled: true\n" +
                "  serviceName: Test Client\n" +
                "  address: http://127.0.0.1:8500\n" +
                "  portRange: 5000-6000\n" +
                "  exporterAddress: host.example.com\n" +
                "  tags:\n" +
                "    - $SYSTEM_PROPERTY(\"SECRET_KEY\")-123\n" +
                "    - $SYSTEM_PROPERTY(\"SECRET_KEY\")-123-$SYSTEM_PROPERTY(\"SECRET_KEY2\")\n" +
                "    - $SYSTEM_PROPERTY(\"FOO\")\n" +
                "    - bar";

        System.setProperty("SECRET_KEY", "42");
        System.setProperty("SECRET_KEY2", "84");
        Config config = ConfigHandler.parseConfig(new StringReader(configString));
        final List<String> expected = Arrays.asList("42-123", "42-123-84", "$SYSTEM_PROPERTY(\"FOO\")", "bar");
        assertTrue(String.format("Expected: '%s' Got: '%s'", expected, config.tags), config.tags.containsAll(expected));
    }

    @Test
    public void argsTest() throws Exception {
        String configString = "---\n" +
                "consul:\n" +
                "  enabled: true\n" +
                "  serviceName: Test Client\n" +
                "  address: http://127.0.0.1:8500\n" +
                "  portRange: 5000-6000\n" +
                "  exporterAddress: host.example.com\n" +
                "  tags:\n" +
                "    - $ARG(1)-123\n" +
                "    - $ARG(1)-123-$ARG(2)\n" +
                "    - $ARG(9)\n" +
                "    - $CLASSNAME\n" +
                "    - bar";

        System.setProperty("sun.java.command", "test.Main 42 84");
        Config config = ConfigHandler.parseConfig(new StringReader(configString));
        final List<String> expected = Arrays.asList("42-123", "42-123-84", "$ARG(9)", "Main", "bar");
        assertTrue(String.format("Expected: '%s' Got: '%s'", expected, config.tags), config.tags.containsAll(expected));
    }

    @Test
    public void emptyTagsTest() throws Exception {
        String configString = "---\n" +
                "consul:\n" +
                "  enabled: true\n" +
                "  serviceName: Test Client\n" +
                "  address: http://127.0.0.1:8500\n" +
                "  portRange: 5000-6000\n" +
                "  exporterAddress: host.example.com";
        Config config = ConfigHandler.parseConfig(new StringReader(configString));
        final List<String> expected = Arrays.asList();
        assertTrue(String.format("Expected: '%s' Got: '%s'", expected, config.tags), config.tags.containsAll(expected));
    }

    @Test
    public void checkTest() throws Exception {
        String configString = "---\n" +
                "consul:\n" +
                "  enabled: true\n" +
                "  serviceName: Test Client\n" +
                "  address: http://127.0.0.1:8500\n" +
                "  portRange: 5000-6000\n" +
                "  exporterAddress: host.example.com\n" +
                "  check:\n" +
                "    enabled: true\n" +
                "    checkInterval: \"10s\"\n" +
                "    deregisterPeriod: \"1m\"";
        Config config = ConfigHandler.parseConfig(new StringReader(configString));
        final Config.Check check = new Config.Check(true, "10s", "1m");
        assertTrue(
                String.format("Expected: '%s' Got: '%s'", check.enabled, config.check.enabled),
                check.enabled == config.check.enabled)
        ;
        assertTrue(String.format(
                "Expected: '%s' Got: '%s'", check.checkInterval, config.check.checkInterval),
                check.checkInterval.equals(config.check.checkInterval)
        );
        assertTrue(String.format(
                "Expected: '%s' Got: '%s'", check.deregisterPeriod, config.check.deregisterPeriod),
                check.deregisterPeriod.equals(config.check.deregisterPeriod)
        );
    }

    @Test
    public void checkToStringTest() throws Exception {
        final Config.Check check = new Config.Check(true, "10s", "1m");
        final String url = "http://localhost";
        final String expected = String.format(",\n" +
                "  \"Check\": {\n" +
                "    \"DeregisterCriticalServiceAfter\": \"1m\",\n" +
                "    \"HTTP\": \"%s\",\n" +
                "    \"Interval\": \"10s\"\n" +
                "  }\n", url);
        assertTrue(String.format("Expected: '%s' Got: '%s'", expected, check.toJSON(url)), expected.equals(check.toJSON(url)));
    }
}
