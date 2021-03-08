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

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.jdk.Target_java_lang_ClassLoader;
import com.oracle.svm.core.jdk.Target_java_lang_Module;
import com.oracle.svm.core.jdk.Target_java_lang_Package;
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
    private JfrRuntimeAccess runtimeAccess;
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ModuleSupport.exportAndOpenAllPackagesToUnnamed("jdk.jfr", false);
        ModuleSupport.exportAndOpenAllPackagesToUnnamed("java.base", false);

        JVM.getJVM().createNativeJFR();
        SubstrateTypeLibrary.installSubstrateTypeLibrary();

        ImageSingletons.add(SubstrateJVM.class, new SubstrateJVM());
        ImageSingletons.add(JfrManager.class, new JfrManager());
        ImageSingletons.add(JfrSerializerSupport.class, new JfrSerializerSupport());
        runtimeAccess = new JfrRuntimeAccessImpl();
        ImageSingletons.add(JfrRuntimeAccess.class, runtimeAccess);

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

    private Set<Class<?>> analysisClasses = new HashSet<>();
    public void afterAnalysis(Feature.AfterAnalysisAccess access) {
        Collection<Class<?>> types = access.reachableTypes();
        int numClasses = 0;
        int numPkgs = 0;
        int numMods = 0;
        int numCls = 0;
        Set<Package> pkgs = new HashSet<>();
        Set<Module> mods = new HashSet<>();
        Set<ClassLoader> cls = new HashSet<>();
        for (Class<?> clazz : types) {
            numClasses++;
            analysisClasses.add(clazz);
            if (clazz.getPackage() != null && !pkgs.contains(clazz.getPackage())) {
                pkgs.add(clazz.getPackage());
                numPkgs++;
            }
            if (clazz.getModule() != null && !mods.contains(clazz.getModule())) {
                mods.add(clazz.getModule());
                numMods++;
            }
            if (clazz.getClassLoader() != null && !cls.contains(clazz.getClassLoader())) {
                cls.add(clazz.getClassLoader());
                numCls++;
            }
        }
        // java.lang.invoke.LambdaForm.BasicType seems not be found by analysis, but is present before compilation
        numClasses++;

        // TODO: Normally I would expect that the number of reachable classes here matches the number of
        // compiled classes in beforeCompilation() (modulo the BasicType above, which also seems strange).
        // However, beforeCompilation() seems to see all classes.
        // See: AfterAnalysisAccessImpl.reachableTypes() and CompilationAccessImpl.compiledTypes().
        // Maybe I'm doing something wrong.
        System.out.println("afterAnalysis: numClasses: " + numClasses + ", numPkgs: " + numPkgs + ", numMods: " + numMods + ", numCls: " + numCls);
        JfrTraceIdMap map = new JfrTraceIdMap(numClasses + numPkgs + numMods + numCls);
        runtimeAccess.setTraceIdMap(map);
    }

    Set<Package> packages = new HashSet<>();
    Set<Module> modules = new HashSet<>();
    Set<ClassLoader> classLoaders = new HashSet<>();

    private Map<Class<?>, Integer> classToIndex = new HashMap<>();
    private void assignClass(Class<?> clazz, int id) {
        if (!analysisClasses.contains(clazz)) {
            System.out.println("newly found class: " + clazz.getCanonicalName());
        }
        classToIndex.put(clazz, id);
        Package pkg = clazz.getPackage();
        if (pkg != null && !packages.contains(pkg)) {
            packages.add(pkg);
        }
        Module module = clazz.getModule();
        if (module != null && !modules.contains(module)) {
            modules.add(module);
        }
        ClassLoader cl = clazz.getClassLoader();
        if (cl != null && !classLoaders.contains(cl)) {
            classLoaders.add(cl);
        }
    }

    @Override
    public void beforeCompilation(BeforeCompilationAccess a) {

        a.compiledTypes(new CompiledTypesVisitor() {
            @Override
            public void visitCompiledType(Class<?> clazz, int typeID) {
                assignClass(clazz, typeID);
            }
        });
        // See comment in afterAnalysis()
        System.out.println("beforeCompilation: numClasses: " + classToIndex.size() + ", numPkgs: " + packages.size() + ", numMods: " + modules.size() + ", numCls: " + classLoaders.size());
        int idx = classToIndex.size();
        for (Class<?> clazz : classToIndex.keySet()) {
            if (!clazz.isPrimitive()) {
                JfrTraceId.assign(clazz, classToIndex);
            }
            if (classToIndex.get(clazz) >= idx) {
                throw new ArrayIndexOutOfBoundsException();
            }
        }
        for (Package pkg : packages) {
            JfrTraceId.assign(pkg, idx++);
        }
        for (Module module : modules) {
            JfrTraceId.assign(module, idx++);
        }
        for (ClassLoader cl : classLoaders) {
            JfrTraceId.assign(cl, idx++);
        }

        // TODO: get the method count
        int methodCount = 0;
        SubstrateJVM.getMethodRepository().initialize(methodCount);
    }
}
