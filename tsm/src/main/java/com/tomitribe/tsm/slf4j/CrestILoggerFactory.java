/*
 * Tomitribe Confidential
 *
 * Copyright Tomitribe Corporation. 2015
 *
 * The source code for this program is not published or otherwise divested
 * of its trade secrets, irrespective of what has been deposited with the
 * U.S. Copyright Office.
 */
package com.tomitribe.tsm.slf4j;

import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;

public class CrestILoggerFactory implements ILoggerFactory {
    @Override
    public Logger getLogger(final String s) {
        return new CrestLogger(s);
    }
}
