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

import static org.junit.Assert.assertTrue;

public class MultipleProgressUpdaterTest {
    @Test
    public void run() {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final PrintStream stream = new PrintStream(out);
        final MultipleProgressUpdater updater = new MultipleProgressUpdater(stream);
        updater.on("first").accept(43.4567);
        assertTrue(new String(out.toByteArray()).trim().endsWith("first=43.46%"));
        updater.on("second").accept(20.);
        assertTrue(new String(out.toByteArray()).trim().endsWith("first=43.46% second=20.00%"));
        updater.close();
        assertTrue(new String(out.toByteArray()).trim().contains("first: "));
        assertTrue(new String(out.toByteArray()).trim().contains("second: "));
    }
}
