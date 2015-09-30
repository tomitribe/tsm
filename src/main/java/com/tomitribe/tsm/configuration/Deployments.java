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
import org.apache.johnzon.mapper.MapperBuilder;

import java.util.Collection;
import java.util.Map;

import static java.util.Collections.emptyList;
import static java.util.Optional.ofNullable;

public interface Deployments {
    @Data
    class Application {
        private final String name;
        private final Map<String, String> properties;
        private final Collection<Environment> environments;

        public Environment findEnvironment(final String environment) {
            return ofNullable(environments).orElse(emptyList()).stream()
                .filter(e -> ofNullable(e.getNames()).orElse(emptyList()).contains(environment))
                .reduce(null, (a, b) -> {
                    if (a == null && b != null) {
                        return b;
                    }
                    throw new IllegalArgumentException("Environment " + environment + " defined multiple times in deployments.json");
                });
        }
    }

    @Data
    class Environment {
        private Map<String, String> properties;
        private Collection<String> libs;
        private Collection<String> webapps;
        private final Collection<String> names;
        private final Collection<String> hosts;
        private final String base;
        private final String user;
    }

    static Application read(final java.io.Reader stream) {
        return new MapperBuilder()
            .setSupportsComments(true)
            .setAccessModeName("field")
            .build()
            .readObject(stream, Application.class);
    }
}
