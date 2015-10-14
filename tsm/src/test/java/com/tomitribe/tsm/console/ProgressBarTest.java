/*
 * Tomitribe Confidential
 *
 * Copyright Tomitribe Corporation. 2015
 *
 * The source code for this program is not published or otherwise divested
 * of its trade secrets, irrespective of what has been deposited with the
 * U.S. Copyright Office.
 */
package com.tomitribe.tsm.console;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.stream.IntStream;

import static java.lang.System.lineSeparator;
import static java.util.stream.Collectors.joining;
import static org.junit.Assert.assertEquals;

public class ProgressBarTest {
    @Test
    public void progress() {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final PrintStream stream = new PrintStream(out);
        final ProgressBar bar = new ProgressBar(stream, "prefix");
        assertEquals("prefix [", new String(out.toByteArray()));
        bar.accept(20.);
        assertEquals("prefix [" + IntStream.range(0, 10).mapToObj(i -> "=").collect(joining()), new String(out.toByteArray()));
        bar.accept(100.);
        assertEquals(
            "prefix [" + IntStream.range(0, 50).mapToObj(i -> "=").collect(joining()) + "] 0s" + lineSeparator(),
            new String(out.toByteArray()));
    }
}
