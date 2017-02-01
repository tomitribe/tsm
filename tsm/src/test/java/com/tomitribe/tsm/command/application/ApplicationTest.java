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
import com.tomitribe.tsm.crest.interceptor.Notifier;
import com.tomitribe.tsm.listener.Listeners;
import com.tomitribe.tsm.listener.MemListener;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.tomitribe.crest.api.interceptor.CrestContext;
import org.tomitribe.crest.api.interceptor.ParameterMetadata;
import org.tomitribe.crest.environments.Environment;
import org.tomitribe.crest.environments.SystemEnvironment;
import org.tomitribe.util.Duration;
import org.tomitribe.util.IO;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
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
        } else if (cmd.equals("cd \"/art/prod/\" && for i in bin conf lib logs temp webapps work; do mkdir -p $i; done")) {
            asList("bin", "conf", "webapps").forEach(p -> new File("target/ApplicationTest/art/prod", p).mkdirs());
        } else if (cmd.equals("cd \"/art2/prod/\" && for i in bin conf lib logs temp webapps work; do mkdir -p $i; done")) {
            asList("bin", "conf", "webapps").forEach(p -> new File("target/ApplicationTest/art2/prod", p).mkdirs());
        } else if (cmd.equals("cd \"/art2/other/\" && for i in bin conf lib logs temp webapps work; do mkdir -p $i; done")) {
            asList("bin", "conf", "webapps").forEach(p -> new File("target/ApplicationTest/art2/other", p).mkdirs());
        } else if (cmd.equals("cd \"/configonly/prod/\" && for i in bin conf lib logs temp webapps work; do mkdir -p $i; done")) {
            asList("bin", "conf", "webapps").forEach(p -> new File("target/ApplicationTest/configonly/prod", p).mkdirs());
        } else if (cmd.equals("cd \"/configonly_apps/prod/\" && for i in apps bin conf lib logs temp webapps work; do mkdir -p $i; done")) {
            asList("apps", "bin", "conf", "webapps").forEach(p -> new File("target/ApplicationTest/configonly_apps/prod", p).mkdirs());
        } else if (cmd.equals("cd \"/configOnlyOneWebappFiltering/prod/\" && for i in bin conf lib logs temp webapps work; do mkdir -p $i; done")) {
            asList("apps", "bin", "conf", "webapps").forEach(p -> new File("target/ApplicationTest/configOnlyOneWebappFiltering/prod", p).mkdirs());
        }
    });

    @Rule
    public final GitRule git = new GitRule("target/ApplicationTest-git/", ssh::getUsername, ssh::port);

    @Before
    public void init() {
        Environment.ENVIRONMENT_THREAD_LOCAL.set(ENVIRONMENT);
    }

    @After
    public void reset() {
        Environment.ENVIRONMENT_THREAD_LOCAL.remove();
    }

    @Test
    public void start() throws IOException {
        git.addDeploymentsJson("start");

        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        Application.start(
                "prod",
                new SshKey(ssh.getKeyPath(), ssh.getKeyPassphrase()),
                new File("target/ApplicationTest-start-work/"), -1, -1,
                null,
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
                null,
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
                null,
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
                null,
                "prod",
                new SshKey(ssh.getKeyPath(), ssh.getKeyPassphrase()),
                new File("target/ApplicationTest-tg-work/"),
                null, false,
                new GitConfiguration(git.directory(), "ApplicationTest-tg", "master", null, ssh.getKeyPath().getAbsolutePath(), ssh.getKeyPassphrase()),
                new LocalFileRepository(new File("target/missing")),
                new Nexus("http://faked", null, null) {
                    @Override
                    public DownloadHandler download(final PrintStream out,
                                                    final String groupId, final String artifactId, final String version,
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

        final String output = new String(out.toByteArray());
        assertTrue(output, output.contains("foo setup in /foo/foo-1/ for host localhost:" + ssh.port()));
    }

    @Test
    public void install() throws Throwable {
        MemListener.MESSAGES.clear();
        git.addFile("art/tribestream/conf/someconf.properties", "e=${tsm.environment}").addDeploymentsJson("art");

        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final ByteArrayOutputStream err = new ByteArrayOutputStream();
        final CrestContext ctx = new CrestContext() {
            @Override
            public Object proceed() {
                try {
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
                            null, "0.69", "8u60", "prod", "com.foo.bar", "art", "1.0", -1, -1, new Duration("-1 minutes"),
                            false, false, null,
                            new PrintStream(out), new PrintStream(err), ENVIRONMENT, new GlobalConfiguration(new File("")));
                } catch (final IOException e) {
                    throw new IllegalStateException(e);
                }
                return null;
            }

            @Override
            public Method getMethod() {
                try {
                    return Application.class.getMethod(
                            "install",
                            Nexus.class, Nexus.class, GitConfiguration.class, LocalFileRepository.class,
                            SshKey.class, File.class, String.class, String.class, String.class, String.class,
                            String.class, String.class, String.class, int.class, int.class, Duration.class,
                            boolean.class, boolean.class, String.class,
                            PrintStream.class, PrintStream.class, Environment.class, GlobalConfiguration.class);
                } catch (final NoSuchMethodException e) {
                    throw new IllegalStateException(e);
                }
            }

            @Override
            public List<Object> getParameters() {
                return asList(null, null, null, null, null, null, null, null, null, "testenv", null, "art");
            }

            @Override
            public String getName() {
                return "install";
            }

            @Override
            public List<ParameterMetadata> getParameterMetadata() {
                return emptyList();
            }
        };
        Environment.ENVIRONMENT_THREAD_LOCAL.set(new SystemEnvironment(
                new HashMap<Class<?>, Object>() {{
                    final GlobalConfiguration configuration = new GlobalConfiguration(new File("target/nothere/attall"));
                    put(GlobalConfiguration.class, configuration);
                    put(Listeners.class, new Listeners(configuration));
                }}));
        try {
            Notifier.intercept(ctx);
        } finally {
            Environment.ENVIRONMENT_THREAD_LOCAL.remove();
        }

        assertEquals(asList(
                "[ -f \"/art/prod/bin/shutdown\" ] && \"/art/prod/bin/shutdown\" 1200 -force",
                "rm -Rf \"/art/prod/\"",
                "mkdir -p \"/art/prod/\"",
                "cd \"/art/prod/\" && for i in bin conf lib logs temp webapps work; do mkdir -p $i; done",
                "mkdir -p \"/art/prod/conf/\"",
                "chmod ug+rwx \"/art/prod/bin/processes\" \"/art/prod/bin/startup\" \"/art/prod/bin/shutdown\" \"/art/prod/bin/run\" \"/art/prod/bin/restart\""), ssh.commands());
        assertEquals("main => com.foo.bar:art:1.0:war", IO.readString(new File(ssh.getHome(), "art/prod/webapps/art.war")));
        assertEquals("e=prod", IO.readString(new File(ssh.getHome(), "art/prod/conf/someconf.properties"))); // filtering

        final String meta = IO.slurp(new File(ssh.getHome(), "art/prod/conf/tsm-metadata.json"))
                .replaceAll("\"date\":\"[^\"]*\"", "\"date\":\"DATE\"")
                .replaceAll("\"revision\":\"[^\"]*\"", "\"revision\":\"REV\"")
                .replace("\n\r", "\n");
        assertEquals(meta,
                "{\n" +
                        "  \"date\":\"DATE\",\n" +
                        "  \"host\":\"localhost:" + ssh.port() + "\",\n" +
                        "  \"environment\":\"prod\",\n" +
                        "  \"application\":{\n" +
                        "    \"groupId\":\"com.foo.bar\",\n" +
                        "    \"artifactId\":\"art\",\n" +
                        "    \"version\":\"1.0\",\n" +
                        "    \"originalArtifact\":\"art\"\n" +
                        "  },\n" +
                        "  \"git\":{\n" +
                        "    \"branch\":\"master\",\n" +
                        "    \"revision\":\"REV\"\n" +
                        "  },\n" +
                        "  \"server\":{\n" +
                        "    \"name\":\"tribestream-0.69\"\n" +
                        "  },\n" +
                        "  \"java\":{\n" +
                        "    \"version\":\"8u60\"\n" +
                        "  }\n" +
                        "}\n");
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

        final List<String> msg = new ArrayList<>();
        synchronized (MemListener.MESSAGES) {
            msg.addAll(MemListener.MESSAGES);
            MemListener.MESSAGES.clear();
        }
        assertEquals(2, msg.size());
        assertTrue(msg.get(0).endsWith(" Executing (environment=testenv) install 'art'"));
        assertTrue(msg.get(1).endsWith(" Executed (environment=testenv) install 'art', status=SUCCESS"));
    }

    @Test
    public void configOnlyOneWebapp() throws Throwable {
        git.addFile(
                "configonly/deployments.json",
                "{\"environments\":[{" +
                        "\"hosts\":[\"localhost:" + git.getSshPort() + "\"]," +
                        "\"webapps\": [\"com.company:superart:0.1.2?context=super\"]," +
                        "\"names\":[\"prod\"]," +
                        "\"base\":\"/\"," +
                        "\"user\":\"" + git.getSshUser() + "\"" +
                        "}]}");

        Application.installConfigOnly(
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
                new File("target/ApplicationTest-configonly-onewebapp-work/"),
                "7.0.1", null, "8u112", "prod", "configonly", -1, -1,
                new Duration("-1 minutes"), false, false, null,
                new PrintStream(System.out), new PrintStream(System.err), ENVIRONMENT, new GlobalConfiguration(new File("")));

        final String meta = IO.slurp(new File(ssh.getHome(), "configonly/prod/conf/tsm-metadata.json"))
                .replaceAll("\"date\":\"[^\"]*\"", "\"date\":\"DATE\"")
                .replaceAll("\"revision\":\"[^\"]*\"", "\"revision\":\"REV\"")
                .replace("\n\r", "\n");
        assertEquals(meta,
                "{\n" +
                        "  \"date\":\"DATE\",\n" +
                        "  \"host\":\"localhost:" + ssh.port() + "\",\n" +
                        "  \"environment\":\"prod\",\n" +
                        "  \"application\":{\n" +
                        "    \"groupId\":\"com.company\",\n" +
                        "    \"artifactId\":\"configonly\",\n" +
                        "    \"version\":\"0.1.2\",\n" +
                        "    \"originalArtifact\":\"superart\"\n" +
                        "  },\n" +
                        "  \"git\":{\n" +
                        "    \"branch\":\"master\",\n" +
                        "    \"revision\":\"REV\"\n" +
                        "  },\n" +
                        "  \"server\":{\n" +
                        "    \"name\":\"apache-tomee-7.0.1\"\n" +
                        "  },\n" +
                        "  \"java\":{\n" +
                        "    \"version\":\"8u112\"\n" +
                        "  }\n" +
                        "}\n");
    }

    @Test
    public void configOnlyOneWebappFiltering() throws Throwable {
        git.addFile(
                "configOnlyOneWebappFiltering/deployments.json",
                "{\"environments\":[{" +
                        "\"hosts\":[\"localhost:" + git.getSshPort() + "\"]," +
                        "\"webapps\": [\"com.company:superart:${app.version}?context=super\"]," +
                        "\"properties\": {\"app.version\":\"0.1.2\"}," +
                        "\"names\":[\"prod\"]," +
                        "\"base\":\"/\"," +
                        "\"user\":\"" + git.getSshUser() + "\"" +
                        "}]}");

        Application.installConfigOnly(
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
                new GitConfiguration(git.directory(), "ApplicationTest-configOnlyOneWebappFiltering", "master", null, ssh.getKeyPath().getAbsolutePath(), ssh.getKeyPassphrase()),
                new LocalFileRepository(new File("target/missing")),
                new SshKey(ssh.getKeyPath(), ssh.getKeyPassphrase()),
                new File("target/ApplicationTest-configonly-onewebapp-filtering-work/"),
                "7.0.2", null, "8u112", "prod", "configOnlyOneWebappFiltering",
                -1, -1, new Duration("-1 minutes"), false, false, null,
                new PrintStream(System.out), new PrintStream(System.err), ENVIRONMENT, new GlobalConfiguration(new File("")));

        final String meta = IO.slurp(new File(ssh.getHome(), "configOnlyOneWebappFiltering/prod/conf/tsm-metadata.json"))
                .replaceAll("\"date\":\"[^\"]*\"", "\"date\":\"DATE\"")
                .replaceAll("\"revision\":\"[^\"]*\"", "\"revision\":\"REV\"")
                .replace("\n\r", "\n");
        assertEquals(meta,
                "{\n" +
                        "  \"date\":\"DATE\",\n" +
                        "  \"host\":\"localhost:" + ssh.port() + "\",\n" +
                        "  \"environment\":\"prod\",\n" +
                        "  \"application\":{\n" +
                        "    \"groupId\":\"com.company\",\n" +
                        "    \"artifactId\":\"configOnlyOneWebappFiltering\",\n" +
                        "    \"version\":\"0.1.2\",\n" +
                        "    \"originalArtifact\":\"superart\"\n" +
                        "  },\n" +
                        "  \"git\":{\n" +
                        "    \"branch\":\"master\",\n" +
                        "    \"revision\":\"REV\"\n" +
                        "  },\n" +
                        "  \"server\":{\n" +
                        "    \"name\":\"apache-tomee-7.0.2\"\n" +
                        "  },\n" +
                        "  \"java\":{\n" +
                        "    \"version\":\"8u112\"\n" +
                        "  }\n" +
                        "}\n");
    }

    @Test
    public void configOnlyApps() throws Throwable {
        git.addFile(
                "configonly_apps/deployments.json",
                "{\"environments\":[{" +
                        "\"hosts\":[\"localhost:" + git.getSshPort() + "\"]," +
                        "\"apps\": [\"com.company:superar:0.1.2:rar?rename=test\"]," +
                        "\"names\":[\"prod\"]," +
                        "\"base\":\"/\"," +
                        "\"user\":\"" + git.getSshUser() + "\"" +
                        "}]}");

        Application.installConfigOnly(
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
                new GitConfiguration(git.directory(), "ApplicationTest-configOnlyApps", "master", null, ssh.getKeyPath().getAbsolutePath(), ssh.getKeyPassphrase()),
                new LocalFileRepository(new File("target/missing")),
                new SshKey(ssh.getKeyPath(), ssh.getKeyPassphrase()),
                new File("target/ApplicationTest-configonly-apps-work/"),
                "7.0.1", null, "8u112", "prod", "configonly_apps", -1,
                -1, new Duration("-1 minutes"), false, false, null,
                new PrintStream(System.out), new PrintStream(System.err), ENVIRONMENT, new GlobalConfiguration(new File("")));

        assertEquals("lib => com.company:superar:0.1.2:rar", IO.slurp(new File(ssh.getHome(), "configonly_apps/prod/apps/test.rar")).trim());
        assertEquals("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<tomee>\n" +
                "  <Deployments dir=\"apps\" />\n" +
                "</tomee>", IO.slurp(new File(ssh.getHome(), "configonly_apps/prod/conf/tomee.xml")).trim());

        final String meta = IO.slurp(new File(ssh.getHome(), "configonly_apps/prod/conf/tsm-metadata.json"))
                .replaceAll("\"date\":\"[^\"]*\"", "\"date\":\"DATE\"")
                .replaceAll("\"revision\":\"[^\"]*\"", "\"revision\":\"REV\"")
                .replace("\n\r", "\n");
        assertEquals("{\n" +
                "  \"date\":\"DATE\",\n" +
                "  \"host\":\"localhost:" + ssh.port() + "\",\n" +
                "  \"environment\":\"prod\",\n" +
                "  \"application\":{\n" +
                "    \"groupId\":\"\",\n" +
                "    \"artifactId\":\"configonly_apps\",\n" +
                "    \"version\":\"\",\n" +
                "    \"originalArtifact\":\"configonly_apps\"\n" +
                "  },\n" +
                "  \"git\":{\n" +
                "    \"branch\":\"master\",\n" +
                "    \"revision\":\"REV\"\n" +
                "  },\n" +
                "  \"server\":{\n" +
                "    \"name\":\"apache-tomee-7.0.1\"\n" +
                "  },\n" +
                "  \"java\":{\n" +
                "    \"version\":\"8u112\"\n" +
                "  }\n" +
                "}", meta.trim());
    }

    @Test
    public void exec() throws IOException {
        git.addDeploymentsJson("exec");
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        Application.exec(
                "prod",
                new SshKey(ssh.getKeyPath(), ssh.getKeyPassphrase()),
                new File("target/ApplicationTest-exec/"),
                -1, -1, null,
                new GitConfiguration(git.directory(), "ApplicationTest-install-envs", "master", null, ssh.getKeyPath().getAbsolutePath(), ssh.getKeyPassphrase()),
                "exec", "exectest %environment",
                new PrintStream(out),
                ENVIRONMENT);
        final String output = new String(out.toByteArray());
        assertTrue(output, output.contains("Executing command for exec on localhost:" + git.getSshPort() + " for environment prod"));
        final Collection<String> commands = ssh.commands();
        assertEquals("exectest prod", commands.iterator().next());
        assertEquals(1, commands.size());
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
                null, "0.69", "8u60", "prod,other", "com.foo.bar", "art2", "1.0",
                -1, -1, new Duration("-1 minutes"), false, false, null,
                new PrintStream(out), new PrintStream(err), ENVIRONMENT, new GlobalConfiguration(new File("")));

        assertEquals("e=prod", IO.readString(new File(ssh.getHome(), "art2/prod/conf/someconf.properties"))); // filtering
        assertEquals("e=other", IO.readString(new File(ssh.getHome(), "art2/other/conf/someconf.properties"))); // filtering
    }
}
