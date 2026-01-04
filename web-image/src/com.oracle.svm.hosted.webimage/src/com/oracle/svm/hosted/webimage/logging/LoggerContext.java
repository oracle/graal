/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package com.oracle.svm.hosted.webimage.logging;

import java.io.File;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.UnmodifiableEconomicMap;
import org.graalvm.collections.UnmodifiableMapCursor;

import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.webimage.options.WebImageOptions;
import com.oracle.svm.hosted.webimage.options.WebImageOptions.LoggerOptions;

import jdk.graal.compiler.debug.DebugOptions;
import jdk.graal.compiler.debug.MetricKey;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionValues;

/**
 * Context object for logging Web Image statistics.
 * <p>
 * LoggerContext is used as an entry point to the Logging API, holding configuration and opening
 * initial {@link LoggerScope}-s. It should be used within try/catch block and <b>should not</b> be
 * shared between threads, but rather each thread should have its own LoggerContext object. Also, to
 * remove the need to pass it around as parameter, method {@link #currentContext()} is used to
 * access last created and active <b>LoggerContext</b> object inside of <i>current</i> thread.
 *
 * Usage example:
 *
 * <pre>
 *     try(LoggerContext context = new LoggerContext.Builder(options).build()){
 *         ...
 *         try(LoggerScope parentScope = context.scope("Analysis")){
 *             ...
 *             try(LoggerScope subScope = parentScope.scope("SubScope")){
 *                 ...
 *                 subScope.counter(METRIC_KEY).add(value);
 *             }
 *         }
 *     }
 * </pre>
 */
public final class LoggerContext implements AutoCloseable {
    public static class Builder {
        private final OptionValues options;
        private PrintStream logStream;
        private LoggerScope.OnCloseHandler defaultOnCloseHandler;
        private boolean deleteMetricFile;

        public Builder(OptionValues options) {
            this.options = options;
            this.logStream = getDefaultStream();
            this.defaultOnCloseHandler = EMPTY_ON_CLOSE_HANDLER;
            this.deleteMetricFile = true;
        }

        public Builder stream(PrintStream stream) {
            logStream = stream;
            return this;
        }

        public Builder onCloseHandler(LoggerScope.OnCloseHandler onCloseHandler) {
            this.defaultOnCloseHandler = onCloseHandler;
            return this;
        }

        public Builder deleteMetricFile(boolean flag) {
            deleteMetricFile = flag;
            return this;
        }

        public LoggerContext build() {
            return new LoggerContext(options, logStream, defaultOnCloseHandler, deleteMetricFile);
        }
    }

    public static PrintStream getDefaultStream() {
        return System.out;
    }

    public static final String QUALIFIED_NAME_SEPARATOR = ".";
    public static final LoggerScope.OnCloseHandler EMPTY_ON_CLOSE_HANDLER = (parentScope, metrics) -> {
    };
    private static final ThreadLocal<LoggerContext> currentLoggerContext = new ThreadLocal<>();

    private final Map<String, Map<String, Number>> savedCounters;
    private final OptionValues debugContextOptions;
    private final PrintStream stream;
    private final LoggerPrinter loggerPrinter;
    private final LoggerScope.OnCloseHandler defaultOnCloseHandler;
    private LoggerScopeImpl currentScope;
    private LoggerContext parent;

    private LoggerContext(OptionValues options, PrintStream stream, LoggerScope.OnCloseHandler onCloseHandler, boolean deleteMetricFile) {
        this.savedCounters = new HashMap<>();
        this.debugContextOptions = convertOptionValues(options);
        this.stream = stream;
        this.defaultOnCloseHandler = onCloseHandler;
        this.loggerPrinter = getLoggerPrinter(options);
        this.parent = currentLoggerContext.get();
        currentLoggerContext.set(this);
        if (deleteMetricFile) {
            deleteMetricFile();
        }
    }

    /**
     * {@link LoggerScopeImpl} uses {@link jdk.graal.compiler.debug.DebugContext} as a storage for
     * tracked metrics and printing of metrics to both output stream and a file. Because of this,
     * configuration of DebugContext for debugging (configured through compiler options) and
     * configuration of DebugContext for Web Image logging should not clash/interfere with each
     * other. That's why there exist {@link LoggerOptions}, to configure Logging API, that are then
     * converted to appropriate {@link DebugOptions} to configure DebugContext for Web Image
     * Logging.
     *
     * @return Options to be used by {@link LoggerScopeImpl} to configure its
     *         {@link jdk.graal.compiler.debug.DebugContext} object.
     */
    private static OptionValues convertOptionValues(OptionValues options) {
        EconomicMap<OptionKey<?>, Object> optionMap = OptionValues.newOptionMap();

        /*
         * DebugOptions.MetricsFile is used to configure DebugContext for storing metrics to a file,
         * while DebugOptions.Log option is used to configure DebugContext for output filtering. We
         * convert options from LoggerOptions to their respective options found in DebugOptions.
         */
        optionMap.put(DebugOptions.MetricsFile, LoggerOptions.LoggingFile.getValue(options));
        optionMap.put(DebugOptions.Log, LoggerOptions.LogFilter.getValue(options));
        /*
         * We need to set this option to an empty string to enable usage of counters.
         */
        optionMap.put(DebugOptions.Counters, "");
        return new OptionValues(optionMap);
    }

    private static LoggerPrinter getLoggerPrinter(OptionValues options) {
        switch (LoggerOptions.LoggingStyle.getValue(options)) {
            case ReadableText:
                return new ReadableTextLoggerPrinter();
            case BenchmarkText:
                return new BenchmarkLoggerPrinter(WebImageOptions.BenchmarkName.getValue(options));
        }
        assert false : "Should not reach here";
        return null;
    }

    private void deleteMetricFile() {
        String metricFileName = getMetricFileName();
        if (metricFileName != null) {
            File file = new File(metricFileName);
            if (file.exists()) {
                file.delete();
            }
        }
    }

    public void saveCounters(LoggerScope scope, MetricKey... keys) {
        savedCounters.put(scope.getQualifiedName(), scope.countersMap(keys));
    }

    public Map<String, Number> getSavedCounters(String qualifiedName, MetricKey... keys) {
        Map<String, Number> saved = savedCounters.get(qualifiedName);
        Map<String, Number> subset = new LinkedHashMap<>();
        for (MetricKey key : keys) {
            subset.put(key.getName(), saved.get(key.getName()));
        }
        return subset;
    }

    /**
     * Creates and enters a new scope which will be child of current scope. The opening and closing
     * of scopes should be used with try block.
     * <p>
     * Example:
     *
     * <pre>
     *     try(LoggerScope scope = loggerContext.scope("analysis", SOME_HANDLER){
     *         ...
     *     }
     * </pre>
     *
     * @param name name of the new scope
     * @param onCloseHandler handler that should be called when scope is closed.
     * @return newly created scope
     */
    public LoggerScope scope(String name, LoggerScope.OnCloseHandler onCloseHandler) {
        return enterScope(name, onCloseHandler);
    }

    /**
     * Similar to {@link #scope(String, LoggerScope.OnCloseHandler)} but empty
     * {@link LoggerScope.OnCloseHandler} is used.
     *
     * @see #scope(String, LoggerScope.OnCloseHandler)
     */
    public LoggerScope scope(String name) {
        return enterScope(name, defaultOnCloseHandler);
    }

    /**
     * Same as {@link #scope(String, LoggerScope.OnCloseHandler)} but makes it easier use-case where
     * name of the scope should be equal to method name (per-method scopes).
     *
     * @see #scope(String, LoggerScope.OnCloseHandler)
     */
    public LoggerScope scope(HostedMethod method, LoggerScope.OnCloseHandler onCloseHandler) {
        return enterScope(method.getQualifiedName(), onCloseHandler);
    }

    /**
     * Similar to {@link #scope(HostedMethod, LoggerScope.OnCloseHandler)} but empty
     * {@link LoggerScope.OnCloseHandler} is used by default.
     *
     * @see #scope(HostedMethod, LoggerScope.OnCloseHandler)
     */
    public LoggerScope scope(HostedMethod method) {
        return enterScope(method.getQualifiedName(), defaultOnCloseHandler);
    }

    LoggerScopeImpl enterScope(String name, LoggerScope.OnCloseHandler onCloseHandler) {
        LoggerScopeImpl scope = new LoggerScopeImpl(name, this, onCloseHandler, currentScope);
        setCurrentScope(scope);
        return scope;
    }

    public LoggerScope currentScope() {
        return currentScope;
    }

    void setCurrentScope(LoggerScopeImpl scope) {
        currentScope = scope;
    }

    @Override
    public void close() {
        assert currentLoggerContext.get() == this : currentLoggerContext.get();
        currentScope = null;
        currentLoggerContext.set(parent);
        parent = null;
    }

    OptionValues getOptions() {
        return debugContextOptions;
    }

    String getMetricFileName() {
        return DebugOptions.MetricsFile.getValue(debugContextOptions);
    }

    PrintStream getStream() {
        return stream;
    }

    LoggerPrinter getLoggerPrinter() {
        return loggerPrinter;
    }

    public static LoggerContext currentContext() {
        return currentLoggerContext.get();
    }

    /**
     * A convenience method that calls {@link LoggerScope#counter(MetricKey)} from the current scope
     * of the current logger context. <br>
     * Removes the need for a rather long chain of method invocation:
     *
     * <pre>
     *     LoggerContext.currentContext().currentScope().counter(KEY).add(..);
     * </pre>
     *
     * with:
     *
     * <pre>
     *     LoggerContext.counter(KEY).add(...);
     * </pre>
     */
    public static LoggableCounter counter(MetricKey key) {
        return currentLoggerContext.get().currentScope().counter(key);
    }

    /**
     * Merge the given metrics into the currently open scope of the receiver. This method is useful
     * to add metrics that were logged in a different {@link LoggerContext} of another thread to the
     * receiver.
     *
     * @param metrics metrics to be added to the currently open scope of the receiver
     */
    public void mergeSavedCounters(UnmodifiableEconomicMap<MetricKey, Number> metrics) {
        UnmodifiableMapCursor<MetricKey, Number> cursor = metrics.getEntries();
        while (cursor.advance()) {
            currentScope.counter(cursor.getKey()).add(cursor.getValue().longValue());
        }
    }

    /**
     * Gets the qualified scope name for a series of scope names. The first scope name has to be the
     * root and the last scope name the leaf.
     */
    public static String getQualifiedScopeName(String... scopes) {
        assert scopes.length >= 1 : Arrays.toString(scopes);
        StringBuilder sb = new StringBuilder();
        sb.append(scopes[0]);
        for (int i = 1; i < scopes.length; i++) {
            sb.append(QUALIFIED_NAME_SEPARATOR);
            sb.append(scopes[i]);
        }
        return sb.toString();
    }
}
