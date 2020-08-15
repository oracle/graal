/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api;

import java.io.Closeable;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import org.graalvm.polyglot.Context;

/**
 * Support for logging in Truffle languages and instruments.
 * <p>
 * The logger's {@link Level} configuration is done using the
 * {@link org.graalvm.polyglot.Context.Builder#options(java.util.Map) Context's options}. The level
 * option key has the following format: {@code log.languageId.className.level} or
 * {@code log.instrumentId.className.level}. The value is either the name of pre-defined
 * {@link Level} constant or a numeric {@link Level} value. If not explicitly set in
 * {@link org.graalvm.polyglot.Context.Builder#options(java.util.Map) Context's options} the level
 * is inherited from the parent logger.
 * <p>
 * The {@link TruffleLogger} supports {@link LogRecord#getParameters() message parameters} of
 * primitive types and strings. The object parameters are converted into string value before they
 * are passed to the {@link Handler}.
 * <p>
 * The {@link TruffleLogger} instances are safe to be used on compiled code paths as well as from
 * multiple-threads.
 *
 * @since 19.0
 */
public final class TruffleLogger {

    private static final String ROOT_NAME = "";
    private static final int MAX_CLEANED_REFS = 100;
    private static final int OFF_VALUE = Level.OFF.intValue();
    private static final int DEFAULT_VALUE = Level.INFO.intValue();
    private static final ReferenceQueue<TruffleLogger> loggersRefQueue = new ReferenceQueue<>();
    private static final Object childrenLock = new Object();

    private final String name;
    private final LoggerCache loggerCache;
    @CompilerDirectives.CompilationFinal private volatile int levelNum;
    @CompilerDirectives.CompilationFinal private volatile Assumption levelNumStable;
    private volatile Level levelObj;
    private volatile TruffleLogger parent;
    private Collection<ChildLoggerRef> children;

    private TruffleLogger(final String loggerName, final LoggerCache loggerCache) {
        this.name = loggerName;
        this.loggerCache = loggerCache;
        this.levelNum = DEFAULT_VALUE;
        this.levelNumStable = Truffle.getRuntime().createAssumption("Log Level Value stable for: " + loggerName);
    }

    private TruffleLogger(LoggerCache loggerCache) {
        this(ROOT_NAME, loggerCache);
    }

    /**
     * Find or create a root logger for a given language or instrument. If the root logger for given
     * language or instrument already exists it's returned, otherwise a new root logger is created.
     *
     * @param id the unique id of language or instrument
     * @return a {@link TruffleLogger}
     * @throws NullPointerException if {@code id} is null
     * @throws IllegalArgumentException if {@code id} is not a valid language or instrument id.
     * @since 19.0
     */
    public static TruffleLogger getLogger(final String id) {
        return getLogger(id, null, LoggerCache.getInstance());
    }

    /**
     * Find or create a logger for a given language or instrument class. If a logger for the class
     * already exists it's returned, otherwise a new logger is created.
     *
     * @param id the unique id of language or instrument
     * @param forClass the {@link Class} to create a logger for
     * @return a {@link TruffleLogger}
     * @throws NullPointerException if {@code id} or {@code forClass} is null
     * @throws IllegalArgumentException if {@code id} is not a valid language or instrument id.
     * @since 19.0
     */
    public static TruffleLogger getLogger(final String id, final Class<?> forClass) {
        Objects.requireNonNull(forClass, "Class must be non null.");
        return getLogger(id, forClass.getName());
    }

    /**
     * Find or create a logger for a given language or instrument. If a logger with given name
     * already exists it's returned, otherwise a new logger is created.
     *
     * @param id the unique id of language or instrument
     * @param loggerName the the name of a {@link TruffleLogger}, if a {@code loggerName} is null or
     *            empty a root logger for language or instrument is returned
     * @return a {@link TruffleLogger}
     * @throws NullPointerException if {@code id} is null
     * @throws IllegalArgumentException if {@code id} is not a valid language or instrument id.
     * @since 19.0
     */
    public static TruffleLogger getLogger(final String id, final String loggerName) {
        return getLogger(id, loggerName, LoggerCache.getInstance());
    }

    static TruffleLogger getLogger(String id, String loggerName, LoggerCache loggerCache) {
        Objects.requireNonNull(id, "LanguageId must be non null.");
        return loggerCache.getOrCreateLogger(id, loggerName);
    }

    static LoggerCache createLoggerCache(Object loggerCache, Map<String, Level> logLevels) {
        LoggerCache cache = new LoggerCache(loggerCache);
        if (!logLevels.isEmpty()) {
            Object vmObject = LanguageAccessor.engineAccess().getLoggerOwner(loggerCache);
            assert vmObject != null;
            cache.addLogLevelsForContext(vmObject, logLevels);
        }
        return cache;
    }

    /**
     * Logs a message with {@link Level#CONFIG config level}.
     * <p>
     * If the logger is enabled for the {@link Level#CONFIG config level} the message is sent to the
     * {@link Handler} registered in the current {@link Context}.
     *
     * @param message the message to log
     * @since 19.0
     */
    public void config(final String message) {
        log(Level.CONFIG, message);
    }

    /**
     * Logs a message with {@link Level#CONFIG config level}. The message is constructed only when
     * the logger is enabled for the {@link Level#CONFIG config level}.
     * <p>
     * If the logger is enabled for the {@link Level#CONFIG config level} the message is sent to the
     * {@link Handler} registered in the current {@link Context}.
     *
     * @param messageSupplier the {@link Supplier} called to produce the message to log
     * @since 19.0
     */
    public void config(final Supplier<String> messageSupplier) {
        log(Level.CONFIG, messageSupplier);
    }

    /**
     * Logs entry into method.
     * <p>
     * This method can be used to log entry into a method. A {@link LogRecord} with message "ENTRY"
     * and the given {@code sourceMethod} and {@code sourceClass} is logged with {@link Level#FINER
     * finer level}.
     *
     * @param sourceClass the entered class
     * @param sourceMethod the entered method
     * @since 19.0
     */
    public void entering(final String sourceClass, final String sourceMethod) {
        logp(Level.FINER, sourceClass, sourceMethod, "ENTRY");
    }

    /**
     * Logs entry into method with single parameter.
     * <p>
     * This method can be used to log entry into a method. A {@link LogRecord} with message "ENTRY",
     * the given {@code sourceMethod} and {@code sourceClass} and given parameter is logged with
     * {@link Level#FINER finer level}.
     *
     * @param sourceClass the entered class
     * @param sourceMethod the entered method
     * @param parameter the method parameter
     * @since 19.0
     */
    public void entering(final String sourceClass, final String sourceMethod, final Object parameter) {
        logp(Level.FINER, sourceClass, sourceMethod, "ENTRY {0}", parameter);
    }

    /**
     * Logs entry into method with multiple parameters.
     * <p>
     * This method can be used to log entry into a method. A {@link LogRecord} with message "ENTRY",
     * the given {@code sourceMethod} and {@code sourceClass} and given parameters is logged with
     * {@link Level#FINER finer level}.
     *
     * @param sourceClass the entered class
     * @param sourceMethod the entered method
     * @param parameters the method parameters
     * @since 19.0
     */
    public void entering(final String sourceClass, final String sourceMethod, final Object[] parameters) {
        String msg = "ENTRY";
        if (parameters == null) {
            logp(Level.FINER, sourceClass, sourceMethod, msg);
            return;
        }
        if (!isLoggable(Level.FINER)) {
            return;
        }
        for (int i = 0; i < parameters.length; i++) {
            msg = msg + " {" + i + "}";
        }
        logp(Level.FINER, sourceClass, sourceMethod, msg, parameters);
    }

    /**
     * Logs a return from method.
     * <p>
     * This method can be used to log return from a method. A {@link LogRecord} with message
     * "RETURN" and the given {@code sourceMethod} and {@code sourceClass} is logged with
     * {@link Level#FINER finer level}.
     *
     * @param sourceClass the exiting class
     * @param sourceMethod the exiting method
     * @since 19.0
     */
    public void exiting(final String sourceClass, final String sourceMethod) {
        logp(Level.FINER, sourceClass, sourceMethod, "RETURN");
    }

    /**
     * Logs a return from method with result.
     * <p>
     * This method can be used to log return from a method. A {@link LogRecord} with message
     * "RETURN", the given {@code sourceMethod} and {@code sourceClass} and method result is logged
     * with {@link Level#FINER finer level}.
     *
     * @param sourceClass the exiting class
     * @param sourceMethod the exiting method
     * @param result the return value
     * @since 19.0
     */
    public void exiting(final String sourceClass, final String sourceMethod, final Object result) {
        logp(Level.FINER, sourceClass, sourceMethod, "RETURN {0}", result);
    }

    /**
     * Logs a message with {@link Level#FINE fine level}.
     * <p>
     * If the logger is enabled for the {@link Level#FINE fine level} the message is sent to the
     * {@link Handler} registered in the current {@link Context}.
     *
     * @param message the message to log
     * @since 19.0
     */
    public void fine(final String message) {
        log(Level.FINE, message);
    }

    /**
     * Logs a message with {@link Level#FINE fine level}. The message is constructed only when the
     * logger is enabled for the {@link Level#FINE fine level}.
     * <p>
     * If the logger is enabled for the {@link Level#FINE fine level} the message is sent to the
     * {@link Handler} registered in the current {@link Context}.
     *
     * @param messageSupplier the {@link Supplier} called to produce the message to log
     * @since 19.0
     */
    public void fine(final Supplier<String> messageSupplier) {
        log(Level.FINE, messageSupplier);
    }

    /**
     * Logs a message with {@link Level#FINER finer level}.
     * <p>
     * If the logger is enabled for the {@link Level#FINER finer level} the message is sent to the
     * {@link Handler} registered in the current {@link Context}.
     *
     * @param message the message to log
     * @since 19.0
     */
    public void finer(final String message) {
        log(Level.FINER, message);
    }

    /**
     * Logs a message with {@link Level#FINER finer level}. The message is constructed only when the
     * logger is enabled for the {@link Level#FINER finer level}.
     * <p>
     * If the logger is enabled for the {@link Level#FINER finer level} the message is sent to the
     * {@link Handler} registered in the current {@link Context}.
     *
     * @param messageSupplier the {@link Supplier} called to produce the message to log
     * @since 19.0
     */
    public void finer(final Supplier<String> messageSupplier) {
        log(Level.FINER, messageSupplier);
    }

    /**
     * Logs a message with {@link Level#FINEST finest level}.
     * <p>
     * If the logger is enabled for the {@link Level#FINEST finest level} the message is sent to the
     * {@link Handler} registered in the current {@link Context}.
     *
     * @param message the message to log
     * @since 19.0
     */
    public void finest(final String message) {
        log(Level.FINEST, message);
    }

    /**
     * Logs a message with {@link Level#FINEST finest level}. The message is constructed only when
     * the logger is enabled for the {@link Level#FINEST finest level}.
     * <p>
     * If the logger is enabled for the {@link Level#FINEST finest level} the message is sent to the
     * {@link Handler} registered in the current {@link Context}.
     *
     * @param messageSupplier the {@link Supplier} called to produce the message to log
     * @since 19.0
     */
    public void finest(final Supplier<String> messageSupplier) {
        log(Level.FINEST, messageSupplier);
    }

    /**
     * Logs a message with {@link Level#INFO info level}.
     * <p>
     * If the logger is enabled for the {@link Level#INFO info level} the message is sent to the
     * {@link Handler} registered in the current {@link Context}.
     *
     * @param message the message to log
     * @since 19.0
     */
    public void info(final String message) {
        log(Level.INFO, message);
    }

    /**
     * Logs a message with {@link Level#INFO info level}. The message is constructed only when the
     * logger is enabled for the {@link Level#INFO info level}.
     * <p>
     * If the logger is enabled for the {@link Level#INFO info level} the message is sent to the
     * {@link Handler} registered in the current {@link Context}.
     *
     * @param messageSupplier the {@link Supplier} called to produce the message to log
     * @since 19.0
     */
    public void info(final Supplier<String> messageSupplier) {
        log(Level.INFO, messageSupplier);
    }

    /**
     * Logs a message with {@link Level#SEVERE severe level}.
     * <p>
     * If the logger is enabled for the {@link Level#SEVERE severe level} the message is sent to the
     * {@link Handler} registered in the current {@link Context}.
     *
     * @param message the message to log
     * @since 19.0
     */
    public void severe(final String message) {
        log(Level.SEVERE, message);
    }

    /**
     * Logs a message with {@link Level#SEVERE severe level}. The message is constructed only when
     * the logger is enabled for the {@link Level#SEVERE severe level}.
     * <p>
     * If the logger is enabled for the {@link Level#SEVERE severe level} the message is sent to the
     * {@link Handler} registered in the current {@link Context}.
     *
     * @param messageSupplier the {@link Supplier} called to produce the message to log
     * @since 19.0
     */
    public void severe(final Supplier<String> messageSupplier) {
        log(Level.SEVERE, messageSupplier);
    }

    /**
     * Logs throwing an exception.
     * <p>
     * This method can be used to log exception thrown from a method. A {@link LogRecord} with
     * message "THROW",the given {@code sourceMethod} and {@code sourceClass} and {@code thrown} is
     * logged with {@link Level#FINER finer level}.
     *
     * @param sourceClass the class throwing an exception
     * @param sourceMethod the method throwing an exception
     * @param thrown the thrown exception
     * @since 19.0
     */
    public <T extends Throwable> T throwing(final String sourceClass, final String sourceMethod, final T thrown) {
        logp(Level.FINER, sourceClass, sourceMethod, "THROW", thrown);
        return thrown;
    }

    /**
     * Logs a message with {@link Level#WARNING warning level}.
     * <p>
     * If the logger is enabled for the {@link Level#WARNING warning level} the message is sent to
     * the {@link Handler} registered in the current {@link Context}.
     *
     * @param message the message to log
     * @since 19.0
     */
    public void warning(final String message) {
        log(Level.WARNING, message);
    }

    /**
     * Logs a message with {@link Level#WARNING warning level}. The message is constructed only when
     * the logger is enabled for the {@link Level#WARNING warning level}.
     * <p>
     * If the logger is enabled for the {@link Level#WARNING warning level} the message is sent to
     * the {@link Handler} registered in the current {@link Context}.
     *
     * @param messageSupplier the {@link Supplier} called to produce the message to log
     * @since 19.0
     */
    public void warning(final Supplier<String> messageSupplier) {
        log(Level.WARNING, messageSupplier);
    }

    /**
     * Logs a message.
     * <p>
     * If the logger is enabled for the given {@code level} the message is sent to the
     * {@link Handler} registered in the current {@link Context}.
     *
     * @param level the required {@link Level}
     * @param message the message to log
     * @since 19.0
     */
    public void log(final Level level, final String message) {
        if (!isLoggable(level)) {
            return;
        }
        doLog(level, message, null, null, (Object) null);
    }

    /**
     * Logs a message. The message is constructed only when the logger is enabled for the given
     * {@code level}.
     * <p>
     * If the logger is enabled for the given {@code level} the message is sent to the
     * {@link Handler} registered in the current {@link Context}.
     *
     * @param level the required {@link Level}
     * @param messageSupplier the {@link Supplier} called to produce the message to log
     * @since 19.0
     */
    public void log(final Level level, final Supplier<String> messageSupplier) {
        if (!isLoggable(level)) {
            return;
        }
        doLog(level, messageSupplier, null, null, (Object) null);
    }

    /**
     * Logs a message with single parameter.
     * <p>
     * If the logger is enabled for the given {@code level} the message is sent to the
     * {@link Handler} registered in the current {@link Context}.
     *
     * @param level the required {@link Level}
     * @param message the message to log
     * @param parameter the log message parameter
     * @since 19.0
     */
    public void log(final Level level, final String message, final Object parameter) {
        if (!isLoggable(level)) {
            return;
        }
        doLog(level, message, null, null, parameter);
    }

    /**
     * Logs a message with multiple parameters.
     * <p>
     * If the logger is enabled for the given {@code level} the message is sent to the
     * {@link Handler} registered in the current {@link Context}.
     *
     * @param level the required {@link Level}
     * @param message the message to log
     * @param parameters the log message parameters
     * @since 19.0
     */
    public void log(final Level level, final String message, final Object[] parameters) {
        if (!isLoggable(level)) {
            return;
        }
        doLog(level, message, null, null, parameters);
    }

    /**
     * Logs a message with an exception.
     * <p>
     * If the logger is enabled for the given {@code level} the message is sent to the
     * {@link Handler} registered in the current {@link Context}.
     *
     * @param level the required {@link Level}
     * @param message the message to log
     * @param thrown the exception to log
     * @since 19.0
     */
    public void log(final Level level, final String message, final Throwable thrown) {
        if (!isLoggable(level)) {
            return;
        }
        doLog(level, message, null, null, thrown);
    }

    /**
     * Logs a message with an exception. The message is constructed only when the logger is enabled
     * for the given {@code level}.
     * <p>
     * If the logger is enabled for the given {@code level} the message is sent to the
     * {@link Handler} registered in the current {@link Context}.
     *
     * @param level the required {@link Level}
     * @param thrown the exception to log
     * @param messageSupplier the {@link Supplier} called to produce the message to log
     * @since 19.0
     */
    public void log(final Level level, final Throwable thrown, final Supplier<String> messageSupplier) {
        if (!isLoggable(level)) {
            return;
        }
        doLog(level, messageSupplier, null, null, thrown);
    }

    /**
     * Logs a message, specifying source class and source method.
     * <p>
     * If the logger is enabled for the given {@code level} the message is sent to the
     * {@link Handler} registered in the current {@link Context}.
     *
     * @param level the required {@link Level}
     * @param sourceClass the class issued the logging request
     * @param sourceMethod the method issued the logging request
     * @param message the message to log
     * @since 19.0
     */
    public void logp(final Level level, final String sourceClass, final String sourceMethod, final String message) {
        if (!isLoggable(level)) {
            return;
        }
        doLog(level, message, sourceClass, sourceMethod, (Object) null);
    }

    /**
     * Logs a message, specifying source class and source method. The message is constructed only
     * when the logger is enabled for the given {@code level}.
     * <p>
     * If the logger is enabled for the given {@code level} the message is sent to the
     * {@link Handler} registered in the current {@link Context}.
     *
     * @param level the required {@link Level}
     * @param sourceClass the class issued the logging request
     * @param sourceMethod the method issued the logging request
     * @param messageSupplier the {@link Supplier} called to produce the message to log
     * @since 19.0
     */
    public void logp(final Level level, final String sourceClass, final String sourceMethod, final Supplier<String> messageSupplier) {
        if (!isLoggable(level)) {
            return;
        }
        doLog(level, messageSupplier, sourceClass, sourceMethod, (Object) null);
    }

    /**
     * Logs a message with single parameter, specifying source class and source method.
     * <p>
     * If the logger is enabled for the given {@code level} the message is sent to the
     * {@link Handler} registered in the current {@link Context}.
     *
     * @param level the required {@link Level}
     * @param sourceClass the class issued the logging request
     * @param sourceMethod the method issued the logging request
     * @param message the message to log
     * @param parameter the log message parameter
     * @since 19.0
     */
    public void logp(final Level level, final String sourceClass, final String sourceMethod, final String message, final Object parameter) {
        if (!isLoggable(level)) {
            return;
        }
        doLog(level, message, sourceClass, sourceMethod, parameter);
    }

    /**
     * Log a message with multiple parameters, specifying source class and source method.
     * <p>
     * If the logger is enabled for the given {@code level} the message is sent to the
     * {@link Handler} registered in the current {@link Context}.
     *
     * @param level the required {@link Level}
     * @param sourceClass the class issued the logging request
     * @param sourceMethod the method issued the logging request
     * @param message the message to log
     * @param parameters the log message parameters
     * @since 19.0
     */
    public void logp(final Level level, final String sourceClass, final String sourceMethod, final String message, Object[] parameters) {
        if (!isLoggable(level)) {
            return;
        }
        doLog(level, message, sourceClass, sourceMethod, parameters);
    }

    /**
     * Logs a message with an exception, specifying source class and source method.
     * <p>
     * If the logger is enabled for the given {@code level} the message is sent to the
     * {@link Handler} registered in the current {@link Context}.
     *
     * @param level the required {@link Level}
     * @param sourceClass the class issued the logging request
     * @param sourceMethod the method issued the logging request
     * @param message the message to log
     * @param thrown the exception to log
     * @since 19.0
     */
    public void logp(final Level level, final String sourceClass, final String sourceMethod, final String message, final Throwable thrown) {
        if (!isLoggable(level)) {
            return;
        }
        doLog(level, message, sourceClass, sourceMethod, thrown);
    }

    /**
     * Logs a message with an exception, specifying source class and source method. The message is
     * constructed only when the logger is enabled for the given {@code level}.
     * <p>
     * If the logger is enabled for the given {@code level} the message is sent to the
     * {@link Handler} registered in the current {@link Context}.
     *
     * @param level the required {@link Level}
     * @param sourceClass the class issued the logging request
     * @param sourceMethod the method issued the logging request
     * @param thrown the exception to log
     * @param messageSupplier the {@link Supplier} called to produce the message to log
     * @since 19.0
     */
    public void logp(final Level level, final String sourceClass, final String sourceMethod, final Throwable thrown, final Supplier<String> messageSupplier) {
        if (!isLoggable(level)) {
            return;
        }
        doLog(level, messageSupplier, sourceClass, sourceMethod, thrown);
    }

    /**
     * Returns the name of the logger.
     *
     * @return the logger name
     * @since 19.0
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the parent {@link TruffleLogger}.
     *
     * @return the parent {@link TruffleLogger} or null when the {@link TruffleLogger} has no
     *         parent.
     * @since 19.0
     */
    public TruffleLogger getParent() {
        return parent;
    }

    /**
     * Checks if a message of the given level would be logged by this logger.
     *
     * @param level the required logging level
     * @return true if message is loggable by this logger
     * @since 19.0
     */
    public boolean isLoggable(final Level level) {
        int value = getLevelNum();
        if (level.intValue() < value || value == OFF_VALUE) {
            return false;
        }
        return isLoggableSlowPath(level);
    }

    @CompilerDirectives.TruffleBoundary
    private boolean isLoggableSlowPath(final Level level) {
        return loggerCache.isLoggable(getName(), level);
    }

    @CompilerDirectives.TruffleBoundary
    private void doLog(
                    final Level level,
                    final String message,
                    final String className,
                    final String methodName,
                    final Object param) {
        doLog(level, message, className, methodName, new Object[]{param});
    }

    @CompilerDirectives.TruffleBoundary
    private void doLog(
                    final Level level,
                    final String message,
                    final String className,
                    final String methodName,
                    final Object[] params) {
        final LogRecord logRecord = LanguageAccessor.engineAccess().createLogRecord(
                        loggerCache.getSPI(),
                        level,
                        getName(),
                        message,
                        className,
                        methodName,
                        params,
                        null);
        callHandlers(logRecord);
    }

    @CompilerDirectives.TruffleBoundary
    private void doLog(
                    final Level level,
                    final String message,
                    final String className,
                    final String methodName,
                    final Throwable thrown) {
        final LogRecord logRecord = LanguageAccessor.engineAccess().createLogRecord(
                        loggerCache.getSPI(),
                        level,
                        getName(),
                        message,
                        className,
                        methodName,
                        null,
                        thrown);
        callHandlers(logRecord);
    }

    @CompilerDirectives.TruffleBoundary
    private void doLog(
                    final Level level,
                    final Supplier<String> messageSupplier,
                    final String className,
                    final String methodName,
                    final Object param) {
        doLog(level, messageSupplier.get(), className, methodName, new Object[]{param});
    }

    @CompilerDirectives.TruffleBoundary
    private void doLog(
                    final Level level,
                    final Supplier<String> messageSupplier,
                    final String className,
                    final String methodName,
                    final Throwable thrown) {
        doLog(level, messageSupplier.get(), className, methodName, thrown);
    }

    private void callHandlers(final LogRecord record) {
        CompilerAsserts.neverPartOfCompilation("Log handler should never be called from compiled code.");
        for (TruffleLogger current = this; current != null; current = current.getParent()) {
            if (current == loggerCache.polyglotRootLogger) {
                LanguageAccessor.engineAccess().getLogHandler(loggerCache.getSPI()).publish(record);
            }
        }
    }

    private void removeChild(final ChildLoggerRef child) {
        synchronized (childrenLock) {
            if (children != null) {
                for (Iterator<ChildLoggerRef> it = children.iterator(); it.hasNext();) {
                    if (it.next() == child) {
                        it.remove();
                        return;
                    }
                }
            }
        }
    }

    private void updateLevelNum(boolean singleContext) {
        int value;
        if (levelObj != null) {
            value = levelObj.intValue();
            if (parent != null && !singleContext) {
                value = Math.min(value, parent.getLevelNum());
            }
        } else if (parent != null) {
            value = parent.getLevelNum();
        } else {
            value = DEFAULT_VALUE;
        }
        setLevelNum(value);
        if (children != null) {
            for (ChildLoggerRef ref : children) {
                final TruffleLogger logger = ref.get();
                if (logger != null) {
                    logger.updateLevelNum(singleContext);
                }
            }
        }
    }

    private int getLevelNum() {
        if (!levelNumStable.isValid()) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
        }
        return levelNum;
    }

    private boolean setLevelNum(final int value) {
        if (this.levelNum != value) {
            this.levelNum = value;
            final Assumption currentAssumtion = levelNumStable;
            levelNumStable = Truffle.getRuntime().createAssumption("Log Level Value stable for: " + getName());
            currentAssumtion.invalidate();
            return true;
        }
        return false;
    }

    private void setLevel(final Level level, final boolean singleContext) {
        synchronized (childrenLock) {
            this.levelObj = level;
            updateLevelNum(singleContext);
        }
    }

    private void setParent(final TruffleLogger newParent, final boolean singleContext) {
        Objects.requireNonNull(newParent, "Parent must be non null.");
        synchronized (childrenLock) {
            ChildLoggerRef found = null;
            if (parent != null) {
                for (Iterator<ChildLoggerRef> it = parent.children.iterator(); it.hasNext();) {
                    final ChildLoggerRef childRef = it.next();
                    final TruffleLogger childLogger = childRef.get();
                    if (childLogger == this) {
                        found = childRef;
                        it.remove();
                        break;
                    }
                }
            }
            this.parent = newParent;
            if (found == null) {
                found = new ChildLoggerRef(this);
            }
            found.setParent(parent);
            if (parent.children == null) {
                parent.children = new ArrayList<>(2);
            }
            parent.children.add(found);
            updateLevelNum(singleContext);
        }
    }

    private static void cleanupFreedReferences() {
        for (int i = 0; i < MAX_CLEANED_REFS; i++) {
            final AbstractLoggerRef ref = (AbstractLoggerRef) loggersRefQueue.poll();
            if (ref == null) {
                break;
            }
            ref.close();
        }
    }

    private abstract static class AbstractLoggerRef extends WeakReference<TruffleLogger> implements Closeable {
        private final AtomicBoolean closed;

        AbstractLoggerRef(final TruffleLogger logger) {
            super(logger, loggersRefQueue);
            this.closed = new AtomicBoolean();
        }

        @Override
        public abstract void close();

        boolean shouldClose() {
            return !closed.getAndSet(true);
        }
    }

    private static final class ChildLoggerRef extends AbstractLoggerRef {

        private volatile Reference<TruffleLogger> parent;

        ChildLoggerRef(final TruffleLogger logger) {
            super(logger);
        }

        void setParent(TruffleLogger parent) {
            this.parent = new WeakReference<>(parent);
        }

        @Override
        public void close() {
            if (shouldClose()) {
                final Reference<TruffleLogger> p = parent;
                if (p != null) {
                    TruffleLogger parentLogger = p.get();
                    if (parentLogger != null) {
                        parentLogger.removeChild(this);
                    }
                    parent = null;
                }
            }
        }
    }

    static final class LoggerCache {
        private static final ReferenceQueue<Object> contextsRefQueue = new ReferenceQueue<>();
        private static final LoggerCache INSTANCE = new LoggerCache(LanguageAccessor.engineAccess().createDefaultLoggerCache());
        private final Object loggerCache;   // an instance of
                                            // com.oracle.truffle.polyglot.PolyglotLoggers.LoggerCache
        private final TruffleLogger polyglotRootLogger;
        private final Map<String, NamedLoggerRef> loggers;
        private final LoggerNode root;
        private final Set<ContextWeakReference> activeContexts;
        private Map<String, Level> effectiveLevels;
        private volatile Set<String> knownIds;

        private LoggerCache(Object loggerCacheSpi) {
            Objects.requireNonNull(loggerCacheSpi);
            this.loggerCache = loggerCacheSpi;
            this.polyglotRootLogger = new TruffleLogger(this);
            this.loggers = new HashMap<>();
            this.loggers.put(ROOT_NAME, new NamedLoggerRef(this.polyglotRootLogger, ROOT_NAME));
            this.root = new LoggerNode(null, new NamedLoggerRef(this.polyglotRootLogger, ROOT_NAME));
            this.activeContexts = new HashSet<>();
            this.effectiveLevels = Collections.emptyMap();
        }

        synchronized void addLogLevelsForContext(final Object spi, final Map<String, Level> addedLevels) {
            activeContexts.add(new ContextWeakReference(spi, contextsRefQueue, addedLevels));
            final Set<String> toRemove = collectRemovedLevels();
            reconfigure(addedLevels, toRemove);
        }

        synchronized void removeLogLevelsForContext(final Object context) {
            Set<String> toRemove = removeContext(context);
            reconfigure(Collections.emptyMap(), toRemove);
        }

        synchronized void close() {
            Object owner = LanguageAccessor.engineAccess().getLoggerOwner(loggerCache);
            if (owner == null) {
                return;
            }
            Set<String> toRemove = removeContext(owner);
            if (!toRemove.isEmpty()) {
                reconfigure(Collections.emptyMap(), toRemove);
            }
        }

        synchronized boolean isLoggable(final String loggerName, final Level level) {
            final Set<String> toRemove = collectRemovedLevels();
            if (!toRemove.isEmpty()) {
                reconfigure(Collections.emptyMap(), toRemove);
                // Logger's effective level may changed
                return getLogger(loggerName).isLoggable(level);
            }
            final Map<String, Level> current = LanguageAccessor.engineAccess().getLogLevels(getSPI());
            if (current == null) {
                return noContext();
            }
            if (current.isEmpty()) {
                final int currentLevel = DEFAULT_VALUE;
                return level.intValue() >= currentLevel && currentLevel != OFF_VALUE;
            }
            if (activeContexts.size() == 1) {
                return true;
            }
            final int currentLevel = computeLevel(loggerName, current);
            return level.intValue() >= currentLevel && currentLevel != OFF_VALUE;
        }

        @SuppressWarnings("all")
        private static boolean noContext() {
            boolean assertionsEnabled = false;
            assert assertionsEnabled = true;
            if (assertionsEnabled) {
                throw new IllegalStateException("Thread using TruffleLogger has to have a current context or the TruffleLogger has to be bound to an engine.");
            }
            return false;
        }

        private static int computeLevel(String loggeName, final Map<String, Level> levels) {
            for (String currentName = loggeName; currentName != null;) {
                final Level l = levels.get(currentName);
                if (l != null) {
                    return l.intValue();
                }
                if (currentName.isEmpty()) {
                    currentName = null;
                } else {
                    final int index = currentName.lastIndexOf('.');
                    currentName = index == -1 ? "" : currentName.substring(0, index);
                }
            }
            return DEFAULT_VALUE;
        }

        private TruffleLogger getOrCreateLogger(final String loggerName) {
            TruffleLogger found = getLogger(loggerName);
            if (found == null) {
                for (final TruffleLogger logger = new TruffleLogger(loggerName, this); found == null;) {
                    if (addLogger(logger)) {
                        found = logger;
                        break;
                    }
                    found = getLogger(loggerName);
                }
            }
            return found;
        }

        private TruffleLogger getOrCreateLogger(final String id, final String loggerName) {
            Set<String> ids = getKnownIds();
            if (!ids.contains(id)) {
                throw new IllegalArgumentException("Unknown language or instrument id " + id + ", known ids: " + String.join(", ", ids));
            }
            final String globalLoggerId = loggerName == null || loggerName.isEmpty() ? id : id + '.' + loggerName;
            return getOrCreateLogger(globalLoggerId);
        }

        private Set<String> getKnownIds() {
            Set<String> result = knownIds;
            if (result == null) {
                result = new HashSet<>();
                result.addAll(LanguageAccessor.engineAccess().getInternalIds());
                result.addAll(LanguageAccessor.engineAccess().getLanguageIds());
                result.addAll(LanguageAccessor.engineAccess().getInstrumentIds());
                knownIds = result;
            }
            return result;
        }

        private Object getSPI() {
            return this.loggerCache;
        }

        private synchronized TruffleLogger getLogger(final String loggerName) {
            TruffleLogger res = null;
            final NamedLoggerRef ref = loggers.get(loggerName);
            if (ref != null) {
                res = ref.get();
                if (res == null) {
                    ref.close();
                }
            }
            return res;
        }

        private boolean addLogger(final TruffleLogger logger) {
            final String loggerName = logger.getName();
            if (loggerName == null) {
                throw new NullPointerException("Logger must have non null name.");
            }
            cleanupFreedReferences();
            synchronized (this) {
                NamedLoggerRef ref = loggers.get(loggerName);
                if (ref != null) {
                    final TruffleLogger loggerInstance = ref.get();
                    if (loggerInstance != null) {
                        return false;
                    } else {
                        ref.close();
                    }
                }
                ref = new NamedLoggerRef(logger, loggerName);
                loggers.put(loggerName, ref);
                setLoggerLevel(logger, loggerName, activeContexts.size() <= 1);
                createParents(loggerName);
                final LoggerNode node = findLoggerNode(loggerName);
                node.setLoggerRef(ref);
                final TruffleLogger parentLogger = node.findParentLogger();
                if (parentLogger != null) {
                    logger.setParent(parentLogger, activeContexts.size() <= 1);
                }
                node.updateChildParents();
                ref.setNode(node);
                return true;
            }
        }

        private Level getEffectiveLevel(final String loggerName) {
            return effectiveLevels.get(loggerName);
        }

        private Set<String> removeContext(Object vmObject) {
            final Set<String> toRemove = collectRemovedLevels();
            for (Iterator<ContextWeakReference> it = activeContexts.iterator(); it.hasNext();) {
                final ContextWeakReference ref = it.next();
                final Object active = ref.get();
                if (vmObject.equals(active)) {
                    toRemove.addAll(ref.configuredLoggers.keySet());
                    it.remove();
                    break;
                }
            }
            return toRemove;
        }

        private Set<String> collectRemovedLevels() {
            assert Thread.holdsLock(this);
            final Set<String> toRemove = new HashSet<>();
            ContextWeakReference ref;
            while ((ref = (ContextWeakReference) contextsRefQueue.poll()) != null) {
                activeContexts.remove(ref);
                toRemove.addAll(ref.configuredLoggers.keySet());
            }
            return toRemove;
        }

        private void reconfigure(final Map<String, Level> addedLevels, final Set<String> toRemove) {
            assert Thread.holdsLock(this);
            assert !addedLevels.isEmpty() || !toRemove.isEmpty();
            final Collection<String> loggersWithRemovedLevels = new HashSet<>();
            final Collection<String> loggersWithChangedLevels = new HashSet<>();
            effectiveLevels = computeEffectiveLevels(
                            effectiveLevels,
                            toRemove,
                            addedLevels,
                            activeContexts,
                            loggersWithRemovedLevels,
                            loggersWithChangedLevels);
            boolean singleContext = activeContexts.size() <= 1;
            for (String loggerName : loggersWithRemovedLevels) {
                final TruffleLogger logger = getLogger(loggerName);
                if (logger != null) {
                    logger.setLevel(null, singleContext);
                }
            }
            for (String loggerName : loggersWithChangedLevels) {
                final TruffleLogger logger = getLogger(loggerName);
                if (logger != null) {
                    setLoggerLevel(logger, loggerName, singleContext);
                    createParents(loggerName);
                } else {
                    getOrCreateLogger(loggerName);
                }
            }
        }

        private void setLoggerLevel(final TruffleLogger logger, final String loggerName, final boolean singleContext) {
            final Level l = getEffectiveLevel(loggerName);
            if (l != null) {
                logger.setLevel(l, singleContext);
            }
        }

        private void createParents(final String loggerName) {
            int index = -1;
            for (int start = 1;; start = index + 1) {
                index = loggerName.indexOf('.', start);
                if (index < 0) {
                    break;
                }
                final String parentName = loggerName.substring(0, index);
                if (getEffectiveLevel(parentName) != null) {
                    getOrCreateLogger(parentName);
                }
            }
        }

        private LoggerNode findLoggerNode(final String loggerName) {
            LoggerNode node = root;
            String currentName = loggerName;
            while (!currentName.isEmpty()) {
                int index = currentName.indexOf('.');
                String currentNameCompoment;
                if (index > 0) {
                    currentNameCompoment = currentName.substring(0, index);
                    currentName = currentName.substring(index + 1);
                } else {
                    currentNameCompoment = currentName;
                    currentName = "";
                }
                if (node.children == null) {
                    node.children = new HashMap<>();
                }
                LoggerNode child = node.children.get(currentNameCompoment);
                if (child == null) {
                    child = new LoggerNode(node, null);
                    node.children.put(currentNameCompoment, child);
                }
                node = child;
            }
            return node;
        }

        static LoggerCache getInstance() {
            return INSTANCE;
        }

        private static Map<String, Level> computeEffectiveLevels(
                        final Map<String, Level> currentEffectiveLevels,
                        final Set<String> removed,
                        final Map<String, Level> added,
                        final Collection<? extends ContextWeakReference> contexts,
                        final Collection<? super String> removedLevels,
                        final Collection<? super String> changedLevels) {
            final Map<String, Level> newEffectiveLevels = new HashMap<>(currentEffectiveLevels);
            for (String loggerName : removed) {
                final Level level = findMinLevel(loggerName, contexts);
                if (level == null) {
                    newEffectiveLevels.remove(loggerName);
                    removedLevels.add(loggerName);
                } else {
                    final Level currentLevel = newEffectiveLevels.get(loggerName);
                    if (currentLevel != level) {
                        newEffectiveLevels.put(loggerName, level);
                        changedLevels.add(loggerName);
                    }
                }
            }
            for (Map.Entry<String, Level> addedLevel : added.entrySet()) {
                final String loggerName = addedLevel.getKey();
                final Level loggerLevel = addedLevel.getValue();
                final Level currentLevel = newEffectiveLevels.get(loggerName);
                if (currentLevel == null || min(loggerLevel, currentLevel) != currentLevel) {
                    newEffectiveLevels.put(loggerName, loggerLevel);
                    changedLevels.add(loggerName);
                }
            }
            return newEffectiveLevels;
        }

        private static Level findMinLevel(final String loggerName, final Collection<? extends ContextWeakReference> contexts) {
            Level min = null;
            for (ContextWeakReference contextRef : contexts) {
                final Object context = contextRef.get();
                final Level level = context == null ? null : contextRef.configuredLoggers.get(loggerName);
                if (level == null) {
                    continue;
                }
                if (min == null) {
                    min = level;
                } else {
                    min = min(min, level);
                }
            }
            return min;
        }

        private static Level min(final Level l1, final Level l2) {
            return l1.intValue() < l2.intValue() ? l1 : l2;
        }

        private final class NamedLoggerRef extends AbstractLoggerRef {
            private final String loggerName;
            private LoggerNode node;

            NamedLoggerRef(final TruffleLogger logger, final String loggerName) {
                super(logger);
                this.loggerName = loggerName;
            }

            void setNode(final LoggerNode node) {
                assert Thread.holdsLock(LoggerCache.this);
                this.node = node;
            }

            @Override
            public void close() {
                if (shouldClose()) {
                    synchronized (LoggerCache.this) {
                        if (node != null) {
                            if (node.loggerRef == this) {
                                LoggerCache.this.loggers.remove(loggerName);
                                node.loggerRef = null;
                            }
                            node = null;
                        }
                    }
                }
            }
        }

        private final class LoggerNode {
            final LoggerNode parent;
            Map<String, LoggerNode> children;
            private NamedLoggerRef loggerRef;

            LoggerNode(final LoggerNode parent, final NamedLoggerRef loggerRef) {
                this.parent = parent;
                this.loggerRef = loggerRef;
            }

            void setLoggerRef(final NamedLoggerRef loggerRef) {
                this.loggerRef = loggerRef;
            }

            void updateChildParents() {
                final TruffleLogger logger = loggerRef.get();
                updateChildParentsImpl(logger);
            }

            TruffleLogger findParentLogger() {
                if (parent == null) {
                    return null;
                }
                TruffleLogger logger;
                if (parent.loggerRef != null && (logger = parent.loggerRef.get()) != null) {
                    return logger;
                }
                return parent.findParentLogger();
            }

            private void updateChildParentsImpl(final TruffleLogger parentLogger) {
                if (children == null || children.isEmpty()) {
                    return;
                }
                for (LoggerNode child : children.values()) {
                    TruffleLogger childLogger = child.loggerRef != null ? child.loggerRef.get() : null;
                    if (childLogger != null) {
                        childLogger.setParent(parentLogger, activeContexts.size() <= 1);
                    } else {
                        child.updateChildParentsImpl(parentLogger);
                    }
                }
            }
        }

        private static final class ContextWeakReference extends WeakReference<Object> {
            private final Map<String, Level> configuredLoggers;

            ContextWeakReference(final Object context, final ReferenceQueue<Object> referenceQueue, final Map<String, Level> logLevels) {
                super(context, referenceQueue);
                configuredLoggers = logLevels;
            }
        }
    }
}
