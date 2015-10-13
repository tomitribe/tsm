/*
 * Tomitribe Confidential
 *
 * Copyright Tomitribe Corporation. 2015
 *
 * The source code for this program is not published or otherwise divested
 * of its trade secrets, irrespective of what has been deposited with the
 * U.S. Copyright Office.
 */
package com.tomitribe.tsm.command.jvm;

import com.tomitribe.tsm.configuration.Deployments;
import com.tomitribe.tsm.configuration.GitConfiguration;
import com.tomitribe.tsm.configuration.LocalFileRepository;
import com.tomitribe.tsm.configuration.SshKey;
import com.tomitribe.tsm.configuration.Substitutors;
import com.tomitribe.tsm.crest.interceptor.DefaultParameters;
import com.tomitribe.tsm.file.TempDir;
import com.tomitribe.crest.provisioning.gui.console.ProgressBar;
import com.tomitribe.crest.provisioning.http.Http;
import com.tomitribe.crest.provisioning.ssh.Ssh;
import lombok.NoArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.tomitribe.crest.api.Command;
import org.tomitribe.crest.api.Default;
import org.tomitribe.crest.api.Err;
import org.tomitribe.crest.api.Option;
import org.tomitribe.crest.api.Out;
import org.tomitribe.util.IO;
import org.tomitribe.util.Size;
import org.tomitribe.util.SizeUnit;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;
import static java.util.Optional.ofNullable;
import static lombok.AccessLevel.PRIVATE;

@Command("java8")
@NoArgsConstructor(access = PRIVATE)
public class Java8 {
    private static final String JAVA8_VERSIONS = "http://www.oracle.com/technetwork/java/javase/8u-relnotes-2225394.html";
    private static final String JAVA8_CURRENT = "http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html";
    private static final String JAVA8_SECURITY_ENFORCEMENT = "http://download.oracle.com/otn-pub/java/jce/8/jce_policy-8.zip";
    private static final String JAVA8_ARCHIVE = "http://www.oracle.com/technetwork/java/javase/downloads/java-archive-javase8-2177648.html";

    @Command(value = "cryptography-extension", interceptedBy = DefaultParameters.class)
    public static void cryptographExtension(
                               @Option("work-dir-base") @Default("${java.io.tmpdir}/tsm") final File workDirBase,
                               @Option("environment") final String environment,
                               @Option("ssh.") final SshKey sshKey,
                               final LocalFileRepository localFileRepository,
                               final GitConfiguration git,
                               final String application,
                               final String version,
                               @Out final PrintStream out) throws IOException, ScriptException {
        // add security enforcement. Needed to use cipher TLS_RSA_WITH_AES_256_CBC_SHA256 for instance

        final File workDir = TempDir.newTempDir(workDirBase, "java8-cryptography-extension-install");
        final File destination = new File(workDir, "jce_policy-8.zip");
        final File cachedJdk = localFileRepository.find("com.oracle", "jce_policy", "8", null, "zip");
        if (cachedJdk.isFile()) {
            out.println("Using locally cached JDK.");
            IO.copy(cachedJdk, destination);
        } else {
            out.println("Didn't find " + cachedJdk.getAbsolutePath() + ", trying to download it on oracle website. You can create this file from " + JAVA8_SECURITY_ENFORCEMENT + ".");
            new Http().download(JAVA8_SECURITY_ENFORCEMENT, destination, new ProgressBar(out, "Downloading JCE " + version), "Cookie", "oraclelicense=accept-securebackup-cookie");
        }
        out.println("Downloaded Java Cryptography Extension in " + destination + " (" + new Size(destination.length(), SizeUnit.BYTES).toString().toLowerCase(Locale.ENGLISH) + ")");

        final File gitConfig = new File(workDir, "java8-jce-git-config");
        git.clone(gitConfig, new PrintWriter(out));

        final File deploymentConfig = new File(gitConfig, application + "/deployments.json");
        if (!deploymentConfig.isFile()) {
            throw new IllegalStateException("No deployments.json in provisioning repository: " + git.repository());
        }

        try (final FileReader reader = new FileReader(deploymentConfig)) {
            final Deployments.Application app = Deployments.read(reader);
            final Deployments.Environment env = app.findEnvironment(environment);
            if (env == null) {
                throw new IllegalArgumentException("No environment " + environment + " for '" + application + "' application");
            }
            if (env.getBase() == null) {
                throw new IllegalArgumentException("No base for environment " + environment + " for '" + application + "' application");
            }

            env.getHosts().forEach(host -> {
                out.println("Deploying jce 8 on " + host + " for java " + version);

                try (final Ssh ssh = new Ssh(
                    // recreate a ssh key using global config
                    new com.tomitribe.crest.provisioning.ssh.SshKey(sshKey.getPath(), Substitutors.resolveWithVariables(sshKey.getPassphrase())),
                    Substitutors.resolveWithVariables(
                        ofNullable(env.getUser()).orElse(System.getProperty("user.name")) + '@' + host,
                        env.getProperties(),
                        app.getProperties()
                    ))) {

                    final String fixedBase = env.getBase() + (env.getBase().endsWith("/") ? "" : "/");
                    final String remoteWorkDir = fixedBase + "work-provisioning/";
                    final String target = remoteWorkDir + destination.getName();
                    final String securityFolder = fixedBase + "java/jdk-" + version + "/jre/lib/security/";
                    final String extractDir = remoteWorkDir + "/" + workDir.getName() + '/';
                    ssh.exec(String.format("mkdir -p \"%s\"", remoteWorkDir))
                        .scp(destination, target, new ProgressBar(out, "Installing JCE on " + host))
                        .exec(String.format("unzip \"%s\" -d \"%s\"", target, extractDir));
                    asList("US_export_policy.jar", "local_policy.jar")
                        .forEach(jar -> ssh.exec(String.format("cp \"%s\" \"%s\"", extractDir + "UnlimitedJCEPolicyJDK8/US_export_policy.jar", securityFolder + jar)));
                    ssh.exec(String.format("rm -Rf \"%s\" \"%s\"", target, extractDir));

                    out.println("JCE 8 setup in " + securityFolder + " for host " + host);
                }
            });
        }
    }

    @Command(interceptedBy = DefaultParameters.class) // java8 install app1 8u60
    public static void install(@Option("architecture") @Default("linux-x64") final String architecture,
                               @Option("extension") @Default("tar.gz") final String extension,
                               @Option("work-dir-base") @Default("${java.io.tmpdir}/tsm") final File workDirBase,
                               @Option("environment") final String environment,
                               @Option("ssh.") final SshKey sshKey,
                               final LocalFileRepository localFileRepository,
                               final GitConfiguration git,
                               final String application,
                               final String version,
                               @Out final PrintStream out,
                               @Err final PrintStream err) throws IOException, ScriptException {
        final ScriptEngine engine = new ScriptEngineManager().getEngineByExtension("js");
        final File workDir = TempDir.newTempDir(workDirBase, "java8-install");

        final File jdkDestination = new File(workDir, "jdk-" + version + "." + extension);
        final File cachedJdk = localFileRepository.find("com.oracle", "jdk", version, null, extension);
        if (cachedJdk.isFile()) {
            out.println("Using locally cached JDK.");
            IO.copy(cachedJdk, jdkDestination);
        } else {
            out.println("Didn't find cached JDK in " + cachedJdk + " so trying to download it for this provisioning.");

            final Function<String, String> findUrl = listUrl -> {
                try {
                    final Document currentHtml = Jsoup.connect(listUrl).get();

                    final Optional<String> elt = currentHtml.select("script").stream()
                        .map(Element::html)
                        .filter(s -> s.contains("var downloads = new Array();"))
                        .findAny();

                    if (elt.isPresent()) { // try archives
                        final Object result;
                        try {
                            final String cleanJs = asList(elt.get().split("\n")).stream()
                                .filter(l -> l.trim().startsWith("downloads[") || l.trim().startsWith("var downloads"))
                                .collect(Collectors.joining("\n"));
                            result = engine.eval(cleanJs + "downloads;");
                        } catch (final ScriptException e) {
                            throw new IllegalArgumentException("Didnt manage to parse downloads in page " + listUrl, e);
                        }
                        if (!Map.class.isInstance(result)) {
                            throw new IllegalArgumentException("downloads in page " + listUrl + " is not a Map, update tsm please.");
                        }

                        final Map<String, Object> map = Map.class.cast(result);
                        final String expectedKey = "jdk-" + version + "-oth-JPR";
                        if (map.containsKey(expectedKey)) {
                            try {
                                return String.valueOf(Map.class.cast(Map.class.cast(Map.class.cast(map.get(expectedKey))
                                    .get("files"))
                                    .get("jdk-" + version + "-" + architecture + "." + extension))
                                    .get("filepath"));
                            } catch (final ClassCastException e) {
                                throw new IllegalArgumentException("Map downloads in page " + listUrl + " doesn't seem the expected one, update tsm please");
                            }
                        }
                    }
                } catch (final IOException e) {
                    throw new IllegalArgumentException("Didnt manage to get downloads in page " + listUrl);
                }
                return null;
            };

            final String url = ofNullable(findUrl.apply(JAVA8_CURRENT)).orElseGet(() -> findUrl.apply(JAVA8_ARCHIVE));
            if (url == null) {
                err.println("Didnt find version '" + version + "', please use one of the following JVM:");
                versions(err);
                return;
            }

            new Http().download(url, jdkDestination, new ProgressBar(out, "Downloading JDK " + version), "Cookie", "oraclelicense=accept-securebackup-cookie");
        }
        out.println("Downloaded JDK in " + jdkDestination + " (" + new Size(jdkDestination.length(), SizeUnit.BYTES).toString().toLowerCase(Locale.ENGLISH) + ")");

        final File gitConfig = new File(workDir, "java8-git-config");
        git.clone(gitConfig, new PrintWriter(out));

        final File deploymentConfig = new File(gitConfig, application + "/deployments.json");
        if (!deploymentConfig.isFile()) {
            throw new IllegalStateException("No deployments.json in provisioning repository: " + git.repository());
        }

        try (final FileReader reader = new FileReader(deploymentConfig)) {
            final Deployments.Application app = Deployments.read(reader);
            final Deployments.Environment env = app.findEnvironment(environment);
            if (env == null) {
                throw new IllegalArgumentException("No environment " + environment + " for '" + application + "' application");
            }
            if (env.getBase() == null) {
                throw new IllegalArgumentException("No base for environment " + environment + " for '" + application + "' application");
            }

            env.getHosts().forEach(host -> {
                out.println("Deploying jdk " + version + " on " + host);

                try (final Ssh ssh = new Ssh(
                    // recreate a ssh key using global config
                    new com.tomitribe.crest.provisioning.ssh.SshKey(sshKey.getPath(), Substitutors.resolveWithVariables(sshKey.getPassphrase())),
                    Substitutors.resolveWithVariables(
                        ofNullable(env.getUser()).orElse(System.getProperty("user.name")) + '@' + host,
                        env.getProperties(),
                        app.getProperties()
                    ))) {

                    final String fixedBase = env.getBase() + (env.getBase().endsWith("/") ? "" : "/");
                    final String remoteWorkDir = fixedBase + "work-provisioning/";
                    final String target = remoteWorkDir + jdkDestination.getName();
                    final String jdkTargetFolder = fixedBase + "java/jdk-" + version + '/';
                    ssh.exec(String.format("mkdir -p \"%s\" \"%s\"", remoteWorkDir, jdkTargetFolder))
                        .scp(jdkDestination, target, new ProgressBar(out, "Installing JDK on " + host))
                        .exec(String.format("tar xvf \"%s\" -C \"%s\" --strip 1", target, jdkTargetFolder))
                        .exec(String.format("rm \"%s\"", target));

                    out.println("JDK setup in " + jdkTargetFolder + " for host " + host);
                }
            });
        }
    }

    @Command(interceptedBy = DefaultParameters.class)
    public static void versions(@Out final PrintStream ps) {
        final List<String> lists = new ArrayList<>();
        final String releaseSuffixMarker = " (public release)";
        try {
            Jsoup.connect(JAVA8_VERSIONS).get().select("div.orcl6w3 a")
                .stream().filter(l -> l.text().endsWith(releaseSuffixMarker))
                .forEach(link -> {
                    final String text = link.text();
                    lists.add(text.substring(0, text.length() - releaseSuffixMarker.length()).replace("JDK ", ""));
                });
        } catch (final IOException e) {
            throw new IllegalStateException(JAVA8_VERSIONS + " not accessible");
        }

        Collections.sort(lists, Comparator.<String>reverseOrder());
        ps.println("JVM:");
        lists.stream().map(e -> "- " + e).forEach(ps::println);
    }
}
