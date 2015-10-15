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

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.KeyPair;
import org.apache.sshd.SshServer;
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory;
import org.apache.sshd.server.auth.UserAuthPublicKey;
import org.apache.sshd.server.command.ScpCommandFactory;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.server.sftp.SftpSubsystem;
import org.junit.Test;
import org.tomitribe.util.Files;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import static java.util.Collections.singletonList;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SshTest {
    @Test
    public void scp() throws IOException, InterruptedException, JSchException {
        final File work = Files.mkdirs(new File("target/SshTest/"));

        final File key = new File("target/SshTest/key");
        final KeyPair kpair = KeyPair.genKeyPair(new JSch(), KeyPair.DSA);
        kpair.writePrivateKey(key.getAbsolutePath());
        kpair.writePublicKey(key.getAbsolutePath() + ".pub", "");
        kpair.dispose();

        final File sshHome = Files.mkdirs(new File(work, "scp/"));
        final SshServer ssh = SshServer.setUpDefaultServer();
        ssh.setPort(0);
        ssh.setKeyPairProvider(new SimpleGeneratorHostKeyProvider());
        ssh.setUserAuthFactories(singletonList(new UserAuthPublicKey.Factory()));
        ssh.setCommandFactory(new ScpCommandFactory());
        ssh.setSubsystemFactories(singletonList(new SftpSubsystem.Factory()));
        ssh.setFileSystemFactory(new VirtualFileSystemFactory(sshHome.getAbsolutePath()));
        ssh.setPublickeyAuthenticator((username, publicKey, session) -> "test".equals(username));
        ssh.start();

        final File file = new File(work, "toupload.txt");
        try (final FileWriter writer = new FileWriter(file)) {
            writer.write("content");
        }

        try {
            final File actualTarget = new File(sshHome, file.getName());
            Files.remove(actualTarget);
            assertFalse(actualTarget.exists());
            new Ssh(new SshKey(key, null), "test@localhost:" + ssh.getPort()).scp(file, "/" + file.getName(), null);
            assertTrue(actualTarget.isFile());
        } finally {
            ssh.stop();
        }
    }
}
