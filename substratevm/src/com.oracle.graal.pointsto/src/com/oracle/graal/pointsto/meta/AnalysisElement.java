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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.oracle.graal.pointsto.util.ConcurrentLightHashSet;
import org.graalvm.nativeimage.hosted.Feature.DuringAnalysisAccess;

public abstract class AnalysisElement {

    /**
     * Contains reachability handlers that are notified when the element is marked as reachable.
     * Each handler is notified only once, and then it is removed from the set.
     */

    private static final AtomicReferenceFieldUpdater<AnalysisElement, Object> reachableNotificationsUpdater = AtomicReferenceFieldUpdater
                    .newUpdater(AnalysisElement.class, Object.class, "elementReachableNotifications");

    @SuppressWarnings("unused") private volatile Object elementReachableNotifications;

    public void registerReachabilityNotification(ElementReachableNotification notification) {
        ConcurrentLightHashSet.addElement(this, reachableNotificationsUpdater, notification);
    }

    public void notifyReachabilityCallback(AnalysisUniverse universe, ElementReachableNotification notification) {
        notification.notifyCallback(universe, this);
        ConcurrentLightHashSet.removeElement(this, reachableNotificationsUpdater, notification);
    }

    protected void notifyReachabilityCallbacks(AnalysisUniverse universe) {
        ConcurrentLightHashSet.forEach(this, reachableNotificationsUpdater, (ElementReachableNotification c) -> c.notifyCallback(universe, this));
        ConcurrentLightHashSet.removeElementIf(this, reachableNotificationsUpdater, ElementReachableNotification::isNotified);
    }

    public abstract boolean isReachable();

    protected abstract void onReachable();

    /** Return true if reachability handlers should be executed for this element. */
    public boolean isTriggered() {
        return isReachable();
    }

    public static final class ElementReachableNotification {

        private final Consumer<DuringAnalysisAccess> callback;
        private final AtomicBoolean notified = new AtomicBoolean();

        public ElementReachableNotification(Consumer<DuringAnalysisAccess> callback) {
            this.callback = callback;
        }

        public boolean isNotified() {
            return notified.get();
        }

        /**
         * Notify the callback exactly once. Note that this callback can be shared by multiple
         * triggers, the one that triggers it is passed into triggeredElement for debugging.
         */
        private void notifyCallback(AnalysisUniverse universe, AnalysisElement triggeredElement) {
            assert triggeredElement.isTriggered();
            if (!notified.getAndSet(true)) {
                execute(universe, () -> callback.accept(universe.getConcurrentAnalysisAccess()));
            }
        }
    }

    public static final class SubtypeReachableNotification {
        private final BiConsumer<DuringAnalysisAccess, Class<?>> callback;
        private final Set<AnalysisType> seenSubtypes = ConcurrentHashMap.newKeySet();

        public SubtypeReachableNotification(BiConsumer<DuringAnalysisAccess, Class<?>> callback) {
            this.callback = callback;
        }

        /** Notify the callback exactly once for each reachable subtype. */
        public void notifyCallback(AnalysisUniverse universe, AnalysisType reachableSubtype) {
            assert reachableSubtype.isReachable();
            if (seenSubtypes.add(reachableSubtype)) {
                execute(universe, () -> callback.accept(universe.getConcurrentAnalysisAccess(), reachableSubtype.getJavaClass()));
            }
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

}
