/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.graal.hotspot.nativelib;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.MapCursor;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.DebugHandlersFactory;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.hotspot.HotSpotCodeCacheListener;
import org.graalvm.compiler.hotspot.HotSpotGraalCompiler;
import org.graalvm.compiler.hotspot.HotSpotReplacementsImpl;
import org.graalvm.compiler.hotspot.meta.HotSpotProviders;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.graphbuilderconf.MethodSubstitutionPlugin;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.serviceprovider.GraalServices;
import org.graalvm.compiler.truffle.common.TruffleCompilerRuntime;
import org.graalvm.compiler.truffle.compiler.hotspot.TruffleCallBoundaryInstrumentationFactory;
import org.graalvm.compiler.truffle.compiler.substitutions.TruffleInvocationPluginProvider;
import org.graalvm.nativeimage.Feature;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.RecomputeFieldValue.Kind;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.core.graal.snippets.NodeLoweringProvider;
import com.oracle.svm.core.option.RuntimeOptionValues;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.graal.hosted.GraalFeature;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.FeatureImpl.DuringAnalysisAccessImpl;
import com.oracle.svm.jni.hosted.JNIFeature;

import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public final class HotSpotGraalLibraryFeature implements com.oracle.svm.core.graal.GraalFeature {

    private HotSpotReplacementsImpl hotSpotSubstrateReplacements;

    @Override
    public void afterImageWrite(AfterImageWriteAccess access) {
    }

    @Override
    public List<Class<? extends Feature>> getRequiredFeatures() {
        return Arrays.asList(JNIFeature.class, GraalFeature.class);
    }

    public static final class IsEnabled implements BooleanSupplier {
        @Override
        public boolean getAsBoolean() {
            return ImageSingletons.contains(HotSpotGraalLibraryFeature.class);
        }
    }

    private EconomicSet<AnnotatedElement> visitedElements = EconomicSet.create();

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(MethodAnnotationSupport.class, new MethodAnnotationSupport());
    }

    @Override
    public void registerLowerings(RuntimeConfiguration runtimeConfig, OptionValues options, Iterable<DebugHandlersFactory> factories, Providers substrateProviders,
                    SnippetReflectionProvider substrateSnippetReflection,
                    Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings, boolean hosted) {
        hotSpotSubstrateReplacements = getReplacements();
    }

    @SuppressWarnings("try")
    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {

        // Services that will not be loaded if native-image is run
        // with -XX:-UseJVMCICompiler.
        GraalServices.load(TruffleCallBoundaryInstrumentationFactory.class);
        GraalServices.load(TruffleInvocationPluginProvider.class);
        GraalServices.load(HotSpotCodeCacheListener.class);

        FeatureImpl.BeforeAnalysisAccessImpl impl = (FeatureImpl.BeforeAnalysisAccessImpl) access;
        DebugContext debug = impl.getBigBang().getDebug();
        try (DebugContext.Scope scope = debug.scope("SnippetSupportEncode")) {

            MapCursor<String, List<InvocationPlugins.Binding>> cursor = hotSpotSubstrateReplacements.getGraphBuilderPlugins().getInvocationPlugins().getBindings(true).getEntries();
            Providers providers = hotSpotSubstrateReplacements.getProviders();
            MetaAccessProvider metaAccess = providers.getMetaAccess();
            while (cursor.advance()) {
                String className = cursor.getKey();
                ResolvedJavaType type = null;
                try {
                    String typeName = className.substring(1, className.length() - 1).replace('/', '.');
                    ClassLoader cl = ClassLoader.getSystemClassLoader();
                    Class<?> clazz = Class.forName(typeName, true, cl);
                    type = metaAccess.lookupJavaType(clazz);
                } catch (ClassNotFoundException e) {
                    debug.log("Can't find original type for %s%n", className);
                    // throw new GraalError(e);
                }

                for (InvocationPlugins.Binding binding : cursor.getValue()) {
                    if (binding.plugin instanceof MethodSubstitutionPlugin) {
                        MethodSubstitutionPlugin plugin = (MethodSubstitutionPlugin) binding.plugin;
                        ResolvedJavaMethod method = plugin.getSubstitute(metaAccess);

                        ResolvedJavaMethod original = null;
                        for (ResolvedJavaMethod declared : type.getDeclaredMethods()) {
                            if (declared.getName().equals(binding.name)) {
                                if (declared.isStatic() == binding.isStatic) {
                                    if (declared.getSignature().toMethodDescriptor().startsWith(binding.argumentsDescriptor)) {
                                        original = declared;
                                        break;
                                    }
                                }
                            }
                        }
                        if (original != null) {
                            debug.log("Method substitution %s %s", method, original);
                            hotSpotSubstrateReplacements.registerMethodSubstitution(method, original);
                        } else {
                            throw new GraalError("Can't find original for " + method);
                        }
                    }
                }
            }
        } catch (Throwable t) {
            throw debug.handle(t);
        }
    }

    @SuppressWarnings("try")
    @Override
    public void duringAnalysis(DuringAnalysisAccess access) {
        DuringAnalysisAccessImpl accessImpl = (DuringAnalysisAccessImpl) access;
        AnalysisUniverse universe = accessImpl.getUniverse();

        int numTypes = universe.getTypes().size();
        int numMethods = universe.getMethods().size();
        int numFields = universe.getFields().size();

        /*
         * JDK implementation of repeatable annotations always instantiates an array of a requested
         * annotation. We need to mark arrays of all reachable annotations as in heap.
         */
        universe.getTypes().stream()
                        .filter(AnalysisType::isAnnotation)
                        .filter(AnalysisType::isInTypeCheck)
                        .map(type -> universe.lookup(type.getWrapped()).getArrayClass())
                        .filter(annotationArray -> !annotationArray.isInstantiated())
                        .forEach(annotationArray -> {
                            accessImpl.registerAsInHeap(annotationArray);
                            access.requireAnalysisIteration();
                        });

        // Capture annotations for all Snippets
        for (ResolvedJavaMethod method : hotSpotSubstrateReplacements.getSnippetMethods()) {
            if (visitedElements.add(method)) {
                ImageSingletons.lookup(MethodAnnotationSupport.class).setParameterAnnotations(method, method.getParameterAnnotations());
                ImageSingletons.lookup(MethodAnnotationSupport.class).setMethodAnnotation(method, method.getAnnotations());
            }
        }

        // Rerun the iteration is new things have been seen.
        if (numTypes != universe.getTypes().size() || numMethods != universe.getMethods().size() || numFields != universe.getFields().size()) {
            access.requireAnalysisIteration();
        }

        // Ensure all known snippets and method subsitutions are encoded and rerun the iteration if
        // new encoding is done.
        if (hotSpotSubstrateReplacements.encode()) {
            access.requireAnalysisIteration();
        }
    }

    @Override
    public void afterAnalysis(AfterAnalysisAccess access) {
        visitedElements.clear();
    }

    static class MethodAnnotationSupport {
        private Map<String, Annotation[]> classAnnotationMap = new HashMap<>();

        private Map<String, Annotation[][]> parameterAnnotationMap = new HashMap<>();
        private Map<String, Annotation[]> methodAnnotationMap = new HashMap<>();

        static final Annotation[][] NO_PARAMETER_ANNOTATIONS = new Annotation[0][];
        static final Annotation[] NO_ANNOTATIONS = new Annotation[0];

        @Platforms(Platform.HOSTED_ONLY.class)
        void setParameterAnnotations(ResolvedJavaMethod element, Annotation[][] parameterAnnotations) {
            String name = element.getDeclaringClass().getName() + " " + element.getName();
            if (parameterAnnotations.length == 0) {
                parameterAnnotationMap.put(name, NO_PARAMETER_ANNOTATIONS);
            } else {
                parameterAnnotationMap.put(name, parameterAnnotations);
            }
        }

        @Platforms(Platform.HOSTED_ONLY.class)
        void setMethodAnnotation(ResolvedJavaMethod javaMethod, Annotation[] annotations) {
            String name = javaMethod.format("%R %H.%n%P");
            if (annotations.length == 0) {
                methodAnnotationMap.put(name, NO_ANNOTATIONS);
            } else {
                methodAnnotationMap.put(name, annotations);
            }
        }

        public Annotation[] getClassAnnotations(String className) {
            return classAnnotationMap.get(className);
        }

        public Annotation[][] getParameterAnnotations(String className, String methodName) {
            String name = className + " " + methodName;
            return parameterAnnotationMap.get(name);
        }

        public Annotation[] getMethodAnnotations(ResolvedJavaMethod javaMethod) {
            String name = javaMethod.format("%R %H.%n%P");
            return methodAnnotationMap.get(name);

        }
    }

    static HotSpotReplacementsImpl getReplacements() {
        HotSpotGraalCompiler compiler = (HotSpotGraalCompiler) HotSpotJVMCIRuntime.runtime().getCompiler();
        HotSpotProviders originalProvider = compiler.getGraalRuntime().getHostProviders();
        return (HotSpotReplacementsImpl) originalProvider.getReplacements();
    }

}

@TargetClass(className = "jdk.vm.ci.hotspot.SharedLibraryJVMCIReflection", onlyWith = HotSpotGraalLibraryFeature.IsEnabled.class)
final class Target_jdk_vm_ci_hotspot_SharedLibraryJVMCIReflection {

    @Substitute
    static Annotation[] getClassAnnotations(String className) {
        return ImageSingletons.lookup(HotSpotGraalLibraryFeature.MethodAnnotationSupport.class).getClassAnnotations(className);
    }

    @Substitute
    static Annotation[][] getParameterAnnotations(String className, String methodName) {
        return ImageSingletons.lookup(HotSpotGraalLibraryFeature.MethodAnnotationSupport.class).getParameterAnnotations(className, methodName);
    }

    @Substitute
    static Annotation[] getMethodAnnotationsInternal(ResolvedJavaMethod javaMethod) {
        return ImageSingletons.lookup(HotSpotGraalLibraryFeature.MethodAnnotationSupport.class).getMethodAnnotations(javaMethod);
    }

    @Substitute
    static Object convertUnknownValue(Object object) {
        return KnownIntrinsics.convertUnknownValue(object, Object.class);
    }
}

@TargetClass(className = "org.graalvm.compiler.hotspot.HotSpotGraalOptionValues", onlyWith = HotSpotGraalLibraryFeature.IsEnabled.class)
final class Target_org_graalvm_compiler_hotspot_HotSpotGraalOptionValues {
    @Substitute
    private static OptionValues initializeOptions() {
        return RuntimeOptionValues.singleton();
    }
}

@TargetClass(className = "org.graalvm.compiler.truffle.common.TruffleCompilerRuntimeInstance", onlyWith = HotSpotGraalLibraryFeature.IsEnabled.class)
final class Target_org_graalvm_compiler_truffle_common_TruffleCompilerRuntimeInstance {
    // Checkstyle: stop
    @Alias @RecomputeFieldValue(kind = Kind.Reset, isFinal = true) static Object TRUFFLE_RUNTIME;
    // Checkstyle: resume
    @Alias @RecomputeFieldValue(kind = Kind.Reset) static TruffleCompilerRuntime truffleCompilerRuntime;
}
