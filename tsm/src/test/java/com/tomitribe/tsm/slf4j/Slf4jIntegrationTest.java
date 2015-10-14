/*
 * Tomitribe Confidential
 *
 * Copyright Tomitribe Corporation. 2015
 *
 * The source code for this program is not published or otherwise divested
 * of its trade secrets, irrespective of what has been deposited with the
 * U.S. Copyright Office.
 */
package com.tomitribe.tsm.slf4j;

import org.junit.Test;
import org.slf4j.LoggerFactory;
import org.tomitribe.crest.environments.Environment;
import org.tomitribe.crest.environments.SystemEnvironment;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static java.lang.System.lineSeparator;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class Slf4jIntegrationTest {
    @Test
    public void log() {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final ByteArrayOutputStream err = new ByteArrayOutputStream();
        final PrintStream stdout = new PrintStream(out);
        final PrintStream stderr = new PrintStream(err);
        final Environment environment = new SystemEnvironment() {
            @Override
            public PrintStream getOutput() {
                return stdout;
            }

            @Override
            public PrintStream getError() {
                return stderr;
            }
        };
        Environment.ENVIRONMENT_THREAD_LOCAL.set(environment);
        try {
            LoggerFactory.getLogger(Slf4jIntegrationTest.class).info("some info");
            assertEquals("some info", new String(out.toByteArray()).trim());

            LoggerFactory.getLogger(Slf4jIntegrationTest.class).error("some error", new IllegalArgumentException("oops"));
            assertTrue(new String(err.toByteArray()).replace(lineSeparator(), "").startsWith(
                "some error" +
                "java.lang.IllegalArgumentException: oops" +
                "\tat com.tomitribe.tsm.slf4j.Slf4jIntegrationTest.log(Slf4jIntegrationTest.java:"));
        } finally {
            Environment.ENVIRONMENT_THREAD_LOCAL.remove();
        }
    }
}
