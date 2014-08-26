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
import static com.oracle.graal.debug.DelegatingDebugConfig.Feature.*;
import static java.util.FormattableFlags.*;

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

    public static final int DEFAULT_LOG_LEVEL = 2;

    public static boolean isDumpEnabled() {
        return isDumpEnabled(DEFAULT_LOG_LEVEL);
    }

    public static boolean isDumpEnabled(int dumpLevel) {
        return ENABLED && DebugScope.getInstance().isDumpEnabled(dumpLevel);
    }

    /**
     * Determines if verification is enabled in the current method, regardless of the
     * {@linkplain Debug#currentScope() current debug scope}.
     *
     * @see Debug#verify(Object, String)
     */
    public static boolean isVerifyEnabledForMethod() {
        if (!ENABLED) {
            return false;
        }
        DebugConfig config = DebugScope.getConfig();
        if (config == null) {
            return false;
        }
        return config.isVerifyEnabledForMethod();
    }

    /**
     * Determines if verification is enabled in the {@linkplain Debug#currentScope() current debug
     * scope}.
     *
     * @see Debug#verify(Object, String)
     */
    public static boolean isVerifyEnabled() {
        return ENABLED && DebugScope.getInstance().isVerifyEnabled();
    }

    public static boolean isMeterEnabled() {
        return ENABLED && DebugScope.getInstance().isMeterEnabled();
    }

    public static boolean isTimeEnabled() {
        return ENABLED && DebugScope.getInstance().isTimeEnabled();
    }

    public static boolean isMemUseTrackingEnabled() {
        return ENABLED && DebugScope.getInstance().isMemUseTrackingEnabled();
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
        return isLogEnabled(DEFAULT_LOG_LEVEL);
    }

    public static boolean isLogEnabled(int logLevel) {
        return ENABLED && DebugScope.getInstance().isLogEnabled(logLevel);
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
     * {@linkplain #scope(Object) scopes} separated by {@code '.'}.
     */
    public static String currentScope() {
        if (ENABLED) {
            return DebugScope.getInstance().getQualifiedName();
        } else {
            return "";
        }
    }

    /**
     * Represents a debug scope entered by {@link Debug#scope(Object)} or
     * {@link Debug#sandbox(CharSequence, DebugConfig, Object...)}. Leaving the scope is achieved
     * via {@link #close()}.
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
     * The {@code name} argument is subject to the following type based conversion before having
     * {@link Object#toString()} called on it:
     *
     * <pre>
     *     Type          | Conversion
     * ------------------+-----------------
     *  java.lang.Class  | arg.getSimpleName()
     *                   |
     * </pre>
     *
     * @param name the name of the new scope
     * @return the scope entered by this method which will be exited when its {@link Scope#close()}
     *         method is called
     */
    public static Scope scope(Object name) {
        if (ENABLED) {
            return DebugScope.getInstance().scope(convertFormatArg(name).toString(), null);
        } else {
            return null;
        }
    }

    /**
     * @see #scope(Object)
     * @param contextObjects an array of object to be appended to the {@linkplain #context()
     *            current} debug context
     */
    public static Scope scope(Object name, Object[] contextObjects) {
        if (ENABLED) {
            return DebugScope.getInstance().scope(convertFormatArg(name).toString(), null, contextObjects);
        } else {
            return null;
        }
    }

    /**
     * @see #scope(Object)
     * @param context an object to be appended to the {@linkplain #context() current} debug context
     */
    public static Scope scope(Object name, Object context) {
        if (ENABLED) {
            return DebugScope.getInstance().scope(convertFormatArg(name).toString(), null, context);
        } else {
            return null;
        }
    }

    /**
     * @see #scope(Object)
     * @param context1 first object to be appended to the {@linkplain #context() current} debug
     *            context
     * @param context2 second object to be appended to the {@linkplain #context() current} debug
     *            context
     */
    public static Scope scope(Object name, Object context1, Object context2) {
        if (ENABLED) {
            return DebugScope.getInstance().scope(convertFormatArg(name).toString(), null, context1, context2);
        } else {
            return null;
        }
    }

    /**
     * @see #scope(Object)
     * @param context1 first object to be appended to the {@linkplain #context() current} debug
     *            context
     * @param context2 second object to be appended to the {@linkplain #context() current} debug
     *            context
     * @param context3 third object to be appended to the {@linkplain #context() current} debug
     *            context
     */
    public static Scope scope(Object name, Object context1, Object context2, Object context3) {
        if (ENABLED) {
            return DebugScope.getInstance().scope(convertFormatArg(name).toString(), null, context1, context2, context3);
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
        ArrayList<Object> context = new ArrayList<>();
        for (Object obj : context()) {
            context.add(obj);
        }
        return Debug.sandbox("forceLog", new DelegatingDebugConfig().enable(LOG).enable(LOG_METHOD), context.toArray());
    }

    /**
     * Opens a scope in which exception {@linkplain DebugConfig#interceptException(Throwable)
     * interception} is disabled. It is recommended to use the try-with-resource statement for
     * managing entering and leaving such scopes:
     *
     * <pre>
     * try (DebugConfigScope s = Debug.disableIntercept()) {
     *     ...
     * }
     * </pre>
     *
     * This is particularly useful to suppress extraneous output in JUnit tests that are expected to
     * throw an exception.
     */
    public static DebugConfigScope disableIntercept() {
        return Debug.setConfig(new DelegatingDebugConfig().disable(INTERCEPT));
    }

    /**
     * Handles an exception in the context of the debug scope just exited. The just exited scope
     * must have the current scope as its parent which will be the case if the try-with-resource
     * pattern recommended by {@link #scope(Object)} and
     * {@link #sandbox(CharSequence, DebugConfig, Object...)} is used
     *
     * @see #scope(Object)
     * @see #sandbox(CharSequence, DebugConfig, Object...)
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

    public static void log(String msg) {
        log(DEFAULT_LOG_LEVEL, msg);
    }

    /**
     * Prints a message to the current debug scope's logging stream if logging is enabled.
     *
     * @param msg the message to log
     */
    public static void log(int logLevel, String msg) {
        if (ENABLED) {
            DebugScope.getInstance().log(logLevel, msg);
        }
    }

    public static void log(String format, Object arg) {
        log(DEFAULT_LOG_LEVEL, format, arg);
    }

    /**
     * Prints a message to the current debug scope's logging stream if logging is enabled.
     *
     * @param format a format string
     * @param arg the argument referenced by the format specifiers in {@code format}
     */
    public static void log(int logLevel, String format, Object arg) {
        if (ENABLED) {
            DebugScope.getInstance().log(logLevel, format, arg);
        }
    }

    public static void log(String format, Object arg1, Object arg2) {
        log(DEFAULT_LOG_LEVEL, format, arg1, arg2);
    }

    /**
     * @see #log(int, String, Object)
     */
    public static void log(int logLevel, String format, Object arg1, Object arg2) {
        if (ENABLED) {
            DebugScope.getInstance().log(logLevel, format, arg1, arg2);
        }
    }

    public static void log(String format, Object arg1, Object arg2, Object arg3) {
        log(DEFAULT_LOG_LEVEL, format, arg1, arg2, arg3);
    }

    /**
     * @see #log(int, String, Object)
     */
    public static void log(int logLevel, String format, Object arg1, Object arg2, Object arg3) {
        if (ENABLED) {
            DebugScope.getInstance().log(logLevel, format, arg1, arg2, arg3);
        }
    }

    public static void log(String format, Object arg1, Object arg2, Object arg3, Object arg4) {
        log(DEFAULT_LOG_LEVEL, format, arg1, arg2, arg3, arg4);
    }

    /**
     * @see #log(int, String, Object)
     */
    public static void log(int logLevel, String format, Object arg1, Object arg2, Object arg3, Object arg4) {
        if (ENABLED) {
            DebugScope.getInstance().log(logLevel, format, arg1, arg2, arg3, arg4);
        }
    }

    public static void log(String format, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5) {
        log(DEFAULT_LOG_LEVEL, format, arg1, arg2, arg3, arg4, arg5);
    }

    /**
     * @see #log(int, String, Object)
     */
    public static void log(int logLevel, String format, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5) {
        if (ENABLED) {
            DebugScope.getInstance().log(logLevel, format, arg1, arg2, arg3, arg4, arg5);
        }
    }

    public static void log(String format, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6) {
        log(DEFAULT_LOG_LEVEL, format, arg1, arg2, arg3, arg4, arg5, arg6);
    }

    /**
     * @see #log(int, String, Object)
     */
    public static void log(int logLevel, String format, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6) {
        if (ENABLED) {
            DebugScope.getInstance().log(logLevel, format, arg1, arg2, arg3, arg4, arg5, arg6);
        }
    }

    public static void logv(String format, Object... args) {
        logv(DEFAULT_LOG_LEVEL, format, args);
    }

    /**
     * Prints a message to the current debug scope's logging stream. This method must only be called
     * if debugging is {@linkplain Debug#isEnabled() enabled} as it incurs allocation at the call
     * site. If possible, call one of the other {@code log()} methods in this class that take a
     * fixed number of parameters.
     *
     * @param format a format string
     * @param args the arguments referenced by the format specifiers in {@code format}
     */
    public static void logv(int logLevel, String format, Object... args) {
        if (!ENABLED) {
            throw new InternalError("Use of Debug.logv() must be guarded by a test of Debug.isEnabled()");
        }
        DebugScope.getInstance().log(logLevel, format, args);
    }

    /**
     * This override exists to catch cases when {@link #log(String, Object)} is called with one
     * argument bound to a varargs method parameter. It will bind to this method instead of the
     * single arg variant and produce a deprecation warning instead of silently wrapping the
     * Object[] inside of another Object[].
     */
    @Deprecated
    public static void log(String format, Object[] args) {
        assert false : "shouldn't use this";
        log(DEFAULT_LOG_LEVEL, format, args);
    }

    /**
     * This override exists to catch cases when {@link #log(int, String, Object)} is called with one
     * argument bound to a varargs method parameter. It will bind to this method instead of the
     * single arg variant and produce a deprecation warning instead of silently wrapping the
     * Object[] inside of another Object[].
     */
    @Deprecated
    public static void log(int logLevel, String format, Object[] args) {
        assert false : "shouldn't use this";
        logv(logLevel, format, args);
    }

    public static void dump(Object object, String msg) {
        dump(DEFAULT_LOG_LEVEL, object, msg);
    }

    public static void dump(int dumpLevel, Object object, String msg) {
        if (ENABLED && DebugScope.getInstance().isDumpEnabled(dumpLevel)) {
            DebugScope.getInstance().dump(dumpLevel, object, msg);
        }
    }

    public static void dump(Object object, String format, Object arg) {
        dump(DEFAULT_LOG_LEVEL, object, format, arg);
    }

    public static void dump(int dumpLevel, Object object, String format, Object arg) {
        if (ENABLED && DebugScope.getInstance().isDumpEnabled(dumpLevel)) {
            DebugScope.getInstance().dump(dumpLevel, object, format, arg);
        }
    }

    public static void dump(Object object, String format, Object arg1, Object arg2) {
        dump(DEFAULT_LOG_LEVEL, object, format, arg1, arg2);
    }

    public static void dump(int dumpLevel, Object object, String format, Object arg1, Object arg2) {
        if (ENABLED && DebugScope.getInstance().isDumpEnabled(dumpLevel)) {
            DebugScope.getInstance().dump(dumpLevel, object, format, arg1, arg2);
        }
    }

    public static void dump(Object object, String format, Object arg1, Object arg2, Object arg3) {
        dump(DEFAULT_LOG_LEVEL, object, format, arg1, arg2, arg3);
    }

    public static void dump(int dumpLevel, Object object, String format, Object arg1, Object arg2, Object arg3) {
        if (ENABLED && DebugScope.getInstance().isDumpEnabled(dumpLevel)) {
            DebugScope.getInstance().dump(dumpLevel, object, format, arg1, arg2, arg3);
        }
    }

    /**
     * This override exists to catch cases when {@link #dump(Object, String, Object)} is called with
     * one argument bound to a varargs method parameter. It will bind to this method instead of the
     * single arg variant and produce a deprecation warning instead of silently wrapping the
     * Object[] inside of another Object[].
     */
    @Deprecated
    public static void dump(Object object, String format, Object[] args) {
        assert false : "shouldn't use this";
        dump(DEFAULT_LOG_LEVEL, object, format, args);
    }

    /**
     * This override exists to catch cases when {@link #dump(int, Object, String, Object)} is called
     * with one argument bound to a varargs method parameter. It will bind to this method instead of
     * the single arg variant and produce a deprecation warning instead of silently wrapping the
     * Object[] inside of another Object[].
     */
    @Deprecated
    public static void dump(int dumpLevel, Object object, String format, Object[] args) {
        assert false : "shouldn't use this";
        if (ENABLED && DebugScope.getInstance().isDumpEnabled(dumpLevel)) {
            DebugScope.getInstance().dump(dumpLevel, object, format, args);
        }
    }

    /**
     * Calls all {@link DebugVerifyHandler}s in the current {@linkplain DebugScope#getConfig()
     * config} to perform verification on a given object.
     *
     * @param object object to verify
     * @param message description of verification context
     *
     * @see DebugVerifyHandler#verify(Object, String)
     */
    public static void verify(Object object, String message) {
        if (ENABLED && DebugScope.getInstance().isVerifyEnabled()) {
            DebugScope.getInstance().verify(object, message);
        }
    }

    /**
     * Calls all {@link DebugVerifyHandler}s in the current {@linkplain DebugScope#getConfig()
     * config} to perform verification on a given object.
     *
     * @param object object to verify
     * @param format a format string for the description of the verification context
     * @param arg the argument referenced by the format specifiers in {@code format}
     *
     * @see DebugVerifyHandler#verify(Object, String)
     */
    public static void verify(Object object, String format, Object arg) {
        if (ENABLED && DebugScope.getInstance().isVerifyEnabled()) {
            DebugScope.getInstance().verify(object, format, arg);
        }
    }

    /**
     * This override exists to catch cases when {@link #verify(Object, String, Object)} is called
     * with one argument bound to a varargs method parameter. It will bind to this method instead of
     * the single arg variant and produce a deprecation warning instead of silently wrapping the
     * Object[] inside of another Object[].
     */
    @Deprecated
    public static void verify(Object object, String format, Object[] args) {
        assert false : "shouldn't use this";
        if (ENABLED && DebugScope.getInstance().isVerifyEnabled()) {
            DebugScope.getInstance().verify(object, format, args);
        }
    }

    /**
     * Opens a new indentation level (by adding some spaces) based on the current indentation level.
     * This should be used in a {@linkplain Indent try-with-resources} pattern.
     *
     * @return an object that reverts to the current indentation level when
     *         {@linkplain Indent#close() closed} or null if debugging is disabled
     * @see #logAndIndent(int, String)
     * @see #logAndIndent(int, String, Object)
     */
    public static Indent indent() {
        if (ENABLED) {
            DebugScope scope = DebugScope.getInstance();
            return scope.pushIndentLogger();
        }
        return null;
    }

    public static Indent logAndIndent(String msg) {
        return logAndIndent(DEFAULT_LOG_LEVEL, msg);
    }

    /**
     * A convenience function which combines {@link #log(String)} and {@link #indent()}.
     *
     * @param msg the message to log
     * @return an object that reverts to the current indentation level when
     *         {@linkplain Indent#close() closed} or null if debugging is disabled
     */
    public static Indent logAndIndent(int logLevel, String msg) {
        if (ENABLED) {
            return logvAndIndent(logLevel, msg);
        }
        return null;
    }

    public static Indent logAndIndent(String format, Object arg) {
        return logAndIndent(DEFAULT_LOG_LEVEL, format, arg);
    }

    /**
     * A convenience function which combines {@link #log(String, Object)} and {@link #indent()}.
     *
     * @param format a format string
     * @param arg the argument referenced by the format specifiers in {@code format}
     * @return an object that reverts to the current indentation level when
     *         {@linkplain Indent#close() closed} or null if debugging is disabled
     */
    public static Indent logAndIndent(int logLevel, String format, Object arg) {
        if (ENABLED) {
            return logvAndIndent(logLevel, format, arg);
        }
        return null;
    }

    public static Indent logAndIndent(String format, Object arg1, Object arg2) {
        return logAndIndent(DEFAULT_LOG_LEVEL, format, arg1, arg2);
    }

    /**
     * @see #logAndIndent(int, String, Object)
     */
    public static Indent logAndIndent(int logLevel, String format, Object arg1, Object arg2) {
        if (ENABLED) {
            return logvAndIndent(logLevel, format, arg1, arg2);
        }
        return null;
    }

    public static Indent logAndIndent(String format, Object arg1, Object arg2, Object arg3) {
        return logAndIndent(DEFAULT_LOG_LEVEL, format, arg1, arg2, arg3);
    }

    /**
     * @see #logAndIndent(int, String, Object)
     */
    public static Indent logAndIndent(int logLevel, String format, Object arg1, Object arg2, Object arg3) {
        if (ENABLED) {
            return logvAndIndent(logLevel, format, arg1, arg2, arg3);
        }
        return null;
    }

    /**
     * A convenience function which combines {@link #logv(int, String, Object...)} and
     * {@link #indent()}.
     *
     * @param format a format string
     * @param args the arguments referenced by the format specifiers in {@code format}
     * @return an object that reverts to the current indentation level when
     *         {@linkplain Indent#close() closed} or null if debugging is disabled
     */
    public static Indent logvAndIndent(int logLevel, String format, Object... args) {
        if (ENABLED) {
            DebugScope scope = DebugScope.getInstance();
            scope.log(logLevel, format, args);
            return scope.pushIndentLogger();
        }
        throw new InternalError("Use of Debug.logvAndIndent() must be guarded by a test of Debug.isEnabled()");
    }

    /**
     * This override exists to catch cases when {@link #logAndIndent(String, Object)} is called with
     * one argument bound to a varargs method parameter. It will bind to this method instead of the
     * single arg variant and produce a deprecation warning instead of silently wrapping the
     * Object[] inside of another Object[].
     */
    @Deprecated
    public static void logAndIndent(String format, Object[] args) {
        assert false : "shouldn't use this";
        logAndIndent(DEFAULT_LOG_LEVEL, format, args);
    }

    /**
     * This override exists to catch cases when {@link #logAndIndent(int, String, Object)} is called
     * with one argument bound to a varargs method parameter. It will bind to this method instead of
     * the single arg variant and produce a deprecation warning instead of silently wrapping the
     * Object[] inside of another Object[].
     */
    @Deprecated
    public static void logAndIndent(int logLevel, String format, Object[] args) {
        assert false : "shouldn't use this";
        logvAndIndent(logLevel, format, args);
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
     * Creates a {@linkplain DebugMemUseTracker memory use tracker} that is enabled iff debugging is
     * {@linkplain #isEnabled() enabled}.
     * <p>
     * A disabled tracker has virtually no overhead.
     */
    public static DebugMemUseTracker memUseTracker(CharSequence name) {
        if (!ENABLED) {
            return VOID_MEM_USE_TRACKER;
        }
        return createMemUseTracker("%s", name, null);
    }

    /**
     * Creates a debug memory use tracker. Invoking this method is equivalent to:
     *
     * <pre>
     * Debug.memUseTracker(format, arg, null)
     * </pre>
     *
     * except that the string formatting only happens if metering is enabled.
     *
     * @see #metric(String, Object, Object)
     */
    public static DebugMemUseTracker memUseTracker(String format, Object arg) {
        if (!ENABLED) {
            return VOID_MEM_USE_TRACKER;
        }
        return createMemUseTracker(format, arg, null);
    }

    /**
     * Creates a debug memory use tracker. Invoking this method is equivalent to:
     *
     * <pre>
     * Debug.memUseTracker(String.format(format, arg1, arg2))
     * </pre>
     *
     * except that the string formatting only happens if memory use tracking is enabled. In
     * addition, each argument is subject to the following type based conversion before being passed
     * as an argument to {@link String#format(String, Object...)}:
     *
     * <pre>
     *     Type          | Conversion
     * ------------------+-----------------
     *  java.lang.Class  | arg.getSimpleName()
     *                   |
     * </pre>
     *
     * @see #memUseTracker(CharSequence)
     */
    public static DebugMemUseTracker memUseTracker(String format, Object arg1, Object arg2) {
        if (!ENABLED) {
            return VOID_MEM_USE_TRACKER;
        }
        return createMemUseTracker(format, arg1, arg2);
    }

    private static DebugMemUseTracker createMemUseTracker(String format, Object arg1, Object arg2) {
        String name = formatDebugName(format, arg1, arg2);
        return new MemUseTrackerImpl(name);
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
        if (enabledMetrics == null && !ENABLED) {
            return VOID_METRIC;
        }
        return createMetric("%s", name, null);
    }

    public static String applyFormattingFlagsAndWidth(String s, int flags, int width) {
        if (flags == 0 && width < 0) {
            return s;
        }
        StringBuilder sb = new StringBuilder(s);

        // apply width and justification
        int len = sb.length();
        if (len < width) {
            for (int i = 0; i < width - len; i++) {
                if ((flags & LEFT_JUSTIFY) == LEFT_JUSTIFY) {
                    sb.append(' ');
                } else {
                    sb.insert(0, ' ');
                }
            }
        }

        String res = sb.toString();
        if ((flags & UPPERCASE) == UPPERCASE) {
            res = res.toUpperCase();
        }
        return res;
    }

    /**
     * Creates a debug metric. Invoking this method is equivalent to:
     *
     * <pre>
     * Debug.metric(format, arg, null)
     * </pre>
     *
     * except that the string formatting only happens if metering is enabled.
     *
     * @see #metric(String, Object, Object)
     */
    public static DebugMetric metric(String format, Object arg) {
        if (enabledMetrics == null && !ENABLED) {
            return VOID_METRIC;
        }
        return createMetric(format, arg, null);
    }

    /**
     * Creates a debug metric. Invoking this method is equivalent to:
     *
     * <pre>
     * Debug.metric(String.format(format, arg1, arg2))
     * </pre>
     *
     * except that the string formatting only happens if metering is enabled. In addition, each
     * argument is subject to the following type based conversion before being passed as an argument
     * to {@link String#format(String, Object...)}:
     *
     * <pre>
     *     Type          | Conversion
     * ------------------+-----------------
     *  java.lang.Class  | arg.getSimpleName()
     *                   |
     * </pre>
     *
     * @see #metric(CharSequence)
     */
    public static DebugMetric metric(String format, Object arg1, Object arg2) {
        if (enabledMetrics == null && !ENABLED) {
            return VOID_METRIC;
        }
        return createMetric(format, arg1, arg2);
    }

    private static DebugMetric createMetric(String format, Object arg1, Object arg2) {
        String name = formatDebugName(format, arg1, arg2);
        boolean conditional = enabledMetrics == null || !enabledMetrics.contains(name);
        if (!ENABLED && conditional) {
            return VOID_METRIC;
        }
        return new MetricImpl(name, conditional);
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
        return fixedConfig(0, 0, false, false, false, false, Collections.<DebugDumpHandler> emptyList(), Collections.<DebugVerifyHandler> emptyList(), null);
    }

    public static DebugConfig fixedConfig(final int logLevel, final int dumpLevel, final boolean isMeterEnabled, final boolean isMemUseTrackingEnabled, final boolean isTimerEnabled,
                    final boolean isVerifyEnabled, final Collection<DebugDumpHandler> dumpHandlers, final Collection<DebugVerifyHandler> verifyHandlers, final PrintStream output) {
        return new DebugConfig() {

            @Override
            public int getLogLevel() {
                return logLevel;
            }

            public boolean isLogEnabledForMethod() {
                return logLevel > 0;
            }

            @Override
            public boolean isMeterEnabled() {
                return isMeterEnabled;
            }

            @Override
            public boolean isMemUseTrackingEnabled() {
                return isMemUseTrackingEnabled;
            }

            @Override
            public int getDumpLevel() {
                return dumpLevel;
            }

            public boolean isDumpEnabledForMethod() {
                return dumpLevel > 0;
            }

            @Override
            public boolean isVerifyEnabled() {
                return isVerifyEnabled;
            }

            public boolean isVerifyEnabledForMethod() {
                return isVerifyEnabled;
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
            public Collection<DebugVerifyHandler> verifyHandlers() {
                return verifyHandlers;
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

    private static final DebugMemUseTracker VOID_MEM_USE_TRACKER = new DebugMemUseTracker() {

        public Closeable start() {
            return MemUseTrackerImpl.VOID_CLOSEABLE;
        }

        public long getCurrentValue() {
            return 0;
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
        parseMetricAndTimerSystemProperties(metrics, timers);
        enabledMetrics = metrics.isEmpty() ? null : metrics;
        enabledTimers = timers.isEmpty() ? null : timers;
    }

    public static boolean areUnconditionalTimersEnabled() {
        return enabledTimers != null && !enabledTimers.isEmpty();
    }

    public static boolean areUnconditionalMetricsEnabled() {
        return enabledMetrics != null && !enabledMetrics.isEmpty();
    }

    protected static void parseMetricAndTimerSystemProperties(Set<String> metrics, Set<String> timers) {
        do {
            try {
                for (Map.Entry<Object, Object> e : System.getProperties().entrySet()) {
                    String name = e.getKey().toString();
                    if (name.startsWith(ENABLE_METRIC_PROPERTY_NAME_PREFIX) && Boolean.parseBoolean(e.getValue().toString())) {
                        metrics.add(name.substring(ENABLE_METRIC_PROPERTY_NAME_PREFIX.length()));
                    }
                    if (name.startsWith(ENABLE_TIMER_PROPERTY_NAME_PREFIX) && Boolean.parseBoolean(e.getValue().toString())) {
                        timers.add(name.substring(ENABLE_TIMER_PROPERTY_NAME_PREFIX.length()));
                    }
                }
                return;
            } catch (ConcurrentModificationException e) {
                // Iterating over the system properties may race with another thread that is
                // updating the system properties. Simply try again in this case.
            }
        } while (true);
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
        if (enabledTimers == null && !ENABLED) {
            return VOID_TIMER;
        }
        return createTimer("%s", name, null);
    }

    /**
     * Creates a debug timer. Invoking this method is equivalent to:
     *
     * <pre>
     * Debug.timer(format, arg, null)
     * </pre>
     *
     * except that the string formatting only happens if timing is enabled.
     *
     * @see #timer(String, Object, Object)
     */
    public static DebugTimer timer(String format, Object arg) {
        if (enabledTimers == null && !ENABLED) {
            return VOID_TIMER;
        }
        return createTimer(format, arg, null);
    }

    /**
     * Creates a debug timer. Invoking this method is equivalent to:
     *
     * <pre>
     * Debug.timer(String.format(format, arg1, arg2))
     * </pre>
     *
     * except that the string formatting only happens if timing is enabled. In addition, each
     * argument is subject to the following type based conversion before being passed as an argument
     * to {@link String#format(String, Object...)}:
     *
     * <pre>
     *     Type          | Conversion
     * ------------------+-----------------
     *  java.lang.Class  | arg.getSimpleName()
     *                   |
     * </pre>
     *
     * @see #timer(CharSequence)
     */
    public static DebugTimer timer(String format, Object arg1, Object arg2) {
        if (enabledTimers == null && !ENABLED) {
            return VOID_TIMER;
        }
        return createTimer(format, arg1, arg2);
    }

    public static Object convertFormatArg(Object arg) {
        if (arg instanceof Class) {
            Class<?> c = (Class<?>) arg;
            final String simpleName = c.getSimpleName();
            Class<?> enclosingClass = c.getEnclosingClass();
            if (enclosingClass != null) {
                String prefix = "";
                while (enclosingClass != null) {
                    prefix = enclosingClass.getSimpleName() + "_" + prefix;
                    enclosingClass = enclosingClass.getEnclosingClass();
                }
                return prefix + simpleName;
            } else {
                return simpleName;
            }
        }
        return arg;
    }

    private static String formatDebugName(String format, Object arg1, Object arg2) {
        return String.format(format, convertFormatArg(arg1), convertFormatArg(arg2));
    }

    private static DebugTimer createTimer(String format, Object arg1, Object arg2) {
        String name = formatDebugName(format, arg1, arg2);
        boolean conditional = enabledTimers == null || !enabledTimers.contains(name);
        if (!ENABLED && conditional) {
            return VOID_TIMER;
        }
        return new TimerImpl(name, conditional);
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
