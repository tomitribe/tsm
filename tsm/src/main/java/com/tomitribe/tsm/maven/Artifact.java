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

import org.tomitribe.crest.api.Option;
import org.tomitribe.crest.api.Options;

@Options
public class Artifact {
    private final String groupId;
    private final String artifactId;
    private final String version;
    private final String type;
    private final String classifier;

    public Artifact(@Option("groupId") final String groupId,
                    @Option("artifactId") final String artifactId,
                    @Option("version") final String version,
                    @Option("type") final String type,
                    @Option("classifier") final String classifier) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.type = type;
        this.classifier = classifier;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public String getVersion() {
        return version;
    }

    public String getType() {
        return type;
    }

    public String getClassifier() {
        return classifier;
    }

    public String filename() {
        return shortFilename() + "." + getType();
    }

    public String shortFilename() {
        return getArtifactId() + "-" + getVersion() + (getClassifier() != null ? "-" + getClassifier() : "");
    }
}
