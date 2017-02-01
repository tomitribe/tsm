/*
 * Tomitribe Confidential
 *
 * Copyright Tomitribe Corporation. 2015
 *
 * The source code for this program is not published or otherwise divested
 * of its trade secrets, irrespective of what has been deposited with the
 * U.S. Copyright Office.
 */
package com.tomitribe.tsm.ssh;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import org.tomitribe.crest.environments.Environment;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static java.util.Optional.of;
import static java.util.Optional.ofNullable;

public final class Ssh implements AutoCloseable {
    private static final int SSH_TIMEOUT = Integer.getInteger("tsm.ssh.timeout", 120000);
    private static final boolean DEBUG = Boolean.getBoolean("tsm.ssh.debug") || Boolean.getBoolean("tsm.debug");

    private final Session session;

    public Ssh(final SshKey sshKey, final String connection) {
        final JSch jsch = new JSch();
        if (sshKey != null && sshKey.getPath() != null) {
            if (!sshKey.getPath().isFile()) {
                throw new IllegalStateException("No key file provided, can't scp " + sshKey.getPath().getName() + ".");
            }
            try {
                jsch.addIdentity(sshKey.getPath().getAbsolutePath(), sshKey.getPassword());
            } catch (final JSchException e) {
                throw new IllegalStateException(e);
            }
        }

        final int at = connection.indexOf('@');
        final int portSep = connection.indexOf(':');
        final String user = connection.substring(0, at);
        final String host = connection.substring(at + 1, portSep < 0 ? connection.length() : portSep);
        final int port = portSep > 0 ? Integer.parseInt(connection.substring(portSep + 1, connection.length())) : 22;

        try {
            session = jsch.getSession(user, host, port);
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect();
        } catch (final JSchException e) {
            throw new IllegalStateException(e);
        }
    }

    public Ssh exec(final String command) {
        if (DEBUG) {
            System.out.println("[SSH] Executing '" + command + "'");
        }
        Channel channel = null;
        try {
            channel = redirectStreams(openExecChannel(command));
            channel.connect(SSH_TIMEOUT);

            try {
                final InputStream inputStream = channel.getInputStream();
                final ByteArrayOutputStream out = new ByteArrayOutputStream();
                final byte[] buffer = new byte[1024];
                int length;
                long retryUntil = -1;
                while (channel.isConnected() && !channel.isClosed()) {
                    // ensure to check available cause read() is blocking
                    final int available = inputStream.available();
                    if (available > 0 && (length = inputStream.read(buffer, 0, Math.min(buffer.length, available))) != -1) {
                        out.write(buffer, 0, length);
                    } else {
                        if (channel.getExitStatus() != -1) { // process is over
                            if (retryUntil < 0) {
                                retryUntil = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(3) /*config?*/;
                            } else if (System.currentTimeMillis() - retryUntil >= 0) {
                                break;
                            }
                            if (DEBUG) {
                                System.out.println("[SSH][Retry] until " + new Date(retryUntil));
                            }
                        }
                        try {
                            Thread.sleep(250);
                        } catch (final InterruptedException e) {
                            Thread.interrupted();
                            break;
                        }
                    }
                }
                if (DEBUG) {
                    System.out.println("[SSH][Exit Status] " + channel.getExitStatus());
                }

                of(out.toByteArray()).map(String::new).filter(s -> !s.isEmpty())
                        .ifPresent(Environment.ENVIRONMENT_THREAD_LOCAL.get().getOutput()::println);
            } catch (final IOException e) {
                // no-op
            }
            return this;
        } catch (final JSchException je) {
            throw new IllegalStateException(je);
        } finally {
            ofNullable(channel).ifPresent(Channel::disconnect);
        }
    }

    public Ssh scp(final File file, final String target, final Consumer<Double> progressTracker) {
        final String cmd = "scp -t " + target;
        ChannelExec channel = null;
        try {
            channel = openExecChannel(cmd);

            final OutputStream out = channel.getOutputStream();
            final InputStream in = channel.getInputStream();
            channel.connect(SSH_TIMEOUT);

            waitForAck(in);

            final long filesize = file.length();
            final String command = "C0644 " + filesize + " " + file.getName() + "\n";
            out.write(command.getBytes());
            out.flush();

            waitForAck(in);

            final byte[] buf = new byte[1024];
            long totalLength = 0;

            // these are not yet wired to the CLI cause experimental config, can be set in $JAVA_OPTS
            final boolean autoThrottling = !Boolean.getBoolean("tsm.ssh.throttling.disabled") && filesize > (1024 * 1024);
            final Consumer<Double> throttler = autoThrottling ? pc -> new Consumer<Double>() {
                private long last = System.currentTimeMillis();
                private long period = Long.getLong("tsm.ssh.throttling.period", 1000);
                private long pause = Long.getLong("tsm.ssh.throttling.pause", 150);

                @Override
                public void accept(final Double val) {
                    final long now = System.currentTimeMillis();
                    if (now - last > period) {
                        try {
                            Thread.sleep(pause);
                        } catch (final InterruptedException e) {
                            Thread.interrupted();
                            throw new IllegalStateException(e);
                        }
                        last = now;
                    }
                }
            } : null;
            final Consumer<Double> decoratedProgressTracker = autoThrottling ?
                    (progressTracker == null ? throttler : (pc -> {
                        progressTracker.accept(pc);
                        throttler.accept(pc);
                    })) : progressTracker;
            try (final InputStream fis = new FileInputStream(file)) {
                while (true) {
                    int len = fis.read(buf, 0, buf.length);
                    if (len < 0) {
                        break;
                    }

                    out.write(buf, 0, len);
                    totalLength += len;

                    if (decoratedProgressTracker != null) {
                        decoratedProgressTracker.accept(totalLength * 100. / filesize);
                    }
                }
            }
            out.flush();
            sendAck(out);
            waitForAck(in);
            return this;
        } catch (final JSchException | IOException je) {
            throw new IllegalStateException(je);
        } finally {
            ofNullable(channel).ifPresent(Channel::disconnect);
        }
    }

    private ChannelExec openExecChannel(final String command) throws JSchException {
        final ChannelExec channelExec = ChannelExec.class.cast(session.openChannel("exec"));
        if (command.startsWith("sudo ")) {
            channelExec.setPty(true);
        }
        channelExec.setCommand(command);
        return channelExec;
    }

    private ChannelExec redirectStreams(final ChannelExec channelExec) {
        final Environment environment = Environment.ENVIRONMENT_THREAD_LOCAL.get();
        channelExec.setOutputStream(environment.getOutput(), true);
        channelExec.setErrStream(environment.getError(), true);
        // channel.setInputStream(environment.getInput(), true); // would leak threads and prevent proper shutdown
        return channelExec;
    }

    private static void sendAck(final OutputStream out) throws IOException {
        out.write(new byte[]{0});
        out.flush();
    }

    private static void waitForAck(final InputStream in) throws IOException {
        final int read = in.read();
        switch (read) {
            case -1:
                throw new IllegalStateException("Server didnt respond.");
            case 0:
                return;
            default:
                final StringBuilder sb = new StringBuilder();

                int c = in.read();
                while (c > 0 && sb.length() < 8192 /*limit*/) {
                    sb.append((char) c);
                    c = in.read();
                    if (c == 0) {
                        return;
                    }
                }
                throw new IllegalStateException("SCP error: " + sb.toString());
        }
    }

    @Override
    public void close() {
        session.disconnect();
    }
}
