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
import lombok.extern.java.Log;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonReaderFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.Collections;

import static java.util.Optional.ofNullable;

@Log
@ApplicationScoped
public class TsmMetadataProducer {
    private JsonObject metadata;

    @PostConstruct
    private void init() {
        final File source = ofNullable(System.getProperty("tsm.cdi.metadata.path")).map(File::new)
            .orElseGet(() -> new File(System.getProperty("catalina.base", System.getProperty("openejb.base", "")), "conf/tsm-metadata.json"));
        if (source.isFile()) {
            final JsonReaderFactory factory = Json.createReaderFactory(Collections.emptyMap());
            try (final JsonReader reader = factory.createReader(new FileInputStream(source))) {
                metadata = reader.readObject();
            } catch (final FileNotFoundException e) {
                throw new IllegalStateException(e);
            }
        } else {
            log.severe("No " + source.getName() + " so server sequence id will use a default. Did you use tsm for the provisioning?");
        }
    }

    @Tsm(Tsm.Key.DATE /* whatever */)
    @Produces
    public String readMeta(final InjectionPoint ip) {
        final Tsm.Key key = ip.getAnnotated().getAnnotation(Tsm.class).value();

        final String[] paths = key.getPath();
        JsonObject object = metadata;
        for (int i = 0; i < paths.length - 1; i++) {
            object = object.getJsonObject(paths[i]);
        }

        if (key == Tsm.Key.TSM_ARTIFACT && !object.containsKey(paths[paths.length - 1])) { // fallback on artifactId if not present
            return object.getString(Tsm.Key.ARTIFACTID.getPath()[paths.length - 1]); // yes both have the same length so this is an indexing shortcut
        }
        return object.getString(paths[paths.length - 1]);
    }
}
