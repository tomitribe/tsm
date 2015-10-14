/*
 * Tomitribe Confidential
 *
 * Copyright Tomitribe Corporation. 2015
 *
 * The source code for this program is not published or otherwise divested
 * of its trade secrets, irrespective of what has been deposited with the
 * U.S. Copyright Office.
 */
package com.tomitribe.tsm.io;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.HashMap;
import java.util.Spliterators;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.lang.ClassLoader.getSystemClassLoader;
import static java.lang.Thread.currentThread;
import static java.util.Collections.singleton;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;

public final class Jars {
    private Jars() {
        // no-op
    }

    public static Collection<JarStream> findStreams(final String prefix) {
        try {
            final String caller = new Exception().getStackTrace()[1].getClassName();
            final Class<?> clazz = currentThread().getContextClassLoader().loadClass(caller);
            final String classFileName = clazz.getName().replace(".", "/") + ".class";
            final URL file = ofNullable(Jars.class.getClassLoader()).orElse(getSystemClassLoader()).getResource(classFileName);

            final URI uri = URI.create(file.toExternalForm().replace(classFileName, ""));
            final FileSystem fs = "jar".equals(uri.getScheme()) ? FileSystems.newFileSystem(uri, new HashMap<>()) : null;
            try {
                final Path base = Paths.get(uri);

                final Function<Path, Stream<? extends Path>> flatMapFilesIgnoringFolders = new Function<Path, Stream<? extends Path>>() {
                    @Override
                    public Stream<? extends Path> apply(final Path path) {
                        try {
                            final BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
                            if (attrs.isDirectory()) {
                                return StreamSupport.stream(Spliterators.spliterator(Files.newDirectoryStream(path).iterator(), 0, 0), false).flatMap(this);
                            }
                            return singleton(path).stream();
                        } catch (final IOException e) {
                            throw new IllegalStateException(e);
                        }
                    }
                };

                try (final DirectoryStream<Path> stream = Files.newDirectoryStream(
                        base, p -> base.relativize(p).toString().startsWith(prefix))) {
                    return StreamSupport.stream(stream.spliterator(), false)
                        .flatMap(flatMapFilesIgnoringFolders)
                        .map(p -> {
                            try {
                                return new JarStream(base.relativize(p).toString(), new String(Files.readAllBytes(p), "UTF-8"));
                            } catch (final IOException e) {
                                throw new IllegalStateException(e);
                            }
                        })
                        .collect(toList());
                }
            } finally {
                ofNullable(fs).ifPresent((fileSystem) -> {
                    try {
                        fileSystem.close();
                    } catch (final IOException e) {
                        // no-op
                    }
                });
            }
        } catch (final IOException | ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    public static class JarStream {
        private final String name;
        private final String content;

        public JarStream(final String name, final String content) {
            this.name = name;
            this.content = content;
        }

        public String name() {
            return name;
        }

        public String content() {
            return content;
        }
    }
}
