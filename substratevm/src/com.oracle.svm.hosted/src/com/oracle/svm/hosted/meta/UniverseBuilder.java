/*
 * Copyright (c) 2012, 2020, Oracle and/or its affiliates. All rights reserved.
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ForkJoinTask;

import org.graalvm.collections.Pair;
import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.Indent;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.c.function.CEntryPointLiteral;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.function.CFunctionPointer;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.constraints.UnsupportedFeatures;
import com.oracle.graal.pointsto.infrastructure.WrappedConstantPool;
import com.oracle.graal.pointsto.infrastructure.WrappedJavaType;
import com.oracle.graal.pointsto.infrastructure.WrappedSignature;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.results.AbstractAnalysisResultsBuilder;
import com.oracle.svm.core.StaticFieldsSupport;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.ExcludeFromReferenceMap;
import com.oracle.svm.core.c.BoxedRelocatedPointer;
import com.oracle.svm.core.c.function.CFunctionOptions;
import com.oracle.svm.core.classinitialization.ClassInitializationInfo.ClassInitializerFunctionPointerHolder;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.config.ObjectLayout;
import com.oracle.svm.core.deopt.DeoptimizedFrame;
import com.oracle.svm.core.heap.FillerObject;
import com.oracle.svm.core.heap.InstanceReferenceMapEncoder;
import com.oracle.svm.core.heap.ReferenceMapEncoder;
import com.oracle.svm.core.heap.ReferenceMapIndex;
import com.oracle.svm.core.heap.StoredContinuation;
import com.oracle.svm.core.heap.SubstrateReferenceMap;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.DynamicHubSupport;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.HostedConfiguration;
import com.oracle.svm.hosted.NativeImageOptions;
import com.oracle.svm.hosted.config.HybridLayout;
import com.oracle.svm.hosted.substitute.AnnotationSubstitutionProcessor;
import com.oracle.svm.hosted.substitute.ComputedValueField;
import com.oracle.svm.hosted.substitute.DeletedMethod;
import com.oracle.svm.util.ReflectionUtil;

import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.ExceptionHandler;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.Signature;
import jdk.vm.ci.meta.UnresolvedJavaType;

public class UniverseBuilder {

    private final AnalysisUniverse aUniverse;
    private final AnalysisMetaAccess aMetaAccess;
    private final HostedUniverse hUniverse;
    private final HostedMetaAccess hMetaAccess;
    private AbstractAnalysisResultsBuilder staticAnalysisResultsBuilder;
    private final UnsupportedFeatures unsupportedFeatures;
    private TypeCheckBuilder typeCheckBuilder;

    public UniverseBuilder(AnalysisUniverse aUniverse, AnalysisMetaAccess aMetaAccess, HostedUniverse hUniverse, HostedMetaAccess hMetaAccess,
                    AbstractAnalysisResultsBuilder staticAnalysisResultsBuilder, UnsupportedFeatures unsupportedFeatures) {
        this.aUniverse = aUniverse;
        this.aMetaAccess = aMetaAccess;
        this.hUniverse = hUniverse;
        this.hMetaAccess = hMetaAccess;
        this.staticAnalysisResultsBuilder = staticAnalysisResultsBuilder;
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
                /*
                 * Set enclosing type lazily to avoid cyclic dependency between interfaces and
                 * enclosing types. For example, in Scala an interface can extends its inner type.
                 */
                if (aType.getEnclosingType() != null) {
                    lookupType(aType).setEnclosingType(Objects.requireNonNull(lookupType(aType.getEnclosingType())));
                }
            }

            for (AnalysisField aField : aUniverse.getFields()) {
                makeField(aField);
            }
            for (AnalysisMethod aMethod : aUniverse.getMethods()) {
                makeMethod(aMethod);
            }

            Collection<HostedType> allTypes = hUniverse.types.values();
            HostedType objectType = hUniverse.objectType();
            HostedType cloneableType = hUniverse.types.get(aMetaAccess.lookupJavaType(Cloneable.class));
            HostedType serializableType = hUniverse.types.get(aMetaAccess.lookupJavaType(Serializable.class));
            typeCheckBuilder = new TypeCheckBuilder(allTypes, objectType, cloneableType, serializableType);
            typeCheckBuilder.buildTypeInformation(hUniverse);
            typeCheckBuilder.calculateIDs();

            collectDeclaredMethods();
            collectMonitorFieldInfo(staticAnalysisResultsBuilder.getBigBang());

            ForkJoinTask<?> profilingInformationBuildTask = ForkJoinTask.adapt(this::buildProfilingInformation).fork();

            layoutInstanceFields();
            layoutStaticFields();

            collectMethodImplementations();
            buildVTables();
            buildHubs();

            processFieldLocations();

            hUniverse.orderedMethods = new ArrayList<>(hUniverse.methods.values());
            Collections.sort(hUniverse.orderedMethods);
            hUniverse.orderedFields = new ArrayList<>(hUniverse.fields.values());
            Collections.sort(hUniverse.orderedFields);
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
            boolean isCloneable = aMetaAccess.lookupJavaType(Cloneable.class).isAssignableFrom(aType);
            hType = new HostedInstanceClass(hUniverse, aType, kind, storageKind, superClass, sInterfaces, isCloneable);

            if (superClass == null) {
                hUniverse.kindToType.put(JavaKind.Object, hType);
            }

        } else if (aType.isArray()) {
            assert !aType.isInterface() && !aType.isInstanceClass();
            HostedClass superType = (HostedClass) makeType(aType.getSuperclass());
            HostedType componentType = makeType(aType.getComponentType());

            hType = new HostedArrayClass(hUniverse, aType, kind, storageKind, superType, sInterfaces, componentType);

        } else {
            throw VMError.shouldNotReachHere();
        }

        HostedType existing = hUniverse.types.put(aType, hType);
        if (existing != null) {
            throw VMError.shouldNotReachHere("Overwriting existing type: " + hType + " != " + existing);
        }

        DynamicHub hub = hType.getHub();
        Class<?> hostedJavaClass = hub.getHostedJavaClass();
        AnalysisType aTypeChecked = aMetaAccess.lookupJavaType(hostedJavaClass);
        HostedType hTypeChecked = hMetaAccess.lookupJavaType(hostedJavaClass);
        if (!sameObject(aType, aTypeChecked) || !sameObject(hTypeChecked, hType)) {
            throw VMError.shouldNotReachHere("Type mismatch when performing round-trip HostedType/AnalysisType -> DynamicHub -> java.lang.Class -> HostedType/AnalysisType: " + System.lineSeparator() +
                            hType + " @ " + Integer.toHexString(System.identityHashCode(hType)) +
                            " / " + aType + " @ " + Integer.toHexString(System.identityHashCode(aType)) + System.lineSeparator() +
                            " -> " + hub + " -> " + hostedJavaClass + System.lineSeparator() +
                            " -> " + hTypeChecked + " @ " + Integer.toHexString(System.identityHashCode(hTypeChecked)) +
                            " / " + aTypeChecked + " @ " + Integer.toHexString(System.identityHashCode(aTypeChecked)));
        }
        return hType;
    }

    /*
     * Normally types need to be compared with equals, and there is a gate check enforcing this.
     * Using a separate method hides the comparison from the checker.
     */
    private static boolean sameObject(Object x, Object y) {
        return x == y;
    }

    private void makeMethod(AnalysisMethod aMethod) {
        HostedType holder;
        holder = lookupType(aMethod.getDeclaringClass());
        Signature signature = makeSignature(aMethod.getSignature(), holder);
        ConstantPool constantPool = makeConstantPool(aMethod.getConstantPool(), holder);

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

        HostedMethod sMethod = new HostedMethod(hUniverse, aMethod, holder, signature, constantPool, sHandlers, null);
        assert !hUniverse.methods.containsKey(aMethod);
        hUniverse.methods.put(aMethod, sMethod);

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
        } else if (aMethod.isNative() && !aMethod.isIntrinsicMethod() && aMethod.isImplementationInvoked() && !NativeImageOptions.ReportUnsupportedElementsAtRuntime.getValue()) {
            unsupportedFeatures.addMessage(aMethod.format("%H.%n(%p)"), aMethod, AnnotationSubstitutionProcessor.deleteErrorMessage(aMethod, DeletedMethod.NATIVE_MESSAGE, true));
        }
    }

    private Signature makeSignature(Signature aSignature, WrappedJavaType defaultAccessingClass) {
        WrappedSignature hSignature = hUniverse.signatures.get(aSignature);
        if (hSignature == null) {
            hSignature = new WrappedSignature(hUniverse, aSignature, defaultAccessingClass);
            hUniverse.signatures.put(aSignature, hSignature);

            for (int i = 0; i < aSignature.getParameterCount(false); i++) {
                lookupType((AnalysisType) aSignature.getParameterType(i, null));
            }
            lookupType((AnalysisType) aSignature.getReturnType(null));
        }
        return hSignature;
    }

    private ConstantPool makeConstantPool(ConstantPool aConstantPool, WrappedJavaType defaultAccessingClass) {
        WrappedConstantPool hConstantPool = hUniverse.constantPools.get(aConstantPool);
        if (hConstantPool == null) {
            hConstantPool = new WrappedConstantPool(hUniverse, aConstantPool, defaultAccessingClass);
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

        HostedField hField = new HostedField(hUniverse, hMetaAccess, aField, holder, type, staticAnalysisResultsBuilder.makeTypeProfile(aField));
        assert !hUniverse.fields.containsKey(aField);
        hUniverse.fields.put(aField, hField);
    }

    private void buildProfilingInformation() {
        /* Convert profiling information after all types and methods have been created. */
        hUniverse.methods.entrySet().parallelStream()
                        .forEach(entry -> entry.getValue().staticAnalysisResults = staticAnalysisResultsBuilder.makeOrApplyResults(entry.getKey()));

        staticAnalysisResultsBuilder = null;
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
                    ClassInitializerFunctionPointerHolder.class,
                    FillerObject.class));

    private void collectMonitorFieldInfo(BigBang bb) {
        if (!SubstrateOptions.MultiThreaded.getValue()) {
            /* No locking information needed in single-threaded mode. */
            return;
        }

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

    private void layoutInstanceFields() {
        layoutInstanceFields(hUniverse.getObjectClass(), ConfigurationValues.getObjectLayout().getFirstFieldOffset());
    }

    private void layoutInstanceFields(HostedInstanceClass clazz, int superSize) {
        ArrayList<HostedField> rawFields = new ArrayList<>();
        ArrayList<HostedField> orderedFields = new ArrayList<>();
        ObjectLayout layout = ConfigurationValues.getObjectLayout();

        HostedConfiguration.instance().findAllFieldsForLayout(hUniverse, hMetaAccess, hUniverse.fields, rawFields, orderedFields, clazz);

        int startSize = superSize;
        if (clazz.getAnnotation(DeoptimizedFrame.ReserveDeoptScratchSpace.class) != null) {
            assert startSize <= DeoptimizedFrame.getScratchSpaceOffset();
            startSize = DeoptimizedFrame.getScratchSpaceOffset() + layout.getDeoptScratchSpace();
        }

        if (HybridLayout.isHybrid(clazz)) {
            /* Set start after array length field */
            assert startSize == layout.getArrayLengthOffset();
            int fieldSize = layout.sizeInBytes(JavaKind.Int);
            startSize += fieldSize;

            /*
             * Set start after typecheck slots field, if the hybrid class has one. For now, only
             * DynamicHubs can this field(s).
             */
            if (clazz.equals(hMetaAccess.lookupJavaType(DynamicHub.class))) {
                /* Each type check id slot is 2 bytes. */
                startSize += typeCheckBuilder.getNumTypeCheckSlots() * 2;
            }
        }

        // Sort so that a) all Object fields are consecutive, and b) bigger types come first.
        Collections.sort(rawFields);

        int nextOffset = startSize;
        while (rawFields.size() > 0) {
            boolean progress = false;
            for (int i = 0; i < rawFields.size(); i++) {
                HostedField field = rawFields.get(i);
                int fieldSize = layout.sizeInBytes(field.getStorageKind());

                if (nextOffset % fieldSize == 0) {
                    field.setLocation(nextOffset);
                    nextOffset += fieldSize;

                    rawFields.remove(i);
                    orderedFields.add(field);
                    progress = true;
                    break;
                }
            }
            if (!progress) {
                // Insert padding byte and try again.
                nextOffset++;
            }
        }

        int endOfFieldsOffset = nextOffset;

        /*
         * Compute the offsets of the "synthetic" fields for this class (but not subclasses).
         * Synthetic fields are put after all the instance fields. They are included in the instance
         * size, but not in the offset passed to subclasses.
         *
         * TODO: Should there be a list of synthetic fields for a class?
         */

        // A reference to a {@link java.util.concurrent.locks.ReentrantLock for "synchronized" or
        // Object.wait() and Object.notify() and friends.
        if (clazz.needMonitorField()) {
            final int referenceFieldAlignmentAndSize = layout.getReferenceSize();
            nextOffset = NumUtil.roundUp(nextOffset, referenceFieldAlignmentAndSize);
            clazz.setMonitorFieldOffset(nextOffset);
            nextOffset += referenceFieldAlignmentAndSize;
        }

        clazz.instanceFields = orderedFields.toArray(new HostedField[orderedFields.size()]);
        clazz.instanceSize = layout.alignUp(nextOffset);
        clazz.afterFieldsOffset = nextOffset;

        for (HostedType subClass : clazz.subTypes) {
            if (subClass.isInstanceClass()) {
                /*
                 * Derived classes ignore the monitor field of the super-class and start the layout
                 * of their fields right after the instance fields of the super-class. This is
                 * possible because each class that needs a synthetic field gets its own synthetic
                 * field at the end of its instance fields.
                 */
                layoutInstanceFields((HostedInstanceClass) subClass, endOfFieldsOffset);
            }
        }
    }

    private void layoutStaticFields() {
        ArrayList<HostedField> fields = new ArrayList<>();
        for (HostedField field : hUniverse.fields.values()) {
            if (Modifier.isStatic(field.getModifiers())) {
                fields.add(field);
            }
        }

        // Sort so that a) all Object fields are consecutive, and b) bigger types come first.
        Collections.sort(fields);

        ObjectLayout layout = ConfigurationValues.getObjectLayout();

        int nextPrimitiveField = 0;
        int nextObjectField = 0;

        @SuppressWarnings("unchecked")
        List<HostedField>[] fieldsOfTypes = (List<HostedField>[]) new ArrayList<?>[hUniverse.getTypes().size()];

        for (HostedField field : fields) {
            if (!field.wrapped.isWritten() && !MaterializedConstantFields.singleton().contains(field.wrapped)) {
                // Constant, does not require memory.
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
    }

    @SuppressWarnings("unchecked")
    private void collectDeclaredMethods() {
        List<HostedMethod>[] methodsOfType = (ArrayList<HostedMethod>[]) new ArrayList<?>[hUniverse.getTypes().size()];
        for (HostedMethod method : hUniverse.methods.values()) {
            int typeId = method.getDeclaringClass().getTypeID();
            List<HostedMethod> list = methodsOfType[typeId];
            if (list == null) {
                list = new ArrayList<>();
                methodsOfType[typeId] = list;
            }
            list.add(method);
        }

        HostedMethod[] noMethods = new HostedMethod[0];
        for (HostedType type : hUniverse.getTypes()) {
            List<HostedMethod> list = methodsOfType[type.getTypeID()];
            if (list != null) {
                Collections.sort(list);
                type.allDeclaredMethods = list.toArray(new HostedMethod[list.size()]);
            } else {
                type.allDeclaredMethods = noMethods;
            }
        }
    }

    private void collectMethodImplementations() {
        for (HostedMethod method : hUniverse.methods.values()) {

            // Reuse the implementations from the analysis method.
            method.implementations = hUniverse.lookup(method.wrapped.getImplementations());
            Arrays.sort(method.implementations);
        }
    }

    private void buildVTables() {
        /*
         * We want to pack the vtables as tight as possible, i.e., we want to avoid filler slots as
         * much as possible. Filler slots are unavoidable because we use the vtable also for
         * interface calls, i.e., an interface method needs a vtable index that is filled for all
         * classes that implement that interface.
         *
         * Note that because of interface methods the same implementation method can be registered
         * multiple times in the same vtable, with a different index used by different interface
         * methods.
         *
         * The optimization goal is to reduce the overall number of vtable slots. To achieve a good
         * result, we process types in three steps: 1) java.lang.Object, 2) interfaces, 3) classes.
         */

        /*
         * The mutable vtables while this algorithm is running. Contains an ArrayList for each type,
         * which is in the end converted to the vtable array.
         */
        Map<HostedType, ArrayList<HostedMethod>> vtablesMap = new HashMap<>();

        /*
         * A bit set of occupied vtable slots for each type.
         */

        Map<HostedType, BitSet> usedSlotsMap = new HashMap<>();
        /*
         * The set of vtable slots used for this method. Because of interfaces, one method can have
         * multiple vtable slots. The assignment algorithm uses this table to find out if a suitable
         * vtable index already exists for a method.
         */
        Map<HostedMethod, Set<Integer>> vtablesSlots = new HashMap<>();

        for (HostedType type : hUniverse.getTypes()) {
            vtablesMap.put(type, new ArrayList<>());
            BitSet initialBitSet = new BitSet();
            usedSlotsMap.put(type, initialBitSet);
        }

        /*
         * 1) Process java.lang.Object first because the methods defined there (equals, hashCode,
         * toString, clone) are in every vtable. We must not have filler slots before these methods.
         */
        HostedInstanceClass objectClass = hUniverse.getObjectClass();
        assignImplementations(objectClass, vtablesMap, usedSlotsMap, vtablesSlots);

        /*
         * 2) Process interfaces. Interface methods have higher constraints on vtable slots because
         * the same slots need to be used in all implementation classes, which can be spread out
         * across the type hierarchy. We assign an importance level to each interface and then sort
         * by that number, to further reduce the filler slots.
         */
        List<Pair<HostedType, Integer>> interfaces = new ArrayList<>();
        for (HostedType type : hUniverse.getTypes()) {
            if (type.isInterface()) {
                /*
                 * We use the number of subtypes as the importance for an interface: If an interface
                 * is implemented often, then it can produce more unused filler slots than an
                 * interface implemented rarely. We do not multiply with the number of methods that
                 * the interface implements: there are usually no filler slots in between methods of
                 * an interface, i.e., an interface that declares many methods does not lead to more
                 * filler slots than an interface that defines only one method.
                 */
                int importance = collectSubtypes(type, new HashSet<>()).size();
                interfaces.add(Pair.create(type, importance));
            }
        }
        interfaces.sort((pair1, pair2) -> pair2.getRight() - pair1.getRight());
        for (Pair<HostedType, Integer> pair : interfaces) {
            assignImplementations(pair.getLeft(), vtablesMap, usedSlotsMap, vtablesSlots);
        }

        /*
         * 3) Process all implementation classes, starting with java.lang.Object and going
         * depth-first down the tree.
         */
        buildVTable(objectClass, vtablesMap, usedSlotsMap, vtablesSlots);

        for (HostedType type : hUniverse.getTypes()) {
            if (type.isArray()) {
                type.vtable = objectClass.vtable;
            }
            if (type.vtable == null) {
                assert type.isInterface() || type.isPrimitive();
                type.vtable = new HostedMethod[0];
            }
        }

        if (SubstrateUtil.assertionsEnabled()) {
            /* Check that all vtable entries are the correctly resolved methods. */
            for (HostedType type : hUniverse.getTypes()) {
                for (HostedMethod m : type.vtable) {
                    assert m == null || m.equals(hUniverse.lookup(type.wrapped.resolveConcreteMethod(m.wrapped, type.wrapped)));
                }
            }
        }
    }

    /** Collects all subtypes of the provided type in the provided set. */
    private static Set<HostedType> collectSubtypes(HostedType type, Set<HostedType> allSubtypes) {
        if (allSubtypes.add(type)) {
            for (HostedType subtype : type.subTypes) {
                collectSubtypes(subtype, allSubtypes);
            }
        }
        return allSubtypes;
    }

    private void buildVTable(HostedClass clazz, Map<HostedType, ArrayList<HostedMethod>> vtablesMap, Map<HostedType, BitSet> usedSlotsMap, Map<HostedMethod, Set<Integer>> vtablesSlots) {
        assignImplementations(clazz, vtablesMap, usedSlotsMap, vtablesSlots);

        ArrayList<HostedMethod> vtable = vtablesMap.get(clazz);
        HostedMethod[] vtableArray = vtable.toArray(new HostedMethod[vtable.size()]);
        assert vtableArray.length == 0 || vtableArray[vtableArray.length - 1] != null : "Unnecessary entry at end of vtable";
        clazz.vtable = vtableArray;

        for (HostedType subClass : clazz.subTypes) {
            if (!subClass.isInterface() && !subClass.isArray()) {
                buildVTable((HostedClass) subClass, vtablesMap, usedSlotsMap, vtablesSlots);
            }
        }
    }

    private void assignImplementations(HostedType type, Map<HostedType, ArrayList<HostedMethod>> vtablesMap, Map<HostedType, BitSet> usedSlotsMap, Map<HostedMethod, Set<Integer>> vtablesSlots) {
        for (HostedMethod method : type.getAllDeclaredMethods()) {
            /* We only need to look at methods that the static analysis registered as invoked. */
            if (method.wrapped.isInvoked() || method.wrapped.isImplementationInvoked()) {
                /*
                 * Methods with 1 implementations do not need a vtable because invokes can be done
                 * as direct calls without the need for a vtable. Methods with 0 implementations are
                 * unreachable.
                 */
                if (method.implementations.length > 1) {
                    /*
                     * Find a suitable vtable slot for the method, taking the existing vtable
                     * assignments into account.
                     */
                    int slot = findSlot(method, vtablesMap, usedSlotsMap, vtablesSlots);
                    method.vtableIndex = slot;

                    /* Assign the vtable slot for the type and all subtypes. */
                    assignImplementations(method.getDeclaringClass(), method, slot, vtablesMap);
                }
            }
        }
    }

    /**
     * Assign the vtable slot to the correct resolved method for all subtypes.
     */
    private void assignImplementations(HostedType type, HostedMethod method, int slot, Map<HostedType, ArrayList<HostedMethod>> vtablesMap) {
        if (type.wrapped.isInstantiated()) {
            assert (type.isInstanceClass() && !type.isAbstract()) || type.isArray();

            HostedMethod resolvedMethod = resolveMethod(type, method);
            if (resolvedMethod != null) {
                ArrayList<HostedMethod> vtable = vtablesMap.get(type);
                if (slot < vtable.size() && vtable.get(slot) != null) {
                    /* We already have a vtable entry from a supertype. Check that it is correct. */
                    assert vtable.get(slot).equals(resolvedMethod);
                } else {
                    resize(vtable, slot + 1);
                    assert vtable.get(slot) == null;
                    vtable.set(slot, resolvedMethod);
                }
            }
        }

        for (HostedType subtype : type.subTypes) {
            if (!subtype.isArray()) {
                assignImplementations(subtype, method, slot, vtablesMap);
            }
        }
    }

    private HostedMethod resolveMethod(HostedType type, HostedMethod method) {
        AnalysisMethod resolved = type.wrapped.resolveConcreteMethod(method.wrapped, type.wrapped);
        if (resolved == null || !resolved.isImplementationInvoked()) {
            return null;
        } else {
            assert !resolved.isAbstract();
            return hUniverse.lookup(resolved);
        }
    }

    private static void resize(ArrayList<?> list, int minSize) {
        list.ensureCapacity(minSize);
        while (list.size() < minSize) {
            list.add(null);
        }
    }

    private int findSlot(HostedMethod method, Map<HostedType, ArrayList<HostedMethod>> vtablesMap, Map<HostedType, BitSet> usedSlotsMap, Map<HostedMethod, Set<Integer>> vtablesSlots) {
        /*
         * Check if all implementation methods already have a common slot assigned. Each
         * implementation method can have multiple slots because of interfaces. We compute the
         * intersection of the slot sets for all implementation methods.
         */
        Set<Integer> resultSlots = vtablesSlots.get(method.implementations[0]);
        for (HostedMethod impl : method.implementations) {
            Set<Integer> implSlots = vtablesSlots.get(impl);
            if (implSlots == null) {
                resultSlots = null;
                break;
            }
            resultSlots.retainAll(implSlots);
        }
        if (resultSlots != null && !resultSlots.isEmpty()) {
            /*
             * All implementations already have the same vtable slot assigned, so we can re-use
             * that. If we have multiple candidates, we use the slot with the lowest number.
             */
            int resultSlot = Integer.MAX_VALUE;
            for (int slot : resultSlots) {
                resultSlot = Math.min(resultSlot, slot);
            }
            return resultSlot;
        }

        /*
         * No slot found, we need to compute a new one. Check the whole subtype hierarchy for
         * constraints using bitset union, and then use the lowest slot number that is available in
         * all subtypes.
         */
        BitSet usedSlots = new BitSet();
        collectUsedSlots(method.getDeclaringClass(), usedSlots, usedSlotsMap);
        for (HostedMethod impl : method.implementations) {
            collectUsedSlots(impl.getDeclaringClass(), usedSlots, usedSlotsMap);
        }

        /*
         * The new slot number is the lowest slot number not occupied by any subtype, i.e., the
         * lowest index not set in the union bitset.
         */
        int resultSlot = usedSlots.nextClearBit(0);

        markSlotAsUsed(resultSlot, method.getDeclaringClass(), vtablesMap, usedSlotsMap);
        for (HostedMethod impl : method.implementations) {
            markSlotAsUsed(resultSlot, impl.getDeclaringClass(), vtablesMap, usedSlotsMap);

            vtablesSlots.computeIfAbsent(impl, k -> new HashSet<>()).add(resultSlot);
        }

        return resultSlot;
    }

    private void collectUsedSlots(HostedType type, BitSet usedSlots, Map<HostedType, BitSet> usedSlotsMap) {
        usedSlots.or(usedSlotsMap.get(type));
        for (HostedType sub : type.subTypes) {
            if (!sub.isArray()) {
                collectUsedSlots(sub, usedSlots, usedSlotsMap);
            }
        }
    }

    private void markSlotAsUsed(int resultSlot, HostedType type, Map<HostedType, ArrayList<HostedMethod>> vtablesMap, Map<HostedType, BitSet> usedSlotsMap) {
        assert resultSlot >= vtablesMap.get(type).size() || vtablesMap.get(type).get(resultSlot) == null;

        usedSlotsMap.get(type).set(resultSlot);
        for (HostedType sub : type.subTypes) {
            if (!sub.isArray()) {
                markSlotAsUsed(resultSlot, sub, vtablesMap, usedSlotsMap);
            }
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
        ImageSingletons.lookup(DynamicHubSupport.class).setData(referenceMapEncoder.encodeAll());

        ObjectLayout ol = ConfigurationValues.getObjectLayout();
        for (HostedType type : hUniverse.getTypes()) {
            hUniverse.bb.getHeartbeatCallback().run();

            int layoutHelper;
            int monitorOffset = 0;
            if (type.isInstanceClass()) {
                HostedInstanceClass instanceClass = (HostedInstanceClass) type;
                if (instanceClass.isAbstract()) {
                    layoutHelper = LayoutEncoding.forAbstract();
                } else if (HybridLayout.isHybrid(type)) {
                    HybridLayout<?> hybridLayout = new HybridLayout<>(instanceClass, ol);
                    JavaKind storageKind = hybridLayout.getArrayElementStorageKind();
                    boolean isObject = (storageKind == JavaKind.Object);
                    layoutHelper = LayoutEncoding.forArray(type, isObject, hybridLayout.getArrayBaseOffset(), ol.getArrayIndexShift(storageKind));
                } else if (instanceClass.getJavaClass().equals(StoredContinuation.class)) {
                    layoutHelper = LayoutEncoding.forStoredContinuation();
                } else {
                    layoutHelper = LayoutEncoding.forInstance(type, ConfigurationValues.getObjectLayout().alignUp(instanceClass.getInstanceSize()));
                }
                monitorOffset = instanceClass.getMonitorFieldOffset();
            } else if (type.isArray()) {
                JavaKind storageKind = type.getComponentType().getStorageKind();
                boolean isObject = (storageKind == JavaKind.Object);
                layoutHelper = LayoutEncoding.forArray(type, isObject, ol.getArrayBaseOffset(storageKind), ol.getArrayIndexShift(storageKind));
            } else if (type.isInterface()) {
                layoutHelper = LayoutEncoding.forInterface();
            } else if (type.isPrimitive()) {
                layoutHelper = LayoutEncoding.forPrimitive();
            } else {
                throw VMError.shouldNotReachHere();
            }

            /*
             * The vtable entry values are available only after the code cache layout is fixed, so
             * leave them 0.
             */
            CFunctionPointer[] vtable = new CFunctionPointer[type.vtable.length];
            for (int idx = 0; idx < type.vtable.length; idx++) {
                /*
                 * We install a CodePointer in the vtable; when generating relocation info, we will
                 * know these point into .text
                 */
                vtable[idx] = MethodPointer.factory(type.vtable[idx]);
            }

            // pointer maps in Dynamic Hub
            ReferenceMapEncoder.Input referenceMap = referenceMaps.get(type);
            assert referenceMap != null;
            assert ((SubstrateReferenceMap) referenceMap).hasNoDerivedOffsets();
            long referenceMapIndex;
            if (referenceMap == SubstrateReferenceMap.STORED_CONTINUATION_REFERENCE_MAP) {
                referenceMapIndex = ReferenceMapIndex.STORED_CONTINUATION;
            } else {
                referenceMapIndex = referenceMapEncoder.lookupEncoding(referenceMap);
            }

            DynamicHub hub = type.getHub();
            hub.setData(layoutHelper, type.getTypeID(), monitorOffset, type.getTypeCheckStart(), type.getTypeCheckRange(), type.getTypeCheckSlot(), type.getTypeCheckSlots(),
                            vtable, referenceMapIndex, type.isInstantiated());
        }
    }

    private static ReferenceMapEncoder.Input createReferenceMap(HostedType type) {
        if (type.getJavaClass().equals(StoredContinuation.class)) {
            return SubstrateReferenceMap.STORED_CONTINUATION_REFERENCE_MAP;
        }

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
        for (HostedField hField : hUniverse.fields.values()) {
            AnalysisField aField = hField.wrapped;
            if (aField.wrapped instanceof ComputedValueField) {
                ((ComputedValueField) aField.wrapped).processSubstrate(hMetaAccess);
            }

            if (!hField.hasLocation() && Modifier.isStatic(hField.getModifiers()) && !aField.isWritten()) {
                hField.setUnmaterializedStaticConstant();
            }
        }
    }
}
