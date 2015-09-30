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
import lombok.NoArgsConstructor;
import org.tomitribe.crest.api.interceptor.CrestContext;
import org.tomitribe.crest.api.interceptor.CrestInterceptor;
import org.tomitribe.crest.api.interceptor.ParameterMetadata;
import org.tomitribe.crest.cli.api.interceptor.base.ParameterVisitor;
import org.tomitribe.crest.environments.Environment;

import java.lang.reflect.AnnotatedElement;

import static lombok.AccessLevel.PRIVATE;

// uses GlobalConfiguration to get config from .tsmrc if some parameters are not set
@NoArgsConstructor(access = PRIVATE)
public class DefaultParameters {
    @CrestInterceptor
    public static Object intercept(final CrestContext context) {
        ParameterVisitor.visit(context, new ParameterVisitor.DefaultOptionVisitor() {
            private GlobalConfiguration configuration;

            @Override
            public Object doOnOption(final int i, final ParameterMetadata meta, final AnnotatedElement elt) {
                if (configuration == null) {
                    configuration = Environment.ENVIRONMENT_THREAD_LOCAL.get().findService(GlobalConfiguration.class);
                }
                return configuration.read(meta.getName());
            }
        });
        return context.proceed();
    }
}
