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
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static java.lang.Thread.sleep;
import static java.util.Comparator.comparing;

public class MultipleProgressUpdater implements AutoCloseable {
    private final PrintStream logger;
    private final long start;
    private final List<SingleConsumer> consumers = new LinkedList<>();

    private long lastUpdate;

    public MultipleProgressUpdater(final PrintStream logger) {
        this.logger = logger;
        this.start = System.currentTimeMillis();
    }

    public synchronized Consumer<Double> on(final String name) {
        final SingleConsumer consumer = new SingleConsumer(this, name);
        consumers.add(consumer);
        Collections.sort(consumers, comparing(s -> s.name));
        return consumer;
    }

    private synchronized void doUpdate() {
        final long now = System.currentTimeMillis();
        if (lastUpdate > 0 && now - lastUpdate < 100) { // just dont take the whole CPU for that
            try {
                sleep(100);
            } catch (final InterruptedException e) {
                // no-op: keep interrupted flag
            }
        }
        lastUpdate = now;
        consumers.stream().filter(c -> c.current < 100).forEach(c -> logger.print(String.format(Locale.ENGLISH, "%s=%3.2f%% ", c.name, c.current)));
        logger.print('\r');
    }

    @Override
    public void close() {
        consumers.forEach(c -> logger.println(c.name + ": " + TimeUnit.MILLISECONDS.toSeconds(c.end - start) + "s"));
    }

    private static class SingleConsumer implements Consumer<Double> {
        private final MultipleProgressUpdater parent;
        private final String name;

        private long end;
        private volatile double current;

        private SingleConsumer(final MultipleProgressUpdater parent, final String name) {
            this.parent = parent;
            this.name = name;
        }

        @Override
        public void accept(final Double perCent) {
            current = perCent;
            parent.doUpdate();
            if (perCent == 100) {
                end = System.currentTimeMillis();
            }
        }
    }
}
