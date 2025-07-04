/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.ref.WeakReference;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.logging.ErrorManager;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import org.graalvm.polyglot.SandboxPolicy;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.LogHandler;

import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.polyglot.PolyglotImpl.VMObject;

final class PolyglotLoggers {

    private static final Map<Path, SharedFileHandler> fileHandlers = new HashMap<>();

    private static final String GRAAL_COMPILER_LOG_ID = "graal";
    private static final Set<String> INTERNAL_IDS;
    static {
        Set<String> s = new HashSet<>();
        Collections.addAll(s, PolyglotEngineImpl.OPTION_GROUP_ENGINE, GRAAL_COMPILER_LOG_ID);
        INTERNAL_IDS = Collections.unmodifiableSet(s);
    }

    private PolyglotLoggers() {
    }

    static Set<String> getInternalIds() {
        return INTERNAL_IDS;
    }

    static boolean haveSameTarget(LogHandler h1, LogHandler h2) {
        if (h1 == h2) {
            return true;
        }
        if (h1 instanceof StreamLogHandler && h2 instanceof StreamLogHandler) {
            return ((StreamLogHandler) h1).stream == ((StreamLogHandler) h2).stream;
        }
        if (h1 instanceof JavaLogHandler && h2 instanceof JavaLogHandler) {
            return ((JavaLogHandler) h1).handler == ((JavaLogHandler) h2).handler;
        }
        return false;
    }

    /**
     * Returns a {@link LogHandler} for given {@link Handler} or {@link OutputStream}. If the
     * {@code logHandlerOrStream} is instance of {@link Handler} the {@link JavaLogHandler} is
     * returned. If the {@code logHandlerOrStream} is instance of {@link OutputStream} a new
     * {@link StreamLogHandler} is created for given stream. Otherwise, a
     * {@link IllegalArgumentException} is thrown.
     *
     * @param logHandlerOrStream the {@link Handler} or {@link OutputStream}
     * @return {@link LogHandler}
     * @throws IllegalArgumentException if {@code logHandlerOrStream} is not {@link Handler} nor
     *             {@link OutputStream}
     */
    static LogHandler asLogHandler(Object logHandlerOrStream) {
        if (logHandlerOrStream instanceof Handler) {
            return new JavaLogHandler((Handler) logHandlerOrStream);
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
     * @param sandboxPolicy the engine's sandbox policy
     */
    static LogHandler createDefaultHandler(OutputStream out, SandboxPolicy sandboxPolicy) {
        return new StreamLogHandler(out, false, true, true,
                        sandboxPolicy.isStricterOrEqual(SandboxPolicy.UNTRUSTED) ? sandboxPolicy : null);
    }

    static boolean isDefault(LogHandler handler) {
        return handler instanceof StreamLogHandler streamLogHandler && streamLogHandler.isDefault;
    }

    static LogHandler getFileHandler(String path) {
        Path absolutePath = Paths.get(path).toAbsolutePath().normalize();
        synchronized (fileHandlers) {
            SharedFileHandler handler = fileHandlers.get(absolutePath);
            if (handler == null) {
                try {
                    handler = new SharedFileHandler(absolutePath);
                    fileHandlers.put(absolutePath, handler);
                } catch (IOException ioe) {
                    throw PolyglotEngineException.illegalArgument("Cannot open log file " + path + " for writing, IO error: " + (ioe.getMessage() != null ? ioe.getMessage() : null));
                }
            }
            return handler.retain();
        }
    }

    /**
     * Used reflectively by {@code ContextPreInitializationTest}.
     */
    static Set<Path> getActiveFileHandlers() {
        synchronized (fileHandlers) {
            return new HashSet<>(fileHandlers.keySet());
        }
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
    static LogHandler createStreamHandler(OutputStream out, boolean closeStream, boolean flushOnPublish) {
        return new StreamLogHandler(out, closeStream, flushOnPublish, false, null);
    }

    static LogRecord createLogRecord(Level level, String loggerName, String message, String className, String methodName, Object[] parameters, Throwable thrown, String formatKind) {
        return new ImmutableLogRecord(level, loggerName, message, className, methodName, parameters, thrown, ImmutableLogRecord.FormatKind.valueOf(formatKind));
    }

    static String getFormatKind(LogRecord logRecord) {
        return (logRecord instanceof ImmutableLogRecord ? ((ImmutableLogRecord) logRecord).getFormatKind() : ImmutableLogRecord.FormatKind.DEFAULT).name();
    }

    static boolean isCallerClassSet(LogRecord logRecord) {
        return logRecord instanceof ImmutableLogRecord && ((ImmutableLogRecord) logRecord).isCallerClassSet();
    }

    static boolean isCallerMethodSet(LogRecord logRecord) {
        return logRecord instanceof ImmutableLogRecord && ((ImmutableLogRecord) logRecord).isCallerMethodSet();
    }

    static final class LoggerCache {

        static final LoggerCache DEFAULT = new LoggerCache(PolyglotLogHandler.INSTANCE, true, null, Collections.emptySet());

        private final LogHandler handler;
        private final boolean useCurrentContext;
        private final Function<VMObject, Map<String, Level>> ownerLogLevelsProvider;
        private final Set<String> rawLoggerIds;
        private final Set<Level> implicitLevels;
        private volatile WeakReference<VMObject> ownerRef;

        private LoggerCache(LogHandler handler, boolean useCurrentContext, Function<VMObject, Map<String, Level>> ownerLogLevelsProvider,
                        Set<String> rawLoggerIds, Level... implicitLevels) {
            Objects.requireNonNull(handler);
            this.handler = handler;
            this.useCurrentContext = useCurrentContext;
            this.ownerLogLevelsProvider = ownerLogLevelsProvider;
            this.rawLoggerIds = rawLoggerIds;
            if (implicitLevels.length == 0) {
                this.implicitLevels = Collections.emptySet();
            } else {
                this.implicitLevels = new HashSet<>();
                Collections.addAll(this.implicitLevels, implicitLevels);
            }
        }

        boolean isContextBoundLogger() {
            return !useCurrentContext;
        }

        void setOwner(VMObject owner) {
            if (ownerRef != null) {
                throw new IllegalStateException("owner can only be set once");
            }
            ownerRef = new WeakReference<>(owner);
        }

        static LoggerCache newEngineLoggerCache(PolyglotEngineImpl engine) {
            Objects.requireNonNull(engine);
            LoggerCache cache = new LoggerCache(new PolyglotLogHandler(engine), true, (owner) -> ((PolyglotEngineImpl) owner).logLevels, Collections.emptySet());
            cache.setOwner(engine);
            return cache;
        }

        static LoggerCache newEngineLoggerCache(LogHandler handler, Map<String, Level> logLevels, Set<String> rawLoggerIds, Level... implicitLevels) {
            return new LoggerCache(handler, false, (owner) -> logLevels, rawLoggerIds, implicitLevels);
        }

        static LoggerCache newContextLoggerCache(PolyglotContextImpl context) {
            Objects.requireNonNull(context);
            LoggerCache cache = new LoggerCache(new ContextLogHandler(context), false, (owner) -> ((PolyglotContextImpl) owner).config.logLevels, Collections.emptySet());
            cache.setOwner(context);
            return cache;
        }

        public VMObject getOwner() {
            return ownerRef == null ? null : ownerRef.get();
        }

        public LogHandler getLogHandler() {
            return handler;
        }

        public Map<String, Level> getLogLevels() {
            if (useCurrentContext) {
                PolyglotContextImpl context = PolyglotFastThreadLocals.getContext(null);
                if (context != null) {
                    return context.config.logLevels;
                }
            }
            if (ownerLogLevelsProvider != null) {
                VMObject owner;
                if (ownerRef != null) {
                    owner = ownerRef.get();
                    if (owner == null) {
                        // if the owner was initialized and owner was collected we shared the
                        // truffle
                        // logger too far.
                        throw ContextLogHandler.invalidSharing();
                    }
                } else {
                    owner = null;
                }
                return ownerLogLevelsProvider.apply(owner);
            }
            return null;
        }

        public LogRecord createLogRecord(Level level, String loggerName, String message, String className, String methodName, Object[] parameters, Throwable thrown) {
            ImmutableLogRecord.FormatKind formaterKind;
            if (rawLoggerIds.contains(loggerName)) {
                formaterKind = ImmutableLogRecord.FormatKind.RAW;
            } else if (implicitLevels.contains(level)) {
                formaterKind = ImmutableLogRecord.FormatKind.NO_LEVEL;
            } else {
                formaterKind = ImmutableLogRecord.FormatKind.DEFAULT;
            }
            return new ImmutableLogRecord(level, loggerName, message, className, methodName, parameters, thrown, formaterKind);
        }
    }

    private abstract static class AbstractLogHandler extends LogHandler {

        volatile boolean closed;
        private ErrorManager errorManager;

        final void checkClosed() {
            if (closed) {
                throw new AssertionError("The log handler is closed.");
            }
        }

        final synchronized void reportHandlerError(int errorKind, Throwable t) {
            if (errorManager == null) {
                errorManager = new PolyglotErrorManager();
            }
            Exception exception;
            if (t instanceof Exception) {
                exception = (Exception) t;
            } else {
                exception = new RuntimeException(String.format("%s: %s", t.getClass().getName(), t.getMessage()));
                exception.setStackTrace(t.getStackTrace());
            }
            errorManager.error("", exception, errorKind);
        }

    }

    private static final class JavaLogHandler extends AbstractLogHandler {

        private final Handler handler;

        JavaLogHandler(Handler handler) {
            this.handler = Objects.requireNonNull(handler, "Handler must be non null");
        }

        @Override
        public void publish(LogRecord logRecord) {
            try {
                checkClosed();
                handler.publish(logRecord);
            } catch (Throwable t) {
                // Called by a compiler thread, never propagate exceptions to the compiler.
                reportHandlerError(ErrorManager.GENERIC_FAILURE, t);
            }
        }

        @Override
        public void flush() {
            try {
                checkClosed();
                handler.flush();
            } catch (Throwable t) {
                // Called by a compiler thread, never propagate exceptions to the compiler.
                reportHandlerError(ErrorManager.FLUSH_FAILURE, t);
            }
        }

        @Override
        public void close() {
            this.closed = true;
            handler.close();
        }
    }

    private static class StreamLogHandler extends AbstractLogHandler {

        private static final String REDIRECT_FORMAT = "[To redirect Truffle log output to a file use one of the following options:%n" +
                        "* '--log.file=<path>' if the option is passed using a guest language launcher.%n" +
                        "* '-Dpolyglot.log.file=<path>' if the option is passed using the host Java launcher.%n" +
                        "* Configure logging using the polyglot embedding API.]%n";

        private static final String DISABLED_FORMAT = "[engine] Logging to context error output stream is not enabled for the sandbox policy %s. " +
                        "To resolve this issue, install a custom logging handler using Builder.logHandler(Handler) " +
                        "or switch to a less strict sandbox policy using Builder.sandbox(SandboxPolicy).%n";

        private final OutputStream stream;
        private final OutputStreamWriter writer;
        private final Formatter formatter;
        private final boolean closeStream;
        private final boolean flushOnPublish;
        private final boolean isDefault;
        private final SandboxPolicy disabledForActiveSandboxPolicy;
        private boolean notificationPrinted;

        StreamLogHandler(OutputStream stream, boolean closeStream, boolean flushOnPublish,
                        boolean isDefault, SandboxPolicy disabledForActiveSandboxPolicy) {
            Objects.requireNonNull(stream, "Stream must be non null");
            this.stream = stream;
            this.writer = new OutputStreamWriter(stream);
            this.formatter = FormatterImpl.INSTANCE;
            this.closeStream = closeStream;
            this.flushOnPublish = flushOnPublish;
            this.isDefault = isDefault;
            this.disabledForActiveSandboxPolicy = disabledForActiveSandboxPolicy;
        }

        @Override
        public synchronized void publish(LogRecord logRecord) {
            try {
                checkClosed();
                if (disabledForActiveSandboxPolicy != null) {
                    assert isDefault : "Only default handler can be disabled";
                    if (!notificationPrinted) {
                        writer.write(String.format(DISABLED_FORMAT, disabledForActiveSandboxPolicy));
                        writer.flush();
                        notificationPrinted = true;
                    }
                    return;
                }
                String msg;
                try {
                    msg = formatter.format(logRecord);
                } catch (Exception ex) {
                    reportHandlerError(ErrorManager.FORMAT_FAILURE, ex);
                    return;
                }
                try {
                    if (isDefault && !notificationPrinted) {
                        writer.write(String.format(REDIRECT_FORMAT));
                        notificationPrinted = true;
                    }
                    writer.write(msg);
                    if (flushOnPublish) {
                        writer.flush();
                    }
                } catch (Exception ex) {
                    reportHandlerError(ErrorManager.WRITE_FAILURE, ex);
                }
            } catch (Throwable t) {
                // Called by a compiler thread, never propagate exceptions to the compiler.
                reportHandlerError(ErrorManager.GENERIC_FAILURE, t);
            }
        }

        @Override
        public synchronized void flush() {
            try {
                checkClosed();
                writer.flush();
            } catch (Throwable t) {
                // Called by a compiler thread, never propagate exceptions to the compiler.
                reportHandlerError(ErrorManager.FLUSH_FAILURE, t);
            }
        }

        @Override
        public synchronized void close() {
            closed = true;
            try {
                writer.flush();
                if (closeStream) {
                    writer.close();
                }
            } catch (Exception ex) {
                reportHandlerError(ErrorManager.CLOSE_FAILURE, ex);
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
                String logEntry;
                ImmutableLogRecord.FormatKind formatKind = ((ImmutableLogRecord) record).getFormatKind();
                switch (formatKind) {
                    case DEFAULT:
                        logEntry = String.format(FORMAT_FULL, loggerName, record.getLevel().getName(), message, stackTrace);
                        break;
                    case NO_LEVEL:
                        logEntry = String.format(FORMAT_NO_LEVEL, loggerName, message, stackTrace);
                        break;
                    case RAW:
                        logEntry = message;
                        break;
                    default:
                        throw new IllegalArgumentException("Unsupported FormatKind " + formatKind);
                }
                return logEntry;
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
                for (int i = loggerName.indexOf('.'); i >= 0; i = loggerName.indexOf('.', i + 1)) {
                    if (i + 1 < loggerName.length() && Character.isUpperCase(loggerName.charAt(i + 1))) {
                        index = i + 1;
                        break;
                    }
                }
                return index < 0 ? loggerName : loggerName.substring(index);
            }
        }
    }

    /**
     * A {@link LogHandler} used for engine created with the `log.file` option. There can be
     * multiple engines with the same `log.file` value in a single process. In order to avoid
     * overwriting each other's log files, these engines must share the same log handler. The is
     * closed only when the reference count drops to zero.
     */
    private static final class SharedFileHandler extends StreamLogHandler {

        private final Path path;
        private int refCount;

        SharedFileHandler(Path path) throws IOException {
            super(new FileOutputStream(path.toFile(), true), true, true, false, null);
            this.path = path;
        }

        SharedFileHandler retain() {
            assert Thread.holdsLock(fileHandlers);
            refCount++;
            return this;
        }

        @Override
        @SuppressWarnings("sync-override")
        public void close() {
            synchronized (fileHandlers) {
                refCount--;
                if (refCount == 0) {
                    fileHandlers.remove(path);
                    super.close();
                }
            }
        }
    }

    private static final class PolyglotLogHandler extends LogHandler {

        private static final LogHandler INSTANCE = new PolyglotLogHandler();

        private final WeakReference<PolyglotEngineImpl> engineRef;

        PolyglotLogHandler() {
            this.engineRef = null;
        }

        PolyglotLogHandler(PolyglotEngineImpl engine) {
            this.engineRef = new WeakReference<>(engine);
        }

        @Override
        public void publish(final LogRecord record) {
            LogHandler handler = findDelegate();
            if (handler == null) {
                PolyglotEngineImpl engine = engineRef != null ? engineRef.get() : null;
                handler = engine != null ? engine.logHandler : null;
            }
            if (handler != null) {
                handler.publish(record);
            }
        }

        @Override
        public void flush() {
            final LogHandler handler = findDelegate();
            if (handler != null) {
                handler.flush();
            }
        }

        @Override
        public void close() throws SecurityException {
            final LogHandler handler = findDelegate();
            if (handler != null) {
                handler.close();
            }
        }

        private static LogHandler findDelegate() {
            final PolyglotContextImpl currentContext = PolyglotFastThreadLocals.getContext(null);
            return currentContext != null ? currentContext.config.logHandler : null;
        }
    }

    /**
     * Delegates to the Context's logging Handler. The Context's logging Handler may be different in
     * the context pre-inialization and the context execution time.
     */
    private static final class ContextLogHandler extends LogHandler {

        private final WeakReference<PolyglotContextImpl> contextRef;

        ContextLogHandler(PolyglotContextImpl context) {
            this.contextRef = new WeakReference<>(context);
        }

        @Override
        public void publish(final LogRecord record) {
            findDelegate().publish(record);
        }

        @Override
        public void flush() {
            findDelegate().flush();
        }

        @Override
        public void close() throws SecurityException {
            findDelegate().close();
        }

        private LogHandler findDelegate() {
            final PolyglotContextImpl context = contextRef.get();
            if (context == null) {
                throw invalidSharing();
            }
            return context.config.logHandler;
        }

        static AssertionError invalidSharing() {
            throw new AssertionError("Invalid sharing of bound TruffleLogger in AST nodes detected.");
        }
    }

    private static final class ImmutableLogRecord extends LogRecord {

        private static final long serialVersionUID = 1L;
        private final FormatKind formatKind;
        private final boolean isCallerClassSet;
        private final boolean isCallerMethodSet;

        enum FormatKind {
            RAW,
            NO_LEVEL,
            DEFAULT
        }

        ImmutableLogRecord(final Level level, final String loggerName, final String message, final String className, final String methodName, final Object[] parameters,
                        final Throwable thrown, FormatKind formatKind) {
            super(level, message);
            super.setLoggerName(loggerName);
            if (className != null) {
                super.setSourceClassName(className);
                this.isCallerClassSet = true;
            } else {
                this.isCallerClassSet = false;
            }
            if (methodName != null) {
                super.setSourceMethodName(methodName);
                this.isCallerMethodSet = true;
            } else {
                this.isCallerMethodSet = false;
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
            this.formatKind = formatKind;
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

        @SuppressWarnings("deprecation")
        @Override
        public void setThreadID(int threadID) {
            throw new UnsupportedOperationException("Setting Thread ID is not supported.");
        }

        @Override
        public void setThrown(Throwable thrown) {
            throw new UnsupportedOperationException("Setting Throwable is not supported.");
        }

        FormatKind getFormatKind() {
            return formatKind;
        }

        boolean isCallerClassSet() {
            return isCallerClassSet;
        }

        public boolean isCallerMethodSet() {
            return isCallerMethodSet;
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

        @Override
        public String toString() {
            return "ImmutableLogRecord [loggerName=" + getLoggerName() + ", level=" + getLevel() + ", sequence=" + getSequenceNumber() +
                            ", message()=" + getMessage() + ", parameters=" + Arrays.toString(getParameters()) + ", instant=" + getInstant() + "]";
        }

    }

    static final class EngineLoggerProvider implements Function<String, TruffleLogger> {

        private volatile Object loggers;
        private final LogHandler logHandler;
        private final Map<String, Level> logLevels;

        EngineLoggerProvider(LogHandler logHandler, Map<String, Level> logLevels) {
            this.logHandler = logHandler;
            this.logLevels = logLevels;
        }

        @Override
        public TruffleLogger apply(String loggerId) {
            Object loggersCache = loggers;
            if (loggersCache == null) {
                synchronized (this) {
                    loggersCache = loggers;
                    if (loggersCache == null) {
                        LoggerCache spi = LoggerCache.newEngineLoggerCache(logHandler, logLevels, Collections.singleton(GRAAL_COMPILER_LOG_ID), Level.INFO);
                        loggers = loggersCache = EngineAccessor.LANGUAGE.createEngineLoggers(spi);
                    }
                }
            }
            return EngineAccessor.LANGUAGE.getLogger(loggerId, null, loggersCache);
        }
    }

    private static final class PolyglotErrorManager extends ErrorManager {

        private final AtomicBoolean reported = new AtomicBoolean();

        PolyglotErrorManager() {
        }

        @Override
        public void error(String msg, Exception ex, int code) {
            if (reported.getAndSet(true)) {
                return;
            }
            StringWriter content = new StringWriter();
            try (PrintWriter out = new PrintWriter(content)) {
                String text = "java.util.logging.ErrorManager: " + code;
                if (msg != null) {
                    text = text + ": " + msg;
                }
                out.println(text);
                if (ex != null) {
                    ex.printStackTrace(out);
                }
            }
            PolyglotEngineImpl.logFallback(content.toString());
        }
    }
}
