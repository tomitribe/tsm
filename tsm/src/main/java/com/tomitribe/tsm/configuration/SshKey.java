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
import org.tomitribe.crest.api.Option;
import org.tomitribe.crest.api.Options;

import java.io.File;

@Data
@Options
public class SshKey {
    private File path;
    private String passphrase;

    public SshKey(@Option("path") final File path, @Option("passphrase") final String passphrase) {
        this.path = path;
        this.passphrase = passphrase;
    }
}
