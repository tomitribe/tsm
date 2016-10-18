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

import com.tomitribe.tsm.configuration.GlobalConfiguration;
import com.tomitribe.tsm.slack.Slack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.ServiceLoader;
import java.util.function.Function;
import java.util.stream.StreamSupport;

import static java.util.Collections.singletonList;

public class Listeners {
    private final Collection<Listener> listeners;
    private final GlobalConfiguration configuration;

    public Listeners(final GlobalConfiguration configuration) {
        this.configuration = configuration;
        this.listeners = new ArrayList<>(singletonList(new Slack()));
        StreamSupport.stream(ServiceLoader.load(Listener.class).spliterator(), false).forEach(this.listeners::add);
    }

    public void sendMessage(final String prefix, final String message) {
        final Function<String, String> config = key -> configuration.read(key, key + "." + prefix);
        listeners.forEach(l -> l.sendMessage(config, message));
    }

    public interface Listener {
        void sendMessage(Function<String, String> config, String message);
    }
}
