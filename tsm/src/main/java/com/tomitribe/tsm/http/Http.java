/*
 * Tomitribe Confidential
 *
 * Copyright Tomitribe Corporation. 2015
 *
 * The source code for this program is not published or otherwise divested
 * of its trade secrets, irrespective of what has been deposited with the
 * U.S. Copyright Office.
 */
package com.tomitribe.tsm.http;

import com.tomitribe.tsm.io.EnhancedIO;
import org.tomitribe.util.Files;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.function.Consumer;

import static java.util.Optional.ofNullable;

public class Http {
    private static final boolean DEBUG = Boolean.getBoolean("tsm.ssh.debug") || Boolean.getBoolean("tsm.debug");

    public File download(final String source,
                         final File target,
                         final Consumer<Double> progressPerCentConsumer,
                         final String... headers) {
        try {
            final URL url = new URL(source);
            HttpURLConnection connection = null;
            try {
                connection = HttpURLConnection.class.cast(url.openConnection());
                connection.setInstanceFollowRedirects(false);
                if (source.startsWith("https")) {
                    try {
                        final SSLContext sc = SSLContext.getInstance("SSL");
                        sc.init(null, new TrustManager[]{
                            new X509TrustManager() {
                                public X509Certificate[] getAcceptedIssuers() {
                                    return null;
                                }

                                public void checkClientTrusted(final X509Certificate[] certs, final String authType) {
                                    // no-op
                                }

                                public void checkServerTrusted(final X509Certificate[] certs, final String authType) {
                                    // no-op
                                }
                            }
                        }, new java.security.SecureRandom());
                        HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
                        HttpsURLConnection.setDefaultHostnameVerifier((s, sslSession) -> true);
                    } catch (final KeyManagementException | NoSuchAlgorithmException e) {
                        // no-op
                    }
                }

                for (int i = 0; i < ofNullable(headers).map(h -> h.length).orElse(0); i += 2) {
                    connection.setRequestProperty(headers[i], headers[i + 1]);
                }

                final int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP || responseCode == HttpURLConnection.HTTP_MOVED_PERM) {
                    final String location = connection.getHeaderField("Location");
                    if (DEBUG) {
                        System.out.println("[HTTP] Following redirection: " + location);
                    }
                    return download(location, target, progressPerCentConsumer, headers);
                } else if (responseCode > 299) {
                    throw new IllegalStateException(
                        "Can't download " + source + ": " +
                            "HTTP " + responseCode + " " + connection.getResponseMessage());
                } else {
                    try (final FileOutputStream fos = new FileOutputStream(new File(Files.mkdirs(target.getParentFile()), target.getName()))) {
                        EnhancedIO.copy(connection.getInputStream(), fos, connection.getContentLength(), progressPerCentConsumer);
                    }
                }
            } catch (final IOException e) {
                throw new IllegalStateException(e);
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
            return target;
        } catch (final MalformedURLException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
