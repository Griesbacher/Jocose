package org.griesbacher.jocose;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.ArrayList;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ConsulJavaAgentTest {
    public static HttpServer MockConsulRegistration(final int port) throws IOException {
        final HttpServer httpServer = HttpServer.create(new InetSocketAddress(port), 0);
        httpServer.start();
        httpServer.createContext("/v1/agent/service/register", new HttpHandler() {
            public void handle(HttpExchange exchange) throws IOException {
                final Gson g = new Gson();
                final ConsulService registeredConsulService = g.fromJson(new InputStreamReader(exchange.getRequestBody()), ConsulService.class);
                assertNotNull(registeredConsulService);
                assertTrue(registeredConsulService.ID != null && !"".equals(registeredConsulService.ID.trim()));

                final byte[] response = new byte[]{};
                exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, response.length);
                exchange.getResponseBody().write(response);
                exchange.close();

                httpServer.createContext(String.format("/v1/agent/service/deregister/%s", registeredConsulService.ID), new HttpHandler() {
                    public void handle(HttpExchange exchange) throws IOException {
                        final byte[] response = new byte[]{};
                        exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, response.length);
                        exchange.getResponseBody().write(response);
                        exchange.close();
                        registeredConsulService.ID = "---";
                    }
                });

                httpServer.createContext("/v1/agent/services", new HttpHandler() {
                    public void handle(HttpExchange exchange) throws IOException {
                        final byte[] response = String.format("{" +
                                "\"2e838f0f-0e32-4fee-b0c9-d40600e5ef96\":{\"EnableTagOverride\":false,\"ModifyIndex\":0,\"CreateIndex\":0,\"ID\":\"2e838f0f-0e32-4fee-b0c9-d40600e5ef96\"}," +
                                "\"8f67e672-1564-439e-9fd2-ecedd81c65ce\":{\"CreateIndex\":0,\"ModifyIndex\":0,\"ID\":\"8f67e672-1564-439e-9fd2-ecedd81c65ce\",\"EnableTagOverride\":false}," +
                                "\"%s\":{\"CreateIndex\":0,\"ModifyIndex\":0,\"ID\":\"%s\",\"EnableTagOverride\":false}" +
                                "}", registeredConsulService.ID, registeredConsulService.ID).getBytes();
                        exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, response.length);
                        exchange.getResponseBody().write(response);
                        exchange.close();
                    }
                });
            }
        });
        return httpServer;
    }


    @Test
    public void consulRegistrationTest() throws Exception {
        MockConsulRegistration(8500);

        System.setProperty("sun.java.command", "org.apache.hadoop.mapred.YarnChild 192.168.1.100 44946 attempt_1506428524976_0013_m_000001_0 3");

        final String config = getClass().getClassLoader().getResource("consul_example_config.yml").getFile();
        final String host = "127.0.0.1";
        //Start exporter
        JavaAgent.premain("-H" + host + ",-cfile://" + config, null);
        final URL url = new URL(String.format("http://%s:%d/metrics", host, JavaAgent.address.getPort()));

        // Test if job id has been detected
        assertTrue(JavaAgent.ymlConfig.tags.contains("job_1506428524976_0013"));

        // Test metric export
        final HttpURLConnection con = (HttpURLConnection) url.openConnection();
        assertTrue(con.getResponseCode() == 200);

        JavaAgent.server.stop();
    }

    @Test
    public void consulIsServiceRegisteredTest() throws Exception {
        final int port = 8700;
        MockConsulRegistration(port);
        final Consul consul = new Consul(
                "123-456-789", "foo", new ArrayList<String>(),
                "localhost", 123, String.format("http://localhost:%d", port),
                new Config.Check()
        );

        assertTrue(consul.registerService());
        assertTrue(consul.isServiceRegistered());
        assertTrue(consul.deregisterService());
        assertFalse(consul.isServiceRegistered());
    }
}
