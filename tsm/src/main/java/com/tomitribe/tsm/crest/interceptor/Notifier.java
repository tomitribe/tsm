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

import com.tomitribe.tsm.listener.Listeners;
import lombok.NoArgsConstructor;
import org.tomitribe.crest.api.Option;
import org.tomitribe.crest.api.interceptor.CrestContext;
import org.tomitribe.crest.api.interceptor.CrestInterceptor;
import org.tomitribe.crest.environments.Environment;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.UUID;

import static lombok.AccessLevel.PRIVATE;

@NoArgsConstructor(access = PRIVATE)
public class Notifier {
    @CrestInterceptor
    public static Object intercept(final CrestContext context) throws Throwable {
        final String id = UUID.randomUUID().toString();
        final Method method = context.getMethod();
        String constantText = context.getName();
        String description = "";
        for (int i = 0; i < method.getParameterCount(); i++) {
            final Parameter parameter = method.getParameters()[i];
            if (parameter.isAnnotationPresent(Description.class)) {
                if (!description.isEmpty()) {
                    throw new IllegalArgumentException("Multiple @Description on " + method);
                }
                description = String.valueOf(context.getParameters().get(i));
                constantText = constantText + " '" + description + "'";
            } else {
                final Option option = parameter.getAnnotation(Option.class);
                if (option != null && "environment".equals(option.value()[0])) {
                    constantText = "(environment=" + String.valueOf(context.getParameters().get(i)) + ") " + constantText;
                }
            }
        }

        notify(id, description, "Executing " + constantText);
        Throwable failed = null;
        try {
            return context.proceed();
        } catch (final Throwable oops) {
            failed = oops;
            throw oops;
        } finally {
            notify(id, description, "Executed " + constantText + ", status=" + (failed != null ? "FAILED (" + failed.getMessage() + ")" : "SUCCESS"));
        }
    }

    private static void notify(final String id, final String marker, final String message) {
        Environment.ENVIRONMENT_THREAD_LOCAL.get().findService(Listeners.class)
                .sendMessage(marker, "[id=" + id + "][user=" + System.getProperty("user.name", "unknown") + "] " + message);
    }

    /**
     * Marks a <b>single</b> parameter of the method as being used in the notification as human identifier.
     *
     * Note: if needed later we can support something like @Description("%1s -> %2s") using Formatter but this is more fragile.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    public @interface Description {
    }
}
