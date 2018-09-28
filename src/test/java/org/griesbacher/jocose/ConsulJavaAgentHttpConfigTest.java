package org.griesbacher.jocose;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.Test;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;

import static org.junit.Assert.assertTrue;

public class ConsulJavaAgentHttpConfigTest {

    @Test
    public void consulRegistrationTest() throws Exception {
        JavaAgent.LOGGER.setUseParentHandlers(false);
        ConsulJavaAgentTest.MockConsulRegistration(8600);
        final HttpServer httpServer = HttpServer.create(new InetSocketAddress(8080), 0);
        httpServer.start();
        httpServer.createContext("/config.yml", new HttpHandler() {
            public void handle(HttpExchange exchange) throws IOException {
                byte[] response = ("---\n" +
                        "consul:\n" +
                        "  enabled: true\n" +
                        "  address: 127.0.0.1:8600\n" +
                        "  useHTTPS: false\n" +
                        "  portRange: 9000-9200\n" +
                        "  serviceName: Test Client\n" +
                        "  exporterAddress: \"localhost\"\n" +
                        "  tags:\n" +
                        "    - foo\n" +
                        "    - bar").getBytes();
                exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, response.length);
                exchange.getResponseBody().write(response);
                exchange.close();
            }
        });

        final String host = "127.0.0.1";
        //Start exporter
        JavaAgent.premain("-H" + host + ",-chttp://http://localhost:8080/config.yml", null);
        final URL url = new URL(String.format("http://%s:%d/metrics", host, JavaAgent.address.getPort()));

        // Test metric export
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        assertTrue(con.getResponseCode() == 200);

        JavaAgent.server.stop();
    }
}
