/*
 * Tomitribe Confidential
 *
 * Copyright Tomitribe Corporation. 2015
 *
 * The source code for this program is not published or otherwise divested
 * of its trade secrets, irrespective of what has been deposited with the
 * U.S. Copyright Office.
 */
package com.tomitribe.tsm.command.node;

import com.tomitribe.tsm.command.junit.GitRule;
import com.tomitribe.tsm.command.junit.SshRule;
import com.tomitribe.tsm.configuration.GitConfiguration;
import com.tomitribe.tsm.configuration.GlobalConfiguration;
import com.tomitribe.tsm.configuration.LocalFileRepository;
import com.tomitribe.tsm.configuration.SshKey;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.tomitribe.crest.environments.Environment;
import org.tomitribe.crest.environments.SystemEnvironment;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonMap;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

// for now this test does a real download of node, should it be an issue we should mock exactly like in ApplicationTest
// but since it is a public binary it should be fine
public class NodeTest {
    private static final Environment ENVIRONMENT = new SystemEnvironment(singletonMap(GlobalConfiguration.class, new GlobalConfiguration(new File("."))));

    @Rule
    public final SshRule ssh = new SshRule("target/NodeTest/", cmd -> {
        if (cmd.startsWith("mkdir -p")) {
            asList(cmd.substring("mkdir -p".length()).trim().replace("\"", "").split(" ")).forEach(p -> new File("target/NodeTest/", p).mkdirs());
        }
    });

    @Rule
    public final GitRule git = new GitRule("target/NodeTest-git/", ssh::getUsername, ssh::port);

    @Before
    public void init() {
        Environment.ENVIRONMENT_THREAD_LOCAL.set(ENVIRONMENT);
    }

    @After
    public void reset() {
        Environment.ENVIRONMENT_THREAD_LOCAL.remove();
    }

    @Test
    public void install() throws IOException {
        git.addDeploymentsJson("node_install");

        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        Node.install(
                new File("target/NodeTest-install-work/"),
                "prod",
                new SshKey(ssh.getKeyPath(), ssh.getKeyPassphrase()),
                "https://nodejs.org/dist/", "v7.2.1", "linux-x64", null,
                new LocalFileRepository(new File("target/missing")),
                new GitConfiguration(git.directory(), "NodeTest-tg", "master", null, ssh.getKeyPath().getAbsolutePath(), ssh.getKeyPassphrase()),
                "node_install", new PrintStream(out), ENVIRONMENT.findService(GlobalConfiguration.class));

        assertEquals(asList(
                "mkdir -p \"/work-provisioning/\" \"/node/node-v7.2.1\"",
                "tar xvf \"/work-provisioning/node_install-v7.2.1.tar.gz\" -C \"/node/node-v7.2.1\" --strip 1",
                "rm \"/work-provisioning/node_install-v7.2.1.tar.gz\""), ssh.commands());
        final File file = new File(ssh.getHome(), "work-provisioning/node_install-v7.2.1.tar.gz");
        assertTrue(file.exists());
        assertEquals(15377699, file.length(), 15377699 * 0.25);
        assertTrue(new String(out.toByteArray()).contains("node-v7.2.1-linux-x64.tar.gz setup in /node/node-v7.2.1 for host localhost:" + ssh.port()));
    }
}
