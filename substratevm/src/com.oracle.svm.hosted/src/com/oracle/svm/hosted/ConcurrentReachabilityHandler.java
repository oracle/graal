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
package com.oracle.svm.hosted;

import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.graal.pointsto.meta.AnalysisElement;
import com.oracle.graal.pointsto.meta.AnalysisElement.ElementNotification;
import com.oracle.graal.pointsto.meta.AnalysisElement.MethodOverrideReachableNotification;
import com.oracle.graal.pointsto.meta.AnalysisElement.SubtypeReachableNotification;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.FeatureImpl.BeforeAnalysisAccessImpl;

@AutomaticallyRegisteredFeature
public class ConcurrentReachabilityHandler implements ReachabilityHandler, InternalFeature {

    private final Map<Consumer<DuringAnalysisAccess>, ElementNotification> reachabilityNotifications = new ConcurrentHashMap<>();

    public static ConcurrentReachabilityHandler singleton() {
        return ImageSingletons.lookup(ConcurrentReachabilityHandler.class);
    }

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return SubstrateOptions.RunReachabilityHandlersConcurrently.getValue();
    }

    @Override
    public void registerMethodOverrideReachabilityHandler(BeforeAnalysisAccessImpl access, BiConsumer<DuringAnalysisAccess, Executable> callback, Executable baseMethod) {
        AnalysisMetaAccess metaAccess = access.getMetaAccess();
        AnalysisMethod baseAnalysisMethod = metaAccess.lookupJavaMethod(baseMethod);

        MethodOverrideReachableNotification notification = new MethodOverrideReachableNotification(callback);
        baseAnalysisMethod.registerOverrideReachabilityNotification(notification);

        /*
         * Notify for already reachable overrides. When a new override becomes reachable all
         * installed reachability callbacks in the supertypes declaring the method are triggered.
         */
        for (AnalysisMethod override : access.reachableMethodOverrides(baseAnalysisMethod)) {
            notification.notifyCallback(metaAccess.getUniverse(), override);
        }
    }

    @Override
    public void registerSubtypeReachabilityHandler(BeforeAnalysisAccessImpl access, BiConsumer<DuringAnalysisAccess, Class<?>> callback, Class<?> baseClass) {
        AnalysisMetaAccess metaAccess = access.getMetaAccess();
        AnalysisType baseType = metaAccess.lookupJavaType(baseClass);

        SubtypeReachableNotification notification = new SubtypeReachableNotification(callback);
        baseType.registerSubtypeReachabilityNotification(notification);

        /*
         * Notify for already reachable subtypes. When a new type becomes reachable all installed
         * reachability callbacks in the supertypes are triggered.
         */
        for (AnalysisType subtype : access.reachableSubtypes(baseType)) {
            notification.notifyCallback(metaAccess.getUniverse(), subtype);
        }
    }

    @Override
    public void registerClassInitializerReachabilityHandler(BeforeAnalysisAccessImpl access, Consumer<DuringAnalysisAccess> callback, Class<?> clazz) {
        registerConcurrentReachabilityHandler(access, callback, new Class<?>[]{clazz}, true);
    }

    @Override
    public void registerReachabilityHandler(BeforeAnalysisAccessImpl access, Consumer<DuringAnalysisAccess> callback, Object[] triggers) {
        registerConcurrentReachabilityHandler(access, callback, triggers, false);
    }

    private void registerConcurrentReachabilityHandler(BeforeAnalysisAccessImpl access, Consumer<DuringAnalysisAccess> callback, Object[] triggers, boolean triggerOnClassInitializer) {
        AnalysisMetaAccess metaAccess = access.getMetaAccess();

        /*
         * All callback->notification pairs are tracked by the reachabilityNotifications map to
         * prevent registering the same callback multiple times. The notifications are also tracked
         * by each AnalysisElement, i.e., each trigger, and are removed as soon as they are
         * notified.
         */
        ElementNotification notification = reachabilityNotifications.computeIfAbsent(callback, ElementNotification::new);

        if (notification.isNotified()) {
            /* Already notified from an earlier registration, nothing to do. */
            return;
        }

        for (Object trigger : triggers) {
            AnalysisElement analysisElement;
            if (trigger instanceof Class) {
                AnalysisType aType = metaAccess.lookupJavaType((Class<?>) trigger);
                analysisElement = triggerOnClassInitializer ? aType.getClassInitializer() : aType;
            } else if (trigger instanceof Field) {
                analysisElement = metaAccess.lookupJavaField((Field) trigger);
            } else if (trigger instanceof Executable) {
                analysisElement = metaAccess.lookupJavaMethod((Executable) trigger);
            } else {
                throw UserError.abort("registerReachabilityHandler called with an element that is not a Class, Field, Method, or Constructor: %s", trigger.getClass().getTypeName());
            }

            analysisElement.registerReachabilityNotification(notification);
            if (analysisElement.isTriggered()) {
                /*
                 * Element already triggered, just notify the callback. At this point we could just
                 * notify the callback and bail out, but, for debugging, it may be useful to execute
                 * the notification for each trigger. Note that although the notification can be
                 * shared between multiple triggers the notification mechanism ensures that the
                 * callback itself is only executed once.
                 */
                analysisElement.notifyReachabilityCallback(access.getUniverse(), notification);
            }
        }
    }
}
