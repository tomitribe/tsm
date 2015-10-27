/*
 * Tomitribe Confidential
 *
 * Copyright Tomitribe Corporation. 2015
 *
 * The source code for this program is not published or otherwise divested
 * of its trade secrets, irrespective of what has been deposited with the
 * U.S. Copyright Office.
 */
package com.tomitribe.tsm.command.tribestream;

import com.tomitribe.tsm.configuration.GitConfiguration;
import com.tomitribe.tsm.configuration.GlobalConfiguration;
import com.tomitribe.tsm.configuration.LocalFileRepository;
import com.tomitribe.tsm.configuration.Nexus;
import com.tomitribe.tsm.configuration.SshKey;
import com.tomitribe.tsm.crest.interceptor.DefaultParameters;
import com.tomitribe.tsm.file.TempDir;
import lombok.NoArgsConstructor;
import org.tomitribe.crest.api.Command;
import org.tomitribe.crest.api.Default;
import org.tomitribe.crest.api.Option;
import org.tomitribe.crest.api.Out;
import org.tomitribe.util.IO;
import org.tomitribe.util.Size;
import org.tomitribe.util.SizeUnit;

import javax.script.ScriptException;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static java.util.Collections.emptyList;
import static java.util.Optional.ofNullable;
import static lombok.AccessLevel.PRIVATE;

@Command("tomee")
@NoArgsConstructor(access = PRIVATE)
public class TomEE {
    private static final String CENTRAL = "http://repo.maven.apache.org/maven2/";
    private static final String APACHE_SNAPSHOT = "https://repository.apache.org/content/repositories/snapshots/";

    @Command(interceptedBy = DefaultParameters.class, usage = "tomee install application version")
    public static void install(@Option("work-dir-base") @Default("${java.io.tmpdir}/tsm") final File workDirBase,
                               @Option("environment") final String environment,
                               @Option("ssh.") final SshKey sshKey,
                               final LocalFileRepository localFileRepository,
                               final GitConfiguration git,
                               final String application,
                               final String version,
                               @Option("classifier") @Default("plus") final String classifier,
                               @Out final PrintStream out,
                               final GlobalConfiguration configuration) throws IOException, ScriptException {
        final String groupId = version.startsWith("1") ? "org.apache.openejb" : "org.apache.tomee";
        final String artifactId = "apache-tomee";

        final File workDir = TempDir.newTempDir(workDirBase, artifactId + "-install");

        final File downloadedFile = new File(workDir, artifactId + "-" + version + ".tar.gz");
        final File cacheFile = localFileRepository.find(groupId, artifactId, version, classifier, "tar.gz");
        if (cacheFile.isFile()) {
            out.println("Using locally cached TomEE.");
            IO.copy(cacheFile, downloadedFile);
        } else {
            out.println("Didn't find cached TomEE in " + cacheFile + " so trying to download it for this provisioning.");
            new Nexus(version.endsWith("-SNAPSHOT") ? APACHE_SNAPSHOT : CENTRAL, null, null)
                .download(out, groupId, artifactId, version, classifier, "tar.gz").to(downloadedFile);
        }
        out.println("Downloaded TomEE in " + downloadedFile + " (" + new Size(downloadedFile.length(), SizeUnit.BYTES).toString().toLowerCase(Locale.ENGLISH) + ")");

        ContainerBase.doInstall(
            "TomEE " + classifier, artifactId, environment, sshKey, git, application,
            version + ofNullable(classifier).map(c -> '-' + c).orElse(""), out, configuration, workDir, downloadedFile);
    }

    @Command(interceptedBy = DefaultParameters.class)
    public static void versions(@Option("snapshots") @Default("false") final boolean includeSnapshots,
                                @Out final PrintStream ps) {
        final Collection<CompletableFuture> tasks = new ArrayList<>();
        final Collection<String> versions = new HashSet<>();
        tasks.add(CompletableFuture.runAsync(() -> versions.addAll(safeVersions(CENTRAL, "org.apache.openejb", "apache-tomee"))));
        tasks.add(CompletableFuture.runAsync(() -> versions.addAll(safeVersions(CENTRAL, "org.apache.tomee", "apache-tomee"))));
        if (includeSnapshots) {
            tasks.add(CompletableFuture.runAsync(() -> versions.addAll(safeVersions(APACHE_SNAPSHOT, "org.apache.openejb", "apache-tomee"))));
            tasks.add(CompletableFuture.runAsync(() -> versions.addAll(safeVersions(APACHE_SNAPSHOT, "org.apache.tomee", "apache-tomee"))));
        }

        try {
            CompletableFuture.allOf(tasks.toArray(new CompletableFuture[tasks.size()]))
                .thenRun(() -> ContainerBase.printVersions("TomEE", ps, new ArrayList<>(versions)))
                .get();
        } catch (final InterruptedException e) {
            Thread.interrupted();
        } catch (final ExecutionException e) {
            throw new IllegalStateException(e.getCause());
        }
    }

    private static Collection<? extends String> safeVersions(final String repo, final String group, final String artifact) {
        try {
            return new Nexus(repo, null, null).versions(group, artifact).getVersioning().getVersion();
        } catch (final IllegalStateException e) {
            return emptyList();
        }
    }
}
