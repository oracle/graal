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

import java.util.Map;
import java.util.function.BiConsumer;

import org.graalvm.collections.UnmodifiableEconomicMap;

import com.oracle.svm.hosted.meta.HostedMethod;

import jdk.graal.compiler.debug.MetricKey;

/**
 * Interface that represents a scope that's used to track Web Image statistics. It allows opening
 * and entering sub-scopes and allocating counters for stats tracking. It is preferred to use this
 * with try block. Additionally, if needed, a scope can invoke an {@link OnCloseHandler} upon
 * closing. This can be useful when there's some repeatable sub-scope(eg. per-method scope) that
 * tracks some statistics that need to be later accumulated in parent scope. <br>
 *
 * Example of usage:
 *
 * <pre>
 *     try(LoggerScope scope = parentScope.scope("CodeGen")){
 *         ...
 *     }
 * </pre>
 *
 * Example with OnCloseHandler:
 *
 * <pre>
 * public static final MetricKey METHOD_SIZE = DebugContext.counter("method-size");
 * public static final MetricKey IMAGE_SIZE = DebugContext.counter("image-size");
 * ...
 * void handler(LoggerScope parentScope, UnmodifiableEconomicMap&lt;MetricKey, LoggableMetrics&gt; metrics) {
 *  ...
 *  parentScope.counter(IMAGE_SIZE).add(metrics.get(METHOD_SIZE));
 *  ...
 * }
 * ...
 * try(LoggerScope scope = currentScope.scope(methodName, handler)) {
 *     ...
 *     scope.counter(METHOD_SIZE).add(someValue);
 *     ...
 * }
 * </pre>
 */
public interface LoggerScope extends AutoCloseable {
    /**
     * @see LoggerContext#scope(String)
     */
    LoggerScope scope(String name);

    /**
     * @see LoggerContext#scope(String, OnCloseHandler)
     */
    LoggerScope scope(String name, OnCloseHandler onCloseHandler);

    /**
     * @see LoggerContext#scope(HostedMethod)
     */
    LoggerScope scope(HostedMethod method);

    /**
     * @see LoggerContext#scope(HostedMethod, OnCloseHandler)
     */
    LoggerScope scope(HostedMethod method, OnCloseHandler onCloseHandler);

    /**
     * Returns a {@link LoggableCounter} associated with the given {@link MetricKey}. If there's no
     * {@link LoggableCounter} associated with the given key, a new counter is allocated and
     * associated with the given key.
     */
    LoggableCounter counter(MetricKey name);

    /**
     * Returns a map of values of the specified counters.
     */
    Map<String, Number> countersMap(MetricKey... keys);

    /**
     * Gets all current values of the metrics in this scope.
     */
    UnmodifiableEconomicMap<MetricKey, Number> getExtractedMetrics();

    String getName();

    String getQualifiedName();

    @Override
    void close();

    /**
     * Returns the parent of the current scope, or <code>null</code> if there is no parent.
     */
    LoggerScope parent();

    interface OnCloseHandler extends BiConsumer<LoggerScope, UnmodifiableEconomicMap<MetricKey, LoggableMetric>> {
    }
}
