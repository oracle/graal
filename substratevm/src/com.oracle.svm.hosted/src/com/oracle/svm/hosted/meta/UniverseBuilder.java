/*
 * Copyright (c) 2012, 2017, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.core.util.VMError.shouldNotReachHere;

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
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ForkJoinTask;

import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.Pair;
import org.graalvm.collections.UnmodifiableEconomicSet;
import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.Indent;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.c.function.CEntryPointLiteral;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.function.CFunctionPointer;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.constraints.UnsupportedFeatures;
import com.oracle.graal.pointsto.flow.MethodTypeFlow;
import com.oracle.graal.pointsto.flow.TypeFlow;
import com.oracle.graal.pointsto.infrastructure.WrappedConstantPool;
import com.oracle.graal.pointsto.infrastructure.WrappedJavaType;
import com.oracle.graal.pointsto.infrastructure.WrappedSignature;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.results.StaticAnalysisResultsBuilder;
import com.oracle.graal.pointsto.typestate.TypeState;
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
import com.oracle.svm.core.heap.SubstrateReferenceMap;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.DynamicHubSupport;
import com.oracle.svm.core.hub.LayoutEncoding;
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
    private StaticAnalysisResultsBuilder staticAnalysisResultsBuilder;
    private final UnsupportedFeatures unsupportedFeatures;

    public UniverseBuilder(AnalysisUniverse aUniverse, AnalysisMetaAccess aMetaAccess, HostedUniverse hUniverse, HostedMetaAccess hMetaAccess,
                    StaticAnalysisResultsBuilder staticAnalysisResultsBuilder, UnsupportedFeatures unsupportedFeatures) {
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
            for (AnalysisField aField : aUniverse.getFields()) {
                makeField(aField);
            }
            for (AnalysisMethod aMethod : aUniverse.getMethods()) {
                makeMethod(aMethod);
            }

            BigBang bb = staticAnalysisResultsBuilder.getBigBang();
            ForkJoinTask<?> profilingInformationBuildTask = ForkJoinTask.adapt(this::buildProfilingInformation).fork();

            buildSubTypes();
            buildOrderedTypes();
            buildTypeCheckIDs();

            collectDeclaredMethods();
            collectMonitorFieldInfo(bb);
            collectHashCodeFieldInfo(bb);

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

            int dimension = hType.getArrayDimension();
            if (hType.getBaseType().getSuperclass() != null) {
                makeType(hType.getBaseType().getSuperclass().getArrayClass(dimension - 1).getWrapped().getArrayClass());
            }
            if (hType.getBaseType().isInterface()) {
                makeType(hUniverse.getObjectClass().getArrayClass(dimension - 1).getWrapped().getArrayClass());
            }
            for (HostedInterface interf : hType.getBaseType().getInterfaces()) {
                makeType(interf.getArrayClass(dimension - 1).getWrapped().getArrayClass());
            }

        } else {
            throw shouldNotReachHere();
        }

        hUniverse.types.put(aType, hType);
        /*
         * Set enclosing type lazily to avoid cyclic dependency between interfaces and enclosing
         * types. For example, in Scala an interface can extends its inner type.
         */
        if (aType.getEnclosingType() != null) {
            hType.setEnclosingType(makeType(aType.getEnclosingType()));
        }

        return hType;
    }

    private void makeMethod(AnalysisMethod aMethod) {
        HostedType holder;
        holder = makeType(aMethod.getDeclaringClass());
        Signature signature = makeSignature(aMethod.getSignature(), holder);
        ConstantPool constantPool = makeConstantPool(aMethod.getConstantPool(), holder);

        ExceptionHandler[] aHandlers = aMethod.getExceptionHandlers();
        ExceptionHandler[] sHandlers = new ExceptionHandler[aHandlers.length];
        for (int i = 0; i < aHandlers.length; i++) {
            ExceptionHandler h = aHandlers[i];
            JavaType catchType = h.getCatchType();
            if (h.getCatchType() instanceof AnalysisType) {
                catchType = makeType((AnalysisType) catchType);
            } else {
                assert catchType == null || catchType instanceof UnresolvedJavaType;
            }
            sHandlers[i] = new ExceptionHandler(h.getStartBCI(), h.getEndBCI(), h.getHandlerBCI(), h.catchTypeCPI(), catchType);
        }

        HostedMethod sMethod = new HostedMethod(hUniverse, aMethod, holder, signature, constantPool, sHandlers);
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
                makeType((AnalysisType) aSignature.getParameterType(i, null));
            }
            makeType((AnalysisType) aSignature.getReturnType(null));
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
        HostedType holder = makeType(aField.getDeclaringClass());
        /*
         * If the field is never written, or only assigned null, then we might not have a type for
         * it yet.
         */
        HostedType type = makeType(aField.getType());

        HostedField hField = new HostedField(hUniverse, hMetaAccess, aField, holder, type, staticAnalysisResultsBuilder.makeTypeProfile(aField));
        assert !hUniverse.fields.containsKey(aField);
        hUniverse.fields.put(aField, hField);
    }

    private void buildProfilingInformation() {
        /* Convert profiling information after all types and methods have been created. */
        hUniverse.methods.entrySet().parallelStream()
                        .forEach(entry -> entry.getValue().staticAnalysisResults = staticAnalysisResultsBuilder.makeResults(entry.getKey()));

        staticAnalysisResultsBuilder = null;
    }

    private void buildSubTypes() {
        /*
         * We cannot use the sub-type information from the AnalysisType because there are some minor
         * differences regarding array types. Therefore we build the sub-type information from
         * scratch.
         */
        Map<HostedType, Set<HostedType>> allSubTypes = new HashMap<>();
        for (HostedType type : hUniverse.types.values()) {
            allSubTypes.put(type, new HashSet<>());
        }

        for (HostedType type : hUniverse.types.values()) {
            if (type.getSuperclass() != null) {
                allSubTypes.get(type.getSuperclass()).add(type);
            }
            if (type.isInterface() && type.getInterfaces().length == 0) {
                allSubTypes.get(hUniverse.getObjectClass()).add(type);
            }
            for (HostedInterface interf : type.getInterfaces()) {
                allSubTypes.get(interf).add(type);
            }
        }

        for (HostedType type : hUniverse.types.values()) {
            Set<HostedType> subTypesSet = allSubTypes.get(type);
            HostedType[] subTypes = subTypesSet.toArray(new HostedType[subTypesSet.size()]);
            Arrays.sort(subTypes);
            type.subTypes = subTypes;
        }
    }

    private void buildOrderedTypes() {
        List<HostedType> orderedTypes = new ArrayList<>();
        int arrayDepth = 0;
        boolean typeFound;
        do {
            typeFound = false;
            /*
             * Order all primitive types are before Object, so that Object and all array types are
             * consecutive.
             */
            for (Map.Entry<JavaKind, HostedType> entry : hUniverse.kindToType.entrySet()) {
                if (entry.getKey() != JavaKind.Object) {
                    typeFound |= orderTypes(entry.getValue(), arrayDepth, orderedTypes);
                }
            }
            if (hUniverse.kindToType.containsKey(JavaKind.Object)) {
                typeFound |= orderTypes(hUniverse.kindToType.get(JavaKind.Object), arrayDepth, orderedTypes);
            }
            arrayDepth++;
        } while (typeFound);

        ImageSingletons.lookup(DynamicHubSupport.class).setMaxTypeId(orderedTypes.size());

        assert assertSame(orderedTypes, hUniverse.types.values());
        hUniverse.orderedTypes = orderedTypes;
    }

    private boolean orderTypes(HostedType baseType, int arrayDepth, List<HostedType> allTypes) {
        HostedType type = baseType.getArrayClass(arrayDepth);
        if (type == null) {
            return false;
        }
        if (type.typeID != -1) {
            return true;
        }

        type.typeID = allTypes.size();
        allTypes.add(type);

        for (HostedType sub : baseType.subTypes) {
            if (!sub.isArray()) {
                orderTypes(sub, arrayDepth, allTypes);
            }
        }
        return true;
    }

    private void buildTypeCheckIDs() {
        BitSet[] assignableTypeIDs = new BitSet[hUniverse.orderedTypes.size()];
        BitSet[] concreteTypeIDs = new BitSet[hUniverse.orderedTypes.size()];
        for (int i = hUniverse.orderedTypes.size() - 1; i >= 0; i--) {
            HostedType type = hUniverse.orderedTypes.get(i);
            buildTypeCheckIDs(type, assignableTypeIDs, concreteTypeIDs);
        }
    }

    private static final int[] EMPTY = new int[0];

    /**
     * Traverses the type hierarchy recursively and builds the information needed for type checks.
     *
     * @param type The current type during traversal
     * @param assignableTypeIDs A bit-set for each type (index by typeID). A set contains bits for
     *            each type which can be assigned to the containing type.
     * @param concreteTypeIDs The same as assignableTypeIDs, but the bit sets only contain
     *            instantiated types, i.e. they are a subset of assignableTypeIDs
     */
    private void buildTypeCheckIDs(HostedType type, BitSet[] assignableTypeIDs, BitSet[] concreteTypeIDs) {
        if (assignableTypeIDs[type.typeID] != null) {
            return;
        }

        BitSet assignable = new BitSet();
        BitSet concrete = new BitSet();
        HostedType strengthenStampType = null;

        /*
         * Collect information about the sub types. Note that the type hierarchy is not a tree but a
         * DAG because sub-types include types which implement interfaces.
         */
        for (HostedType subBase : type.getBaseType().subTypes) {
            HostedType sub = subBase.getArrayClass(type.getArrayDimension());
            if (sub != null && !sub.equals(type)) {
                buildTypeCheckIDs(sub, assignableTypeIDs, concreteTypeIDs);
                assignable.or(assignableTypeIDs[sub.getTypeID()]);
                concrete.or(concreteTypeIDs[sub.getTypeID()]);

                if (strengthenStampType == null) {
                    strengthenStampType = sub.strengthenStampType;
                } else if (sub.strengthenStampType != null) {
                    strengthenStampType = type;
                }
            }
        }

        if (type.isInstantiated()) {
            strengthenStampType = type;
        }
        type.strengthenStampType = strengthenStampType;

        /*
         * Set the bit for this type.
         */
        assignable.set(type.typeID);
        if (type.getWrapped().isInstantiated()) {
            assert (type.isInstanceClass() && !Modifier.isAbstract(type.getModifiers())) || type.isArray();
            concrete.set(type.typeID);
        }

        /*
         * Convert the assignable bit-set to a list of typeID ranges. Note that the assignable set
         * is used for dynamic type checks (e.g. Class.isInstance(Object)). It includes bits for
         * abstract types and not instantated types.
         */
        int[] assignableFromMatches = EMPTY;
        int assignableStart = assignable.nextSetBit(0);
        while (assignableStart != -1) {
            int assignableEnd = assignable.nextClearBit(assignableStart);
            assignableFromMatches = Arrays.copyOf(assignableFromMatches, assignableFromMatches.length + 2);
            assignableFromMatches[assignableFromMatches.length - 2] = assignableStart;
            assignableFromMatches[assignableFromMatches.length - 1] = assignableEnd - assignableStart;
            assignableStart = assignable.nextSetBit(assignableEnd);
        }
        type.assignableFromMatches = assignableFromMatches;

        /*
         * Now we do the same for concrete types (which are a significant subset of all types). Used
         * for static instanceof checks.
         */
        int rangeStart = concrete.nextSetBit(0);
        if (rangeStart >= 0) {
            int rangeEnd = concrete.nextClearBit(rangeStart);
            int assignableEnd = assignable.nextClearBit(rangeStart);
            if (assignableEnd > concrete.nextSetBit(rangeEnd)) {
                /*
                 * Better a single larger range than multiple ranges.
                 */
                rangeEnd = assignableEnd;
            }
            int rangeLength = rangeEnd - rangeStart;
            if (concrete.nextSetBit(rangeEnd) < 0) {
                /*
                 * There is only a single range. We are done. Note that we exclude Word types here,
                 * since all implementations that we might see during native image generation are
                 * not present at run time.
                 */
                if (rangeLength == 1 && !type.isWordType()) {
                    type.uniqueConcreteImplementation = hUniverse.orderedTypes.get(rangeStart);
                }
                type.setInstanceOfRange(rangeStart, rangeLength);
            } else { // if (type.getWrapped().isInTypeCheck()) {
                /*
                 * There are multiple ranges, e.g. if the type is an interface which is
                 * "distributed" over the type hierarchy. In this case we use another method for
                 * instanceof checks: bit testing. We assign a unique bit to the type.
                 */
                int interfaceBit = hUniverse.numInterfaceBits++;
                setInstanceOfBits(type, interfaceBit);
                type.setInstanceOfRange(interfaceBit, -1);
                // } else {
                // type.setInstanceOfRange(0, -2);
            }
        }

        assignableTypeIDs[type.typeID] = assignable;
        concreteTypeIDs[type.typeID] = concrete;
    }

    /**
     * Sets the allocated bit for instanceof checking to all sub-types of a type.
     */
    private static void setInstanceOfBits(HostedType type, int bit) {
        if (type.instanceOfBits == null) {
            type.instanceOfBits = new BitSet(bit + 1);
        }
        if (!type.instanceOfBits.get(bit)) {
            type.instanceOfBits.set(bit);

            for (HostedType subBase : type.getBaseType().subTypes) {
                HostedType sub = subBase.getArrayClass(type.getArrayDimension());
                if (sub != null && !sub.equals(type)) {
                    setInstanceOfBits(sub, bit);
                }
            }
        }
    }

    // @formatter:off
//    /**
//     * New version of the method that uses the static analysis results collected by the static
//     * analysis results builder instead of accessing the type states directly.
//     */
//    @SuppressWarnings("try")
//    private void collectHashCodeFieldInfo() {
//
//        AnalysisMethod method = null;
//        try {
//            method = aMetaAccess.lookupJavaMethod(System.class.getMethod("identityHashCode", Object.class));
//        } catch (NoSuchMethodException | SecurityException e) {
//            throw shouldNotReachHere();
//        }
//        if (method == null) {
//            return;
//        }
//
//        try (Indent indent = Debug.logAndIndent("check types for which identityHashCode is invoked")) {
//
//            // Check which types may be a parameter of System.identityHashCode (which is invoked by
//            // Object.hashCode).
//
//            HostedMethod hMethod = hUniverse.methods.get(method);
//            JavaTypeProfile paramProfile = hMethod.getProfilingInfo().getParameterTypeProfile(0);
//
//            if (paramProfile == null) {
//
//                // This is the case if the identityHashCode parameter type is unknown. So all
//                // classes get the hashCode field.
//                // But this is only a fail-safe, because it cannot happen in the current
//                // implementation of the analysis pass.
//
//                Debug.log("all types need a hashCode field");
//                for (HostedType hType : hUniverse.getTypes()) {
//                    if (hType.isInstanceClass()) {
//                        ((HostedInstanceClass) hType).setNeedHashCodeField();
//                    }
//                }
//                hUniverse.getObjectClass().setNeedHashCodeField();
//            } else {
//
//                // Mark all paramter types of System.identityHashCode to have a hash-code field.
//
//                for (ProfiledType type : paramProfile.getTypes()) {
//                    Debug.log("type %s is argument to identityHashCode", type);
//
//                    /*
//                     * Array types get a hash-code field by default. So we only have to deal with
//                     * instance types here.
//                     */
//                    if (type.getType().isInstanceClass()) {
//                        HostedInstanceClass hType = (HostedInstanceClass) hUniverse.lookup(type.getType());
//                        hType.setNeedHashCodeField();
//                    }
//                }
//            }
//        }
//    }
    // @formatter:on

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

    /** These classes must never have a separate hash code field. */
    private static final Class<?>[] CLASSES_WITHOUT_HASH_CODE_FIELD = new Class<?>[]{
                    FillerObject.class, // for size reasons, instances should never be accessed
    };

    @SuppressWarnings("try")
    private void collectHashCodeFieldInfo(BigBang bb) {

        AnalysisMethod method;
        try {
            method = aMetaAccess.lookupJavaMethod(System.class.getMethod("identityHashCode", Object.class));
        } catch (NoSuchMethodException | SecurityException e) {
            throw shouldNotReachHere();
        }
        if (method == null) {
            return;
        }

        DebugContext debug = bb.getDebug();
        try (Indent ignore = debug.logAndIndent("check types for which identityHashCode is invoked")) {
            EconomicSet<HostedType> typesWithoutHashCodeField = EconomicSet.create();
            for (Class<?> type : CLASSES_WITHOUT_HASH_CODE_FIELD) {
                Optional<HostedType> hType = hMetaAccess.optionalLookupJavaType(type);
                hType.ifPresent(typesWithoutHashCodeField::add);
            }

            // Check which types may be a parameter of System.identityHashCode (which is invoked by
            // Object.hashCode).
            MethodTypeFlow methodFlow = method.getTypeFlow();
            TypeFlow<?> paramFlow = methodFlow.getParameterFlow(0);
            TypeState thisParamState = methodFlow.getParameterTypeState(bb, 0);
            assert thisParamState != null;
            Iterable<AnalysisType> typesNeedHashCode = thisParamState.types();
            if (typesNeedHashCode == null || thisParamState.isUnknown() || methodFlow.isSaturated(bb, paramFlow)) {
                /*
                 * If the identityHashCode parameter type is unknown or it is saturated then all
                 * classes need to get the hashCode field.
                 */
                debug.log("all types need a hashCode field");
                for (HostedType hType : hUniverse.getTypes()) {
                    maybeSetNeedHashCodeField(hType, typesWithoutHashCodeField);
                }
                maybeSetNeedHashCodeField(hUniverse.getObjectClass(), typesWithoutHashCodeField);
            } else {
                // Mark all parameter types of System.identityHashCode to have a hash-code field.
                for (AnalysisType type : typesNeedHashCode) {
                    debug.log("type %s is argument to identityHashCode", type);
                    maybeSetNeedHashCodeField(hUniverse.lookup(type), typesWithoutHashCodeField);
                }
            }
        }
    }

    private static void maybeSetNeedHashCodeField(HostedType hType, UnmodifiableEconomicSet<HostedType> typesWithoutHashCodeField) {
        // Array types get a hash code field by default, so we only have to deal with instance types
        if (hType.isInstanceClass() && !typesWithoutHashCodeField.contains(hType)) {
            ((HostedInstanceClass) hType).setNeedHashCodeField();
        }
    }

    private void layoutInstanceFields() {
        layoutInstanceFields(hUniverse.getObjectClass(), ConfigurationValues.getObjectLayout().getFirstFieldOffset());
    }

    private void layoutInstanceFields(HostedInstanceClass clazz, int superSize) {
        ArrayList<HostedField> rawFields = new ArrayList<>();
        ArrayList<HostedField> orderedFields = new ArrayList<>();

        HostedConfiguration.instance().findAllFieldsForLayout(hUniverse, hMetaAccess, hUniverse.fields, rawFields, orderedFields, clazz);

        int startSize = superSize;
        if (clazz.getAnnotation(DeoptimizedFrame.ReserveDeoptScratchSpace.class) != null) {
            assert startSize <= DeoptimizedFrame.getScratchSpaceOffset();
            startSize = DeoptimizedFrame.getScratchSpaceOffset() + ConfigurationValues.getObjectLayout().getDeoptScratchSpace();
        }

        if (HybridLayout.isHybrid(clazz)) {
            /* Set start after array length field */
            assert startSize == ConfigurationValues.getObjectLayout().getArrayLengthOffset();
            int fieldSize = ConfigurationValues.getObjectLayout().sizeInBytes(JavaKind.Int);
            startSize += fieldSize;

            /*
             * Set start after bitset field, if the hybrid class has one. For now, only DynamicHubs
             * can have bitsets.
             */
            if (clazz.equals(hMetaAccess.lookupJavaType(DynamicHub.class))) {
                startSize += (hUniverse.numInterfaceBits + Byte.SIZE - 1) / Byte.SIZE;
            }
        }

        // Sort so that a) all Object fields are consecutive, and b) bigger types come first.
        Collections.sort(rawFields);

        int nextOffset = startSize;
        while (rawFields.size() > 0) {
            boolean progress = false;
            for (int i = 0; i < rawFields.size(); i++) {
                HostedField field = rawFields.get(i);
                int fieldSize = ConfigurationValues.getObjectLayout().sizeInBytes(field.getStorageKind());

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
         *
         * I am putting the 8-byte aligned reference fields before the 4-byte aligned hashcode field
         * to avoid unnecessary padding.
         *
         * TODO: The code for aligning the fields assumes that the alignment and the size are the
         * same.
         */

        // A reference to a {@link java.util.concurrent.locks.ReentrantLock for "synchronized" or
        // Object.wait() and Object.notify() and friends.
        if (clazz.needMonitorField()) {
            final int referenceFieldAlignmentAndSize = ConfigurationValues.getObjectLayout().getReferenceSize();
            nextOffset = NumUtil.roundUp(nextOffset, referenceFieldAlignmentAndSize);
            clazz.setMonitorFieldOffset(nextOffset);
            nextOffset += referenceFieldAlignmentAndSize;
        }

        // An int to hold the result for System.identityHashCode.
        if (ConfigurationValues.getObjectLayout().useExplicitIdentityHashCodeField()) {
            if (clazz.needHashCodeField()) {
                int intFieldSize = ConfigurationValues.getObjectLayout().sizeInBytes(JavaKind.Int);
                nextOffset = NumUtil.roundUp(nextOffset, intFieldSize);
                clazz.setHashCodeFieldOffset(nextOffset);
                nextOffset += intFieldSize;
            }
        } else {
            int offset = ConfigurationValues.getObjectLayout().getInstanceIdentityHashCodeOffset();
            assert offset >= 0;
            clazz.setHashCodeFieldOffset(offset);
        }

        clazz.instanceFields = orderedFields.toArray(new HostedField[orderedFields.size()]);
        clazz.instanceSize = ConfigurationValues.getObjectLayout().alignUp(nextOffset);

        for (HostedType subClass : clazz.subTypes) {
            if (subClass.isInstanceClass()) {
                /*
                 * Derived classes ignore the hashCode field of the super-class and start the layout
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
        List<HostedField>[] fieldsOfTypes = (List<HostedField>[]) new ArrayList<?>[hUniverse.orderedTypes.size()];

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
        for (HostedType type : hUniverse.orderedTypes) {
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
        List<HostedMethod>[] methodsOfType = (ArrayList<HostedMethod>[]) new ArrayList<?>[hUniverse.orderedTypes.size()];
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
        for (HostedType type : hUniverse.orderedTypes) {
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

        for (HostedType type : hUniverse.orderedTypes) {
            vtablesMap.put(type, new ArrayList<>());
            BitSet initialBitSet = new BitSet();
            usedSlotsMap.put(type, initialBitSet);
        }

        /*
         * 1) Process java.lang.Object first because the methods defined there (equals, hashCode,
         * toString, clone) are in every vtable. We must not have filler slots before these methods.
         */
        assignImplementations(hUniverse.getObjectClass(), vtablesMap, usedSlotsMap, vtablesSlots);

        /*
         * 2) Process interfaces. Interface methods have higher constraints on vtable slots because
         * the same slots need to be used in all implementation classes, which can be spread out
         * across the type hierarchy. We assign an importance level to each interface and then sort
         * by that number, to further reduce the filler slots.
         */
        List<Pair<HostedType, Integer>> interfaces = new ArrayList<>();
        for (HostedType type : hUniverse.orderedTypes) {
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
        buildVTable(hUniverse.getObjectClass(), vtablesMap, usedSlotsMap, vtablesSlots);

        for (HostedType type : hUniverse.orderedTypes) {
            if (type.vtable == null) {
                assert type.isInterface() || type.isPrimitive();
                type.vtable = new HostedMethod[0];
            }
        }

        if (SubstrateUtil.assertionsEnabled()) {
            /* Check that all vtable entries are the correctly resolved methods. */
            for (HostedType type : hUniverse.orderedTypes) {
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
            if (!subClass.isInterface()) {
                buildVTable((HostedClass) subClass, vtablesMap, usedSlotsMap, vtablesSlots);
            }
        }
    }

    private void assignImplementations(HostedType type, Map<HostedType, ArrayList<HostedMethod>> vtablesMap, Map<HostedType, BitSet> usedSlotsMap, Map<HostedMethod, Set<Integer>> vtablesSlots) {
        for (HostedMethod method : type.getAllDeclaredMethods()) {
            /* We only need to look at methods that the static analysis registred as invoked. */
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
            assignImplementations(subtype, method, slot, vtablesMap);
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
            collectUsedSlots(sub, usedSlots, usedSlotsMap);
        }
    }

    private void markSlotAsUsed(int resultSlot, HostedType type, Map<HostedType, ArrayList<HostedMethod>> vtablesMap, Map<HostedType, BitSet> usedSlotsMap) {
        assert resultSlot >= vtablesMap.get(type).size() || vtablesMap.get(type).get(resultSlot) == null;

        usedSlotsMap.get(type).set(resultSlot);
        for (HostedType sub : type.subTypes) {
            markSlotAsUsed(resultSlot, sub, vtablesMap, usedSlotsMap);
        }
    }

    private void buildHubs() {
        InstanceReferenceMapEncoder referenceMapEncoder = new InstanceReferenceMapEncoder();
        Map<HostedType, ReferenceMapEncoder.Input> referenceMaps = new HashMap<>();
        for (HostedType type : hUniverse.orderedTypes) {
            ReferenceMapEncoder.Input referenceMap = createReferenceMap(type);
            assert ((SubstrateReferenceMap) referenceMap).hasNoDerivedOffsets();
            referenceMaps.put(type, referenceMap);
            referenceMapEncoder.add(referenceMap);
        }
        ImageSingletons.lookup(DynamicHubSupport.class).setData(referenceMapEncoder.encodeAll());

        ObjectLayout ol = ConfigurationValues.getObjectLayout();
        for (HostedType type : hUniverse.orderedTypes) {
            int layoutHelper;
            int monitorOffset = 0;
            int hashCodeOffset = 0;
            if (type.isInstanceClass()) {
                HostedInstanceClass instanceClass = (HostedInstanceClass) type;
                if (instanceClass.isAbstract()) {
                    layoutHelper = LayoutEncoding.forAbstract();
                } else if (HybridLayout.isHybrid(type)) {
                    HybridLayout<?> hybridLayout = new HybridLayout<>(instanceClass, ol);
                    JavaKind storageKind = hybridLayout.getArrayElementStorageKind();
                    boolean isObject = (storageKind == JavaKind.Object);
                    layoutHelper = LayoutEncoding.forArray(type, isObject, hybridLayout.getArrayBaseOffset(), ol.getArrayIndexShift(storageKind));
                } else {
                    layoutHelper = LayoutEncoding.forInstance(type, ConfigurationValues.getObjectLayout().alignUp(instanceClass.getInstanceSize()));
                }
                monitorOffset = instanceClass.getMonitorFieldOffset();
                hashCodeOffset = instanceClass.getHashCodeFieldOffset();
            } else if (type.isArray()) {
                JavaKind storageKind = type.getComponentType().getStorageKind();
                boolean isObject = (storageKind == JavaKind.Object);
                layoutHelper = LayoutEncoding.forArray(type, isObject, ol.getArrayBaseOffset(storageKind), ol.getArrayIndexShift(storageKind));
                hashCodeOffset = ol.getArrayIdentityHashcodeOffset();
            } else if (type.isInterface()) {
                layoutHelper = LayoutEncoding.forInterface();
            } else if (type.isPrimitive()) {
                layoutHelper = LayoutEncoding.forPrimitive();
            } else {
                throw shouldNotReachHere();
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
            long referenceMapIndex = referenceMapEncoder.lookupEncoding(referenceMap);

            DynamicHub hub = type.getHub();
            hub.setData(layoutHelper, type.getTypeID(), monitorOffset, hashCodeOffset, type.getAssignableFromMatches(), type.instanceOfBits, vtable, referenceMapIndex,
                            type.isInstantiated());
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

    private static boolean assertSame(Collection<HostedType> c1, Collection<HostedType> c2) {
        List<HostedType> list1 = new ArrayList<>(c1);
        List<HostedType> list2 = new ArrayList<>(c2);
        Collections.sort(list1);
        Collections.sort(list2);

        for (int i = 0; i < Math.min(list1.size(), list2.size()); i++) {
            assert list1.get(i) == list2.get(i);
        }
        assert list1.size() == list2.size();
        return true;
    }
}
