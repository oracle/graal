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

import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.classinitialization.ClassInitializationSupport;

public abstract class ConditionalConfigurationRegistry {
    private Feature.BeforeAnalysisAccess beforeAnalysisAccess;
    private final Map<Class<?>, Collection<Runnable>> pendingReachabilityHandlers = new ConcurrentHashMap<>();

    protected void registerConditionalConfiguration(ConfigurationCondition condition, Consumer<ConfigurationCondition> consumer) {
        Objects.requireNonNull(condition, "Cannot use null value as condition for conditional configuration. Please ensure that you register a non-null condition.");
        Objects.requireNonNull(consumer, "Cannot use null value as runnable for conditional configuration. Please ensure that you register a non-null runnable.");
        if (condition.isRuntimeChecked() && !condition.isAlwaysTrue()) {
            /*
             * We do this before the type is reached as the handler runs during analysis when it is
             * too late to register types for reached tracking. If the type is never reached, there
             * is no damage as subtypes will also never be reached.
             */
            ClassInitializationSupport.singleton().addForTypeReachedTracking(condition.getType());
        }
        if (ConfigurationCondition.alwaysTrue().equals(condition)) {
            /* analysis optimization to include new types as early as possible */
            consumer.accept(ConfigurationCondition.alwaysTrue());
        } else {
            ConfigurationCondition runtimeCondition;
            if (condition.isRuntimeChecked()) {
                runtimeCondition = condition;
            } else {
                runtimeCondition = ConfigurationCondition.alwaysTrue();
            }
            if (beforeAnalysisAccess == null) {
                Collection<Runnable> handlers = pendingReachabilityHandlers.computeIfAbsent(condition.getType(), key -> new ConcurrentLinkedQueue<>());
                handlers.add(() -> consumer.accept(runtimeCondition));
            } else {
                beforeAnalysisAccess.registerReachabilityHandler(access -> consumer.accept(runtimeCondition), condition.getType());
            }

        }

    }

    public void setAnalysisAccess(Feature.BeforeAnalysisAccess beforeAnalysisAccess) {
        VMError.guarantee(this.beforeAnalysisAccess == null, "Analysis access can be set only once.");
        this.beforeAnalysisAccess = Objects.requireNonNull(beforeAnalysisAccess);
        for (Map.Entry<Class<?>, Collection<Runnable>> reachabilityEntry : pendingReachabilityHandlers.entrySet()) {
            this.beforeAnalysisAccess.registerReachabilityHandler(access -> reachabilityEntry.getValue().forEach(Runnable::run), reachabilityEntry.getKey());
        }
        pendingReachabilityHandlers.clear();
    }

}
