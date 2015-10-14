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

import java.io.PrintStream;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.IntStream;

public class ProgressBar implements Consumer<Double> {
    private final double factor;
    private final PrintStream logger;
    private final long start;

    private int current;

    public ProgressBar(final PrintStream logger, final String text) {
        this(logger, text, 50);
    }

    public ProgressBar(final PrintStream logger, final String text, final int width) {
        this.factor = 100. / width;
        this.logger = logger;
        logger.print(text + " [");
        start = System.currentTimeMillis();
    }

    @Override
    public void accept(final Double perCent) {
        int newCurrent = (int) (perCent / factor);
        if (newCurrent > current) {
            IntStream.range(0, newCurrent - current).forEach(i -> logger.print('='));
            current = newCurrent;
        }
        if (perCent == 100) {
            logger.println("] " + TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - start) + "s");
        }
    }
}
