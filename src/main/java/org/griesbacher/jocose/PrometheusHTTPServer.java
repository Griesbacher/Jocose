package org.griesbacher.jocose;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.exporter.HTTPServer;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;

public class PrometheusHTTPServer extends HTTPServer {
    public PrometheusHTTPServer(InetSocketAddress addr, CollectorRegistry registry, boolean daemon) throws IOException {
        super(addr, registry, daemon);
    }

    public void addUUIDContext(final String uuid) {
        this.server.createContext("/" + uuid, new HttpHandler() {
            public void handle(HttpExchange httpExchange) throws IOException {
                byte[] response = uuid.getBytes();
                httpExchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, response.length);
                httpExchange.getResponseBody().write(response);
                httpExchange.close();
            }
        });
    }
}
