package org.griesbacher.jocose;

import org.yaml.snakeyaml.Yaml;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.griesbacher.jocose.JavaAgent.errorExit;


class ConfigHandler {
    private static final String ADDRESS = "address";
    private static final String PORT_RANGE = "portRange";
    private static final String TAGS = "tags";
    private static final String CHECK = "check";
    private static final String ENABLED = "enabled";
    private static final String CONSUL = "consul";

    private ConfigHandler() {
    }

    private static Map<String, Object> loadYAML(Reader reader) {
        final Object yamlObj;
        try {
            yamlObj = new Yaml().load(reader);
            if (!(yamlObj instanceof Map)) {
                errorExit("Configfile is not in a valid map structure");
            }

            // load jmx ymlConfig
            Map<String, Object> configMap = (Map<String, Object>) yamlObj;
            if (configMap != null && configMap.containsKey(CONSUL) && configMap.get(CONSUL) instanceof Map) {
                // load consul ymlConfig
                return (Map<String, Object>) configMap.get(CONSUL);
            }
        } catch (Exception e) {
            errorExit("Configfile does not contain valid YAML");
        }
        return new HashMap<String, Object>();
    }

    private static Config.Check loadCheck(Map<String, Object> consulConfig) {
        final Config.Check check = new Config.Check();
        if (consulConfig.containsKey(CHECK) && consulConfig.get(CHECK) instanceof Map) {
            Map<String, Object> config = (Map<String, Object>) consulConfig.get(CHECK);

            if (config.containsKey(ENABLED)) {
                check.enabled = (Boolean) config.get(ENABLED);
            }
            if (config.containsKey("checkInterval")) {
                check.checkInterval = (String) config.get("checkInterval");
            }
            if (config.containsKey("deregisterPeriod")) {
                check.deregisterPeriod = (String) config.get("deregisterPeriod");
            }
        }
        return check;
    }

    static Config parseConfig(Reader reader) {
        final Config config = new Config();
        final Map<String, Object> consulConfig = loadYAML(reader);

        if (consulConfig.containsKey(ENABLED)) {
            config.enabled = (Boolean) consulConfig.get(ENABLED);
        }
        if (consulConfig.containsKey(ADDRESS)) {
            config.consulAddress = ((String) consulConfig.get(ADDRESS));
        } else if (config.enabled) {
            errorExit("The consul address field is mandatory if consul is enabled");
        }

        if (consulConfig.containsKey("serviceName")) {
            config.serviceName = (String) consulConfig.get("serviceName");
        }

        if (consulConfig.containsKey("exporterAddress")) {
            config.exporterAddress = (String) consulConfig.get("exporterAddress");
        }

        if (consulConfig.containsKey(PORT_RANGE)) {
            String[] portRange = ((String) consulConfig.get(PORT_RANGE)).split("-");
            if (portRange.length == 2) {
                config.startPort = Integer.parseInt(portRange[0]);
                config.endPort = Integer.parseInt(portRange[1]);
            } else {
                errorExit(
                        "The given consulPort range is invalid. Expected consulPort-consulPort. Given: %s",
                        consulConfig.get(PORT_RANGE)
                );
            }
        } else if (config.enabled) {
            errorExit("The %s field is mandatory if consul is enabled", PORT_RANGE);
        }

        if (consulConfig.containsKey(TAGS) && consulConfig.get(TAGS) instanceof List) {
            config.tags = (ArrayList) consulConfig.get(TAGS);
            config.tags = replaceSpecialTags(config.tags);
        } else {
            config.tags = new ArrayList<String>();
        }
        config.tags = addAdditionalTags(config.tags);

        config.check = loadCheck(consulConfig);

        return config;
    }

    private static List<String> addAdditionalTags(List<String> tags) {
        // Trying to find a hadoop job id and add it as tag
        String job = searchJob();
        if (!"".equals(job)) {
            tags.add("job_" + job);
        }

        // Trying to add the username
        final String userName = System.getProperty("user.name");
        if (userName != null) {
            tags.add("user_" + userName);
        }
        return tags;
    }

    private static String searchJob() {
        Pattern p = Pattern.compile("^attempt_(.*?)_\\w_.*");
        for (String arg : System.getProperty("sun.java.command").split(" ")) {
            Matcher m = p.matcher(arg);
            if (m.matches()) {
                return m.group(1);
            }
        }
        return "";
    }


    private static List<String> replaceSpecialTags(List<String> tags) {
        final Pattern envPattern = Pattern.compile("(\\$ENV\\(\"(.*?)\"\\))");
        final Pattern systemPropertyPattern = Pattern.compile("(\\$SYSTEM_PROPERTY\\(\"(.*?)\"\\))");
        final Pattern argsPattern = Pattern.compile("(\\$ARG\\((\\d+)\\))");

        for (int i = 0; i < tags.size(); i++) {
            String tag = tags.get(i);
            // Replace $ENV("KEY") with the env value
            final Matcher envMatcher = envPattern.matcher(tag);
            while (envMatcher.find()) {
                if (System.getenv(envMatcher.group(2)) != null) {
                    tag = tag.replaceAll(Pattern.quote(envMatcher.group(1)), System.getenv(envMatcher.group(2)));
                }
            }

            // $SYSTEM_PROPERTY("KEY") with the SystemProperty value
            final Matcher systemPropertyMatcher = systemPropertyPattern.matcher(tag);
            while (systemPropertyMatcher.find()) {
                if (System.getProperty(systemPropertyMatcher.group(2)) != null) {
                    tag = tag.replaceAll(Pattern.quote(systemPropertyMatcher.group(1)), System.getProperty(systemPropertyMatcher.group(2)));
                }
            }

            // $ARG("KEY") with the SystemProperty value
            final Matcher argsMatcher = argsPattern.matcher(tag);
            String[] args = System.getProperty("sun.java.command").split(" ");
            while (argsMatcher.find()) {
                int argsIndex = Integer.parseInt(argsMatcher.group(2));
                if (argsIndex < args.length) {
                    tag = tag.replaceAll(Pattern.quote(argsMatcher.group(1)), args[argsIndex]);
                }
            }

            // $CLASSNAME
            if (args.length > 0) {
                String[] path = args[0].split("\\.");
                if (path.length > 0) {
                    tag = tag.replaceAll("\\$CLASSNAME", path[path.length - 1]);
                }
            }

            tags.set(i, tag);
        }
        return tags;
    }
}
