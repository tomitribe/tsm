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

import static org.junit.Assert.assertEquals;

public class MavenPathFactoryTest {
    @Test
    public void createPath() {
        final MavenPathFactory factory = new MavenPathFactory();
        assertEquals(
            "com/tomitribe/tribestream/tribestream/0.61.2/tribestream-0.61.2.zip",
            factory.createPathFor("com.tomitribe.tribestream", "tribestream", "0.61.2", "zip", null));
        assertEquals(
            "com/tomitribe/tribestream/tribestream/0.61.2/tribestream-0.61.2.zip",
            factory.createPathFor("com.tomitribe.tribestream", "tribestream", "0.61.2", "zip", ""));
    }

    @Test
    public void createPathWithClassifier() {
        assertEquals(
            "com/tomitribe/tribestream/tribestream/0.61.2/tribestream-0.61.2-release-notes.pdf",
            new MavenPathFactory().createPathFor("com.tomitribe.tribestream", "tribestream", "0.61.2", "pdf", "release-notes"));
    }
}
