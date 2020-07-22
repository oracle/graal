/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jdk.management;

// Checkstyle: allow reflection

import java.lang.management.ClassLoadingMXBean;
import java.lang.management.CompilationMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.PlatformManagedObject;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import javax.management.MBeanServerBuilder;
import javax.management.openmbean.OpenType;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeClassInitialization;
import org.graalvm.nativeimage.hosted.RuntimeReflection;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.jdk.JNIRegistrationUtil;
import com.oracle.svm.core.jdk.RuntimeFeature;
import com.oracle.svm.util.ReflectionUtil;

/** See {@link ManagementSupport} for documentation. */
@AutomaticFeature
public final class ManagementFeature extends JNIRegistrationUtil implements Feature {
    private Map<PlatformManagedObject, PlatformManagedObject> platformManagedObjectReplacements;

    @Override
    public List<Class<? extends Feature>> getRequiredFeatures() {
        return Collections.singletonList(RuntimeFeature.class);
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(ManagementSupport.class, new ManagementSupport());
    }

    @Override
    public void duringSetup(DuringSetupAccess access) {
        platformManagedObjectReplacements = new IdentityHashMap<>();
        for (Class<? extends PlatformManagedObject> clazz : Arrays.asList(ClassLoadingMXBean.class, CompilationMXBean.class, RuntimeMXBean.class,
                        ThreadMXBean.class, OperatingSystemMXBean.class, MemoryMXBean.class)) {
            PlatformManagedObject source = ManagementFactory.getPlatformMXBean(clazz);
            PlatformManagedObject target = (PlatformManagedObject) ManagementSupport.getSingleton().platformManagedObjectsMap.get(clazz);
            if (source != null && target != null) {
                platformManagedObjectReplacements.put(source, target);
            }
        }
        access.registerObjectReplacer(this::replaceHostedPlatformManagedObject);

        RuntimeClassInitialization.initializeAtBuildTime("com.sun.jmx.mbeanserver.DefaultMXBeanMappingFactory", "Avoids unnecessary reflection in the image");
    }

    /**
     * PlatformManagedObject are often caches in static final fields of application classes.
     * Replacing the hosted objects with the proper runtime objects allows these application classes
     * to be initialized at image build time. Note that only singleton beans can be automatically
     * replaced, beans that have a list (like {@link GarbageCollectorMXBean} cannot be replaced
     * automatically.
     */
    private Object replaceHostedPlatformManagedObject(Object source) {
        if (source instanceof PlatformManagedObject) {
            Object replacement = platformManagedObjectReplacements.get(source);
            if (replacement != null) {
                return replacement;
            }
        }
        return source;
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        access.registerReachabilityHandler(ManagementFeature::registerMBeanServerFactoryNewBuilder, method(access, "javax.management.MBeanServerFactory", "newBuilder", Class.class));
        access.registerReachabilityHandler(ManagementFeature::registerMXBeanMappingMakeOpenClass, method(access, "com.sun.jmx.mbeanserver.MXBeanMapping", "makeOpenClass", Type.class, OpenType.class));
    }

    private static void registerMBeanServerFactoryNewBuilder(@SuppressWarnings("unused") DuringAnalysisAccess a) {
        /*
         * MBeanServerBuilder is the default builder used when no class is explicitly specified via
         * a system property.
         */
        RuntimeReflection.register(ReflectionUtil.lookupConstructor(MBeanServerBuilder.class));
    }

    private static void registerMXBeanMappingMakeOpenClass(DuringAnalysisAccess access) {
        /*
         * The allowed "open types" are looked up by class name. According to the specification, all
         * array types of arbitrary depth are allowed, but we cannot register all array classes.
         * Registering the one-dimensional array classes capture the common use cases.
         */
        for (String className : OpenType.ALLOWED_CLASSNAMES_LIST) {
            RuntimeReflection.register(clazz(access, className));
            RuntimeReflection.register(clazz(access, "[L" + className + ";"));
        }
    }
}
