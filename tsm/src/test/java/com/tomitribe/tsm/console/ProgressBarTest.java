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

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Locale;

import static org.junit.Assert.assertEquals;

public class ProgressBarTest {
    private Locale original;

    @Before
    public void setLocale() {
        original = Locale.getDefault();
        Locale.setDefault(Locale.ENGLISH);
    }

    @After
    public void resetLocale() {
        Locale.setDefault(original);
    }

    @Test
    public void progress() {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final PrintStream stream = new PrintStream(out);
        final ProgressBar bar = new ProgressBar(stream, "prefix");
        assertEquals("prefix   0.00% [                                                  ] 0s\r", new String(out.toByteArray()));
        bar.accept(20.);
        assertEquals(
                "prefix   0.00% [                                                  ] 0s\r" +
                "prefix  20.00% [==========                                        ] 0s\r", new String(out.toByteArray()));
        bar.accept(100.);
        assertEquals(
            "prefix   0.00% [                                                  ] 0s\r" +
            "prefix  20.00% [==========                                        ] 0s\r" +
            "prefix 100.00% [==================================================] 0s\n",
            new String(out.toByteArray()));
    }
}
