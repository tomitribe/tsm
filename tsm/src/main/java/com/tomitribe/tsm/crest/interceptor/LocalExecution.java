/*
 * Tomitribe Confidential
 *
 * Copyright Tomitribe Corporation. 2015
 *
 * The source code for this program is not published or otherwise divested
 * of its trade secrets, irrespective of what has been deposited with the
 * U.S. Copyright Office.
 */
package com.tomitribe.tsm.crest.interceptor;

import com.tomitribe.tsm.configuration.GlobalConfiguration;
import com.tomitribe.tsm.configuration.SshKey;
import com.tomitribe.tsm.ssh.EmbeddedSshServer;
import lombok.NoArgsConstructor;
import org.tomitribe.crest.api.interceptor.CrestContext;
import org.tomitribe.crest.api.interceptor.CrestInterceptor;
import org.tomitribe.crest.api.interceptor.ParameterMetadata;
import org.tomitribe.crest.environments.Environment;

import static lombok.AccessLevel.PRIVATE;

// when execution is local (--local) force environment to be "local"
@NoArgsConstructor(access = PRIVATE)
public class LocalExecution {
    @CrestInterceptor
    public static Object intercept(final CrestContext context) {
        final Environment environment = Environment.ENVIRONMENT_THREAD_LOCAL.get();
        final boolean notLocal = !environment.findService(GlobalConfiguration.class).isLocal();
        if (notLocal) {
            return context.proceed();
        }

        int i = 0;
        for (final ParameterMetadata meta : context.getParameterMetadata()) {
            if (meta.getReflectType() == SshKey.class) {
                context.getParameters().set(i, environment.findService(EmbeddedSshServer.class).getKey());
            } else if ("environment".equals(meta.getName())) {
                context.getParameters().set(i, "local");
            }
            i++;
        }
        return context.proceed();
    }
}
