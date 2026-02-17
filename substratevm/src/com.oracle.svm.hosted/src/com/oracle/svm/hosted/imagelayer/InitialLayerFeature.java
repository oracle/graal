/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.imagelayer;

import static com.oracle.svm.sdk.staging.layeredimage.LayeredCompilationBehavior.Behavior.PINNED_TO_INITIAL_LAYER;

import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.svm.core.bootstrap.BootstrapMethodInfo;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.jdk.UninterruptibleUtils;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.hosted.FeatureImpl.DuringSetupAccessImpl;
import com.oracle.svm.sdk.staging.hosted.layeredimage.LayeredCompilationSupport;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.BuildtimeAccessOnly;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.SingleLayer;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;
import com.oracle.svm.util.GuestAccess;
import com.oracle.svm.util.JVMCIReflectionUtil;
import com.oracle.svm.shared.util.ReflectionUtil;

import jdk.graal.compiler.options.ModifiableOptionValues;
import jdk.internal.misc.Unsafe;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

@AutomaticallyRegisteredFeature
@SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = SingleLayer.class)
public class InitialLayerFeature implements InternalFeature {
    @Override
    public boolean isInConfiguration(Feature.IsInConfigurationAccess access) {
        return ImageLayerBuildingSupport.buildingInitialLayer();
    }

    @Override
    public void duringSetup(DuringSetupAccess a) {
        DuringSetupAccessImpl access = (DuringSetupAccessImpl) a;
        /*
         * Make sure that critical VM components are included in the base layer by registering
         * runtime APIs as entry points. Although the types below are part of java.base, so they
         * would anyway be included in every base layer created with module=java.base, this ensures
         * that the base layer is usable regardless of the class inclusion policy.
         */
        var compilationSupport = LayeredCompilationSupport.singleton();
        compilationSupport.registerCompilationBehavior(ReflectionUtil.lookupMethod(Unsafe.class, "getUnsafe"), PINNED_TO_INITIAL_LAYER);
        compilationSupport.registerCompilationBehavior(ReflectionUtil.lookupMethod(Unsafe.class, "allocateInstance", Class.class), PINNED_TO_INITIAL_LAYER);
        compilationSupport.registerCompilationBehavior(ReflectionUtil.lookupMethod(Runtime.class, "getRuntime"), PINNED_TO_INITIAL_LAYER);
        compilationSupport.registerCompilationBehavior(ReflectionUtil.lookupMethod(Runtime.class, "gc"), PINNED_TO_INITIAL_LAYER);
        compilationSupport.registerCompilationBehavior(ReflectionUtil.lookupMethod(Class.class, "getResource", String.class), PINNED_TO_INITIAL_LAYER);

        AnalysisMetaAccess metaAccess = access.getMetaAccess();
        access.getUniverse().lookup(GuestAccess.elements().Uninterruptible).registerAsReachable("Core type");
        metaAccess.lookupJavaType(UninterruptibleUtils.class).registerAsReachable("Core type");
        access.getUniverse().lookup(getProxyClass(GuestAccess.elements().Uninterruptible)).registerAsInstantiated("Core type");
        metaAccess.lookupJavaType(BootstrapMethodInfo.class).registerAsInstantiated("Core type");
        metaAccess.lookupJavaType(BootstrapMethodInfo.ExceptionWrapper.class).registerAsInstantiated("Core type");
        metaAccess.lookupJavaType(UnmanagedMemory.class).registerAsReachable("Core type");
        metaAccess.lookupJavaType(VMThreads.OSThreadHandle.class).registerAsReachable("Core type");
        metaAccess.lookupJavaType(ReflectionUtil.lookupClass("com.oracle.svm.core.hub.DynamicHub$ClassRedefinedCountAccessors")).registerAsReachable("Core type");
        var pthread = ReflectionUtil.lookupClass(true, "com.oracle.svm.core.posix.headers.Pthread$pthread_t");
        if (pthread != null) {
            metaAccess.lookupJavaType(pthread).registerAsReachable("Core type");
        }
    }

    private static ResolvedJavaType getProxyClass(ResolvedJavaType uninterruptibleType) {
        GuestAccess access = GuestAccess.get();
        MetaAccessProvider metaAccess = access.getProviders().getMetaAccess();
        ConstantReflectionProvider constantReflection = access.getProviders().getConstantReflection();

        ResolvedJavaMethod getProxyClassMethod = JVMCIReflectionUtil.getUniqueDeclaredMethod(metaAccess, access.elems.java_lang_reflect_Proxy, "getProxyClass", ClassLoader.class,
                        Class[].class);
        ResolvedJavaMethod appClassLoaderMethod = JVMCIReflectionUtil.getUniqueDeclaredMethod(metaAccess, access.elems.jdk_internal_loader_ClassLoaders, "appClassLoader");

        JavaConstant appClassLoader = access.invoke(appClassLoaderMethod, null);
        JavaConstant uninterruptible = constantReflection.asJavaClass(uninterruptibleType);
        JavaConstant interfaces = access.asArrayConstant(access.elems.java_lang_Class, uninterruptible);

        JavaConstant proxyClass = access.invoke(getProxyClassMethod, null, appClassLoader, interfaces);
        return constantReflection.asJavaType(proxyClass);
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        // GR-71504 automatically detect accesses to fields of application layer only singletons
        access.registerAsUnsafeAccessed(ReflectionUtil.lookupField(ModifiableOptionValues.class, "v"));
    }
}
