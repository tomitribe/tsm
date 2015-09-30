/*
 * Tomitribe Confidential
 *
 * Copyright Tomitribe Corporation. 2015
 *
 * The source code for this program is not published or otherwise divested
 * of its trade secrets, irrespective of what has been deposited with the
 * U.S. Copyright Office.
 */
package com.tomitribe.tsm.crest;

import org.tomitribe.crest.environments.Environment;
import org.tomitribe.crest.environments.SystemEnvironment;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

public interface CrestOutputCapture {
    static String capture(final Runnable task) {
        final Environment old = Environment.ENVIRONMENT_THREAD_LOCAL.get();
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        final PrintStream memOut = new PrintStream(outputStream);
        Environment.ENVIRONMENT_THREAD_LOCAL.set(new SystemEnvironment() {
            @Override
            public PrintStream getOutput() {
                return memOut;
            }
        });
        try {
            task.run();
            return new String(outputStream.toByteArray());
        } finally {
            Environment.ENVIRONMENT_THREAD_LOCAL.set(old);
        }
    }
}
