/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.FeatureImpl.BeforeAnalysisAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.DuringAnalysisAccessImpl;

@AutomaticFeature
public class ReachabilityHandlerFeature implements Feature {

    private final IdentityHashMap<Object, Set<Object>> activeHandlers = new IdentityHashMap<>();
    private final IdentityHashMap<Object, Map<Object, Set<Object>>> triggeredHandlers = new IdentityHashMap<>();

    public static ReachabilityHandlerFeature singleton() {
        return ImageSingletons.lookup(ReachabilityHandlerFeature.class);
    }

    public void registerMethodOverrideReachabilityHandler(BeforeAnalysisAccessImpl a, BiConsumer<DuringAnalysisAccess, Executable> callback, Executable baseMethod) {
        registerReachabilityHandler(a, callback, new Executable[]{baseMethod});
    }

    public void registerSubtypeReachabilityHandler(BeforeAnalysisAccess a, BiConsumer<DuringAnalysisAccess, Class<?>> callback, Class<?> baseClass) {
        registerReachabilityHandler(a, callback, new Class<?>[]{baseClass});
    }

    public void registerReachabilityHandler(BeforeAnalysisAccess a, Consumer<DuringAnalysisAccess> callback, Object[] triggers) {
        registerReachabilityHandler(a, (Object) callback, triggers);
    }

    private void registerReachabilityHandler(BeforeAnalysisAccess a, Object callback, Object[] triggers) {
        if (triggeredHandlers.containsKey(callback)) {
            /* Handler has already been triggered from another registration, so nothing to do. */
            return;
        }

        BeforeAnalysisAccessImpl access = (BeforeAnalysisAccessImpl) a;
        AnalysisMetaAccess metaAccess = access.getMetaAccess();

        Set<Object> triggerSet = activeHandlers.computeIfAbsent(callback, c -> new HashSet<>());

        for (Object trigger : triggers) {
            if (trigger instanceof Class) {
                triggerSet.add(metaAccess.lookupJavaType((Class<?>) trigger));
            } else if (trigger instanceof Field) {
                triggerSet.add(metaAccess.lookupJavaField((Field) trigger));
            } else if (trigger instanceof Executable) {
                triggerSet.add(metaAccess.lookupJavaMethod((Executable) trigger));
            } else {
                throw UserError.abort("registerReachabilityHandler called with an element that is not a Class, Field, Method, or Constructor: " + trigger.getClass().getTypeName());
            }
        }

        if (access instanceof DuringAnalysisAccess) {
            /* Ensure that a newly installed callback runs if a trigger is already reachable. */
            ((DuringAnalysisAccess) access).requireAnalysisIteration();
        }
    }

    @Override
    public void duringAnalysis(DuringAnalysisAccess a) {
        DuringAnalysisAccessImpl access = (DuringAnalysisAccessImpl) a;
        HashSet<Object> handledCallbacks = new HashSet<>();
        HashSet<Object> callbacks = new HashSet<>(activeHandlers.keySet());
        do {
            List<Object> completedCallbacks = new ArrayList<>();
            for (Object callback : callbacks) {
                Set<Object> triggers = activeHandlers.get(callback);
                if (callback instanceof Consumer) {
                    if (isTriggered(access, triggers)) {
                        triggeredHandlers.put(callback, null);
                        toExactCallback(callback).accept(access);
                        completedCallbacks.add(callback);
                    }
                } else {
                    VMError.guarantee(callback instanceof BiConsumer);
                    processReachable(access, callback, triggers);
                }
                handledCallbacks.add(callback);
            }
            for (Object completed : completedCallbacks) {
                activeHandlers.remove(completed);
                handledCallbacks.remove(completed);
            }
            callbacks = new HashSet<>(activeHandlers.keySet());
            callbacks.removeAll(handledCallbacks);
        } while (!callbacks.isEmpty());
    }

    private static boolean isTriggered(DuringAnalysisAccessImpl access, Set<Object> triggers) {
        for (Object trigger : triggers) {
            if (trigger instanceof AnalysisType) {
                if (access.isReachable((AnalysisType) trigger)) {
                    return true;
                }
            } else if (trigger instanceof AnalysisField) {
                if (access.isReachable((AnalysisField) trigger)) {
                    return true;
                }
            } else if (trigger instanceof AnalysisMethod) {
                if (access.isReachable((AnalysisMethod) trigger)) {
                    return true;
                }
            } else {
                throw VMError.shouldNotReachHere("Unexpected trigger: " + trigger.getClass().getTypeName());
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static Consumer<DuringAnalysisAccess> toExactCallback(Object callback) {
        return (Consumer<DuringAnalysisAccess>) callback;
    }

    private void processReachable(DuringAnalysisAccessImpl access, Object callback, Set<Object> triggers) {
        Map<Object, Set<Object>> handledTriggers = triggeredHandlers.computeIfAbsent(callback, c -> new IdentityHashMap<>());
        for (Object trigger : triggers) {
            if (trigger instanceof AnalysisType) {
                Set<AnalysisType> newReachable = access.reachableSubtypes(((AnalysisType) trigger));
                Set<Object> prevReachable = handledTriggers.computeIfAbsent(trigger, c -> new HashSet<>());
                newReachable.removeAll(prevReachable);
                for (AnalysisType reachable : newReachable) {
                    toSubtypeCallback(callback).accept(access, reachable.getJavaClass());
                    prevReachable.add(reachable);
                }
            } else if (trigger instanceof AnalysisMethod) {
                Set<AnalysisMethod> newReachable = access.reachableMethodOverrides((AnalysisMethod) trigger);
                Set<Object> prevReachable = handledTriggers.computeIfAbsent(trigger, c -> new HashSet<>());
                newReachable.removeAll(prevReachable);
                for (AnalysisMethod reachable : newReachable) {
                    toOverrideCallback(callback).accept(access, reachable.getJavaMethod());
                    prevReachable.add(reachable);
                }
            } else {
                throw VMError.shouldNotReachHere("Unexpected subtype/override trigger: " + trigger.getClass().getTypeName());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static BiConsumer<DuringAnalysisAccess, Class<?>> toSubtypeCallback(Object callback) {
        return (BiConsumer<DuringAnalysisAccess, Class<?>>) callback;
    }

    @SuppressWarnings("unchecked")
    private static BiConsumer<DuringAnalysisAccess, Executable> toOverrideCallback(Object callback) {
        return (BiConsumer<DuringAnalysisAccess, Executable>) callback;
    }
}
