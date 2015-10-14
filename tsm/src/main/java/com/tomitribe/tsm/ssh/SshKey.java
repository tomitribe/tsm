/*
 * Tomitribe Confidential
 *
 * Copyright Tomitribe Corporation. 2015
 *
 * The source code for this program is not published or otherwise divested
 * of its trade secrets, irrespective of what has been deposited with the
 * U.S. Copyright Office.
 */
package com.tomitribe.tsm.ssh;

import org.tomitribe.crest.api.Default;
import org.tomitribe.crest.api.Option;
import org.tomitribe.crest.api.Options;

import java.io.File;

@Options
public class SshKey {
    private final File path;
    private final String password;

    public SshKey(@Option("path") @Default("${user.home}/.ssh/id_rsa") final File path,
                  @Option("password") final String password) {
        this.path = path;
        this.password = password;
    }

    public File getPath() {
        return path;
    }

    public String getPassword() {
        return password;
    }
}
