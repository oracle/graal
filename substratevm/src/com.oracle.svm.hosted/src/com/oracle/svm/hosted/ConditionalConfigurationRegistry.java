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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.impl.ConfigurationCondition;

import com.oracle.svm.core.TypeResult;

public abstract class ConditionalConfigurationRegistry {
    private final Map<String, List<Runnable>> pendingReachabilityHandlers = new ConcurrentHashMap<>();

    protected void registerConditionalConfiguration(ConfigurationCondition condition, Runnable runnable) {
        if (ConfigurationCondition.alwaysTrue().equals(condition)) {
            /* analysis optimization to include new types as early as possible */
            runnable.run();
        } else {
            List<Runnable> handlers = pendingReachabilityHandlers.computeIfAbsent(condition.getTypeName(), key -> new ArrayList<>());
            handlers.add(runnable);
        }
    }

    public void flushConditionalConfiguration(Feature.BeforeAnalysisAccess b) {
        for (Map.Entry<String, List<Runnable>> reachabilityEntry : pendingReachabilityHandlers.entrySet()) {
            TypeResult<Class<?>> typeResult = ((FeatureImpl.BeforeAnalysisAccessImpl) b).getImageClassLoader().findClass(reachabilityEntry.getKey());
            b.registerReachabilityHandler(access -> reachabilityEntry.getValue().forEach(Runnable::run), typeResult.get());
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
