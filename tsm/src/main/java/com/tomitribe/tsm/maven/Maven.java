/*
 * Tomitribe Confidential
 *
 * Copyright Tomitribe Corporation. 2015
 *
 * The source code for this program is not published or otherwise divested
 * of its trade secrets, irrespective of what has been deposited with the
 * U.S. Copyright Office.
 */
package com.tomitribe.tsm.maven;

import org.tomitribe.crest.api.Default;
import org.tomitribe.crest.api.Option;
import org.tomitribe.crest.api.Options;

import java.util.Collection;

import static java.util.Arrays.asList;

@Options
public class Maven {
    private final Collection<String> repositories;
    private final String username;
    private final String password;

    public Maven(@Option("repositories")
                 @Default( // best is to have all dependencies locally, ie have them in the provisoining pom
                     "${user.home}/.m2/repository/," +
                     "https://repo.maven.apache.org/maven2/")
                 final String[] repositories,

                 @Option("username")
                 @Default("${user.name}")
                 final String user,

                 @Option("password")
                 final String pwd) {
        this.repositories = asList(repositories);
        this.username = user;
        this.password = pwd;
    }

    public Collection<String> getRepositories() {
        return repositories;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public LightMavenResolver resolver() {
        return new LightMavenResolver(getRepositories(), getUsername(), getPassword());
    }
}
