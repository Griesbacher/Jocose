package org.griesbacher.jocose;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Consul will be used to communicate with the consul server.
 * This is a poor mans API, the official API is not used to avoid class loading conflicts.
 */
class Consul {
    private static final int HTTP_TIMEOUT = 5000;
    private static final Logger LOGGER = Logger.getLogger(Consul.class.getName());
    private static final String APPLICATION_JSON = "application/json";
    private final String id;
    private final String name;
    private final List<String> tags;
    private final String host;
    private final int port;
    private final String consulAddress;
    private final Config.Check check;
    private final Pattern regexIDPattern = Pattern.compile("\"(.*?)\"\\s*:.*\\{");

    public Consul(
            final String id, final String name, final List<String> tags, final String host,
            final int port, final String consulAddress, final Config.Check check
    ) {
        this.id = id;
        this.name = name;
        this.tags = tags;
        this.host = host;
        this.port = port;
        this.consulAddress = consulAddress;
        this.check = check;
    }

    private static boolean putRequest(final URL url, final String data) {
        try {
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("PUT");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", APPLICATION_JSON);
            connection.setRequestProperty("Accept", APPLICATION_JSON);
            connection.setConnectTimeout(HTTP_TIMEOUT);
            OutputStreamWriter osw = new OutputStreamWriter(connection.getOutputStream());
            osw.write(data);
            osw.flush();
            osw.close();
            return connection.getResponseCode() == 200;
        } catch (IOException e) {
            LOGGER.fine(e.getMessage());
            return false;
        }
    }

    private static String getRequest(final URL url) {
        try {
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", APPLICATION_JSON);
            connection.setRequestProperty("Accept", APPLICATION_JSON);
            connection.setConnectTimeout(HTTP_TIMEOUT);
            final BufferedReader bufferedReader;
            if (200 <= connection.getResponseCode() && connection.getResponseCode() <= 299) {
                bufferedReader = new BufferedReader(new InputStreamReader((connection.getInputStream())));
            } else {
                bufferedReader = new BufferedReader(new InputStreamReader((connection.getErrorStream())));
            }
            StringBuilder stringBuilder = new StringBuilder();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                if ("".equals(line)) {
                    break;
                }
                stringBuilder.append(line).append("\n");
            }
            return stringBuilder.toString().replaceAll("},", "},\n");
        } catch (IOException e) {
            LOGGER.fine(e.getMessage());
            return e.getMessage();
        }
    }

    /**
     * Registers the service at the consul.
     *
     * @return true if the registration went well, false else.
     */
    boolean registerService() {
        final String consulTemplate = "{\n" +
                "  \"ID\": \"%s\",\n" +
                "  \"Name\": \"%s\",\n" +
                "  \"Tags\": [ %s ],\n" +
                "  \"Address\": \"%s\",\n" +
                "  \"Port\": %d%s\n" +
                "}";
        final StringBuilder tagBuilder = new StringBuilder();
        for (String tag : tags) {
            tagBuilder.append("\"").append(tag).append("\"").append(",");
        }
        if (tagBuilder.length() > 0) {
            tagBuilder.delete(tagBuilder.length() - 1, tagBuilder.length());
        }
        final String consulServiceJSON;
        if (check.enabled) {
            consulServiceJSON = String.format(consulTemplate, id, name, tagBuilder, host, port, check.toJSON(
                    String.format("http://%s:%d/%s", host, port, id))
            );
        } else {
            consulServiceJSON = String.format(consulTemplate, id, name, tagBuilder, host, port, "");
        }
        final URL url;
        try {
            url = new URL(String.format("%s/v1/agent/service/register", consulAddress));
            return putRequest(url, consulServiceJSON);
        } catch (MalformedURLException e) {
            LOGGER.fine(e.getMessage());
            return false;
        }
    }

    /**
     * Deregisters the service.
     *
     * @return If the service could be deregistered.
     */
    boolean deregisterService() {
        final URL url;
        try {
            url = new URL(String.format("%s/v1/agent/service/deregister/%s", consulAddress, id));
            return putRequest(url, "");
        } catch (MalformedURLException e) {
            LOGGER.fine(e.getMessage());
            return false;
        }
    }

    /**
     * Tests if the service is still registered at consul.
     *
     * @return returns true if the service is registered false else.
     */
    boolean isServiceRegistered() {
        final URL url;
        try {
            url = new URL(String.format("%s/v1/agent/services", consulAddress));
            final String consulJSON = getRequest(url);
            Matcher matcher = regexIDPattern.matcher(consulJSON);
            while (matcher.find()) {
                if (matcher.group(1).equals(id)) {
                    return true;
                }
            }
        } catch (MalformedURLException e) {
            LOGGER.fine(e.getMessage());
        }
        return false;
    }

    /**
     * This Thread can be used to deregister at the end of reporting.
     */
    public static class Deregister extends Thread {
        private final Consul consul;

        Deregister(Consul consul) {
            this.consul = consul;
        }

        @Override
        public void run() {
            consul.deregisterService();
        }
    }

}
