/*
 * Copyright (c) 2013, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.jdk.management;

import java.lang.management.ClassLoadingMXBean;
import java.lang.management.CompilationMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryManagerMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.PlatformManagedObject;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.Type;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import javax.management.MBeanServerBuilder;
import javax.management.openmbean.OpenType;

import org.graalvm.collections.EconomicSet;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.RuntimeReflection;

import com.oracle.graal.pointsto.heap.ImageHeapConstant;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.jdk.JNIRegistrationUtil;
import com.oracle.svm.core.jdk.management.ManagementSupport;
import com.oracle.svm.core.jdk.management.SubstrateRuntimeMXBean;
import com.oracle.svm.core.jdk.management.SubstrateThreadMXBean;
import com.oracle.svm.core.thread.ThreadListenerSupport;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.imagelayer.CrossLayerConstantRegistry;
import com.oracle.svm.shared.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.shared.util.ReflectionUtil;
import com.oracle.svm.util.JVMCIRuntimeClassInitializationSupport;
import com.oracle.svm.util.dynamicaccess.JVMCIRuntimeReflection;

/** See {@link ManagementSupport} for documentation. */
@AutomaticallyRegisteredFeature
public final class ManagementFeature extends JNIRegistrationUtil implements InternalFeature {
    @SuppressWarnings({"unchecked", "rawtypes"}) //
    private static final Class<? extends PlatformManagedObject>[] MANAGED_OBJECT_REPLACEMENT_CANDIDATES = new Class[]{ClassLoadingMXBean.class,
                    CompilationMXBean.class, RuntimeMXBean.class,
                    ThreadMXBean.class, OperatingSystemMXBean.class, MemoryMXBean.class};

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        if (ImageLayerBuildingSupport.firstImageBuild()) {
            SubstrateRuntimeMXBean runtimeMXBean = new SubstrateRuntimeMXBean();
            ImageSingletons.add(SubstrateRuntimeMXBean.class, runtimeMXBean);

            SubstrateThreadMXBean threadMXBean = new SubstrateThreadMXBean();
            ImageSingletons.add(SubstrateThreadMXBean.class, threadMXBean);

            ManagementSupport managementSupport = new ManagementSupport(runtimeMXBean, threadMXBean);
            ImageSingletons.add(ManagementSupport.class, managementSupport);
            ThreadListenerSupport.get().register(managementSupport);
        }
    }

    @Override
    public void duringSetup(DuringSetupAccess access) {
        if (ImageLayerBuildingSupport.firstImageBuild()) {
            Map<PlatformManagedObject, PlatformManagedObject> platformManagedObjectReplacements = new IdentityHashMap<>();
            var managementSupport = ManagementSupport.getSingleton();
            for (var clazz : MANAGED_OBJECT_REPLACEMENT_CANDIDATES) {
                PlatformManagedObject source = ManagementFactory.getPlatformMXBean(clazz);
                PlatformManagedObject target = managementSupport.getPlatformMXBeanRaw(clazz);
                if (source != null && target != null) {
                    platformManagedObjectReplacements.put(source, target);
                    if (ImageLayerBuildingSupport.buildingInitialLayer()) {
                        /* Retain replacement beans even when only a later layer references them. */
                        CrossLayerConstantRegistry.singletonOrNull().registerHeapConstant(replacementKey(clazz), target);
                    }
                }
            }

            /*
             * PlatformManagedObject are often caches in static final fields of application classes.
             * Replacing the hosted objects with the proper runtime objects allows these application
             * classes to be initialized at image build time. Note that only singleton beans can be
             * automatically replaced, beans that have a list (like {@link GarbageCollectorMXBean}
             * cannot be replaced automatically.
             */
            access.registerObjectReplacer(source -> {
                if (source instanceof PlatformManagedObject) {
                    Object replacement = platformManagedObjectReplacements.get(source);
                    if (replacement != null) {
                        return replacement;
                    }
                }
                return source;
            });
        } else {
            /*
             * All VM beans were created in the initial layer. The registry carries their keys
             * through intermediate shared layers, so every extension layer links to the same
             * initial-layer objects. Do not register new bean replacements in intermediate layers.
             */
            Map<PlatformManagedObject, String> priorLayerPlatformManagedObjectReplacements = new IdentityHashMap<>();
            var registry = CrossLayerConstantRegistry.singletonOrNull();
            for (var clazz : MANAGED_OBJECT_REPLACEMENT_CANDIDATES) {
                String key = replacementKey(clazz);
                if (registry.constantExists(key)) {
                    PlatformManagedObject source = ManagementFactory.getPlatformMXBean(clazz);
                    assert source != null;
                    priorLayerPlatformManagedObjectReplacements.put(source, key);
                }
            }

            var accessImpl = (FeatureImpl.DuringSetupAccessImpl) access;
            var hostedValuesProvider = accessImpl.getUniverse().getHostedValuesProvider();
            // JVMCI migration blocked by GR-80269: Migrate ManagementFeature object replacement to Terminus
            accessImpl.registerJVMCIObjectToConstantReplacer(constant -> {
                Object source = hostedValuesProvider.asObject(Object.class, constant);
                if (source instanceof PlatformManagedObject) {
                    String key = priorLayerPlatformManagedObjectReplacements.get(source);
                    if (key != null) {
                        /* Reconstruct constants after all substitution processors are installed. */
                        return (ImageHeapConstant) registry.getConstant(key);
                    }
                }
                return null;
            });
        }

        JVMCIRuntimeClassInitializationSupport rci = JVMCIRuntimeClassInitializationSupport.singleton();
        rci.initializeAtBuildTime("com.sun.jmx.mbeanserver.DefaultMXBeanMappingFactory");
        rci.initializeAtBuildTime("com.sun.jmx.mbeanserver.DefaultMXBeanMappingFactory$Mappings");
        rci.initializeAtBuildTime("com.sun.jmx.mbeanserver.DefaultMXBeanMappingFactory$IdentityMapping");
        rci.initializeAtBuildTime("com.sun.jmx.mbeanserver.DescriptorCache");
        rci.initializeAtBuildTime("com.sun.jmx.remote.util.ClassLogger");

        rci.initializeAtRunTime("sun.management.MemoryImpl");
        rci.initializeAtRunTime("com.sun.management.internal.PlatformMBeanProviderImpl");
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        access.registerReachabilityHandler(ManagementFeature::registerMBeanServerFactoryNewBuilder, method(access, "javax.management.MBeanServerFactory", "newBuilder", Class.class));
        access.registerReachabilityHandler(ManagementFeature::registerMXBeanMappingMakeOpenClass,
                        method(access, "com.sun.jmx.mbeanserver.MXBeanMapping", "makeOpenClass", Type.class, OpenType.class));

        if (ImageLayerBuildingSupport.firstImageBuild()) {
            assert verifyMemoryManagerBeans();
            assert ManagementSupport.getSingleton().verifyNoOverlappingMxBeans();
        }
    }

    private static boolean verifyMemoryManagerBeans() {
        ManagementSupport managementSupport = ManagementSupport.getSingleton();
        List<MemoryPoolMXBean> memoryPools = managementSupport.getPlatformMXBeans(MemoryPoolMXBean.class);
        List<MemoryManagerMXBean> memoryManagers = managementSupport.getPlatformMXBeans(MemoryManagerMXBean.class);

        EconomicSet<String> memoryManagerNames = EconomicSet.create();
        EconomicSet<String> memoryPoolNames = EconomicSet.create();
        for (MemoryPoolMXBean memoryPool : memoryPools) {
            String memoryPoolName = memoryPool.getName();
            assert verifyObjectName(memoryPoolName);
            memoryPoolNames.add(memoryPoolName);
        }
        for (MemoryManagerMXBean memoryManager : memoryManagers) {
            String memoryManagerName = memoryManager.getName();
            assert verifyObjectName(memoryManagerName);
            memoryManagerNames.add(memoryManagerName);
            assert memoryPoolNames.containsAll(List.of(memoryManager.getMemoryPoolNames())) : memoryManagerName;
        }
        for (MemoryPoolMXBean memoryPool : memoryPools) {
            assert memoryManagerNames.containsAll(List.of(memoryPool.getMemoryManagerNames())) : memoryPool.getName();
        }
        return true;
    }

    private static boolean verifyObjectName(String name) {
        assert !name.contains(":");
        assert !name.contains("=");
        assert !name.contains("\"");
        assert !name.contains("\n");
        return true;
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
            JVMCIRuntimeReflection.register(type(access, className));
            JVMCIRuntimeReflection.register(type(access, "[L" + className + ";"));
        }
    }

    private static String replacementKey(Class<? extends PlatformManagedObject> clazz) {
        return "ManagementFeature#" + clazz.getName();
    }
}
