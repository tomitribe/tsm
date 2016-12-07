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

import com.tomitribe.tsm.configuration.Deployments;
import com.tomitribe.tsm.configuration.GitConfiguration;
import com.tomitribe.tsm.configuration.GlobalConfiguration;
import com.tomitribe.tsm.configuration.LocalFileRepository;
import com.tomitribe.tsm.configuration.SshKey;
import com.tomitribe.tsm.configuration.Substitutors;
import com.tomitribe.tsm.console.ProgressBar;
import com.tomitribe.tsm.crest.interceptor.DefaultParameters;
import com.tomitribe.tsm.file.TempDir;
import com.tomitribe.tsm.http.Http;
import com.tomitribe.tsm.ssh.Ssh;
import lombok.NoArgsConstructor;
import org.tomitribe.crest.api.Command;
import org.tomitribe.crest.api.Default;
import org.tomitribe.crest.api.Option;
import org.tomitribe.crest.api.Out;
import org.tomitribe.util.IO;
import org.tomitribe.util.Size;
import org.tomitribe.util.SizeUnit;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.Base64;
import java.util.Locale;

import static java.util.Optional.ofNullable;
import static lombok.AccessLevel.PRIVATE;

@Command("node")
@NoArgsConstructor(access = PRIVATE)
public class Node {
    @Command(interceptedBy = DefaultParameters.class)
    public static void install(@Option("work-dir-base") @Default("${java.io.tmpdir}/tsm") final File workDirBase,
                               @Option("environment") final String inEnvironment,
                               @Option("ssh.") final SshKey sshKey,
                               @Option("download-root") @Default("https://nodejs.org/dist/") final String root,
                               @Option("version") @Default("v7.2.1") final String version,
                               @Option("classifier") @Default("linux-x64") final String classifier,
                               final LocalFileRepository localFileRepository,
                               final GitConfiguration git,
                               final String application,
                               @Out final PrintStream out,
                               final GlobalConfiguration configuration) throws IOException {
        final File workDir = TempDir.newTempDir(workDirBase, application + "_node_install");
        final File downloadedFile = new File(workDir, application + "-" + version + ".tar.gz");

        final File cacheFile = localFileRepository.find("org.node", "node", version, classifier, "tar.gz");
        final String displayName = cacheFile.getName();
        if (!cacheFile.isFile()) {
            out.println("Didn't find cached " + displayName + " in " + cacheFile + " so trying to download it for this provisioning.");
            new Http().download(root + version + "/" + displayName, downloadedFile, new ProgressBar(out, "Downloading " + displayName));
            out.println("Downloaded Node in " + downloadedFile + " (" + new Size(downloadedFile.length(), SizeUnit.BYTES).toString().toLowerCase(Locale.ENGLISH) + ")");
        } else {
            out.println("Using locally cached " + displayName + ".");
            IO.copy(cacheFile, downloadedFile);
        }

        final File gitConfig = new File(workDir, application + "-git-config");
        git.clone(gitConfig, new PrintWriter(out));

        final File deploymentConfig = new File(gitConfig, application + "/deployments.json");
        if (!deploymentConfig.isFile()) {
            throw new IllegalStateException("No deployments.json in provisioning repository: " + git.repository());
        }

        try (final FileReader reader = new FileReader(deploymentConfig)) {
            final Deployments.Application app = Deployments.read(reader);
            app.findEnvironments(inEnvironment).forEach(env -> {
                if (env.getEnvironment().getBase() == null) {
                    throw new IllegalArgumentException("No base for environment " + env.getName() + " for '" + application + "' application");
                }

                env.getEnvironment().getHosts().forEach(host -> {
                    out.println("Deploying " + displayName + " on " + host);

                    try (final Ssh ssh = new Ssh(
                            // recreate a ssh key using global config
                            new com.tomitribe.tsm.ssh.SshKey(sshKey.getPath(), Substitutors.resolveWithVariables(
                                    ofNullable(sshKey.getPassphrase())
                                            .orElseGet(() -> ofNullable(configuration.read("ssh.passphrase", "git.passphrase"))
                                                    .map(s -> new String(Base64.getDecoder().decode(s)))
                                                    .orElse(null)))),
                            Substitutors.resolveWithVariables(
                                    ofNullable(env.getEnvironment().getUser()).orElse(System.getProperty("user.name")) + '@' + host,
                                    env.getEnvironment().getProperties(),
                                    app.getProperties()
                            ))) {

                        final String fixedBase = env.getEnvironment().getBase() + (env.getEnvironment().getBase().endsWith("/") ? "" : "/");
                        final String remoteWorkDir = fixedBase + "work-provisioning/";
                        final String target = remoteWorkDir + downloadedFile.getName();
                        final String targetFolder = fixedBase + "node/node-" + version;
                        ssh.exec(String.format("mkdir -p \"%s\" \"%s\"", remoteWorkDir, targetFolder))
                                .scp(downloadedFile, target, new ProgressBar(out, "Installing " + displayName + " on " + host))
                                .exec(String.format("tar xvf \"%s\" -C \"%s\" --strip 1", target, targetFolder))
                                .exec(String.format("rm \"%s\"", target));

                        out.println(displayName + " setup in " + targetFolder + " for host " + host);
                    }
                });
            });
        }
    }
}
