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

import org.junit.Test;
import org.tomitribe.util.Files;

import java.io.File;

import static java.util.Collections.singletonList;
import static org.junit.Assert.assertTrue;

public class LightMavenResolverTest {
    @Test
    public void resolveJunit() {
        final File workDir = new File("target/LightMavenResolverTest");
        Files.remove(workDir);
        assertTrue(new LightMavenResolver(
                singletonList(System.getProperty("user.home", "base.maven") + "/.m2/repository"), null, null)
            .resolve(new Artifact("junit", "junit", "4.12", "jar", null), null,
                new File(Files.mkdirs(workDir), "junit.jar")).length() > 300000);
    }
}
