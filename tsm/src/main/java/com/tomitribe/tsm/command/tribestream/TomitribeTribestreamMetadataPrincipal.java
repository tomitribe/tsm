/*
 * Tomitribe Confidential
 *
 * Copyright Tomitribe Corporation. 2015
 *
 * The source code for this program is not published or otherwise divested
 * of its trade secrets, irrespective of what has been deposited with the
 * U.S. Copyright Office.
 */
package com.tomitribe.tsm.command.tribestream;

import lombok.Data;
import org.tomitribe.crest.api.Option;
import org.tomitribe.crest.api.Options;

import java.io.UnsupportedEncodingException;
import java.util.Base64;

import static java.util.Objects.requireNonNull;

@Data
@Options
public class TomitribeTribestreamMetadataPrincipal {
    private String username;
    private String password;

    private String authorization;

    public TomitribeTribestreamMetadataPrincipal(@Option("www.tomitribe.com.username") final String user,
                                                 @Option("www.tomitribe.com.password") final String pwd) {
        this.username = user;
        this.password = pwd;
    }

    public String getAuthorization() {
        if (authorization == null) {
            try {
                // don't use @Default to not show it in help
                this.authorization = "Basic " + Base64.getEncoder().encodeToString(
                    (requireNonNull(username, "tomitribe user is null") + ':' +
                        requireNonNull(password, "tomitribe password is null")).getBytes("UTF-8"));
            } catch (final UnsupportedEncodingException e) {
                throw new IllegalArgumentException(e);
            }
        }
        return authorization;
    }
}
