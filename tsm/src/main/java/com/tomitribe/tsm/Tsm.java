/*
 * Tomitribe Confidential
 *
 * Copyright Tomitribe Corporation. 2015
 *
 * The source code for this program is not published or otherwise divested
 * of its trade secrets, irrespective of what has been deposited with the
 * U.S. Copyright Office.
 */
package com.tomitribe.tsm;

import com.tomitribe.tsm.configuration.GlobalConfiguration;
import com.tomitribe.tsm.listener.Listeners;
import com.tomitribe.tsm.ssh.EmbeddedSshServer;
import jline.console.history.History;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.tomitribe.crest.Main;
import org.tomitribe.crest.api.Exit;
import org.tomitribe.crest.cli.api.CliEnvironment;
import org.tomitribe.crest.cli.api.CrestCli;
import org.tomitribe.crest.cli.api.InputReader;
import org.tomitribe.crest.cmds.CommandFailedException;
import org.tomitribe.crest.environments.Environment;
import org.tomitribe.crest.environments.SystemEnvironment;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Arrays.asList;
import static java.util.Optional.ofNullable;
import static lombok.AccessLevel.PRIVATE;

// tsm can be launched with:
// - tsm ...
// - tsm -i # interactive
// - tsm -i -c mytsmrc # interactive and not default config
@Log
@NoArgsConstructor(access = PRIVATE)
public class Tsm {
    public static void main(final String[] args) throws Exception {
        final String tsmRc = ofNullable(System.getProperty("tsm.rc.location"))
                .orElseGet(() -> new File(System.getProperty("user.home"), ".tsmrc").getAbsolutePath());

        final GlobalConfiguration configuration = new GlobalConfiguration(new File(tsmRc));

        final List<String> options = new ArrayList<>(asList(ofNullable(args).orElse(new String[0])));
        final boolean interactive = options.remove("-i");
        if (options.size() >= 2) {
            if ("-c".equals(options.get(0))) {
                final String pathname = options.get(1);
                final File newTsmRc = new File(pathname);
                configuration.reload(newTsmRc);
                options.remove("-c");
                options.remove(pathname);
            }
        }
        configuration.local(options.remove("--local"));

        final Map<Class<?>, Object> services = new HashMap<>();
        services.put(GlobalConfiguration.class, configuration);
        services.put(Listeners.class, new Listeners(configuration));
        services.put(EmbeddedSshServer.class, new EmbeddedSshServer(configuration));

        final Thread hook = new Thread(() -> services.values().forEach(s -> {
            if (Closeable.class.isInstance(s)) {
                try {
                    Closeable.class.cast(s).close();
                } catch (final IOException e) {
                    log.warning(e.getMessage());
                }
            }
        }));
        Runtime.getRuntime().addShutdownHook(hook);

        try {
            if (interactive) {
                new CrestCli() {
                    private final String prompt = "tsm @ " + Tsm.class.getPackage().getImplementationVersion() + "$ ";

                    @Override
                    protected File aliasesFile() {
                        return new File(System.getProperty("user.home"), ".tomitribe/tsm_aliases");
                    }

                    @Override
                    protected File cliHistoryFile() {
                        return new File(System.getProperty("user.home"), ".tomitribe/tsm_history");
                    }

                    @Override
                    protected String nextPrompt() {
                        return prompt;
                    }

                    @Override
                    protected CliEnvironment createMainEnvironment(final AtomicReference<InputReader> input, final AtomicReference<History> history) {
                        return new TsmEnvironment(super.createMainEnvironment(input, history), services);
                    }
                }.run(options.toArray(new String[options.size()]));
            } else { // single command
                final SystemEnvironment env = new SystemEnvironment(services);
                Environment.ENVIRONMENT_THREAD_LOCAL.set(env); // before next line otherwise meta are wrong
                try {
                    new Main().main(env, options.toArray(new String[options.size()]));
                } catch (final CommandFailedException e) {
                    final Throwable cause = e.getCause();
                    final Exit exit = cause.getClass().getAnnotation(Exit.class);
                    if (exit != null) {
                        System.err.println(cause.getMessage());
                        System.exit(exit.value());
                    } else {
                        cause.printStackTrace();
                        System.exit(-1);
                    }
                } catch (final Exception ex) {
                    ex.printStackTrace();
                    System.exit(-1);
                }
            }
        } finally {
            try {
                Runtime.getRuntime().removeShutdownHook(hook);
                hook.run();
            } catch (final IllegalStateException ise) {
                // already done
            }
        }
    }

    @RequiredArgsConstructor
    private static class TsmEnvironment implements CliEnvironment {
        private final CliEnvironment cliEnv;
        private final Map<Class<?>, Object> services;

        @Override
        public PrintStream getOutput() {
            return cliEnv.getOutput();
        }

        @Override
        public PrintStream getError() {
            return cliEnv.getError();
        }

        @Override
        public InputStream getInput() {
            return cliEnv.getInput();
        }

        @Override
        public Properties getProperties() {
            return cliEnv.getProperties();
        }

        @Override
        public <T> T findService(final Class<T> service) {
            return (T) services.get(service);
        }

        @Override
        public History history() {
            return cliEnv.history();
        }

        @Override
        public InputReader reader() {
            return cliEnv.reader();
        }

        @Override
        public Map<String, ?> userData() {
            return cliEnv.userData();
        }
    }
}
