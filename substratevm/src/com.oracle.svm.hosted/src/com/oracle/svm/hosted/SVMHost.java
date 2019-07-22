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

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.core.common.spi.ForeignCallsProvider;
import org.graalvm.compiler.java.GraphBuilderPhase.Instance;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.graphbuilderconf.IntrinsicContext;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.nativeimage.hosted.Feature.DuringAnalysisAccess;
import org.graalvm.nativeimage.c.function.RelocatedPointer;

import com.oracle.graal.pointsto.api.HostVM;
import com.oracle.graal.pointsto.api.PointstoOptions;
import com.oracle.graal.pointsto.constraints.UnsupportedFeatureException;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.UnknownClass;
import com.oracle.svm.core.annotate.UnknownObjectField;
import com.oracle.svm.core.annotate.UnknownPrimitiveField;
import com.oracle.svm.core.graal.meta.SubstrateForeignCallLinkage;
import com.oracle.svm.core.graal.meta.SubstrateForeignCallsProvider;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.jdk.JavaLangSubstitutions.ClassLoaderSupport;
import com.oracle.svm.core.jdk.Target_java_lang_ClassLoader;
import com.oracle.svm.core.util.HostedStringDeduplication;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.c.GraalAccess;
import com.oracle.svm.hosted.classinitialization.ClassInitializationSupport;
import com.oracle.svm.hosted.meta.HostedType;
import com.oracle.svm.hosted.phases.AnalysisGraphBuilderPhase;
import com.oracle.svm.hosted.substitute.UnsafeAutomaticSubstitutionProcessor;

import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

public final class SVMHost implements HostVM {
    private final ConcurrentHashMap<AnalysisType, DynamicHub> typeToHub = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<DynamicHub, AnalysisType> hubToType = new ConcurrentHashMap<>();

    private final Map<String, EnumSet<AnalysisType.UsageKind>> forbiddenTypes;

    private final OptionValues options;
    private final ClassLoader classLoader;
    private final ClassInitializationSupport classInitializationSupport;
    private final HostedStringDeduplication stringTable;
    private final UnsafeAutomaticSubstitutionProcessor automaticSubstitutions;
    private final List<BiConsumer<DuringAnalysisAccess, Class<?>>> classReachabilityListeners;

    public SVMHost(OptionValues options, ClassLoader classLoader, ClassInitializationSupport classInitializationSupport, UnsafeAutomaticSubstitutionProcessor automaticSubstitutions) {
        this.options = options;
        this.classLoader = classLoader;
        this.classInitializationSupport = classInitializationSupport;
        this.stringTable = HostedStringDeduplication.singleton();
        this.classReachabilityListeners = new ArrayList<>();
        this.forbiddenTypes = setupForbiddenTypes(options);
        this.automaticSubstitutions = automaticSubstitutions;
    }

    private static Map<String, EnumSet<AnalysisType.UsageKind>> setupForbiddenTypes(OptionValues options) {
        String[] forbiddenTypesOptionValues = SubstrateOptions.ReportAnalysisForbiddenType.getValue(options);
        Map<String, EnumSet<AnalysisType.UsageKind>> forbiddenTypes = new HashMap<>();
        for (String forbiddenTypesOptionValue : forbiddenTypesOptionValues) {
            String[] typeNameUsageKind = forbiddenTypesOptionValue.split(":", 2);
            EnumSet<AnalysisType.UsageKind> usageKinds;
            if (typeNameUsageKind.length == 1) {
                usageKinds = EnumSet.allOf(AnalysisType.UsageKind.class);
            } else {
                usageKinds = EnumSet.noneOf(AnalysisType.UsageKind.class);
                String[] usageKindValues = typeNameUsageKind[1].split(",");
                for (String usageKindValue : usageKindValues) {
                    usageKinds.add(AnalysisType.UsageKind.valueOf(usageKindValue));
                }

            }
            forbiddenTypes.put(typeNameUsageKind[0], usageKinds);
        }
        return forbiddenTypes.isEmpty() ? null : forbiddenTypes;
    }

    @Override
    public void checkForbidden(AnalysisType type, AnalysisType.UsageKind kind) {
        if (forbiddenTypes == null) {
            return;
        }

        /*
         * We check the class hierarchy, because putting a restriction on a superclass should cover
         * all subclasses too.
         *
         * We do not check the interface hierarchy for now, although it would be possible. But it
         * seems less likely that someone registers an interface as forbidden.
         */
        for (AnalysisType cur = type; cur != null; cur = cur.getSuperclass()) {
            EnumSet<AnalysisType.UsageKind> forbiddenType = forbiddenTypes.get(cur.getWrapped().toJavaName());
            if (forbiddenType != null && forbiddenType.contains(kind)) {
                throw new UnsupportedFeatureException("Forbidden type " + cur.getWrapped().toJavaName() +
                                (cur.equals(type) ? "" : " (superclass of " + type.getWrapped().toJavaName() + ")") +
                                " UsageKind: " + kind);
            }
        }
    }

    @Override
    public OptionValues options() {
        return options;
    }

    @Override
    public Instance createGraphBuilderPhase(HostedProviders providers, GraphBuilderConfiguration graphBuilderConfig, OptimisticOptimizations optimisticOpts, IntrinsicContext initialIntrinsicContext) {
        return new AnalysisGraphBuilderPhase(providers, graphBuilderConfig, optimisticOpts, initialIntrinsicContext, providers.getWordTypes());
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
        return SubstrateOptions.Name.getValue(options);
    }

    @Override
    public boolean isRelocatedPointer(Object originalObject) {
        return originalObject instanceof RelocatedPointer;
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
        return ImageSingletonsSupportImpl.HostedManagement.getAndAssertExists();
    }

    @Override
    public void registerType(AnalysisType analysisType) {
        classInitializationSupport.maybeInitializeHosted(analysisType);

        DynamicHub hub = createHub(analysisType);
        Object existing = typeToHub.put(analysisType, hub);
        assert existing == null;
        existing = hubToType.put(hub, analysisType);
        assert existing == null;

        /* Compute the automatic substitutions. */
        automaticSubstitutions.computeSubstitutions(GraalAccess.getOriginalProviders().getMetaAccess().lookupJavaType(analysisType.getJavaClass()), options);
    }

    @Override
    public boolean isInitialized(AnalysisType type) {
        boolean shouldInitializeAtRuntime = classInitializationSupport.shouldInitializeAtRuntime(type);
        assert shouldInitializeAtRuntime || type.getWrapped().isInitialized() : "Types that are not marked for runtime initializations must have been initialized: " + type;

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
            throw VMError.shouldNotReachHere("Found unsupported type: " + type);
        }
        return typeToHub.get(aType);
    }

    public AnalysisType lookupType(DynamicHub hub) {
        assert hub != null : "Hub must not be null";
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
        int modifiers = javaClass.getModifiers();

        Target_java_lang_ClassLoader hubClassLoader = ClassLoaderSupport.getInstance().getOrCreate(javaClass.getClassLoader());

        /* Class names must be interned strings according to the Java specification. */
        String className = type.toClassName().intern();
        /*
         * There is no need to have file names as interned strings. So we perform our own
         * de-duplication.
         */
        String sourceFileName = stringTable.deduplicate(type.getSourceFileName(), true);

        return new DynamicHub(className, type.isLocal(), superHub, componentHub, sourceFileName, modifiers, hubClassLoader);
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

    public void registerClassReachabilityListener(BiConsumer<DuringAnalysisAccess, Class<?>> listener) {
        classReachabilityListeners.add(listener);
    }

    void notifyClassReachabilityListener(AnalysisUniverse universe, DuringAnalysisAccess access) {
        for (AnalysisType type : universe.getTypes()) {
            if ((type.isInTypeCheck() || type.isInstantiated()) && !type.getReachabilityListenerNotified()) {
                type.setReachabilityListenerNotified(true);

                for (BiConsumer<DuringAnalysisAccess, Class<?>> listener : classReachabilityListeners) {
                    listener.accept(access, type.getJavaClass());
                }
            }
        }
    }

    public ClassInitializationSupport getClassInitializationSupport() {
        return classInitializationSupport;
    }

    public UnsafeAutomaticSubstitutionProcessor getAutomaticSubstitutionProcessor() {
        return automaticSubstitutions;
    }
}
