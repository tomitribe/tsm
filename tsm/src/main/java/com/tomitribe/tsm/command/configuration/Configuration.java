/*
 * Tomitribe Confidential
 *
 * Copyright Tomitribe Corporation. 2015
 *
 * The source code for this program is not published or otherwise divested
 * of its trade secrets, irrespective of what has been deposited with the
 * U.S. Copyright Office.
 */
package com.tomitribe.tsm.command.configuration;

import com.tomitribe.tsm.configuration.GlobalConfiguration;
import com.tomitribe.tsm.crest.interceptor.DefaultParameters;
import lombok.NoArgsConstructor;
import org.tomitribe.crest.api.Command;

import java.io.File;
import java.io.IOException;

import static lombok.AccessLevel.PRIVATE;

@Command("configuration")
@NoArgsConstructor(access = PRIVATE)
public class Configuration {
    @Command(value = "reload", interceptedBy = DefaultParameters.class)
    public static void reloadTsmrc(final String file, final GlobalConfiguration configuration) throws IOException {
        configuration.reload(new File(file.startsWith("~") ? new File(System.getProperty("user.home"), file.substring(1)).getAbsolutePath() : file));
    }
}
