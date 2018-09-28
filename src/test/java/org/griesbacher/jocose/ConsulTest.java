package org.griesbacher.jocose;

import org.junit.Test;

import java.util.ArrayList;

import static junit.framework.TestCase.assertFalse;

public class ConsulTest {
    @Test
    public void ioExceptionTest() throws Exception {
        boolean result = new Consul(
                "123", "foo", new ArrayList<String>(),
                "localhost", 123, "https://localhost:-1",
                new Config.Check()
        ).registerService();
        assertFalse("This registration should fail", result);
    }

    @Test
    public void deregisterMalformedURLExceptionTest() throws Exception {
        boolean result = new Consul(
                "123", "foo", new ArrayList<String>(),
                "localhost", 123, "https://localhost:-1",
                new Config.Check()
        ).deregisterService();
        assertFalse("This deregistration should fail", result);
    }
}
