package org.griesbacher.jocose;

import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;

import java.net.HttpURLConnection;
import java.net.URL;

import static org.griesbacher.jocose.ConfigHandlerTest.disableStdErr;
import static org.griesbacher.jocose.ConfigHandlerTest.enableStdErr;
import static org.junit.Assert.assertTrue;

public class SimpleJavaAgentTest {
    @Rule
    public final ExpectedSystemExit exit = ExpectedSystemExit.none();

    @Test
    public void emptyArgTest() throws Exception {
        exit.expectSystemExitWithStatus(1);
        disableStdErr();
        JavaAgent.premain("", null);
        enableStdErr();
    }

    @Test
    public void wrongArgTest() throws Exception {
        exit.expectSystemExitWithStatus(1);
        disableStdErr();
        JavaAgent.premain("asdf,foo", null);
        enableStdErr();
    }


    @Test
    public void plainExporterTest() throws Exception {
        final String config = getClass().getClassLoader().getResource("none_consul_example_config.yml").getFile();
        final String host = "127.0.0.1";
        final int port = 9876;
        //Start exporter
        JavaAgent.premain("-H" + host + ",-p" + port + ",-cfile://" + config+",-sFOO", null);
        URL url = new URL(String.format("http://%s:%d/metrics", host, port));

        // Test metric export
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        assertTrue(con.getResponseCode() == 200);

        //Check args
        assertTrue(JavaAgent.ymlConfig.serviceName.equals("FOO"));

        //Clean up
        JavaAgent.server.stop();
    }
}
