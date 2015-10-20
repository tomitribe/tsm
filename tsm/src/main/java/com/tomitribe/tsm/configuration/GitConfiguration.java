/*
 * Tomitribe Confidential
 *
 * Copyright Tomitribe Corporation. 2015
 *
 * The source code for this program is not published or otherwise divested
 * of its trade secrets, irrespective of what has been deposited with the
 * U.S. Copyright Office.
 */
package com.tomitribe.tsm.configuration;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import lombok.Data;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.UnsupportedCredentialItem;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.jgit.transport.CredentialItem;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.JschConfigSessionFactory;
import org.eclipse.jgit.transport.OpenSshConfig;
import org.eclipse.jgit.transport.SshTransport;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.util.FS;
import org.tomitribe.crest.api.Default;
import org.tomitribe.crest.api.Option;
import org.tomitribe.crest.api.Options;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.Locale;
import java.util.stream.Stream;

import static java.util.Arrays.asList;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.of;
import static java.util.Optional.ofNullable;
import static org.eclipse.jgit.api.ResetCommand.ResetType.HARD;
import static org.eclipse.jgit.lib.Constants.HEAD;

@Data
@Options
public class GitConfiguration {
    private static final String DEFAULT_KEYS = "defaults";
    private static final String NO_PASSPHRASE = "no";

    private String revision;
    private String branch;
    private String sshKey;
    private String sshPassphrase;
    private String base;
    private String repository;

    public GitConfiguration(
            @Option("git.base") final String base,
            @Option("git.repository") final String repo,
            @Option("git.branch") @Default("master") final String branch,
            @Option("git.revision") final String revision,
            @Option("git.sshKey") final String sshKey,
            @Option("git.sshPassphrase") final String sshPassphrase) {
        this.base = base;
        this.repository = repo;
        this.branch = branch;
        this.revision = revision;
        this.sshKey = ofNullable(sshKey).map(Substitutors::resolveWithVariables).orElse(null);
        this.sshPassphrase = Substitutors.resolveWithVariables(sshPassphrase);
    }

    public void clone(final File checkoutDir, final Writer stdout) {
        requireNonNull(repository, "git.repository needs to be set");
        final boolean hasPassphrase = hasPassphrase();
        try {
            final CloneCommand cloneCommand = Git.cloneRepository()
                .setBranch(branch)
                .setURI(repository())
                .setDirectory(checkoutDir)
                .setProgressMonitor(new TextProgressMonitor(stdout))
                .setTransportConfigCallback(newTransportConfigCallback(hasPassphrase));
            if (hasPassphrase) {
                cloneCommand.setCredentialsProvider(newCredentialsProvider());
            }

            final Git git = cloneCommand.call();
            if (revision != null) {
                git.checkout().setCreateBranch(true).setName("deployment-" + revision).setStartPoint(revision).call();
            }
        } catch (final GitAPIException e) {
            throw new IllegalStateException(e);
        }
    }

    protected boolean hasPassphrase() {
        return sshPassphrase != null && !sshPassphrase.isEmpty() && !NO_PASSPHRASE.equals(sshPassphrase);
    }

    protected CredentialsProvider newCredentialsProvider() {
        return new CredentialsProvider() {
            @Override
            public boolean isInteractive() {
                return false;
            }

            @Override
            public boolean supports(final CredentialItem... items) {
                return filterPassphrase(items)
                    .findAny()
                    .isPresent();
            }

            @Override
            public boolean get(final URIish uri, final CredentialItem... items) throws UnsupportedCredentialItem {
                filterPassphrase(items).forEach(st -> CredentialItem.StringType.class.cast(st).setValue(sshPassphrase));
                return true;
            }

            private Stream<CredentialItem> filterPassphrase(final CredentialItem[] items) {
                return asList(ofNullable(items).orElse(new CredentialItem[0])).stream()
                    .filter(CredentialItem.StringType.class::isInstance)
                    .filter(st -> st.getPromptText().toLowerCase(Locale.ENGLISH).startsWith("passphrase for"));
            }
        };
    }

    protected TransportConfigCallback newTransportConfigCallback(final boolean hasPassphrase) {
        return transport -> of(transport).filter(SshTransport.class::isInstance).ifPresent(t -> SshTransport.class.cast(t).setSshSessionFactory(new JschConfigSessionFactory() {
            @Override
            protected void configure(final OpenSshConfig.Host hc, final Session session) {
                session.setConfig("StrictHostKeyChecking", "no");
                session.setConfig("PreferredAuthentications", "publickey");
            }

            @Override
            protected JSch createDefaultJSch(final FS fs) throws JSchException {
                final JSch jSch = super.createDefaultJSch(fs);
                if (new File(sshKey).isFile()) {
                    if (hasPassphrase) {
                        jSch.addIdentity(sshKey, sshPassphrase);
                    } else {
                        jSch.addIdentity(sshKey);
                    }
                } // else handled by jgit
                return jSch;
            }
        }));
    }

    public String repository() {
        return base.startsWith("file:") ? base : base + ":" + repository + ".git";
    }

    public void reset(final File deploymentConfig) {
        try {
            Git.open(deploymentConfig).reset().setMode(HARD).setRef(HEAD).call();
        } catch (final GitAPIException | IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
