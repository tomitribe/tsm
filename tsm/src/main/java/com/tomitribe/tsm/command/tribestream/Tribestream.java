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
import com.tomitribe.tsm.configuration.SshKey;
import com.tomitribe.tsm.crest.interceptor.DefaultParameters;
import lombok.NoArgsConstructor;
import org.tomitribe.crest.api.Command;
import org.tomitribe.crest.api.Default;
import org.tomitribe.crest.api.Option;
import org.tomitribe.crest.api.Out;

import javax.script.ScriptException;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

import static lombok.AccessLevel.PRIVATE;

@Command("tribestream")
@NoArgsConstructor(access = PRIVATE)
public class Tribestream {
    @Command(interceptedBy = DefaultParameters.class)
    public static void install(@Option("work-dir-base") @Default("${java.io.tmpdir}/tsm") final File workDirBase,
                               @Option("environment") final String environment,
                               @Option("ssh.") final SshKey sshKey,
                               @Option("tomitribe.baseUrl") @Default("https://www.tomitribe.com") final String baseUrl,
                               final TomitribeTribestreamMetadataPrincipal security,
                               final LocalFileRepository localFileRepository,
                               final GitConfiguration git,
                               final String application,
                               final String version,
                               @Out final PrintStream out,
                               final GlobalConfiguration configuration) throws IOException, ScriptException {
        ContainerBase.tribestreamInstall(
            "Tribestream",
            "com.tomitribe.tribestream", "tribestream", null,
            workDirBase, environment, sshKey, security, localFileRepository, git, application, version, out, configuration, baseUrl);
    }

    @Command(interceptedBy = DefaultParameters.class)
    public static void versions(final TomitribeTribestreamMetadataPrincipal security,
                                @Option("snapshots") @Default("false") final boolean includeSnapshots,
                                @Option("tomitribe.baseUrl") @Default("https://www.tomitribe.com") final String baseUrl,
                                @Out final PrintStream ps) throws IOException {
        ContainerBase.tomitribeVersions("Tribestream", "tribestream", security, includeSnapshots, baseUrl, ps);
    }
}
