/*
 * Tomitribe Confidential
 *
 * Copyright Tomitribe Corporation. 2015
 *
 * The source code for this program is not published or otherwise divested
 * of its trade secrets, irrespective of what has been deposited with the
 * U.S. Copyright Office.
 */
package com.tomitribe.tsm.webapp.resource.domain;

import lombok.Data;

import java.util.Date;

@Data
public class SimpleRepository {
    private long id;
    private String name;
    private String base;
    private String key;
    private Date created;
    private Date updated;
}
