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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyList;
import static java.util.Optional.ofNullable;

public interface Deployments {
    @Data
    class Application {
        private final String name;
        private final Map<String, String> properties;
        private Map<String, List<String>> byHostProperties;
        private final Collection<Environment> environments;

        private Collection<String> libs;
        private Collection<String> webapps;
        private String base;
        private String user;
        private String groupId;
        private String version;

        public Environment findEnvironment(final String environment) {
            final Environment reduce = ofNullable(environments).orElse(emptyList()).stream()
                .filter(e -> ofNullable(e.getNames()).orElse(emptyList()).contains(environment))
                .reduce(null, (a, b) -> {
                    if (a == null && b != null) {
                        return b;
                    }
                    throw new IllegalArgumentException("Environment " + environment + " defined multiple times in deployments.json");
                });

            // libs/webapps/base/user are merged there == inheritance
            // not done for properties since this is handled by interpolation logic in aggregation mode
            // and here we just handle inheritance as overriding
            ofNullable(reduce).ifPresent(env -> {
                if (env.getBase() == null) {
                    env.setBase(base);
                }
                if (env.getUser() == null) {
                    env.setUser(user);
                }

                // lists are just in override mode, no "merge" logic to avoid misunderstanding
                if (env.getLibs() == null) {
                    env.setLibs(libs);
                }
                if (env.getWebapps() == null) {
                    env.setWebapps(webapps);
                }

                // coordinates
                if (env.getGroupId() == null) {
                    env.setGroupId(groupId);
                }
                if (env.getVersion() == null) {
                    env.setVersion(version);
                }

                // avoid NPE
                if (env.getDeployerProperties() == null) {
                    env.setDeployerProperties(new HashMap<>());
                }
                if (env.getProperties() == null) {
                    env.setProperties(new HashMap<>());
                }
                if (env.getByHostProperties() == null) {
                    env.setByHostProperties(new HashMap<>());
                }
            });

            return reduce;
        }
    }

    @Data
    class Environment {
        private Map<String, String> properties;
        private Map<String, List<String>> byHostProperties;
        private Collection<String> libs;
        private Collection<String> webapps;
        private Collection<String> names;
        private Collection<String> hosts;
        private String base;
        private String user;
        private String groupId;
        private String version;
        private Map<String, String> deployerProperties;

        public void validate() {
            final Integer expectedSize = ofNullable(hosts).map(Collection::size).orElse(0);
            ofNullable(byHostProperties).ifPresent(m -> m.values().forEach(l -> {
                if (l.size() != expectedSize) {
                    throw new IllegalArgumentException("byHostProperties values size should be the same as for the hosts list");
                }
            }));
        }
    }

    static Application read(final java.io.Reader stream) {
        return new MapperBuilder()
            .setSupportsComments(true)
            .setAccessModeName("field")
            .build()
            .readObject(stream, Application.class);
    }
}
