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

import lombok.Data;
import org.tomitribe.crest.api.Default;
import org.tomitribe.crest.api.Option;
import org.tomitribe.crest.api.Options;

import java.io.File;

@Data
@Options
public class LocalFileRepository {
    private File base;

    public LocalFileRepository(@Option("cache.base") @Default("${user.home}/.m2/repository") final File base) {
        this.base = base;
    }

    public File find(final String groupId, final String artifactId, final String version, final String classifier, final String type) {
        return new File(base,
            String.format("%s/%s/%s/%s-%s%s.%s",
                groupId.replace('.', '/'), artifactId, version, artifactId, version,
                classifier == null || classifier.isEmpty() ? "" : ('-' + classifier), type));
    }
}
