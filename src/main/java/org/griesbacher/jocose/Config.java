package org.griesbacher.jocose;

import java.util.List;

/**
 * This class represents the configuration file.
 */
public class Config {
    boolean enabled = false;
    String consulAddress;
    int startPort = -1;
    int endPort = -1;
    String serviceName;
    String exporterAddress;
    List<String> tags;

    //is not stored in ymlConfig, will be set from agent
    String ownHost;
    int ownPort = -1;

    Check check;

    Config() {
    }

    @Override
    public String toString() {
        return "Config{" +
                "enabled=" + enabled +
                ", consulAddress='" + consulAddress + '\'' +
                ", startPort=" + startPort +
                ", endPort=" + endPort +
                ", serviceName='" + serviceName + '\'' +
                ", exporterAddress='" + exporterAddress + '\'' +
                ", ownHost='" + ownHost + '\'' +
                ", ownPort=" + ownPort +
                ", check=" + check +
                '}';
    }

    /**
     * Check represents the subclass of service, within the config.
     */
    public static class Check {
        boolean enabled = false;
        String checkInterval;
        String deregisterPeriod;

        Check(final boolean enabled, final String checkInterval, final String deregisterPeriod) {
            this.enabled = enabled;
            this.deregisterPeriod = deregisterPeriod;
            this.checkInterval = checkInterval;
        }

        Check() {
        }

        /**
         * This generates an JSON string for the consul service object.
         * @param address The address of the exporter.
         * @return Returns a JSON like string.
         */
        public String toJSON(final String address) {
            return String.format(
                    ",\n" +
                            "  \"Check\": {\n" +
                            "    \"DeregisterCriticalServiceAfter\": \"%s\",\n" +
                            "    \"HTTP\": \"%s\",\n" +
                            "    \"Interval\": \"%s\"\n" +
                            "  }\n", this.deregisterPeriod, address, this.checkInterval);
        }

        @Override
        public String toString() {
            return "Check{" +
                    "enabled=" + enabled +
                    ", checkInterval='" + checkInterval + '\'' +
                    ", deregisterPeriod='" + deregisterPeriod + '\'' +
                    '}';
        }
    }
}