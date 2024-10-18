/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.heap;

import static com.oracle.svm.hosted.methodhandles.InjectedInvokerRenamingSubstitutionProcessor.isInjectedInvokerType;
import static com.oracle.svm.hosted.methodhandles.MethodHandleInvokerRenamingSubstitutionProcessor.isMethodHandleType;
import static com.oracle.svm.hosted.reflect.proxy.ProxyRenamingSubstitutionProcessor.isProxyType;
import static jdk.graal.compiler.java.LambdaUtils.isLambdaType;

import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.oracle.graal.pointsto.heap.ImageLayerLoader;
import com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil;
import com.oracle.graal.pointsto.heap.ImageLayerWriter;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.svm.core.SubstrateDiagnostics;
import com.oracle.svm.core.c.struct.CInterfaceLocationIdentity;
import com.oracle.svm.core.deopt.DeoptimizationCounters;
import com.oracle.svm.core.graal.snippets.DeoptTester;
import com.oracle.svm.core.graal.snippets.StackOverflowCheckImpl;
import com.oracle.svm.core.graal.snippets.SubstrateAllocationSnippets;
import com.oracle.svm.core.heap.NoAllocationVerifier;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.identityhashcode.IdentityHashCodeSupport;
import com.oracle.svm.core.jfr.JfrThreadLocal;
import com.oracle.svm.core.jfr.events.JfrAllocationEvents;
import com.oracle.svm.core.jfr.events.ThreadCPULoadEvent;
import com.oracle.svm.core.jfr.sampler.AbstractJfrExecutionSampler;
import com.oracle.svm.core.jni.JNIObjectHandles;
import com.oracle.svm.core.jni.JNIThreadLocalEnvironment;
import com.oracle.svm.core.jni.JNIThreadLocalPendingException;
import com.oracle.svm.core.jni.JNIThreadLocalPrimitiveArrayViews;
import com.oracle.svm.core.jni.JNIThreadOwnedMonitors;
import com.oracle.svm.core.option.HostedOptionValues;
import com.oracle.svm.core.reflect.serialize.SerializationSupport;
import com.oracle.svm.core.snippets.ExceptionUnwind;
import com.oracle.svm.core.snippets.ImplicitExceptions;
import com.oracle.svm.core.stack.JavaFrameAnchors;
import com.oracle.svm.core.thread.JavaThreads;
import com.oracle.svm.core.thread.PlatformThreads;
import com.oracle.svm.core.thread.Safepoint;
import com.oracle.svm.core.thread.ThreadingSupportImpl;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.core.threadlocal.FastThreadLocal;
import com.oracle.svm.hosted.code.FactoryMethod;
import com.oracle.svm.hosted.code.IncompatibleClassChangeFallbackMethod;
import com.oracle.svm.hosted.meta.HostedArrayClass;
import com.oracle.svm.hosted.meta.HostedInstanceClass;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.HostedSnippetReflectionProvider;
import com.oracle.svm.hosted.meta.HostedType;
import com.oracle.svm.util.ReflectionUtil;

import jdk.graal.compiler.api.replacements.SnippetReflectionProvider;
import jdk.graal.compiler.nodes.EncodedGraph;
import jdk.graal.compiler.util.ObjectCopier;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;

public class SVMImageLayerSnapshotUtil extends ImageLayerSnapshotUtil {
    public static final String GENERATED_SERIALIZATION = "jdk.internal.reflect.GeneratedSerializationConstructorAccessor";

    public static final Field companion = ReflectionUtil.lookupField(DynamicHub.class, "companion");
    public static final Field classInitializationInfo = ReflectionUtil.lookupField(DynamicHub.class, "classInitializationInfo");
    private static final Field name = ReflectionUtil.lookupField(DynamicHub.class, "name");
    private static final Field superHub = ReflectionUtil.lookupField(DynamicHub.class, "superHub");
    private static final Field componentType = ReflectionUtil.lookupField(DynamicHub.class, "componentType");
    public static final Field arrayHub = ReflectionUtil.lookupField(DynamicHub.class, "arrayHub");
    public static final Field interfacesEncoding = ReflectionUtil.lookupField(DynamicHub.class, "interfacesEncoding");
    public static final Field enumConstantsReference = ReflectionUtil.lookupField(DynamicHub.class, "enumConstantsReference");

    protected static final Set<Field> dynamicHubRelinkedFields = Set.of(companion, classInitializationInfo, name, superHub, componentType, arrayHub);

    protected final Map<AnalysisType, Set<Integer>> fieldsToRelink = new HashMap<>();

    @SuppressWarnings("this-escape")
    public SVMImageLayerSnapshotUtil() {
        super();
        addExternalValues(DeoptTester.class);
        addExternalValues(JfrThreadLocal.class);
        addExternalValues(JfrAllocationEvents.class);
        addExternalValues(AbstractJfrExecutionSampler.class);
        addExternalValues(ThreadCPULoadEvent.class);
        addExternalValues(JNIObjectHandles.class);
        addExternalValues(JNIThreadLocalPendingException.class);
        addExternalValues(JNIThreadLocalPrimitiveArrayViews.class);
        addExternalValues(JNIThreadOwnedMonitors.class);
        addExternalValues(JNIThreadLocalEnvironment.class);
        addExternalValues(ExceptionUnwind.class);
        addExternalValues(JavaThreads.class);
        addExternalValues(ImplicitExceptions.class);
        addExternalValues(IdentityHashCodeSupport.class);
        addExternalValues(StackOverflowCheckImpl.class);
        addExternalValues(CInterfaceLocationIdentity.class);
        addExternalValues(JavaFrameAnchors.class);
        addExternalValues(PlatformThreads.class);
        addExternalValues(VMThreads.class);
        addExternalValues(VMThreads.StatusSupport.class);
        addExternalValues(VMThreads.SafepointBehavior.class);
        addExternalValues(VMThreads.ActionOnTransitionToJavaSupport.class);
        addExternalValues(Safepoint.class);
        addExternalValues(NoAllocationVerifier.class);
        addExternalValues(ThreadingSupportImpl.class);
        addExternalValues(ThreadingSupportImpl.RecurringCallbackTimer.class);
        addExternalValues(DeoptimizationCounters.class);
        addExternalValues(Objects.requireNonNull(ReflectionUtil.lookupClass(false, "com.oracle.svm.core.genscavenge.ThreadLocalAllocation")));
        addExternalValues(Objects.requireNonNull(ReflectionUtil.lookupClass(false, "com.oracle.svm.core.genscavenge.graal.BarrierSnippets")));
        addExternalValues(SubstrateDiagnostics.class);
        addExternalValues(SubstrateAllocationSnippets.class);
    }

    @Override
    protected boolean shouldAddExternalValue(Class<?> type) {
        return FastThreadLocal.class.isAssignableFrom(type) || super.shouldAddExternalValue(type);
    }

    @Override
    public String getTypeIdentifier(AnalysisType type) {
        if (type.toJavaName(true).contains(GENERATED_SERIALIZATION)) {
            return getGeneratedSerializationName(type);
        }
        if (isProxyType(type)) {
            return type.toJavaName(true);
        }
        return super.getTypeIdentifier(type);
    }

    private static String generatedSerializationClassName(SerializationSupport.SerializationLookupKey serializationLookupKey) {
        return GENERATED_SERIALIZATION + ":" + serializationLookupKey.getDeclaringClass() + "," + serializationLookupKey.getTargetConstructorClass();
    }

    @Override
    public String getMethodIdentifier(AnalysisMethod method) {
        AnalysisType declaringClass = method.getDeclaringClass();
        String moduleName = declaringClass.getJavaClass().getModule().getName();
        if (declaringClass.toJavaName(true).contains(GENERATED_SERIALIZATION)) {
            return getGeneratedSerializationName(declaringClass) + ":" + method.getName();
        }
        if (method.wrapped instanceof FactoryMethod factoryMethod) {
            AnalysisMethod targetConstructor = method.getUniverse().lookup(factoryMethod.getTargetConstructor());
            return addModuleName(targetConstructor.getDeclaringClass().toJavaName(true) + getQualifiedName(method), moduleName);
        }
        if (method.wrapped instanceof IncompatibleClassChangeFallbackMethod) {
            Executable originalMethod = method.getJavaMethod();
            if (originalMethod != null) {
                return addModuleName(method.getQualifiedName() + " " + method.getJavaMethod().toString(), moduleName);
            }
        }
        if (!(method.wrapped instanceof HotSpotResolvedJavaMethod)) {
            return addModuleName(getQualifiedName(method), moduleName);
        }
        /*
         * Those methods cannot use the name of the wrapped method as it would not use the name of
         * the SubstitutionType
         */
        if (isLambdaType(declaringClass) || isInjectedInvokerType(declaringClass) || isMethodHandleType(declaringClass) || isProxyType(declaringClass)) {
            return getQualifiedName(method);
        }
        return super.getMethodIdentifier(method);
    }

    /*
     * The GeneratedSerializationConstructorAccessor names created in SerializationSupport are not
     * stable in a multi threading context. To ensure the correct one is matched in the extension
     * image, the constructor accessors table from SerializationSupport is accessed.
     */
    private static String getGeneratedSerializationName(AnalysisType type) {
        Class<?> constructorAccessor = type.getJavaClass();
        SerializationSupport serializationRegistry = SerializationSupport.singleton();
        SerializationSupport.SerializationLookupKey serializationLookupKey = serializationRegistry.getKeyFromConstructorAccessorClass(constructorAccessor);
        return generatedSerializationClassName(serializationLookupKey);
    }

    @Override
    public Set<Integer> getRelinkedFields(AnalysisType type, AnalysisMetaAccess metaAccess) {
        Set<Integer> result = fieldsToRelink.computeIfAbsent(type, key -> {
            Class<?> clazz = type.getJavaClass();
            if (clazz == Class.class) {
                type.getInstanceFields(true);
                return dynamicHubRelinkedFields.stream().map(metaAccess::lookupJavaField).map(AnalysisField::getPosition).collect(Collectors.toSet());
            } else {
                return null;
            }
        });
        if (result == null) {
            return Set.of();
        }
        return result;
    }

    @Override
    public GraphEncoder getGraphEncoder(ImageLayerWriter imageLayerWriter) {
        return new SVMGraphEncoder(externalValues, imageLayerWriter);
    }

    @Override
    public GraphDecoder getGraphDecoder(ImageLayerLoader imageLayerLoader, AnalysisMethod analysisMethod, SnippetReflectionProvider snippetReflectionProvider) {
        return new SVMGraphDecoder(EncodedGraph.class.getClassLoader(), (SVMImageLayerLoader) imageLayerLoader, analysisMethod, snippetReflectionProvider);
    }

    public static class SVMGraphEncoder extends GraphEncoder {
        @SuppressWarnings("this-escape")
        public SVMGraphEncoder(List<Field> externalValues, ImageLayerWriter imageLayerWriter) {
            super(externalValues, imageLayerWriter);
            addBuiltin(new HostedTypeBuiltIn(null));
            addBuiltin(new HostedMethodBuiltIn(null));
            addBuiltin(new HostedOptionValuesBuiltIn());
            addBuiltin(new HostedSnippetReflectionProviderBuiltIn(null));
            addBuiltin(new CInterfaceLocationIdentityBuiltIn());
            addBuiltin(new FastThreadLocalLocationIdentityBuiltIn());
        }
    }

    public static class SVMGraphDecoder extends GraphDecoder {
        @SuppressWarnings("this-escape")
        public SVMGraphDecoder(ClassLoader classLoader, SVMImageLayerLoader svmImageLayerLoader, AnalysisMethod analysisMethod, SnippetReflectionProvider snippetReflectionProvider) {
            super(classLoader, svmImageLayerLoader, analysisMethod);
            addBuiltin(new HostedTypeBuiltIn(svmImageLayerLoader));
            addBuiltin(new HostedMethodBuiltIn(svmImageLayerLoader));
            addBuiltin(new HostedOptionValuesBuiltIn());
            addBuiltin(new HostedSnippetReflectionProviderBuiltIn(snippetReflectionProvider));
            addBuiltin(new CInterfaceLocationIdentityBuiltIn());
            addBuiltin(new FastThreadLocalLocationIdentityBuiltIn());
        }
    }

    public static class HostedTypeBuiltIn extends ObjectCopier.Builtin {
        private final SVMImageLayerLoader svmImageLayerLoader;

        protected HostedTypeBuiltIn(SVMImageLayerLoader svmImageLayerLoader) {
            super(HostedType.class, HostedInstanceClass.class, HostedArrayClass.class);
            this.svmImageLayerLoader = svmImageLayerLoader;
        }

        @Override
        protected String encode(ObjectCopier.Encoder encoder, Object obj) {
            return String.valueOf(((HostedType) obj).getWrapped().getId());
        }

        @Override
        protected Object decode(ObjectCopier.Decoder decoder, Class<?> concreteType, String encoding, String encoded) {
            AnalysisType type = svmImageLayerLoader.getAnalysisType(Integer.parseInt(encoded));
            return svmImageLayerLoader.getHostedUniverse().lookup(type);
        }
    }

    public static class HostedMethodBuiltIn extends ObjectCopier.Builtin {
        private final SVMImageLayerLoader svmImageLayerLoader;

        protected HostedMethodBuiltIn(SVMImageLayerLoader svmImageLayerLoader) {
            super(HostedMethod.class);
            this.svmImageLayerLoader = svmImageLayerLoader;
        }

        @Override
        protected String encode(ObjectCopier.Encoder encoder, Object obj) {
            return String.valueOf(((HostedMethod) obj).getWrapped().getId());
        }

        @Override
        protected Object decode(ObjectCopier.Decoder decoder, Class<?> concreteType, String encoding, String encoded) {
            AnalysisMethod method = svmImageLayerLoader.getAnalysisMethod(Integer.parseInt(encoded));
            return svmImageLayerLoader.getHostedUniverse().lookup(method);
        }
    }

    public static class HostedOptionValuesBuiltIn extends ObjectCopier.Builtin {
        protected HostedOptionValuesBuiltIn() {
            super(HostedOptionValues.class);
        }

        @Override
        protected String encode(ObjectCopier.Encoder encoder, Object obj) {
            return "";
        }

        @Override
        protected Object decode(ObjectCopier.Decoder decoder, Class<?> concreteType, String encoding, String encoded) {
            return HostedOptionValues.singleton();
        }
    }

    public static class HostedSnippetReflectionProviderBuiltIn extends ObjectCopier.Builtin {
        private final SnippetReflectionProvider snippetReflectionProvider;

        protected HostedSnippetReflectionProviderBuiltIn(SnippetReflectionProvider snippetReflectionProvider) {
            super(SnippetReflectionProvider.class, HostedSnippetReflectionProvider.class);
            this.snippetReflectionProvider = snippetReflectionProvider;
        }

        @Override
        protected String encode(ObjectCopier.Encoder encoder, Object obj) {
            return "";
        }

        @Override
        protected Object decode(ObjectCopier.Decoder decoder, Class<?> concreteType, String encoding, String encoded) {
            return snippetReflectionProvider;
        }
    }

    public static class CInterfaceLocationIdentityBuiltIn extends ObjectCopier.Builtin {
        protected CInterfaceLocationIdentityBuiltIn() {
            super(CInterfaceLocationIdentity.class);
        }

        @Override
        protected String encode(ObjectCopier.Encoder encoder, Object obj) {
            CInterfaceLocationIdentity cInterfaceLocationIdentity = (CInterfaceLocationIdentity) obj;
            return cInterfaceLocationIdentity.toString();
        }

        @Override
        protected Object decode(ObjectCopier.Decoder decoder, Class<?> concreteType, String encoding, String encoded) {
            return new CInterfaceLocationIdentity(encoded);
        }
    }

    public static class FastThreadLocalLocationIdentityBuiltIn extends ObjectCopier.Builtin {
        protected FastThreadLocalLocationIdentityBuiltIn() {
            super(FastThreadLocal.FastThreadLocalLocationIdentity.class);
        }

        @Override
        protected String encode(ObjectCopier.Encoder encoder, Object obj) {
            FastThreadLocal.FastThreadLocalLocationIdentity fastThreadLocalLocationIdentity = (FastThreadLocal.FastThreadLocalLocationIdentity) obj;
            FastThreadLocal fastThreadLocal = ReflectionUtil.readField(FastThreadLocal.FastThreadLocalLocationIdentity.class, "this$0", fastThreadLocalLocationIdentity);
            Field staticField = encoder.getExternalValues().get(fastThreadLocal);
            return staticField.getDeclaringClass().getName() + ":" + staticField.getName();
        }

        @Override
        protected Object decode(ObjectCopier.Decoder decoder, Class<?> concreteType, String encoding, String encoded) {
            String[] fieldParts = encoded.split(":");
            String className = fieldParts[0];
            String fieldName = fieldParts[1];
            Class<?> declaringClass = ReflectionUtil.lookupClass(false, className);
            FastThreadLocal fastThreadLocal = ReflectionUtil.readStaticField(declaringClass, fieldName);
            return fastThreadLocal.getLocationIdentity();
        }
    }
}
