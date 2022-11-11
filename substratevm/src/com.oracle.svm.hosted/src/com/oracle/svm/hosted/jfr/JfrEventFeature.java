/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.jfr;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeClassInitialization;
import org.graalvm.nativeimage.hosted.RuntimeReflection;

import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.DynamicHubSupport;
import com.oracle.svm.core.jfr.JfrFeature;
import com.oracle.svm.core.jfr.JfrJavaEvents;
import com.oracle.svm.core.jfr.traceid.JfrTraceId;
import com.oracle.svm.core.jfr.traceid.JfrTraceIdMap;
import com.oracle.svm.core.meta.SharedType;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.FeatureImpl.DuringAnalysisAccessImpl;
import com.oracle.svm.util.ModuleSupport;

import jdk.jfr.Event;
import jdk.jfr.internal.JVM;
import jdk.vm.ci.meta.MetaAccessProvider;

/**
 * Support for Java-level JFR events. This feature is only present if the {@link JfrFeature} is used
 * as well but it needs functionality that is only available in com.oracle.svm.hosted.
 */
@AutomaticallyRegisteredFeature
public class JfrEventFeature implements InternalFeature {
    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return JfrFeature.isInConfiguration(false);
    }

    @Override
    public List<Class<? extends Feature>> getRequiredFeatures() {
        return Collections.singletonList(JfrFeature.class);
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ModuleSupport.accessPackagesToClass(ModuleSupport.Access.OPEN, JfrEventFeature.class, false, "jdk.jfr", "jdk.jfr.events");
        ModuleSupport.accessPackagesToClass(ModuleSupport.Access.OPEN, JfrFeature.class, false, "jdk.jfr", "jdk.jfr.events");
        ModuleSupport.accessPackagesToClass(ModuleSupport.Access.OPEN, JfrEventSubstitution.class, false, "jdk.internal.vm.ci", "jdk.vm.ci.hotspot");
    }

    @Override
    public void duringSetup(DuringSetupAccess c) {
        FeatureImpl.DuringSetupAccessImpl config = (FeatureImpl.DuringSetupAccessImpl) c;
        MetaAccessProvider metaAccess = config.getMetaAccess().getWrapped();

        for (Class<?> eventSubClass : config.findSubclasses(Event.class)) {
            RuntimeClassInitialization.initializeAtBuildTime(eventSubClass.getName());
        }
        config.registerSubstitutionProcessor(new JfrEventSubstitution(metaAccess));
    }

    @Override
    public void beforeAnalysis(Feature.BeforeAnalysisAccess access) {
        if (JavaVersionUtil.JAVA_SPEC < 19) {
            Class<?> eventClass = access.findClassByName("jdk.internal.event.Event");
            if (eventClass != null) {
                access.registerSubtypeReachabilityHandler(JfrEventFeature::eventSubtypeReachable, eventClass);
            }
        }
    }

    @Override
    public void beforeCompilation(BeforeCompilationAccess a) {
        // Reserve slot 0 for error-catcher.
        int mapSize = ImageSingletons.lookup(DynamicHubSupport.class).getMaxTypeId() + 1;

        // Create trace-ID map with fixed size.
        ImageSingletons.lookup(JfrTraceIdMap.class).initialize(mapSize);

        // Scan all classes and build sets of packages, modules and class-loaders. Count all items.
        Collection<? extends SharedType> types = ((FeatureImpl.CompilationAccessImpl) a).getTypes();
        for (SharedType type : types) {
            DynamicHub hub = type.getHub();
            Class<?> clazz = hub.getHostedJavaClass();
            // Off-set by one for error-catcher
            JfrTraceId.assign(clazz, hub.getTypeID() + 1);
        }
        if (JavaVersionUtil.JAVA_SPEC >= 19) {
            try {
                FeatureImpl.CompilationAccessImpl accessImpl = ((FeatureImpl.CompilationAccessImpl) a);
                Method getConfiguration = JVM.class.getDeclaredMethod("getConfiguration", Class.class);
                for (var newEventClass : JfrJavaEvents.getAllEventClasses()) {
                    /* Store the event configuration in the companion. */
                    Object ec = getConfiguration.invoke(JVM.getJVM(), newEventClass);
                    DynamicHub dynamicHub = accessImpl.getMetaAccess().lookupJavaType(newEventClass).getHub();
                    dynamicHub.setJrfEventConfiguration(ec);
                }
            } catch (ReflectiveOperationException ex) {
                throw VMError.shouldNotReachHere(ex);
            }
        }
    }

    private static void eventSubtypeReachable(DuringAnalysisAccess a, Class<?> c) {
        DuringAnalysisAccessImpl access = (DuringAnalysisAccessImpl) a;
        if (c.getCanonicalName().equals("jdk.jfr.Event") ||
                        c.getCanonicalName().equals("jdk.internal.event.Event") ||
                        c.getCanonicalName().equals("jdk.jfr.events.AbstractJDKEvent") ||
                        c.getCanonicalName().equals("jdk.jfr.events.AbstractBufferStatisticsEvent") ||
                        Modifier.isAbstract(c.getModifiers())) {
            return;
        }
        try {
            Field f = c.getDeclaredField("eventHandler");
            RuntimeReflection.register(f);
            access.rescanRoot(f);
            if (!access.concurrentReachabilityHandlers()) {
                access.requireAnalysisIteration();
            }
        } catch (Exception e) {
            throw VMError.shouldNotReachHere("Unable to register eventHandler for: " + c.getCanonicalName(), e);
        }
    }
}
