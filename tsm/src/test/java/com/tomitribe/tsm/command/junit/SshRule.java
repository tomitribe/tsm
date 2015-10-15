/*
 * Tomitribe Confidential
 *
 * Copyright Tomitribe Corporation. 2015
 *
 * The source code for this program is not published or otherwise divested
 * of its trade secrets, irrespective of what has been deposited with the
 * U.S. Copyright Office.
 */
package com.tomitribe.tsm.command.junit;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import lombok.Getter;
import org.apache.sshd.SshServer;
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory;
import org.apache.sshd.server.Command;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.auth.UserAuthPublicKey;
import org.apache.sshd.server.command.ScpCommandFactory;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.logging.Logger;

import static java.util.Collections.singletonList;

public class SshRule implements TestRule {
    private static final Logger LOGGER = Logger.getLogger(SshRule.class.getName());

    @Getter
    private final String home;

    @Getter
    private final File keyPath;

    @Getter
    private final String keyPassphrase = "pwd";

    private final AtomicInteger port = new AtomicInteger();
    private final AtomicReference<Collection<String>> commands = new AtomicReference<>();
    private final Consumer<String> cmdConsumer;

    public SshRule(final String home, final Consumer<String> cmdConsumer) {
        this.home = home;
        this.cmdConsumer = cmdConsumer;
        this.keyPath = new File(home + "/ssh.key");

        try {
            final  com.jcraft.jsch.KeyPair keyPair = com.jcraft.jsch.KeyPair.genKeyPair(new JSch(), com.jcraft.jsch.KeyPair.RSA);
            org.tomitribe.util.Files.mkdirs(keyPath.getParentFile());
            try (final OutputStream os = new FileOutputStream(keyPath)){
                keyPair.writePrivateKey(os);
            }
        } catch (final IOException | JSchException e) {
            throw new IllegalStateException(e);
        }
    }

    public String getUsername() {
        return "test";
    }

    public int port() {
        return port.get();
    }

    public Collection<String> commands() {
        return commands.get();
    }

    @Override
    public Statement apply(final Statement base, final Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                commands.set(new ArrayList<>());
                final SshServer ssh = SshServer.setUpDefaultServer();
                ssh.setFileSystemFactory(new VirtualFileSystemFactory(home));
                ssh.setPort(0);
                ssh.setKeyPairProvider(new SimpleGeneratorHostKeyProvider());
                ssh.setUserAuthFactories(singletonList(new UserAuthPublicKey.Factory()));
                ssh.setPublickeyAuthenticator((username, key, session) -> "test".equals(username) && "RSA".equals(key.getAlgorithm()));
                ssh.setCommandFactory(new ScpCommandFactory(command -> new Command() {
                    private ExitCallback callback;
                    private OutputStream os;

                    @Override
                    public void setInputStream(final InputStream in) {
                        // no-op
                    }

                    @Override
                    public void setOutputStream(final OutputStream out) {
                        os = out;
                    }

                    @Override
                    public void setErrorStream(final OutputStream err) {
                        // no-op
                    }

                    @Override
                    public void setExitCallback(final ExitCallback callback) {
                        this.callback = callback;
                    }

                    @Override
                    public void start(final Environment env) throws IOException {
                        new Thread(() -> {
                            cmdConsumer.accept(command);
                            commands.get().add(command);
                            callback.onExit(0);
                        }).start();
                    }

                    @Override
                    public void destroy() {
                        // no-op
                    }
                }));

                ssh.start();
                port.set(ssh.getPort());
                LOGGER.info("Started SSH on " + port());
                try {
                    base.evaluate();
                } finally {
                    ssh.stop();
                    commands.set(null);
                    LOGGER.info("Stopped SSH");
                }
            }
        };
    }
}
