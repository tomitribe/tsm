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

import org.slf4j.helpers.FormattingTuple;
import org.slf4j.helpers.MarkerIgnoringBase;
import org.slf4j.helpers.MessageFormatter;
import org.tomitribe.crest.environments.Environment;

import java.io.PrintStream;

public class CrestLogger extends MarkerIgnoringBase {
    private final boolean verbose;
    private final PrintStream out;
    private final PrintStream err;

    public CrestLogger(final String s) {
        final Environment environment = Environment.ENVIRONMENT_THREAD_LOCAL.get();

        this.name = s;
        this.verbose = Boolean.parseBoolean(environment.getProperties().getProperty("log.verbose", "false"));
        this.out = environment.getOutput();
        this.err = environment.getError();
    }

    private void doLog(final PrintStream out, final FormattingTuple format) {
        out.println(format.getMessage());
        if (format.getThrowable() != null) {
            format.getThrowable().printStackTrace(out);
        }
    }

    @Override
    public boolean isTraceEnabled() {
        return verbose;
    }

    @Override
    public void trace(final String msg) {
        if (isTraceEnabled()) {
            out.println(msg);
        }
    }

    @Override
    public void trace(final String format, final Object arg) {
        if (isTraceEnabled()) {
            doLog(out, MessageFormatter.format(format, arg));
        }
    }

    @Override
    public void trace(final String s, final Object o, final Object o1) {
        if (isTraceEnabled()) {
            doLog(out, MessageFormatter.format(s, o, o1));
        }
    }

    @Override
    public void trace(final String s, final Object... objects) {
        if (isTraceEnabled()) {
            doLog(out, MessageFormatter.format(s, objects));
        }
    }

    @Override
    public void trace(final String s, final Throwable throwable) {
        if (isTraceEnabled()) {
            doLog(out, MessageFormatter.format(s, throwable));
        }
    }

    @Override
    public boolean isDebugEnabled() {
        return verbose;
    }

    @Override
    public void debug(final String s) {
        if (isDebugEnabled()) {
            out.println(s);
        }
    }

    @Override
    public void debug(final String s, final Object o) {
        if (isDebugEnabled()) {
            doLog(out, MessageFormatter.format(s, o));
        }
    }

    @Override
    public void debug(final String s, final Object o, final Object o1) {
        if (isDebugEnabled()) {
            doLog(out, MessageFormatter.format(s, o, o1));
        }
    }

    @Override
    public void debug(final String s, final Object... objects) {
        if (isDebugEnabled()) {
            doLog(out, MessageFormatter.format(s, objects));
        }
    }

    @Override
    public void debug(final String s, final Throwable throwable) {
        if (isDebugEnabled()) {
            doLog(out, MessageFormatter.format(s, throwable));
        }
    }

    @Override
    public boolean isInfoEnabled() {
        return true;
    }

    @Override
    public void info(final String s) {
        if (isInfoEnabled()) {
            out.println(s);
        }
    }

    @Override
    public void info(final String s, final Object o) {
        if (isInfoEnabled()) {
            doLog(out, MessageFormatter.format(s, o));
        }
    }

    @Override
    public void info(final String s, final Object o, final Object o1) {
        if (isInfoEnabled()) {
            doLog(out, MessageFormatter.format(s, o, o1));
        }
    }

    @Override
    public void info(final String s, final Object... objects) {
        if (isInfoEnabled()) {
            doLog(out, MessageFormatter.format(s, objects));
        }
    }

    @Override
    public void info(final String s, final Throwable throwable) {
        if (isInfoEnabled()) {
            doLog(out, MessageFormatter.format(s, throwable));
        }
    }

    @Override
    public boolean isWarnEnabled() {
        return true;
    }

    @Override
    public void warn(final String s) {
        if (isWarnEnabled()) {
            err.println(s);
        }
    }

    @Override
    public void warn(final String s, final Object o) {
        if (isWarnEnabled()) {
            doLog(err, MessageFormatter.format(s, o));
        }
    }

    @Override
    public void warn(final String s, final Object... objects) {
        if (isWarnEnabled()) {
            doLog(err, MessageFormatter.format(s, objects));
        }
    }

    @Override
    public void warn(final String s, final Object o, final Object o1) {
        if (isWarnEnabled()) {
            doLog(err, MessageFormatter.format(s, o, o1));
        }
    }

    @Override
    public void warn(final String s, final Throwable throwable) {
        if (isWarnEnabled()) {
            doLog(err, MessageFormatter.format(s, throwable));
        }
    }

    @Override
    public boolean isErrorEnabled() {
        return true;
    }

    @Override
    public void error(final String s) {
        if (isErrorEnabled()) {
            err.println(s);
        }
    }

    @Override
    public void error(final String s, final Object o) {
        if (isErrorEnabled()) {
            doLog(err, MessageFormatter.format(s, o));
        }
    }

    @Override
    public void error(final String s, final Object o, final Object o1) {
        if (isErrorEnabled()) {
            doLog(err, MessageFormatter.format(s, o, o1));
        }
    }

    @Override
    public void error(final String s, final Object... objects) {
        if (isErrorEnabled()) {
            doLog(err, MessageFormatter.format(s, objects));
        }
    }

    @Override
    public void error(final String s, final Throwable throwable) {
        if (isErrorEnabled()) {
            doLog(err, MessageFormatter.format(s, throwable));
        }
    }
}
