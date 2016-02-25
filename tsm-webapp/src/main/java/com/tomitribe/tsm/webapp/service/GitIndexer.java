/*
 * Tomitribe Confidential
 *
 * Copyright Tomitribe Corporation. 2015
 *
 * The source code for this program is not published or otherwise divested
 * of its trade secrets, irrespective of what has been deposited with the
 * U.S. Copyright Office.
 */
package com.tomitribe.tsm.webapp.service;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.tomitribe.tsm.configuration.GitConfiguration;
import com.tomitribe.tsm.webapp.io.LoggerWriter;
import com.tomitribe.tsm.webapp.service.jpa.Repository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.apache.commons.lang3.text.StrSubstitutor;
import org.apache.deltaspike.core.api.config.ConfigProperty;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.TransportConfigCallback;

import javax.annotation.PostConstruct;
import javax.ejb.ConcurrencyManagement;
import javax.ejb.Schedule;
import javax.ejb.Singleton;
import javax.ejb.Timer;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static java.util.Collections.emptyList;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;
import static javax.ejb.ConcurrencyManagementType.BEAN;

@Log
@Singleton
@ConcurrencyManagement(BEAN)
public class GitIndexer {
    @Inject // just some clones for metada, not used for deployments where a clean copy is done
    @ConfigProperty(name = "tsm.git.workdir", defaultValue = "${catalina.base}/work/tsm/git")
    private String workDirBase;

    @PersistenceContext
    private EntityManager em;

    private final AtomicBoolean updateDone = new AtomicBoolean(true);
    private volatile Map<ExtendedGit, Meta> applications;

    @PostConstruct
    private void reload() {
        workDirBase = StrSubstitutor.replaceSystemProperties(workDirBase);
        doUpdate();
    }

    public Map<ExtendedGit, Meta> applicationsByRepository() {
        return new HashMap<>(applications);
    }

    @Schedule(hour = "*", minute = "*/5", persistent = false, info = "update-applications")
    public void updateApps(final Timer ignoredAndCanBeNull) {
        if (updateDone.compareAndSet(true, false)) {
            try {
                doUpdate();
            } finally {
                updateDone.compareAndSet(false, true);
            }
        }
    }

    private void doUpdate() {
        final Map<ExtendedGit, Meta> newValue = new HashMap<>();
        em.createNamedQuery("Repository.findAll", Repository.class).getResultList().stream().forEach(repo -> {
            final ExtendedGit key = new ExtendedGit(repo.getBase(), repo.getName(), "master", null, repo.getKey(), null, workDirBase);
            key.update();
            newValue.put(
                key,
                new Meta(
                    ofNullable(key.directory.listFiles(pathname -> pathname.isDirectory() && new File(pathname, "deployments.json").isFile()))
                        .map(Arrays::asList).orElse(emptyList())
                        .stream().map(File::getName).collect(toList())));
        });
        applications = newValue;
    }

    public static class ExtendedGit extends GitConfiguration {
        private final File directory;

        private ExtendedGit(final String base, final String repo, final String branch, final String revision,
                           final String sshKey, final String sshPassphrase, final String baseDir) {
            super(base, repo, branch, revision, sshKey, sshPassphrase);
            this.directory = new File(baseDir, base.substring(base.indexOf('@') + 1) + '/' + repo + '/' +branch + '/');
        }

        @Override
        protected TransportConfigCallback newTransportConfigCallback(final Consumer<JSch> consumer) {
            return super.newTransportConfigCallback(jsch -> {
                if (getSshKey() != null) {
                    try {
                        jsch.addIdentity(getBase() + '/' + getRepository(), getSshKey().getBytes(), null, null);
                    } catch (final JSchException e) {
                        throw new IllegalStateException(e);
                    }
                }
            });
        }

        public void update() {// todo: audit errors
            if (directory.isDirectory()) { // just pull rebase
                try {
                    try (final Git open = Git.open(directory)) {
                        final PullResult result = open
                            .pull()
                            .setRemote("origin")
                            .setRebase(true)
                            .setRemoteBranchName(getBranch())
                            .setCredentialsProvider(super.newCredentialsProvider())
                            .setTransportConfigCallback(super.newTransportConfigCallback(null))
                            .call();
                        if (!result.isSuccessful()) {
                            throw new IllegalStateException("Can't pull from " + directory);
                        }
                    }
                } catch (final IllegalStateException ise) {
                    throw ise;
                } catch (final Exception e) {
                    throw new IllegalStateException(e);
                }
            } else {
                directory.getParentFile().mkdirs();
                super.clone(directory, new LoggerWriter(log));
            }
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            final ExtendedGit key = ExtendedGit.class.cast(o);
            return getBase().equals(key.getBase())
                && getRepository().equals(key.getRepository())
                && getBranch().equals(key.getBranch());
        }

        @Override
        public int hashCode() {
            return Objects.hash(getBase(), getRepository(), getBase());
        }
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Meta {
        private final Collection<String> applications;
    }
}
