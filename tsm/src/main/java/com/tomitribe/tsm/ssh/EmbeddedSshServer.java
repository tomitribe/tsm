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

import com.tomitribe.tsm.configuration.GlobalConfiguration;
import lombok.RequiredArgsConstructor;
import org.apache.sshd.SshServer;
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory;
import org.apache.sshd.server.auth.UserAuthNone;
import org.apache.sshd.server.command.ScpCommandFactory;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.server.sftp.SftpSubsystem;
import org.apache.sshd.server.shell.ProcessShellFactory;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;

import static java.util.Collections.singletonList;

@RequiredArgsConstructor
public class EmbeddedSshServer implements Closeable {
    public static final com.tomitribe.tsm.configuration.SshKey SSH_KEY = new com.tomitribe.tsm.configuration.SshKey(null, null);
    private volatile boolean started;
    private final GlobalConfiguration configuration;
    private SshServer ssh;

    public void start() {
        if (started) {
            return;
        }
        synchronized (this) {
            try {
                doStart();
            } catch (final IOException e) {
                throw new IllegalStateException(e);
            }
            started = true;
        }
    }

    public com.tomitribe.tsm.configuration.SshKey getKey() {
        return SSH_KEY;
    }

    public String getUrl() {
        return "localhost:" + ssh.getPort();
    }

    private void doStart() throws IOException {
        final File sshHome = new File("/"); // if you want to handle a subfolder ensure to make relative all paths

        ssh = SshServer.setUpDefaultServer();
        ssh.setPort(0);
        ssh.setKeyPairProvider(new SimpleGeneratorHostKeyProvider());
        ssh.setUserAuthFactories(singletonList(new UserAuthNone.Factory())); // no security there
        ssh.setPublickeyAuthenticator((username, publicKey, session) -> true);
        ssh.setCommandFactory(new ScpCommandFactory(command -> new ProcessShellFactory(new String[]{
                System.getProperty("tsm.local.ssh.shell", "/bin/bash"),  // unlikely it doesn't work
                "-c",
                command
        }).create()));
        ssh.setShellFactory(new ProcessShellFactory());
        ssh.setSubsystemFactories(singletonList(new SftpSubsystem.Factory()));
        ssh.setFileSystemFactory(new VirtualFileSystemFactory(sshHome.getAbsolutePath()));
        ssh.start();
    }

    @Override
    public void close() throws IOException {
        if (!started) {
            return;
        }
        synchronized (this) {
            try {
                ssh.stop();
            } catch (final InterruptedException e) {
                Thread.interrupted();
            }
            ssh = null;
            started = false;
        }
    }
}
