/*
 * Tomitribe Confidential
 *
 * Copyright Tomitribe Corporation. 2015
 *
 * The source code for this program is not published or otherwise divested
 * of its trade secrets, irrespective of what has been deposited with the
 * U.S. Copyright Office.
 */
package com.tomitribe.tsm.slack;

import com.tomitribe.tsm.listener.Listeners;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.apache.johnzon.mapper.JohnzonProperty;
import org.apache.johnzon.mapper.Mapper;
import org.apache.johnzon.mapper.MapperBuilder;
import org.tomitribe.util.IO;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;

import static java.util.Optional.ofNullable;
import static javax.xml.bind.DatatypeConverter.printBase64Binary;

@RequiredArgsConstructor
public class Slack implements Listeners.Listener {
    private final Mapper mapper = new MapperBuilder().build();

    @Override
    public void sendMessage(final Function<String, String> reader, final String message) {
        if (!Boolean.parseBoolean(reader.apply("slack.active"))) {
            return;
        }

        final String authType = reader.apply("slack.auth.type");
        final String authUser = authType == null ? null : reader.apply("slack.auth.user");
        final String authPassword = authType == null ? null : reader.apply("slack.auth.password");
        final String channel = reader.apply("slack.channel");
        final String emoji = reader.apply("slack.emoji");
        final String endpoint = reader.apply("slack.endpoint");
        final String timeout = reader.apply("slack.timeout");

        final URL url;
        try {
            url = new URL(endpoint);
        } catch (final MalformedURLException e) {
            throw new IllegalArgumentException(e);
        }

        IOException err = null;
        final int retries = 2; // simple retries, just in case
        for (int i = 0; i < retries; i++) {
            HttpURLConnection connection = null;
            try {
                connection = HttpURLConnection.class.cast(url.openConnection());
                if ("basic".equalsIgnoreCase(authType)) {
                    String auth = authType + " " + printBase64Binary((authUser + (authPassword == null ? "" : ':' + authPassword)).getBytes(StandardCharsets.UTF_8));
                    connection.setRequestProperty("Authorization", auth);
                }
                connection.setRequestMethod("POST");
                connection.setDoInput(true);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                if (timeout != null) {
                    final int timeoutInt = Integer.parseInt(timeout);
                    connection.setConnectTimeout(timeoutInt);
                    connection.setReadTimeout(timeoutInt);
                }
                connection.connect();

                try (final OutputStream os = connection.getOutputStream()) {
                    final String json = mapper.writeObjectAsString(new Message(emoji, channel.startsWith("#") ? channel : ('#' + channel), message));
                    os.write(("payload=" + URLEncoder.encode(json, "UTF-8")).getBytes(StandardCharsets.UTF_8));
                }

                try (final InputStream in = connection.getInputStream()) { // read response to not split too early
                    IO.slurp(in);
                }

                if (connection.getResponseCode() > HttpURLConnection.HTTP_NO_CONTENT) { // accept 200 and 204
                    if (retries == i + 1) {
                        throw new IllegalStateException("Got response " + connection.getResponseCode());
                    }
                    continue;
                }
                return;
            } catch (final IOException ioe) {
                err = ioe;
            } finally {
                ofNullable(connection).ifPresent(HttpURLConnection::disconnect);
            }
        }
        throw new IllegalStateException(err);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Message {
        @JohnzonProperty("icon_emoji")
        private String emoji;
        private String channel;
        private String text;
    }
}
