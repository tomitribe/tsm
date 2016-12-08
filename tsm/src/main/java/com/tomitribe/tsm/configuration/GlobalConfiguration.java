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

import org.apache.johnzon.mapper.MapperBuilder;
import org.apache.johnzon.mapper.reflection.JohnzonParameterizedType;
import org.tomitribe.util.IO;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.Map;
import java.util.Properties;

import static java.util.Optional.ofNullable;

public final class GlobalConfiguration {
    private final Properties properties = new Properties();
    private volatile Map<String, Map<String, ?>> defaults;
    private boolean local;

    public GlobalConfiguration(final File tsmrcLocation) {
        reload(tsmrcLocation);
    }

    public boolean isLocal() {
        return local;
    }

    public String read(final String... keys) {
        for (final String key : keys) {
            final String property = properties.getProperty(key);
            if (property != null) {
                if (property.startsWith("base64:")) {
                    return new String(Base64.getDecoder().decode(property.substring("base64:".length())));
                }
                return property;
            }
        }
        return null;
    }

    public Map<String, Map<String, ?>> getDefaults() {
        return defaults;
    }

    public void reload(final File tsmrcLocation) {
        properties.clear();
        if (tsmrcLocation.isFile()) {
            try {
                IO.readProperties(tsmrcLocation, properties);
            } catch (final IOException e) {
                throw new IllegalArgumentException(e);
            }
        }

        try (final InputStream stream = findEnvConfig()) {
            defaults = new MapperBuilder().setAccessModeName("field").setSupportsComments(true).build()
                .readObject(
                    stream,
                    new JohnzonParameterizedType(Map.class, String.class, Object.class));
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }

        local = "true".equalsIgnoreCase(properties.getProperty("local", Boolean.toString(local)));
    }

    private InputStream findEnvConfig() {
        return ofNullable(read("environment.defaults.configuration.file"))
            .map(File::new)
            .filter(File::isFile)
            .map(f -> {
                try {
                    return InputStream.class.cast(new FileInputStream(f));
                } catch (final FileNotFoundException e) {
                    throw new IllegalStateException(e);
                }
            })
            .orElseGet(() -> Thread.currentThread().getContextClassLoader().getResourceAsStream("environment-defaults.json"));
    }

    public void local(final boolean value) {
        local = value;
    }
}
