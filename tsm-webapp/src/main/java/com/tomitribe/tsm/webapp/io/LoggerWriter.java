/*
 * Tomitribe Confidential
 *
 * Copyright Tomitribe Corporation. 2015
 *
 * The source code for this program is not published or otherwise divested
 * of its trade secrets, irrespective of what has been deposited with the
 * U.S. Copyright Office.
 */
package com.tomitribe.tsm.webapp.io;

import lombok.experimental.Delegate;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.logging.Logger;

public class LoggerWriter extends Writer {
    private final Logger logger;

    @Delegate
    private final Writer delegate;

    public LoggerWriter(final Logger logger) {
        this.logger = logger;
        this.delegate = new PrintWriter(new OutputStream() {
            private String buffer = "";

            @Override
            public void write(final int b) {
                buffer = buffer + new String(new byte[] { (byte) (b & 0xff) });
                if (buffer.endsWith("\n")) {
                    buffer = buffer.substring(0, buffer.length() - 1);
                    flush();
                }
            }

            public void flush() {
                logger.info(buffer);
                buffer = "";
            }
        });
    }
}
