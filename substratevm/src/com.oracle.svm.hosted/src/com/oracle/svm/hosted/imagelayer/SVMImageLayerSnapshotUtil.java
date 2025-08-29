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
package com.oracle.svm.hosted.imagelayer;

import static com.oracle.svm.hosted.methodhandles.InjectedInvokerRenamingSubstitutionProcessor.isInjectedInvokerType;
import static com.oracle.svm.hosted.methodhandles.MethodHandleInvokerRenamingSubstitutionProcessor.isMethodHandleType;
import static com.oracle.svm.hosted.reflect.proxy.ProxyRenamingSubstitutionProcessor.isProxyType;
import static jdk.graal.compiler.java.LambdaUtils.isLambdaType;

import java.io.IOException;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.graal.pointsto.ObjectScanner;
import com.oracle.graal.pointsto.heap.ImageHeapConstant;
import com.oracle.graal.pointsto.heap.ImageHeapInstance;
import com.oracle.graal.pointsto.heap.ImageHeapObjectArray;
import com.oracle.graal.pointsto.heap.ImageHeapPrimitiveArray;
import com.oracle.graal.pointsto.infrastructure.OriginalMethodProvider;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.meta.PointsToAnalysisField;
import com.oracle.graal.pointsto.meta.PointsToAnalysisMethod;
import com.oracle.graal.pointsto.meta.PointsToAnalysisType;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.svm.core.c.CGlobalDataImpl;
import com.oracle.svm.core.c.struct.CInterfaceLocationIdentity;
import com.oracle.svm.core.graal.code.CGlobalDataInfo;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.DynamicHubCompanion;
import com.oracle.svm.core.option.HostedOptionValues;
import com.oracle.svm.core.reflect.serialize.SerializationSupport;
import com.oracle.svm.core.threadlocal.FastThreadLocal;
import com.oracle.svm.core.threadlocal.VMThreadLocalInfo;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.ImageClassLoader;
import com.oracle.svm.hosted.VMFeature;
import com.oracle.svm.hosted.c.AppLayerCGlobalTracking;
import com.oracle.svm.hosted.c.CGlobalDataFeature;
import com.oracle.svm.hosted.c.InitialLayerCGlobalTracking;
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
import jdk.graal.compiler.debug.CounterKey;
import jdk.graal.compiler.nodes.EncodedGraph;
import jdk.graal.compiler.nodes.FieldLocationIdentity;
import jdk.graal.compiler.nodes.NodeClassMap;
import jdk.graal.compiler.util.ObjectCopier;
import jdk.graal.compiler.util.ObjectCopierInputStream;
import jdk.graal.compiler.util.ObjectCopierOutputStream;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;
import jdk.vm.ci.meta.ConstantReflectionProvider;

public class SVMImageLayerSnapshotUtil {

    public static final String CONSTRUCTOR_NAME = "<init>";
    public static final String CLASS_INIT_NAME = "<clinit>";

    public static final String PERSISTED = "persisted";
    public static final String TRACKED_REASON = "reachable from a graph";

    public static final int UNDEFINED_CONSTANT_ID = -1;
    public static final int UNDEFINED_FIELD_INDEX = -1;

    public static final String GENERATED_SERIALIZATION = "jdk.internal.reflect.GeneratedSerializationConstructorAccessor";

    static final Field companion = ReflectionUtil.lookupField(DynamicHub.class, "companion");
    static final Field name = ReflectionUtil.lookupField(DynamicHub.class, "name");
    static final Field componentType = ReflectionUtil.lookupField(DynamicHub.class, "componentType");

    static final Field classInitializationInfo = ReflectionUtil.lookupField(DynamicHubCompanion.class, "classInitializationInfo");
    static final Field superHub = ReflectionUtil.lookupField(DynamicHubCompanion.class, "superHub");
    static final Field interfacesEncoding = ReflectionUtil.lookupField(DynamicHubCompanion.class, "interfacesEncoding");
    static final Field enumConstantsReference = ReflectionUtil.lookupField(DynamicHubCompanion.class, "enumConstantsReference");
    static final Field arrayHub = ReflectionUtil.lookupField(DynamicHubCompanion.class, "arrayHub");

    protected static final Set<Field> dynamicHubRelinkedFields = Set.of(companion, name, componentType);
    protected static final Set<Field> dynamicHubCompanionRelinkedFields = Set.of(classInitializationInfo, superHub, arrayHub);

    private static final Class<?> sourceRoots = ReflectionUtil.lookupClass("com.oracle.svm.hosted.image.sources.SourceCache$SourceRoots");
    private static final Class<?> completableFuture = ReflectionUtil.lookupClass("com.oracle.svm.core.jdk.CompletableFutureFieldHolder");

    /**
     * This map stores the field indexes that should be relinked using the hosted value of a
     * constant from the key type.
     */
    protected final Map<AnalysisType, Set<Integer>> fieldsToRelink = new ConcurrentHashMap<>();
    private final ImageClassLoader imageClassLoader;
    protected final List<Field> externalValueFields;
    /** This needs to be initialized after analysis, as some fields are not available before. */
    protected Map<Object, Field> externalValues;

    @SuppressWarnings("this-escape")
    public SVMImageLayerSnapshotUtil(ImageClassLoader imageClassLoader) {
        try {
            this.externalValueFields = ObjectCopier.getExternalValueFields();
        } catch (IOException e) {
            throw AnalysisError.shouldNotReachHere("Unexpected exception when creating external value fields list", e);
        }
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
                    externalValueFields.addAll(getStaticFinalObjectFields(clazz));
                } catch (ClassNotFoundException e) {
                    throw AnalysisError.shouldNotReachHere("The class %s from the modulePath %s was not found".formatted(className, svmURI.getPath()), e);
                }
            }
        }
    }

    public List<Field> getStaticFinalObjectFields(Class<?> clazz) {
        String packageName = clazz.getPackageName();
        if (!shouldScanPackage(packageName)) {
            return List.of();
        }

        if (!shouldScanClass(clazz)) {
            return List.of();
        }

        /* The ObjectCopier needs to access the static fields by reflection */
        Module module = clazz.getModule();
        if (module.getName() != null) {
            ModuleSupport.accessPackagesToClass(ModuleSupport.Access.OPEN, ObjectCopier.class, false, module.getName(), packageName);
        }

        return ObjectCopier.getStaticFinalObjectFields(clazz);
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

    private static boolean shouldScanClass(Class<?> clazz) {
        /* This class should not be scanned because it needs to be initialized after the analysis */
        return !clazz.equals(sourceRoots) && !clazz.equals(completableFuture);
    }

    /**
     * Get all the field indexes that should be relinked using the hosted value of a constant from
     * the given type.
     */
    public Set<Integer> getRelinkedFields(AnalysisType type, AnalysisMetaAccess metaAccess) {
        Set<Integer> result = fieldsToRelink.computeIfAbsent(type, key -> {
            Class<?> clazz = type.getJavaClass();
            if (clazz == Class.class) {
                return getRelinkedFields(type, dynamicHubRelinkedFields, metaAccess);
            } else if (clazz == DynamicHubCompanion.class) {
                return getRelinkedFields(type, dynamicHubCompanionRelinkedFields, metaAccess);
            }
            return null;
        });
        if (result == null) {
            return Set.of();
        }
        return result;
    }

    private static Set<Integer> getRelinkedFields(AnalysisType type, Set<Field> typeRelinkedFieldsSet, AnalysisMetaAccess metaAccess) {
        type.getInstanceFields(true);
        return typeRelinkedFieldsSet.stream().map(metaAccess::lookupJavaField).map(AnalysisField::getPosition).collect(Collectors.toSet());
    }

    public SVMGraphEncoder getGraphEncoder(NodeClassMap nodeClassMap) {
        return new SVMGraphEncoder(externalValues, nodeClassMap);
    }

    public AbstractSVMGraphDecoder getGraphHostedToAnalysisElementsDecoder(SVMImageLayerLoader imageLayerLoader, AnalysisMethod analysisMethod, SnippetReflectionProvider snippetReflectionProvider,
                    NodeClassMap nodeClassMap) {

        return new SVMGraphHostedToAnalysisElementsDecoder(EncodedGraph.class.getClassLoader(), imageLayerLoader, analysisMethod, snippetReflectionProvider, nodeClassMap);
    }

    public AbstractSVMGraphDecoder getGraphDecoder(SVMImageLayerLoader imageLayerLoader, AnalysisMethod analysisMethod,
                    SnippetReflectionProvider snippetReflectionProvider, NodeClassMap nodeClassMap) {
        return new SVMGraphDecoder(EncodedGraph.class.getClassLoader(), imageLayerLoader, analysisMethod, snippetReflectionProvider, nodeClassMap);
    }

    /**
     * Compute and cache the final {@code externalValues} map in
     * {@link SVMImageLayerSnapshotUtil#externalValues} to avoid computing it for each graph.
     * <p>
     * A single {@code ObjectCopier.Encoder} instance could alternatively be used for all graphs,
     * but it would then be impossible to process multiple graphs concurrently.
     */
    public void initializeExternalValues() {
        assert externalValues == null : "The external values should be computed only once.";
        externalValues = ObjectCopier.Encoder.gatherExternalValues(externalValueFields);
    }

    public String getTypeDescriptor(AnalysisType type) {
        String javaName = type.toJavaName(true);
        if (javaName.contains(GENERATED_SERIALIZATION)) {
            return getGeneratedSerializationName(type);
        }
        if (isProxyType(type)) {
            return javaName;
        }
        return addModuleName(javaName, type.getJavaClass().getModule().getName());
    }

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
        Executable originalMethod = OriginalMethodProvider.getJavaMethod(method);
        if (originalMethod != null) {
            return addModuleName(originalMethod.toString(), moduleName);
        }
        /*
         * The wrapped qualified method is needed here as the AnalysisMethod replaces unresolved
         * parameter or return types with java.lang.Object, potentially causing method descriptor
         * duplication. The wrapped method signature preserves the original type information,
         * preventing this issue.
         */
        return addModuleName(getWrappedQualifiedName(method), moduleName);
    }

    /*
     * The GeneratedSerializationConstructorAccessor names created in SerializationSupport are not
     * stable in a multi threading context. To ensure the correct one is matched in the extension
     * image, the constructor accessors table from SerializationSupport is accessed.
     */
    private static String getGeneratedSerializationName(AnalysisType type) {
        Class<?> constructorAccessor = type.getJavaClass();
        SerializationSupport serializationRegistry = SerializationSupport.currentLayer();
        SerializationSupport.SerializationLookupKey serializationLookupKey = serializationRegistry.getKeyFromConstructorAccessorClass(constructorAccessor);
        return generatedSerializationClassName(serializationLookupKey);
    }

    private static String generatedSerializationClassName(SerializationSupport.SerializationLookupKey serializationLookupKey) {
        return GENERATED_SERIALIZATION + ":" + serializationLookupKey.getDeclaringClass() + "," + serializationLookupKey.getTargetConstructorClass();
    }

    private static String addModuleName(String elementName, String moduleName) {
        return moduleName + ":" + elementName;
    }

    private static String getQualifiedName(AnalysisMethod method) {
        return method.getSignature().getReturnType().toJavaName(true) + " " + method.getQualifiedName();
    }

    private static String getWrappedQualifiedName(AnalysisMethod method) {
        return method.wrapped.format("%R %H.%n(%P)");
    }

    public static void forcePersistConstant(ImageHeapConstant imageHeapConstant) {
        AnalysisUniverse universe = imageHeapConstant.getType().getUniverse();
        universe.getHeapScanner().markReachable(imageHeapConstant, ObjectScanner.OtherReason.PERSISTED);

        imageHeapConstant.getType().registerAsTrackedAcrossLayers(imageHeapConstant);
        /* If this is a Class constant persist the corresponding type. */
        ConstantReflectionProvider constantReflection = universe.getBigbang().getConstantReflectionProvider();
        AnalysisType typeFromClassConstant = (AnalysisType) constantReflection.asJavaType(imageHeapConstant);
        if (typeFromClassConstant != null) {
            typeFromClassConstant.registerAsTrackedAcrossLayers(imageHeapConstant);
        }
    }

    public static class SVMGraphEncoder extends ObjectCopier.Encoder {
        @SuppressWarnings("this-escape")
        public SVMGraphEncoder(Map<Object, Field> externalValues, NodeClassMap nodeClassMap) {
            super(externalValues);
            addBuiltin(new ImageHeapConstantBuiltIn(null));
            addBuiltin(new AnalysisTypeBuiltIn(null));
            addBuiltin(new AnalysisMethodBuiltIn(null, null));
            addBuiltin(new AnalysisFieldBuiltIn(null));
            addBuiltin(new FieldLocationIdentityBuiltIn(null));
            addBuiltin(new HostedTypeBuiltIn(null));
            addBuiltin(new HostedMethodBuiltIn(null));
            addBuiltin(new HostedOptionValuesBuiltIn());
            addBuiltin(new HostedSnippetReflectionProviderBuiltIn(null));
            addBuiltin(new CInterfaceLocationIdentityBuiltIn());
            addBuiltin(new FastThreadLocalLocationIdentityBuiltIn());
            addBuiltin(new VMThreadLocalInfoBuiltIn());
            LayeredCGlobalTracking cGlobalTracking = new LayeredCGlobalTracking(CGlobalDataFeature.singleton().getInitialLayerCGlobalTracking(), null);
            addBuiltin(new CGlobalDataImplBuiltIn(cGlobalTracking));
            addBuiltin(new CGlobalDataInfoBuiltIn(cGlobalTracking));
            if (nodeClassMap != null) {
                addBuiltin(new NodeClassMapBuiltin(nodeClassMap));
            }
        }

        @Override
        protected void prepareObject(Object obj) {
            if (obj instanceof CounterKey counterKey) {
                /*
                 * The name needs to be cached before we persist the graph to avoid modifying the
                 * field during the encoding.
                 */
                counterKey.getName();
            }
        }
    }

    public abstract static class AbstractSVMGraphDecoder extends ObjectCopier.Decoder {
        private final HostedImageLayerBuildingSupport imageLayerBuildingSupport;

        @SuppressWarnings("this-escape")
        public AbstractSVMGraphDecoder(ClassLoader classLoader, SVMImageLayerLoader imageLayerLoader, AnalysisMethod analysisMethod, SnippetReflectionProvider snippetReflectionProvider,
                        NodeClassMap nodeClassMap) {
            super(classLoader);
            this.imageLayerBuildingSupport = imageLayerLoader.getImageLayerBuildingSupport();
            addBuiltin(new ImageHeapConstantBuiltIn(imageLayerLoader));
            addBuiltin(new AnalysisTypeBuiltIn(imageLayerLoader));
            addBuiltin(new AnalysisMethodBuiltIn(imageLayerLoader, analysisMethod));
            addBuiltin(new AnalysisFieldBuiltIn(imageLayerLoader));
            addBuiltin(new FieldLocationIdentityBuiltIn(imageLayerLoader));
            addBuiltin(new HostedOptionValuesBuiltIn());
            addBuiltin(new HostedSnippetReflectionProviderBuiltIn(snippetReflectionProvider));
            addBuiltin(new CInterfaceLocationIdentityBuiltIn());
            addBuiltin(new FastThreadLocalLocationIdentityBuiltIn());
            addBuiltin(new VMThreadLocalInfoBuiltIn());
            LayeredCGlobalTracking cGlobalTracking = new LayeredCGlobalTracking(null, CGlobalDataFeature.singleton().getAppLayerCGlobalTracking());
            addBuiltin(new CGlobalDataImplBuiltIn(cGlobalTracking));
            addBuiltin(new CGlobalDataInfoBuiltIn(cGlobalTracking));
            if (nodeClassMap != null) {
                addBuiltin(new NodeClassMapBuiltin(nodeClassMap));
            }
        }

        @Override
        public Class<?> loadClass(String className) {
            return imageLayerBuildingSupport.lookupClass(false, className);
        }
    }

    public static class SVMGraphHostedToAnalysisElementsDecoder extends AbstractSVMGraphDecoder {
        @SuppressWarnings("this-escape")
        public SVMGraphHostedToAnalysisElementsDecoder(ClassLoader classLoader, SVMImageLayerLoader svmImageLayerLoader, AnalysisMethod analysisMethod,
                        SnippetReflectionProvider snippetReflectionProvider, NodeClassMap nodeClassMap) {
            super(classLoader, svmImageLayerLoader, analysisMethod, snippetReflectionProvider, nodeClassMap);
            addBuiltin(new HostedToAnalysisTypeDecoderBuiltIn(svmImageLayerLoader));
            addBuiltin(new HostedToAnalysisMethodDecoderBuiltIn(svmImageLayerLoader));
        }
    }

    public static class SVMGraphDecoder extends AbstractSVMGraphDecoder {
        @SuppressWarnings("this-escape")
        public SVMGraphDecoder(ClassLoader classLoader, SVMImageLayerLoader svmImageLayerLoader, AnalysisMethod analysisMethod,
                        SnippetReflectionProvider snippetReflectionProvider, NodeClassMap nodeClassMap) {
            super(classLoader, svmImageLayerLoader, analysisMethod, snippetReflectionProvider, nodeClassMap);
            addBuiltin(new HostedTypeBuiltIn(svmImageLayerLoader));
            addBuiltin(new HostedMethodBuiltIn(svmImageLayerLoader));
        }
    }

    /**
     * Builtin to replace a {@link NodeClassMap} during encoding with a placeholder so that a single
     * map will be shared by all {@link EncodedGraph}s processed by a
     * {@link jdk.graal.compiler.util.ObjectCopier.Encoder}.
     */
    public static class NodeClassMapBuiltin extends ObjectCopier.Builtin {
        private final NodeClassMap nodeClassMap;

        protected NodeClassMapBuiltin(NodeClassMap nodeClassMap) {
            super(NodeClassMap.class);
            this.nodeClassMap = Objects.requireNonNull(nodeClassMap);
        }

        @Override
        public void encode(ObjectCopier.Encoder encoder, ObjectCopierOutputStream stream, Object obj) throws IOException {
            if (nodeClassMap != obj) {
                throw AnalysisError.shouldNotReachHere("Unexpected NodeClassMap instance encountered");
            }
        }

        @Override
        protected Object decode(ObjectCopier.Decoder decoder, Class<?> concreteType, ObjectCopierInputStream stream) throws IOException {
            return nodeClassMap;
        }
    }

    public static class ImageHeapConstantBuiltIn extends ObjectCopier.Builtin {
        private final SVMImageLayerLoader imageLayerLoader;

        protected ImageHeapConstantBuiltIn(SVMImageLayerLoader imageLayerLoader) {
            super(ImageHeapConstant.class, ImageHeapInstance.class, ImageHeapObjectArray.class, ImageHeapPrimitiveArray.class);
            this.imageLayerLoader = imageLayerLoader;
        }

        @Override
        public void encode(ObjectCopier.Encoder encoder, ObjectCopierOutputStream stream, Object obj) throws IOException {
            ImageHeapConstant imageHeapConstant = (ImageHeapConstant) obj;
            forcePersistConstant(imageHeapConstant);
            stream.writePackedUnsignedInt(ImageHeapConstant.getConstantID(imageHeapConstant));
        }

        @Override
        protected Object decode(ObjectCopier.Decoder decoder, Class<?> concreteType, ObjectCopierInputStream stream) throws IOException {
            int id = stream.readPackedUnsignedInt();
            return imageLayerLoader.getOrCreateConstant(id);
        }
    }

    public static class AnalysisTypeBuiltIn extends ObjectCopier.Builtin {
        private final SVMImageLayerLoader imageLayerLoader;

        protected AnalysisTypeBuiltIn(SVMImageLayerLoader imageLayerLoader) {
            super(AnalysisType.class, PointsToAnalysisType.class);
            this.imageLayerLoader = imageLayerLoader;
        }

        @Override
        public void encode(ObjectCopier.Encoder encoder, ObjectCopierOutputStream stream, Object obj) throws IOException {
            AnalysisType type = (AnalysisType) obj;
            type.registerAsTrackedAcrossLayers(TRACKED_REASON);
            stream.writePackedUnsignedInt(type.getId());
        }

        @Override
        protected Object decode(ObjectCopier.Decoder decoder, Class<?> concreteType, ObjectCopierInputStream stream) throws IOException {
            int id = stream.readPackedUnsignedInt();
            return imageLayerLoader.getAnalysisTypeForBaseLayerId(id);
        }
    }

    public static class AnalysisMethodBuiltIn extends ObjectCopier.Builtin {
        private final SVMImageLayerLoader imageLayerLoader;
        private final AnalysisMethod analysisMethod;

        protected AnalysisMethodBuiltIn(SVMImageLayerLoader imageLayerLoader, AnalysisMethod analysisMethod) {
            super(AnalysisMethod.class, PointsToAnalysisMethod.class);
            this.imageLayerLoader = imageLayerLoader;
            this.analysisMethod = analysisMethod;
        }

        @Override
        public void encode(ObjectCopier.Encoder encoder, ObjectCopierOutputStream stream, Object obj) throws IOException {
            AnalysisMethod method = (AnalysisMethod) obj;
            method.registerAsTrackedAcrossLayers(TRACKED_REASON);
            stream.writePackedUnsignedInt(method.getId());
        }

        @Override
        protected Object decode(ObjectCopier.Decoder decoder, Class<?> concreteType, ObjectCopierInputStream stream) throws IOException {
            int id = stream.readPackedUnsignedInt();
            if (id == analysisMethod.getId()) {
                return analysisMethod;
            }
            return imageLayerLoader.getAnalysisMethodForBaseLayerId(id);
        }
    }

    public static class AnalysisFieldBuiltIn extends ObjectCopier.Builtin {
        private final SVMImageLayerLoader imageLayerLoader;

        protected AnalysisFieldBuiltIn(SVMImageLayerLoader imageLayerLoader) {
            super(AnalysisField.class, PointsToAnalysisField.class);
            this.imageLayerLoader = imageLayerLoader;
        }

        @Override
        public void encode(ObjectCopier.Encoder encoder, ObjectCopierOutputStream stream, Object obj) throws IOException {
            AnalysisField field = (AnalysisField) obj;
            int id = encodeField(field);
            stream.writePackedUnsignedInt(id);
        }

        @Override
        protected Object decode(ObjectCopier.Decoder decoder, Class<?> concreteType, ObjectCopierInputStream stream) throws IOException {
            int id = stream.readPackedUnsignedInt();
            return decodeField(imageLayerLoader, id);
        }
    }

    public static class FieldLocationIdentityBuiltIn extends ObjectCopier.Builtin {
        private final SVMImageLayerLoader imageLayerLoader;

        protected FieldLocationIdentityBuiltIn(SVMImageLayerLoader imageLayerLoader) {
            super(FieldLocationIdentity.class);
            this.imageLayerLoader = imageLayerLoader;
        }

        @Override
        public void encode(ObjectCopier.Encoder encoder, ObjectCopierOutputStream stream, Object obj) throws IOException {
            FieldLocationIdentity fieldLocationIdentity = (FieldLocationIdentity) obj;
            int id = encodeField((AnalysisField) fieldLocationIdentity.getField());
            stream.writePackedUnsignedInt(id);
        }

        @Override
        protected Object decode(ObjectCopier.Decoder decoder, Class<?> concreteType, ObjectCopierInputStream stream) throws IOException {
            int id = stream.readPackedUnsignedInt();
            return new FieldLocationIdentity(decodeField(imageLayerLoader, id));
        }
    }

    private static int encodeField(AnalysisField field) {
        field.registerAsTrackedAcrossLayers(TRACKED_REASON);
        return field.getId();
    }

    private static AnalysisField decodeField(SVMImageLayerLoader imageLayerLoader, int id) {
        return imageLayerLoader.getAnalysisFieldForBaseLayerId(id);
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

    public static class HostedToAnalysisTypeDecoderBuiltIn extends AbstractHostedTypeBuiltIn {
        protected HostedToAnalysisTypeDecoderBuiltIn(SVMImageLayerLoader svmImageLayerLoader) {
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

    public static class HostedToAnalysisMethodDecoderBuiltIn extends AbstractHostedMethodBuiltIn {
        protected HostedToAnalysisMethodDecoderBuiltIn(SVMImageLayerLoader svmImageLayerLoader) {
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

    static final class LayeredCGlobalTracking {
        private final InitialLayerCGlobalTracking initialLayerTracking;
        private final AppLayerCGlobalTracking appLayerTracking;

        private LayeredCGlobalTracking(InitialLayerCGlobalTracking initialLayerTracking, AppLayerCGlobalTracking appLayerTracking) {
            this.initialLayerTracking = initialLayerTracking;
            this.appLayerTracking = appLayerTracking;
        }

        int getEncodedIndex(CGlobalDataImpl<?> data) {
            return initialLayerTracking.getEncodedIndex(data);
        }

        CGlobalDataImpl<?> getCGlobalDataImpl(int index) {
            return appLayerTracking.registerOrGetCGlobalDataImplByPersistedIndex(index);
        }

        CGlobalDataInfo getCGlobalDataInfo(int index) {
            return appLayerTracking.registerOrGetCGlobalDataInfoByPersistedIndex(index);
        }
    }

    private static class CGlobalDataImplBuiltIn extends ObjectCopier.Builtin {
        private final LayeredCGlobalTracking cGlobalTracking;

        CGlobalDataImplBuiltIn(LayeredCGlobalTracking cGlobalTracking) {
            super(CGlobalDataImpl.class);
            this.cGlobalTracking = cGlobalTracking;
        }

        @Override
        protected void encode(ObjectCopier.Encoder encoder, ObjectCopierOutputStream stream, Object obj) throws IOException {
            int id = cGlobalTracking.getEncodedIndex((CGlobalDataImpl<?>) obj);
            stream.writePackedUnsignedInt(id);
        }

        @Override
        protected Object decode(ObjectCopier.Decoder decoder, Class<?> concreteType, ObjectCopierInputStream stream) throws IOException {
            int id = stream.readPackedUnsignedInt();
            return cGlobalTracking.getCGlobalDataImpl(id);
        }
    }

    private static class CGlobalDataInfoBuiltIn extends ObjectCopier.Builtin {
        private final LayeredCGlobalTracking cGlobalTracking;

        CGlobalDataInfoBuiltIn(LayeredCGlobalTracking cGlobalTracking) {
            super(CGlobalDataInfo.class);
            this.cGlobalTracking = cGlobalTracking;
        }

        @Override
        protected void encode(ObjectCopier.Encoder encoder, ObjectCopierOutputStream stream, Object obj) throws IOException {
            int id = cGlobalTracking.getEncodedIndex(((CGlobalDataInfo) obj).getData());
            stream.writePackedUnsignedInt(id);
        }

        @Override
        protected Object decode(ObjectCopier.Decoder decoder, Class<?> concreteType, ObjectCopierInputStream stream) throws IOException {
            int id = stream.readPackedUnsignedInt();
            return cGlobalTracking.getCGlobalDataInfo(id);
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
