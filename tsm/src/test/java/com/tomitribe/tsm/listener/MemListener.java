/*
 * Tomitribe Confidential
 *
 * Copyright Tomitribe Corporation. 2015
 *
 * The source code for this program is not published or otherwise divested
 * of its trade secrets, irrespective of what has been deposited with the
 * U.S. Copyright Office.
 */
package com.tomitribe.tsm.listener;

import java.util.ArrayList;
import java.util.Collection;
import java.util.function.Function;

public class MemListener implements Listeners.Listener {
    public static Collection<String> MESSAGES = new ArrayList<>();

    @Override
    public void sendMessage(final Function<String, String> config, final String message) {
        synchronized (MESSAGES) {
            MESSAGES.add(message);
        }
    }
}
