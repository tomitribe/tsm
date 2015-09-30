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

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.tomitribe.util.Files;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.function.Supplier;

public class GitRule implements TestRule {
    private final File dir;
    private final Supplier<String> sshUser;
    private final Supplier<Integer> sshPort;

    public GitRule(final String dir, final Supplier<String> sshUser, final Supplier<Integer> portSupplier) {
        this.sshUser = sshUser;
        this.sshPort = portSupplier;
        this.dir = new File(dir);
        Files.mkdirs(this.dir);
    }

    public String directory() {
        return dir.toURI().toASCIIString();
    }

    public GitRule addFile(final String path, final String content) {
        final File target = new File(dir, path);
        Files.mkdirs(target.getParentFile());
        try (final FileWriter writer = new FileWriter(target)) {
            writer.write(content);
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }

        try {
            final Git git = Git.open(dir);
            git.add().addFilepattern(path).call();
            git.commit().setMessage("adding " + path).setAuthor("test", "test@test.test").call();
        } catch (final GitAPIException | IOException e) {
            throw new IllegalStateException(e);
        }
        return this;
    }

    public GitRule addDeploymentsJson(final String artifact) {
        return addFile(
            artifact + "/deployments.json",
            "{\"environments\":[{" +
            "\"hosts\":[\"localhost:" + sshPort.get() + "\"]," +
            "\"names\":[\"prod\"]," +
            "\"base\":\"/\"," +
            "\"user\":\"" + sshUser.get() + "\"" +
            "}]}");
    }

    @Override
    public Statement apply(final Statement base, final Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                Git.init()
                    .setDirectory(dir)
                    .call();
                try {
                    base.evaluate();
                } finally {
                    Files.remove(dir);
                }
            }
        };
    }
}
