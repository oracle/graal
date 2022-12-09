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
package com.oracle.graal.pointsto.flow.context.bytecode;

import static com.oracle.graal.pointsto.util.ListUtils.getTLArrayList;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.BitSet;

import org.graalvm.compiler.options.OptionValues;

import com.oracle.graal.pointsto.AnalysisPolicy;
import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.api.PointstoOptions;
import com.oracle.graal.pointsto.flow.AbstractSpecialInvokeTypeFlow;
import com.oracle.graal.pointsto.flow.AbstractStaticInvokeTypeFlow;
import com.oracle.graal.pointsto.flow.AbstractVirtualInvokeTypeFlow;
import com.oracle.graal.pointsto.flow.ActualReturnTypeFlow;
import com.oracle.graal.pointsto.flow.ArrayElementsTypeFlow;
import com.oracle.graal.pointsto.flow.CallSiteSensitiveMethodTypeFlow;
import com.oracle.graal.pointsto.flow.CloneTypeFlow;
import com.oracle.graal.pointsto.flow.ContextInsensitiveFieldTypeFlow;
import com.oracle.graal.pointsto.flow.FieldTypeFlow;
import com.oracle.graal.pointsto.flow.InvokeTypeFlow;
import com.oracle.graal.pointsto.flow.MethodFlowsGraph;
import com.oracle.graal.pointsto.flow.MethodFlowsGraphClone;
import com.oracle.graal.pointsto.flow.MethodTypeFlow;
import com.oracle.graal.pointsto.flow.ProxyTypeFlow;
import com.oracle.graal.pointsto.flow.TypeFlow;
import com.oracle.graal.pointsto.flow.context.AnalysisContext;
import com.oracle.graal.pointsto.flow.context.object.AllocationContextSensitiveObject;
import com.oracle.graal.pointsto.flow.context.object.AnalysisObject;
import com.oracle.graal.pointsto.flow.context.object.ConstantContextSensitiveObject;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.meta.PointsToAnalysisMethod;
import com.oracle.graal.pointsto.typestate.MultiTypeState;
import com.oracle.graal.pointsto.typestate.PointsToStats;
import com.oracle.graal.pointsto.typestate.SingleTypeState;
import com.oracle.graal.pointsto.typestate.TypeState;
import com.oracle.graal.pointsto.typestate.TypeStateUtils;
import com.oracle.graal.pointsto.typestore.ArrayElementsTypeStore;
import com.oracle.graal.pointsto.typestore.FieldTypeStore;
import com.oracle.graal.pointsto.typestore.SplitArrayElementsTypeStore;
import com.oracle.graal.pointsto.typestore.SplitFieldTypeStore;
import com.oracle.graal.pointsto.typestore.UnifiedArrayElementsTypeStore;
import com.oracle.graal.pointsto.typestore.UnifiedFieldTypeStore;
import com.oracle.graal.pointsto.util.ListUtils;
import com.oracle.graal.pointsto.util.ListUtils.UnsafeArrayList;
import com.oracle.graal.pointsto.util.ListUtils.UnsafeArrayListClosable;

import jdk.vm.ci.code.BytecodePosition;
import jdk.vm.ci.meta.JavaConstant;

public class BytecodeSensitiveAnalysisPolicy extends AnalysisPolicy {

    private final BytecodeAnalysisContextPolicy contextPolicy;

    public BytecodeSensitiveAnalysisPolicy(OptionValues options) {
        super(options);
        this.contextPolicy = new BytecodeAnalysisContextPolicy();
    }

    @Override
    public boolean isContextSensitiveAnalysis() {
        return true;
    }

    public BytecodeAnalysisContextPolicy getContextPolicy() {
        return contextPolicy;
    }

    @Override
    public MethodTypeFlow createMethodTypeFlow(PointsToAnalysisMethod method) {
        return new CallSiteSensitiveMethodTypeFlow(options, method);
    }

    @Override
    public boolean needsConstantCache() {
        return true;
    }

    @Override
    public boolean isSummaryObject(AnalysisObject object) {
        /* Context insensitive objects summarize context sensitive objects of the same type. */
        return object.isContextInsensitiveObject();
    }

    @Override
    public boolean isMergingEnabled() {
        // the context sensitive analysis relies on proper signal of merging
        return true;
    }

    @Override
    public void noteMerge(PointsToAnalysis bb, TypeState t) {
        t.noteMerge(bb);
    }

    @Override
    public void noteMerge(PointsToAnalysis bb, AnalysisObject... a) {
        for (AnalysisObject o : a) {
            o.noteMerge(bb);
        }
    }

    @Override
    public void noteMerge(PointsToAnalysis bb, AnalysisObject o) {
        o.noteMerge(bb);
    }

    @Override
    public boolean isContextSensitiveAllocation(PointsToAnalysis bb, AnalysisType type, AnalysisContext allocationContext) {
        return bb.trackConcreteAnalysisObjects(type);
    }

    @Override
    public AnalysisObject createHeapObject(PointsToAnalysis bb, AnalysisType type, BytecodePosition allocationSite, AnalysisContext allocationContext) {
        assert PointstoOptions.AllocationSiteSensitiveHeap.getValue(options);
        if (isContextSensitiveAllocation(bb, type, allocationContext)) {
            return new AllocationContextSensitiveObject(bb, type, allocationSite, allocationContext);
        } else {
            return type.getContextInsensitiveAnalysisObject();
        }
    }

    @Override
    public AnalysisObject createConstantObject(PointsToAnalysis bb, JavaConstant constant, AnalysisType exactType) {
        /* Get the analysis object wrapping the JavaConstant. */
        if (bb.trackConcreteAnalysisObjects(exactType)) {
            return exactType.getCachedConstantObject(bb, constant, (c) -> new ConstantContextSensitiveObject(bb, exactType, c));
        } else {
            return exactType.getContextInsensitiveAnalysisObject();
        }
    }

    @Override
    public TypeState constantTypeState(PointsToAnalysis bb, JavaConstant constant, AnalysisType exactType) {
        AnalysisObject constantObject = bb.analysisPolicy().createConstantObject(bb, constant, exactType);
        return TypeState.forNonNullObject(bb, constantObject);
    }

    @Override
    public TypeState dynamicNewInstanceState(PointsToAnalysis bb, TypeState currentState, TypeState newState, BytecodePosition allocationSite, AnalysisContext allocationContext) {
        /* Generate a heap object for every new incoming type. */
        TypeState resultState = TypeState.forEmpty();
        for (AnalysisType type : newState.types(bb)) {
            if (!currentState.containsType(type)) {
                TypeState typeState = forAllocation(bb, allocationSite, type, allocationContext);
                resultState = TypeState.forUnion(bb, resultState, typeState);
            }
        }
        assert !resultState.canBeNull();
        return resultState;
    }

    @Override
    public TypeState cloneState(PointsToAnalysis bb, TypeState currentState, TypeState inputState, BytecodePosition cloneSite, AnalysisContext allocationContext) {
        TypeState resultState;
        if (inputState.isEmpty() || inputState.isNull()) {
            /* Nothing to be cloned if the input state is not a concrete type state. */
            resultState = inputState.forNonNull(bb);
        } else {
            resultState = TypeState.forEmpty();
            for (AnalysisType type : inputState.types(bb)) {
                if (!currentState.containsType(type)) {
                    TypeState typeState = forClone(bb, cloneSite, type, allocationContext);
                    resultState = TypeState.forUnion(bb, resultState, typeState);
                }
            }
        }
        assert !resultState.canBeNull();
        return resultState;
    }

    /**
     * Wraps the analysis object corresponding to a clone site for a given context into a non-null
     * type state.
     */
    private static TypeState forClone(PointsToAnalysis bb, BytecodePosition cloneSite, AnalysisType type, AnalysisContext allocationContext) {
        return forAllocation(bb, cloneSite, type, allocationContext);
    }

    /**
     * Wraps the analysis object corresponding to an allocation site for a given context into a
     * non-null type state.
     */
    private static TypeState forAllocation(PointsToAnalysis bb, BytecodePosition allocationSite, AnalysisType objectType, AnalysisContext allocationContext) {
        assert objectType.isArray() || (objectType.isInstanceClass() && !Modifier.isAbstract(objectType.getModifiers())) : objectType;

        AnalysisObject allocationObject = bb.analysisPolicy().createHeapObject(bb, objectType, allocationSite, allocationContext);
        return TypeState.forNonNullObject(bb, allocationObject);
    }

    @Override
    public void linkClonedObjects(PointsToAnalysis bb, TypeFlow<?> inputFlow, CloneTypeFlow cloneFlow, BytecodePosition source) {
        TypeState inputState = inputFlow.getState();
        TypeState cloneState = cloneFlow.getState();

        for (AnalysisType type : inputState.types(bb)) {
            if (type.isArray()) {
                if (bb.analysisPolicy().aliasArrayTypeFlows()) {
                    /* All arrays are aliased, no need to model the array clone operation. */
                    continue;
                }

                /* The object array clones must also get the elements flows of the originals. */
                for (AnalysisObject originalObject : inputState.objects(type)) {
                    if (originalObject.isPrimitiveArray() || originalObject.isEmptyObjectArrayConstant(bb)) {
                        /* Nothing to read from a primitive array or an empty array constant. */
                        continue;
                    }
                    ArrayElementsTypeFlow originalObjectElementsFlow = originalObject.getArrayElementsFlow(bb, false);

                    for (AnalysisObject cloneObject : cloneState.objects(type)) {
                        if (cloneObject.isPrimitiveArray() || cloneObject.isEmptyObjectArrayConstant(bb)) {
                            /* Cannot write to a primitive array or an empty array constant. */
                            continue;
                        }
                        ArrayElementsTypeFlow cloneObjectElementsFlow = cloneObject.getArrayElementsFlow(bb, true);
                        originalObjectElementsFlow.addUse(bb, cloneObjectElementsFlow);
                    }
                }
            } else {

                /* The object clones must get field flows of the originals. */
                for (AnalysisObject originalObject : inputState.objects(type)) {
                    /* Link all the field flows of the original to the clone. */
                    for (AnalysisField field : type.getInstanceFields(true)) {
                        FieldTypeFlow originalObjectFieldFlow = originalObject.getInstanceFieldFlow(bb, inputFlow, source, field, false);

                        for (AnalysisObject cloneObject : cloneState.objects(type)) {
                            FieldTypeFlow cloneObjectFieldFlow = cloneObject.getInstanceFieldFlow(bb, cloneFlow, source, field, true);
                            originalObjectFieldFlow.addUse(bb, cloneObjectFieldFlow);
                        }
                    }
                }
            }
        }
    }

    @Override
    public FieldTypeStore createFieldTypeStore(PointsToAnalysis bb, AnalysisObject object, AnalysisField field, AnalysisUniverse universe) {
        assert PointstoOptions.AllocationSiteSensitiveHeap.getValue(options);
        if (object.isContextInsensitiveObject()) {
            /*
             * Write flow is context-sensitive and read flow is context-insensitive. This split is
             * used to model context sensitivity and context merging for fields of this
             * context-insensitive object, and the interaction with the fields of context-sensitive
             * objects of the same type.
             * 
             * All values written to fields of context-sensitive receivers are also reflected to the
             * context-insensitive receiver *read* flow, but without any context information, such
             * that all the reads from the fields of the context insensitive object reflect all the
             * types written to the context-sensitive ones, but without triggering merging.
             * 
             * Once the context-sensitive receiver object is marked as merged, i.e., it looses its
             * context sensitivity, the field flows are routed to the context-insensitive receiver
             * *write* flow, thus triggering their merging. See ContextSensitiveAnalysisObject.
             * mergeInstanceFieldFlow().
             */
            FieldTypeFlow writeFlow = new FieldTypeFlow(field, field.getType(), object);
            ContextInsensitiveFieldTypeFlow readFlow = new ContextInsensitiveFieldTypeFlow(field, field.getType(), object);
            return new SplitFieldTypeStore(field, object, writeFlow, readFlow);
        } else {
            return new UnifiedFieldTypeStore(field, object);
        }
    }

    @Override
    public ArrayElementsTypeStore createArrayElementsTypeStore(AnalysisObject object, AnalysisUniverse universe) {
        assert PointstoOptions.AllocationSiteSensitiveHeap.getValue(options);
        if (object.type().isArray()) {
            if (aliasArrayTypeFlows) {
                /* Alias all array type flows using the elements type flow model of Object type. */
                if (object.type().getElementalType().isJavaLangObject() && object.isContextInsensitiveObject()) {
                    // return getArrayElementsTypeStore(object);
                    return new UnifiedArrayElementsTypeStore(object);
                }
                return universe.objectType().getArrayClass().getContextInsensitiveAnalysisObject().getArrayElementsTypeStore();
            }
            return getArrayElementsTypeStore(object);
        } else {
            return null;
        }
    }

    private static ArrayElementsTypeStore getArrayElementsTypeStore(AnalysisObject object) {
        if (object.isContextInsensitiveObject()) {
            return new SplitArrayElementsTypeStore(object);
        } else {
            return new UnifiedArrayElementsTypeStore(object);
        }
    }

    @Override
    public AbstractVirtualInvokeTypeFlow createVirtualInvokeTypeFlow(BytecodePosition invokeLocation, AnalysisType receiverType, PointsToAnalysisMethod targetMethod,
                    TypeFlow<?>[] actualParameters, ActualReturnTypeFlow actualReturn) {
        return new BytecodeSensitiveVirtualInvokeTypeFlow(invokeLocation, receiverType, targetMethod, actualParameters, actualReturn);
    }

    @Override
    public AbstractSpecialInvokeTypeFlow createSpecialInvokeTypeFlow(BytecodePosition invokeLocation, AnalysisType receiverType, PointsToAnalysisMethod targetMethod,
                    TypeFlow<?>[] actualParameters, ActualReturnTypeFlow actualReturn) {
        return new BytecodeSensitiveSpecialInvokeTypeFlow(invokeLocation, receiverType, targetMethod, actualParameters, actualReturn);
    }

    @Override
    public AbstractStaticInvokeTypeFlow createStaticInvokeTypeFlow(BytecodePosition invokeLocation, AnalysisType receiverType, PointsToAnalysisMethod targetMethod,
                    TypeFlow<?>[] actualParameters, ActualReturnTypeFlow actualReturn) {
        return new BytecodeSensitiveStaticInvokeTypeFlow(invokeLocation, receiverType, targetMethod, actualParameters, actualReturn);
    }

    @Override
    public MethodFlowsGraph staticRootMethodGraph(PointsToAnalysis bb, PointsToAnalysisMethod pointsToMethod) {
        return ((CallSiteSensitiveMethodTypeFlow) pointsToMethod.getTypeFlow()).addContext(bb, contextPolicy.emptyContext(), null);
    }

    @Override
    public AnalysisContext allocationContext(PointsToAnalysis bb, MethodFlowsGraph callerGraph) {
        return contextPolicy.allocationContext((BytecodeAnalysisContext) ((MethodFlowsGraphClone) callerGraph).context(), PointstoOptions.MaxHeapContextDepth.getValue(bb.getOptions()));
    }

    @Override
    public TypeFlow<?> proxy(BytecodePosition source, TypeFlow<?> input) {
        return new ProxyTypeFlow(source, input);
    }

    @Override
    public boolean addOriginalUse(PointsToAnalysis bb, TypeFlow<?> flow, TypeFlow<?> use) {
        /* Adds the use, if not already present, without propagating the type state. */
        return flow.addUse(bb, use, false);
    }

    @Override
    public boolean addOriginalObserver(PointsToAnalysis bb, TypeFlow<?> flow, TypeFlow<?> observer) {
        /* Adds the observer, if not already present, without triggering an update. */
        return flow.addObserver(bb, observer, false);
    }

    @Override
    public void linkActualReturn(PointsToAnalysis bb, boolean isStatic, InvokeTypeFlow invoke) {
        /* Nothing to do, the cloning mechanism does all the linking. */
    }

    @Override
    public void registerAsImplementationInvoked(InvokeTypeFlow invoke, MethodFlowsGraph calleeFlows) {
        if (invoke.isContextInsensitive()) {
            calleeFlows.getMethod().registerAsImplementationInvoked(invoke);
        } else {
            calleeFlows.getMethod().registerAsImplementationInvoked(invoke.getOriginalInvoke());
        }
    }

    static BytecodeAnalysisContextPolicy contextPolicy(BigBang bb) {
        return ((BytecodeSensitiveAnalysisPolicy) bb.analysisPolicy()).getContextPolicy();
    }

    @Override
    public TypeState forContextInsensitiveTypeState(PointsToAnalysis bb, TypeState state) {
        if (state.isEmpty() || state.isNull()) {
            /* The type state is already context insensitive. */
            return state;
        } else {
            if (state instanceof SingleTypeState) {
                AnalysisType type = state.exactType();
                AnalysisObject analysisObject = type.getContextInsensitiveAnalysisObject();
                return singleTypeState(bb, state.canBeNull(), makeProperties(bb, analysisObject), analysisObject.type(), analysisObject);
            } else {
                ContextSensitiveMultiTypeState multiState = (ContextSensitiveMultiTypeState) state;
                AnalysisObject[] objectsArray = new AnalysisObject[multiState.typesCount()];

                int i = 0;
                for (AnalysisType type : multiState.types(bb)) {
                    objectsArray[i++] = type.getContextInsensitiveAnalysisObject();
                }
                /*
                 * For types use the already created bit set. Since the original type state is
                 * immutable its types bit set cannot change.
                 */

                BitSet typesBitSet = multiState.bitSet();
                int properties = makeProperties(bb, objectsArray);
                return multiTypeState(bb, multiState.canBeNull(), properties, typesBitSet, objectsArray);
            }
        }
    }

    @Override
    public SingleTypeState singleTypeState(PointsToAnalysis bb, boolean canBeNull, int properties, AnalysisType type, AnalysisObject... objects) {
        return new ContextSensitiveSingleTypeState(bb, canBeNull, properties, type, objects);
    }

    @Override
    public MultiTypeState multiTypeState(PointsToAnalysis bb, boolean canBeNull, int properties, BitSet typesBitSet, AnalysisObject... objects) {
        return new ContextSensitiveMultiTypeState(bb, canBeNull, properties, typesBitSet, objects);
    }

    @Override
    public TypeState doUnion(PointsToAnalysis bb, SingleTypeState state1, SingleTypeState state2) {
        if (state1.equals(state2)) {
            return state1;
        }

        ContextSensitiveSingleTypeState s1 = (ContextSensitiveSingleTypeState) state1;
        ContextSensitiveSingleTypeState s2 = (ContextSensitiveSingleTypeState) state2;

        boolean resultCanBeNull = s1.canBeNull() || s2.canBeNull();
        if (s1.exactType().equals(s2.exactType())) {

            /* The inputs have the same type, so the result is a SingleTypeState. */

            /* Create the resulting objects array. */
            AnalysisObject[] resultObjects = TypeStateUtils.union(bb, s1.objects, s2.objects);

            /* Check if any of the arrays contains the other. */
            if (resultObjects == s1.objects) {
                return s1.forCanBeNull(bb, resultCanBeNull);
            } else if (resultObjects == s2.objects) {
                return s2.forCanBeNull(bb, resultCanBeNull);
            }

            /* Due to the test above the union set cannot be equal to any of the two arrays. */
            assert !bb.extendedAsserts() || !Arrays.equals(resultObjects, s1.objects) && !Arrays.equals(resultObjects, s2.objects);

            /* Create the resulting exact type state. */
            SingleTypeState result = new ContextSensitiveSingleTypeState(bb, resultCanBeNull, bb.analysisPolicy().makePropertiesForUnion(s1, s2), s1.exactType(), resultObjects);
            assert !s1.equals(result) && !s2.equals(result);
            PointsToStats.registerUnionOperation(bb, s1, s2, result);
            return result;
        } else {
            /* The inputs have different types, so the result is a MultiTypeState. */
            AnalysisObject[] resultObjects;
            if (s1.exactType().getId() < s2.exactType().getId()) {
                resultObjects = TypeStateUtils.concat(s1.objects, s2.objects);
            } else {
                resultObjects = TypeStateUtils.concat(s2.objects, s1.objects);
            }

            /* We know the types, construct the types bit set without walking the objects. */
            BitSet typesBitSet = TypeStateUtils.newBitSet(s1.exactType().getId(), s2.exactType().getId());
            int properties = bb.analysisPolicy().makePropertiesForUnion(s1, s2);
            TypeState result = new ContextSensitiveMultiTypeState(bb, resultCanBeNull, properties, typesBitSet, resultObjects);
            PointsToStats.registerUnionOperation(bb, s1, s2, result);
            return result;
        }
    }

    @Override
    public TypeState doUnion(PointsToAnalysis bb, MultiTypeState state1, SingleTypeState state2) {

        ContextSensitiveMultiTypeState s1 = (ContextSensitiveMultiTypeState) state1;
        ContextSensitiveSingleTypeState s2 = (ContextSensitiveSingleTypeState) state2;

        boolean resultCanBeNull = s1.canBeNull() || s2.canBeNull();

        AnalysisObject[] so1 = s1.objects;
        AnalysisObject[] so2 = s2.objects;
        if (so2.length == 1 && containsObject(s1, so2[0])) {
            /*
             * Speculate that s2 has a single object and s1 already contains that object. This
             * happens often during object scanning where we repeatedly add the scanned constants to
             * field or array elements flows. The binary search executed by containsObject should be
             * faster than the linear search below.
             */
            return s1.forCanBeNull(bb, resultCanBeNull);
        }

        if (s1.containsType(s2.exactType())) {
            /* Objects of the same type as s2 are contained in s1. */

            /* Get the range of objects in s1 corresponding to the type of s2. */
            ContextSensitiveMultiTypeState.Range typeRange = s1.findTypeRange(s2.exactType());
            /* Get the slice of objects in s1 corresponding to the type of s2. */
            AnalysisObject[] s1ObjectsSlice = s1.objectsArray(typeRange);

            /* Create the resulting objects array. */
            AnalysisObject[] unionObjects = TypeStateUtils.union(bb, s1ObjectsSlice, so2);

            /* Check if s1 contains s2's objects for this type. */
            if (unionObjects == s1ObjectsSlice) {
                return s1.forCanBeNull(bb, resultCanBeNull);
            }

            /*
             * Due to the test above and to the fact that TypeStateUtils.union checks if one array
             * contains the other the union set cannot be equal to s1's objects slice.
             */
            assert !bb.extendedAsserts() || !Arrays.equals(unionObjects, s1ObjectsSlice);

            /*
             * Replace the s1 objects slice of the same type as s2 with the union objects and create
             * a new state.
             */
            int resultSize = so1.length + unionObjects.length - s1ObjectsSlice.length;
            AnalysisObject[] resultObjects = new AnalysisObject[resultSize];

            System.arraycopy(so1, 0, resultObjects, 0, typeRange.left());
            System.arraycopy(unionObjects, 0, resultObjects, typeRange.left(), unionObjects.length);
            System.arraycopy(so1, typeRange.right(), resultObjects, typeRange.left() + unionObjects.length, so1.length - typeRange.right());

            /* The types bit set of the result and s1 are the same. */

            int properties = bb.analysisPolicy().makePropertiesForUnion(s1, s2);

            MultiTypeState result = new ContextSensitiveMultiTypeState(bb, resultCanBeNull, properties, s1.bitSet(), resultObjects);
            assert !result.equals(s1);
            /*
             * No need to check the result size against the all-instantiated since the type count
             * didn't change.
             */
            PointsToStats.registerUnionOperation(bb, s1, s2, result);
            return result;
        } else {
            AnalysisObject[] resultObjects;
            if (s2.exactType().getId() < s1.firstType().getId()) {
                resultObjects = TypeStateUtils.concat(so2, so1);
            } else if (s2.exactType().getId() > s1.lastType().getId()) {
                resultObjects = TypeStateUtils.concat(so1, so2);
            } else {

                /* Find insertion point within the s1.objects. */
                int idx1 = 0;
                while (idx1 < so1.length && so1[idx1].getTypeId() < s2.exactType().getId()) {
                    idx1++;
                }

                /* Create the resulting objects array and insert the s2 objects. */
                resultObjects = new AnalysisObject[so1.length + so2.length];

                System.arraycopy(so1, 0, resultObjects, 0, idx1);
                System.arraycopy(so2, 0, resultObjects, idx1, so2.length);
                System.arraycopy(so1, idx1, resultObjects, idx1 + so2.length, so1.length - idx1);
            }

            /* Create the types bit set by adding the s2 type to avoid walking the objects. */
            BitSet typesBitSet = TypeStateUtils.set(s1.bitSet(), s2.exactType().getId());
            int properties = bb.analysisPolicy().makePropertiesForUnion(s1, s2);

            MultiTypeState result = new ContextSensitiveMultiTypeState(bb, resultCanBeNull, properties, typesBitSet, resultObjects);
            PointsToStats.registerUnionOperation(bb, s1, s2, result);
            return result;
        }
    }

    /** Returns true if this type state contains the object, otherwise it returns false. */
    public static boolean containsObject(ContextSensitiveMultiTypeState state, AnalysisObject object) {
        /* AnalysisObject implements Comparable and the objects array is always sorted by ID. */
        return state.containsType(object.type()) && Arrays.binarySearch(state.objects, object) >= 0;
    }

    @Override
    public TypeState doUnion(PointsToAnalysis bb, MultiTypeState state1, MultiTypeState state2) {
        ContextSensitiveMultiTypeState s1 = (ContextSensitiveMultiTypeState) state1;
        ContextSensitiveMultiTypeState s2 = (ContextSensitiveMultiTypeState) state2;

        assert s1.objectsCount() >= s2.objectsCount() : "Union is commutative, must call it with s1 being the bigger state";
        boolean resultCanBeNull = s1.canBeNull() || s2.canBeNull();

        /*
         * No need for a deep equality check (which would need to iterate the arrays), since the
         * speculation logic below is doing that anyway.
         */
        if (s1.objects == s2.objects) {
            return s1.forCanBeNull(bb, resultCanBeNull);
        }

        return doUnion0(bb, s1, s2, resultCanBeNull);
    }

    private static TypeState doUnion0(PointsToAnalysis bb, ContextSensitiveMultiTypeState s1, ContextSensitiveMultiTypeState s2, boolean resultCanBeNull) {

        /* Speculate that s1 and s2 are distinct sets. */

        if (s1.lastType().getId() < s2.firstType().getId()) {
            /* Speculate that objects in s2 follow after objects in s1. */

            /* Concatenate the objects. */
            AnalysisObject[] resultObjects = TypeStateUtils.concat(s1.objects, s2.objects);

            /* Logical OR the type bit sets. */
            BitSet resultTypesBitSet = TypeStateUtils.or(s1.bitSet(), s2.bitSet());
            int properties = bb.analysisPolicy().makePropertiesForUnion(s1, s2);

            MultiTypeState result = new ContextSensitiveMultiTypeState(bb, resultCanBeNull, properties, resultTypesBitSet, resultObjects);
            PointsToStats.registerUnionOperation(bb, s1, s2, result);
            return result;

        } else if (s2.lastType().getId() < s1.firstType().getId()) {
            /* Speculate that objects in s1 follow after objects in s2. */

            /* Concatenate the objects. */
            AnalysisObject[] resultObjects = TypeStateUtils.concat(s2.objects, s1.objects);

            /* Logical OR the type bit sets. */
            BitSet resultTypesBitSet = TypeStateUtils.or(s1.bitSet(), s2.bitSet());
            int properties = bb.analysisPolicy().makePropertiesForUnion(s1, s2);

            MultiTypeState result = new ContextSensitiveMultiTypeState(bb, resultCanBeNull, properties, resultTypesBitSet, resultObjects);
            PointsToStats.registerUnionOperation(bb, s1, s2, result);
            return result;
        }

        return doUnion1(bb, s1, s2, resultCanBeNull);
    }

    private static TypeState doUnion1(PointsToAnalysis bb, ContextSensitiveMultiTypeState s1, ContextSensitiveMultiTypeState s2, boolean resultCanBeNull) {
        if (PointstoOptions.AllocationSiteSensitiveHeap.getValue(bb.getOptions())) {
            return allocationSensitiveSpeculativeUnion1(bb, s1, s2, resultCanBeNull);
        } else {
            return allocationInsensitiveSpeculativeUnion1(bb, s1, s2, resultCanBeNull);
        }
    }

    /**
     * Optimization that gives 1.5-3x in performance for the (typeflow) phase.
     */
    private static TypeState allocationInsensitiveSpeculativeUnion1(PointsToAnalysis bb, ContextSensitiveMultiTypeState s1, ContextSensitiveMultiTypeState s2, boolean resultCanBeNull) {
        if (s1.bitSet().length() >= s2.bitSet().length()) {
            long[] bits1 = TypeStateUtils.extractBitSetField(s1.bitSet());
            long[] bits2 = TypeStateUtils.extractBitSetField(s2.bitSet());
            assert s2.bitSet().cardinality() == s2.objects.length : "Cardinality and length of objects must match.";

            boolean speculate = true;
            int numberOfWords = Math.min(bits1.length, bits2.length);
            for (int i = 0; i < numberOfWords; i++) {
                /* bits2 is a subset of bits1 */
                if ((bits1[i] & bits2[i]) != bits2[i]) {
                    speculate = false;
                    break;
                }
            }
            if (speculate) {
                return s1.forCanBeNull(bb, resultCanBeNull);
            }
        }
        return doUnion2(bb, s1, s2, resultCanBeNull, 0, 0);
    }

    private static TypeState allocationSensitiveSpeculativeUnion1(PointsToAnalysis bb, ContextSensitiveMultiTypeState s1, ContextSensitiveMultiTypeState s2, boolean resultCanBeNull) {
        int idx1 = 0;
        int idx2 = 0;
        AnalysisPolicy analysisPolicy = bb.analysisPolicy();
        AnalysisObject[] so1 = s1.objects;
        AnalysisObject[] so2 = s2.objects;
        while (idx1 < so1.length && idx2 < so2.length) {
            AnalysisObject o1 = so1[idx1];
            AnalysisObject o2 = so2[idx2];
            if (analysisPolicy.isSummaryObject(o1) && o1.getTypeId() == o2.getTypeId()) {
                idx1++;
                /* Skip over s2 objects of this type while marking them as merged. */
                while (idx2 < s2.objectsCount() && so2[idx2].getTypeId() == o1.getTypeId()) {
                    analysisPolicy.noteMerge(bb, so2[idx2]);
                    idx2++;
                }
            } else if (o1.getId() < o2.getId()) {
                idx1++;
            } else if (o1.getId() == o2.getId()) {
                /* If the objects are equal continue. */
                idx1++;
                idx2++;
            } else {
                /* Our speculation failed. */
                break;
            }

            if (idx2 == so2.length) {
                return s1.forCanBeNull(bb, resultCanBeNull);
            }
        }
        return doUnion2(bb, s1, s2, resultCanBeNull, idx1, idx2);
    }

    private static final ThreadLocal<UnsafeArrayListClosable<AnalysisObject>> doUnion2TL = new ThreadLocal<>();
    private static final ThreadLocal<UnsafeArrayListClosable<AnalysisObject>> doUnion2ObjectsTL = new ThreadLocal<>();

    private static TypeState doUnion2(PointsToAnalysis bb, ContextSensitiveMultiTypeState s1, ContextSensitiveMultiTypeState s2, boolean resultCanBeNull, int startId1, int startId2) {
        try (UnsafeArrayListClosable<AnalysisObject> resultObjectsClosable = getTLArrayList(doUnion2TL, s1.objects.length + s2.objects.length)) {
            UnsafeArrayList<AnalysisObject> resultObjects = resultObjectsClosable.list();
            /* Add the beginning of the s1 list that we already walked above. */
            AnalysisObject[] objects = s1.objects;
            resultObjects.addAll(objects, 0, startId1);

            int idx1 = startId1;
            int idx2 = startId2;

            /* Create the union of the overlapping sections of the s1 and s2. */
            try (UnsafeArrayListClosable<AnalysisObject> tlUnionObjectsClosable = getTLArrayList(doUnion2ObjectsTL, s1.objects.length + s2.objects.length)) {
                UnsafeArrayList<AnalysisObject> unionObjects = tlUnionObjectsClosable.list();

                AnalysisObject[] so1 = s1.objects;
                AnalysisObject[] so2 = s2.objects;
                AnalysisPolicy analysisPolicy = bb.analysisPolicy();
                while (idx1 < so1.length && idx2 < so2.length) {
                    AnalysisObject o1 = so1[idx1];
                    AnalysisObject o2 = so2[idx2];
                    int t1 = o1.getTypeId();
                    int t2 = o2.getTypeId();
                    if (analysisPolicy.isSummaryObject(o1) && t1 == t2) {
                        unionObjects.add(o1);
                        /* Skip over s2 objects of this type while marking them as merged. */
                        while (idx2 < so2.length && t1 == so2[idx2].getTypeId()) {
                            analysisPolicy.noteMerge(bb, so2[idx2]);
                            idx2++;
                        }
                        idx1++;
                    } else if (analysisPolicy.isSummaryObject(o2) && t1 == t2) {
                        unionObjects.add(o2);
                        /* Skip over s1 objects of this type while marking them as merged. */
                        while (idx1 < so1.length && so1[idx1].getTypeId() == t2) {
                            analysisPolicy.noteMerge(bb, so1[idx1]);
                            idx1++;
                        }
                        idx2++;
                    } else if (o1.getId() < o2.getId()) {
                        unionObjects.add(o1);
                        idx1++;
                    } else if (o1.getId() > o2.getId()) {
                        unionObjects.add(o2);
                        idx2++;
                    } else {
                        assert o1.equals(o2);
                        unionObjects.add(o1);
                        idx1++;
                        idx2++;
                    }
                }

                /*
                 * Check if the union of objects of a type in the overlapping section reached the
                 * limit. The limit, bb.options().maxObjectSetSize(), has a minimum value of 1.
                 */
                if (PointstoOptions.LimitObjectArrayLength.getValue(bb.getOptions()) && unionObjects.size() > PointstoOptions.MaxObjectSetSize.getValue(bb.getOptions())) {
                    int idxStart = 0;
                    int idxEnd = 0;
                    while (idxEnd < unionObjects.size()) {
                        AnalysisObject oStart = unionObjects.get(idxStart);

                        /* While types are equal and the end is not reached, advance idxEnd. */
                        while (idxEnd < unionObjects.size() && oStart.equals(unionObjects.get(idxEnd))) {
                            idxEnd = idxEnd + 1;
                        }
                        /*
                         * Process the type change or, if idxEnd reached the end, process the last
                         * stride
                         */
                        int size = idxEnd - idxStart;
                        if (size > PointstoOptions.MaxObjectSetSize.getValue(bb.getOptions())) {
                            /*
                             * Object count exceeds the limit. Mark the objects in the stride as
                             * merged.
                             */
                            for (int i = idxStart; i < idxEnd; i += 1) {
                                bb.analysisPolicy().noteMerge(bb, unionObjects.get(i));
                            }
                            /* Add the context insensitive object in the result list. */
                            resultObjects.add(oStart.type().getContextInsensitiveAnalysisObject());
                        } else {
                            /* Object count is within the limit, add them to the result. */
                            resultObjects.addAll(unionObjects.elementData(), idxStart, idxEnd);
                        }
                        idxStart = idxEnd;
                    }

                } else {
                    resultObjects.addAll(unionObjects.elementData(), 0, unionObjects.size());
                }
            }

            /*
             * Add the leftover objects in the result list.
             *
             * Arrays.asList(a).subList(from, to) first creates a list wrapper over the array then
             * it creates a view of a portion of the list, thus it only allocates the list and
             * sub-list wrappers. Then ArrayList.addAll() calls System.arraycopy() which should be
             * more efficient than copying one element at a time.
             */
            if (idx1 < s1.objects.length) {
                resultObjects.addAll(s1.objects, idx1, s1.objects.length);
            } else if (idx2 < s2.objects.length) {
                resultObjects.addAll(s2.objects, idx2, s2.objects.length);
            }

            assert resultObjects.size() > 1 : "The result state of a (Multi U Multi) operation must have at least 2 objects";

            /* Logical OR the type bit sets. */
            BitSet resultTypesBitSet = TypeStateUtils.or(s1.bitSet(), s2.bitSet());
            int properties = bb.analysisPolicy().makePropertiesForUnion(s1, s2);

            MultiTypeState result = new ContextSensitiveMultiTypeState(bb, resultCanBeNull, properties, resultTypesBitSet, resultObjects.copyToArray(new AnalysisObject[resultObjects.size()]));
            assert !result.equals(s1) : "speculation code should prevent this case";

            /* The result can be equal to s2 only if s1 and s2 have the same number of types. */
            if (s1.typesCount() == s2.typesCount() && result.equals(s2)) {
                return s2.forCanBeNull(bb, resultCanBeNull);
            }

            PointsToStats.registerUnionOperation(bb, s1, s2, result);

            return result;
        }
    }

    /*
     * Implementation of intersection.
     *
     * The implementation of intersection is specific to our current use case, i.e., it is not a
     * general set intersection implementation. The limitation, checked by the assertions below,
     * refers to the fact that when we use intersection we only care about selecting all the objects
     * of a certain type or types, e.g., for filtering. We don't currently have a situation where we
     * only want to select a subset of objects of a type. In our use the types whose objects need to
     * be selected are always specified in s2 through their context insensitive objects, thus s2
     * must only contain context insensitive objects.
     */

    @Override
    public TypeState doIntersection(PointsToAnalysis bb, MultiTypeState s1, SingleTypeState s2) {
        /* See comment above for the limitation explanation. */
        assert !bb.extendedAsserts() || TypeStateUtils.isContextInsensitiveTypeState(bb, s2) : "Current implementation limitation.";

        boolean resultCanBeNull = s1.canBeNull() && s2.canBeNull();
        if (s1.containsType(s2.exactType())) {
            /* The s2's type is contained in s1, so pick all objects of the same type from s1. */
            AnalysisObject[] resultObjects = ((ContextSensitiveMultiTypeState) s1).objectsArray(s2.exactType());
            /* All objects must have the same type. */
            assert TypeStateUtils.holdsSingleTypeState(resultObjects);
            return new ContextSensitiveSingleTypeState(bb, resultCanBeNull, bb.analysisPolicy().makeProperties(bb, resultObjects), s2.exactType(), resultObjects);
        } else {
            return TypeState.forEmpty().forCanBeNull(bb, resultCanBeNull);
        }
    }

    @Override
    public TypeState doIntersection(PointsToAnalysis bb, MultiTypeState state1, MultiTypeState state2) {
        ContextSensitiveMultiTypeState s1 = (ContextSensitiveMultiTypeState) state1;
        ContextSensitiveMultiTypeState s2 = (ContextSensitiveMultiTypeState) state2;

        assert !bb.extendedAsserts() || TypeStateUtils.isContextInsensitiveTypeState(bb, s2) : "Current implementation limitation.";

        boolean resultCanBeNull = s1.canBeNull() && s2.canBeNull();

        /*
         * No need for a deep equality check (which would need to iterate the arrays), since the
         * speculation logic below is doing that anyway.
         */
        if (s1.objects == s2.objects) {
            return s1.forCanBeNull(bb, resultCanBeNull);
        }

        return doIntersection0(bb, s1, s2, resultCanBeNull);
    }

    private static TypeState doIntersection0(PointsToAnalysis bb, ContextSensitiveMultiTypeState s1, ContextSensitiveMultiTypeState s2, boolean resultCanBeNull) {
        /* Speculate that s1 and s2 have either the same types, or no types in common. */

        if (s1.bitSet().equals(s2.bitSet())) {
            /* Speculate that s1 and s2 have the same types, i.e., the result is s1. */
            return s1.forCanBeNull(bb, resultCanBeNull);
        }

        if (!s1.bitSet().intersects(s2.bitSet())) {
            /* Speculate that s1 and s2 have no types in common, i.e., the result is empty. */
            return TypeState.forEmpty().forCanBeNull(bb, resultCanBeNull);
        }

        return doIntersection1(bb, s1, s2, resultCanBeNull);
    }

    private static TypeState doIntersection1(PointsToAnalysis bb, ContextSensitiveMultiTypeState s1, ContextSensitiveMultiTypeState s2, boolean resultCanBeNull) {
        /*
         * Speculate that s2 contains all types of s1, i.e., the filter is broader than s1, thus the
         * result is s1.
         */

        int idx1 = 0;
        int idx2 = 0;
        AnalysisObject[] so1 = s1.objects;
        AnalysisObject[] so2 = s2.objects;
        while (idx2 < so2.length) {
            AnalysisObject o1 = so1[idx1];
            AnalysisObject o2 = so2[idx2];

            /* See comment above for the limitation explanation. */
            assert o2.isContextInsensitiveObject() : "Current implementation limitation.";

            if (o1.getTypeId() > o2.getTypeId()) {
                /* s2 is behind, advance s2. */
                idx2++;
            } else if (o1.getTypeId() == o2.getTypeId()) {
                /* If the types are equal continue with speculation. */
                while (idx1 < so1.length && so1[idx1].getTypeId() == o2.getTypeId()) {
                    /* Walk over the s1 objects of the same type as o2. */
                    idx1++;
                }
                idx2++;
            } else {
                /* Our speculation failed. */
                break;
            }

            if (idx1 == so1.length) {
                /*
                 * Our speculation succeeded: we walked down the whole s1 list, and all of its types
                 * are included in s2.
                 */

                return s1.forCanBeNull(bb, resultCanBeNull);
            }

        }

        return doIntersection2(bb, s1, s2, resultCanBeNull, idx1, idx2);
    }

    private static ThreadLocal<ListUtils.UnsafeArrayListClosable<AnalysisObject>> intersectionArrayListTL = new ThreadLocal<>();

    private static TypeState doIntersection2(PointsToAnalysis bb, ContextSensitiveMultiTypeState s1, ContextSensitiveMultiTypeState s2, boolean resultCanBeNull, int idx1Param, int idx2Param) {

        try (ListUtils.UnsafeArrayListClosable<AnalysisObject> tlArrayClosable = ListUtils.getTLArrayList(intersectionArrayListTL, 256)) {
            ListUtils.UnsafeArrayList<AnalysisObject> resultObjects = tlArrayClosable.list();

            AnalysisObject[] so1 = s1.objects;
            AnalysisObject[] so2 = s2.objects;
            int[] types1 = s1.getObjectTypeIds();
            int[] types2 = s2.getObjectTypeIds();
            int idx1 = idx1Param;
            int idx2 = idx2Param;
            int l1 = so1.length;
            int l2 = so2.length;
            int t1 = types1[idx1];
            int t2 = types2[idx2];
            while (idx1 < l1 && idx2 < l2) {
                assert so2[idx2].isContextInsensitiveObject() : "Current implementation limitation.";
                if (t1 == t2) {
                    assert so1[idx1].type().equals(so2[idx2].type());
                    resultObjects.add(so1[idx1]);
                    t1 = types1[++idx1];
                } else if (t1 < t2) {
                    t1 = types1[++idx1];
                } else if (t1 > t2) {
                    t2 = types2[++idx2];
                }
            }

            int totalLength = idx1Param + resultObjects.size();

            if (totalLength == 0) {
                return TypeState.forEmpty().forCanBeNull(bb, resultCanBeNull);
            } else {
                AnalysisObject[] objects = new AnalysisObject[totalLength];
                /* Copy the recently touched first */
                resultObjects.copyToArray(objects, idx1Param);
                /* Add the beginning of the s1 list that we already walked above. */
                System.arraycopy(s1.objects, 0, objects, 0, idx1Param);

                if (TypeStateUtils.holdsSingleTypeState(objects, objects.length)) {
                    /* Multiple objects of the same type. */
                    return new ContextSensitiveSingleTypeState(bb, resultCanBeNull, bb.analysisPolicy().makeProperties(bb, objects), objects[0].type(), objects);
                } else {
                    /* Logical AND the type bit sets. */
                    BitSet resultTypesBitSet = TypeStateUtils.and(s1.bitSet(), s2.bitSet());
                    MultiTypeState result = new ContextSensitiveMultiTypeState(bb, resultCanBeNull, bb.analysisPolicy().makeProperties(bb, objects), resultTypesBitSet, objects);

                    /*
                     * The result can be equal to s1 if and only if s1 and s2 have the same type
                     * count.
                     */
                    if (s1.typesCount() == s2.typesCount() && result.equals(s1)) {
                        return s1.forCanBeNull(bb, resultCanBeNull);
                    }

                    /*
                     * Don't need to check if the result is close-to-all-instantiated since result
                     * <= s1.
                     */
                    return result;
                }
            }
        }
    }

    /*
     * Implementation of subtraction.
     *
     * The implementation of subtraction is specific to our current use case, i.e., it is not a
     * general set subtraction implementation. The limitation, checked by the assertions below,
     * refers to the fact that when we use subtraction we only care about eliminating all the
     * objects of a certain type or types, e.g., for filtering. We don't currently have a situation
     * where we only want to remove a subset of objects of a type. In our use the types whose
     * objects need to be eliminated are always specified in s2 through their context insensitive
     * objects, thus s2 must only contain context insensitive objects.
     */

    @Override
    public TypeState doSubtraction(PointsToAnalysis bb, MultiTypeState state1, SingleTypeState state2) {

        ContextSensitiveMultiTypeState s1 = (ContextSensitiveMultiTypeState) state1;
        ContextSensitiveSingleTypeState s2 = (ContextSensitiveSingleTypeState) state2;

        boolean resultCanBeNull = s1.canBeNull() && !s2.canBeNull();
        if (s1.containsType(s2.exactType())) {
            /* s2 is contained in s1, so remove all objects of the same type from s1. */

            /* See comment above for the limitation explanation. */
            assert !bb.extendedAsserts() || TypeStateUtils.isContextInsensitiveTypeState(bb, s2) : "Current implementation limitation.";

            /* Find the range of objects of s2.exactType() in s1. */
            ContextSensitiveMultiTypeState.Range typeRange = s1.findTypeRange(s2.exactType());
            int newLength = s1.objects.length - (typeRange.right() - typeRange.left());
            AnalysisObject[] resultObjects = new AnalysisObject[newLength];

            /* Copy all the objects in s1 but the ones inside the range to the result list. */
            System.arraycopy(s1.objects, 0, resultObjects, 0, typeRange.left());
            System.arraycopy(s1.objects, typeRange.right(), resultObjects, typeRange.left(), s1.objects.length - typeRange.right());

            if (resultObjects.length == 1) {
                return new ContextSensitiveSingleTypeState(bb, resultCanBeNull, bb.analysisPolicy().makeProperties(bb, resultObjects[0]), resultObjects[0].type(), resultObjects[0]);
            } else if (TypeStateUtils.holdsSingleTypeState(resultObjects)) {
                /* Multiple objects of the same type. */
                return new ContextSensitiveSingleTypeState(bb, resultCanBeNull, bb.analysisPolicy().makeProperties(bb, resultObjects), resultObjects[0].type(), resultObjects);
            } else {
                BitSet resultTypesBitSet = TypeStateUtils.clear(s1.bitSet(), s2.exactType().getId());
                return new ContextSensitiveMultiTypeState(bb, resultCanBeNull, bb.analysisPolicy().makeProperties(bb, resultObjects), resultTypesBitSet, resultObjects);
            }

        } else {
            return s1.forCanBeNull(bb, resultCanBeNull);
        }
    }

    @Override
    public TypeState doSubtraction(PointsToAnalysis bb, MultiTypeState state1, MultiTypeState state2) {
        ContextSensitiveMultiTypeState s1 = (ContextSensitiveMultiTypeState) state1;
        ContextSensitiveMultiTypeState s2 = (ContextSensitiveMultiTypeState) state2;

        boolean resultCanBeNull = s1.canBeNull() && !s2.canBeNull();
        /*
         * No need for a deep equality check (which would need to iterate the arrays), since the
         * speculation logic below is doing that anyway.
         */
        if (s1.objects == s2.objects) {
            return TypeState.forEmpty().forCanBeNull(bb, resultCanBeNull);
        }

        return doSubtraction0(bb, s1, s2, resultCanBeNull);
    }

    private static TypeState doSubtraction0(PointsToAnalysis bb, ContextSensitiveMultiTypeState s1, ContextSensitiveMultiTypeState s2, boolean resultCanBeNull) {
        /* Speculate that s1 and s2 have either the same types, or no types in common. */

        if (s1.bitSet().equals(s2.bitSet())) {
            /* Speculate that s1 and s2 have the same types, i.e., the result is empty set. */
            return TypeState.forEmpty().forCanBeNull(bb, resultCanBeNull);
        }

        if (!s1.bitSet().intersects(s2.bitSet())) {
            /* Speculate that s1 and s2 have no types in common, i.e., the result is s1. */
            return s1.forCanBeNull(bb, resultCanBeNull);
        }

        return doSubtraction1(bb, s1, s2, resultCanBeNull);
    }

    private static TypeState doSubtraction1(PointsToAnalysis bb, ContextSensitiveMultiTypeState s1, ContextSensitiveMultiTypeState s2, boolean resultCanBeNull) {
        /*
         * Speculate that s1 and s2 have no overlap, i.e., they don't have any objects in common. In
         * that case, the result is just s1.
         */
        int idx1 = 0;
        int idx2 = 0;

        AnalysisObject[] so1 = s1.objects;
        AnalysisObject[] so2 = s2.objects;
        while (true) {
            AnalysisObject o1 = so1[idx1];
            AnalysisObject o2 = so2[idx2];

            /* See comment above for the limitation explanation. */
            assert o2.isContextInsensitiveObject() : "Current implementation limitation.";

            if (o1.getTypeId() < o2.getTypeId()) {
                idx1++;
                if (idx1 == so1.length) {
                    return s1.forCanBeNull(bb, resultCanBeNull);
                }
            } else if (o1.getTypeId() > o2.getTypeId()) {
                idx2++;
                if (idx2 == so2.length) {
                    return s1.forCanBeNull(bb, resultCanBeNull);
                }
            } else {
                /* Our speculation failed. */
                break;
            }
        }

        return doSubtraction2(bb, s1, s2, resultCanBeNull, idx1, idx2);
    }

    private static TypeState doSubtraction2(PointsToAnalysis bb, ContextSensitiveMultiTypeState s1, ContextSensitiveMultiTypeState s2, boolean resultCanBeNull, int idx1Param, int idx2Param) {
        try (ListUtils.UnsafeArrayListClosable<AnalysisObject> tlArrayClosable = ListUtils.getTLArrayList(intersectionArrayListTL, 256)) {
            ListUtils.UnsafeArrayList<AnalysisObject> resultObjects = tlArrayClosable.list();

            AnalysisObject[] so1 = s1.objects;
            AnalysisObject[] so2 = s2.objects;
            int[] types1 = s1.getObjectTypeIds();
            int[] types2 = s2.getObjectTypeIds();
            int idx1 = idx1Param;
            int idx2 = idx2Param;
            int l1 = so1.length;
            int l2 = so2.length;
            int t1 = types1[idx1];
            int t2 = types2[idx2];
            while (idx1 < l1 && idx2 < l2) {
                assert so2[idx2].isContextInsensitiveObject() : "Current implementation limitation.";
                if (t1 < t2) {
                    resultObjects.add(so1[idx1]);
                    t1 = types1[++idx1];
                } else if (t1 > t2) {
                    t2 = types2[++idx2];
                } else if (t1 == t2) {
                    assert so1[idx1].type().equals(so2[idx2].type());
                    t1 = types1[++idx1];
                }
            }

            int remainder = s1.objects.length - idx1;
            int totalLength = idx1Param + resultObjects.size() + remainder;

            if (totalLength == 0) {
                return TypeState.forEmpty().forCanBeNull(bb, resultCanBeNull);
            } else {
                AnalysisObject[] objects = new AnalysisObject[totalLength];
                /* Copy recently touched first */
                resultObjects.copyToArray(objects, idx1Param);
                /* leading elements */
                System.arraycopy(s1.objects, 0, objects, 0, idx1Param);
                /* trailing elements (remainder) */
                System.arraycopy(s1.objects, idx1, objects, totalLength - remainder, remainder);

                if (TypeStateUtils.holdsSingleTypeState(objects, totalLength)) {
                    /* Multiple objects of the same type. */
                    return new ContextSensitiveSingleTypeState(bb, resultCanBeNull, bb.analysisPolicy().makeProperties(bb, objects), objects[0].type(), objects);
                } else {
                    BitSet resultTypesBitSet = TypeStateUtils.andNot(s1.bitSet(), s2.bitSet());
                    /*
                     * Don't need to check if the result is close-to-all-instantiated since result
                     * <= s1.
                     */
                    return new ContextSensitiveMultiTypeState(bb, resultCanBeNull, bb.analysisPolicy().makeProperties(bb, objects), resultTypesBitSet, objects);
                }
            }
        }
    }
}
