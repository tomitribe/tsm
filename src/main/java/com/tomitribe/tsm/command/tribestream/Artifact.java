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

@Data
public class Artifact {
    private String groupId;
    private String state;
    private String artifactId;
    private String type;
    private long size;
    private String version;
}
