/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.polyglot;

import static com.oracle.truffle.api.CompilerDirectives.shouldNotReachHere;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.function.Supplier;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.StreamHandler;

import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import java.io.IOException;

final class PolyglotLoggers {

    private PolyglotLoggers() {
    }

    static Set<String> getInternalIds() {
        return Collections.singleton(PolyglotEngineImpl.OPTION_GROUP_ENGINE);
    }

    static LoggerCache defaultSPI() {
        return LoggerCacheImpl.DEFAULT;
    }

    static LoggerCache createEngineSPI(PolyglotEngineImpl engine) {
        return LoggerCacheImpl.newEngineLoggerCache(new PolyglotLogHandler(engine), engine, true);
    }

    static PolyglotContextImpl getCurrentOuterContext() {
        PolyglotContextImpl currentContext = PolyglotContextImpl.currentNotEntered();
        if (currentContext != null) {
            while (currentContext.parent != null) {
                currentContext = currentContext.parent;
            }
        }
        return currentContext;
    }

    static boolean isSameLogSink(Handler h1, Handler h2) {
        if (h1 == h2) {
            return true;
        }
        if (h1 instanceof PolyglotStreamHandler && h2 instanceof PolyglotStreamHandler) {
            return ((PolyglotStreamHandler) h1).sink == ((PolyglotStreamHandler) h2).sink;
        }
        return false;
    }

    static Supplier<TruffleLogger> createCompilerLoggerProvider(PolyglotEngineImpl engine) {
        return new CompilerLoggerProvider(engine);
    }

    /**
     * Returns a {@link Handler} for given {@link Handler} or {@link OutputStream}. If the
     * {@code logHandlerOrStream} is instance of {@link Handler} the {@code logHandlerOrStream} is
     * returned. If the {@code logHandlerOrStream} is instance of {@link OutputStream} a new
     * {@link StreamHandler} is created for given stream. If the {@code logHandlerOrStream} is
     * {@code null} the {@code null} is returned. Otherwise a {@link IllegalArgumentException} is
     * thrown.
     *
     * @param logHandlerOrStream the {@link Handler} or {@link OutputStream}
     * @return {@link Handler} or {@code null}
     * @throws IllegalArgumentException if {@code logHandlerOrStream} is not {@code null} nor
     *             {@link Handler} nor {@link OutputStream}
     */
    static Handler asHandler(Object logHandlerOrStream) {
        if (logHandlerOrStream == null) {
            return null;
        }
        if (logHandlerOrStream instanceof Handler) {
            return (Handler) logHandlerOrStream;
        }
        if (logHandlerOrStream instanceof OutputStream) {
            return createStreamHandler((OutputStream) logHandlerOrStream, true, true);
        }
        throw new IllegalArgumentException("Unexpected logHandlerOrStream parameter: " + logHandlerOrStream);
    }

    /**
     * Creates a default {@link Handler} for an engine when a {@link Handler} was not specified.
     *
     * @param out the {@link OutputStream} to print log messages into
     */
    static Handler createDefaultHandler(final OutputStream out) {
        return new PolyglotStreamHandler(out, false, true, true);
    }

    /**
     * Creates a {@link Handler} printing log messages into given {@link OutputStream}.
     *
     * @param out the {@link OutputStream} to print log messages into
     * @param closeStream if true the {@link Handler#close() handler's close} method closes given
     *            stream
     * @param flushOnPublish if true the {@link Handler#flush() flush} method is called after
     *            {@link Handler#publish(java.util.logging.LogRecord) publish}
     * @return the {@link Handler}
     */
    static Handler createStreamHandler(final OutputStream out, final boolean closeStream, final boolean flushOnPublish) {
        return new PolyglotStreamHandler(out, closeStream, flushOnPublish, false);
    }

    static boolean isDefaultHandler(Handler handler) {
        if (!(handler instanceof PolyglotStreamHandler)) {
            return false;
        }
        PolyglotStreamHandler phandler = ((PolyglotStreamHandler) handler);
        return phandler.isDefault;
    }

    interface LoggerCache {

        Handler getLogHandler();

        Map<String, Level> getLogLevels();

        PolyglotEngineImpl getEngine();

        LogRecord createLogRecord(Level level, String loggerName, String message, String className, String methodName, Object[] parameters, Throwable thrown);
    }

    private static final class LoggerCacheImpl implements LoggerCache {

        static final LoggerCache DEFAULT = new LoggerCacheImpl(PolyglotLogHandler.INSTANCE, null, true, null);

        private final Handler handler;
        private final boolean useCurrentContext;
        private final Reference<PolyglotEngineImpl> engineRef;
        private final Map<String, Level> defaultValue;
        private final Set<Level> implicitLevels;

        private LoggerCacheImpl(Handler handler, PolyglotEngineImpl engine, boolean useCurrentContext, Map<String, Level> defaultValue, Level... implicitLevels) {
            Objects.requireNonNull(handler);
            this.handler = handler;
            this.useCurrentContext = useCurrentContext;
            this.engineRef = engine == null ? null : new WeakReference<>(engine);
            this.defaultValue = defaultValue;
            if (implicitLevels.length == 0) {
                this.implicitLevels = Collections.emptySet();
            } else {
                this.implicitLevels = new HashSet<>();
                Collections.addAll(this.implicitLevels, implicitLevels);
            }
        }

        static LoggerCacheImpl newEngineLoggerCache(Handler handler, PolyglotEngineImpl engine, boolean useCurrentContext, Level... implicitLevels) {
            return new LoggerCacheImpl(handler, Objects.requireNonNull(engine), useCurrentContext, null, implicitLevels);
        }

        static LoggerCacheImpl newFallBackLoggerCache(Handler handler) {
            return new LoggerCacheImpl(handler, null, false, Collections.emptyMap(), Level.INFO);
        }

        @Override
        public PolyglotEngineImpl getEngine() {
            return engineRef == null ? null : engineRef.get();
        }

        @Override
        public Handler getLogHandler() {
            return handler;
        }

        @Override
        public Map<String, Level> getLogLevels() {
            if (useCurrentContext) {
                PolyglotContextImpl context = getCurrentOuterContext();
                if (context != null) {
                    return context.config.logLevels;
                }
            }
            PolyglotEngineImpl engine = getEngine();
            if (engine != null) {
                return engine.logLevels;
            }
            return defaultValue;
        }

        @Override
        public LogRecord createLogRecord(Level level, String loggerName, String message, String className, String methodName, Object[] parameters, Throwable thrown) {
            return new ImmutableLogRecord(level, loggerName, message, className, methodName, parameters, thrown, implicitLevels.contains(level));
        }
    }

    private static final class PolyglotLogHandler extends Handler {

        private static final Handler INSTANCE = new PolyglotLogHandler();

        private final Handler fallBackHandler;

        PolyglotLogHandler() {
            this.fallBackHandler = null;
        }

        PolyglotLogHandler(PolyglotEngineImpl engine) {
            fallBackHandler = engine.logHandler;
        }

        @Override
        public void publish(final LogRecord record) {
            Handler handler = findDelegate();
            if (handler == null) {
                handler = fallBackHandler;
            }
            if (handler != null) {
                handler.publish(record);
            }
        }

        @Override
        public void flush() {
            final Handler handler = findDelegate();
            if (handler != null) {
                handler.flush();
            }
        }

        @Override
        public void close() throws SecurityException {
            final Handler handler = findDelegate();
            if (handler != null) {
                handler.close();
            }
        }

        private static Handler findDelegate() {
            final PolyglotContextImpl currentContext = getCurrentOuterContext();
            return currentContext != null ? currentContext.config.logHandler : null;
        }
    }

    private static final class ImmutableLogRecord extends LogRecord {

        private static final long serialVersionUID = 1L;
        private final boolean implicit;

        ImmutableLogRecord(final Level level, final String loggerName, final String message, final String className, final String methodName, final Object[] parameters,
                        final Throwable thrown, boolean implicit) {
            super(level, message);
            super.setLoggerName(loggerName);
            if (className != null) {
                super.setSourceClassName(className);
            }
            if (methodName != null) {
                super.setSourceMethodName(methodName);
            }
            Object[] copy = parameters;
            if (parameters != null && parameters.length > 0) {
                copy = new Object[parameters.length];
                for (int i = 0; i < parameters.length; i++) {
                    copy[i] = safeValue(parameters[i]);
                }
            }
            super.setParameters(copy);
            super.setThrown(thrown);
            this.implicit = implicit;
        }

        @Override
        public void setLevel(Level level) {
            throw new UnsupportedOperationException("Setting Level is not supported.");
        }

        @Override
        public void setLoggerName(String name) {
            throw new UnsupportedOperationException("Setting Logger Name is not supported.");
        }

        @Override
        public void setMessage(String message) {
            throw new UnsupportedOperationException("Setting Messag is not supported.");
        }

        @SuppressWarnings("deprecation")
        @Override
        public void setMillis(long millis) {
            throw new UnsupportedOperationException("Setting Millis is not supported.");
        }

        @Override
        public void setParameters(Object[] parameters) {
            throw new UnsupportedOperationException("Setting Parameters is not supported.");
        }

        @Override
        public void setResourceBundle(ResourceBundle bundle) {
            throw new UnsupportedOperationException("Setting Resource Bundle is not supported.");
        }

        @Override
        public void setResourceBundleName(String name) {
            throw new UnsupportedOperationException("Setting Resource Bundle Name is not supported.");
        }

        @Override
        public void setSequenceNumber(long seq) {
            throw new UnsupportedOperationException("Setting Sequence Number is not supported.");
        }

        @Override
        public void setSourceClassName(String sourceClassName) {
            throw new UnsupportedOperationException("Setting Parameters is not supported.");
        }

        @Override
        public void setSourceMethodName(String sourceMethodName) {
            throw new UnsupportedOperationException("Setting Source Method Name is not supported.");
        }

        @Override
        public void setThreadID(int threadID) {
            throw new UnsupportedOperationException("Setting Thread ID is not supported.");
        }

        @Override
        public void setThrown(Throwable thrown) {
            throw new UnsupportedOperationException("Setting Throwable is not supported.");
        }

        boolean isImplicit() {
            return implicit;
        }

        private static Object safeValue(final Object param) {
            if (param == null || EngineAccessor.EngineImpl.isPrimitive(param)) {
                return param;
            }
            try {
                return InteropLibrary.getFactory().getUncached().asString(InteropLibrary.getFactory().getUncached().toDisplayString(param));
            } catch (UnsupportedMessageException e) {
                throw shouldNotReachHere(e);
            }
        }
    }

    private static final class PolyglotStreamHandler extends StreamHandler {

        private final OutputStream sink;
        private final boolean closeStream;
        private final boolean flushOnPublish;
        private final boolean isDefault;

        PolyglotStreamHandler(final OutputStream out, final boolean closeStream, final boolean flushOnPublish, final boolean defaultHandler) {
            super(out, FormatterImpl.INSTANCE);
            setLevel(Level.ALL);
            this.sink = out;
            this.closeStream = closeStream;
            this.flushOnPublish = flushOnPublish;
            this.isDefault = defaultHandler;
        }

        @Override
        public synchronized void publish(LogRecord record) {
            super.publish(record);
            if (flushOnPublish) {
                flush();
            }
        }

        @SuppressWarnings("sync-override")
        @Override
        public void close() {
            if (closeStream) {
                super.close();
            } else {
                flush();
            }
        }

        private static final class FormatterImpl extends Formatter {
            private static final String FORMAT_FULL = "[%1$s] %2$s: %3$s%4$s%n";
            private static final String FORMAT_NO_LEVEL = "[%1$s] %2$s%3$s%n";
            static final Formatter INSTANCE = new FormatterImpl();

            private FormatterImpl() {
            }

            @Override
            public String format(LogRecord record) {
                String loggerName = formatLoggerName(record.getLoggerName());
                final String message = formatMessage(record);
                String stackTrace = "";
                final Throwable exception = record.getThrown();
                if (exception != null) {
                    final StringWriter str = new StringWriter();
                    try (PrintWriter out = new PrintWriter(str)) {
                        out.println();
                        exception.printStackTrace(out);
                    }
                    stackTrace = str.toString();
                }
                boolean implicit = record.getClass() == ImmutableLogRecord.class && ((ImmutableLogRecord) record).isImplicit();
                return implicit ? String.format(FORMAT_NO_LEVEL, loggerName, message, stackTrace) : String.format(FORMAT_FULL, loggerName, record.getLevel().getName(), message, stackTrace);
            }

            private static String formatLoggerName(final String loggerName) {
                final String id;
                String name;
                int index = loggerName.indexOf('.');
                if (index < 0) {
                    id = loggerName;
                    name = "";
                } else {
                    id = loggerName.substring(0, index);
                    name = loggerName.substring(index + 1);
                }
                if (name.isEmpty()) {
                    return id;
                }
                final StringBuilder sb = new StringBuilder(id);
                sb.append("::");
                sb.append(possibleSimpleName(name));
                return sb.toString();
            }

            private static String possibleSimpleName(final String loggerName) {
                int index = -1;
                for (int i = 0; i >= 0; i = loggerName.indexOf('.', i + 1)) {
                    if (i + 1 < loggerName.length() && Character.isUpperCase(loggerName.charAt(i + 1))) {
                        index = i + 1;
                        break;
                    }
                }
                return index < 0 ? loggerName : loggerName.substring(index);
            }
        }
    }

    private static final class CompilerLoggerProvider implements Supplier<TruffleLogger> {

        private final PolyglotEngineImpl engine;
        private volatile Object loggers;

        CompilerLoggerProvider(PolyglotEngineImpl engine) {
            this.engine = engine;
        }

        @Override
        public TruffleLogger get() {
            Object loggersCache = loggers;
            if (loggersCache == null) {
                synchronized (this) {
                    loggersCache = loggers;
                    if (loggersCache == null) {
                        LoggerCache spi;
                        Map<String, Level> levels;
                        if (engine != null) {
                            Handler useHandler = resolveHandler(engine.logHandler);
                            spi = LoggerCacheImpl.newEngineLoggerCache(useHandler, engine, false, Level.INFO);
                            levels = engine.logLevels;
                        } else {
                            OutputStream logOut = EngineAccessor.RUNTIME.getConfiguredLogStream();
                            Handler useHandler = logOut != null ? createStreamHandler(logOut, false, true) : createDefaultHandler(PolyglotEngineImpl.ALLOW_IO ? System.err : new NullOutputStream());
                            spi = LoggerCacheImpl.newFallBackLoggerCache(useHandler);
                            levels = Collections.emptyMap();
                        }
                        loggersCache = EngineAccessor.LANGUAGE.createEngineLoggers(spi, levels);
                        loggers = loggersCache;
                    }
                }
            }
            return EngineAccessor.LANGUAGE.getLogger(PolyglotEngineImpl.OPTION_GROUP_ENGINE, null, loggersCache);
        }

        private static Handler resolveHandler(Handler handler) {
            if (isDefaultHandler(handler)) {
                OutputStream logOut = EngineAccessor.RUNTIME.getConfiguredLogStream();
                if (logOut != null) {
                    return createStreamHandler(logOut, false, true);
                } else {
                    return handler;
                }
            } else {
                return new SafeHandler(handler);
            }
        }
    }

    private static final class SafeHandler extends Handler {

        private final Handler delegate;

        SafeHandler(Handler delegate) {
            Objects.requireNonNull(delegate);
            this.delegate = delegate;
        }

        @Override
        public void publish(LogRecord lr) {
            try {
                delegate.publish(lr);
            } catch (Throwable t) {
                // Called by a compiler thread, never propagate exceptions to the compiler.
            }
        }

        @Override
        public void flush() {
            delegate.flush();
        }

        @Override
        public void close() throws SecurityException {
            delegate.close();
        }
    }

    private static final class NullOutputStream extends OutputStream {

        @Override
        public void write(int b) throws IOException {
        }

        @Override
        public void write(byte[] array, int off, int len) throws IOException {
        }
    }
}
