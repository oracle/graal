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
package com.oracle.graal.pointsto.typestate;

import java.util.Arrays;
import java.util.BitSet;
import java.util.Iterator;
import java.util.Objects;

import com.oracle.graal.pointsto.AnalysisPolicy;
import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.api.PointstoOptions;
import com.oracle.graal.pointsto.flow.AbstractSpecialInvokeTypeFlow;
import com.oracle.graal.pointsto.flow.AbstractStaticInvokeTypeFlow;
import com.oracle.graal.pointsto.flow.AbstractVirtualInvokeTypeFlow;
import com.oracle.graal.pointsto.flow.ActualReturnTypeFlow;
import com.oracle.graal.pointsto.flow.ArrayElementsTypeFlow;
import com.oracle.graal.pointsto.flow.CloneTypeFlow;
import com.oracle.graal.pointsto.flow.FieldTypeFlow;
import com.oracle.graal.pointsto.flow.InvokeTypeFlow;
import com.oracle.graal.pointsto.flow.MethodFlowsGraph;
import com.oracle.graal.pointsto.flow.MethodFlowsGraphInfo;
import com.oracle.graal.pointsto.flow.MethodTypeFlow;
import com.oracle.graal.pointsto.flow.TypeFlow;
import com.oracle.graal.pointsto.flow.context.AnalysisContext;
import com.oracle.graal.pointsto.flow.context.object.AnalysisObject;
import com.oracle.graal.pointsto.heap.ImageHeapRelocatableConstant;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.meta.PointsToAnalysisMethod;
import com.oracle.graal.pointsto.typestore.ArrayElementsTypeStore;
import com.oracle.graal.pointsto.typestore.FieldTypeStore;
import com.oracle.graal.pointsto.typestore.UnifiedArrayElementsTypeStore;
import com.oracle.graal.pointsto.typestore.UnifiedFieldTypeStore;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.svm.common.meta.MultiMethod;

import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.options.OptionValues;
import jdk.vm.ci.code.BytecodePosition;
import jdk.vm.ci.meta.JavaConstant;

/**
 * This class implements the default, context-insensitive, static analysis policy.
 * <p>
 * For {@link MultiTypeState}, the class is aware of the two implementations
 * ({@link MultiTypeStateWithBitSet} and {@link MultiTypeStateWithArray}) including their underlying
 * data structures ({@link BitSet} vs {@code int[]}) and tries to choose the most effective
 * implementation of the given operation. We try to proactively (and sometimes optimistically)
 * compute the result into {@link MultiTypeStateWithArray} whenever the inputs suggest it is
 * guaranteed or at least likely that the cardinality of the result will satisfy the
 * {@link PointstoOptions#MultiTypeStateArrayBitSetThreshold}. Most methods implement fast paths
 * specialized for {@link MultiTypeStateWithArray}, with fallbacks to the bit-set based
 * implementations for larger sets.
 */
public class DefaultAnalysisPolicy extends AnalysisPolicy {

    /**
     * This field holds pre-allocated int arrays used by the fast paths for
     * {@link MultiTypeStateWithArray}. We do this to avoid needless allocation of the array in
     * speculative cases such as {@link #maybeMergeArrayBasedTypeStates}, where we sometimes have to
     * fall back to bitset-based handling. Furthermore, the final length is not always known
     * beforehand, for example in {@link #subtractIntoArrayBased}. Therefore, we compute the result
     * in the thread-local array first and then only do one appropriately-sized allocation at the
     * end of the operation to create a subarray based on the thread-local array. Since the next
     * operation will again start filling the thread-local array from the beginning, it is not even
     * necessary to clear it between operations. However, it is important to not let the
     * thread-local array escape and become a part of any {@link MultiTypeStateWithArray} as that
     * would cause hard-to-debug errors. We check this via assertion in
     * {@link #multiTypeState(PointsToAnalysis,boolean,int[])}.
     * <p>
     * This field is intentionally not static, because the size of the thread-local array depends on
     * {@link PointstoOptions#MultiTypeStateArrayBitSetIntersectionSpeculationThreshold}. Note,
     * however, that this class is a singleton, so the field is effectively still static.
     */
    private final ThreadLocal<int[]> threadLocalTypeIdArray;

    public DefaultAnalysisPolicy(OptionValues options) {
        super(options);
        threadLocalTypeIdArray = ThreadLocal.withInitial(() -> new int[multiTypeStateArrayBitSetIntersectionSpeculationThreshold]);
    }

    @Override
    public boolean isContextSensitiveAnalysis() {
        return false;
    }

    @Override
    public MethodTypeFlow createMethodTypeFlow(PointsToAnalysisMethod method) {
        return new MethodTypeFlow(method);
    }

    @Override
    public boolean needsConstantCache() {
        return false;
    }

    @Override
    public boolean isSummaryObject(AnalysisObject object) {
        /* Context insensitive objects summarize context sensitive objects of the same type. */
        return object.isContextInsensitiveObject();
    }

    @Override
    public boolean isMergingEnabled() {
        // by default no merging is necessary
        return false;
    }

    @Override
    public void noteMerge(PointsToAnalysis bb, TypeState t) {
        // nothing to do
    }

    @Override
    public void noteMerge(PointsToAnalysis bb, AnalysisObject... a) {
        // nothing to do
    }

    @Override
    public void noteMerge(PointsToAnalysis bb, AnalysisObject a) {
        // nothing to do
    }

    @Override
    public boolean isContextSensitiveAllocation(PointsToAnalysis bb, AnalysisType type, AnalysisContext allocationContext) {
        return false;
    }

    @Override
    public AnalysisObject createHeapObject(PointsToAnalysis bb, AnalysisType type, BytecodePosition allocationSite, AnalysisContext allocationContext) {
        return type.getContextInsensitiveAnalysisObject();
    }

    /**
     * In the context-insensitive analysis, although we track constant values using the
     * {@link ConstantTypeState}, we don't track fields or array elements separately for constant
     * objects. The field and array flows used for load/store operations of a "constant" are shared
     * with the context-insensitive object of its declared type. See also {@link ConstantTypeState}.
     */
    @Override
    public AnalysisObject createConstantObject(PointsToAnalysis bb, JavaConstant constant, AnalysisType exactType) {
        return exactType.getContextInsensitiveAnalysisObject();
    }

    @Override
    public TypeState constantTypeState(PointsToAnalysis bb, JavaConstant constant, AnalysisType exactType) {
        if (constant instanceof ImageHeapRelocatableConstant relocatableConstant) {
            /*
             * ImageHeapRelocatableConstants are placeholder values which will be later replaced
             * with an unknown non-null object.
             */
            return TypeState.forType(bb, relocatableConstant.getType(), false);
        }
        return PointsToStats.registerTypeState(bb, new ConstantTypeState(bb, exactType, constant));
    }

    @Override
    public TypeState dynamicNewInstanceState(PointsToAnalysis bb, TypeState currentState, TypeState newState, BytecodePosition allocationSite, AnalysisContext allocationContext) {
        /* Just return the new type state as there is no allocation context. */
        return eraseConstant(bb, newState).forNonNull(bb);
    }

    @Override
    public TypeState cloneState(PointsToAnalysis bb, TypeState currentState, TypeState inputState, BytecodePosition cloneSite, AnalysisContext allocationContext) {
        return eraseConstant(bb, inputState).forNonNull(bb);
    }

    @Override
    public void linkClonedObjects(PointsToAnalysis bb, TypeFlow<?> inputFlow, CloneTypeFlow cloneFlow, BytecodePosition source) {
        /*
         * Nothing to do for the context insensitive analysis. The source and clone flows are
         * identical, thus their elements are modeled by the same array or field flows.
         */
    }

    @Override
    public FieldTypeStore createFieldTypeStore(PointsToAnalysis bb, AnalysisObject object, AnalysisField field, AnalysisUniverse universe) {
        assert object.isContextInsensitiveObject() : "Object should be context insensitive: " + object;
        return new UnifiedFieldTypeStore(field, object, new FieldTypeFlow(field, field.getType(), object));
    }

    @Override
    public ArrayElementsTypeStore createArrayElementsTypeStore(AnalysisObject object, AnalysisUniverse universe) {
        assert object.isContextInsensitiveObject() : "Object should be context insensitive: " + object;
        if (object.type().isArray()) {
            if (aliasArrayTypeFlows) {
                /* Alias all array type flows using the elements type flow model of Object type. */
                if (object.type().getComponentType().isJavaLangObject()) {
                    return new UnifiedArrayElementsTypeStore(object);
                }
                return universe.objectType().getArrayClass().getContextInsensitiveAnalysisObject().getArrayElementsTypeStore();
            }
            return new UnifiedArrayElementsTypeStore(object);
        } else {
            return null;
        }
    }

    @Override
    public AbstractVirtualInvokeTypeFlow createVirtualInvokeTypeFlow(BytecodePosition invokeLocation, AnalysisType receiverType, PointsToAnalysisMethod targetMethod, TypeFlow<?>[] actualParameters,
                    ActualReturnTypeFlow actualReturn, MultiMethod.MultiMethodKey callerMultiMethodKey) {
        return new DefaultVirtualInvokeTypeFlow(invokeLocation, receiverType, targetMethod, actualParameters, actualReturn, callerMultiMethodKey);
    }

    @Override
    public AbstractSpecialInvokeTypeFlow createSpecialInvokeTypeFlow(BytecodePosition invokeLocation, AnalysisType receiverType, PointsToAnalysisMethod targetMethod, TypeFlow<?>[] actualParameters,
                    ActualReturnTypeFlow actualReturn, MultiMethod.MultiMethodKey callerMultiMethodKey) {
        return new DefaultSpecialInvokeTypeFlow(invokeLocation, receiverType, targetMethod, actualParameters, actualReturn, callerMultiMethodKey);
    }

    @Override
    public AbstractStaticInvokeTypeFlow createStaticInvokeTypeFlow(BytecodePosition invokeLocation, AnalysisType receiverType, PointsToAnalysisMethod targetMethod, TypeFlow<?>[] actualParameters,
                    ActualReturnTypeFlow actualReturn, MultiMethod.MultiMethodKey callerMultiMethodKey) {
        return new DefaultStaticInvokeTypeFlow(invokeLocation, receiverType, targetMethod, actualParameters, actualReturn, callerMultiMethodKey);
    }

    @Override
    public InvokeTypeFlow createDeoptInvokeTypeFlow(BytecodePosition invokeLocation, AnalysisType receiverType, PointsToAnalysisMethod targetMethod, TypeFlow<?>[] actualParameters,
                    ActualReturnTypeFlow actualReturn, MultiMethod.MultiMethodKey callerMultiMethodKey) {
        if (targetMethod.isStatic()) {
            return new DefaultStaticInvokeTypeFlow(invokeLocation, receiverType, targetMethod, actualParameters, actualReturn, callerMultiMethodKey, true);
        } else {
            return new DefaultSpecialInvokeTypeFlow(invokeLocation, receiverType, targetMethod, actualParameters, actualReturn, callerMultiMethodKey, true);
        }
    }

    @Override
    public MethodFlowsGraphInfo staticRootMethodGraph(PointsToAnalysis bb, PointsToAnalysisMethod method) {
        return method.getTypeFlow().getOrCreateMethodFlowsGraphInfo(bb, null);
    }

    @Override
    public AnalysisContext allocationContext(PointsToAnalysis bb, MethodFlowsGraph callerGraph) {
        throw AnalysisError.shouldNotReachHere("should be overwritten");
    }

    @Override
    public TypeFlow<?> proxy(BytecodePosition source, TypeFlow<?> input) {
        return input;
    }

    @Override
    public boolean addOriginalUse(PointsToAnalysis bb, TypeFlow<?> flow, TypeFlow<?> use) {
        /* Adds the use, if not already present, and propagates the type state. */
        return flow.addUse(bb, use, true);
    }

    @Override
    public boolean addOriginalObserver(PointsToAnalysis bb, TypeFlow<?> flow, TypeFlow<?> observer) {
        /* Adds the observer, if not already present, and triggers an update. */
        return flow.addObserver(bb, observer, true);
    }

    @Override
    public void linkActualReturn(PointsToAnalysis bb, boolean isStatic, InvokeTypeFlow invoke) {
        /*
         * Link the actual return with the formal return of already linked callees. Note the callees
         * may not be fully linked if they have been {@code DirectInvokeTypeFlow#initializeCallees}
         * but not yet processed.
         */
        for (AnalysisMethod callee : invoke.getCalleesForReturnLinking()) {
            MethodFlowsGraphInfo calleeFlows = ((PointsToAnalysisMethod) callee).getTypeFlow().getOrCreateMethodFlowsGraphInfo(bb, invoke);
            invoke.linkReturn(bb, isStatic, calleeFlows);
        }
        /*
         * If the invoke is saturated then we must ensure the actual return is linked to the context
         * insensitive invoke.
         */
        if (invoke.isSaturated()) {
            InvokeTypeFlow contextInsensitiveInvoke = invoke.getTargetMethod().getContextInsensitiveVirtualInvoke(invoke.getCallerMultiMethodKey());
            contextInsensitiveInvoke.getActualReturn().addUse(bb, invoke.getActualReturn());
        }
    }

    @Override
    public void registerAsImplementationInvoked(InvokeTypeFlow invoke, PointsToAnalysisMethod method) {
        method.registerAsImplementationInvoked(invoke);
    }

    @Override
    public TypeState forContextInsensitiveTypeState(PointsToAnalysis bb, TypeState state) {
        return state;
    }

    @Override
    public SingleTypeState singleTypeState(PointsToAnalysis bb, boolean canBeNull, AnalysisType type, AnalysisObject... objects) {
        return PointsToStats.registerTypeState(bb, new SingleTypeState(canBeNull, type));
    }

    @Override
    public MultiTypeState multiTypeState(PointsToAnalysis bb, boolean canBeNull, BitSet typesBitSet, int typesCount, AnalysisObject... objects) {
        assert typesCount == typesBitSet.cardinality() : typesCount + " vs " + typesBitSet.cardinality();
        assert typesCount > bb.analysisPolicy().multiTypeStateArrayBitSetThreshold() : Assertions.errorMessageContext("BitSet-based MultiTypeState should only be used for larger sets, typesCount",
                        typesCount, "threshold", bb.analysisPolicy().multiTypeStateArrayBitSetThreshold());
        return PointsToStats.registerTypeState(bb, new MultiTypeStateWithBitSet(canBeNull, typesBitSet, typesCount));
    }

    /**
     * Create an instance of {@link MultiTypeStateWithArray}, register it in {@link PointsToStats}.
     */
    public MultiTypeStateWithArray multiTypeState(PointsToAnalysis bb, boolean canBeNull, int[] typeIds) {
        assert typeIds != threadLocalTypeIdArray.get() : "The pre-allocated thread-local array should not be shared.";
        assert typeIds.length <= bb.analysisPolicy().multiTypeStateArrayBitSetThreshold() : "MultiTypeStates with size above the MultiTypeStateArrayBitSetThreshold value " +
                        bb.analysisPolicy().multiTypeStateArrayBitSetThreshold() + " should use bitsets.";
        return PointsToStats.registerTypeState(bb, new MultiTypeStateWithArray(canBeNull, typeIds));
    }

    /*
     * When a constant state is an operand of a union, or it is the input for a clone or
     * dynamic-new-instance, then the constant gets erased.
     */
    private static TypeState eraseConstant(PointsToAnalysis bb, TypeState state) {
        if (state instanceof ConstantTypeState) {
            /* Return an exact type state that essentially models all objects of the given type. */
            return TypeState.forExactType(bb, state.exactType(), state.canBeNull());
        }
        return state;
    }

    @Override
    public TypeState doUnion(PointsToAnalysis bb, SingleTypeState state1, SingleTypeState state2) {
        if (state1 instanceof ConstantTypeState && state2 instanceof ConstantTypeState) {
            ConstantTypeState cs1 = (ConstantTypeState) state1;
            ConstantTypeState cs2 = (ConstantTypeState) state2;
            /* If the states wrap the same constant return one of them preserving the "null". */
            if (cs1.equals(cs2)) {
                return cs1;
            } else if (Objects.equals(cs1.getConstant(), cs2.getConstant())) {
                assert cs1.exactType().equals(cs2.exactType()) : state1 + ", " + state2;
                boolean resultCanBeNull = state1.canBeNull() || state2.canBeNull();
                return cs1.canBeNull() == resultCanBeNull ? cs1 : cs2;
            }
        }

        /* Otherwise, when a constant state is an operand of a union the constant is erased. */
        TypeState s1 = eraseConstant(bb, state1);
        TypeState s2 = eraseConstant(bb, state2);

        boolean resultCanBeNull = s1.canBeNull() || s2.canBeNull();
        if (s1.exactType().equals(s2.exactType())) {
            /*
             * The inputs have the same type, so the result is a SingleTypeState. Check if any of
             * the states has the right null state.
             */
            return s1.canBeNull() == resultCanBeNull ? s1 : s2;
        } else {
            /*
             * The inputs have different types, so the result is a MultiTypeState. We know the
             * types, just construct the types bit set.
             */
            var leftId = s1.exactType().getId();
            var rightId = s2.exactType().getId();
            TypeState result;
            int resultSize = 2;
            if (resultSize <= bb.analysisPolicy().multiTypeStateArrayBitSetThreshold()) {
                int[] typeIds = leftId < rightId ? new int[]{leftId, rightId} : new int[]{rightId, leftId};
                result = multiTypeState(bb, resultCanBeNull, typeIds);
            } else {
                BitSet typesBitSet = TypeStateUtils.newBitSet(s1.exactType().getId(), s2.exactType().getId());
                assert typesBitSet.cardinality() == 2 : typesBitSet;
                result = multiTypeState(bb, resultCanBeNull, typesBitSet, resultSize);
            }
            PointsToStats.registerUnionOperation(bb, s1, s2, result);
            return result;
        }
    }

    @Override
    public TypeState doUnion(PointsToAnalysis bb, MultiTypeState s1, SingleTypeState state2) {
        /* If ConstantTypeState is an operand of a union operation the constant is erased. */
        TypeState s2 = eraseConstant(bb, state2);

        boolean resultCanBeNull = s1.canBeNull() || s2.canBeNull();

        if (s1.containsType(s2.exactType())) {
            /* The type of s2 is already contained in s1. */
            return s1.forCanBeNull(bb, resultCanBeNull);
        } else {
            BitSet typesBitSet;
            if (s1 instanceof MultiTypeStateWithArray withArray) {
                /* We know the type is not in s1, so the size will increase by 1. */
                if (withArray.typesCount() + 1 <= bb.analysisPolicy().multiTypeStateArrayBitSetThreshold()) {
                    /* Create a new array-based type state with the extra type inserted in. */
                    return multiArrayTypeStateWithExtraType(bb, s2.exactType(), withArray, resultCanBeNull);
                }
                typesBitSet = TypeStateUtils.newBitSet(withArray, s2.exactType());
            } else {
                typesBitSet = ((MultiTypeStateWithBitSet) s1).typesBitSet();
                typesBitSet = TypeStateUtils.set(typesBitSet, s2.exactType().getId());
            }
            int typesCount = s1.typesCount() + 1;
            assert typesCount == typesBitSet.cardinality() : typesBitSet;
            MultiTypeState result = multiTypeState(bb, resultCanBeNull, typesBitSet, typesCount);
            PointsToStats.registerUnionOperation(bb, s1, s2, result);
            return result;
        }
    }

    /**
     * Create a new {@link MultiTypeStateWithArray} state based on the {@code originalTypeState},
     * extended with {@code newType}.
     */
    private MultiTypeStateWithArray multiArrayTypeStateWithExtraType(PointsToAnalysis bb, AnalysisType newType, MultiTypeStateWithArray originalTypeState, boolean resultCanBeNull) {
        assert !originalTypeState.containsType(newType) : newType + " should not be in the TypeState: " + originalTypeState;
        var newTypeId = newType.getId();
        var oldTypeIds = originalTypeState.getTypeIdArray();
        assert oldTypeIds.length + 1 <= bb.analysisPolicy().multiTypeStateArrayBitSetThreshold() : Assertions
                        .errorMessageContext("The resulting array-based type state would be too large, originalTypeState", originalTypeState);
        var newTypeIds = new int[oldTypeIds.length + 1];
        if (newTypeId < oldTypeIds[0]) {
            /* Fastpath if the new element will be at the beginning */
            newTypeIds[0] = newTypeId;
            System.arraycopy(oldTypeIds, 0, newTypeIds, 1, oldTypeIds.length);
        } else if (newTypeId > oldTypeIds[oldTypeIds.length - 1]) {
            /* Fastpath if the new element will be at the end */
            System.arraycopy(oldTypeIds, 0, newTypeIds, 0, oldTypeIds.length);
            newTypeIds[oldTypeIds.length] = newTypeId;
        } else {
            /* Find where to insert the new id using binary search. */
            int insertionPoint = Arrays.binarySearch(oldTypeIds, newTypeId);
            assert insertionPoint < 0 : newType + " should not be present in " + originalTypeState;
            /* Extract the proper index for insertion. */
            insertionPoint = (-insertionPoint) - 1;
            if (insertionPoint > 0) {
                /* Copy the smaller elements. */
                System.arraycopy(oldTypeIds, 0, newTypeIds, 0, insertionPoint);
            }
            /* Insert the new element. */
            newTypeIds[insertionPoint] = newTypeId;
            if (insertionPoint < oldTypeIds.length) {
                /* Copy the bigger elements. */
                System.arraycopy(oldTypeIds, insertionPoint, newTypeIds, insertionPoint + 1, oldTypeIds.length - insertionPoint);
            }
        }
        return multiTypeState(bb, resultCanBeNull, newTypeIds);
    }

    /**
     * Helper method to extract a {@link BitSet} representing the set of types in the given
     * {@code typeState}. Depending on the type of the {@link MultiTypeState}, this may be either
     * cheap field access (for {@link MultiTypeStateWithBitSet}, or a more expensive construction of
     * the {@link BitSet} from {@code typeId} array (for {@link MultiTypeStateWithArray}), which
     * should be exercised rarely (should happen in ~1% of calls).
     */
    private static BitSet getBitSet(MultiTypeState typeState) {
        return switch (typeState) {
            case MultiTypeStateWithBitSet withBitSet -> withBitSet.typesBitSet();
            case MultiTypeStateWithArray withArray -> TypeStateUtils.bitSetFromArray(withArray);
        };
    }

    @Override
    public TypeState doUnion(PointsToAnalysis bb, MultiTypeState s1, MultiTypeState s2) {
        assert s1.typesCount() >= s2.typesCount() : s1 + ", " + s2;

        boolean resultCanBeNull = s1.canBeNull() || s2.canBeNull();

        if (s1 instanceof MultiTypeStateWithArray withArray1 && s2 instanceof MultiTypeStateWithArray withArray2) {
            /*
             * Try to optimize only if both are array-based, because otherwise the union has to be
             * bitset-based anyway (it won't get smaller).
             */
            TypeState result = maybeMergeArrayBasedTypeStates(bb, withArray1, withArray2, resultCanBeNull);
            if (result != null) {
                return result;
            }
        }
        var bitSet1 = getBitSet(s1);
        var bitSet2 = getBitSet(s2);

        /*
         * No need for a deep equality check (which would need to iterate the arrays), since the
         * speculation logic below is doing that anyway.
         */
        if (bitSet1 == bitSet2) {
            return s1.forCanBeNull(bb, resultCanBeNull);
        }

        /* Speculate that s1 is a superset of s2. */
        if (TypeStateUtils.isSuperset(bitSet1, bitSet2)) {
            return s1.forCanBeNull(bb, resultCanBeNull);
        }

        /* Logical OR the type bit sets. */
        BitSet resultTypesBitSet = TypeStateUtils.or(bitSet1, bitSet2);

        MultiTypeState result = multiTypeState(bb, resultCanBeNull, resultTypesBitSet, resultTypesBitSet.cardinality());
        assert !result.equals(s1) && !result.equals(s2) : result;
        PointsToStats.registerUnionOperation(bb, s1, s2, result);
        return result;
    }

    /**
     * Optimistically try to merge two {@link MultiTypeStateWithArray}s, return {@code null} if the
     * result exceeds {@link PointstoOptions#MultiTypeStateArrayBitSetThreshold}.
     */
    private TypeState maybeMergeArrayBasedTypeStates(PointsToAnalysis bb, MultiTypeStateWithArray left, MultiTypeStateWithArray right, boolean resultCanBeNull) {
        assert left.typesCount() >= right.typesCount() : left + ", " + right;
        var leftIds = left.getTypeIdArray();
        var rightIds = right.getTypeIdArray();
        int currLen = 0;
        int maxResultLen = Math.min(leftIds.length + rightIds.length, bb.analysisPolicy().multiTypeStateArrayBitSetThreshold());
        int[] result = threadLocalTypeIdArray.get();
        int leftPos = 0;
        int rightPos = 0;
        /* Merge two type id arrays. */
        while (currLen < maxResultLen && leftPos < leftIds.length && rightPos < rightIds.length) {
            if (leftIds[leftPos] < rightIds[rightPos]) {
                result[currLen++] = leftIds[leftPos++];
            } else if (leftIds[leftPos] > rightIds[rightPos]) {
                result[currLen++] = rightIds[rightPos++];
            } else {
                /* Same type id, insert only once. */
                result[currLen++] = leftIds[leftPos++];
                rightPos++;
            }
        }

        /* Process leftover elements. */
        while (currLen < maxResultLen && leftPos < leftIds.length) {
            result[currLen++] = leftIds[leftPos++];
        }
        while (currLen < maxResultLen && rightPos < rightIds.length) {
            result[currLen++] = rightIds[rightPos++];
        }

        /* Check if both inputs were fully covered. */
        if (leftPos == leftIds.length && rightPos == rightIds.length) {
            /* Check if left (the bigger one) was a superset, reuse it if possible. */
            if (currLen == leftIds.length) {
                assert left.isSuperSet(right) : left + " is not a superset of " + right;
                return left.forCanBeNull(bb, resultCanBeNull);
            }
            assert currLen > leftIds.length : "Left type state could be reused. : " + left + ", " + right;
            return multiTypeState(bb, resultCanBeNull, copySubArray(result, currLen));

        }
        /* The result is too big, fallback to bitset-based MultiTypeState. */
        return null;
    }

    @Override
    public TypeState doIntersection(PointsToAnalysis bb, MultiTypeState s1, SingleTypeState s2) {
        boolean resultCanBeNull = s1.canBeNull() && s2.canBeNull();
        if (s1.containsType(s2.exactType())) {
            /* s1 contains s2's type, return s2. */
            return s2.forCanBeNull(bb, resultCanBeNull);
        } else {
            return TypeState.forEmpty().forCanBeNull(bb, resultCanBeNull);
        }
    }

    @Override
    public TypeState doIntersection(PointsToAnalysis bb, MultiTypeState s1, MultiTypeState s2) {
        boolean resultCanBeNull = s1.canBeNull() && s2.canBeNull();

        if (s1 instanceof MultiTypeStateWithArray || s2 instanceof MultiTypeStateWithArray) {
            /* Fastpath if one TypeState is array-based. */
            return intersectArrayFastPath(bb, s1, s2, resultCanBeNull);
        }

        /*
         * Speculate that the result of doIntersection will be small enough to be stored as an array
         * even though the inputs are bigger. In practice, this is the case over 90% of the time.
         */
        if (Math.min(s1.typesCount(), s2.typesCount()) < bb.analysisPolicy().multiTypeStateArrayBitSetIntersectionSpeculationThreshold()) {
            TypeState result = maybeIntersectIntoArrayBased(bb, ((MultiTypeStateWithBitSet) s1), ((MultiTypeStateWithBitSet) s2), resultCanBeNull);
            if (result != null) {
                return result;
            }
        }
        /* Otherwise fallback to the original implementation. */

        /* Speculate that s1 and s2 have either the same types, or no types in common. */
        var bitSet1 = getBitSet(s1);
        var bitSet2 = getBitSet(s2);
        if (bitSet1.equals(bitSet2)) {
            /* Speculate that s1 and s2 have the same types, i.e., the result is s1. */
            return s1.forCanBeNull(bb, resultCanBeNull);
        }

        if (!bitSet1.intersects(bitSet2)) {
            /* Speculate that s1 and s2 have no types in common, i.e., the result is empty. */
            return TypeState.forEmpty().forCanBeNull(bb, resultCanBeNull);
        }

        /*
         * Speculate that s2 contains all types of s1, i.e., the filter is broader than s1, thus the
         * result is s1.
         */
        if (TypeStateUtils.isSuperset(bitSet2, bitSet1)) {
            return s1.forCanBeNull(bb, resultCanBeNull);
        }

        BitSet resultTypesBitSet = TypeStateUtils.and(bitSet1, bitSet2);
        int typesCount = resultTypesBitSet.cardinality();
        if (typesCount == 0) {
            return TypeState.forEmpty().forCanBeNull(bb, resultCanBeNull);
        } else if (typesCount == 1) {
            AnalysisType type = bb.getUniverse().getType(resultTypesBitSet.nextSetBit(0));
            return singleTypeState(bb, resultCanBeNull, type);
        } else if (typesCount <= bb.analysisPolicy().multiTypeStateArrayBitSetThreshold()) {
            return PointsToStats.registerTypeState(bb, new MultiTypeStateWithArray(resultCanBeNull, TypeStateUtils.typeIdArrayFromBitSet(resultTypesBitSet)));
        } else {
            MultiTypeState result = multiTypeState(bb, resultCanBeNull, resultTypesBitSet, typesCount);
            assert !result.equals(s1) : result;
            return result;
        }
    }

    private TypeState maybeIntersectIntoArrayBased(PointsToAnalysis bb, MultiTypeStateWithBitSet s1, MultiTypeStateWithBitSet s2, boolean resultCanBeNull) {
        MultiTypeStateWithBitSet lo = s1;
        MultiTypeStateWithBitSet hi = s2;
        if (s2.typesCount() < s1.typesCount()) {
            lo = s2;
            hi = s1;
        }
        var maxSize = bb.analysisPolicy().multiTypeStateArrayBitSetThreshold();
        var resultLen = 0;
        int[] resultArray = threadLocalTypeIdArray.get();
        for (Iterator<Integer> iterator = lo.typeIdsIterator(); iterator.hasNext();) {
            int typeId = iterator.next();
            if (hi.containsType(typeId)) {
                if (resultLen == maxSize) {
                    /* The result does not fit into the array. */
                    return null;
                }
                resultArray[resultLen++] = typeId;
            }
        }
        if (resultLen == 0) {
            return TypeState.forEmpty().forCanBeNull(bb, resultCanBeNull);
        } else if (resultLen == 1) {
            return singleTypeState(bb, resultCanBeNull, bb.getUniverse().getType(resultArray[0]));
        } else {
            assert resultLen < lo.typesCount() : "No types were removed, allocating new TypeState is not necessary: " + lo + " " + resultLen;
            return multiTypeState(bb, resultCanBeNull, copySubArray(resultArray, resultLen));
        }
    }

    /**
     * Copy a subarray from the thread-local pre-allocated array that is to be passed into
     * {@link #multiTypeState(PointsToAnalysis, boolean, int[])}.
     */
    private int[] copySubArray(int[] arr, int len) {
        assert arr == threadLocalTypeIdArray.get() : "The input should be the pre-allocated thread-local array.";
        return Arrays.copyOf(arr, len);
    }

    /**
     * Optimized intersection when one of the {@link MultiTypeState}s is
     * {@link MultiTypeStateWithArray}, as the result will also be small enough to be array-based.
     */
    private TypeState intersectArrayFastPath(PointsToAnalysis bb, MultiTypeState s1, MultiTypeState s2, boolean resultCanBeNull) {
        assert s1 instanceof MultiTypeStateWithArray || s2 instanceof MultiTypeStateWithArray : "At least one of the MultiTypeStates should be array-based: " + s1 + ", " + s2;
        var resultArray = threadLocalTypeIdArray.get();
        int len = 0;
        if (s1 instanceof MultiTypeStateWithArray withArray1 && s2 instanceof MultiTypeStateWithArray withArray2) {
            len = intersectSortedUniqueArrays(withArray1.getTypeIdArray(), withArray2.getTypeIdArray(), resultArray);
        } else {
            MultiTypeStateWithArray withArray;
            MultiTypeStateWithBitSet withBitSet;
            if (s1 instanceof MultiTypeStateWithArray) {
                withArray = (MultiTypeStateWithArray) s1;
                withBitSet = (MultiTypeStateWithBitSet) s2;
            } else {
                withArray = (MultiTypeStateWithArray) s2;
                withBitSet = (MultiTypeStateWithBitSet) s1;
            }
            for (int typeId : withArray.getTypeIdArray()) {
                if (withBitSet.containsType(typeId)) {
                    resultArray[len++] = typeId;
                }
            }
        }
        if (len == 0) {
            return TypeState.forEmpty().forCanBeNull(bb, resultCanBeNull);
        } else if (len == 1) {
            return singleTypeState(bb, resultCanBeNull, bb.getUniverse().getType(resultArray[0]));
        } else if (len == s1.typesCount()) {
            /* withArray was a subset, can be returned. */
            return s1.forCanBeNull(bb, resultCanBeNull);
        } else if (len == s2.typesCount()) {
            /* other was a subset, can be returned. */
            return s2.forCanBeNull(bb, resultCanBeNull);
        } else {
            assert len < s1.typesCount() : "No types were removed, allocating new TypeState is not necessary: " + s1 + ", " + len;
            assert len < s2.typesCount() : "No types were removed, allocating new TypeState is not necessary: " + s2 + ", " + len;
            return multiTypeState(bb, resultCanBeNull, copySubArray(resultArray, len));
        }
    }

    /**
     * Intersect input arrays, writing the result in the caller-provided result array of sufficient
     * size. Return the count such that the caller can know the valid slice of result array. If
     * result array is larger than the intersection, leftover entries remain unchanged.
     */
    private static int intersectSortedUniqueArrays(int[] typeIdArray1, int[] typeIdArray2, int[] resultArray) {
        assert resultArray.length >= Math.min(typeIdArray1.length, typeIdArray2.length);
        int idx1 = 0;
        int idx2 = 0;
        int idxr = 0;
        while (idx1 < typeIdArray1.length && idx2 < typeIdArray2.length) {
            if (typeIdArray1[idx1] == typeIdArray2[idx2]) {
                resultArray[idxr++] = typeIdArray1[idx1];
                idx1++;
                idx2++;
            } else if (typeIdArray1[idx1] < typeIdArray2[idx2]) {
                idx1++;
            } else {
                idx2++;
            }
        }
        return idxr;
    }

    @Override
    public TypeState doSubtraction(PointsToAnalysis bb, MultiTypeState s1, SingleTypeState s2) {
        boolean resultCanBeNull = s1.canBeNull() && !s2.canBeNull();
        if (s1.containsType(s2.exactType())) {
            if (s1.typesCount() - 1 <= bb.analysisPolicy().multiTypeStateArrayBitSetThreshold()) {
                /* -1 because 1 type will be removed for sure. */
                return subtractIntoArrayBased(bb, s1, s2, resultCanBeNull);
            }
            /* s2 is contained in s1, so remove s2's type from s1. */
            BitSet resultTypesBitSet = TypeStateUtils.clear(((MultiTypeStateWithBitSet) s1).typesBitSet(), s2.exactType().getId());
            int typesCount = resultTypesBitSet.cardinality();
            assert typesCount > 0 : typesCount;
            if (typesCount == 1) {
                AnalysisType type = bb.getUniverse().getType(resultTypesBitSet.nextSetBit(0));
                return singleTypeState(bb, resultCanBeNull, type);
            } else {
                return multiTypeState(bb, resultCanBeNull, resultTypesBitSet, typesCount);
            }
        } else {
            return s1.forCanBeNull(bb, resultCanBeNull);
        }
    }

    @Override
    public TypeState doSubtraction(PointsToAnalysis bb, MultiTypeState s1, MultiTypeState s2) {
        boolean resultCanBeNull = s1.canBeNull() && !s2.canBeNull();

        if (s1 instanceof MultiTypeStateWithArray) {
            /* If s1 is array-based, hence small, the result will not be any bigger. */
            return subtractIntoArrayBased(bb, s1, s2, resultCanBeNull);
        }

        /* Speculate that s1 and s2 have either the same types, or no types in common. */
        var bitSet1 = getBitSet(s1);
        var bitSet2 = getBitSet(s2);

        if (bitSet1.equals(bitSet2)) {
            /* Speculate that s1 and s2 have the same types, i.e., the result is empty set. */
            return TypeState.forEmpty().forCanBeNull(bb, resultCanBeNull);
        }

        if (!bitSet1.intersects(bitSet2)) {
            /* Speculate that s1 and s2 have no types in common, i.e., the result is s1. */
            return s1.forCanBeNull(bb, resultCanBeNull);
        }

        /*
         * Speculate that s2 contains all types of s1, i.e., the filter is broader than s1, thus the
         * result is empty.
         */
        if (TypeStateUtils.isSuperset(bitSet2, bitSet1)) {
            return TypeState.forEmpty().forCanBeNull(bb, resultCanBeNull);
        }

        BitSet resultTypesBitSet = TypeStateUtils.andNot(bitSet1, bitSet2);
        int typesCount = resultTypesBitSet.cardinality();
        if (typesCount == 0) {
            return TypeState.forEmpty().forCanBeNull(bb, resultCanBeNull);
        } else if (typesCount == 1) {
            AnalysisType type = bb.getUniverse().getType(resultTypesBitSet.nextSetBit(0));
            return singleTypeState(bb, resultCanBeNull, type);
        } else if (typesCount <= bb.analysisPolicy().multiTypeStateArrayBitSetThreshold()) {
            return PointsToStats.registerTypeState(bb, new MultiTypeStateWithArray(resultCanBeNull, TypeStateUtils.typeIdArrayFromBitSet(resultTypesBitSet)));
        } else {
            return multiTypeState(bb, resultCanBeNull, resultTypesBitSet, typesCount);
        }
    }

    /**
     * Optimized subtraction when the result is known to be small-enough to be array-based.
     */
    private TypeState subtractIntoArrayBased(PointsToAnalysis bb, TypeState source, TypeState toRemove, boolean resultCanBeNull) {
        assert source.typesCount() <= bb.analysisPolicy().multiTypeStateArrayBitSetThreshold() + 1 : "The source type state is too large, explicit iteration could be slow: " + source;
        var resultArray = threadLocalTypeIdArray.get();
        int len = 0;
        for (Iterator<Integer> iterator = source.typeIdsIterator(); iterator.hasNext();) {
            int typeId = iterator.next();
            if (!toRemove.containsType(typeId)) {
                resultArray[len++] = typeId;
            }
        }
        if (len == 0) {
            return TypeState.forEmpty().forCanBeNull(bb, resultCanBeNull);
        } else if (len == 1) {
            return singleTypeState(bb, resultCanBeNull, bb.getUniverse().getType(resultArray[0]));
        } else if (len == source.typesCount()) {
            /* Nothing was removed, reuse input. */
            return source.forCanBeNull(bb, resultCanBeNull);
        } else {
            assert len < source.typesCount() : "No types were removed, allocating new TypeState is not necessary: " + source + " " + len;
            assert len <= bb.analysisPolicy().multiTypeStateArrayBitSetThreshold() : "Post condition check failed: the resulting type state is not small enough: " + source;
            return multiTypeState(bb, resultCanBeNull, copySubArray(resultArray, len));
        }
    }

    @Override
    public void processArrayCopyStates(PointsToAnalysis bb, TypeState srcArrayState, TypeState dstArrayState) {
        /* In the default configuration, when array aliasing is enabled, this method is not used. */
        assert !bb.analysisPolicy().aliasArrayTypeFlows() : "policy mismatch";

        /*
         * The source and destination array can have reference types which, although must be
         * compatible, can be different.
         */
        for (AnalysisObject srcArrayObject : srcArrayState.objects(bb)) {
            if (!srcArrayObject.type().isArray()) {
                /*
                 * Ignore non-array type. Sometimes the analysis cannot filter out non-array types
                 * flowing into array copy, however this will fail at runtime.
                 */
                continue;
            }

            if (srcArrayObject.isPrimitiveArray() || srcArrayObject.isEmptyObjectArrayConstant(bb)) {
                /* Nothing to read from a primitive array or an empty array constant. */
                continue;
            }

            ArrayElementsTypeFlow srcArrayElementsFlow = srcArrayObject.getArrayElementsFlow(bb, false);

            for (AnalysisObject dstArrayObject : dstArrayState.objects(bb)) {
                if (!dstArrayObject.type().isArray()) {
                    /* Ignore non-array type. */
                    continue;
                }

                if (dstArrayObject.isPrimitiveArray() || dstArrayObject.isEmptyObjectArrayConstant(bb)) {
                    /* Cannot write to a primitive array or an empty array constant. */
                    continue;
                }

                if (areTypesCompatibleForSystemArraycopy(srcArrayObject.type(), dstArrayObject.type())) {
                    ArrayElementsTypeFlow dstArrayElementsFlow = dstArrayObject.getArrayElementsFlow(bb, true);
                    srcArrayElementsFlow.addUse(bb, dstArrayElementsFlow);
                }
            }
        }
    }
}
