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

import static java.util.Optional.ofNullable;

public class MavenPathFactory {
    public String createPathFor(
            final String groupId, final String artifactId, final String version,
            final String type, final String classifier) {
        return groupId.replace('.', '/') + '/'
            + artifactId + '/'
            + version + '/'
            + artifactId + '-' + version + ofNullable(classifier).filter(c -> !c.isEmpty()).map(c -> '-' + c).orElse("") + '.' + type;
    }
}
