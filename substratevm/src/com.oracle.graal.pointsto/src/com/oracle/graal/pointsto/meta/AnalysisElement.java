/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.pointsto.meta;

import java.lang.reflect.Executable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.graalvm.nativeimage.hosted.Feature.DuringAnalysisAccess;

import com.oracle.graal.pointsto.ObjectScanner;
import com.oracle.graal.pointsto.util.AnalysisFuture;
import com.oracle.graal.pointsto.util.ConcurrentLightHashSet;

import jdk.vm.ci.code.BytecodePosition;
import jdk.vm.ci.meta.ModifiersProvider;

public abstract class AnalysisElement {

    /**
     * Contains reachability handlers that are notified when the element is marked as reachable.
     * Each handler is notified only once, and then it is removed from the set.
     */

    private static final AtomicReferenceFieldUpdater<AnalysisElement, Object> reachableNotificationsUpdater = AtomicReferenceFieldUpdater
                    .newUpdater(AnalysisElement.class, Object.class, "elementReachableNotifications");

    @SuppressWarnings("unused") private volatile Object elementReachableNotifications;

    public void registerReachabilityNotification(ElementNotification notification) {
        ConcurrentLightHashSet.addElement(this, reachableNotificationsUpdater, notification);
    }

    public void notifyReachabilityCallback(AnalysisUniverse universe, ElementNotification notification) {
        notification.notifyCallback(universe, this);
        ConcurrentLightHashSet.removeElement(this, reachableNotificationsUpdater, notification);
    }

    protected void notifyReachabilityCallbacks(AnalysisUniverse universe, List<AnalysisFuture<Void>> futures) {
        ConcurrentLightHashSet.forEach(this, reachableNotificationsUpdater, (ElementNotification c) -> futures.add(c.notifyCallback(universe, this)));
        ConcurrentLightHashSet.removeElementIf(this, reachableNotificationsUpdater, ElementNotification::isNotified);
    }

    /**
     * Used to validate the reason why an analysis element is registered as reachable.
     */
    boolean isValidReason(Object reason) {
        if (reason == null) {
            return false;
        }
        if (reason instanceof String) {
            return !((String) reason).isEmpty();
        }
        /*
         * ModifiersProvider is a common interface of ResolvedJavaField, ResolvedJavaMethod and
         * ResolvedJavaType.
         */
        return reason instanceof AnalysisElement || reason instanceof ModifiersProvider || reason instanceof ObjectScanner.ScanReason || reason instanceof BytecodePosition;
    }

    public abstract boolean isReachable();

    protected abstract void onReachable();

    /** Return true if reachability handlers should be executed for this element. */
    public boolean isTriggered() {
        return isReachable();
    }

    public static final class ElementNotification {

        private final Consumer<DuringAnalysisAccess> callback;
        private final AtomicReference<AnalysisFuture<Void>> notified = new AtomicReference<>();

        public ElementNotification(Consumer<DuringAnalysisAccess> callback) {
            this.callback = callback;
        }

        public boolean isNotified() {
            return notified.get() != null;
        }

        /**
         * Notify the callback exactly once. Note that this callback can be shared by multiple
         * triggers, the one that triggers it is passed into triggeredElement for debugging.
         */
        AnalysisFuture<Void> notifyCallback(AnalysisUniverse universe, AnalysisElement triggeredElement) {
            assert triggeredElement.isTriggered();
            var existing = notified.get();
            if (existing != null) {
                return existing;
            }

            AnalysisFuture<Void> newValue = new AnalysisFuture<>(() -> {
                callback.accept(universe.getConcurrentAnalysisAccess());
                return null;
            });

            existing = notified.compareAndExchange(null, newValue);
            if (existing != null) {
                return existing;
            }

            execute(universe, newValue);
            return newValue;
        }
    }

    public static final class SubtypeReachableNotification {
        private final BiConsumer<DuringAnalysisAccess, Class<?>> callback;
        private final Map<AnalysisType, AnalysisFuture<Void>> seenSubtypes = new ConcurrentHashMap<>();

        public SubtypeReachableNotification(BiConsumer<DuringAnalysisAccess, Class<?>> callback) {
            this.callback = callback;
        }

        /** Notify the callback exactly once for each reachable subtype. */
        public AnalysisFuture<Void> notifyCallback(AnalysisUniverse universe, AnalysisType reachableSubtype) {
            assert reachableSubtype.isReachable();
            return seenSubtypes.computeIfAbsent(reachableSubtype, k -> {
                AnalysisFuture<Void> newValue = new AnalysisFuture<>(() -> {
                    callback.accept(universe.getConcurrentAnalysisAccess(), reachableSubtype.getJavaClass());
                    return null;
                });
                execute(universe, newValue);
                return newValue;
            });
        }
    }

    public static final class MethodOverrideReachableNotification {
        private final BiConsumer<DuringAnalysisAccess, Executable> callback;
        private final Set<AnalysisMethod> seenOverride = ConcurrentHashMap.newKeySet();

        public MethodOverrideReachableNotification(BiConsumer<DuringAnalysisAccess, Executable> callback) {
            this.callback = callback;
        }

        /** Notify the callback exactly once for each reachable method override. */
        public void notifyCallback(AnalysisUniverse universe, AnalysisMethod reachableOverride) {
            assert reachableOverride.isReachable();
            if (seenOverride.add(reachableOverride)) {
                execute(universe, () -> callback.accept(universe.getConcurrentAnalysisAccess(), reachableOverride.getJavaMethod()));
            }
        }
    }

    private static void execute(AnalysisUniverse universe, Runnable task) {
        /*
         * Post the tasks to the analysis executor. This ensures that even for elements registered
         * as reachable early, before the analysis is started, the reachability callbacks are run
         * during the analysis.
         */
        universe.getBigbang().postTask((d) -> task.run());
    }

    private static void execute(AnalysisUniverse universe, AnalysisFuture<?> task) {
        /*
         * Post the tasks to the analysis executor. This ensures that even for elements registered
         * as reachable early, before the analysis is started, the reachability callbacks are run
         * during the analysis.
         */
        universe.getBigbang().postTask((d) -> task.ensureDone());
    }
}
