/*
 * Tomitribe Confidential
 *
 * Copyright Tomitribe Corporation. 2015
 *
 * The source code for this program is not published or otherwise divested
 * of its trade secrets, irrespective of what has been deposited with the
 * U.S. Copyright Office.
 */
package com.tomitribe.tsm.cdi.api;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import javax.enterprise.util.Nonbinding;
import javax.inject.Qualifier;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Qualifier
@Retention(RUNTIME)
@Target({METHOD, FIELD, PARAMETER})
public @interface Tsm {
    @Nonbinding Key value();

    @RequiredArgsConstructor
    enum Key {
        DATE(new String[] {"date"}),
        HOST(new String[] {"host"}),
        ENVIRONMENT(new String[] {"environment"}),
        GROUPID(new String[] {"application", "groupId"}),
        ARTIFACTID(new String[] {"application", "artifactId"}),
        TSM_ARTIFACT(new String[] {"application", "originalArtifact"}),
        VERSION(new String[] {"application", "version"}),
        BRANCH(new String[] {"git", "branch"}),
        REVISION(new String[] {"git", "revision"}),
        SERVER(new String[] {"server", "name"}),
        JAVA(new String[] {"java", "version"});

        @Getter
        private final String[] path;
    }
}
