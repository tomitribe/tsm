/*
 * Tomitribe Confidential
 *
 * Copyright Tomitribe Corporation. 2015
 *
 * The source code for this program is not published or otherwise divested
 * of its trade secrets, irrespective of what has been deposited with the
 * U.S. Copyright Office.
 */
package com.tomitribe.tsm.command.nexus;

import com.tomitribe.tsm.configuration.LocalFileRepository;
import com.tomitribe.tsm.configuration.Nexus;
import com.tomitribe.tsm.crest.interceptor.DefaultParameters;
import lombok.NoArgsConstructor;
import org.tomitribe.crest.api.Command;
import org.tomitribe.crest.api.Out;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

import static lombok.AccessLevel.PRIVATE;

@Command("nexus")
@NoArgsConstructor(access = PRIVATE)
public class CacheFromNexus {
    @Command(interceptedBy = DefaultParameters.class)
    public static void install(final Nexus nexus,
                               final LocalFileRepository localFileRepository,
                               final String groupId, final String artifactId, final String version, final String type,
                               @Out final PrintStream out) throws IOException {
        final File cacheFile = localFileRepository.find(groupId, artifactId, version, null, type);
        if (cacheFile.isFile()) {
            out.println(String.format("%s:%s:%s:%s is already cached", groupId, artifactId, version, type));
            return;
        }
        nexus.download(out, groupId, artifactId, version, null, type).to(cacheFile);
        out.println("Done.");
    }
}
