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
import com.tomitribe.tsm.crest.interceptor.LocalExecution;
import com.tomitribe.tsm.crest.interceptor.Notifier;
import com.tomitribe.tsm.file.TempDir;
import com.tomitribe.tsm.ssh.Ssh;
import lombok.NoArgsConstructor;
import org.tomitribe.crest.api.Command;
import org.tomitribe.crest.api.Default;
import org.tomitribe.crest.api.Option;
import org.tomitribe.crest.api.Out;
import org.tomitribe.crest.cli.api.CliEnvironment;
import org.tomitribe.crest.environments.Environment;
import org.tomitribe.util.Duration;
import org.tomitribe.util.Files;
import org.tomitribe.util.IO;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonReaderFactory;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

import static com.tomitribe.tsm.crest.CrestOutputCapture.capture;
import static java.util.Arrays.asList;
import static java.util.Arrays.stream;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.of;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static lombok.AccessLevel.PRIVATE;

@Command("application")
@NoArgsConstructor(access = PRIVATE)
public class Application {
    private static final JsonReaderFactory READER_FACTORY = Json.createReaderFactory(
            Collections.singletonMap("org.apache.johnzon.supports-comments", "true"));

    @Command(interceptedBy = {DefaultParameters.class, Notifier.class, LocalExecution.class})
    public static void start(@Option("environment") final String environment,
                             @Option("ssh.") final SshKey sshKey,
                             @Option("work-dir-base") @Default("${java.io.tmpdir}/tsm") final File workDirBase,
                             @Option("node-index") @Default("-1") final int nodeIndex,
                             @Option("node-grouping-size") @Default("-1") final int nodeGroup,
                             @Option("sub-directory") final String subFolder,
                             final GitConfiguration git,
                             @Notifier.Description final String artifactId,
                             @Out final PrintStream out,
                             final Environment crestEnv) throws IOException {
        execute(
                environment, subFolder, sshKey, workDirBase, git, artifactId, out, nodeIndex, nodeGroup, crestEnv,
                "Starting %s on %s for environment %s", "\"%s/bin/startup\"");
    }

    @Command(interceptedBy = {DefaultParameters.class, Notifier.class, LocalExecution.class})
    public static void exec(@Option("environment") final String environment,
                            @Option("ssh.") final SshKey sshKey,
                            @Option("work-dir-base") @Default("${java.io.tmpdir}/tsm") final File workDirBase,
                            @Option("node-index") @Default("-1") final int nodeIndex,
                            @Option("node-grouping-size") @Default("-1") final int nodeGroup,
                            @Option("sub-directory") final String subFolder,
                            final GitConfiguration git,
                            @Notifier.Description final String artifactId,
                            final String inCmd,
                            @Out final PrintStream out,
                            final Environment crestEnv) throws IOException {
        final String cmd;
        if (inCmd.startsWith("@")) {
            try (final BufferedReader r = new BufferedReader(new FileReader(inCmd.substring(1)))) {
                cmd = r.readLine();
            }
        } else {
            cmd = inCmd;
        }
        execute(
                environment, subFolder, sshKey, workDirBase, git, artifactId, out, nodeIndex, nodeGroup, crestEnv,
                "Executing command for %s on %s for environment %s", cmd);
    }

    @Command(interceptedBy = {DefaultParameters.class, Notifier.class, LocalExecution.class})
    public static void stop(@Option("environment") final String environment,
                            @Option("ssh.") final SshKey sshKey,
                            @Option("work-dir-base") @Default("${java.io.tmpdir}/tsm") final File workDirBase,
                            @Option("node-index") @Default("-1") final int nodeIndex,
                            @Option("node-grouping-size") @Default("-1") final int nodeGroup,
                            @Option("sub-directory") final String subFolder,
                            final GitConfiguration git,
                            @Notifier.Description final String artifactId,
                            @Out final PrintStream out,
                            final Environment crestEnv) throws IOException {
        execute(
                environment, subFolder, sshKey, workDirBase, git, artifactId, out, nodeIndex, nodeGroup, crestEnv,
                "Stopping %s on %s for environment %s", "\"%s/bin/shutdown\" 1200 -force");
    }

    @Command(interceptedBy = {DefaultParameters.class, Notifier.class, LocalExecution.class})
    public static void restart(@Option("environment") final String environment,
                               @Option("ssh.") final SshKey sshKey,
                               @Option("work-dir-base") @Default("${java.io.tmpdir}/tsm") final File workDirBase,
                               @Option("node-index") @Default("-1") final int nodeIndex,
                               @Option("node-grouping-size") @Default("-1") final int nodeGroup,
                               @Option("sub-directory") final String subFolder,
                               final GitConfiguration git,
                               @Notifier.Description final String artifactId,
                               @Out final PrintStream out,
                               final Environment crestEnv) throws IOException {
        execute(
                environment, subFolder, sshKey, workDirBase, git, artifactId, out, nodeIndex, nodeGroup, crestEnv,
                "Restarting %s on %s for environment %s", "\"%s/bin/shutdown\"", "sleep 3", "\"%s/bin/startup\"");
    }

    @Command(interceptedBy = {DefaultParameters.class, LocalExecution.class})
    public static void ping(@Option("environment") final String environment,
                            @Option("ssh.") final SshKey sshKey,
                            @Option("work-dir-base") @Default("${java.io.tmpdir}/tsm") final File workDirBase,
                            @Option("node-index") @Default("-1") final int nodeIndex,
                            @Option("node-grouping-size") @Default("-1") final int nodeGroup,
                            @Option("sub-directory") final String subFolder,
                            final GitConfiguration git,
                            final String artifactId,
                            @Out final PrintStream out,
                            final Environment crestEnv) throws IOException {
        execute(
                environment, subFolder, sshKey, workDirBase, git, artifactId, out, nodeIndex, nodeGroup,
                "Testing %s on %s for environment %s", e -> {
                    final String url = "http://127.0.0.1:" + e.getProperties().get("tsm.https");
                    return new String[]{ // GET is often in PCI zones but not curl so try it first
                            String.format("GET %s 2>&1 | grep -v 'command not found' || curl -v %s", url, url)
                    };
                }, crestEnv);
    }

    @Command(value = "install-tar.gz", interceptedBy = {DefaultParameters.class, Notifier.class, LocalExecution.class})
    public static void installTarGzArtifact(@Option("lib.") final Nexus nexusLib,
                                            @Option("environment") final String inEnvironment,
                                            @Option("ssh.") final SshKey sshKey,
                                            @Option("work-dir-base") @Default("${java.io.tmpdir}/tsm") final File workDirBase,
                                            @Option("sub-directory") final String subFolder,
                                            @Option("restart") @Default("false") final boolean restart,
                                            final GitConfiguration git,
                                            final LocalFileRepository localFileRepository,
                                            final Nexus nexus,
                                            @Notifier.Description final String artifactId, final String coordinates,
                                            @Out final PrintStream out,
                                            final Environment crestEnv) throws IOException {
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

        final AtomicReference<File> clone = new AtomicReference<>(gitClone(git, artifactId, subFolder, out, workDir));
        try (final FileReader reader = new FileReader(clone.get())) {
            final Deployments.Application app = Deployments.read(reader);
            app.findEnvironments(inEnvironment).forEach(contextualEnvironment -> {
                contextualEnvironment.resetEnvironment();
                validateEnvironment(artifactId, contextualEnvironment);
                overrideDefaultProperties(crestEnv, artifactId, contextualEnvironment);
                contextualEnvironment.selfFiltering(app);

                final Deployments.Environment env = contextualEnvironment.getEnvironment();

                final Map<String, Iterator<String>> byHostEntries = ofNullable(env.getByHostProperties()).orElse(emptyMap())
                        .entrySet().stream().collect(toMap(Map.Entry::getKey, e -> e.getValue().iterator()));

                env.getHosts().forEach(host -> {
                    out.println("Installing " + segments[1] + " to " + host);
                    env.getProperties().put("host", host);

                    byHostEntries.forEach((k, v) -> env.getProperties().put(k, v.next()));

                    final Map<String, File> libs = ofNullable(env.getCustomLibs()).orElse(emptyMap()).entrySet().stream()
                            .collect(toMap(Map.Entry::getKey, e -> findLib(nexus, nexusLib, localFileRepository, out, workDir, e.getValue())));

                    final String fixedBase = env.getBase() + (env.getBase().endsWith("/") ? "" : "/");
                    final String remoteWorkDir = fixedBase + "work-provisioning/";
                    final String target = remoteWorkDir + tarGz.getName();
                    final String targetFolder = fixedBase + segments[1] + "/" + segments[1] + '-' + segments[2] + '/';
                    final String additionalBase = fixedBase + artifactId + '/' + contextualEnvironment.getName() + '/';
                    try (final Ssh ssh = newSsh(sshKey, host, app, env)) {
                        ssh.exec(String.format("mkdir -p \"%s\" \"%s\"", remoteWorkDir, targetFolder))
                                .scp(tarGz, target, new ProgressBar(out, "Installing " + segments[1] + " on " + host))
                                .exec(String.format("tar xvf \"%s\" -C \"%s\" --strip 1", target, targetFolder));
                        libs.forEach((lib, file) -> {
                            final String finalFilePath = additionalBase + lib;
                            final int dirSep = finalFilePath.lastIndexOf('/');
                            if (dirSep > 0) {
                                ssh.exec("mkdir -p \"" + finalFilePath.substring(0, dirSep) + "\"");
                            }
                            ssh.scp(file, finalFilePath, new ProgressBar(out, "Uploading " + file.getName()));
                        });

                        final List<File> foldersToSyncs = new LinkedList<>();
                        final String envFolder = envFolder(env, contextualEnvironment.getName());
                        final List<String> configFolders = asList(artifactId, artifactId + "-" + envFolder);
                        configFolders.forEach(folder -> {
                            final File foldersToSync = new File(clone.get().getParentFile(), folder);
                            if (foldersToSync.isDirectory()) {
                                out.println("Synchronizing " + foldersToSync.getName() + " folders");
                                synch(out, ssh, foldersToSync, foldersToSync, additionalBase, app, env);
                                foldersToSyncs.add(foldersToSync);
                            } else {
                                out.println("No '" + folder + "' configuration folder found.");
                            }
                        });
                        Collections.reverse(foldersToSyncs);
                        final List<String> files = foldersToSyncs.stream().map(f -> new File(f, "bin"))
                                .filter(File::isDirectory)
                                .flatMap(f -> Stream.of(ofNullable(f.listFiles()).orElse(new File[0])))
                                .map(f -> f.getParentFile().getName() + '/' + f.getName())
                                .collect(toList());
                        Stream.of("bin/startup", "bin/shutdown").filter(files::contains).forEach(k -> ssh.exec(String.format("chmod +x \"%s/%s\"", additionalBase, k)));
                        ssh.exec(String.format("rm \"%s\"", target));

                        final File root = clone.get().getParentFile().getParentFile();
                        final File potentialCopy = git.reset(root, out);
                        if (potentialCopy != root) {
                            clone.set(new File(potentialCopy, artifactId + ofNullable(subFolder).map(s -> "/" + s).orElse("") + "/deployments.json"));
                        }

                        if (!restart) {
                            out.println(segments[1] + " setup in " + targetFolder + " for host " + host);
                        } else {
                            out.println("Restarting " + targetFolder + " for host " + host);
                            ssh.exec("\"" + targetFolder + "bin/startup\"");
                        }
                    }
                });
            });
        } finally {
            try {
                Files.remove(workDir);
            } catch (final IllegalStateException ise) {
                // ok
            }
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

    @Command(value = "update-config", interceptedBy = {DefaultParameters.class, Notifier.class, LocalExecution.class})
    public static void updateConfig(final GitConfiguration git,
                                    final LocalFileRepository localFileRepository,
                                    @Option("ssh.") final SshKey sshKey,
                                    @Option("work-dir-base") @Default("${java.io.tmpdir}/tsm") final File workDirBase,
                                    @Option("tomee-version") final String tomeeVersion,
                                    @Option("tribestream-version") final String tribestreamVersion,
                                    @Option("java-version") final String javaVersion,
                                    @Option("environment") final String environment,
                                    @Notifier.Description final String artifactId,
                                    @Option("node-index") @Default("-1") final int nodeIndex,
                                    @Option("node-grouping-size") @Default("-1") final int nodeGroup,
                                    @Option("pause-between-deployments") @Default("-1 minutes") final Duration pause, // httpd uses 60s by default
                                    @Option("as-service") final boolean asService,
                                    @Option("restart") @Default("false") final boolean restart,
                                    @Option("sub-directory") final String subFolder,
                                    @Out final PrintStream out,
                                    @Out final PrintStream err,
                                    final Environment crestEnv,
                                    final GlobalConfiguration configuration) throws IOException {
        install(
                null, null, git, localFileRepository, sshKey, workDirBase, tomeeVersion, tribestreamVersion, javaVersion, environment,
                null, artifactId, null, // see install() for details
                nodeIndex, nodeGroup, pause, asService, restart, subFolder, out, err, crestEnv, configuration);
    }

    private static File download(final Nexus nexus, final LocalFileRepository localFileRepository,
                                 final PrintStream out, final String appWorkName,
                                 final File workDir,
                                 final String artifactId, final String groupId, final String version, final String type) {
        if (nexus != null) {
            if (groupId == null && version == null) {
                out.println("configuration only, skipping artifacts");
                return null;
            }

            final File downloadedFile = new File(workDir, appWorkName + "_" + version + "." + type);
            if (!downloadedFile.isFile()) {
                final File cacheFile = localFileRepository.find(groupId, artifactId, version, null, type);
                if (cacheFile.isFile()) {
                    out.println("Using locally cached " + artifactId + '.');
                    try {
                        IO.copy(cacheFile, downloadedFile);
                    } catch (final IOException e) {
                        throw new IllegalStateException(e);
                    }
                } else {
                    out.println("Didn't find cached " + artifactId + " in " + cacheFile + " so trying to download it for this provisioning.");
                    nexus.download(out, groupId, artifactId, version, null, type).to(downloadedFile);
                }
            }
            return downloadedFile;
        }
        return null;
    }

    @Command(value = "config-only", interceptedBy = {DefaultParameters.class, Notifier.class, LocalExecution.class})
    public static void installConfigOnly(final Nexus nexus, // likely our apps
                                         @Option("lib.") final Nexus nexusLib, // likely central proxy
                                         final GitConfiguration git,
                                         final LocalFileRepository localFileRepository,
                                         @Option("ssh.") final SshKey sshKey,
                                         @Option("work-dir-base") @Default("${java.io.tmpdir}/tsm") final File workDirBase,
                                         @Option("tomee-version") final String tomeeVersion,
                                         @Option("tribestream-version") final String tribestreamVersion,
                                         @Option("java-version") final String javaVersion,
                                         @Option("environment") final String environment,
                                         @Notifier.Description final String artifactId, // ie application in git
                                         @Option("node-index") @Default("-1") final int nodeIndex,
                                         @Option("node-grouping-size") @Default("-1") final int nodeGroup,
                                         @Option("pause-between-deployments") @Default("-1 minutes") final Duration pause, // httpd uses 60s by default
                                         @Option("as-service") final boolean asService,
                                         @Option("restart") @Default("false") final boolean restart,
                                         @Option("sub-directory") final String subFolder,
                                         @Out final PrintStream out,
                                         @Out final PrintStream err,
                                         final Environment crestEnv,
                                         final GlobalConfiguration configuration) throws IOException {
        install(
                nexus, nexusLib, git, localFileRepository, sshKey, workDirBase, tomeeVersion, tribestreamVersion, javaVersion, environment,
                null, artifactId, null, // see install() for details
                nodeIndex, nodeGroup, pause, asService, restart, subFolder, out, err, crestEnv, configuration);
    }

    // same as install but without groupId/artifactId (read from deployments.json)
    // behavior wise it is an alias for config-only
    @Command(value = "quick-install", interceptedBy = {DefaultParameters.class, Notifier.class, LocalExecution.class})
    public static void quickInstall(final Nexus nexus, // likely our apps
                                    @Option("lib.") final Nexus nexusLib, // likely central proxy
                                    final GitConfiguration git,
                                    final LocalFileRepository localFileRepository,
                                    @Option("ssh.") final SshKey sshKey,
                                    @Option("work-dir-base") @Default("${java.io.tmpdir}/tsm") final File workDirBase,
                                    @Option("tomee-version") final String tomeeVersion,
                                    @Option("tribestream-version") final String tribestreamVersion,
                                    @Option("java-version") final String javaVersion,
                                    @Option("environment") final String environment,
                                    @Notifier.Description final String artifactId, // ie application in git
                                    @Option("node-index") @Default("-1") final int nodeIndex,
                                    @Option("node-grouping-size") @Default("-1") final int nodeGroup,
                                    @Option("pause-between-deployments") @Default("-1 minutes") final Duration pause,
                                    @Option("as-service") final boolean asService,
                                    @Option("restart") @Default("false") final boolean restart,
                                    @Option("sub-directory") final String subFolder,
                                    @Out final PrintStream out,
                                    @Out final PrintStream err,
                                    final Environment crestEnv,
                                    final GlobalConfiguration configuration) throws IOException {
        install(
                nexus, nexusLib, git, localFileRepository, sshKey, workDirBase, tomeeVersion, tribestreamVersion, javaVersion, environment,
                null, artifactId, null, // see install() for details
                nodeIndex, nodeGroup, pause, asService, restart, subFolder, out, err, crestEnv, configuration);
    }

    // meta command reading tsm-metadata.json to set git, server, env, app and java config
    @Command(interceptedBy = {DefaultParameters.class, Notifier.class, LocalExecution.class}, value = "install-from-metadata",
            usage = "application install-from-metadata my-tsm-metadata.json")
    public static void installFromMetadata(final Nexus nexus,
                                           @Option("lib.") final Nexus nexusLib,
                                           final GitConfiguration git,
                                           final LocalFileRepository localFileRepository,
                                           @Option("ssh.") final SshKey sshKey,
                                           @Option("work-dir-base") @Default("${java.io.tmpdir}/tsm") final File workDirBase,
                                           @Option("node-index") @Default("-1") final int nodeIndex,
                                           @Option("node-grouping-size") @Default("-1") final int nodeGroup,
                                           @Option("pause-between-deployments") @Default("-1 minutes") final Duration pause,
                                           @Option("restart") @Default("false") final boolean restart,
                                           @Option("sub-directory") final String subFolder,
                                           @Out final PrintStream out,
                                           @Out final PrintStream err,
                                           @Notifier.Description final File tsmMetadata,
                                           final Environment crestEnv,
                                           final GlobalConfiguration configuration) throws IOException {
        final String tomee;
        final String tribestream;
        final String java;
        final String groupId;
        final String artifactId;
        final String version;
        final String environment;
        try (final JsonReader reader = READER_FACTORY.createReader(new FileInputStream(tsmMetadata))) {
            final JsonObject root = reader.readObject();
            final JsonObject applicationConfig = requireNonNull(root.getJsonObject("application"), "application configuration missing");
            final JsonObject serverConfig = requireNonNull(root.getJsonObject("server"), "server configuration missing");
            final JsonObject gitConfig = requireNonNull(root.getJsonObject("git"), "git configuration missing");
            final JsonObject javaConfig = requireNonNull(root.getJsonObject("java"), "java configuration missing");

            // environment
            environment = requireNonNull(root.getString("environment"), "environment configuration missing");

            { // git
                final String revision = gitConfig.getString("revision");
                if (revision != null && !"LAST".equals(revision)) {
                    git.setRevision(revision);
                }
                git.setBranch(requireNonNull(gitConfig.getString("branch"), "no git revision"));
            }
            { // server
                final String name = serverConfig.getString("name");
                if (name.startsWith("tribestream-")) {
                    tomee = null;
                    tribestream = name.substring("tribestream-".length());
                } else {
                    tribestream = null;
                    tomee = name.substring("apache-tomee-".length());
                }
            }
            { // java
                java = javaConfig.getString("version").replace("jdk-", "");
            }
            { // app
                groupId = requireNonNull(applicationConfig.getString("groupId"), "groupId missing");
                artifactId = requireNonNull(applicationConfig.getString("artifactId"), "artifactId missing");
                version = requireNonNull(applicationConfig.getString("version"), "version missing");
            }
        }

        install(
                nexus, nexusLib, git, localFileRepository, sshKey, workDirBase,
                tomee, tribestream, java, environment, groupId, artifactId, version,
                nodeIndex, nodeGroup, pause, false, restart, subFolder, out, err, crestEnv, configuration);
    }

    // this is the master logic for all deployments (application, config only etc...)
    @Command(interceptedBy = {DefaultParameters.class, Notifier.class, LocalExecution.class})
    public static void install(final Nexus nexus, // likely for our apps
                               @Option("lib.") final Nexus nexusLib, // likely central proxy for webapps and libraries
                               final GitConfiguration git,
                               final LocalFileRepository localFileRepository,
                               @Option("ssh.") final SshKey sshKey,
                               @Option("work-dir-base") @Default("${java.io.tmpdir}/tsm") final File workDirBase,
                               @Option("tomee-version") final String tomeeVersion,
                               @Option("tribestream-version") final String tribestreamVersion,
                               @Option("java-version") final String javaVersion,
                               @Option("environment") final String inEnvironment,
                               final String inGroupId, @Notifier.Description final String inArtifactId, final String inVersion,
                               @Option("node-index") @Default("-1") final int nodeIndex,
                               @Option("node-grouping-size") @Default("-1") final int nodeGroup,
                               @Option("pause-between-deployments") @Default("-1 minutes") final Duration pause, // httpd uses 60s by default
                               @Option("as-service") final boolean asService,
                               @Option("restart") @Default("false") final boolean restart,
                               @Option("sub-directory") final String subFolder,
                               @Out final PrintStream out,
                               @Out final PrintStream err,
                               final Environment crestEnv,
                               final GlobalConfiguration configuration) throws IOException {
        if (tomeeVersion != null && tribestreamVersion != null) {
            throw new IllegalArgumentException("Only use either --tomee-version or --tribestream-version");
        }

        final String appWorkName = ofNullable(inGroupId).orElse("-") + '_' + inArtifactId;
        final File workDir = TempDir.newTempDir(workDirBase, appWorkName);

        final AtomicReference<File> deploymentConfig = new AtomicReference<>(gitClone(git, inArtifactId, subFolder, out, workDir));

        try (final FileReader reader = new FileReader(deploymentConfig.get())) {
            final Deployments.Application app = Deployments.read(reader);
            final AtomicBoolean reInitFiltering = new AtomicBoolean();
            app.findEnvironments(inEnvironment).forEach(contextualEnvironment -> {
                contextualEnvironment.resetEnvironment();
                validateEnvironment(inArtifactId, contextualEnvironment);
                overrideDefaultProperties(crestEnv, inArtifactId, contextualEnvironment);
                contextualEnvironment.selfFiltering(app);

                final String envName = contextualEnvironment.getName();
                final Deployments.Environment env = contextualEnvironment.getEnvironment();

                final String artifactId = ofNullable(env.getDeployerProperties().get("artifactId")).orElse(inArtifactId);
                final boolean skipEnvFolder = Boolean.parseBoolean(ofNullable(env.getDeployerProperties().get("skipEnvironmentFolder")).orElse("false"));

                final String groupId = ofNullable(inGroupId).orElse(env.getGroupId());
                final String version = ofNullable(inVersion).orElse(env.getVersion());

                final File downloadedFile = download(nexus, localFileRepository, out, appWorkName, workDir, artifactId, groupId, version, "war");

                final Collection<File> additionalLibs = new LinkedList<>();
                final Map<String, File> additionalCustomLibs = new TreeMap<>();
                final Map<String, File> additionalWebapps = new TreeMap<>();
                final Map<String, File> additionalApps = new TreeMap<>();
                final AtomicReference<String[]> singleWebapp = new AtomicReference<>(); // reused if we have a single webapp, don't parse N times
                if (nexusLib != null) {
                    ofNullable(env.getLibs()).orElse(emptyList())
                            .forEach(lib -> additionalLibs.add(findLib(nexus, nexusLib, localFileRepository, out, workDir, lib)));
                    ofNullable(env.getCustomLibs()).orElse(emptyMap())
                            .forEach((target, coords) -> additionalCustomLibs.put(target, findLib(nexus, nexusLib, localFileRepository, out, workDir, coords)));
                    ofNullable(env.getWebapps()).orElse(emptyList()).forEach(war -> {
                        final String[] segments = war.replaceAll("\\?.*", "").split(":");
                        final int contextIdx = war.indexOf("?context=");

                        final File local = new File(workDir, segments[1] + "-" + segments[2] +
                                ofNullable(segments.length >= 5 ? segments[4] : null).map(c -> '-' + c).orElse("") + ".war");
                        if (!local.isFile()) {
                            final String aType = segments.length >= 4 ? segments[3] : "war";
                            final String aClassifier = segments.length >= 5 ? segments[4] : null;
                            doDownload(nexus, nexusLib, localFileRepository, out, local, segments[0], segments[1], segments[2], aType, aClassifier);
                        }
                        singleWebapp.set(segments);

                        final String context = contextIdx > 0 ? war.substring(contextIdx + "?context=".length()) : segments[1];
                        additionalWebapps.put(context, local);
                    });
                    if (additionalWebapps.size() > 1) {
                        singleWebapp.set(null);
                    }

                    ofNullable(env.getApps()).orElse(emptyList()).forEach(a -> {
                        final String[] segments = a.replaceAll("\\?.*", "").split(":");
                        final int nameIdx = a.indexOf("?rename=");

                        final File local = new File(workDir, segments[1] + "-" + segments[2] +
                                ofNullable(segments.length >= 5 ? segments[4] : null).map(c -> '-' + c).orElse("") + segments[3]);
                        if (!local.isFile()) {
                            final String aClassifier = segments.length >= 5 ? segments[4] : null;
                            // ear? rar? jar? we don't know so we don't guess like we do for wars.
                            doDownload(nexus, nexusLib, localFileRepository, out, local, segments[0], segments[1], segments[2], segments[3], aClassifier);
                        }

                        final String name = (nameIdx > 0 ? a.substring(nameIdx + "?rename=".length()) : segments[1]) + "." + segments[3];
                        additionalApps.put(name, local);
                    });
                }

                final AtomicReference<String> chosenServerVersion = new AtomicReference<>(
                        tribestreamVersion != null ? "tribestream-" + tribestreamVersion :
                                (tomeeVersion != null ? "apache-tomee-" + tomeeVersion : null));

                final AtomicReference<String> chosenJavaVersion = new AtomicReference<>(javaVersion == null ? null : "jdk-" + javaVersion);

                final Map<String, Iterator<String>> byHostEntries = ofNullable(env.getByHostProperties()).orElse(emptyMap())
                        .entrySet().stream().collect(toMap(Map.Entry::getKey, e -> e.getValue().iterator()));
                final AtomicInteger currentIdx = new AtomicInteger();
                reInitFiltering.set(true);
                final NodeSelector selector = new NodeSelector(nodeIndex, nodeGroup);
                env.getHosts().forEach(host -> {
                    out.println("Deploying " + artifactId + " on " + host);

                    // override by host variables
                    byHostEntries.forEach((k, v) -> env.getProperties().put(k, v.next()));
                    env.getProperties().put("host", host);

                    if (nodeIndex >= 0 && !selector.isSelected(currentIdx.getAndIncrement())) {
                        return;
                    } // else deploy

                    final String fixedBase = env.getBase() + (env.getBase().endsWith("/") ? "" : "/");
                    final String targetFolder = fixedBase + artifactId + "/" + (skipEnvFolder ? "" : (envName + '/'));
                    final String serverShutdownCmd = targetFolder + "bin/shutdown";
                    final String javaBase = env.getDeployerProperties().getOrDefault("java.base", fixedBase + "java/");
                    final String serverBase = env.getDeployerProperties().getOrDefault("server.base", fixedBase);

                    try (final Ssh ssh = newSsh(sshKey, host, app, env)) {
                        // get tribestream version or just ask the user for it listing the ones the server has
                        ofNullable(chosenServerVersion.get())
                                .orElseGet(() -> readVersion(out, err, ssh, serverBase, true, chosenServerVersion, "tribestream", "apache-tomee"));
                        ofNullable(chosenJavaVersion.get())
                                .orElseGet(() -> readVersion(out, err, ssh, javaBase, false, chosenJavaVersion, "jdk"));

                        // shutdown if running
                        ssh.exec("[ -f \"" + serverShutdownCmd + "\" ] && \"" + serverShutdownCmd + "\" 1200 -force");
                        if (nexus != null) {
                            // recreate the base folder if needed, if nexus == null we redeploy only the (git) config so dont delete artifacts
                            ssh.exec(String.format("rm -Rf \"%s\"", targetFolder));
                        }
                        // create the structure if needed
                        ssh.exec(String.format("mkdir -p \"%s\"", targetFolder))
                                // create app structure
                                .exec("cd \"" + targetFolder + "\" && for i in " +
                                        (env.getApps().isEmpty() ? "" : "apps ") +
                                        "bin conf lib logs temp webapps work; do mkdir -p $i; done");

                        if (downloadedFile != null) {
                            ssh.scp(
                                    downloadedFile,
                                    targetFolder + "webapps/" + env.getDeployerProperties().getOrDefault("application.context", artifactId) + ".war",
                                    new ProgressBar(out, "Uploading " + artifactId + " on " + host));
                        }

                        // uploading libs
                        additionalLibs.forEach(lib -> ssh.scp(lib, targetFolder + "lib/" + lib.getName(), new ProgressBar(out, "Uploading " + lib.getName())));
                        additionalCustomLibs.forEach((target, file) -> {
                            final String finalFilePath = targetFolder + target;
                            final int dirSep = finalFilePath.lastIndexOf('/');
                            if (dirSep > 0) {
                                ssh.exec("mkdir -p \"" + finalFilePath.substring(0, dirSep) + "\"");
                            }
                            ssh.scp(file, finalFilePath, new ProgressBar(out, "Uploading " + file.getName()));
                        });
                        additionalWebapps.forEach((name, war) -> ssh.scp(war, targetFolder + "webapps/" + name + ".war", new ProgressBar(out, "Uploading " + war.getName())));
                        additionalApps.forEach((name, ar) -> ssh.scp(ar, targetFolder + "apps/" + name, new ProgressBar(out, "Uploading " + ar.getName())));

                        // synchronizing configuration
                        final List<File> foldersToSyncs = new LinkedList<>();
                        final String envFolder = envFolder(env, envName);
                        final List<String> configFolders = asList("tomee", "tomee-" + envFolder, "tribestream", "tribestream-" + envFolder);
                        final AtomicBoolean hasTomEEXml = new AtomicBoolean(false);
                        configFolders.forEach(folder -> {
                            final File foldersToSync = new File(deploymentConfig.get().getParentFile(), folder);
                            if (foldersToSync.isDirectory()) {
                                out.println("Synchronizing " + foldersToSync.getName() + " folders");
                                synch(out, ssh, foldersToSync, foldersToSync, targetFolder, app, env);
                                foldersToSyncs.add(foldersToSync);
                            } else {
                                out.println("No '" + folder + "' configuration folder found.");
                            }
                            if (!hasTomEEXml.get()) {
                                hasTomEEXml.set(foldersToSyncs.stream().map(f -> new File(f, "conf/tomee.xml")).anyMatch(File::isFile));
                            }
                        });

                        if (!hasTomEEXml.get() && !env.getApps().isEmpty()) { // create one - note we ignore the system.properties declaration for now
                            final File tmp = new File(workDir, "tomee-apps.xml");
                            try {
                                IO.writeString(tmp,
                                        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                                                "<tomee>\n" +
                                                "  <Deployments dir=\"apps\" />\n" +
                                                "</tomee>\n" +
                                                "\n");
                            } catch (final IOException e) {
                                throw new IllegalStateException(e);
                            }
                            ssh.scp(tmp, targetFolder + "conf/tomee.xml", new ProgressBar(out, "Uploading tomee.xml (for apps/)"));
                        }

                        Collections.reverse(foldersToSyncs);

                        final String serverFolder = chosenServerVersion.get().startsWith("apache-tomee") ? "apache-tomee" :
                                (chosenServerVersion.get().startsWith("tribestream-access-gateway") ? "tribestream-access-gateway" : "tribestream");
                        final String envrt =
                                "export JAVA_HOME=\"" + javaBase + chosenJavaVersion.get() + "\"\n" +
                                        "export CATALINA_HOME=\"" + serverBase + serverFolder + "/" + chosenServerVersion.get() + "\"\n" +
                                        "export CATALINA_BASE=\"" + targetFolder + "\"\n" +
                                        ofNullable(env.getClasspath()).map(cp -> "export CLASSPATH=\"" + cp + "\"\n").orElse("") + // set the classpath after CATALINA_ to let reuse them
                                        "export CATALINA_PID=\"" + targetFolder + "work/" + serverFolder.replace("apache-", "") + ".pid" + "\"\n";

                        {   // setenv needs some more love to get a proper env setup
                            final File setEnv = new File(workDir, "setenv.sh");
                            if (reInitFiltering.get() || !setEnv.isFile()) {
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
                                            final String substituted = Substitutors.resolveWithVariables(line, env.getProperties(), app.getProperties());
                                            content.append(substituted).append('\n');
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
                                reInitFiltering.get(),
                                out, ssh, foldersToSyncs, workDir, targetFolder, "processes",
                                scriptTop + "ps aux | grep \"$proc_script_base\" | grep -v grep\n\n");
                        addScript(
                                reInitFiltering.get(),
                                out, ssh, foldersToSyncs, workDir, targetFolder, "startup",
                                scriptTop +
                                        "[ -f \"$proc_script_base/bin/pre_startup.sh\" ] && \"$proc_script_base/bin/pre_startup.sh\"\n" +
                                        "nohup \"$CATALINA_HOME/bin/startup.sh\" \"$@\" > $proc_script_base/logs/nohup.log &\n" +
                                        "[ -f \"$proc_script_base/bin/post_startup.sh\" ] && \"$proc_script_base/bin/post_startup.sh\"\n" +
                                        "\n");
                        addScript(
                                reInitFiltering.get(),
                                out, ssh, foldersToSyncs, workDir, targetFolder, "shutdown",
                                scriptTop +
                                        "[ -f \"$proc_script_base/bin/pre_shutdown.sh\" ] && \"$proc_script_base/bin/pre_shutdown.sh\"\n" +
                                        "\"$CATALINA_HOME/bin/shutdown.sh\" \"$@\"\n" +
                                        "[ -f \"$proc_script_base/bin/post_shutdown.sh\" ] && \"$proc_script_base/bin/post_shutdown.sh\"\n" +
                                        "\n");
                        addScript(
                                reInitFiltering.get(),
                                out, ssh, foldersToSyncs, workDir, targetFolder, "run",
                                scriptTop +
                                        "[ -f \"$proc_script_base/bin/pre_startup.sh\" ] && \"$proc_script_base/bin/pre_startup.sh\"\n" +
                                        "\"$CATALINA_HOME/bin/catalina.sh\" \"run\" \"$@\"\n\n"); // no post_startup since run is blocking
                        addScript(
                                reInitFiltering.get(),
                                out, ssh, foldersToSyncs, workDir, targetFolder, "restart",
                                scriptTop + "\"$proc_script_base/bin/shutdown\" && sleep 3 && \"$proc_script_base/bin/startup\"\n\n");

                        {   // just to be able to know what we did and when when browsing manually the installation, we could add much more if needed
                            final File metadata = new File(workDir, "tsm-metadata.json"); // sample usage in com.sbux.pos.basket.configuration.aspconfig.TsmPropertiesProvider

                            // if using config-only to install a webapp, extract metadata from the only webapp
                            String metaGroup = groupId;
                            String metaArtifact = artifactId;
                            String metaVersion = version;
                            if (downloadedFile == null && additionalWebapps.size() == 1) {
                                metaGroup = singleWebapp.get()[0];
                                metaArtifact = singleWebapp.get()[1];
                                metaVersion = singleWebapp.get()[2];
                            }

                            try (final FileWriter writer = new FileWriter(metadata)) {
                                writer.write("{\n");
                                writer.write("  \"date\":\"" + LocalDateTime.now().toString() + "\",\n");
                                writer.write("  \"host\":\"" + host + "\",\n");
                                writer.write("  \"environment\":\"" + envName + "\",\n");
                                writer.write("  \"application\":{\n");
                                writer.write("    \"groupId\":\"" + ofNullable(metaGroup).orElse("") + "\",\n");
                                writer.write("    \"artifactId\":\"" + artifactId + "\",\n"); // tsm one for compatibility and often the same
                                writer.write("    \"version\":\"" + ofNullable(metaVersion).orElse("") + "\",\n");
                                writer.write("    \"originalArtifact\":\"" + metaArtifact + "\"\n");
                                writer.write("  },\n");
                                writer.write("  \"git\":{\n");
                                writer.write("    \"branch\":\"" + git.getBranch() + "\",\n");
                                writer.write("    \"revision\":\"" + git.getRevision() + "\"\n");
                                writer.write("  },\n");
                                writer.write("  \"server\":{\n");
                                writer.write("    \"name\":\"" + chosenServerVersion.get() + "\"\n");
                                writer.write("  },\n");
                                writer.write("  \"java\":{\n");
                                writer.write("    \"version\":\"" + chosenJavaVersion.get().replace("jdk-", "") + "\"\n");
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
                            if (reInitFiltering.get() || !initD.isFile()) {
                                try (final FileWriter writer = new FileWriter(initD)) {
                                    writer.write("" +
                                            "# chkconfig: 345 99 01\n" + // <levels> <start> <stop>
                                            "# description: handles " + artifactId + "\n" +
                                            "\n" + envrt + "\n\n" +
                                            "sudo -E -u " + env.getUser() + " $CATALINA_HOME/bin/catalina.sh $*" +
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

                        final File root = deploymentConfig.get().getParentFile().getParentFile();
                        final File potentialCopy = git.reset(root, out);
                        if (potentialCopy != root) { // reset failed, the repo has been checked out again
                            deploymentConfig.set(new File(potentialCopy, inArtifactId + ofNullable(subFolder).map(s -> "/" + s).orElse("") + "/deployments.json"));
                        }
                        reInitFiltering.set(false);

                        if (pause.getTime() > 0 && (nodeGroup < 0 || (currentIdx.get() % nodeGroup) == 0)) {
                            try {
                                Thread.sleep(pause.getUnit().toMillis(pause.getTime()));
                            } catch (final InterruptedException e) {
                                Thread.interrupted();
                            }
                        }
                    }
                });
            });
        } finally {
            try {
                Files.remove(workDir);
            } catch (final IllegalStateException ise) {
                // can't delete, pby locked on windows. Since deployments should be done in a temp repo it is ok
            }
        }
    }

    private static void doDownload(final Nexus nexus, final Nexus nexusLib,
                                   final LocalFileRepository localFileRepository,
                                   final PrintStream out, final File local,
                                   final String gId, final String aId, final String aVersion, final String aType, final String aClassifier) {
        try {
            final File m2Local = localFileRepository.find(gId, aId, aVersion, aClassifier, aType);
            if (m2Local.isFile()) {
                try {
                    IO.copy(m2Local, local);
                } catch (final IOException e) {
                    nexusLib.download(out, gId, aId, aVersion, aClassifier, aType).to(local);
                }
            } else {
                nexusLib.download(out, gId, aId, aVersion, aClassifier, aType).to(local);
            }
        } catch (final IllegalStateException ise) {
            if (nexus != null) {
                nexus.download(out, gId, aId, aVersion, aClassifier, aType).to(local);
            }
        }
    }

    private static File findLib(final Nexus nexus, final Nexus nexusLib, final LocalFileRepository fileRepository,
                                final PrintStream out, final File workDir, final String lib) {
        final String[] segments = lib.split(":");
        final File local = new File(workDir, segments[1] + (segments.length >= 5 ? '-' + segments[4] : "") + ".jar");
        if (!local.isFile()) {
            doDownload(nexus, nexusLib, fileRepository, out, local, segments[0], segments[1], segments[2],
                    segments.length >= 4 ? segments[3] : "jar", segments.length >= 5 ? segments[4] : null);
        }
        return local;
    }

    private static String envFolder(final Deployments.Environment environment, final String def) {
        return ofNullable(environment.getProperties().get("tsm.tribestream.folder"))
                .orElseGet(() -> environment.getDeployerProperties().getOrDefault("tribestream.folder", def));
    }

    private static void addScript(final boolean forceInit,
                                  final PrintStream out, final Ssh ssh, final Collection<File> foldersToSync, final File workDir,
                                  final String targetFolder, final String name, final String content) {
        if (!foldersToSync.stream().map(f -> new File(f, "bin/" + name)).filter(File::isFile).findAny().isPresent()) {
            final File script = new File(workDir, name);
            if (forceInit || !script.isFile()) {
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
        return stream(ofNullable(foldersToSync.listFiles(n -> !n.getName().startsWith("."))).orElse(new File[0]));
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
            String newContent = Substitutors.resolveWithVariables(content, environment.getProperties(), application.getProperties());

            // if we need to deploy apps ensure apps folder is deployed
            if ("tomee.xml".equals(file.getName()) && !environment.getApps().isEmpty() && (!newContent.contains("<Deployments") || !newContent.contains("dir=\"apps\""))) {
                newContent = newContent.replace("</tomee>", "") + "\n<Deployments dir=\"apps\" />\n</tomee>\n";
            }

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
                                      final String fixedBase,
                                      final boolean useNames, // if null then use current name
                                      final AtomicReference<String> currentVersion,
                                      final String... name) {
        final List<String> names = asList(name);
        final Map<String, List<String>> versionByName = new HashMap<>();
        names.forEach(server -> of(Arrays.stream(capture(() -> ssh.exec("ls \"" + fixedBase + (useNames ? server + "/\"" : ""))).split("\\n+"))
                .filter(v -> v != null && v.startsWith(server + '-'))
                .map(v -> v.substring(server.length() + 1 /* 1 = '-' length */))
                .collect(toList()))
                .filter(v -> !v.isEmpty())
                .ifPresent(versions -> versionByName.put(server, versions)));

        if (currentVersion.get() != null &&
                !versionByName.entrySet().stream()
                        .flatMap(e -> e.getValue().stream().map(v -> e.getKey() + "-" + v))
                        .filter(currentVersion.get()::equals)
                        .findAny().isPresent()) {
            throw new IllegalStateException(
                    "You need " + currentVersion.get() + ", please do it on ALL nodes before provisioning this application." +
                            "Found: " + versionByName);
        }
        if (!versionByName.values().stream().flatMap(Collection::stream).findAny().isPresent()) {
            throw new IllegalStateException("No " + names + " installed in " + fixedBase + ", please do it before provisioning an application.");
        }

        if (names.isEmpty()) {
            throw new IllegalStateException("No server " + names + " found.");
        }

        final Environment environment = Environment.ENVIRONMENT_THREAD_LOCAL.get();
        final boolean isCli = CliEnvironment.class.isInstance(environment);

        String selectedName;
        if (versionByName.size() == 1) {
            selectedName = versionByName.keySet().iterator().next();
        } else {
            out.println("Select a server:");
            versionByName.keySet().forEach(n -> out.println("- " + n));

            String server;
            do {
                server = readString(out, environment, isCli, "Selected server: ");
                if (!versionByName.containsKey(server)) {
                    err.println("No server " + server + ", please select another one");
                    server = null;
                }
            } while (server == null);
            selectedName = server;
        }

        final List<String> potentialVersions = versionByName.get(selectedName);
        out.println("Select a " + selectedName + " version:");
        potentialVersions.forEach(v -> out.println("- " + v));

        String v;
        do {
            v = readString(out, environment, isCli, "Selected " + selectedName + " version: ");
            if (!potentialVersions.contains(v)) {
                err.println("No version " + v + ", please select another one");
                v = null;
            }
        } while (v == null);

        currentVersion.set(selectedName + "-" + v);
        return v;
    }

    private static String readString(final PrintStream out, final Environment environment, final boolean isCli, final String text) {
        String server;
        try {
            if (!isCli) {
                out.print(text);
                server = System.console().readLine();
            } else {
                server = CliEnvironment.class.cast(environment).reader().readLine(text);
            }
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
        return server;
    }

    private static void validateEnvironment(final String artifactId, final Deployments.ContextualEnvironment contextualEnvironment) {
        final Deployments.Environment env = contextualEnvironment.getEnvironment();
        if (env.getBase() == null) {
            throw new IllegalArgumentException("No base for environment " + env.getNames() + " for '" + artifactId + "' application");
        }
    }

    private static void overrideDefaultProperties(final Environment ce, final String artifactId, final Deployments.ContextualEnvironment contextualEnvironment) {
        final GlobalConfiguration configuration = ce.findService(GlobalConfiguration.class);
        final Map<String, Map<String, ?>> defaults = ofNullable(configuration).map(GlobalConfiguration::getDefaults).orElse(emptyMap());

        final Deployments.Environment env = contextualEnvironment.getEnvironment();
        ofNullable(defaults.get(contextualEnvironment.getName())).ifPresent(def -> def.forEach((k, v) -> env.getProperties().putIfAbsent(k, v.toString())));
        env.getProperties().putIfAbsent("base", env.getBase());
        env.getProperties().putIfAbsent("user", env.getUser());
        env.getProperties().putIfAbsent("artifact", artifactId);
        env.getProperties().putIfAbsent("environment", contextualEnvironment.getName());
    }

    private static File gitClone(final GitConfiguration git, final String base,
                                 final String subFolder,
                                 final PrintStream out, final File workDir) {
        final File gitConfig = new File(workDir, base + "-git-config");
        git.clone(gitConfig, new PrintWriter(out));

        // here we suppose application name == artifactId
        final File deploymentConfig = new File(gitConfig, base + ofNullable(subFolder).map(s -> "/" + s).orElse("") + "/deployments.json");
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
                                final String subFolder,
                                final SshKey sshKey,
                                final File workDirBase,
                                final GitConfiguration git,
                                final String artifactId,
                                final PrintStream out,
                                final int nodeIndex,
                                final int nodeGroup,
                                final Environment crestEnv,
                                final String textByHost,
                                final String... cmds) throws IOException {
        execute(environment, subFolder, sshKey, workDirBase, git, artifactId, out, nodeIndex, nodeGroup, textByHost, e -> cmds, crestEnv);
    }

    private static void execute(final String inEnvironment,
                                final String subFolder,
                                final SshKey sshKey,
                                final File workDirBase,
                                final GitConfiguration git,
                                final String artifactId,
                                final PrintStream out,
                                final int nodeIndex,
                                final int nodeGroup,
                                final String textByHost,
                                final Function<Deployments.Environment, String[]> cmdBuilder,
                                final Environment crestEnv) throws IOException {
        final File workDir = TempDir.newTempDir(workDirBase, artifactId);

        final File deploymentConfig = gitClone(git, artifactId, subFolder, out, workDir);

        try (final FileReader reader = new FileReader(deploymentConfig)) {
            final Deployments.Application app = Deployments.read(reader);
            app.findEnvironments(inEnvironment).forEach(env -> {
                env.resetEnvironment();
                validateEnvironment(artifactId, env);
                overrideDefaultProperties(crestEnv, artifactId, env);

                final Deployments.Environment environment = env.getEnvironment();

                final boolean skipEnvFolder = Boolean.parseBoolean(ofNullable(environment.getDeployerProperties().get("skipEnvironmentFolder")).orElse("false"));

                final AtomicInteger currentIdx = new AtomicInteger();
                final NodeSelector selector = new NodeSelector(nodeIndex, nodeGroup);

                final Map<String, Iterator<String>> byHostEntries = ofNullable(env.getEnvironment().getByHostProperties()).orElse(emptyMap())
                        .entrySet().stream().collect(toMap(Map.Entry::getKey, e -> e.getValue().iterator()));

                environment.getHosts().forEach(host -> {
                    byHostEntries.forEach((k, v) -> env.getEnvironment().getProperties().put(k, v.next()));
                    env.getEnvironment().getProperties().put("host", host);
                    env.getEnvironment().getProperties().putIfAbsent("environment", env.getName());

                    if (!selector.isSelected(currentIdx.getAndIncrement())) {
                        return;
                    }

                    out.println(String.format(textByHost, artifactId, host, env.getName()));

                    try (final Ssh ssh = newSsh(sshKey, host, app, environment)) {
                        final String targetFolder = environment.getBase() + (environment.getBase().endsWith("/") ? "" : "/") + artifactId + (skipEnvFolder ? "" : ("/" + env.getName()));
                        Stream.of(cmdBuilder.apply(environment))
                                .map(c -> Substitutors.resolveWithVariables((c.contains("%s") ? String.format(c, targetFolder) : c)
                                                .replace("%targetFolder", targetFolder)
                                                .replace("%environment", env.getName()),
                                        env.getEnvironment().getProperties(), app.getProperties()))
                                .forEach(ssh::exec);
                    }
                });
            });
        } finally {
            try {
                Files.remove(workDir);
            } catch (final IllegalStateException ise) {
                // ok
            }
        }
    }
}
