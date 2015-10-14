/*
 * Tomitribe Confidential
 *
 * Copyright Tomitribe Corporation. 2015
 *
 * The source code for this program is not published or otherwise divested
 * of its trade secrets, irrespective of what has been deposited with the
 * U.S. Copyright Office.
 */
package com.tomitribe.tsm.maven;

import com.tomitribe.tsm.http.Http;
import com.tomitribe.tsm.io.EnhancedIO;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.function.Consumer;

import static java.util.Optional.ofNullable;
import static javax.xml.bind.DatatypeConverter.printBase64Binary;

public class LightMavenResolver {
    private final Collection<String> repositories;
    private final String username;
    private final String password;
    private final MavenPathFactory pathFactory = new MavenPathFactory();
    private final Http http = new Http();

    public LightMavenResolver(final Collection<String> repositories, final String username, final String password) {
        this.repositories = repositories;
        this.username = username;
        this.password = password;
    }

    public File resolve(
        final Artifact artifact,
        final Consumer<Double> progressPerCentConsumer,
        final File destination) {
        final String path = pathFactory.createPathFor(
            artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion(),
            artifact.getType(), artifact.getClassifier());
        for (final String repo : repositories) {
            if (repo.startsWith("http")) {
                final String[] headers = password == null ? null : new String[] { "Authorization", printBase64Binary((username + ":" + password).getBytes()) };
                try {
                    return http.download(repo + (repo.endsWith("/") ? "" : "/") + path, destination, progressPerCentConsumer, headers);
                } catch (final IllegalStateException | IllegalArgumentException err) {
                    // no-op: try next
                }
            } else { // local
                final File potentialArtifact = new File(repo, path);
                if (potentialArtifact.isFile()) {
                    try (final FileOutputStream fos = new FileOutputStream(destination);
                         final FileInputStream fis = new FileInputStream(potentialArtifact)) {
                        EnhancedIO.copy(fis, fos, potentialArtifact.length(), progressPerCentConsumer);
                        return destination;
                    } catch (final IOException e) {
                        throw new IllegalStateException(e);
                    }
                }
            }
        }
        throw new IllegalStateException(String.format("%s:%s:%s:%s[:%s] not found",
            artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion(),
            artifact.getType(), ofNullable(artifact.getClassifier()).orElse("-")));
    }
}
