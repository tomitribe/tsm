/*
 * Tomitribe Confidential
 *
 * Copyright Tomitribe Corporation. 2015
 *
 * The source code for this program is not published or otherwise divested
 * of its trade secrets, irrespective of what has been deposited with the
 * U.S. Copyright Office.
 */
package com.tomitribe.tsm.webapp.deltaspike;

import org.apache.deltaspike.core.impl.config.ConfigurationExtension;
import org.apache.deltaspike.core.spi.activation.ClassDeactivator;
import org.apache.deltaspike.core.spi.activation.Deactivatable;

public class TsmClassDeactivator implements ClassDeactivator {
    @Override
    public Boolean isActivated(final Class<? extends Deactivatable> targetClass) {
        return ConfigurationExtension.class == targetClass;
    }
}
