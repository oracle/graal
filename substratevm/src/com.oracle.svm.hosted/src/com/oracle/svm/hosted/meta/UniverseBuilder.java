/*
 * Copyright (c) 2012, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.meta;

import java.io.Serializable;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinTask;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.graalvm.nativeimage.c.function.CEntryPointLiteral;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.function.CFunctionPointer;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.constraints.UnsupportedFeatures;
import com.oracle.graal.pointsto.infrastructure.ResolvedSignature;
import com.oracle.graal.pointsto.infrastructure.WrappedConstantPool;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.meta.BaseLayerType;
import com.oracle.graal.pointsto.results.StrengthenGraphs;
import com.oracle.svm.common.meta.MultiMethod;
import com.oracle.svm.core.FunctionPointerHolder;
import com.oracle.svm.core.InvalidMethodPointerHandler;
import com.oracle.svm.core.StaticFieldsSupport;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.c.BoxedRelocatedPointer;
import com.oracle.svm.core.c.function.CFunctionOptions;
import com.oracle.svm.core.classinitialization.ClassInitializationInfo;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.config.ObjectLayout;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.heap.ExcludeFromReferenceMap;
import com.oracle.svm.core.heap.FillerArray;
import com.oracle.svm.core.heap.FillerObject;
import com.oracle.svm.core.heap.InstanceReferenceMapEncoder;
import com.oracle.svm.core.heap.ReferenceMapEncoder;
import com.oracle.svm.core.heap.StoredContinuation;
import com.oracle.svm.core.heap.SubstrateReferenceMap;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.DynamicHubSupport;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.meta.MethodPointer;
import com.oracle.svm.core.reflect.SubstrateConstructorAccessor;
import com.oracle.svm.core.reflect.SubstrateMethodAccessor;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.FeatureImpl.BeforeAnalysisAccessImpl;
import com.oracle.svm.hosted.HostedConfiguration;
import com.oracle.svm.hosted.NativeImageOptions;
import com.oracle.svm.hosted.ameta.FieldValueInterceptionSupport;
import com.oracle.svm.hosted.annotation.CustomSubstitutionMethod;
import com.oracle.svm.hosted.classinitialization.ClassInitializationSupport;
import com.oracle.svm.hosted.config.DynamicHubLayout;
import com.oracle.svm.hosted.config.HybridLayout;
import com.oracle.svm.hosted.heap.PodSupport;
import com.oracle.svm.hosted.substitute.AnnotationSubstitutionProcessor;
import com.oracle.svm.hosted.substitute.ComputedValueField;
import com.oracle.svm.hosted.substitute.DeletedMethod;
import com.oracle.svm.util.ReflectionUtil;

import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.Indent;
import jdk.internal.vm.annotation.Contended;
import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.ExceptionHandler;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.UnresolvedJavaType;

public class UniverseBuilder {

    private final AnalysisUniverse aUniverse;
    private final AnalysisMetaAccess aMetaAccess;
    private final HostedUniverse hUniverse;
    private final HostedMetaAccess hMetaAccess;
    private StrengthenGraphs strengthenGraphs;
    private final UnsupportedFeatures unsupportedFeatures;

    public UniverseBuilder(AnalysisUniverse aUniverse, AnalysisMetaAccess aMetaAccess, HostedUniverse hUniverse, HostedMetaAccess hMetaAccess,
                    StrengthenGraphs strengthenGraphs, UnsupportedFeatures unsupportedFeatures) {
        this.aUniverse = aUniverse;
        this.aMetaAccess = aMetaAccess;
        this.hUniverse = hUniverse;
        this.hMetaAccess = hMetaAccess;
        this.strengthenGraphs = strengthenGraphs;
        this.unsupportedFeatures = unsupportedFeatures;
    }

    /**
     * This step is single threaded, i.e., all the maps are modified only by a single thread, so no
     * synchronization is necessary. Accesses (the lookup methods) are multi-threaded.
     */
    @SuppressWarnings("try")
    public void build(DebugContext debug) {
        for (AnalysisField aField : aUniverse.getFields()) {
            if (aField.wrapped instanceof ComputedValueField) {
                ((ComputedValueField) aField.wrapped).processAnalysis(aMetaAccess);
            }
        }
        aUniverse.seal();

        try (Indent indent = debug.logAndIndent("build universe")) {
            for (AnalysisType aType : aUniverse.getTypes()) {
                makeType(aType);
            }
            for (AnalysisType aType : aUniverse.getTypes()) {
                checkHierarchyForTypeReachedConstraints(aType);
            }
            for (AnalysisField aField : aUniverse.getFields()) {
                makeField(aField);
            }
            for (AnalysisMethod aMethod : aUniverse.getMethods()) {
                assert aMethod.isOriginalMethod();
                Collection<MultiMethod> allMethods = aMethod.getAllMultiMethods();
                HostedMethod origHMethod = null;
                if (allMethods.size() == 1) {
                    origHMethod = makeMethod(aMethod);
                } else {
                    ConcurrentHashMap<MultiMethod.MultiMethodKey, MultiMethod> multiMethodMap = new ConcurrentHashMap<>();
                    for (MultiMethod method : aMethod.getAllMultiMethods()) {
                        HostedMethod hMethod = makeMethod((AnalysisMethod) method);
                        hMethod.setMultiMethodMap(multiMethodMap);
                        MultiMethod previous = multiMethodMap.put(hMethod.getMultiMethodKey(), hMethod);
                        assert previous == null : "Overwriting multimethod key";
                        if (method.equals(aMethod)) {
                            origHMethod = hMethod;
                        }
                    }
                }
                assert origHMethod != null;
                HostedMethod previous = hUniverse.methods.put(aMethod, origHMethod);
                assert previous == null : "Overwriting analysis key";
            }

            HostedConfiguration.initializeDynamicHubLayout(hMetaAccess);

            Collection<HostedType> allTypes = hUniverse.types.values();
            HostedType objectType = hUniverse.objectType();
            HostedType cloneableType = hUniverse.types.get(aMetaAccess.lookupJavaType(Cloneable.class));
            HostedType serializableType = hUniverse.types.get(aMetaAccess.lookupJavaType(Serializable.class));
            int numTypeCheckSlots = TypeCheckBuilder.buildTypeMetadata(hUniverse, allTypes, objectType, cloneableType, serializableType);

            collectDeclaredMethods();
            collectMonitorFieldInfo(aUniverse.getBigbang());

            ForkJoinTask<?> profilingInformationBuildTask = ForkJoinTask.adapt(this::buildProfilingInformation).fork();

            layoutInstanceFields(numTypeCheckSlots);
            layoutStaticFields();

            collectMethodImplementations();
            VTableBuilder.buildTables(hUniverse, hMetaAccess);
            buildHubs();

            processFieldLocations();

            hUniverse.orderedMethods = new ArrayList<>(hUniverse.methods.values());
            Collections.sort(hUniverse.orderedMethods, HostedUniverse.METHOD_COMPARATOR);
            hUniverse.orderedFields = new ArrayList<>(hUniverse.fields.values());
            Collections.sort(hUniverse.orderedFields, HostedUniverse.FIELD_COMPARATOR_RELAXED);
            profilingInformationBuildTask.join();
        }
    }

    private HostedType lookupType(AnalysisType aType) {
        return Objects.requireNonNull(hUniverse.types.get(aType));
    }

    private HostedType makeType(AnalysisType aType) {
        if (aType == null) {
            return null;
        }

        HostedType hType = hUniverse.types.get(aType);
        if (hType != null) {
            return hType;
        }

        String typeName = aType.getName();

        assert SubstrateUtil.isBuildingLibgraal() || !typeName.contains("/hotspot/") || typeName.contains("/jtt/hotspot/") || typeName.contains("/hotspot/shared/") : "HotSpot object in image " +
                        typeName;
        assert !typeName.contains("/analysis/meta/") : "Analysis meta object in image " + typeName;
        assert !typeName.contains("/hosted/meta/") : "Hosted meta object in image " + typeName;

        AnalysisType[] aInterfaces = aType.getInterfaces();
        HostedInterface[] sInterfaces = new HostedInterface[aInterfaces.length];
        for (int i = 0; i < aInterfaces.length; i++) {
            sInterfaces[i] = (HostedInterface) makeType(aInterfaces[i]);
        }

        JavaKind kind = aType.getJavaKind();
        JavaKind storageKind = aType.getStorageKind();

        if (aType.getJavaKind() != JavaKind.Object) {
            assert !aType.isInterface() && !aType.isInstanceClass() && !aType.isArray();
            hType = new HostedPrimitiveType(hUniverse, aType, kind, storageKind);
            hUniverse.kindToType.put(hType.getJavaKind(), hType);

        } else if (aType.isInterface()) {
            assert !aType.isInstanceClass() && !aType.isArray();
            hType = new HostedInterface(hUniverse, aType, kind, storageKind, sInterfaces);

        } else if (aType.isInstanceClass()) {
            assert !aType.isInterface() && !aType.isArray();
            HostedInstanceClass superClass = (HostedInstanceClass) makeType(aType.getSuperclass());
            hType = new HostedInstanceClass(hUniverse, aType, kind, storageKind, superClass, sInterfaces);

            if (superClass == null) {
                hUniverse.kindToType.put(JavaKind.Object, hType);
            }

        } else if (aType.isArray()) {
            assert !aType.isInterface() && !aType.isInstanceClass();
            HostedClass superType = (HostedClass) makeType(aType.getSuperclass());
            HostedType componentType = makeType(aType.getComponentType());

            hType = new HostedArrayClass(hUniverse, aType, kind, storageKind, superType, sInterfaces, componentType);

        } else {
            throw VMError.shouldNotReachHereUnexpectedInput(aType); // ExcludeFromJacocoGeneratedReport
        }

        HostedType existing = hUniverse.types.put(aType, hType);
        if (existing != null) {
            throw VMError.shouldNotReachHere("Overwriting existing type: " + hType + " != " + existing);
        }

        DynamicHub hub = hType.getHub();
        Class<?> hostedJavaClass = hub.getHostedJavaClass();
        AnalysisType aTypeChecked = aMetaAccess.lookupJavaType(hostedJavaClass);
        HostedType hTypeChecked = hMetaAccess.lookupJavaType(hostedJavaClass);
        if ((!sameObject(aType, aTypeChecked) || !sameObject(hTypeChecked, hType)) && !(aType.getWrapped() instanceof BaseLayerType)) {
            throw VMError.shouldNotReachHere("Type mismatch when performing round-trip HostedType/AnalysisType -> DynamicHub -> java.lang.Class -> HostedType/AnalysisType: " + System.lineSeparator() +
                            hType + " @ " + Integer.toHexString(System.identityHashCode(hType)) +
                            " / " + aType + " @ " + Integer.toHexString(System.identityHashCode(aType)) + System.lineSeparator() +
                            " -> " + hub + " -> " + hostedJavaClass + System.lineSeparator() +
                            " -> " + hTypeChecked + " @ " + Integer.toHexString(System.identityHashCode(hTypeChecked)) +
                            " / " + aTypeChecked + " @ " + Integer.toHexString(System.identityHashCode(aTypeChecked)));
        }

        /*
         * Mark all types whose subtype is marked as --initialize-at-build-time types as reached. We
         * need this as interfaces without default methods are not transitively initialized at build
         * time by their subtypes.
         */
        if (hType.wrapped.isReachable() &&
                        ClassInitializationSupport.singleton().maybeInitializeAtBuildTime(hostedJavaClass) &&
                        hub.getClassInitializationInfo().getTypeReached() == ClassInitializationInfo.TypeReached.NOT_REACHED) {
            hType.wrapped.forAllSuperTypes(t -> {
                var superHub = hUniverse.hostVM().dynamicHub(t);
                if (superHub.getClassInitializationInfo().getTypeReached() == ClassInitializationInfo.TypeReached.NOT_REACHED) {
                    superHub.getClassInitializationInfo().setTypeReached();
                }
            });
        }
        return hType;
    }

    /**
     * The {@link ClassInitializationInfo#getTypeReached()} for each super-type hub must have a
     * value whose ordinal is greater or equal to its own value.
     */
    private void checkHierarchyForTypeReachedConstraints(AnalysisType type) {
        if (type.isReachable()) {
            var hub = hUniverse.hostVM().dynamicHub(type);
            if (type.getSuperclass() != null) {
                checkSuperHub(hub, hub.getSuperHub());
            }

            for (AnalysisType superInterface : type.getInterfaces()) {
                checkSuperHub(hub, hUniverse.hostVM().dynamicHub(superInterface));
            }
        }
    }

    private static void checkSuperHub(DynamicHub hub, DynamicHub superTypeHub) {
        ClassInitializationInfo.TypeReached typeReached = hub.getClassInitializationInfo().getTypeReached();
        ClassInitializationInfo.TypeReached superTypeReached = superTypeHub.getClassInitializationInfo().getTypeReached();
        VMError.guarantee(superTypeReached.ordinal() >= typeReached.ordinal(),
                        "Super type of a type must have type reached >= than the type: %s is %s but %s is %s", hub.getName(), typeReached, superTypeHub.getName(), superTypeReached);
    }

    /*
     * Normally types need to be compared with equals, and there is a gate check enforcing this.
     * Using a separate method hides the comparison from the checker.
     */
    private static boolean sameObject(Object x, Object y) {
        return x == y;
    }

    private HostedMethod makeMethod(AnalysisMethod aMethod) {
        AnalysisType aDeclaringClass = aMethod.getDeclaringClass();
        HostedType hDeclaringClass = lookupType(aDeclaringClass);
        ResolvedSignature<HostedType> signature = makeSignature(aMethod.getSignature());
        ConstantPool constantPool = makeConstantPool(aMethod.getConstantPool(), aDeclaringClass);

        ExceptionHandler[] aHandlers = aMethod.getExceptionHandlers();
        ExceptionHandler[] sHandlers = new ExceptionHandler[aHandlers.length];
        for (int i = 0; i < aHandlers.length; i++) {
            ExceptionHandler h = aHandlers[i];
            JavaType catchType = h.getCatchType();
            if (h.getCatchType() instanceof AnalysisType) {
                catchType = lookupType((AnalysisType) catchType);
            } else {
                assert catchType == null || catchType instanceof UnresolvedJavaType;
            }
            sHandlers[i] = new ExceptionHandler(h.getStartBCI(), h.getEndBCI(), h.getHandlerBCI(), h.catchTypeCPI(), catchType);
        }

        HostedMethod hMethod = HostedMethod.create(hUniverse, aMethod, hDeclaringClass, signature, constantPool, sHandlers);

        boolean isCFunction = aMethod.getAnnotation(CFunction.class) != null;
        boolean hasCFunctionOptions = aMethod.getAnnotation(CFunctionOptions.class) != null;
        if (hasCFunctionOptions && !isCFunction) {
            unsupportedFeatures.addMessage(aMethod.format("%H.%n(%p)"), aMethod,
                            "Method annotated with @" + CFunctionOptions.class.getSimpleName() + " must also be annotated with @" + CFunction.class);
        }

        if (isCFunction) {
            if (!aMethod.isNative()) {
                unsupportedFeatures.addMessage(aMethod.format("%H.%n(%p)"), aMethod,
                                "Method annotated with @" + CFunction.class.getSimpleName() + " must be declared native");
            }
        } else if (aMethod.isNative() && !aMethod.isIntrinsicMethod() && !(aMethod.getWrapped() instanceof CustomSubstitutionMethod) &&
                        aMethod.isImplementationInvoked() && !NativeImageOptions.ReportUnsupportedElementsAtRuntime.getValue()) {
            unsupportedFeatures.addMessage(aMethod.format("%H.%n(%p)"), aMethod, AnnotationSubstitutionProcessor.deleteErrorMessage(aMethod, DeletedMethod.NATIVE_MESSAGE, true));
        }

        return hMethod;
    }

    private ResolvedSignature<HostedType> makeSignature(ResolvedSignature<AnalysisType> aSignature) {
        ResolvedSignature<HostedType> hSignature = hUniverse.signatures.get(aSignature);
        if (hSignature == null) {
            HostedType[] paramTypes = new HostedType[aSignature.getParameterCount(false)];
            for (int i = 0; i < paramTypes.length; i++) {
                paramTypes[i] = lookupType(aSignature.getParameterType(i));
            }
            HostedType returnType = lookupType(aSignature.getReturnType());

            hSignature = ResolvedSignature.fromArray(paramTypes, returnType);
            hUniverse.signatures.put(aSignature, hSignature);
        }
        return hSignature;
    }

    private ConstantPool makeConstantPool(ConstantPool aConstantPool, AnalysisType aDefaultAccessingClass) {
        WrappedConstantPool hConstantPool = hUniverse.constantPools.get(aConstantPool);
        if (hConstantPool == null) {
            hConstantPool = new WrappedConstantPool(hUniverse, aConstantPool, aDefaultAccessingClass);
            hUniverse.constantPools.put(aConstantPool, hConstantPool);
        }
        return hConstantPool;
    }

    private void makeField(AnalysisField aField) {
        HostedType holder = lookupType(aField.getDeclaringClass());
        /*
         * If the field is never written, or only assigned null, then we might not have a type for
         * it yet.
         */
        HostedType type = lookupType(aField.getType());

        HostedField hField = new HostedField(aField, holder, type);
        assert !hUniverse.fields.containsKey(aField);
        hUniverse.fields.put(aField, hField);
    }

    private void buildProfilingInformation() {
        /* Convert profiling information after all types and methods have been created. */
        hUniverse.methods.values().parallelStream()
                        .forEach(method -> {
                            assert method.isOriginalMethod();
                            for (MultiMethod multiMethod : method.getAllMultiMethods()) {
                                HostedMethod hMethod = (HostedMethod) multiMethod;
                                strengthenGraphs.applyResults(hMethod.getWrapped());
                            }
                        });

        strengthenGraphs = null;
    }

    /**
     * We want these types to be immutable so that they can be in the read-only part of the image
     * heap. Those types that contain relocatable pointers *must* be in the read-only relocatables
     * partition of the image heap. Immutable types will not get a monitor field and will always use
     * the secondary storage for monitor slots.
     */
    private static final Set<Class<?>> IMMUTABLE_TYPES = new HashSet<>(Arrays.asList(
                    String.class,
                    DynamicHub.class,
                    CEntryPointLiteral.class,
                    BoxedRelocatedPointer.class,
                    FunctionPointerHolder.class,
                    StoredContinuation.class,
                    SubstrateMethodAccessor.class,
                    SubstrateConstructorAccessor.class,
                    FillerObject.class,
                    FillerArray.class));

    private void collectMonitorFieldInfo(BigBang bb) {
        HostedConfiguration.instance().collectMonitorFieldInfo(bb, hUniverse, getImmutableTypes());
    }

    private Set<AnalysisType> getImmutableTypes() {
        Set<AnalysisType> immutableTypes = new HashSet<>();
        for (Class<?> immutableType : IMMUTABLE_TYPES) {
            Optional<AnalysisType> aType = aMetaAccess.optionalLookupJavaType(immutableType);
            aType.ifPresent(immutableTypes::add);
        }
        return immutableTypes;
    }

    public static boolean isKnownImmutableType(Class<?> clazz) {
        return IMMUTABLE_TYPES.contains(clazz);
    }

    private void layoutInstanceFields(int numTypeCheckSlots) {
        BitSet usedBytes = new BitSet();
        usedBytes.set(0, ConfigurationValues.getObjectLayout().getFirstFieldOffset());
        layoutInstanceFields(hUniverse.getObjectClass(), new HostedField[0], usedBytes, numTypeCheckSlots);
    }

    private static boolean mustReserveArrayFields(HostedInstanceClass clazz) {
        if (PodSupport.isPresent() && PodSupport.singleton().mustReserveArrayFields(clazz.getJavaClass())) {
            return true;
        }
        if (HybridLayout.isHybrid(clazz)) {
            // A pod ancestor subclassing Object must have already reserved memory for the array
            // fields, unless the pod subclasses Object itself, in which case we would have returned
            // true earlier.
            return !PodSupport.isPresent() || !PodSupport.singleton().isPodClass(clazz.getJavaClass());
        }
        return false;
    }

    private static void reserve(BitSet usedBytes, int offset, int size) {
        int offsetAfter = offset + size;
        assert usedBytes.previousSetBit(offsetAfter - 1) < offset; // (also matches -1)
        usedBytes.set(offset, offsetAfter);
    }

    private static void reserveAtEnd(BitSet usedBytes, int size) {
        int endOffset = usedBytes.length();
        usedBytes.set(endOffset, endOffset + size);
    }

    private void layoutInstanceFields(HostedInstanceClass clazz, HostedField[] superFields, BitSet usedBytes, int numTypeCheckSlots) {
        ArrayList<HostedField> rawFields = new ArrayList<>();
        ArrayList<HostedField> allFields = new ArrayList<>();
        ObjectLayout layout = ConfigurationValues.getObjectLayout();

        HostedConfiguration.instance().findAllFieldsForLayout(hUniverse, hMetaAccess, hUniverse.fields, rawFields, allFields, clazz);

        int firstInstanceFieldOffset;
        int minimumFirstFieldOffset = layout.getFirstFieldOffset();
        DynamicHubLayout dynamicHubLayout = DynamicHubLayout.singleton();
        if (dynamicHubLayout.isDynamicHub(clazz)) {
            /*
             * Reserve the vtable and typeslots
             */
            int intSize = layout.sizeInBytes(JavaKind.Int);
            int afterVTableLengthOffset = dynamicHubLayout.getVTableLengthOffset() + intSize;

            /*
             * Reserve the extra memory that DynamicHub fields may use (at least the vtable length
             * field).
             */
            int fieldBytes = afterVTableLengthOffset - minimumFirstFieldOffset;
            assert fieldBytes >= intSize;
            reserve(usedBytes, minimumFirstFieldOffset, fieldBytes);

            if (SubstrateOptions.closedTypeWorld()) {
                /* Each type check id slot is 2 bytes. */
                assert numTypeCheckSlots != TypeCheckBuilder.UNINITIALIZED_TYPECHECK_SLOTS : "numTypeCheckSlots is uninitialized";
                int slotsSize = numTypeCheckSlots * 2;
                int typeIDSlotsBaseOffset = dynamicHubLayout.getClosedTypeWorldTypeCheckSlotsOffset();
                reserve(usedBytes, typeIDSlotsBaseOffset, slotsSize);
                firstInstanceFieldOffset = typeIDSlotsBaseOffset + slotsSize;
            } else {
                /*
                 * In the open world we do not inline the type checks into the dynamic hub since the
                 * typeIDSlots array will be of variable length.
                 */
                firstInstanceFieldOffset = afterVTableLengthOffset;
            }

        } else if (mustReserveArrayFields(clazz)) {
            int intSize = layout.sizeInBytes(JavaKind.Int);
            int afterArrayLengthOffset = layout.getArrayLengthOffset() + intSize;
            firstInstanceFieldOffset = afterArrayLengthOffset;

            /*
             * Reserve the extra memory that array fields may use (at least the array length field).
             */
            int arrayFieldBytes = afterArrayLengthOffset - minimumFirstFieldOffset;
            assert arrayFieldBytes >= intSize;
            reserve(usedBytes, minimumFirstFieldOffset, arrayFieldBytes);
        } else {
            firstInstanceFieldOffset = minimumFirstFieldOffset;
        }

        /*
         * Group fields annotated @Contended(tag) by their tag, where a tag of "" implies a group
         * for each individual field. Each group gets padded at the beginning and end to avoid false
         * sharing. Unannotated fields are placed in a separate group which is not padded unless the
         * class itself is annotated @Contended.
         *
         * Sort so that in each group, Object fields are consecutive, and bigger types come first.
         */
        Object uncontendedSentinel = new Object();
        Object unannotatedGroup = clazz.isAnnotationPresent(Contended.class) ? new Object() : uncontendedSentinel;
        Function<HostedField, Object> getAnnotationGroup = field -> Optional.ofNullable(field.getAnnotation(Contended.class))
                        .map(a -> "".equals(a.value()) ? new Object() : a.value())
                        .orElse(unannotatedGroup);
        Map<Object, ArrayList<HostedField>> contentionGroups = rawFields.stream()
                        .sorted(HostedUniverse.FIELD_COMPARATOR_RELAXED)
                        .collect(Collectors.groupingBy(getAnnotationGroup, Collectors.toCollection(ArrayList::new)));

        ArrayList<HostedField> uncontendedFields = contentionGroups.remove(uncontendedSentinel);
        if (uncontendedFields != null) {
            assert !uncontendedFields.isEmpty();
            placeFields(uncontendedFields, usedBytes, 0, layout);
        }

        for (ArrayList<HostedField> groupFields : contentionGroups.values()) {
            reserveAtEnd(usedBytes, getContendedPadding());
            int firstOffset = usedBytes.length();
            placeFields(groupFields, usedBytes, firstOffset, layout);
            usedBytes.set(firstOffset, usedBytes.length()); // prevent subclass fields
        }

        if (!contentionGroups.isEmpty()) {
            reserveAtEnd(usedBytes, getContendedPadding());
        }

        BitSet usedBytesInSubclasses = null;
        if (clazz.subTypes.length != 0) {
            usedBytesInSubclasses = (BitSet) usedBytes.clone();
        }

        // Reserve "synthetic" fields in this class (but not subclasses) below.

        // A reference to a {@link java.util.concurrent.locks.ReentrantLock for "synchronized" or
        // Object.wait() and Object.notify() and friends.
        if (clazz.needMonitorField()) {
            int size = layout.getReferenceSize();
            int endOffset = usedBytes.length();
            int offset = findGapForField(usedBytes, 0, size, endOffset);
            if (offset == -1) {
                offset = endOffset + getAlignmentAdjustment(endOffset, size);
            }
            reserve(usedBytes, offset, size);
            clazz.setMonitorFieldOffset(offset);
        }

        /*
         * This sequence of fields is returned by ResolvedJavaType.getInstanceFields, which requires
         * them to in "natural order", i.e., sorted by location (offset).
         */
        allFields.sort(FIELD_LOCATION_COMPARATOR);

        int afterFieldsOffset = usedBytes.length();

        // Identity hash code
        if (clazz.isAbstract()) {
            /* No identity hash field needed. */
        } else if (layout.isIdentityHashFieldInObjectHeader()) {
            clazz.setIdentityHashOffset(layout.getObjectHeaderIdentityHashOffset());
        } else if (HostedConfiguration.isArrayLikeLayout(clazz)) {
            if (layout.isIdentityHashFieldAtTypeSpecificOffset()) {
                clazz.setIdentityHashOffset(layout.getObjectHeaderIdentityHashOffset());
            } else {
                /* Nothing to do - will be treated like an array. */
            }
        } else if (layout.isIdentityHashFieldAtTypeSpecificOffset() || layout.isIdentityHashFieldOptional()) {
            /* Add a synthetic field (in gap if any, or append). */
            int hashSize = Integer.BYTES;
            int endOffset = usedBytes.length();
            int offset = findGapForField(usedBytes, 0, hashSize, endOffset);
            if (offset == -1) {
                offset = endOffset + getAlignmentAdjustment(endOffset, hashSize);
                if (layout.isIdentityHashFieldAtTypeSpecificOffset()) {
                    /* Include the identity hashcode field in the instance size. */
                    afterFieldsOffset = offset + hashSize;
                }
            }
            reserve(usedBytes, offset, hashSize);
            clazz.setIdentityHashOffset(offset);
        }

        clazz.instanceFieldsWithoutSuper = allFields.toArray(new HostedField[0]);
        clazz.firstInstanceFieldOffset = firstInstanceFieldOffset;
        clazz.afterFieldsOffset = afterFieldsOffset;
        clazz.instanceSize = layout.alignUp(afterFieldsOffset);

        if (clazz.instanceFieldsWithoutSuper.length == 0) {
            clazz.instanceFieldsWithSuper = superFields;
        } else if (superFields.length == 0) {
            clazz.instanceFieldsWithSuper = clazz.instanceFieldsWithoutSuper;
        } else {
            HostedField[] instanceFieldsWithSuper = Arrays.copyOf(superFields, superFields.length + clazz.instanceFieldsWithoutSuper.length);
            System.arraycopy(clazz.instanceFieldsWithoutSuper, 0, instanceFieldsWithSuper, superFields.length, clazz.instanceFieldsWithoutSuper.length);
            // Fields must be sorted by location, and we might have put a field between super fields
            Arrays.sort(instanceFieldsWithSuper, FIELD_LOCATION_COMPARATOR);
            clazz.instanceFieldsWithSuper = instanceFieldsWithSuper;
        }

        for (HostedType subClass : clazz.subTypes) {
            if (subClass.isInstanceClass()) {
                layoutInstanceFields((HostedInstanceClass) subClass, clazz.instanceFieldsWithSuper, (BitSet) usedBytesInSubclasses.clone(), numTypeCheckSlots);
            }
        }
    }

    private static final Comparator<HostedField> FIELD_LOCATION_COMPARATOR = (a, b) -> {
        if (!a.hasLocation() || !b.hasLocation()) { // hybrid fields
            return Boolean.compare(a.hasLocation(), b.hasLocation());
        }
        return Integer.compare(a.getLocation(), b.getLocation());
    };

    private static int getContendedPadding() {
        Integer value = SubstrateOptions.ContendedPaddingWidth.getValue();
        return (value > 0) ? value : 0; // no alignment required, placing fields takes care of it
    }

    private static void placeFields(ArrayList<HostedField> fields, BitSet usedBytes, int minOffset, ObjectLayout layout) {
        int lastSearchSize = -1;
        boolean lastSearchSuccess = false;
        for (HostedField field : fields) {
            int fieldSize = layout.sizeInBytes(field.getStorageKind());
            int offset = -1;
            int endOffset = usedBytes.length();
            if (lastSearchSuccess || lastSearchSize != fieldSize) {
                offset = findGapForField(usedBytes, minOffset, fieldSize, endOffset);
                lastSearchSuccess = (offset != -1);
                lastSearchSize = fieldSize;
            }
            if (offset == -1) {
                offset = endOffset + getAlignmentAdjustment(endOffset, fieldSize);
            }
            reserve(usedBytes, offset, fieldSize);
            field.setLocation(offset);
        }
    }

    private static int findGapForField(BitSet usedBytes, int minOffset, int fieldSize, int endOffset) {
        int candidateOffset = -1;
        int candidateSize = -1;
        int offset = usedBytes.nextClearBit(minOffset);
        while (offset < endOffset) {
            int size = usedBytes.nextSetBit(offset + 1) - offset;
            int adjustment = getAlignmentAdjustment(offset, fieldSize);
            if (size >= adjustment + fieldSize) { // fit
                if (candidateOffset == -1 || size < candidateSize) { // better fit
                    candidateOffset = offset + adjustment;
                    candidateSize = size;
                }
            }
            offset = usedBytes.nextClearBit(offset + size);
        }
        return candidateOffset;
    }

    private static int getAlignmentAdjustment(int offset, int alignment) {
        int bits = alignment - 1;
        assert (alignment & bits) == 0 : "expecting power of 2";
        int alignedOffset = (offset + bits) & ~bits;
        return alignedOffset - offset;
    }

    /**
     * Determines whether a static field does not need to be written to the native-image heap.
     */
    private static boolean skipStaticField(HostedField field) {
        if (field.wrapped.isWritten() || MaterializedConstantFields.singleton().contains(field.wrapped)) {
            return false;
        }

        if (!field.wrapped.isAccessed()) {
            // if the field is never accessed then it does not need to be materialized
            return true;
        }

        /*
         * The field can be treated as a constant. Check if constant is available.
         */

        var interceptor = field.getWrapped().getFieldValueInterceptor();
        if (interceptor == null) {
            return true;
        }

        boolean available = field.isValueAvailable();
        if (!available) {
            /*
             * Since the value is not yet available we must register it as a
             * MaterializedConstantField. Note the field may be constant folded at a later point
             * when the value becomes available. However, at this phase of the image building
             * process this is not determinable.
             */
            MaterializedConstantFields.singleton().register(field.wrapped);
        }

        return available;
    }

    private void layoutStaticFields() {
        ArrayList<HostedField> fields = new ArrayList<>();
        for (HostedField field : hUniverse.fields.values()) {
            if (Modifier.isStatic(field.getModifiers())) {
                fields.add(field);
            }
        }

        // Sort so that a) all Object fields are consecutive, and b) bigger types come first.
        Collections.sort(fields, HostedUniverse.FIELD_COMPARATOR_RELAXED);

        ObjectLayout layout = ConfigurationValues.getObjectLayout();

        int nextPrimitiveField = 0;
        int nextObjectField = 0;

        @SuppressWarnings("unchecked")
        List<HostedField>[] fieldsOfTypes = (List<HostedField>[]) new ArrayList<?>[DynamicHubSupport.singleton().getMaxTypeId()];

        for (HostedField field : fields) {
            if (skipStaticField(field)) {
                // does not require memory.
            } else if (field.wrapped.isInBaseLayer()) {
                field.setLocation(aUniverse.getImageLayerLoader().getFieldLocation(field.wrapped));
            } else if (field.getStorageKind() == JavaKind.Object) {
                field.setLocation(NumUtil.safeToInt(layout.getArrayElementOffset(JavaKind.Object, nextObjectField)));
                nextObjectField += 1;
            } else {
                int fieldSize = layout.sizeInBytes(field.getStorageKind());
                while (layout.getArrayElementOffset(JavaKind.Byte, nextPrimitiveField) % fieldSize != 0) {
                    // Insert padding byte for alignment
                    nextPrimitiveField++;
                }
                field.setLocation(NumUtil.safeToInt(layout.getArrayElementOffset(JavaKind.Byte, nextPrimitiveField)));
                nextPrimitiveField += fieldSize;
            }

            int typeId = field.getDeclaringClass().getTypeID();
            if (fieldsOfTypes[typeId] == null) {
                fieldsOfTypes[typeId] = new ArrayList<>();
            }
            fieldsOfTypes[typeId].add(field);
        }

        HostedField[] noFields = new HostedField[0];
        for (HostedType type : hUniverse.getTypes()) {
            List<HostedField> fieldsOfType = fieldsOfTypes[type.getTypeID()];
            if (fieldsOfType != null) {
                type.staticFields = fieldsOfType.toArray(new HostedField[fieldsOfType.size()]);
            } else {
                type.staticFields = noFields;
            }
        }

        Object[] staticObjectFields = new Object[nextObjectField];
        byte[] staticPrimitiveFields = new byte[nextPrimitiveField];
        StaticFieldsSupport.setData(staticObjectFields, staticPrimitiveFields);
        /* After initializing the static field arrays add them to the shadow heap. */
        aUniverse.getHeapScanner().rescanObject(StaticFieldsSupport.getStaticObjectFields());
        aUniverse.getHeapScanner().rescanObject(StaticFieldsSupport.getStaticPrimitiveFields());
    }

    @SuppressWarnings("unchecked")
    private void collectDeclaredMethods() {
        List<HostedMethod>[] methodsOfType = (ArrayList<HostedMethod>[]) new ArrayList<?>[DynamicHubSupport.singleton().getMaxTypeId()];
        for (HostedMethod method : hUniverse.methods.values()) {
            int typeId = method.getDeclaringClass().getTypeID();
            List<HostedMethod> list = methodsOfType[typeId];
            if (list == null) {
                list = new ArrayList<>();
                methodsOfType[typeId] = list;
            }
            list.add(method);
        }

        for (HostedType type : hUniverse.getTypes()) {
            List<HostedMethod> list = methodsOfType[type.getTypeID()];
            if (list != null) {
                Collections.sort(list, HostedUniverse.METHOD_COMPARATOR);
                type.allDeclaredMethods = list.toArray(HostedMethod.EMPTY_ARRAY);
            } else {
                type.allDeclaredMethods = HostedMethod.EMPTY_ARRAY;
            }
        }
    }

    private void collectMethodImplementations() {
        for (HostedMethod method : hUniverse.methods.values()) {

            // Reuse the implementations from the analysis method.
            method.implementations = hUniverse.lookup(method.wrapped.collectMethodImplementations(false).toArray(new AnalysisMethod[0]));
            Arrays.sort(method.implementations, HostedUniverse.METHOD_COMPARATOR);
        }
    }

    private void buildHubs() {
        InstanceReferenceMapEncoder referenceMapEncoder = new InstanceReferenceMapEncoder();
        Map<HostedType, ReferenceMapEncoder.Input> referenceMaps = new HashMap<>();
        for (HostedType type : hUniverse.getTypes()) {
            ReferenceMapEncoder.Input referenceMap = createReferenceMap(type);
            assert ((SubstrateReferenceMap) referenceMap).hasNoDerivedOffsets();
            referenceMaps.put(type, referenceMap);
            referenceMapEncoder.add(referenceMap);
        }
        DynamicHubSupport.singleton().setReferenceMapEncoding(referenceMapEncoder.encodeAll());

        ObjectLayout ol = ConfigurationValues.getObjectLayout();
        DynamicHubLayout dynamicHubLayout = DynamicHubLayout.singleton();

        for (HostedType type : hUniverse.getTypes()) {
            hUniverse.hostVM().recordActivity();

            int layoutHelper;
            boolean canUnsafeInstantiateAsInstance = false;
            int monitorOffset = 0;
            int identityHashOffset = 0;
            if (type.isInstanceClass()) {
                HostedInstanceClass instanceClass = (HostedInstanceClass) type;
                if (instanceClass.isAbstract()) {
                    layoutHelper = LayoutEncoding.forAbstract();
                } else if (dynamicHubLayout.isDynamicHub(type)) {
                    layoutHelper = LayoutEncoding.forDynamicHub(type, dynamicHubLayout.vTableOffset(), ol.getArrayIndexShift(dynamicHubLayout.getVTableSlotStorageKind()));
                } else if (HybridLayout.isHybrid(type)) {
                    HybridLayout hybridLayout = new HybridLayout(instanceClass, ol, hMetaAccess);
                    JavaKind storageKind = hybridLayout.getArrayElementStorageKind();
                    boolean isObject = (storageKind == JavaKind.Object);
                    layoutHelper = LayoutEncoding.forHybrid(type, isObject, hybridLayout.getArrayBaseOffset(), ol.getArrayIndexShift(storageKind));
                    canUnsafeInstantiateAsInstance = type.wrapped.isUnsafeAllocated() && HybridLayout.canInstantiateAsInstance(type);
                } else {
                    layoutHelper = LayoutEncoding.forPureInstance(type, ConfigurationValues.getObjectLayout().alignUp(instanceClass.getInstanceSize()));
                    canUnsafeInstantiateAsInstance = type.wrapped.isUnsafeAllocated();
                }
                monitorOffset = instanceClass.getMonitorFieldOffset();
                identityHashOffset = instanceClass.getIdentityHashOffset();
            } else if (type.isArray()) {
                JavaKind storageKind = type.getComponentType().getStorageKind();
                boolean isObject = (storageKind == JavaKind.Object);
                layoutHelper = LayoutEncoding.forArray(type, isObject, ol.getArrayBaseOffset(storageKind), ol.getArrayIndexShift(storageKind));
                if (ol.isIdentityHashFieldInObjectHeader() || ol.isIdentityHashFieldAtTypeSpecificOffset()) {
                    identityHashOffset = NumUtil.safeToInt(ol.getObjectHeaderIdentityHashOffset());
                }
            } else if (type.isInterface()) {
                layoutHelper = LayoutEncoding.forInterface();
            } else if (type.isPrimitive()) {
                layoutHelper = LayoutEncoding.forPrimitive();
            } else {
                throw VMError.shouldNotReachHereUnexpectedInput(type); // ExcludeFromJacocoGeneratedReport
            }

            // pointer maps in Dynamic Hub
            ReferenceMapEncoder.Input referenceMap = referenceMaps.get(type);
            assert referenceMap != null;
            assert ((SubstrateReferenceMap) referenceMap).hasNoDerivedOffsets();
            long referenceMapIndex = referenceMapEncoder.lookupEncoding(referenceMap);

            DynamicHub hub = type.getHub();
            hub.setSharedData(layoutHelper, monitorOffset, identityHashOffset,
                            referenceMapIndex, type.isInstantiated(), canUnsafeInstantiateAsInstance);

            if (SubstrateOptions.closedTypeWorld()) {
                CFunctionPointer[] vtable = new CFunctionPointer[type.closedTypeWorldVTable.length];
                for (int idx = 0; idx < type.closedTypeWorldVTable.length; idx++) {
                    /*
                     * We install a CodePointer in the vtable; when generating relocation info, we
                     * will know these point into .text
                     */
                    vtable[idx] = new MethodPointer(type.closedTypeWorldVTable[idx]);
                }
                hub.setClosedTypeWorldData(vtable, type.getTypeID(), type.getTypeCheckStart(), type.getTypeCheckRange(),
                                type.getTypeCheckSlot(), type.getClosedTypeWorldTypeCheckSlots());
            } else {

                /*
                 * Within the open type world, interface type checks are two entries long and
                 * contain information about both the implemented interface ids as well as their
                 * itable starting offset within the dispatch table.
                 */
                int numClassTypes = type.getNumClassTypes();
                int[] openTypeWorldTypeCheckSlots = new int[numClassTypes + (type.getNumInterfaceTypes() * 2)];
                System.arraycopy(type.openTypeWorldTypeCheckSlots, 0, openTypeWorldTypeCheckSlots, 0, numClassTypes);
                int typeSlotIdx = numClassTypes;
                for (int interfaceIdx = 0; interfaceIdx < type.numInterfaceTypes; interfaceIdx++) {
                    int typeID = type.getOpenTypeWorldTypeCheckSlots()[numClassTypes + interfaceIdx];
                    int itableStartingOffset;
                    if (type.itableStartingOffsets.length > 0) {
                        itableStartingOffset = type.itableStartingOffsets[interfaceIdx];
                    } else {
                        itableStartingOffset = 0xBADD0D1D;
                    }
                    openTypeWorldTypeCheckSlots[typeSlotIdx] = typeID;
                    /*
                     * We directly encode the offset of the itable within the DynamicHub to limit
                     * the amount of arithmetic needed to be performed at runtime.
                     */
                    int itableDynamicHubOffset = dynamicHubLayout.vTableOffset() + (itableStartingOffset * dynamicHubLayout.vTableSlotSize);
                    openTypeWorldTypeCheckSlots[typeSlotIdx + 1] = itableDynamicHubOffset;
                    typeSlotIdx += 2;
                }

                CFunctionPointer[] vtable = new CFunctionPointer[type.openTypeWorldDispatchTables.length];
                for (int idx = 0; idx < type.openTypeWorldDispatchTables.length; idx++) {
                    /*
                     * We install a CodePointer in the open world vtable; when generating relocation
                     * info, we will know these point into .text
                     */
                    vtable[idx] = new MethodPointer(type.openTypeWorldDispatchTables[idx]);
                }

                hub.setOpenTypeWorldData(vtable, type.getTypeID(),
                                type.getTypeIDDepth(), type.getNumClassTypes(), type.getNumInterfaceTypes(), openTypeWorldTypeCheckSlots);
            }
        }
    }

    private static ReferenceMapEncoder.Input createReferenceMap(HostedType type) {
        HostedField[] fields = type.getInstanceFields(true);

        SubstrateReferenceMap referenceMap = new SubstrateReferenceMap();
        for (HostedField field : fields) {
            if (field.getType().getStorageKind() == JavaKind.Object && field.hasLocation() && !excludeFromReferenceMap(field)) {
                referenceMap.markReferenceAtOffset(field.getLocation(), true);
            }
        }
        if (type.isInstanceClass()) {
            final HostedInstanceClass instanceClass = (HostedInstanceClass) type;
            /*
             * If the instance type has a monitor field, add it to the reference map.
             */
            final int monitorOffset = instanceClass.getMonitorFieldOffset();
            if (monitorOffset != 0) {
                referenceMap.markReferenceAtOffset(monitorOffset, true);
            }
        }
        return referenceMap;
    }

    private static boolean excludeFromReferenceMap(HostedField field) {
        ExcludeFromReferenceMap annotation = field.getAnnotation(ExcludeFromReferenceMap.class);
        if (annotation != null) {
            return ReflectionUtil.newInstance(annotation.onlyIf()).getAsBoolean();
        }
        return false;
    }

    private void processFieldLocations() {
        var fieldValueInterceptionSupport = FieldValueInterceptionSupport.singleton();
        for (HostedField hField : hUniverse.fields.values()) {
            AnalysisField aField = hField.wrapped;
            if (aField.wrapped instanceof ComputedValueField) {
                ((ComputedValueField) aField.wrapped).processSubstrate(hMetaAccess);
            }

            if (hField.isReachable() && !hField.hasLocation() && Modifier.isStatic(hField.getModifiers()) && !aField.isWritten() && fieldValueInterceptionSupport.isValueAvailable(aField)) {
                hField.setUnmaterializedStaticConstant();
            }
        }
    }
}

@AutomaticallyRegisteredFeature
final class InvalidVTableEntryFeature implements InternalFeature {

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess a) {
        BeforeAnalysisAccessImpl access = (BeforeAnalysisAccessImpl) a;
        access.registerAsRoot(InvalidMethodPointerHandler.INVALID_VTABLE_ENTRY_HANDLER_METHOD, true, "Registered in " + InvalidVTableEntryFeature.class);
    }
}
