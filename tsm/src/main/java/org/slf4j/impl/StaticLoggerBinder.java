/*
 * Tomitribe Confidential
 *
 * Copyright Tomitribe Corporation. 2015
 *
 * The source code for this program is not published or otherwise divested
 * of its trade secrets, irrespective of what has been deposited with the
 * U.S. Copyright Office.
 */
package org.slf4j.impl;

import com.tomitribe.tsm.slf4j.CrestILoggerFactory;
import org.slf4j.ILoggerFactory;
import org.slf4j.spi.LoggerFactoryBinder;

public class StaticLoggerBinder implements LoggerFactoryBinder {
    @Override
    public ILoggerFactory getLoggerFactory() {
        return new CrestILoggerFactory();
    }

    @Override
    public String getLoggerFactoryClassStr() {
        return CrestILoggerFactory.class.getName();
    }

    public static StaticLoggerBinder getSingleton() {
        return Instance.THIS;
    }

    private static final class Instance {
        public static final StaticLoggerBinder THIS = new StaticLoggerBinder();
    }
}
