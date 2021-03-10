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
package com.oracle.svm.jfr;

import static com.oracle.svm.jfr.PredefinedJFCSubstitition.DEFAULT_JFC;
import static com.oracle.svm.jfr.PredefinedJFCSubstitition.PROFILE_JFC;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.jdk.Target_java_lang_ClassLoader;
import com.oracle.svm.core.jdk.Target_java_lang_Module;
import com.oracle.svm.core.jdk.Target_java_lang_Package;
import com.oracle.svm.core.meta.SharedType;
import com.oracle.svm.jfr.traceid.JfrTraceId;
import com.oracle.svm.jfr.traceid.JfrTraceIdMap;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeClassInitialization;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.jdk.Resources;
import com.oracle.svm.core.jdk.RuntimeSupport;
import com.oracle.svm.core.thread.ThreadListenerFeature;
import com.oracle.svm.core.thread.ThreadListenerSupport;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.util.ModuleSupport;

import jdk.jfr.Event;
import jdk.jfr.internal.JVM;
import jdk.vm.ci.meta.MetaAccessProvider;

/**
 * Provides basic JFR support.
 *
 * A lot of the JFR infrastructure is {@link Uninterruptible} and uses native memory instead of the
 * Java heap. This is necessary as JFR events may, for example, also be used in the following
 * situations:
 * <ul>
 * <li>When allocating Java heap memory.</li>
 * <li>While executing a garbage collection (i.e., when the Java heap is not necessarily in a
 * consistent state).</li>
 * </ul>
 */
@AutomaticFeature
public class JfrFeature implements Feature {
    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return JfrEnabled.get();
    }

    @Override
    public List<Class<? extends Feature>> getRequiredFeatures() {
        return Collections.singletonList(ThreadListenerFeature.class);
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ModuleSupport.exportAndOpenAllPackagesToUnnamed("jdk.jfr", false);
        ModuleSupport.exportAndOpenAllPackagesToUnnamed("java.base", false);

        JVM.getJVM().createNativeJFR();
        SubstrateTypeLibrary.installSubstrateTypeLibrary();

        ImageSingletons.add(SubstrateJVM.class, new SubstrateJVM());
        ImageSingletons.add(JfrManager.class, new JfrManager());
        ImageSingletons.add(JfrSerializerSupport.class, new JfrSerializerSupport());
        ImageSingletons.add(JfrRuntimeAccess.class, new JfrRuntimeAccess());

        JfrSerializerSupport.get().register(new JfrFrameTypeSerializer());
        ThreadListenerSupport.get().register(SubstrateJVM.getThreadLocal());
    }

    @Override
    public void duringSetup(DuringSetupAccess c) {
        FeatureImpl.DuringSetupAccessImpl config = (FeatureImpl.DuringSetupAccessImpl) c;
        MetaAccessProvider metaAccess = config.getMetaAccess().getWrapped();

        for (Class<?> eventSubClass : config.findSubclasses(Event.class)) {
            RuntimeClassInitialization.initializeAtBuildTime(eventSubClass.getName());
        }
        config.registerSubstitutionProcessor(new JfrEventSubstitution(metaAccess));

        // Register for runtime access.
        ClassLoader cl = PredefinedJFCSubstitition.class.getClassLoader();
        Resources.registerResource(DEFAULT_JFC, cl.getResourceAsStream(DEFAULT_JFC));
        Resources.registerResource(PROFILE_JFC, cl.getResourceAsStream(PROFILE_JFC));
    }

    @Override
    public void beforeAnalysis(Feature.BeforeAnalysisAccess access) {
        RuntimeSupport runtime = RuntimeSupport.getRuntimeSupport();
        JfrManager manager = JfrManager.get();
        runtime.addStartupHook(manager::setup);
        runtime.addShutdownHook(manager::teardown);
    }

    private static class JfrMetadataCollection {
        final Map<Class<?>, Integer> classToIndex = new HashMap<>();
        final Set<Package> packages = new HashSet<>();
        final Set<Module> modules = new HashSet<>();
        final Set<ClassLoader> classLoaders = new HashSet<>();
        int mapSize = 1; // First field is error-catcher
    }

    private void assignClass(JfrMetadataCollection metadata, Class<?> clazz, int id) {
        metadata.classToIndex.put(clazz, id);
        metadata.mapSize++;
        Package pkg = clazz.getPackage();
        if (pkg != null && !metadata.packages.contains(pkg)) {
            metadata.packages.add(pkg);
            metadata.mapSize++;
        }
        Module module = clazz.getModule();
        if (module != null && !metadata.modules.contains(module)) {
            metadata.modules.add(module);
            metadata.mapSize++;
        }
        ClassLoader cl = clazz.getClassLoader();
        if (cl != null && !metadata.classLoaders.contains(cl)) {
            metadata.classLoaders.add(cl);
            metadata.mapSize++;
        }
    }

    @Override
    public void beforeCompilation(BeforeCompilationAccess a) {

        final JfrMetadataCollection metadata = new JfrMetadataCollection();

        // Scan all classes and build sets of packages, modules and class-loaders. Count all items.
        Collection<? extends SharedType> types = ((FeatureImpl.CompilationAccessImpl)a).getTypes();
        for (SharedType type : types) {
            DynamicHub hub = type.getHub();
            assignClass(metadata, hub.getHostedJavaClass(), hub.getTypeID());
        }

        // Create trace-ID map with fixed size.
        JfrTraceIdMap map = new JfrTraceIdMap(metadata.mapSize);
        ImageSingletons.lookup(JfrRuntimeAccess.class).setTraceIdMap(map);

        // Assign each class, package, module and class-loader a unique index.
        int idx = metadata.classToIndex.size() + 1;
        for (Class<?> clazz : metadata.classToIndex.keySet()) {
            if (metadata.classToIndex.get(clazz) + 1 >= idx) {
                throw new ArrayIndexOutOfBoundsException();
            }
            if (!clazz.isPrimitive()) {
                JfrTraceId.assign(clazz, metadata.classToIndex);
            }
        }
        long traceId = 0;
        for (Package pkg : metadata.packages) {
            Target_java_lang_Package.setJfrID(pkg, idx);
            JfrTraceId.assign(pkg, idx++, traceId++);
        }
        traceId = 0;
        for (Module module : metadata.modules) {
            Target_java_lang_Module.setJfrID(module, idx);
            JfrTraceId.assign(module, idx++, traceId++);
        }
        traceId = 0;
        for (ClassLoader cl : metadata.classLoaders) {
            Target_java_lang_ClassLoader.setJfrID(cl, idx);
            JfrTraceId.assign(cl, idx++, traceId++);
        }

        // TODO: get the method count
        int methodCount = 0;
        SubstrateJVM.getMethodRepository().initialize(methodCount);
    }
}
