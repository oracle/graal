/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.graal.debug;

import static com.oracle.graal.debug.Debug.Initialization.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

import com.oracle.graal.debug.internal.*;

/**
 * Scope based debugging facility. This facility is {@link #isEnabled()} if assertions are enabled
 * for the {@link Debug} class or the {@value Initialization#INITIALIZER_PROPERTY_NAME} system
 * property is {@code "true"} when {@link Debug} is initialized.
 */
public class Debug {

    /**
     * Class to assist with initialization of {@link Debug}.
     */
    public static class Initialization {

        public static final String INITIALIZER_PROPERTY_NAME = "graal.debug.enable";

        private static boolean initialized;

        /**
         * Determines if {@link Debug} has been initialized.
         */
        public static boolean isDebugInitialized() {
            return initialized;
        }

    }

    @SuppressWarnings("all")
    private static boolean initialize() {
        boolean assertionsEnabled = false;
        assert assertionsEnabled = true;
        Initialization.initialized = true;
        return assertionsEnabled || Boolean.getBoolean(INITIALIZER_PROPERTY_NAME);
    }

    private static final boolean ENABLED = initialize();

    public static boolean isEnabled() {
        return ENABLED;
    }

    public static boolean isDumpEnabledForMethod() {
        if (!ENABLED) {
            return false;
        }
        DebugConfig config = DebugScope.getConfig();
        if (config == null) {
            return false;
        }
        return config.isDumpEnabledForMethod();
    }

    public static boolean isDumpEnabled() {
        return ENABLED && DebugScope.getInstance().isDumpEnabled();
    }

    public static boolean isMeterEnabled() {
        return ENABLED && DebugScope.getInstance().isMeterEnabled();
    }

    public static boolean isTimeEnabled() {
        return ENABLED && DebugScope.getInstance().isTimeEnabled();
    }

    public static boolean isLogEnabledForMethod() {
        if (!ENABLED) {
            return false;
        }
        DebugConfig config = DebugScope.getConfig();
        if (config == null) {
            return false;
        }
        return config.isLogEnabledForMethod();
    }

    public static boolean isLogEnabled() {
        return ENABLED && DebugScope.getInstance().isLogEnabled();
    }

    @SuppressWarnings("unused")
    public static Runnable decorateDebugRoot(Runnable runnable, String name, DebugConfig config) {
        return runnable;
    }

    @SuppressWarnings("unused")
    public static <T> Callable<T> decorateDebugRoot(Callable<T> callable, String name, DebugConfig config) {
        return callable;
    }

    @SuppressWarnings("unused")
    public static Runnable decorateScope(Runnable runnable, String name, Object... context) {
        return runnable;
    }

    @SuppressWarnings("unused")
    public static <T> Callable<T> decorateScope(Callable<T> callable, String name, Object... context) {
        return callable;
    }

    /**
     * Gets a string composed of the names in the current nesting of debug
     * {@linkplain #scope(String, Object...) scopes} separated by {@code '.'}.
     */
    public static String currentScope() {
        if (ENABLED) {
            return DebugScope.getInstance().getQualifiedName();
        } else {
            return "";
        }
    }

    /**
     * Represents a debug scope entered by {@link Debug#scope(String, Object...)} or
     * {@link Debug#sandbox(String, DebugConfig, Object...)}. Leaving the scope is achieved via
     * {@link #close()}.
     */
    public interface Scope extends AutoCloseable {
        void close();
    }

    /**
     * Creates and enters a new debug scope which will be a child of the current debug scope.
     * <p>
     * It is recommended to use the try-with-resource statement for managing entering and leaving
     * debug scopes. For example:
     * 
     * <pre>
     * try (Scope s = Debug.scope(&quot;InliningGraph&quot;, inlineeGraph)) {
     *     ...
     * } catch (Throwable e) {
     *     throw Debug.handle(e);
     * }
     * </pre>
     * 
     * @param name the name of the new scope
     * @param context objects to be appended to the {@linkplain #context() current} debug context
     * @return the scope entered by this method which will be exited when its {@link Scope#close()}
     *         method is called
     */
    public static Scope scope(CharSequence name, Object... context) {
        if (ENABLED) {
            return DebugScope.getInstance().scope(name, null, context);
        } else {
            return null;
        }
    }

    /**
     * Creates and enters a new debug scope which will be disjoint from the current debug scope.
     * <p>
     * It is recommended to use the try-with-resource statement for managing entering and leaving
     * debug scopes. For example:
     * 
     * <pre>
     * try (Scope s = Debug.sandbox(&quot;CompilingStub&quot;, null, stubGraph)) {
     *     ...
     * } catch (Throwable e) {
     *     throw Debug.handle(e);
     * }
     * </pre>
     * 
     * @param name the name of the new scope
     * @param config the debug configuration to use for the new scope
     * @param context objects to be appended to the {@linkplain #context() current} debug context
     * @return the scope entered by this method which will be exited when its {@link Scope#close()}
     *         method is called
     */
    public static Scope sandbox(CharSequence name, DebugConfig config, Object... context) {
        if (ENABLED) {
            DebugConfig sandboxConfig = config == null ? silentConfig() : config;
            return DebugScope.getInstance().scope(name, sandboxConfig, context);
        } else {
            return null;
        }
    }

    public static Scope forceLog() {
        return Debug.sandbox("forceLog", new DelegatingDebugConfig(DebugScope.getConfig()) {
            @Override
            public boolean isLogEnabled() {
                return true;
            }

            @Override
            public boolean isLogEnabledForMethod() {
                return true;
            }
        });
    }

    /**
     * Handles an exception in the context of the debug scope just exited. The just exited scope
     * must have the current scope as its parent which will be the case if the try-with-resource
     * pattern recommended by {@link #scope(String, Object...)} and
     * {@link #sandbox(String, DebugConfig, Object...)} is used
     * 
     * @see #scope(String, Object...)
     * @see #sandbox(String, DebugConfig, Object...)
     */
    public static RuntimeException handle(Throwable exception) {
        if (ENABLED) {
            return DebugScope.getInstance().handle(exception);
        } else {
            if (exception instanceof Error) {
                throw (Error) exception;
            }
            if (exception instanceof RuntimeException) {
                throw (RuntimeException) exception;
            }
            throw new RuntimeException(exception);
        }
    }

    /**
     * Prints an indented message to the current debug scopes's logging stream if logging is enabled
     * in the scope.
     * 
     * @param msg The format string of the log message
     * @param args The arguments referenced by the log message string
     * @see Indent#log
     */
    public static void log(String msg, Object... args) {
        if (ENABLED) {
            DebugScope.getInstance().log(msg, args);
        }
    }

    /**
     * The same as {@link #log}, but without line termination and without indentation.
     */
    public static void printf(String msg, Object... args) {
        if (ENABLED && DebugScope.getInstance().isLogEnabled()) {
            DebugScope.getInstance().printf(msg, args);
        }
    }

    public static void dump(Object object, String msg, Object... args) {
        if (ENABLED && DebugScope.getInstance().isDumpEnabled()) {
            DebugScope.getInstance().dump(object, msg, args);
        }
    }

    private static final class NoLogger implements Indent {

        @Override
        public void log(String msg, Object... args) {
        }

        @Override
        public Indent indent() {
            return this;
        }

        @Override
        public Indent logAndIndent(String msg, Object... args) {
            return this;
        }

        @Override
        public Indent outdent() {
            return this;
        }

        @Override
        public void close() {
        }
    }

    private static final NoLogger noLoggerInstance = new NoLogger();

    /**
     * Creates a new indentation level (by adding some spaces) based on the last used Indent of the
     * current DebugScope.
     * 
     * @return The new indentation level
     * @see Indent#indent
     */
    public static Indent indent() {
        if (ENABLED) {
            DebugScope scope = DebugScope.getInstance();
            return scope.pushIndentLogger();
        }
        return noLoggerInstance;
    }

    /**
     * A convenience function which combines {@link #log} and {@link #indent()}.
     * 
     * @param msg The format string of the log message
     * @param args The arguments referenced by the log message string
     * @return The new indentation level
     * @see Indent#logAndIndent
     */
    public static Indent logAndIndent(String msg, Object... args) {
        if (ENABLED) {
            DebugScope scope = DebugScope.getInstance();
            scope.log(msg, args);
            return scope.pushIndentLogger();
        }
        return noLoggerInstance;
    }

    public static Iterable<Object> context() {
        if (ENABLED) {
            return DebugScope.getInstance().getCurrentContext();
        } else {
            return Collections.emptyList();
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> List<T> contextSnapshot(Class<T> clazz) {
        if (ENABLED) {
            List<T> result = new ArrayList<>();
            for (Object o : context()) {
                if (clazz.isInstance(o)) {
                    result.add((T) o);
                }
            }
            return result;
        } else {
            return Collections.emptyList();
        }
    }

    /**
     * Searches the current debug scope, bottom up, for a context object that is an instance of a
     * given type. The first such object found is returned.
     */
    @SuppressWarnings("unchecked")
    public static <T> T contextLookup(Class<T> clazz) {
        if (ENABLED) {
            for (Object o : context()) {
                if (clazz.isInstance(o)) {
                    return ((T) o);
                }
            }
        }
        return null;
    }

    /**
     * Creates a {@linkplain DebugMetric metric} that is enabled iff debugging is
     * {@linkplain #isEnabled() enabled} or the system property whose name is formed by adding to
     * {@value #ENABLE_METRIC_PROPERTY_NAME_PREFIX} to {@code name} is
     * {@linkplain Boolean#getBoolean(String) true}. If the latter condition is true, then the
     * returned metric is {@linkplain DebugMetric#isConditional() unconditional} otherwise it is
     * conditional.
     * <p>
     * A disabled metric has virtually no overhead.
     */
    public static DebugMetric metric(CharSequence name) {
        if (enabledMetrics != null && enabledMetrics.contains(name.toString())) {
            return new MetricImpl(name.toString(), false);
        } else if (ENABLED) {
            return new MetricImpl(name.toString(), true);
        } else {
            return VOID_METRIC;
        }
    }

    /**
     * Changes the debug configuration for the current thread.
     * 
     * @param config new configuration to use for the current thread
     * @return an object that when {@linkplain DebugConfigScope#close() closed} will restore the
     *         debug configuration for the current thread to what it was before this method was
     *         called
     */
    public static DebugConfigScope setConfig(DebugConfig config) {
        if (ENABLED) {
            return new DebugConfigScope(config);
        } else {
            return null;
        }
    }

    /**
     * Creates an object for counting value frequencies.
     */
    public static DebugHistogram createHistogram(String name) {
        return new DebugHistogramImpl(name);
    }

    public static DebugConfig silentConfig() {
        return fixedConfig(false, false, false, false, Collections.<DebugDumpHandler> emptyList(), System.out);
    }

    public static DebugConfig fixedConfig(final boolean isLogEnabled, final boolean isDumpEnabled, final boolean isMeterEnabled, final boolean isTimerEnabled,
                    final Collection<DebugDumpHandler> dumpHandlers, final PrintStream output) {
        return new DebugConfig() {

            @Override
            public boolean isLogEnabled() {
                return isLogEnabled;
            }

            public boolean isLogEnabledForMethod() {
                return isLogEnabled;
            }

            @Override
            public boolean isMeterEnabled() {
                return isMeterEnabled;
            }

            @Override
            public boolean isDumpEnabled() {
                return isDumpEnabled;
            }

            public boolean isDumpEnabledForMethod() {
                return isDumpEnabled;
            }

            @Override
            public boolean isTimeEnabled() {
                return isTimerEnabled;
            }

            @Override
            public RuntimeException interceptException(Throwable e) {
                return null;
            }

            @Override
            public Collection<DebugDumpHandler> dumpHandlers() {
                return dumpHandlers;
            }

            @Override
            public PrintStream output() {
                return output;
            }

            @Override
            public void addToContext(Object o) {
            }

            @Override
            public void removeFromContext(Object o) {
            }
        };
    }

    private static final DebugMetric VOID_METRIC = new DebugMetric() {

        public void increment() {
        }

        public void add(long value) {
        }

        public void setConditional(boolean flag) {
            throw new InternalError("Cannot make void metric conditional");
        }

        public boolean isConditional() {
            return false;
        }

        public long getCurrentValue() {
            return 0L;
        }
    };

    /**
     * @see #timer(CharSequence)
     */
    public static final String ENABLE_TIMER_PROPERTY_NAME_PREFIX = "graal.debug.timer.";

    /**
     * @see #metric(CharSequence)
     */
    public static final String ENABLE_METRIC_PROPERTY_NAME_PREFIX = "graal.debug.metric.";

    private static final Set<String> enabledMetrics;
    private static final Set<String> enabledTimers;
    static {
        Set<String> metrics = new HashSet<>();
        Set<String> timers = new HashSet<>();
        for (Map.Entry<Object, Object> e : System.getProperties().entrySet()) {
            String name = e.getKey().toString();
            if (name.startsWith(ENABLE_METRIC_PROPERTY_NAME_PREFIX) && Boolean.parseBoolean(e.getValue().toString())) {
                metrics.add(name.substring(ENABLE_METRIC_PROPERTY_NAME_PREFIX.length()));
            }
            if (name.startsWith(ENABLE_TIMER_PROPERTY_NAME_PREFIX) && Boolean.parseBoolean(e.getValue().toString())) {
                timers.add(name.substring(ENABLE_TIMER_PROPERTY_NAME_PREFIX.length()));
            }
        }
        enabledMetrics = metrics.isEmpty() ? null : metrics;
        enabledTimers = timers.isEmpty() ? null : timers;
    }

    /**
     * Creates a {@linkplain DebugTimer timer} that is enabled iff debugging is
     * {@linkplain #isEnabled() enabled} or the system property whose name is formed by adding to
     * {@value #ENABLE_TIMER_PROPERTY_NAME_PREFIX} to {@code name} is
     * {@linkplain Boolean#getBoolean(String) true}. If the latter condition is true, then the
     * returned timer is {@linkplain DebugMetric#isConditional() unconditional} otherwise it is
     * conditional.
     * <p>
     * A disabled timer has virtually no overhead.
     */
    public static DebugTimer timer(CharSequence name) {
        if (enabledTimers != null && enabledTimers.contains(name.toString())) {
            return new TimerImpl(name.toString(), false);
        } else if (ENABLED) {
            return new TimerImpl(name.toString(), true);
        } else {
            return VOID_TIMER;
        }
    }

    private static final DebugTimer VOID_TIMER = new DebugTimer() {

        public TimerCloseable start() {
            return TimerImpl.VOID_CLOSEABLE;
        }

        public void setConditional(boolean flag) {
            throw new InternalError("Cannot make void timer conditional");
        }

        public boolean isConditional() {
            return false;
        }

        public long getCurrentValue() {
            return 0L;
        }

        public TimeUnit getTimeUnit() {
            return null;
        }
    };
}
