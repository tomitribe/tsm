/*
 * Tomitribe Confidential
 *
 * Copyright Tomitribe Corporation. 2015
 *
 * The source code for this program is not published or otherwise divested
 * of its trade secrets, irrespective of what has been deposited with the
 * U.S. Copyright Office.
 */
package com.tomitribe.tsm.io;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.function.Consumer;

import static java.util.Optional.ofNullable;

public final class EnhancedIO {
    private EnhancedIO() {
        // no-op
    }

    public static void safeDelete(final File dir) {
        if (!dir.exists()) {
            return;
        }

        if (dir.isDirectory()) {
            for (final File child : ofNullable(dir.listFiles()).orElseGet(() -> new File[0])) {
                safeDelete(child);
            }
        }

        if (!dir.delete())  {
            dir.deleteOnExit();
        }
    }

    public static void copy(final String from, final File to, final Consumer<Double> progressPerCentConsumer) {
        try (final ByteArrayInputStream fis = new ByteArrayInputStream(from.getBytes("UTF-8"));
             final FileOutputStream fos= new FileOutputStream(to)) {
            copy(fis, fos, from.length(), progressPerCentConsumer);
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static void copy(final File from, final File to, final Consumer<Double> progressPerCentConsumer) {
        try (final FileInputStream fis = new FileInputStream(from);
             final FileOutputStream fos= new FileOutputStream(to)) {
            copy(fis, fos, from.length(), progressPerCentConsumer);
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static void copy(final InputStream fis, final OutputStream fos,
                            final long fullLength, final Consumer<Double> progressPerCentConsumer) throws IOException {
        final byte[] buffer = new byte[Math.min(Math.max(1024, (int) (fullLength / 40)), 10 * 1024 * 1024)];
        int cumulatedLength = 0;
        int length;
        final Thread thread = Thread.currentThread();
        while ((length = fis.read(buffer)) != -1 && !thread.isInterrupted()) {
            fos.write(buffer, 0, length);

            if (fullLength > 0) {
                cumulatedLength += length;
                if (progressPerCentConsumer != null) {
                    progressPerCentConsumer.accept(cumulatedLength * 100. / fullLength);
                }
            }
        }
    }
}
