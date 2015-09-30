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
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.tomitribe.crest.Main;
import org.tomitribe.crest.api.Exit;
import org.tomitribe.crest.cli.api.CliEnvironment;
import org.tomitribe.crest.cli.api.CrestCli;
import org.tomitribe.crest.cmds.CommandFailedException;
import org.tomitribe.crest.environments.Environment;
import org.tomitribe.crest.environments.SystemEnvironment;

import java.io.File;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Arrays.asList;
import static java.util.Optional.ofNullable;
import static lombok.AccessLevel.PRIVATE;

@NoArgsConstructor(access = PRIVATE)
public class Tsm {
    public static void main(final String[] args) throws Exception {
        final String tsmRc = ofNullable(System.getProperty("tsm.rc.location"))
            .orElseGet(() -> new File(System.getProperty("user.home"), ".tsmrc").getAbsolutePath());

        final GlobalConfiguration configuration = new GlobalConfiguration(new File(tsmRc));

        if (args != null && args.length > 0 && "-i".equals(args[0])) {
            final Collection<String> options = new ArrayList<>(asList(args));
            options.remove("-i");

            new CrestCli() {
                private final String prompt = "tsm @ " + Tsm.class.getPackage().getImplementationVersion() + "$ ";

                @Override
                protected String nextPrompt() {
                    return prompt;
                }

                @Override
                protected CliEnvironment createMainEnvironment(final AtomicReference<InputReader> input) {
                    return new TsmEnvironment(super.createMainEnvironment(input), configuration);
                }
            }.run(options.toArray(new String[options.size()]));
        } else { // single command
            final Map<Class<?>, Object> services = new HashMap<>();
            services.put(GlobalConfiguration.class, configuration);

            final SystemEnvironment env = new SystemEnvironment(services);
            Environment.ENVIRONMENT_THREAD_LOCAL.set(env); // before next line otherwise meta are wrong
            try {
                new Main().main(env, args);
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
    }

    @RequiredArgsConstructor
    private static class TsmEnvironment implements CliEnvironment {
        private final CliEnvironment cliEnv;
        private final GlobalConfiguration configuration;

        @Override
        public String readInput(final String s) {
            return cliEnv.readInput(s);
        }

        @Override
        public String readPassword(final String s) {
            return cliEnv.readPassword(s);
        }

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
            return service == GlobalConfiguration.class ? service.cast(configuration) : cliEnv.findService(service);
        }
    }
}
