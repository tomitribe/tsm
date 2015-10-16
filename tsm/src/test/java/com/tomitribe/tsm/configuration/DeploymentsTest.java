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

import org.junit.Test;

import java.io.StringReader;
import java.util.Collection;
import java.util.Iterator;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

public class DeploymentsTest {
    @Test
    public void read() {
        final Deployments.Application app = Deployments.read(new StringReader(
            "     {" +
                "  /* comment doesnt hurt */" +
                "  // inline as well\n" +
            "       \"environments\":[" +
            "         {" +
            "           \"names\":[" +
            "             \"ps\"," +
            "             \"pd\"" +
            "           ]," +
            "           \"hosts\":[" +
            "             \"h1\"," +
            "             \"h2\"" +
            "           ]," +
            "           \"base\":\"/opt/java8\"," +
            "           \"user\":\"aspadm\"" +
            "         }," +
            "         {" +
            "           \"names\":[" +
            "             \"prod\"" +
            "           ]," +
            "           \"hosts\":[" +
            "             \"h3\"," +
            "             \"h4\"," +
            "             \"h5\"," +
            "             \"h6\"" +
            "           ]" +
            "         }" +
            "       ]" +
            "     }"));
        assertNotNull(app);

            final Collection<Deployments.Environment> environments = app.getEnvironments();
            assertEquals(2, environments.size());
            final Iterator<Deployments.Environment> envIt = environments.iterator();
            {
                final Deployments.Environment env = envIt.next();
                assertEquals(asList("ps", "pd"), env.getNames());
                assertEquals(asList("h1", "h2"), env.getHosts());
                assertEquals("/opt/java8", env.getBase());
                assertEquals("aspadm", env.getUser());
            }
            {
                final Deployments.Environment env = envIt.next();
                assertEquals(singletonList("prod"), env.getNames());
                assertEquals(asList("h3", "h4", "h5", "h6"), env.getHosts());
            }

        assertEquals(asList("ps", "pd"), app.findEnvironments("ps").iterator().next().getEnvironment().getNames());
        try {
            assertNull(app.findEnvironments("missing"));
            fail("missing is not an environment");
        } catch (final IllegalArgumentException iae) {
            // ok
        }
    }
}
