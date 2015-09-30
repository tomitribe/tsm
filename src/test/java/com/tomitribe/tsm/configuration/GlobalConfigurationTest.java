/*
 * Tomitribe Confidential
 *
 * Copyright Tomitribe Corporation. 2015
 *
 * The source code for this program is not published or otherwise divested
 * of its trade secrets, irrespective of what has been deposited with the
 * U.S. Copyright Office.
 */
package com.tomitribe.tsm.configuration;

import com.tomitribe.tsm.crest.interceptor.DefaultParameters;
import org.junit.Test;
import org.tomitribe.crest.Main;
import org.tomitribe.crest.api.Command;
import org.tomitribe.crest.environments.Environment;
import org.tomitribe.crest.environments.SystemEnvironment;

import java.io.File;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class GlobalConfigurationTest {
    @Test
    public void readCustomLocationPath() throws Exception {
        final File faketsmrc = new File("target/GlobalConfigurationTest/readCustomLocationPath.tsmrc");
        faketsmrc.getParentFile().mkdirs();
        try (final FileWriter w = new FileWriter(faketsmrc)) {
            w.write("a: b");
        }

        final Map<Class<?>, Object> services = new HashMap<>();
        services.put(GlobalConfiguration.class, new GlobalConfiguration(faketsmrc));

        Environment.ENVIRONMENT_THREAD_LOCAL.set(new SystemEnvironment(services));
        final Main main = new Main(Simulate.class, DefaultParameters.class);
        try {
            assertEquals("b", main.exec("sim"));
        } finally {
            Environment.ENVIRONMENT_THREAD_LOCAL.remove();
        }
    }

    public static class Simulate {
        @Command
        public static String sim(final GlobalConfiguration configuration) {
            return configuration.read("a");
        }
    }
}
