/*
 * Tomitribe Confidential
 *
 * Copyright Tomitribe Corporation. 2015
 *
 * The source code for this program is not published or otherwise divested
 * of its trade secrets, irrespective of what has been deposited with the
 * U.S. Copyright Office.
 */
package com.tomitribe.tsm.cdi.internal;

import com.tomitribe.tsm.cdi.api.Tsm;
import lombok.Getter;
import org.apache.openejb.jee.WebApp;
import org.apache.openejb.junit.ApplicationComposer;
import org.apache.openejb.testing.Classes;
import org.apache.openejb.testing.Configuration;
import org.apache.openejb.testing.Module;
import org.apache.openejb.testng.PropertiesBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

@RunWith(ApplicationComposer.class)
public class TsmMetadataProducerTest {
    @Module
    @Classes(cdi = true, value = {TsmMetadataProducer.class, Tsm.class}, innerClassesAsBean = true)
    public WebApp app() {
        return new WebApp().contextRoot("app");
    }

    @Configuration
    public Properties config() {
        return new PropertiesBuilder()
            .p("tsm.cdi.metadata.path", createTsmFile())
            .build();
    }

    @Inject
    private ReadData data;

    @Test
    public void inject() {
        assertNotNull(data);
        assertEquals("superhost", data.getHost());
        assertEquals("myenv", data.getEnv());
        assertEquals("com.tomitribe.foo", data.getGroup());
        assertEquals("bar", data.getArtifact());
        assertEquals("2.1.4", data.getVersion());
        assertEquals("master", data.getBranch());
        assertEquals("kwgkfgrrfcrw", data.getRev());
        assertEquals("0.71", data.getServer());
        assertEquals("8u60", data.getJava());
    }

    private static String createTsmFile() {
        final File file = new File("target/TsmMetadataProducerTest.json");
        try (final FileWriter writer = new FileWriter(file)) {
            writer.write("{\n");
            writer.write("  \"date\":\"" + LocalDateTime.now().toString() + "\",\n");
            writer.write("  \"host\":\"superhost\",\n");
            writer.write("  \"environment\":\"myenv\",\n");
            writer.write("  \"application\":{\n");
            writer.write("    \"groupId\":\"com.tomitribe.foo\",\n");
            writer.write("    \"artifactId\":\"bar\",\n");
            writer.write("    \"version\":\"2.1.4\"\n");
            writer.write("  },\n");
            writer.write("  \"git\":{\n");
            writer.write("    \"branch\":\"master\",\n");
            writer.write("    \"revision\":\"kwgkfgrrfcrw\"\n");
            writer.write("  },\n");
            writer.write("  \"server\":{\n");
            writer.write("    \"name\":\"0.71\"\n");
            writer.write("  },\n");
            writer.write("  \"java\":{\n");
            writer.write("    \"version\":\"8u60\"\n");
            writer.write("  }\n");
            writer.write("}\n");
        } catch (final IOException e) {
            fail(e.getMessage());
        }
        return file.getAbsolutePath();
    }

    @Getter
    @ApplicationScoped
    public static class ReadData {
        @Inject
        @Tsm(Tsm.Key.HOST)
        private String host;

        @Inject
        @Tsm(Tsm.Key.ENVIRONMENT)
        private String env;

        @Inject
        @Tsm(Tsm.Key.GROUPID)
        private String group;

        @Inject
        @Tsm(Tsm.Key.ARTIFACTID)
        private String artifact;

        @Inject
        @Tsm(Tsm.Key.VERSION)
        private String version;

        @Inject
        @Tsm(Tsm.Key.BRANCH)
        private String branch;

        @Inject
        @Tsm(Tsm.Key.REVISION)
        private String rev;

        @Inject
        @Tsm(Tsm.Key.SERVER)
        private String server;

        @Inject
        @Tsm(Tsm.Key.JAVA)
        private String java;
    }
}
