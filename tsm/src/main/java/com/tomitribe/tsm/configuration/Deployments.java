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

import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.apache.johnzon.mapper.MapperBuilder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Arrays.stream;
import static java.util.Collections.emptyList;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;

public interface Deployments {
    @Data
    class Application {
        private final String name;
        private final Map<String, String> properties;
        private Map<String, List<String>> byHostProperties;
        private final Collection<Environment> environments;

        private String classpath;
        private Map<String, String> customLibs;
        private Collection<String> libs;
        private Collection<String> webapps;
        private String base;
        private String user;
        private String groupId;
        private String version;

        public Collection<ContextualEnvironment> findEnvironments(final String inEnvironment) {
            if ("*".equals(inEnvironment)) {
                return environments.stream()
                    .flatMap(e ->
                            ofNullable(e.getNames()).orElse(emptyList()).stream()
                                .map(n -> new ContextualEnvironment(n, e)).collect(toList()).stream()
                    ).collect(toList());
            }
            return stream(inEnvironment.split(" *, *")).map(environment -> {
                final Environment reduce = ofNullable(environments).orElse(emptyList()).stream()
                    .filter(e -> ofNullable(e.getNames()).orElse(emptyList()).contains(environment))
                    .reduce(null, (a, b) -> {
                        if (a == null && b != null) {
                            return b;
                        }
                        throw new IllegalArgumentException("Environment " + environment + " defined multiple times in deployments.json");
                    });
                if (reduce == null) {
                    throw new IllegalArgumentException("No environment " + environment + ".");
                }
                return new ContextualEnvironment(environment, reduce);
            }).collect(toList());
        }

        private void init() {
            ofNullable(environments).orElse(emptyList()).forEach(envrt -> {
                if (ofNullable(envrt.getHosts()).orElse(emptyList()).isEmpty()) {
                    throw new IllegalArgumentException("No host for " + envrt);
                }

                // libs/webapps/base/user are merged there == inheritance
                // not done for properties since this is handled by interpolation logic in aggregation mode
                // and here we just handle inheritance as overriding
                if (envrt.getBase() == null) {
                    envrt.setBase(base);
                }
                if (envrt.getUser() == null) {
                    envrt.setUser(user);
                }

                if (envrt.getClasspath() == null) {
                    envrt.setClasspath(classpath);
                }

                // lists are just in override mode, no "merge" logic to avoid misunderstanding
                if (envrt.getLibs() == null) {
                    envrt.setLibs(libs);
                }
                if (envrt.getCustomLibs() == null) {
                    envrt.setCustomLibs(customLibs);
                }
                if (envrt.getWebapps() == null) {
                    envrt.setWebapps(webapps);
                }
                if (envrt.getLibs() == null) {
                    envrt.setLibs(new ArrayList<>());
                }
                if (envrt.getCustomLibs() == null) {
                    envrt.setCustomLibs(new HashMap<>());
                }
                if (envrt.getWebapps() == null) {
                    envrt.setWebapps(new ArrayList<>());
                }

                // coordinates
                if (envrt.getGroupId() == null) {
                    envrt.setGroupId(groupId);
                }
                if (envrt.getVersion() == null) {
                    envrt.setVersion(version);
                }

                // avoid NPE
                if (envrt.getDeployerProperties() == null) {
                    envrt.setDeployerProperties(new HashMap<>());
                }
                if (envrt.getProperties() == null) {
                    envrt.setProperties(new HashMap<>());
                }
                if (envrt.getByHostProperties() == null) {
                    envrt.setByHostProperties(new HashMap<>());
                }

                // validate the envrt
                final Integer expectedSize = ofNullable(envrt.getHosts()).map(Collection::size).orElse(0);
                ofNullable(byHostProperties).filter(p -> !p.isEmpty()).ifPresent(m -> m.values().forEach(l -> {
                    if (l.size() != expectedSize) {
                        throw new IllegalArgumentException("byHostProperties values size should be the same as for the hosts list");
                    }
                }));
            });
        }
    }

    @Data
    class ContextualEnvironment {
        private final String name;
        private final Environment environment;

        @Setter(AccessLevel.NONE)
        @Getter(AccessLevel.NONE)
        private Environment environmentCopy;

        public void resetEnvironment() {
            environmentCopy = new Environment();
            environmentCopy.setProperties(new HashMap<>(environment.getProperties()));
            environmentCopy.setByHostProperties(new HashMap<>(environment.getByHostProperties()));
            environmentCopy.setDeployerProperties(new HashMap<>(environment.getDeployerProperties()));
            environmentCopy.setCustomLibs(new HashMap<>(environment.getCustomLibs()));
            environmentCopy.setLibs(new ArrayList<>(environment.getLibs()));
            environmentCopy.setWebapps(new ArrayList<>(environment.getWebapps()));
            environmentCopy.setNames(new ArrayList<>(environment.getNames()));
            environmentCopy.setHosts(new ArrayList<>(environment.getHosts()));
            environmentCopy.setBase(environment.getBase());
            environmentCopy.setUser(environment.getUser());
            environmentCopy.setGroupId(environment.getGroupId());
            environmentCopy.setVersion(environment.getVersion());
            environmentCopy.setClasspath(environment.getClasspath());
        }

        public Environment getEnvironment() {
            if (environmentCopy == null) {
                resetEnvironment();
            }
            return environmentCopy;
        }
    }

    @Data
    class Environment {
        private Map<String, String> properties;
        private Map<String, List<String>> byHostProperties;
        private Map<String, String> customLibs;
        private Collection<String> libs;
        private Collection<String> webapps;
        private Collection<String> names;
        private Collection<String> hosts;
        private String classpath;
        private String base;
        private String user;
        private String groupId;
        private String version;
        private Map<String, String> deployerProperties;
    }

    static Application read(final java.io.Reader stream) {
        final Application application = new MapperBuilder()
            .setSupportsComments(true)
            .setAccessModeName("field")
            .build()
            .readObject(stream, Application.class);
        application.init();
        return application;
    }
}
