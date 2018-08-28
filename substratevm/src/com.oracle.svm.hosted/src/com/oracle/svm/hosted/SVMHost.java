/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Modifier;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.core.common.spi.ForeignCallsProvider;
import org.graalvm.compiler.java.GraphBuilderPhase.Instance;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.graphbuilderconf.IntrinsicContext;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.function.RelocatedPointer;

import com.oracle.graal.pointsto.AnalysisPolicy;
import com.oracle.graal.pointsto.api.HostVM;
import com.oracle.graal.pointsto.api.PointstoOptions;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.svm.core.annotate.UnknownClass;
import com.oracle.svm.core.annotate.UnknownObjectField;
import com.oracle.svm.core.annotate.UnknownPrimitiveField;
import com.oracle.svm.core.graal.meta.SubstrateForeignCallLinkage;
import com.oracle.svm.core.graal.meta.SubstrateForeignCallsProvider;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.jdk.JavaLangSubstitutions.ClassLoaderSupport;
import com.oracle.svm.core.jdk.Target_java_lang_ClassLoader;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.meta.HostedType;
import com.oracle.svm.hosted.phases.AnalysisGraphBuilderPhase;
import com.oracle.svm.hosted.substitute.UnsafeAutomaticSubstitutionProcessor;

import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public final class SVMHost implements HostVM {
    private final ConcurrentHashMap<AnalysisType, DynamicHub> typeToHub = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<DynamicHub, AnalysisType> hubToType = new ConcurrentHashMap<>();

    private final OptionValues options;
    private final Platform platform;
    private final AnalysisPolicy analysisPolicy;
    private final ClassLoader classLoader;

    public SVMHost(OptionValues options, Platform platform, AnalysisPolicy analysisPolicy, ClassLoader classLoader) {
        this.options = options;
        this.platform = platform;
        this.analysisPolicy = analysisPolicy;
        this.classLoader = classLoader;
    }

    @Override
    public OptionValues options() {
        return options;
    }

    @Override
    public AnalysisPolicy analysisPolicy() {
        return analysisPolicy;
    }

    @Override
    public Instance createGraphBuilderPhase(HostedProviders providers, GraphBuilderConfiguration graphBuilderConfig, OptimisticOptimizations optimisticOpts, IntrinsicContext initialIntrinsicContext) {
        return new AnalysisGraphBuilderPhase(providers.getMetaAccess(), providers.getStampProvider(), providers.getConstantReflection(), providers.getConstantFieldProvider(), graphBuilderConfig,
                        optimisticOpts, initialIntrinsicContext, providers.getWordTypes());
    }

    @Override
    public String inspectServerContentPath() {
        return PointstoOptions.InspectServerContentPath.getValue(options);
    }

    @Override
    public void warn(String message) {
        System.err.println("warning: " + message);
    }

    @Override
    public String getImageName() {
        return NativeImageOptions.Name.getValue(options);
    }

    @Override
    public boolean isRelocatedPointer(Object originalObject) {
        return originalObject instanceof RelocatedPointer;
    }

    @Override
    public boolean isCFunction(AnalysisMethod result) {
        return result.getAnnotation(CFunction.class) != null;
    }

    @Override
    public void clearInThread() {
        Thread.currentThread().setContextClassLoader(SVMHost.class.getClassLoader());
        ImageSingletonsSupportImpl.HostedManagement.clearInThread();
    }

    @Override
    public void installInThread(Object vmConfig) {
        Thread.currentThread().setContextClassLoader(classLoader);
        ImageSingletonsSupportImpl.HostedManagement.installInThread((ImageSingletonsSupportImpl.HostedManagement) vmConfig);
    }

    @Override
    public Object getConfiguration() {
        return ImageSingletonsSupportImpl.HostedManagement.get();
    }

    @Override
    public boolean platformSupported(ResolvedJavaField field) {
        return NativeImageGenerator.includedIn(platform, field.getAnnotation(Platforms.class));
    }

    @Override
    public boolean platformSupported(ResolvedJavaMethod method) {
        return NativeImageGenerator.includedIn(platform, method.getAnnotation(Platforms.class));
    }

    @Override
    public boolean platformSupported(ResolvedJavaType type) {
        return NativeImageGenerator.includedIn(platform, type.getAnnotation(Platforms.class));
    }

    @Override
    public void registerType(AnalysisType analysisType, ResolvedJavaType hostType) {
        ClassInitializationFeature.maybeInitializeHosted(analysisType);

        DynamicHub hub = createHub(analysisType);
        Object existing = typeToHub.put(analysisType, hub);
        assert existing == null;
        existing = hubToType.put(hub, analysisType);
        assert existing == null;

        /* Compute the automatic substitutions. */
        UnsafeAutomaticSubstitutionProcessor automaticSubstitutions = ImageSingletons.lookup(UnsafeAutomaticSubstitutionProcessor.class);
        automaticSubstitutions.computeSubstitutions(hostType, options);
    }

    @Override
    public boolean isInitialized(AnalysisType type) {
        boolean shouldInitializeAtRuntime = ClassInitializationFeature.shouldInitializeAtRuntime(type);
        assert shouldInitializeAtRuntime || type.getWrapped().isInitialized() : "Types that are not marked for runtime initializations must have been initialized";

        return !shouldInitializeAtRuntime;
    }

    @Override
    public Optional<AnalysisMethod> handleForeignCall(ForeignCallDescriptor foreignCallDescriptor, ForeignCallsProvider foreignCallsProvider) {
        SubstrateForeignCallsProvider foreignCalls = (SubstrateForeignCallsProvider) foreignCallsProvider;
        /* In unit tests, we run with no registered foreign calls. */
        Optional<AnalysisMethod> targetMethod = Optional.empty();
        if (foreignCalls.getForeignCalls().size() > 0) {
            SubstrateForeignCallLinkage linkage = foreignCalls.lookupForeignCall(foreignCallDescriptor);
            targetMethod = Optional.of((AnalysisMethod) linkage.getMethod());
        }
        return targetMethod;
    }

    public DynamicHub dynamicHub(ResolvedJavaType type) {
        AnalysisType aType;
        if (type instanceof AnalysisType) {
            aType = (AnalysisType) type;
        } else if (type instanceof HostedType) {
            aType = ((HostedType) type).getWrapped();
        } else {
            throw VMError.shouldNotReachHere();
        }
        return typeToHub.get(aType);
    }

    public AnalysisType lookupType(DynamicHub hub) {
        return hubToType.get(hub);
    }

    private DynamicHub createHub(AnalysisType type) {
        DynamicHub superHub = null;
        if ((type.isInstanceClass() && type.getSuperclass() != null) || type.isArray()) {
            superHub = dynamicHub(type.getSuperclass());
        }
        DynamicHub componentHub = null;
        if (type.isArray()) {
            componentHub = dynamicHub(type.getComponentType());
        }
        Class<?> javaClass = type.getJavaClass();
        boolean isStatic = Modifier.isStatic(javaClass.getModifiers());
        boolean isSynthetic = javaClass.isSynthetic();

        Target_java_lang_ClassLoader hubClassLoader = ClassLoaderSupport.getInstance().getOrCreate(javaClass.getClassLoader());
        return new DynamicHub(type.toClassName(), type.isLocal(), superHub, componentHub, type.getSourceFileName(), isStatic, isSynthetic, hubClassLoader);
    }

    public static boolean isUnknownClass(ResolvedJavaType resolvedJavaType) {
        return resolvedJavaType.getAnnotation(UnknownClass.class) != null;
    }

    public static boolean isUnknownObjectField(ResolvedJavaField resolvedJavaField) {
        return resolvedJavaField.getAnnotation(UnknownObjectField.class) != null;
    }

    public static boolean isUnknownPrimitiveField(AnalysisField field) {
        return field.getAnnotation(UnknownPrimitiveField.class) != null;
    }
}
