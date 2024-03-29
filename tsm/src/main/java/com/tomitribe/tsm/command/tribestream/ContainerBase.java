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

import com.tomitribe.tsm.configuration.Deployments;
import com.tomitribe.tsm.configuration.GitConfiguration;
import com.tomitribe.tsm.configuration.GlobalConfiguration;
import com.tomitribe.tsm.configuration.LocalFileRepository;
import com.tomitribe.tsm.configuration.SshKey;
import com.tomitribe.tsm.configuration.Substitutors;
import com.tomitribe.tsm.console.ProgressBar;
import com.tomitribe.tsm.file.TempDir;
import com.tomitribe.tsm.http.Http;
import com.tomitribe.tsm.ssh.Ssh;
import org.apache.johnzon.mapper.MapperBuilder;
import org.apache.johnzon.mapper.reflection.JohnzonParameterizedType;
import org.tomitribe.util.Files;
import org.tomitribe.util.IO;
import org.tomitribe.util.Size;
import org.tomitribe.util.SizeUnit;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.script.ScriptException;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import static java.util.Optional.ofNullable;

class ContainerBase {
    static void tribestreamInstall(final String displayName,
                                   final String groupId,
                                   final String artifactId,
                                   final String classifier,
                                   final File workDirBase,
                                   final String inEnvironment,
                                   final SshKey sshKey,
                                   final TomitribeTribestreamMetadataPrincipal security,
                                   final LocalFileRepository localFileRepository,
                                   final GitConfiguration git,
                                   final String application,
                                   final String version,
                                   final PrintStream out,
                                   final GlobalConfiguration configuration,
                                   final String baseUrl,
                                   final String subFolder) throws IOException, ScriptException {
        final File workDir = TempDir.newTempDir(workDirBase, artifactId + "-install");

        final File downloadedFile = new File(workDir, artifactId + "-" + version + ".tar.gz");
        final File cacheFile = localFileRepository.find(groupId, artifactId, version, classifier, "tar.gz");
        if (cacheFile.isFile()) {
            out.println("Using locally cached " + displayName + ".");
            IO.copy(cacheFile, downloadedFile);
        } else {
            out.println("Didn't find cached " + displayName + " in " + cacheFile + " so trying to download it for this provisioning.");

            String token;
            final String pathWithVersion = groupId.replace('/', '.') + "/" + artifactId + "/" + version + "/tar.gz";
            final HttpURLConnection urlConnection = HttpURLConnection.class.cast(new URL(base(baseUrl) + "/downloads/api/catalog/token/" + pathWithVersion).openConnection());
            try {
                if (HttpsURLConnection.class.isInstance(urlConnection)) {
                    final HttpsURLConnection httpsURLConnection = HttpsURLConnection.class.cast(urlConnection);
                    httpsURLConnection.setHostnameVerifier((s, sslSession) -> true);

                    try {
                        final SSLContext context = SSLContext.getInstance("SSL");
                        context.init(null, new TrustManager[]{
                                new X509TrustManager() {
                                    @Override
                                    public void checkClientTrusted(final X509Certificate[] x509Certificates, final String s) throws CertificateException {
                                        // no-op
                                    }

                                    @Override
                                    public void checkServerTrusted(final X509Certificate[] x509Certificates, final String s) throws CertificateException {
                                        // no-op
                                    }

                                    @Override
                                    public X509Certificate[] getAcceptedIssuers() {
                                        return null;
                                    }
                                }
                        }, new SecureRandom());
                        httpsURLConnection.setSSLSocketFactory(context.getSocketFactory());
                    } catch (final Exception e) {
                        throw new IllegalStateException(e);
                    }
                }
                urlConnection.setRequestProperty("Authorization", security.getAuthorization());
                urlConnection.setRequestProperty("accept-licence", "true");
                token = IO.slurp(urlConnection.getInputStream());
            } finally {
                urlConnection.disconnect();
            }
            out.println("Downloading " + displayName + ", please wait...");
            new Http().download(
                    "https://www.tomitribe.com/downloads/api/catalog/get/" + pathWithVersion + "?token=" + token, downloadedFile,
                    null /* don't use new ProgressBar(out, "Downloading Tribestream " + version) since we don't have Content-Length for now here */);
        }
        out.println("Downloaded " + displayName + " in " + downloadedFile + " (" + new Size(downloadedFile.length(), SizeUnit.BYTES).toString().toLowerCase(Locale.ENGLISH) + ")");

        try {
            doInstall(displayName, artifactId, inEnvironment, sshKey, git, application, version, out, configuration, workDir, downloadedFile, subFolder);
        } finally {
            try {
                Files.remove(workDir);
            } catch (final IllegalStateException ise) {
                // ok
            }
        }
    }

    static void doInstall(final String displayName, final String artifactId, final String inEnvironment,
                          final SshKey sshKey, GitConfiguration git, final String application,
                          final String versionAndClassifier, final PrintStream out, final GlobalConfiguration configuration,
                          final File workDir, final File downloadedFile,
                          final String subFolder) throws IOException {
        final File gitConfig = new File(workDir, artifactId + "-git-config");
        git.clone(gitConfig, new PrintWriter(out));

        final File deploymentConfig = new File(gitConfig, application + ofNullable(subFolder).map(s -> "/" + s).orElse("") + "/deployments.json");
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
                    out.println("Deploying " + displayName + " " + versionAndClassifier + " on " + host);

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
                        final String targetFolder = fixedBase + artifactId + "/" + artifactId + '-' + versionAndClassifier + '/';
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

    static void tomitribeVersions(final String displayName,
                                  final String artifactId,
                                  final TomitribeTribestreamMetadataPrincipal security,
                                  final boolean includeSnapshots,
                                  final String baseUrl,
                                  final PrintStream ps) throws IOException {
        final List<String> lists = new ArrayList<>();
        final HttpURLConnection urlConnection = HttpURLConnection.class.cast(new URL(base(baseUrl) + "/downloads/api/catalog/artifact/com.tomitribe.tribestream/" + artifactId).openConnection());
        try {
            urlConnection.setRequestProperty("Accept", "application/json");
            urlConnection.setRequestProperty("Authorization", security.getAuthorization());
            final Collection<Artifact> artifacts = new MapperBuilder().setAccessModeName("field").build().readCollection(urlConnection.getInputStream(), new JohnzonParameterizedType(List.class, Artifact.class));
            artifacts.stream()
                    .filter(a -> ("ACTIVATED".equals(a.getState()) || "ARCHIVED".equals(a.getState())) && a.getSize() > 0 && "tar.gz".equals(a.getType()) && (includeSnapshots || !a.getVersion().contains("SNAPSHOT")))
                    .forEach(a -> lists.add(a.getVersion()));
        } finally {
            urlConnection.disconnect();
        }

        printVersions(displayName, ps, lists);
    }

    private static String base(final String baseUrl) {
        return ofNullable(baseUrl).orElse("https://www.tomitribe.com");
    }

    static void printVersions(final String displayName, final PrintStream ps, final List<String> lists) {
        Collections.sort(lists, Comparator.<String>reverseOrder());
        ps.println(displayName + ":");
        lists.stream().map(e -> "- " + e).forEach(ps::println);
    }
}
