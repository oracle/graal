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

import java.io.IOException;
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
import com.oracle.svm.hosted.meta.HostedUniverse;
import com.oracle.svm.hosted.thread.VMThreadLocalCollector;
import com.oracle.svm.util.ModuleSupport;
import com.oracle.svm.util.ReflectionUtil;

import jdk.graal.compiler.api.replacements.SnippetReflectionProvider;
import jdk.graal.compiler.nodes.EncodedGraph;
import jdk.graal.compiler.util.ObjectCopier;
import jdk.graal.compiler.util.ObjectCopierInputStream;
import jdk.graal.compiler.util.ObjectCopierOutputStream;
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

    /**
     * This map stores the field indexes that should be relinked using the hosted value of a
     * constant from the key type.
     */
    protected final Map<AnalysisType, Set<Integer>> fieldsToRelink = new HashMap<>();
    private final ImageClassLoader imageClassLoader;

    @SuppressWarnings("this-escape")
    public SVMImageLayerSnapshotUtil(ImageClassLoader imageClassLoader) {
        super(true);
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
    public String getTypeDescriptor(AnalysisType type) {
        if (type.toJavaName(true).contains(GENERATED_SERIALIZATION)) {
            return getGeneratedSerializationName(type);
        }
        if (isProxyType(type)) {
            return type.toJavaName(true);
        }
        return super.getTypeDescriptor(type);
    }

    private static String generatedSerializationClassName(SerializationSupport.SerializationLookupKey serializationLookupKey) {
        return GENERATED_SERIALIZATION + ":" + serializationLookupKey.getDeclaringClass() + "," + serializationLookupKey.getTargetConstructorClass();
    }

    @Override
    public String getMethodDescriptor(AnalysisMethod method) {
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
        return super.getMethodDescriptor(method);
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
    public GraphDecoder getGraphAnalysisElementsDecoder(ImageLayerLoader imageLayerLoader, AnalysisMethod analysisMethod, SnippetReflectionProvider snippetReflectionProvider) {
        return new SVMGraphAnalysisElementsDecoder(EncodedGraph.class.getClassLoader(), (SVMImageLayerLoader) imageLayerLoader, analysisMethod, snippetReflectionProvider);
    }

    @Override
    public GraphDecoder getGraphDecoder(ImageLayerLoader imageLayerLoader, AnalysisMethod analysisMethod, SnippetReflectionProvider snippetReflectionProvider) {
        return new SVMGraphDecoder(EncodedGraph.class.getClassLoader(), (SVMImageLayerLoader) imageLayerLoader, analysisMethod, snippetReflectionProvider);
    }

    public static class SVMGraphEncoder extends GraphEncoder {
        @SuppressWarnings("this-escape")
        public SVMGraphEncoder(Map<Object, Field> externalValues, ImageLayerWriter imageLayerWriter) {
            super(externalValues, imageLayerWriter);
            addBuiltin(new AnalysisTypeBuiltIn(null));
            addBuiltin(new HostedMethodBuiltIn(null));
            addBuiltin(new HostedOptionValuesBuiltIn());
            addBuiltin(new HostedSnippetReflectionProviderBuiltIn(null));
            addBuiltin(new CInterfaceLocationIdentityBuiltIn());
            addBuiltin(new FastThreadLocalLocationIdentityBuiltIn());
            addBuiltin(new VMThreadLocalInfoBuiltIn());
        }
    }

    public abstract static class AbstractSVMGraphDecoder extends GraphDecoder {
        @SuppressWarnings("this-escape")
        public AbstractSVMGraphDecoder(ClassLoader classLoader, SVMImageLayerLoader svmImageLayerLoader, AnalysisMethod analysisMethod, SnippetReflectionProvider snippetReflectionProvider) {
            super(classLoader, svmImageLayerLoader, analysisMethod);
            addBuiltin(new HostedOptionValuesBuiltIn());
            addBuiltin(new HostedSnippetReflectionProviderBuiltIn(snippetReflectionProvider));
            addBuiltin(new CInterfaceLocationIdentityBuiltIn());
            addBuiltin(new FastThreadLocalLocationIdentityBuiltIn());
            addBuiltin(new VMThreadLocalInfoBuiltIn());
        }
    }

    public static class SVMGraphAnalysisElementsDecoder extends AbstractSVMGraphDecoder {
        @SuppressWarnings("this-escape")
        public SVMGraphAnalysisElementsDecoder(ClassLoader classLoader, SVMImageLayerLoader svmImageLayerLoader, AnalysisMethod analysisMethod, SnippetReflectionProvider snippetReflectionProvider) {
            super(classLoader, svmImageLayerLoader, analysisMethod, snippetReflectionProvider);
            addBuiltin(new AnalysisTypeBuiltIn(svmImageLayerLoader));
            addBuiltin(new AnalysisMethodBuiltIn(svmImageLayerLoader));
        }
    }

    public static class SVMGraphDecoder extends AbstractSVMGraphDecoder {
        @SuppressWarnings("this-escape")
        public SVMGraphDecoder(ClassLoader classLoader, SVMImageLayerLoader svmImageLayerLoader, AnalysisMethod analysisMethod, SnippetReflectionProvider snippetReflectionProvider) {
            super(classLoader, svmImageLayerLoader, analysisMethod, snippetReflectionProvider);
            addBuiltin(new HostedTypeBuiltIn(svmImageLayerLoader));
            addBuiltin(new HostedMethodBuiltIn(svmImageLayerLoader));
        }
    }

    public abstract static class AbstractHostedTypeBuiltIn extends ObjectCopier.Builtin {
        protected final SVMImageLayerLoader svmImageLayerLoader;

        protected AbstractHostedTypeBuiltIn(SVMImageLayerLoader svmImageLayerLoader) {
            super(HostedType.class, HostedInstanceClass.class, HostedArrayClass.class);
            this.svmImageLayerLoader = svmImageLayerLoader;
        }

        @Override
        protected void encode(ObjectCopier.Encoder encoder, ObjectCopierOutputStream stream, Object obj) throws IOException {
            int id = ((HostedType) obj).getWrapped().getId();
            stream.writePackedUnsignedInt(id);
        }
    }

    public static class AnalysisTypeBuiltIn extends AbstractHostedTypeBuiltIn {
        protected AnalysisTypeBuiltIn(SVMImageLayerLoader svmImageLayerLoader) {
            super(svmImageLayerLoader);
        }

        @Override
        protected Object decode(ObjectCopier.Decoder decoder, Class<?> concreteType, ObjectCopierInputStream stream) throws IOException {
            return getAnalysisType(svmImageLayerLoader, stream);
        }
    }

    public static class HostedTypeBuiltIn extends AbstractHostedTypeBuiltIn {
        protected HostedTypeBuiltIn(SVMImageLayerLoader svmImageLayerLoader) {
            super(svmImageLayerLoader);
        }

        @Override
        protected Object decode(ObjectCopier.Decoder decoder, Class<?> concreteType, ObjectCopierInputStream stream) throws IOException {
            HostedUniverse hostedUniverse = svmImageLayerLoader.getHostedUniverse();
            return hostedUniverse.lookup(getAnalysisType(svmImageLayerLoader, stream));
        }
    }

    private static AnalysisType getAnalysisType(SVMImageLayerLoader imageLayerLoader, ObjectCopierInputStream stream) throws IOException {
        int id = stream.readPackedUnsignedInt();
        return imageLayerLoader.getAnalysisTypeForBaseLayerId(id);
    }

    public abstract static class AbstractHostedMethodBuiltIn extends ObjectCopier.Builtin {
        protected final SVMImageLayerLoader svmImageLayerLoader;

        protected AbstractHostedMethodBuiltIn(SVMImageLayerLoader svmImageLayerLoader) {
            super(HostedMethod.class);
            this.svmImageLayerLoader = svmImageLayerLoader;
        }

        @Override
        protected void encode(ObjectCopier.Encoder encoder, ObjectCopierOutputStream stream, Object obj) throws IOException {
            stream.writePackedUnsignedInt(((HostedMethod) obj).getWrapped().getId());
        }
    }

    public static class AnalysisMethodBuiltIn extends AbstractHostedMethodBuiltIn {
        protected AnalysisMethodBuiltIn(SVMImageLayerLoader svmImageLayerLoader) {
            super(svmImageLayerLoader);
        }

        @Override
        protected Object decode(ObjectCopier.Decoder decoder, Class<?> concreteType, ObjectCopierInputStream stream) throws IOException {
            return getAnalysisMethod(svmImageLayerLoader, stream);
        }
    }

    public static class HostedMethodBuiltIn extends AbstractHostedMethodBuiltIn {
        protected HostedMethodBuiltIn(SVMImageLayerLoader svmImageLayerLoader) {
            super(svmImageLayerLoader);
        }

        @Override
        protected Object decode(ObjectCopier.Decoder decoder, Class<?> concreteType, ObjectCopierInputStream stream) throws IOException {
            HostedUniverse hostedUniverse = svmImageLayerLoader.getHostedUniverse();
            return hostedUniverse.lookup(getAnalysisMethod(svmImageLayerLoader, stream));
        }
    }

    private static AnalysisMethod getAnalysisMethod(SVMImageLayerLoader imageLayerLoader, ObjectCopierInputStream stream) throws IOException {
        int id = stream.readPackedUnsignedInt();
        return imageLayerLoader.getAnalysisMethodForBaseLayerId(id);
    }

    public static class HostedOptionValuesBuiltIn extends ObjectCopier.Builtin {
        protected HostedOptionValuesBuiltIn() {
            super(HostedOptionValues.class);
        }

        @Override
        protected void encode(ObjectCopier.Encoder encoder, ObjectCopierOutputStream stream, Object obj) throws IOException {
        }

        @Override
        protected Object decode(ObjectCopier.Decoder decoder, Class<?> concreteType, ObjectCopierInputStream stream) throws IOException {
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
        protected void encode(ObjectCopier.Encoder encoder, ObjectCopierOutputStream stream, Object obj) throws IOException {
        }

        @Override
        protected Object decode(ObjectCopier.Decoder decoder, Class<?> concreteType, ObjectCopierInputStream stream) throws IOException {
            return snippetReflectionProvider;
        }
    }

    public static class CInterfaceLocationIdentityBuiltIn extends ObjectCopier.Builtin {
        protected CInterfaceLocationIdentityBuiltIn() {
            super(CInterfaceLocationIdentity.class);
        }

        private static String asString(Object obj) {
            var cInterfaceLocationIdentity = (CInterfaceLocationIdentity) obj;
            return cInterfaceLocationIdentity.toString();
        }

        @Override
        protected void makeChildIds(ObjectCopier.Encoder encoder, Object obj, ObjectCopier.ObjectPath objectPath) {
            encoder.makeStringId(asString(obj), objectPath);
        }

        @Override
        protected void encode(ObjectCopier.Encoder encoder, ObjectCopierOutputStream stream, Object obj) throws IOException {
            String string = asString(obj);
            encoder.writeString(stream, string);
        }

        @Override
        protected Object decode(ObjectCopier.Decoder decoder, Class<?> concreteType, ObjectCopierInputStream stream) throws IOException {
            String encoded = decoder.readString(stream);
            return new CInterfaceLocationIdentity(encoded);
        }
    }

    public static class FastThreadLocalLocationIdentityBuiltIn extends ObjectCopier.Builtin {
        protected FastThreadLocalLocationIdentityBuiltIn() {
            super(FastThreadLocal.FastThreadLocalLocationIdentity.class);
        }

        private static FastThreadLocal getFastThreadLocal(Object obj) {
            var fastThreadLocalLocationIdentity = (FastThreadLocal.FastThreadLocalLocationIdentity) obj;
            return ReflectionUtil.readField(FastThreadLocal.FastThreadLocalLocationIdentity.class, "this$0", fastThreadLocalLocationIdentity);
        }

        @Override
        protected void makeChildIds(ObjectCopier.Encoder encoder, Object obj, ObjectCopier.ObjectPath objectPath) {
            makeStaticFieldIds(encoder, objectPath, getFastThreadLocal(obj));
        }

        @Override
        protected void encode(ObjectCopier.Encoder encoder, ObjectCopierOutputStream stream, Object obj) throws IOException {
            writeStaticField(encoder, stream, getFastThreadLocal(obj));
        }

        @Override
        protected Object decode(ObjectCopier.Decoder decoder, Class<?> concreteType, ObjectCopierInputStream stream) throws IOException {
            FastThreadLocal fastThreadLocal = readStaticFieldAndGetObject(decoder, stream);
            return fastThreadLocal.getLocationIdentity();
        }
    }

    public static class VMThreadLocalInfoBuiltIn extends ObjectCopier.Builtin {
        protected VMThreadLocalInfoBuiltIn() {
            super(VMThreadLocalInfo.class);
        }

        private static FastThreadLocal getThreadLocal(Object obj) {
            VMThreadLocalCollector vmThreadLocalCollector = ImageSingletons.lookup(VMThreadLocalCollector.class);
            return vmThreadLocalCollector.getThreadLocal((VMThreadLocalInfo) obj);
        }

        @Override
        protected void makeChildIds(ObjectCopier.Encoder encoder, Object obj, ObjectCopier.ObjectPath objectPath) {
            makeStaticFieldIds(encoder, objectPath, getThreadLocal(obj));
        }

        @Override
        protected void encode(ObjectCopier.Encoder encoder, ObjectCopierOutputStream stream, Object obj) throws IOException {
            writeStaticField(encoder, stream, getThreadLocal(obj));
        }

        @Override
        protected Object decode(ObjectCopier.Decoder decoder, Class<?> concreteType, ObjectCopierInputStream stream) throws IOException {
            FastThreadLocal fastThreadLocal = readStaticFieldAndGetObject(decoder, stream);
            return ImageSingletons.lookup(VMThreadLocalCollector.class).forFastThreadLocal(fastThreadLocal);
        }
    }

    private static void makeStaticFieldIds(ObjectCopier.Encoder encoder, ObjectCopier.ObjectPath objectPath, Object object) {
        Field staticField = encoder.getExternalValues().get(object);
        encoder.makeStringId(staticField.getDeclaringClass().getName(), objectPath);
        encoder.makeStringId(staticField.getName(), objectPath);
    }

    private static void writeStaticField(ObjectCopier.Encoder encoder, ObjectCopierOutputStream stream, Object object) throws IOException {
        Field staticField = encoder.getExternalValues().get(object);
        encoder.writeString(stream, staticField.getDeclaringClass().getName());
        encoder.writeString(stream, staticField.getName());
    }

    private static <T> T readStaticFieldAndGetObject(ObjectCopier.Decoder decoder, ObjectCopierInputStream stream) throws IOException {
        String className = decoder.readString(stream);
        String fieldName = decoder.readString(stream);
        Class<?> declaringClass = ReflectionUtil.lookupClass(false, className);
        return ReflectionUtil.readStaticField(declaringClass, fieldName);
    }
}
