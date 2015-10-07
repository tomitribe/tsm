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

import org.tomitribe.util.IO;

import java.io.File;
import java.io.IOException;
import java.util.Base64;
import java.util.Properties;

public final class GlobalConfiguration {
    private final Properties properties = new Properties();

    public GlobalConfiguration(final File tsmrcLocation) {
        reload(tsmrcLocation);
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

    public void reload(final File tsmrcLocation) {
        properties.clear();
        if (tsmrcLocation.isFile()) {
            try {
                IO.readProperties(tsmrcLocation, properties);
            } catch (final IOException e) {
                throw new IllegalArgumentException(e);
            }
        }
    }
}
