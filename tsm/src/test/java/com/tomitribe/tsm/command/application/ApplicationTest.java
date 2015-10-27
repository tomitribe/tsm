/*
 * Tomitribe Confidential
 *
 * Copyright Tomitribe Corporation. 2015
 *
 * The source code for this program is not published or otherwise divested
 * of its trade secrets, irrespective of what has been deposited with the
 * U.S. Copyright Office.
 */
package com.tomitribe.tsm.command.application;

import com.tomitribe.tsm.command.junit.GitRule;
import com.tomitribe.tsm.command.junit.SshRule;
import com.tomitribe.tsm.configuration.GitConfiguration;
import com.tomitribe.tsm.configuration.GlobalConfiguration;
import com.tomitribe.tsm.configuration.LocalFileRepository;
import com.tomitribe.tsm.configuration.Nexus;
import com.tomitribe.tsm.configuration.SshKey;
import org.junit.Rule;
import org.junit.Test;
import org.tomitribe.crest.environments.Environment;
import org.tomitribe.crest.environments.SystemEnvironment;
import org.tomitribe.util.Duration;
import org.tomitribe.util.IO;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ApplicationTest {
    private static final Environment ENVIRONMENT = new SystemEnvironment(singletonMap(GlobalConfiguration.class, new GlobalConfiguration(new File("."))));

    @Rule
    public final SshRule ssh = new SshRule("target/ApplicationTest/", cmd -> {
        if (cmd.startsWith("mkdir -p")) {
            asList(cmd.substring("mkdir -p".length()).trim().replace("\"", "").split(" ")).forEach(p -> new File("target/ApplicationTest/", p).mkdirs());
        } else if (cmd.equals("cd \"/art/prod/\" && for i in bin conf lib logs temp webapps work; do mkdir $i; done")) {
            asList("bin", "conf", "webapps").forEach(p -> new File("target/ApplicationTest/art/prod", p).mkdirs());
        } else if (cmd.equals("cd \"/art2/prod/\" && for i in bin conf lib logs temp webapps work; do mkdir $i; done")) {
            asList("bin", "conf", "webapps").forEach(p -> new File("target/ApplicationTest/art2/prod", p).mkdirs());
        } else if (cmd.equals("cd \"/art2/other/\" && for i in bin conf lib logs temp webapps work; do mkdir $i; done")) {
            asList("bin", "conf", "webapps").forEach(p -> new File("target/ApplicationTest/art2/other", p).mkdirs());
        }
    });

    @Rule
    public final GitRule git = new GitRule("target/ApplicationTest-git/", ssh::getUsername, ssh::port);

    @Test
    public void start() throws IOException {
        git.addDeploymentsJson("start");

        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        Application.start(
            "prod",
            new SshKey(ssh.getKeyPath(), ssh.getKeyPassphrase()),
            new File("target/ApplicationTest-start-work/"), -1, -1,
            new GitConfiguration(git.directory(), "ApplicationTest-start", "master", null, ssh.getKeyPath().getAbsolutePath(), ssh.getKeyPassphrase()),
            "start", new PrintStream(out), ENVIRONMENT);

        assertEquals(singletonList("\"/start/prod/bin/startup\""), ssh.commands());
        assertTrue(new String(out.toByteArray()).contains("Starting start on localhost:" + ssh.port() + " for environment prod"));
    }

    @Test
    public void stop() throws IOException {
        git.addDeploymentsJson("stop");

        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        Application.stop(
            "prod",
            new SshKey(ssh.getKeyPath(), ssh.getKeyPassphrase()),
            new File("target/ApplicationTest-stop-work/"), -1, -1,
            new GitConfiguration(git.directory(), "ApplicationTest-stop", "master", null, ssh.getKeyPath().getAbsolutePath(), ssh.getKeyPassphrase()),
            "stop", new PrintStream(out), ENVIRONMENT);

        assertEquals(singletonList("\"/stop/prod/bin/shutdown\" 1200 -force"), ssh.commands());
        assertTrue(new String(out.toByteArray()).contains("Stopping stop on localhost:" + ssh.port() + " for environment prod"));
    }

    @Test
    public void ping() throws IOException {
        git.addDeploymentsJson("ping");

        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        Application.ping(
            "prod",
            new SshKey(ssh.getKeyPath(), ssh.getKeyPassphrase()),
            new File("target/ApplicationTest-ping-work/"), -1, -1,
            new GitConfiguration(git.directory(), "ApplicationTest-ping", "master", null, ssh.getKeyPath().getAbsolutePath(), ssh.getKeyPassphrase()),
            "ping", new PrintStream(out), ENVIRONMENT);

        assertEquals(singletonList("GET http://127.0.0.1:8443 2>&1 | grep -v 'command not found' || curl -v http://127.0.0.1:8443"), ssh.commands());
        assertTrue(new String(out.toByteArray()).contains("Testing ping on localhost:" + ssh.port() + " for environment prod"));
    }

    @Test
    public void installTarGz() throws IOException {
        git.addDeploymentsJson("itg");

        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        Application.installTarGzArtifact(
            "prod",
            new SshKey(ssh.getKeyPath(), ssh.getKeyPassphrase()),
            new File("target/ApplicationTest-tg-work/"),
            new GitConfiguration(git.directory(), "ApplicationTest-tg", "master", null, ssh.getKeyPath().getAbsolutePath(), ssh.getKeyPassphrase()),
            new LocalFileRepository(new File("target/missing")),
            new Nexus("http://faked", null, null) {
                @Override
                public DownloadHandler download(final PrintStream out,
                                                final String groupId,final  String artifactId, final String version,
                                                final String classifier, final String type) {
                    return destination -> {
                        try {
                            IO.writeString(destination, "install.tar.gz");
                        } catch (final IOException e) {
                            fail(e.getMessage());
                        }
                    };
                }
            },
            "itg", "foo:foo:1", new PrintStream(out), ENVIRONMENT);

        assertEquals(asList(
            "mkdir -p \"/work-provisioning/\" \"/foo/foo-1/\"",
            "tar xvf \"/work-provisioning/foo-1.tar.gz\" -C \"/foo/foo-1/\" --strip 1",
            "rm \"/work-provisioning/foo-1.tar.gz\""), ssh.commands());
        assertEquals("install.tar.gz", IO.readString(new File(ssh.getHome(), "work-provisioning/foo-1.tar.gz")));
        assertTrue(new String(out.toByteArray()).contains("foo setup in /foo/foo-1/ for host localhost:" + ssh.port() + "\n"));
    }

    @Test
    public void install() throws IOException {
        git.addFile("art/tribestream/conf/someconf.properties", "e=${tsm.environment}").addDeploymentsJson("art");

        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final ByteArrayOutputStream err = new ByteArrayOutputStream();
        Application.install(
            new Nexus("http://faked", null, null) {
                @Override
                public DownloadHandler download(final PrintStream out,
                                                final String groupId, final String artifactId, final String version,
                                                final String classifier, final String type) {
                    return destination -> {
                        try {
                            IO.writeString(destination, "main => " + groupId + ":" + artifactId + ":" + version + ":" + type);
                        } catch (final IOException e) {
                            fail(e.getMessage());
                        }
                    };
                }
            },
            new Nexus("http://faked", null, null) {
                @Override
                public DownloadHandler download(final PrintStream out,
                                                final String groupId, final String artifactId, final String version,
                                                final String classifier, final String type) {
                    return destination -> {
                        try {
                            IO.writeString(destination, "lib => " + groupId + ":" + artifactId + ":" + version + ":" + type);
                        } catch (final IOException e) {
                            fail(e.getMessage());
                        }
                    };
                }
            },
            new GitConfiguration(git.directory(), "ApplicationTest-install", "master", null, ssh.getKeyPath().getAbsolutePath(), ssh.getKeyPassphrase()),
            new LocalFileRepository(new File("target/missing")),
            new SshKey(ssh.getKeyPath(), ssh.getKeyPassphrase()),
            new File("target/ApplicationTest-install-work/"),
            null, "0.69", "8u60", "prod", "com.foo.bar", "art", "1.0", -1,  -1, new Duration("-1 minutes"), false, false,
            new PrintStream(out), new PrintStream(err), ENVIRONMENT);

        assertEquals(asList(
            "[ -f \"/art/prod/bin/shutdown\" ] && \"/art/prod/bin/shutdown\" 1200 -force",
            "rm -Rf \"/art/prod/\"",
            "mkdir -p \"/art/prod/\"",
            "cd \"/art/prod/\" && for i in bin conf lib logs temp webapps work; do mkdir $i; done",
            "mkdir -p \"/art/prod/conf/\"",
            "chmod ug+rwx \"/art/prod/bin/processes\" \"/art/prod/bin/startup\" \"/art/prod/bin/shutdown\" \"/art/prod/bin/run\" \"/art/prod/bin/restart\""), ssh.commands());
        assertEquals("main => com.foo.bar:art:1.0:war", IO.readString(new File(ssh.getHome(), "art/prod/webapps/art.war")));
        assertEquals("e=prod", IO.readString(new File(ssh.getHome(), "art/prod/conf/someconf.properties"))); // filtering
        assertTrue(IO.slurp(new File(ssh.getHome(), "art/prod/conf/tsm-metadata.json")).contains(
            "  \"host\":\"localhost:" + ssh.port() + "\",\n" +
            "  \"environment\":\"prod\",\n" +
            "  \"application\":{\n" +
            "    \"groupId\":\"com.foo.bar\",\n" +
            "    \"artifactId\":\"art\",\n" +
            "    \"version\":\"1.0\"\n" +
            "  },\n" +
            "  \"server\":{\n" +
            "    \"name\":\"tribestream-0.69\"\n" +
            "  },\n" +
            "  \"java\":{\n" +
            "    \"version\":\"8u60\"\n" +
            "  }\n" +
            "}\n"));
        assertEquals(
            "#! /bin/bash\n" +
            "\n" +
            "proc_script_base=\"`cd $(dirname $0) && cd .. && pwd`\"\n" +
            "source \"$proc_script_base/bin/setenv.sh\"\n" +
            "ps aux | grep \"$proc_script_base\" | grep -v grep\n" +
            "\n", IO.slurp(new File(ssh.getHome(), "art/prod/bin/processes")));
        assertEquals(
            "#! /bin/bash\n" +
            "\n" +
            "proc_script_base=\"`cd $(dirname $0) && cd .. && pwd`\"\n" +
            "source \"$proc_script_base/bin/setenv.sh\"\n" +
            "\"$proc_script_base/bin/shutdown\" && sleep 3 && \"$proc_script_base/bin/startup\"\n" +
            "\n", IO.slurp(new File(ssh.getHome(), "art/prod/bin/restart")));
        assertEquals(
            "#! /bin/bash\n" +
            "\n" +
            "proc_script_base=\"`cd $(dirname $0) && cd .. && pwd`\"\n" +
            "source \"$proc_script_base/bin/setenv.sh\"\n" +
            "[ -f \"$proc_script_base/bin/pre_startup.sh\" ] && \"$proc_script_base/bin/pre_startup.sh\"\n" +
            "\"$CATALINA_HOME/bin/catalina.sh\" \"run\" \"$@\"\n" +
            "\n", IO.slurp(new File(ssh.getHome(), "art/prod/bin/run")));
        assertEquals(
            "#! /bin/sh\n" +
            "\n" +
            "# Generated by TSM, don't edit please\n" +
            "export JAVA_HOME=\"/java/jdk-8u60\"\n" +
            "export CATALINA_HOME=\"/tribestream/tribestream-0.69\"\n" +
            "export CATALINA_BASE=\"/art/prod/\"\n" +
            "export CATALINA_PID=\"/art/prod/work/tribestream.pid\"\n" +
            "# End of TSM edit\n" +
            "\n", IO.slurp(new File(ssh.getHome(), "art/prod/bin/setenv.sh")));
        assertEquals(
            "#! /bin/bash\n" +
            "\n" +
            "proc_script_base=\"`cd $(dirname $0) && cd .. && pwd`\"\n" +
            "source \"$proc_script_base/bin/setenv.sh\"\n" +
            "[ -f \"$proc_script_base/bin/pre_shutdown.sh\" ] && \"$proc_script_base/bin/pre_shutdown.sh\"\n" +
            "\"$CATALINA_HOME/bin/shutdown.sh\" \"$@\"\n" +
            "[ -f \"$proc_script_base/bin/post_shutdown.sh\" ] && \"$proc_script_base/bin/post_shutdown.sh\"\n" +
            "\n", IO.slurp(new File(ssh.getHome(), "art/prod/bin/shutdown")));
        assertEquals(
            "#! /bin/bash\n" +
            "\n" +
            "proc_script_base=\"`cd $(dirname $0) && cd .. && pwd`\"\n" +
            "source \"$proc_script_base/bin/setenv.sh\"\n" +
            "[ -f \"$proc_script_base/bin/pre_startup.sh\" ] && \"$proc_script_base/bin/pre_startup.sh\"\n" +
            "nohup \"$CATALINA_HOME/bin/startup.sh\" \"$@\" > $proc_script_base/logs/nohup.log &\n" +
            "[ -f \"$proc_script_base/bin/post_startup.sh\" ] && \"$proc_script_base/bin/post_startup.sh\"\n" +
            "\n", IO.slurp(new File(ssh.getHome(), "art/prod/bin/startup")));
        assertTrue(new String(out.toByteArray()).contains("art setup in /art/prod/ for host localhost:" + ssh.port() + ", you can now use start command."));
    }

    @Test
    public void installMultipleEnvrts() throws IOException {
        git.addFile("art2/tribestream/conf/someconf.properties", "e=${environment}").addDeploymentsJson("art2", "prod", "other");

        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final ByteArrayOutputStream err = new ByteArrayOutputStream();
        Application.install(
            new Nexus("http://faked", null, null) {
                @Override
                public DownloadHandler download(final PrintStream out,
                                                final String groupId, final String artifactId, final String version,
                                                final String classifier, final String type) {
                    return destination -> {
                        try {
                            IO.writeString(destination, "main => " + groupId + ":" + artifactId + ":" + version + ":" + type);
                        } catch (final IOException e) {
                            fail(e.getMessage());
                        }
                    };
                }
            },
            new Nexus("http://faked", null, null) {
                @Override
                public DownloadHandler download(final PrintStream out,
                                                final String groupId, final String artifactId, final String version,
                                                final String classifier, final String type) {
                    return destination -> {
                        try {
                            IO.writeString(destination, "lib => " + groupId + ":" + artifactId + ":" + version + ":" + type);
                        } catch (final IOException e) {
                            fail(e.getMessage());
                        }
                    };
                }
            },
            new GitConfiguration(git.directory(), "ApplicationTest-install-envs", "master", null, ssh.getKeyPath().getAbsolutePath(), ssh.getKeyPassphrase()),
            new LocalFileRepository(new File("target/missing")),
            new SshKey(ssh.getKeyPath(), ssh.getKeyPassphrase()),
            new File("target/ApplicationTest-install-envs/"),
            null, "0.69", "8u60", "prod,other", "com.foo.bar", "art2", "1.0", -1, -1, new Duration("-1 minutes"), false, false,
            new PrintStream(out), new PrintStream(err), ENVIRONMENT);

        assertEquals("e=prod", IO.readString(new File(ssh.getHome(), "art2/prod/conf/someconf.properties"))); // filtering
        assertEquals("e=other", IO.readString(new File(ssh.getHome(), "art2/other/conf/someconf.properties"))); // filtering
    }
}
