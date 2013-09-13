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

    public static boolean isDumpEnabled() {
        return ENABLED && DebugScope.getInstance().isDumpEnabled();
    }

    public static boolean isMeterEnabled() {
        return ENABLED && DebugScope.getInstance().isMeterEnabled();
    }

    public static boolean isTimeEnabled() {
        return ENABLED && DebugScope.getInstance().isTimeEnabled();
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

    public static void sandbox(String name, DebugConfig config, Runnable runnable) {
        if (ENABLED) {
            DebugScope.getInstance().scope(name, runnable, null, true, config, new Object[0]);
        } else {
            runnable.run();
        }
    }

    /**
     * Creates a new debug scope that is unrelated to the current scope and runs a given task in the
     * new scope.
     * 
     * @param name new scope name
     * @param context the context objects of the new scope
     * @param config the debug configuration to use for the new scope
     * @param runnable the task to run in the new scope
     */
    public static void sandbox(String name, Object[] context, DebugConfig config, Runnable runnable) {
        if (ENABLED) {
            DebugScope.getInstance().scope(name, runnable, null, true, config, context);
        } else {
            runnable.run();
        }
    }

    /**
     * Creates a new debug scope that is unrelated to the current scope and runs a given task in the
     * new scope.
     * 
     * @param name new scope name
     * @param context the context objects of the new scope
     * @param config the debug configuration to use for the new scope
     * @param callable the task to run in the new scope
     */
    public static <T> T sandbox(String name, Object[] context, DebugConfig config, Callable<T> callable) {
        if (ENABLED) {
            return DebugScope.getInstance().scope(name, null, callable, true, config, context);
        } else {
            return DebugScope.call(callable);
        }
    }

    public static void scope(String name, Runnable runnable) {
        scope(name, new Object[0], runnable);
    }

    public static <T> T scope(String name, Callable<T> callable) {
        return scope(name, new Object[0], callable);
    }

    public static void scope(String name, Object context, Runnable runnable) {
        scope(name, new Object[]{context}, runnable);
    }

    public static void scope(String name, Object[] context, Runnable runnable) {
        if (ENABLED) {
            DebugScope.getInstance().scope(name, runnable, null, false, null, context);
        } else {
            runnable.run();
        }
    }

    public static String currentScope() {
        if (ENABLED) {
            return DebugScope.getInstance().getQualifiedName();
        } else {
            return "";
        }
    }

    public static <T> T scope(String name, Object context, Callable<T> callable) {
        return scope(name, new Object[]{context}, callable);
    }

    public static <T> T scope(String name, Object[] context, Callable<T> callable) {
        if (ENABLED) {
            return DebugScope.getInstance().scope(name, null, callable, false, null, context);
        } else {
            return DebugScope.call(callable);
        }
    }

    /**
     * Prints an indented message to the current DebugLevel's logging stream if logging is enabled.
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
        public void setEnabled(boolean enabled) {
        }

        @Override
        public Indent indent() {
            return this;
        }

        @Override
        public Indent logIndent(String msg, Object... args) {
            return this;
        }

        @Override
        public Indent outdent() {
            return this;
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
     * Creates a new indentation level based on the last used Indent of the current DebugScope and
     * turns on/off logging.
     * 
     * @param enabled If true, logging is enabled, otherwise disabled
     * @return The new indentation level
     */
    public static Indent indent(boolean enabled) {
        if (ENABLED) {
            Indent logger = DebugScope.getInstance().pushIndentLogger();
            logger.setEnabled(enabled);
            return logger;
        }
        return noLoggerInstance;
    }

    /**
     * A convenience function which combines {@link #log} and {@link #indent()}.
     * 
     * @param msg The format string of the log message
     * @param args The arguments referenced by the log message string
     * @return The new indentation level
     * @see Indent#logIndent
     */
    public static Indent logIndent(String msg, Object... args) {
        if (ENABLED) {
            DebugScope scope = DebugScope.getInstance();
            scope.log(msg, args);
            return scope.pushIndentLogger();
        }
        return noLoggerInstance;
    }

    /**
     * A convenience function which combines {@link #log} and {@link #indent(boolean)}.
     * 
     * @param enabled If true, logging is enabled, otherwise disabled
     * @param msg The format string of the log message
     * @param args The arguments referenced by the log message string
     * @return The new indentation level
     */
    public static Indent logIndent(boolean enabled, String msg, Object... args) {
        if (ENABLED) {
            DebugScope scope = DebugScope.getInstance();
            boolean saveLogEnabled = scope.isLogEnabled();
            scope.setLogEnabled(enabled);
            scope.log(msg, args);
            scope.setLogEnabled(saveLogEnabled);
            Indent indent = scope.pushIndentLogger();
            indent.setEnabled(enabled);
            return indent;
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

    public static DebugMetric metric(String name) {
        if (ENABLED) {
            return new MetricImpl(name, true);
        } else {
            return VOID_METRIC;
        }
    }

    public static void setConfig(DebugConfig config) {
        if (ENABLED) {
            DebugScope.getInstance().setConfig(config);
        }
    }

    /**
     * Creates an object for counting value frequencies.
     */
    public static DebugHistogram createHistogram(String name) {
        return new DebugHistogramImpl(name);
    }

    public static DebugConfig fixedConfig(final boolean isLogEnabled, final boolean isDumpEnabled, final boolean isMeterEnabled, final boolean isTimerEnabled,
                    final Collection<DebugDumpHandler> dumpHandlers, final PrintStream output) {
        return new DebugConfig() {

            @Override
            public boolean isLogEnabled() {
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
    };

    public static DebugTimer timer(String name) {
        if (ENABLED) {
            return new TimerImpl(name, true);
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
    };
}
