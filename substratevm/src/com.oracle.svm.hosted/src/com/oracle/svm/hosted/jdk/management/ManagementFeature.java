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
import java.util.Arrays;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.management.MBeanServerBuilder;
import javax.management.openmbean.OpenType;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeClassInitialization;
import org.graalvm.nativeimage.hosted.RuntimeReflection;

import com.oracle.graal.pointsto.heap.ImageHeapConstant;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.jdk.JNIRegistrationUtil;
import com.oracle.svm.core.jdk.RuntimeSupportFeature;
import com.oracle.svm.core.jdk.management.ManagementSupport;
import com.oracle.svm.core.jdk.management.SubstrateRuntimeMXBean;
import com.oracle.svm.core.jdk.management.SubstrateThreadMXBean;
import com.oracle.svm.core.layeredimagesingleton.ImageSingletonLoader;
import com.oracle.svm.core.layeredimagesingleton.ImageSingletonWriter;
import com.oracle.svm.core.layeredimagesingleton.LayeredPersistFlags;
import com.oracle.svm.core.thread.ThreadListenerSupport;
import com.oracle.svm.core.thread.ThreadListenerSupportFeature;
import com.oracle.svm.core.traits.BuiltinTraits.BuildtimeAccessOnly;
import com.oracle.svm.core.traits.SingletonLayeredCallbacks;
import com.oracle.svm.core.traits.SingletonLayeredCallbacksSupplier;
import com.oracle.svm.core.traits.SingletonLayeredInstallationKind.Independent;
import com.oracle.svm.core.traits.SingletonTrait;
import com.oracle.svm.core.traits.SingletonTraitKind;
import com.oracle.svm.core.traits.SingletonTraits;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.imagelayer.HostedImageLayerBuildingSupport;
import com.oracle.svm.util.ReflectionUtil;
import com.oracle.svm.util.dynamicaccess.JVMCIRuntimeReflection;

import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.code.BytecodePosition;

/** See {@link ManagementSupport} for documentation. */
@AutomaticallyRegisteredFeature
@SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = ManagementFeature.LayeredCallbacks.class, layeredInstallationKind = Independent.class)
public final class ManagementFeature extends JNIRegistrationUtil implements InternalFeature {
    private static final int EMPTY_ID = -1;
    private static final int TO_PROCESS = -2;

    @SuppressWarnings({"unchecked", "rawtypes"}) //
    private static final Class<? extends PlatformManagedObject>[] MANAGED_OBJECT_REPLACEMENT_CANDIDATES = new Class[]{ClassLoadingMXBean.class,
                    CompilationMXBean.class, RuntimeMXBean.class,
                    ThreadMXBean.class, OperatingSystemMXBean.class, MemoryMXBean.class};

    private int[] manageObjectReplacementConstantIds = new int[]{EMPTY_ID, EMPTY_ID, EMPTY_ID, EMPTY_ID, EMPTY_ID, EMPTY_ID};

    @Override
    public List<Class<? extends Feature>> getRequiredFeatures() {
        return Arrays.asList(RuntimeSupportFeature.class, ThreadListenerSupportFeature.class);
    }

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
            for (int i = 0; i < MANAGED_OBJECT_REPLACEMENT_CANDIDATES.length; i++) {
                var clazz = MANAGED_OBJECT_REPLACEMENT_CANDIDATES[i];
                PlatformManagedObject source = ManagementFactory.getPlatformMXBean(clazz);
                PlatformManagedObject target = managementSupport.getPlatformMXBeanRaw(clazz);
                if (source != null && target != null) {
                    platformManagedObjectReplacements.put(source, target);
                    /*
                     * Mark slots corresponding to objects which need to be installed in the initial
                     * layer.
                     */
                    manageObjectReplacementConstantIds[i] = TO_PROCESS;
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
             * All beans were created in the initial layer. In subsequent layers we must ensure
             * these objects are again properly linked.
             */
            Map<PlatformManagedObject, ImageHeapConstant> priorLayerPlatformManagedObjectReplacements = new IdentityHashMap<>();
            var loader = HostedImageLayerBuildingSupport.singleton().getLoader();
            for (int i = 0; i < manageObjectReplacementConstantIds.length; i++) {
                int objectId = manageObjectReplacementConstantIds[i];
                if (objectId != EMPTY_ID) {
                    var clazz = MANAGED_OBJECT_REPLACEMENT_CANDIDATES[i];
                    PlatformManagedObject source = ManagementFactory.getPlatformMXBean(clazz);
                    assert source != null;
                    priorLayerPlatformManagedObjectReplacements.put(source, loader.getOrCreateConstant(objectId));
                }
            }

            ((FeatureImpl.DuringSetupAccessImpl) access).registerObjectToConstantReplacer(source -> {
                if (source instanceof PlatformManagedObject) {
                    return priorLayerPlatformManagedObjectReplacements.get(source);
                }
                return null;
            });
        }

        RuntimeClassInitialization.initializeAtBuildTime("com.sun.jmx.mbeanserver.DefaultMXBeanMappingFactory");
        RuntimeClassInitialization.initializeAtBuildTime("com.sun.jmx.mbeanserver.DefaultMXBeanMappingFactory$Mappings");
        RuntimeClassInitialization.initializeAtBuildTime("com.sun.jmx.mbeanserver.DefaultMXBeanMappingFactory$IdentityMapping");
        RuntimeClassInitialization.initializeAtBuildTime("com.sun.jmx.mbeanserver.DescriptorCache");
        RuntimeClassInitialization.initializeAtBuildTime("com.sun.jmx.remote.util.ClassLogger");

        RuntimeClassInitialization.initializeAtRunTime("sun.management.MemoryImpl");
        RuntimeClassInitialization.initializeAtRunTime("com.sun.management.internal.PlatformMBeanProviderImpl");
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        access.registerReachabilityHandler(ManagementFeature::registerMBeanServerFactoryNewBuilder, method(access, "javax.management.MBeanServerFactory", "newBuilder", Class.class));
        access.registerReachabilityHandler(ManagementFeature::registerMXBeanMappingMakeOpenClass,
                        method(access, "com.sun.jmx.mbeanserver.MXBeanMapping", "makeOpenClass", Type.class, OpenType.class));

        if (ImageLayerBuildingSupport.firstImageBuild()) {
            assert verifyMemoryManagerBeans();
            assert ManagementSupport.getSingleton().verifyNoOverlappingMxBeans();

            if (ImageLayerBuildingSupport.buildingInitialLayer()) {
                /*
                 * When building an initial layer, we must ensure that all beans potentially
                 * referred to by later layers are installed in the heap. Further, we must collect
                 * their constant ids so that we can access them in later layers.
                 */
                var managementSupport = ManagementSupport.getSingleton();
                var config = (FeatureImpl.BeforeAnalysisAccessImpl) access;
                var universe = config.getUniverse();
                var snippetReflection = universe.getSnippetReflection();
                AnalysisMetaAccess metaAccess = config.getMetaAccess();
                var method = metaAccess.lookupJavaMethod(ReflectionUtil.lookupMethod(ManagementSupport.class, "getPlatformMXBeans", Class.class));
                for (int i = 0; i < manageObjectReplacementConstantIds.length; i++) {
                    int objectId = manageObjectReplacementConstantIds[i];
                    if (objectId != EMPTY_ID) {
                        assert objectId == TO_PROCESS : objectId;
                        var clazz = MANAGED_OBJECT_REPLACEMENT_CANDIDATES[i];
                        PlatformManagedObject target = managementSupport.getPlatformMXBeanRaw(clazz);
                        var ihc = (ImageHeapConstant) snippetReflection.forObject(target);
                        universe.registerEmbeddedRoot(ihc, new BytecodePosition(null, method, BytecodeFrame.UNKNOWN_BCI));
                        manageObjectReplacementConstantIds[i] = ImageHeapConstant.getConstantID(ihc);
                    }
                }
            }
        }
    }

    private static boolean verifyMemoryManagerBeans() {
        ManagementSupport managementSupport = ManagementSupport.getSingleton();
        List<MemoryPoolMXBean> memoryPools = managementSupport.getPlatformMXBeans(MemoryPoolMXBean.class);
        List<MemoryManagerMXBean> memoryManagers = managementSupport.getPlatformMXBeans(MemoryManagerMXBean.class);

        Set<String> memoryManagerNames = new HashSet<>();
        Set<String> memoryPoolNames = new HashSet<>();
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

    /**
     * We must track the replaced objects so that all object replacements refer to the correct
     * object.
     */
    static class LayeredCallbacks extends SingletonLayeredCallbacksSupplier {

        @Override
        public SingletonTrait getLayeredCallbacksTrait() {
            return new SingletonTrait(SingletonTraitKind.LAYERED_CALLBACKS, new SingletonLayeredCallbacks<ManagementFeature>() {
                @Override
                public LayeredPersistFlags doPersist(ImageSingletonWriter writer, ManagementFeature singleton) {
                    writer.writeIntList("objectIds", Arrays.stream(singleton.manageObjectReplacementConstantIds).boxed().toList());
                    return LayeredPersistFlags.CALLBACK_ON_REGISTRATION;
                }

                @Override
                public void onSingletonRegistration(ImageSingletonLoader loader, ManagementFeature singleton) {
                    singleton.manageObjectReplacementConstantIds = loader.readIntList("objectIds").stream().mapToInt(Integer::intValue).toArray();
                }
            });
        }
    }
}
