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

import com.tomitribe.crest.provisioning.gui.console.ProgressBar;
import com.tomitribe.crest.provisioning.http.Http;
import lombok.Data;
import org.tomitribe.crest.api.Option;
import org.tomitribe.crest.api.Options;
import org.tomitribe.util.IO;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;

import static java.util.Optional.ofNullable;
import static javax.xml.bind.annotation.XmlAccessType.FIELD;

@Data
@Options
public class Nexus {
    private String url;

    public Nexus(@Option("nexus.url") final String url) {
        setUrl(url);
    }

    public void setUrl(final String url) {
        this.url = url == null ? null : (url.endsWith("/") ? url : (url + '/'));
    }

    public Metadata versions(final String groupId, final String artifactId) {
        HttpURLConnection urlConnection = null;
        try {
            urlConnection = HttpURLConnection.class.cast(new URL(String.format("%s%s/%s/maven-metadata.xml", url, groupId.replace('.', '/'), artifactId)).openConnection());
            urlConnection.setRequestProperty("Accept", "application/xml");
            return Metadata.class.cast(JAXBContext.newInstance(Metadata.class).createUnmarshaller().unmarshal(urlConnection.getInputStream()));
        } catch (final IOException | JAXBException e) {
            throw new IllegalStateException(e);
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
        }
    }

    public DownloadHandler download(final PrintStream out, final String groupId, final String artifactId, final String version, final String classifier, final String type) {
        final String actualVersion;
        if (version.endsWith("-SNAPSHOT")) { // find the timestamp version if exists
            HttpURLConnection urlConnection = null;
            try {
                urlConnection = HttpURLConnection.class.cast(new URL(String.format("%s%s/%s/%s/maven-metadata.xml", url, groupId.replace('.', '/'), artifactId, version)).openConnection());
                urlConnection.setRequestProperty("Accept", "application/xml");
                urlConnection.setRequestProperty("User-Agent", "Chrome");

                final InputStream inputStream = urlConnection.getInputStream();
                final String content = IO.slurp(inputStream);
                final Metadata metadata = Metadata.class.cast(JAXBContext.newInstance(Metadata.class).createUnmarshaller().unmarshal(new ByteArrayInputStream(content.getBytes("UTF-8"))));
                final Versioning versioning = metadata.getVersioning();
                if (versioning != null && versioning.getSnapshot() != null) {
                    final Snapshot snapshot = versioning.getSnapshot();
                    final String potentialVersion = version.substring(0, version.length() - "SNAPSHOT" .length()) + snapshot.getTimestamp() + '-' + snapshot.getBuildNumber();
                    actualVersion = ofNullable(versioning.getSnapshotVersion()).orElse(Collections.emptyList()).stream()
                            .filter(s -> type.equals(s.getExtension()) && potentialVersion.equals(s.getValue())).findAny().isPresent() ? potentialVersion : version;
                } else {
                    actualVersion = version;
                }
            } catch (final IOException | JAXBException e) {
                throw new IllegalStateException(e);
            } finally {
                if (urlConnection != null) {
                    urlConnection.disconnect();
                }
            }
        } else {
            actualVersion = version;
        }

        return destination -> {
            final String classifierPart = classifier != null ? ':' + classifier : "";
            new Http().download(
                String.format("%s%s/%s/%s/%s-%s%s.%s",
                    url, groupId.replace('.', '/'), artifactId, version, artifactId, actualVersion,
                    classifierPart.isEmpty() ? "" : '-' + classifierPart.substring(1), type),
                destination,
                new ProgressBar(out, "Downloading " + groupId + ':' + artifactId + ':' + actualVersion + classifierPart + ':' + type));
        };
    }

    public interface DownloadHandler {
        void to(final File destination);
    }

    @Data
    @XmlAccessorType(FIELD)
    @XmlRootElement(name = "metadata")
    public static class Metadata {
        private String groupId;
        private String artifactId;
        private String version;
        private Versioning versioning;
    }

    @Data
    @XmlAccessorType(FIELD)
    public static class Versioning {
        private String latest;
        private String lastUpdated;
        private Snapshot snapshot;

        @XmlElement(name = "version")
        @XmlElementWrapper(name = "versions")
        private Collection<String> version;

        @XmlElement(name = "snapshotVersion")
        @XmlElementWrapper(name = "snapshotVersions")
        private Collection<SnapshotVersion> snapshotVersion;
    }

    @Data
    @XmlAccessorType(FIELD)
    public static class Snapshot {
        private String timestamp;
        private String buildNumber;

        @XmlElement(name = "version")
        @XmlElementWrapper(name = "versions")
        private Collection<String> version;
    }

    @Data
    @XmlAccessorType(FIELD)
    public static class SnapshotVersion {
        private String extension;
        private String value;
        private String udpated;
    }
}
