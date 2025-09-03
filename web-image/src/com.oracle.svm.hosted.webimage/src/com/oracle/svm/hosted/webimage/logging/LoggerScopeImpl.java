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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.UnmodifiableEconomicMap;

import com.oracle.svm.hosted.meta.HostedMethod;

import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.MetricKey;

class LoggerScopeImpl implements LoggerScope {
    private final String name;
    private final String qualifiedName;
    private final LoggerContext context;
    private final LoggerScopeImpl parent;
    private final DebugContext debugContext;
    private final OnCloseHandler onCloseHandler;
    private final DebugContext.Scope scope;
    private boolean closed;
    private final EconomicMap<MetricKey, LoggableMetric> metrics;

    LoggerScopeImpl(String name, LoggerContext context, OnCloseHandler onCloseHandler, LoggerScopeImpl parent) {
        this.name = name;
        this.qualifiedName = parent == null ? name : parent.getQualifiedName() + LoggerContext.QUALIFIED_NAME_SEPARATOR + name;
        this.parent = parent;
        this.onCloseHandler = onCloseHandler;
        this.debugContext = new DebugContext.Builder(context.getOptions()).logStream(context.getStream()).description(null).build();
        this.context = context;
        this.metrics = EconomicMap.create();
        this.scope = debugContext.scope(qualifiedName);
        this.closed = false;
    }

    @Override
    public LoggerScope scope(String scopeName) {
        return context.scope(scopeName);
    }

    @Override
    public LoggerScope scope(String scopeName, OnCloseHandler handler) {
        return context.scope(scopeName, handler);
    }

    @Override
    public LoggerScope scope(HostedMethod method) {
        return context.scope(method);
    }

    @Override
    public LoggerScope scope(HostedMethod method, OnCloseHandler handler) {
        return context.scope(method, handler);
    }

    @Override
    public LoggableCounter counter(MetricKey key) {
        return lazyPutIfAbsent(key, () -> new LoggableCounter(this, DebugContext.counter(key.getName())));
    }

    @SuppressWarnings("unchecked")
    private <T extends LoggableMetric> T lazyPutIfAbsent(MetricKey key, Supplier<T> provider) {
        assert provider != null;

        T metric = (T) metrics.get(key);
        if (metric == null) {
            metric = provider.get();
            metrics.put(key, metric);
        }

        return metric;
    }

    @Override
    public void close() {
        /*
         * Order is important because we want to make sure current scope is reachable inside of
         * onClose handler and we should close debugContext at the end because once debugContext is
         * closed, metrics are lost.
         */
        printMetrics();
        if (scope != null) {
            scope.close();
        }
        onCloseHandler.accept(this, metrics);
        context.setCurrentScope(parent);
        debugContext.close();
        closed = true;
    }

    @Override
    public Map<String, Number> countersMap(MetricKey... keys) {
        Map<String, Number> map = new LinkedHashMap<>();
        for (MetricKey key : keys) {
            map.put(key.getName(), counter(key).get());
        }
        return map;
    }

    @Override
    public UnmodifiableEconomicMap<MetricKey, Number> getExtractedMetrics() {
        EconomicMap<MetricKey, Number> map = EconomicMap.create(metrics.size());
        for (MetricKey key : metrics.getKeys()) {
            map.put(key, counter(key).get());
        }
        return map;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getQualifiedName() {
        return qualifiedName;
    }

    @Override
    public LoggerScope parent() {
        return parent;
    }

    DebugContext getDebugContext() {
        return debugContext;
    }

    boolean isClosed() {
        return closed;
    }

    UnmodifiableEconomicMap<MetricKey, LoggableMetric> getMetrics() {
        return metrics;
    }

    private void printMetrics() {
        debugContext.printMetrics(new DebugContext.Description(qualifiedName, ""));
        if (debugContext.isLogEnabled()) {
            context.getLoggerPrinter().print(this);
        }
    }
}
