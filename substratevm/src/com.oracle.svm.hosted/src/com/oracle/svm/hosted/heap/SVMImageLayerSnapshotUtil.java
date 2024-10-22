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
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.graal.pointsto.heap.ImageLayerLoader;
import com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil;
import com.oracle.graal.pointsto.heap.ImageLayerWriter;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.svm.core.c.struct.CInterfaceLocationIdentity;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.option.HostedOptionValues;
import com.oracle.svm.core.reflect.serialize.SerializationSupport;
import com.oracle.svm.core.threadlocal.FastThreadLocal;
import com.oracle.svm.core.threadlocal.VMThreadLocalInfo;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.ImageClassLoader;
import com.oracle.svm.hosted.VMFeature;
import com.oracle.svm.hosted.code.FactoryMethod;
import com.oracle.svm.hosted.code.IncompatibleClassChangeFallbackMethod;
import com.oracle.svm.hosted.meta.HostedArrayClass;
import com.oracle.svm.hosted.meta.HostedInstanceClass;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.HostedSnippetReflectionProvider;
import com.oracle.svm.hosted.meta.HostedType;
import com.oracle.svm.hosted.thread.VMThreadLocalCollector;
import com.oracle.svm.util.ModuleSupport;
import com.oracle.svm.util.ReflectionUtil;

import jdk.graal.compiler.api.replacements.SnippetReflectionProvider;
import jdk.graal.compiler.nodes.EncodedGraph;
import jdk.graal.compiler.util.ObjectCopier;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaMethod;

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

    /**
     * This map stores the field indexes that should be relinked using the hosted value of a
     * constant from the key type.
     */
    protected final Map<AnalysisType, Set<Integer>> fieldsToRelink = new HashMap<>();
    private final ImageClassLoader imageClassLoader;

    @SuppressWarnings("this-escape")
    public SVMImageLayerSnapshotUtil(ImageClassLoader imageClassLoader) {
        super();
        this.imageClassLoader = imageClassLoader;
        addSVMExternalValueFields();
    }

    /**
     * Gets the externalValues (like {@link ObjectCopier#getExternalValueFields()}) of classes from
     * the SVM core classes.
     */
    private void addSVMExternalValueFields() {
        for (URI svmURI : getBuilderLocations()) {
            for (String className : imageClassLoader.classLoaderSupport.classes(svmURI)) {
                try {
                    Class<?> clazz = imageClassLoader.forName(className);

                    String packageName = clazz.getPackageName();
                    if (!shouldScanPackage(packageName)) {
                        continue;
                    }

                    /* The ObjectCopier needs to access the static fields by reflection */
                    Module module = clazz.getModule();
                    ModuleSupport.accessPackagesToClass(ModuleSupport.Access.OPEN, ObjectCopier.class, false, module.getName(), packageName);

                    ObjectCopier.addStaticFinalObjectFields(clazz, externalValueFields);
                } catch (ClassNotFoundException e) {
                    throw AnalysisError.shouldNotReachHere("The class %s from the modulePath %s was not found".formatted(className, svmURI.getPath()), e);
                }
            }
        }
    }

    protected Set<URI> getBuilderLocations() {
        try {
            Class<?> vmFeatureClass = ImageSingletons.lookup(VMFeature.class).getClass();
            URI svmURI = VMFeature.class.getProtectionDomain().getCodeSource().getLocation().toURI();
            if (vmFeatureClass == VMFeature.class) {
                return Set.of(svmURI);
            } else {
                return Set.of(svmURI, vmFeatureClass.getProtectionDomain().getCodeSource().getLocation().toURI());
            }
        } catch (URISyntaxException e) {
            throw VMError.shouldNotReachHere("Error when trying to get SVM URI", e);
        }
    }

    @SuppressWarnings("unused")
    protected boolean shouldScanPackage(String packageName) {
        return true;
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
            ResolvedJavaMethod targetConstructor = factoryMethod.getTargetConstructor();
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
        public SVMGraphEncoder(Map<Object, Field> externalValues, ImageLayerWriter imageLayerWriter) {
            super(externalValues, imageLayerWriter);
            addBuiltin(new HostedTypeBuiltIn(null));
            addBuiltin(new HostedMethodBuiltIn(null));
            addBuiltin(new HostedOptionValuesBuiltIn());
            addBuiltin(new HostedSnippetReflectionProviderBuiltIn(null));
            addBuiltin(new CInterfaceLocationIdentityBuiltIn());
            addBuiltin(new FastThreadLocalLocationIdentityBuiltIn());
            addBuiltin(new VMThreadLocalInfoBuiltIn());
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
            addBuiltin(new VMThreadLocalInfoBuiltIn());
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
            return encodeStaticField(encoder, fastThreadLocal);
        }

        @Override
        protected Object decode(ObjectCopier.Decoder decoder, Class<?> concreteType, String encoding, String encoded) {
            FastThreadLocal fastThreadLocal = getObjectFromStaticField(encoded);
            return fastThreadLocal.getLocationIdentity();
        }
    }

    public static class VMThreadLocalInfoBuiltIn extends ObjectCopier.Builtin {
        protected VMThreadLocalInfoBuiltIn() {
            super(VMThreadLocalInfo.class);
        }

        @Override
        protected String encode(ObjectCopier.Encoder encoder, Object obj) {
            VMThreadLocalInfo vmThreadLocalInfo = (VMThreadLocalInfo) obj;
            VMThreadLocalCollector vmThreadLocalCollector = ImageSingletons.lookup(VMThreadLocalCollector.class);
            FastThreadLocal fastThreadLocal = vmThreadLocalCollector.getThreadLocal(vmThreadLocalInfo);
            return encodeStaticField(encoder, fastThreadLocal);
        }

        @Override
        protected Object decode(ObjectCopier.Decoder decoder, Class<?> concreteType, String encoding, String encoded) {
            FastThreadLocal fastThreadLocal = getObjectFromStaticField(encoded);
            return ImageSingletons.lookup(VMThreadLocalCollector.class).forFastThreadLocal(fastThreadLocal);
        }
    }

    private static String encodeStaticField(ObjectCopier.Encoder encoder, Object object) {
        Field staticField = encoder.getExternalValues().get(object);
        return staticField.getDeclaringClass().getName() + ":" + staticField.getName();
    }

    private static <T> T getObjectFromStaticField(String staticField) {
        String[] fieldParts = staticField.split(":");
        String className = fieldParts[0];
        String fieldName = fieldParts[1];
        Class<?> declaringClass = ReflectionUtil.lookupClass(false, className);
        return ReflectionUtil.readStaticField(declaringClass, fieldName);
    }
}
