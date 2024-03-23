/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.impl.ConfigurationCondition;

public abstract class ConditionalConfigurationRegistry {
    private final Map<Class<?>, Collection<Runnable>> pendingReachabilityHandlers = new ConcurrentHashMap<>();

    protected void registerConditionalConfiguration(ConfigurationCondition condition, Consumer<ConfigurationCondition> consumer) {
        Objects.requireNonNull(condition, "Cannot use null value as condition for conditional configuration. Please ensure that you register a non-null condition.");
        Objects.requireNonNull(consumer, "Cannot use null value as runnable for conditional configuration. Please ensure that you register a non-null runnable.");
        if (ConfigurationCondition.alwaysTrue().equals(condition)) {
            /* analysis optimization to include new types as early as possible */
            consumer.accept(condition);
        } else {
            Collection<Runnable> handlers = pendingReachabilityHandlers.computeIfAbsent(condition.getType(), key -> new ConcurrentLinkedQueue<>());
            ConfigurationCondition runtimeCondition = condition.isRuntimeChecked() ? condition : ConfigurationCondition.alwaysTrue();
            handlers.add(() -> consumer.accept(runtimeCondition));
        }

    }

    public void flushConditionalConfiguration(Feature.BeforeAnalysisAccess b) {
        for (Map.Entry<Class<?>, Collection<Runnable>> reachabilityEntry : pendingReachabilityHandlers.entrySet()) {
            b.registerReachabilityHandler(access -> reachabilityEntry.getValue().forEach(Runnable::run), reachabilityEntry.getKey());
        }
        pendingReachabilityHandlers.clear();
    }

    public void flushConditionalConfiguration(Feature.DuringAnalysisAccess b) {
        if (!pendingReachabilityHandlers.isEmpty()) {
            b.requireAnalysisIteration();
        }
        flushConditionalConfiguration((Feature.BeforeAnalysisAccess) b);
    }

}
