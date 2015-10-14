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

import com.tomitribe.tsm.configuration.Deployments;
import com.tomitribe.tsm.configuration.GitConfiguration;
import com.tomitribe.tsm.configuration.GlobalConfiguration;
import com.tomitribe.tsm.configuration.LocalFileRepository;
import com.tomitribe.tsm.configuration.Nexus;
import com.tomitribe.tsm.configuration.SshKey;
import com.tomitribe.tsm.configuration.Substitutors;
import com.tomitribe.tsm.console.ProgressBar;
import com.tomitribe.tsm.crest.interceptor.DefaultParameters;
import com.tomitribe.tsm.file.TempDir;
import com.tomitribe.tsm.ssh.Ssh;
import lombok.NoArgsConstructor;
import org.apache.johnzon.mapper.MapperBuilder;
import org.apache.johnzon.mapper.reflection.JohnzonParameterizedType;
import org.tomitribe.crest.api.Command;
import org.tomitribe.crest.api.Default;
import org.tomitribe.crest.api.Option;
import org.tomitribe.crest.api.Out;
import org.tomitribe.crest.cli.api.CliEnvironment;
import org.tomitribe.crest.environments.Environment;
import org.tomitribe.util.IO;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

import static com.tomitribe.tsm.crest.CrestOutputCapture.capture;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static lombok.AccessLevel.PRIVATE;

@Command("application")
@NoArgsConstructor(access = PRIVATE)
public class Application {
    @Command(interceptedBy = DefaultParameters.class)
    public static void start(@Option("environment") final String environment,
                             @Option("ssh.") final SshKey sshKey,
                             @Option("work-dir-base") @Default("${java.io.tmpdir}/tsm") final File workDirBase,
                             @Option("node-index") @Default("-1") final int nodeIndex,
                             final GitConfiguration git,
                             final String artifactId,
                             @Out final PrintStream out) throws IOException {
        execute(
            environment, sshKey, workDirBase, git, artifactId, out, nodeIndex,
            "Starting %s on %s for environment %s", "\"%s/bin/startup\"");
    }

    @Command(interceptedBy = DefaultParameters.class)
    public static void stop(@Option("environment") final String environment,
                            @Option("ssh.") final SshKey sshKey,
                            @Option("work-dir-base") @Default("${java.io.tmpdir}/tsm") final File workDirBase,
                            @Option("node-index") @Default("-1") final int nodeIndex,
                            final GitConfiguration git,
                            final String artifactId,
                            @Out final PrintStream out) throws IOException {
        execute(
            environment, sshKey, workDirBase, git, artifactId, out, nodeIndex,
            "Stopping %s on %s for environment %s", "\"%s/bin/shutdown\" 1200 -force");
    }

    @Command(interceptedBy = DefaultParameters.class)
    public static void restart(@Option("environment") final String environment,
                               @Option("ssh.") final SshKey sshKey,
                               @Option("work-dir-base") @Default("${java.io.tmpdir}/tsm") final File workDirBase,
                               @Option("node-index") @Default("-1") final int nodeIndex,
                               final GitConfiguration git,
                               final String artifactId,
                               @Out final PrintStream out) throws IOException {
        execute(
            environment, sshKey, workDirBase, git, artifactId, out, nodeIndex,
            "Restarting %s on %s for environment %s", "\"%s/bin/shutdown\"", "sleep 3", "\"%s/bin/startup\"");
    }

    @Command(interceptedBy = DefaultParameters.class)
    public static void ping(@Option("environment") final String environment,
                            @Option("ssh.") final SshKey sshKey,
                            @Option("work-dir-base") @Default("${java.io.tmpdir}/tsm") final File workDirBase,
                            @Option("node-index") @Default("-1") final int nodeIndex,
                            final GitConfiguration git,
                            final String artifactId,
                            @Out final PrintStream out) throws IOException {
        execute(
            environment, sshKey, workDirBase, git, artifactId, out, nodeIndex,
            "Testing %s on %s for environment %s", e -> {
                final String url = "http://127.0.0.1:" + e.getProperties().get("tsm.https");
                return new String[]{ // GET is often in PCI zones but not curl so try it first
                    String.format("GET %s 2>&1 | grep -v 'command not found' || curl -v %s", url, url)
                };
            });
    }

    @Command(value = "install-tar.gz", interceptedBy = DefaultParameters.class)
    public static void installTarGzArtifact(@Option("environment") final String environment,
                                            @Option("ssh.") final SshKey sshKey,
                                            @Option("work-dir-base") @Default("${java.io.tmpdir}/tsm") final File workDirBase,
                                            final GitConfiguration git,
                                            final LocalFileRepository localFileRepository,
                                            final Nexus nexus,
                                            final String artifactId, final String coordinates,
                                            @Out final PrintStream out) throws IOException {
        final String appWorkName = "installtargz_" + artifactId;
        final File workDir = TempDir.newTempDir(workDirBase, appWorkName);

        final String[] segments = coordinates.split(":");
        final File cached = localFileRepository.find(segments[0], segments[1], segments[2], segments.length > 3 ? segments[3] : null, "tar.gz");
        final File tarGz;
        if (cached.isFile()) {
            tarGz = cached;
        } else {
            tarGz = new File(workDir, segments[1] + '-' + segments[2] + ".tar.gz");
            nexus.download(out, segments[0], segments[1], segments[2], segments.length > 3 ? segments[3] : null, "tar.gz").to(tarGz);
        }

        try (final FileReader reader = new FileReader(gitClone(git, artifactId, out, workDir))) {
            final Deployments.Application app = Deployments.read(reader);
            final Deployments.Environment env = app.findEnvironment(environment);
            validateEnvironment(environment, artifactId, env);

            env.getHosts().forEach(host -> {
                out.println("Installing " + segments[1] + " to " + host);

                try (final Ssh ssh = newSsh(sshKey, host, app, env)) {
                    final String fixedBase = env.getBase() + (env.getBase().endsWith("/") ? "" : "/");
                    final String remoteWorkDir = fixedBase + "work-provisioning/";
                    final String target = remoteWorkDir + tarGz.getName();
                    final String targetFolder = fixedBase + segments[1] + "/" + segments[1] + '-' + segments[2] + '/';
                    ssh.exec(String.format("mkdir -p \"%s\" \"%s\"", remoteWorkDir, targetFolder))
                        .scp(tarGz, target, new ProgressBar(out, "Installing " + segments[1] + " on " + host))
                        .exec(String.format("tar xvf \"%s\" -C \"%s\" --strip 1", target, targetFolder))
                        .exec(String.format("rm \"%s\"", target));

                    out.println(segments[1] + " setup in " + targetFolder + " for host " + host);
                }
            });
        }
    }

    @Command(interceptedBy = DefaultParameters.class)
    public static void versions(final Nexus nexus,
                                final String groupId, final String artifactId,
                                @Out final PrintStream out) {
        final Nexus.Metadata metadata = nexus.versions(groupId, artifactId);
        out.println("Metadata for " + groupId + ':' + artifactId + ':');

        final Nexus.Versioning versioning = metadata.getVersioning();
        if (versioning == null || ofNullable(versioning.getVersion()).orElse(emptyList()).isEmpty()) {
            out.println("- NO VERSION, ensure nexus index is up to date");
        } else {
            out.println("- last update: " + versioning.getLastUpdated());
            out.println("- lastest version: " + versioning.getLatest());
            out.println("- available versions:");
            versioning.getVersion().stream().map(v -> "-- " + v).forEach(out::println);
        }
    }

    @Command(value = "config-only", interceptedBy = DefaultParameters.class)
    public static void installConfigOnly(final Nexus nexus, // likely our apps
                                         @Option("lib.") final Nexus nexusLib, // likely central proxy
                                         final GitConfiguration git,
                                         final LocalFileRepository localFileRepository,
                                         @Option("ssh.") final SshKey sshKey,
                                         @Option("work-dir-base") @Default("${java.io.tmpdir}/tsm") final File workDirBase,
                                         @Option("tribestream-version") final String tribestreamVersion,
                                         @Option("java-version") final String javaVersion,
                                         @Option("environment") final String environment,
                                         final String artifactId, // ie application in git
                                         @Option("node-index") @Default("-1") final int nodeIndex,
                                         @Option("as-service") final boolean asService,
                                         @Option("restart") @Default("false") final boolean restart,
                                         @Out final PrintStream out,
                                         @Out final PrintStream err) throws IOException {
        install(
            nexus, nexusLib, git, localFileRepository, sshKey, workDirBase, tribestreamVersion, javaVersion, environment,
            null, artifactId, null, // see install() for details
            nodeIndex, asService, restart, out, err);
    }

    // same as install but without groupId/artifactId (read from deployments.json)
    // behavior wise it is an alias for config-only
    @Command(value = "quick-install", interceptedBy = DefaultParameters.class)
    public static void quickInstall(final Nexus nexus, // likely our apps
                                         @Option("lib.") final Nexus nexusLib, // likely central proxy
                                         final GitConfiguration git,
                                         final LocalFileRepository localFileRepository,
                                         @Option("ssh.") final SshKey sshKey,
                                         @Option("work-dir-base") @Default("${java.io.tmpdir}/tsm") final File workDirBase,
                                         @Option("tribestream-version") final String tribestreamVersion,
                                         @Option("java-version") final String javaVersion,
                                         @Option("environment") final String environment,
                                         final String artifactId, // ie application in git
                                         @Option("node-index") @Default("-1") final int nodeIndex,
                                         @Option("as-service") final boolean asService,
                                         @Option("restart") @Default("false") final boolean restart,
                                         @Out final PrintStream out,
                                         @Out final PrintStream err) throws IOException {
        install(
            nexus, nexusLib, git, localFileRepository, sshKey, workDirBase, tribestreamVersion, javaVersion, environment,
            null, artifactId, null, // see install() for details
            nodeIndex, asService, restart, out, err);
    }

    @Command(interceptedBy = DefaultParameters.class)
    public static void install(final Nexus nexus, // likely our apps
                               @Option("lib.") final Nexus nexusLib, // likely central proxy
                               final GitConfiguration git,
                               final LocalFileRepository localFileRepository,
                               @Option("ssh.") final SshKey sshKey,
                               @Option("work-dir-base") @Default("${java.io.tmpdir}/tsm") final File workDirBase,
                               @Option("tribestream-version") final String tribestreamVersion,
                               @Option("java-version") final String javaVersion,
                               @Option("environment") final String environment,
                               final String inGroupId, final String inArtifactId, final String inVersion,
                               @Option("node-index") @Default("-1") final int nodeIndex,
                               @Option("as-service") final boolean asService,
                               @Option("restart") @Default("false") final boolean restart,
                               @Out final PrintStream out,
                               @Out final PrintStream err) throws IOException {
        final String appWorkName = ofNullable(inGroupId).orElse("-") + '_' + inArtifactId;
        final File workDir = TempDir.newTempDir(workDirBase, appWorkName);

        final File deploymentConfig = gitClone(git, inArtifactId, out, workDir);

        try (final FileReader reader = new FileReader(deploymentConfig)) {
            final Deployments.Application app = Deployments.read(reader);
            final Deployments.Environment env = app.findEnvironment(environment);
            validateEnvironment(environment, inArtifactId, env);

            final String artifactId = ofNullable(env.getDeployerProperties().get("artifactId")).orElse(inArtifactId);
            final boolean skipEnvFolder = Boolean.parseBoolean(ofNullable(env.getDeployerProperties().get("skipEnvironmentFolder")).orElse("false"));

            final String groupId = ofNullable(inGroupId).orElse(env.getGroupId());
            final String version = ofNullable(inVersion).orElse(env.getVersion());

            final File downloadedFile;
            if (groupId == null && version == null) {
                downloadedFile = null;
                out.println("configuration only, skipping artifacts");
            } else {
                downloadedFile = new File(workDir, appWorkName + "_" + version + ".war");
                final File cacheFile = localFileRepository.find(groupId, artifactId, version, null, "war");
                if (cacheFile.isFile()) {
                    out.println("Using locally cached " + artifactId + '.');
                    IO.copy(cacheFile, downloadedFile);
                } else {
                    out.println("Didn't find cached " + artifactId + " in " + cacheFile + " so trying to download it for this provisioning.");
                    nexus.download(out, groupId, artifactId, version, null, "war").to(downloadedFile);
                }
            }

            final Collection<File> additionalLibs = new LinkedList<>();
            ofNullable(env.getLibs()).orElse(emptyList()).stream().forEach(lib -> {
                final String[] segments = lib.split(":");
                final File local = new File(workDir, segments[1] + ".jar");
                nexusLib.download(out, segments[0], segments[1], segments[2], null, "jar").to(local);
                additionalLibs.add(local);
            });

            final Collection<File> additionalWebapps = new LinkedList<>();
            ofNullable(env.getWebapps()).orElse(emptyList()).stream().forEach(war -> {
                final String[] segments = war.split(":");
                final File local = new File(workDir, segments[1] + ".war");
                nexusLib.download(out, segments[0], segments[1], segments[2], null, "war").to(local);
                additionalWebapps.add(local);
            });

            final AtomicReference<String> chosenTribestreamVersion = new AtomicReference<>(tribestreamVersion);
            final AtomicReference<String> chosenJavaVersion = new AtomicReference<>(javaVersion);
            final Map<String, Iterator<String>> byHostEntries = ofNullable(env.getByHostProperties()).orElse(emptyMap())
                .entrySet().stream().collect(toMap(Map.Entry::getKey, e -> e.getValue().iterator()));
            final AtomicInteger currentIdx = new AtomicInteger();
            env.getHosts().forEach(host -> {
                out.println("Deploying " + artifactId + " on " + host);

                // override by host variables
                byHostEntries.forEach((k, v) -> env.getProperties().put(k, v.next()));
                env.getProperties().putIfAbsent("host", host);

                if (nodeIndex >= 0 && nodeIndex != currentIdx.getAndIncrement()) {
                    return;
                } // else deploy

                try (final Ssh ssh = newSsh(sshKey, host, app, env)) {
                    final String fixedBase = env.getBase() + (env.getBase().endsWith("/") ? "" : "/");

                    // get tribestream version or just ask the user for it listing the ones the server has
                    final String serverVersion = ofNullable(tribestreamVersion).orElseGet(() -> readVersion(out, err, ssh, fixedBase, "tribestream", chosenTribestreamVersion, "tribestream"));
                    final String jdkVersion = ofNullable(javaVersion).orElseGet(() -> readVersion(out, err, ssh, fixedBase, "java", chosenJavaVersion, "jdk"));

                    final String targetFolder = fixedBase + artifactId + "/" + (skipEnvFolder ? "" : (environment + '/'));
                    final String serverShutdownCmd = targetFolder + "bin/shutdown";

                    ssh
                        // shutdown if running
                        .exec("[ -f \"" + serverShutdownCmd + "\" ] && \"" + serverShutdownCmd + "\" 1200 -force")
                            // recreate the base folder if needed
                        .exec(String.format("rm -Rf \"%s\"", targetFolder))
                        .exec(String.format("mkdir -p \"%s\"", targetFolder))
                            // create app structure
                        .exec("cd \"" + targetFolder + "\" && for i in bin conf lib logs temp webapps work; do mkdir $i; done");

                    if (downloadedFile != null) {
                        ssh.scp(downloadedFile, targetFolder + "webapps/" + artifactId + ".war", new ProgressBar(out, "Uploading " + artifactId + " on " + host));
                    }

                    // uploading libs
                    additionalLibs.forEach(lib -> ssh.scp(lib, targetFolder + "lib/" + lib.getName(), new ProgressBar(out, "Uploading " + lib.getName())));
                    additionalWebapps.forEach(war -> ssh.scp(war, targetFolder + "webapps/" + app.getName(), new ProgressBar(out, "Uploading " + war.getName())));

                    // synchronizing configuration
                    final List<File> foldersToSyncs = new LinkedList<>();
                    final List<String> configFolders = asList("tribestream", "tribestream-" + envFolder(env, environment));
                    configFolders.forEach(folder -> {
                        final File foldersToSync = new File(deploymentConfig.getParentFile(), folder);
                        if (foldersToSync.isDirectory()) {
                            out.println("Synchronizing " + foldersToSync.getName() + " folders");
                            synch(out, ssh, foldersToSync, foldersToSync, targetFolder, app, env);
                            foldersToSyncs.add(foldersToSync);
                        } else {
                            out.println("No '" + folder + "' configuration folder found.");
                        }
                    });

                    Collections.reverse(foldersToSyncs);

                    final String envrt =
                        "export JAVA_HOME=\"" + fixedBase + "java/jdk-" + jdkVersion + "\"\n" +
                            "export CATALINA_HOME=\"" + fixedBase + "tribestream/tribestream-" + serverVersion + "\"\n" +
                            "export CATALINA_BASE=\"" + targetFolder + "\"\n" +
                            "export CATALINA_PID=\"" + targetFolder + "work/tribestream.pid" + "\"\n";

                    {   // setenv needs some more love to get a proper env setup
                        final File setEnv = new File(workDir, "setenv.sh");
                        if (!setEnv.isFile()) {
                            final Optional<File> source = foldersToSyncs.stream().map(f -> new File(f, "bin/setenv.sh")).filter(File::isFile).findFirst();
                            final StringBuilder content = new StringBuilder();
                            final Consumer<StringBuilder> appender = b -> {
                                b.append("\n");
                                b.append("# Generated by TSM, don't edit please\n");
                                b.append(envrt);
                                b.append("# End of TSM edit\n");
                                b.append("\n");
                            };
                            if (source.isPresent()) {
                                try (final BufferedReader setEnvReader = new BufferedReader(new FileReader(source.get()))) {
                                    String line;
                                    boolean jobDone = false;
                                    while ((line = setEnvReader.readLine()) != null) {
                                        if (!jobDone && !line.startsWith("#")) {
                                            // we do it first then the user can still override it but at least we set it up
                                            appender.accept(content);
                                            jobDone = true;
                                        }
                                        content.append(line).append('\n');
                                    }
                                } catch (final IOException e) {
                                    throw new IllegalStateException(e);
                                }
                            } else {
                                content.append("#! /bin/sh\n");
                                appender.accept(content);
                            }
                            try (final FileWriter writer = new FileWriter(setEnv)) {
                                writer.write(content.toString());
                            } catch (final IOException e) {
                                throw new IllegalStateException(e);
                            }
                        }
                        ssh.scp(setEnv, targetFolder + "bin/" + setEnv.getName(), new ProgressBar(out, "Uploading updated setenv.sh"));
                    }

                    // now we will add few script/files for easiness but these ones are "nice to have" and not "must have"
                    final String scriptTop =
                        "#! /bin/bash\n" +
                            "\n" +
                            "proc_script_base=\"`cd $(dirname $0) && cd .. && pwd`\"\n" +
                            "source \"$proc_script_base/bin/setenv.sh\"\n";
                    addScript(
                        out, ssh, foldersToSyncs, workDir, targetFolder, "processes",
                        scriptTop + "ps aux | grep \"$proc_script_base\" | grep -v grep\n\n");
                    addScript(
                        out, ssh, foldersToSyncs, workDir, targetFolder, "startup",
                        scriptTop +
                            "[ -f \"$proc_script_base/bin/pre_startup.sh\" ] && \"$proc_script_base/bin/pre_startup.sh\"\n" +
                            "nohup \"$CATALINA_HOME/bin/startup.sh\" \"$@\" > $proc_script_base/logs/nohup.log &\n" +
                            "[ -f \"$proc_script_base/bin/post_startup.sh\" ] && \"$proc_script_base/bin/post_startup.sh\"\n" +
                            "\n");
                    addScript(
                        out, ssh, foldersToSyncs, workDir, targetFolder, "shutdown",
                        scriptTop +
                            "[ -f \"$proc_script_base/bin/pre_shutdown.sh\" ] && \"$proc_script_base/bin/pre_shutdown.sh\"\n" +
                            "\"$CATALINA_HOME/bin/shutdown.sh\" \"$@\"\n" +
                            "[ -f \"$proc_script_base/bin/post_shutdown.sh\" ] && \"$proc_script_base/bin/post_shutdown.sh\"\n" +
                            "\n");
                    addScript(
                        out, ssh, foldersToSyncs, workDir, targetFolder, "run",
                        scriptTop +
                            "[ -f \"$proc_script_base/bin/pre_startup.sh\" ] && \"$proc_script_base/bin/pre_startup.sh\"\n" +
                            "\"$CATALINA_HOME/bin/catalina.sh\" \"run\" \"$@\"\n\n"); // no post_startup since run is blocking
                    addScript(
                        out, ssh, foldersToSyncs, workDir, targetFolder, "restart",
                        scriptTop + "\"$proc_script_base/bin/shutdown\" && sleep 3 && \"$proc_script_base/bin/startup\"\n\n");

                    {   // just to be able to know what we did and when when browsing manually the installation, we could add much more if needed
                        final File metadata = new File(workDir, "tsm-metadata.json"); // sample usage in com.sbux.pos.basket.configuration.aspconfig.TsmPropertiesProvider
                        try (final FileWriter writer = new FileWriter(metadata)) {
                            writer.write("{\n");
                            writer.write("  \"date\":\"" + LocalDateTime.now().toString() + "\",\n");
                            writer.write("  \"host\":\"" + host + "\",\n");
                            writer.write("  \"environment\":\"" + environment + "\",\n");
                            writer.write("  \"application\":{\n");
                            writer.write("    \"groupId\":\"" + ofNullable(groupId).orElse("") + "\",\n");
                            writer.write("    \"artifactId\":\"" + artifactId + "\",\n");
                            writer.write("    \"version\":\"" + ofNullable(version).orElse("") + "\"\n");
                            writer.write("  },\n");
                            writer.write("  \"tribestream\":{\n");
                            writer.write("    \"version\":\"" + serverVersion + "\"\n");
                            writer.write("  },\n");
                            writer.write("  \"java\":{\n");
                            writer.write("    \"version\":\"" + jdkVersion + "\"\n");
                            writer.write("  }\n");
                            writer.write("}\n");
                        } catch (final IOException e) {
                            throw new IllegalStateException(e);
                        }
                        ssh.scp(metadata, targetFolder + "conf/" + metadata.getName(), new ProgressBar(out, "Uploading deployment metadata"));
                    }

                    // finally make scripts executable if they were not
                    final List<String> scripts = new ArrayList<>(asList("processes", "startup", "shutdown", "run", "restart"));
                    scripts.addAll(foldersToSyncs.stream().map(f -> new File(f, "/bin"))
                        .flatMap(f -> asList(ofNullable(f.listFiles(scr -> scr.getName().endsWith(".sh"))).orElse(new File[0])).stream())
                        .map(File::getName)
                        .collect(toList()));
                    ssh.exec("chmod ug+rwx " + scripts.stream()
                        .map(n -> "\"" + targetFolder + "bin/" + n + "\"").collect(joining(" ")));

                    if (asService) { // needs write access in /etc /init.d/and sudo without password
                        final File initD = new File(workDir, "initd");
                        if (!initD.isFile()) {
                            try (final FileWriter writer = new FileWriter(initD)) {
                                writer.write("" +
                                    "# chkconfig: 345 99 01\n" + // <levels> <start> <stop>
                                    "# description: handles " + artifactId + "\n" +
                                    "\n" + envrt + "\n\n" +
                                    "exec $CATALINA_HOME/bin/catalina.sh $*" +
                                    "");
                            } catch (final IOException e) {
                                throw new IllegalStateException(e);
                            }
                        }
                        final String script = targetFolder + "bin/init.d.sh";
                        ssh.scp(initD, script, new ProgressBar(out, "Uploading init.d script"));
                        ssh.exec("sudo mv \"" + script + "\" \"/etc/init.d/" + artifactId + "\"")
                            .exec("sudo chmod ug+rx \"/etc/init.d/" + artifactId + "\"")
                            .exec("sudo chkconfig --add " + artifactId);
                    }

                    if (!restart) {
                        out.println(artifactId + " setup in " + targetFolder + " for host " + host + ", you can now use start command.");
                    } else {
                        out.println("Restarting " + targetFolder + " for host " + host);
                        ssh.exec("\"" + targetFolder + "bin/startup\"");
                    }

                    git.reset(deploymentConfig.getParentFile().getParentFile());
                    // WARN: don't start now, use start/stop/restart/status commands but not provisioning one!!!
                }
            });
        }
    }

    private static String envFolder(final Deployments.Environment environment, final String def) {
        return ofNullable(environment.getProperties().get("tsm.tribestream.folder"))
            .orElseGet(() -> ofNullable(environment.getDeployerProperties().get("tribestream.folder")).orElse(def));
    }

    private static void addScript(final PrintStream out, final Ssh ssh, final Collection<File> foldersToSync, final File workDir,
                                  final String targetFolder, final String name, final String content) {
        if (!foldersToSync.stream().map(f -> new File(f, "bin/" + name)).filter(File::isFile).findAny().isPresent()) {
            final File script = new File(workDir, name);
            if (!script.isFile()) {
                try (final FileWriter writer = new FileWriter(script)) {
                    writer.write(content);
                } catch (final IOException e) {
                    throw new IllegalStateException(e);
                }
            }
            ssh.scp(script, targetFolder + "bin/" + script.getName(), new ProgressBar(out, "Uploading script " + script.getName()));
        }
    }

    private static Stream<File> childrenStream(final File foldersToSync) {
        return asList(ofNullable(foldersToSync.listFiles(n -> !n.getName().startsWith("."))).orElse(new File[0])).stream();
    }

    private static void synch(final PrintStream out, final Ssh ssh, final File foldersToSync, final File file, final String targetFolder,
                              final Deployments.Application application, final Deployments.Environment environment) {
        if (file.isDirectory()) {
            childrenStream(file).forEach(f -> synch(out, ssh, foldersToSync, f, targetFolder, application, environment));
        } else if (!"setenv.sh".equals(file.getName())) { // setenv is a particular case handled somewhere else
            String relativeName = file.getAbsolutePath().substring(foldersToSync.getAbsolutePath().length()).replace('\\', '/');
            if (relativeName.startsWith("/")) {
                relativeName = relativeName.substring(1);
            }
            if (relativeName.isEmpty()) {
                return;
            }

            if (relativeName.contains("/")) {
                ssh.exec("mkdir -p \"" + targetFolder + relativeName.substring(0, relativeName.lastIndexOf('/')) + "/\"");
            }
            if (isFilterable(file)) {
                filterAndRewrite(file, application, environment);
            }
            ssh.scp(file, targetFolder + relativeName, new ProgressBar(out, "Uploading " + relativeName));
        }
    }

    private static void filterAndRewrite(final File file, final Deployments.Application application, final Deployments.Environment environment) { // rewrite the file filtered
        try {
            final String content = IO.slurp(file);
            final String newContent = Substitutors.resolveWithVariables(content, environment.getProperties(), application.getProperties());
            if (!newContent.equals(content)) {
                IO.writeString(file, newContent);
            }
        } catch (final IOException e) {
            // no-op: ignore, if there is a real IO issue it will fail during scp
        }
    }

    private static boolean isFilterable(final File file) {
        final String name = file.getName();
        return name.endsWith(".properties") || name.endsWith(".xml") || name.endsWith(".yaml") || name.endsWith(".yml") || name.endsWith(".json")
            || name.endsWith(".sh") || name.endsWith(".config");
    }

    private static String readVersion(final PrintStream out, final PrintStream err,
                                      final Ssh ssh,
                                      final String fixedBase, final String folder,
                                      final AtomicReference<String> currentVersion,
                                      final String name) {
        final List<String> versions = asList(capture(() -> ssh.exec("ls \"" + fixedBase + folder + "/\"")).split("\\n+")).stream()
            .filter(v -> v != null && v.startsWith(name + "-"))
            .map(v -> v.substring(name.length() + 1 /* 1 = '-' length */))
            .collect(toList());
        if (currentVersion.get() != null && !versions.contains(currentVersion.get())) {
            throw new IllegalStateException("You need " + folder + " " + currentVersion.get() + ", please do it on ALL nodes before provisioning this application.");
        }
        if (versions.isEmpty()) {
            throw new IllegalStateException("No " + name + " installed in " + fixedBase + ", please do it before provisioning an application.");
        }

        out.println("You didn't set a " + name + " version, please select one:");
        versions.forEach(v -> out.println("- " + v));

        final Environment environment = Environment.ENVIRONMENT_THREAD_LOCAL.get();
        final boolean isCli = CliEnvironment.class.isInstance(environment);

        String v;
        do {
            try {
                if (!isCli) {
                    out.print("Enter the " + name + " version: ");
                    v = System.console().readLine();
                } else {
                    v = CliEnvironment.class.cast(environment).reader().readLine("Enter the " + name + " version: ");
                }
            } catch (final IOException e) {
                throw new IllegalStateException(e);
            }
            if (!versions.contains(v)) {
                err.println("No version " + v + ", please select another one");
                v = null; // continue
            }
        } while (v == null);
        currentVersion.set(v);
        return v;
    }

    private static void validateEnvironment(final String environment, final String artifactId, final Deployments.Environment env) throws IOException {
        if (env == null) {
            throw new IllegalArgumentException("No environment " + environment + " for '" + artifactId + "' application");
        }
        if (env.getBase() == null) {
            throw new IllegalArgumentException("No base for environment " + environment + " for '" + artifactId + "' application");
        }

        if (ofNullable(env.getHosts()).orElse(emptyList()).isEmpty()) {
            throw new IllegalArgumentException("No host for application " + artifactId);
        }

        env.validate();

        // fill default built-in properties if not present
        if (env.getProperties() == null) {
            env.setProperties(new HashMap<>());
        }

        final Map<String, Map<String, ?>> defaults;
        try (final InputStream stream = findEnvConfig()) {
            defaults = new MapperBuilder().setAccessModeName("field").setSupportsComments(true).build()
                .readObject(
                    stream,
                    new JohnzonParameterizedType(Map.class, String.class, Object.class));
        }
        ofNullable(defaults.get(environment)).ifPresent(def -> def.forEach((k, v) -> env.getProperties().putIfAbsent(k, v.toString())));
        env.getProperties().putIfAbsent("base", env.getBase());
        env.getProperties().putIfAbsent("user", env.getUser());
        env.getProperties().putIfAbsent("artifact", artifactId);
        env.getProperties().putIfAbsent("environment", environment);
    }

    private static InputStream findEnvConfig() {
        return ofNullable(Environment.ENVIRONMENT_THREAD_LOCAL.get().findService(GlobalConfiguration.class))
            .map(c -> c.read("environment.defaults.configuration.file"))
            .map(File::new)
            .filter(File::isFile)
            .map(f -> {
                try {
                    return InputStream.class.cast(new FileInputStream(f));
                } catch (final FileNotFoundException e) {
                    throw new IllegalStateException(e);
                }
            })
            .orElseGet(() -> Thread.currentThread().getContextClassLoader().getResourceAsStream("environment-defaults.json"));
    }

    private static File gitClone(final GitConfiguration git, final String base,
                                 final PrintStream out, final File workDir) {
        final File gitConfig = new File(workDir, base + "-git-config");
        git.clone(gitConfig, new PrintWriter(out));

        // here we suppose application name == artifactId
        final File deploymentConfig = new File(gitConfig, base + "/deployments.json");
        if (!deploymentConfig.isFile()) {
            throw new IllegalStateException("No deployments.json in provisioning repository: " + git.repository());
        }
        return deploymentConfig;
    }

    private static Ssh newSsh(final SshKey sshKey, final String host, final Deployments.Application app, final Deployments.Environment env) {
        return new Ssh(
            // recreate a ssh key using global config
            new com.tomitribe.tsm.ssh.SshKey(sshKey.getPath(), Substitutors.resolveWithVariables(sshKey.getPassphrase())),
            Substitutors.resolveWithVariables(
                ofNullable(env.getUser()).orElse(System.getProperty("user.name")) + '@' + host,
                env.getProperties(),
                app.getProperties()
            ));
    }

    private static void execute(final String environment,
                                final SshKey sshKey,
                                final File workDirBase,
                                final GitConfiguration git,
                                final String artifactId,
                                final PrintStream out,
                                final int nodeIndex,
                                final String textByHost,
                                final String... cmds) throws IOException {
        execute(environment, sshKey, workDirBase, git, artifactId, out, nodeIndex, textByHost, e -> cmds);
    }

    private static void execute(final String environment,
                                final SshKey sshKey,
                                final File workDirBase,
                                final GitConfiguration git,
                                final String artifactId,
                                final PrintStream out,
                                final int nodeIndex,
                                final String textByHost,
                                final Function<Deployments.Environment, String[]> cmdBuilder) throws IOException {
        final File workDir = TempDir.newTempDir(workDirBase, artifactId);

        final File deploymentConfig = gitClone(git, artifactId, out, workDir);

        try (final FileReader reader = new FileReader(deploymentConfig)) {
            final Deployments.Application app = Deployments.read(reader);
            final Deployments.Environment env = app.findEnvironment(environment);
            validateEnvironment(environment, artifactId, env);

            final boolean skipEnvFolder = Boolean.parseBoolean(ofNullable(env.getDeployerProperties().get("skipEnvironmentFolder")).orElse("false"));

            final AtomicInteger currentIdx = new AtomicInteger();
            env.getHosts().forEach(host -> {
                if (nodeIndex >= 0 && currentIdx.getAndIncrement() != nodeIndex) {
                    return;
                }
                out.println(String.format(textByHost, artifactId, host, environment));

                try (final Ssh ssh = newSsh(sshKey, host, app, env)) {
                    final String targetFolder = env.getBase() + (env.getBase().endsWith("/") ? "" : "/") + artifactId + (skipEnvFolder ? "" : ("/" + environment));
                    asList(cmdBuilder.apply(env)).stream().map(c -> String.format(c, targetFolder)).forEach(ssh::exec);
                }
            });
        }
    }
}
