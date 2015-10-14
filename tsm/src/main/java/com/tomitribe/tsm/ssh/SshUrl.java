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

public class SshUrl {
    private final String directory;
    private final String sshConnection;

    /**
     * @param url connection with folder: user@host[:port]:targetDir
     */
    public SshUrl(final String url) {
        // create a temp dir to just rm -Rf if something went wrong and we want to cleanup what has been done
        final String normalized = url + (url.endsWith("/") ? "" : "/");
        final int pathSeparator = normalized.lastIndexOf(':');
        directory = normalized.substring(pathSeparator + 1);
        sshConnection = url.substring(0, pathSeparator);
    }

    public String getDirectory() {
        return directory;
    }

    public String getSshConnection() {
        return sshConnection;
    }
}
