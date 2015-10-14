/*
 * Tomitribe Confidential
 *
 * Copyright Tomitribe Corporation. 2015
 *
 * The source code for this program is not published or otherwise divested
 * of its trade secrets, irrespective of what has been deposited with the
 * U.S. Copyright Office.
 */
package com.tomitribe.tsm.http;

import org.junit.Test;
import org.tomitribe.util.Files;
import org.tomitribe.util.IO;

import java.io.File;
import java.io.IOException;

import static junit.framework.Assert.assertEquals;

public class HttpTest {
    @Test
    public void download() throws IOException {
        final File target = new File("target/HttpTest/tmp");
        Files.remove(target.getParentFile());
        assertEquals("6a41298d1331ea4fef808639926bdc3b82f6a984", IO.slurp(new Http()
            .download("http://repo1.maven.org/maven2/org/apache/johnzon/johnzon-core/0.7-incubating/johnzon-core-0.7-incubating.pom.sha1", target, null)));
    }
}
