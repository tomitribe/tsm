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

import org.apache.commons.lang3.text.StrLookup;
import org.apache.commons.lang3.text.StrSubstitutor;
import org.tomitribe.crest.environments.Environment;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

import static java.util.Collections.emptyList;
import static java.util.Optional.ofNullable;

public interface Substitutors {
    @SafeVarargs
    static String resolveWithVariables(final String key, final Map<String, String>... vars) {
        if (key == null) {
            return null;
        }
        return new StrSubstitutor(new StrLookup<String>() {
            @Override
            public String lookup(final String key) {
                final String property = System.getProperty(key);
                return ofNullable(ofNullable(property)
                    .orElseGet(() ->
                        ofNullable(vars).map(Arrays::asList).orElse(emptyList()).stream()
                            .filter(Objects::nonNull)
                            .map(m -> m.get(key))
                            .filter(Objects::nonNull)
                            .findFirst().orElse(null)))
                    .orElseGet(() -> ofNullable(Environment.ENVIRONMENT_THREAD_LOCAL.get().findService(GlobalConfiguration.class)).map(c -> c.read(key)).orElse(null));
            }
        }).replace(key);
    }
}
