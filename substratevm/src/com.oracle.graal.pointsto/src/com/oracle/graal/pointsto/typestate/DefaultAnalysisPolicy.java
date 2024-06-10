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

import java.util.BitSet;
import java.util.Objects;

import org.graalvm.compiler.options.OptionValues;

import com.oracle.graal.pointsto.AnalysisPolicy;
import com.oracle.graal.pointsto.PointsToAnalysis;
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

import jdk.vm.ci.code.BytecodePosition;
import jdk.vm.ci.meta.JavaConstant;

public class DefaultAnalysisPolicy extends AnalysisPolicy {

    public DefaultAnalysisPolicy(OptionValues options) {
        super(options);
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
    public ConstantTypeState constantTypeState(PointsToAnalysis bb, JavaConstant constant, AnalysisType exactType) {
        return new ConstantTypeState(bb, exactType, constant);
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
        assert object.isContextInsensitiveObject();
        return new UnifiedFieldTypeStore(field, object, new FieldTypeFlow(field, field.getType(), object));
    }

    @Override
    public ArrayElementsTypeStore createArrayElementsTypeStore(AnalysisObject object, AnalysisUniverse universe) {
        assert object.isContextInsensitiveObject();
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
    public AbstractVirtualInvokeTypeFlow createVirtualInvokeTypeFlow(BytecodePosition invokeLocation, AnalysisType receiverType, PointsToAnalysisMethod targetMethod,
                    TypeFlow<?>[] actualParameters, ActualReturnTypeFlow actualReturn, MultiMethod.MultiMethodKey callerMultiMethodKey) {
        return new DefaultVirtualInvokeTypeFlow(invokeLocation, receiverType, targetMethod, actualParameters, actualReturn, callerMultiMethodKey);
    }

    @Override
    public AbstractSpecialInvokeTypeFlow createSpecialInvokeTypeFlow(BytecodePosition invokeLocation, AnalysisType receiverType, PointsToAnalysisMethod targetMethod,
                    TypeFlow<?>[] actualParameters, ActualReturnTypeFlow actualReturn, MultiMethod.MultiMethodKey callerMultiMethodKey) {
        return new DefaultSpecialInvokeTypeFlow(invokeLocation, receiverType, targetMethod, actualParameters, actualReturn, callerMultiMethodKey);
    }

    @Override
    public AbstractStaticInvokeTypeFlow createStaticInvokeTypeFlow(BytecodePosition invokeLocation, AnalysisType receiverType, PointsToAnalysisMethod targetMethod,
                    TypeFlow<?>[] actualParameters, ActualReturnTypeFlow actualReturn, MultiMethod.MultiMethodKey callerMultiMethodKey) {
        return new DefaultStaticInvokeTypeFlow(invokeLocation, receiverType, targetMethod, actualParameters, actualReturn, callerMultiMethodKey);
    }

    @Override
    public InvokeTypeFlow createDeoptInvokeTypeFlow(BytecodePosition invokeLocation, AnalysisType receiverType, PointsToAnalysisMethod targetMethod,
                    TypeFlow<?>[] actualParameters, ActualReturnTypeFlow actualReturn, MultiMethod.MultiMethodKey callerMultiMethodKey) {
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
        return new SingleTypeState(bb, canBeNull, type);
    }

    @Override
    public MultiTypeState multiTypeState(PointsToAnalysis bb, boolean canBeNull, BitSet typesBitSet, int typesCount, AnalysisObject... objects) {
        return new MultiTypeState(bb, canBeNull, typesBitSet, typesCount);
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
                assert cs1.exactType().equals(cs2.exactType());
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
            BitSet typesBitSet = TypeStateUtils.newBitSet(s1.exactType().getId(), s2.exactType().getId());
            assert typesBitSet.cardinality() == 2;
            TypeState result = new MultiTypeState(bb, resultCanBeNull, typesBitSet, 2);
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
            BitSet typesBitSet = TypeStateUtils.set(s1.typesBitSet(), s2.exactType().getId());
            int typesCount = s1.typesCount() + 1;
            assert typesCount == typesBitSet.cardinality();
            MultiTypeState result = new MultiTypeState(bb, resultCanBeNull, typesBitSet, typesCount);
            PointsToStats.registerUnionOperation(bb, s1, s2, result);
            return result;
        }
    }

    @Override
    public TypeState doUnion(PointsToAnalysis bb, MultiTypeState s1, MultiTypeState s2) {
        assert s1.typesCount() >= s2.typesCount();

        boolean resultCanBeNull = s1.canBeNull() || s2.canBeNull();

        /*
         * No need for a deep equality check (which would need to iterate the arrays), since the
         * speculation logic below is doing that anyway.
         */
        if (s1.typesBitSet() == s2.typesBitSet()) {
            return s1.forCanBeNull(bb, resultCanBeNull);
        }

        /* Speculate that s1 is a superset of s2. */
        if (TypeStateUtils.isSuperset(s1.typesBitSet(), s2.typesBitSet())) {
            return s1.forCanBeNull(bb, resultCanBeNull);
        }

        /* Logical OR the type bit sets. */
        BitSet resultTypesBitSet = TypeStateUtils.or(s1.typesBitSet(), s2.typesBitSet());

        MultiTypeState result = new MultiTypeState(bb, resultCanBeNull, resultTypesBitSet, resultTypesBitSet.cardinality());
        assert !result.equals(s1) && !result.equals(s2);
        PointsToStats.registerUnionOperation(bb, s1, s2, result);
        return result;
    }

    @Override
    public TypeState doIntersection(PointsToAnalysis bb, MultiTypeState s1, SingleTypeState s2) {
        assert !bb.extendedAsserts() || TypeStateUtils.isContextInsensitiveTypeState(bb, s2) : "Current implementation limitation.";

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

        /* Speculate that s1 and s2 have either the same types, or no types in common. */

        if (s1.typesBitSet().equals(s2.typesBitSet())) {
            /* Speculate that s1 and s2 have the same types, i.e., the result is s1. */
            return s1.forCanBeNull(bb, resultCanBeNull);
        }

        if (!s1.typesBitSet().intersects(s2.typesBitSet())) {
            /* Speculate that s1 and s2 have no types in common, i.e., the result is empty. */
            return TypeState.forEmpty().forCanBeNull(bb, resultCanBeNull);
        }

        /*
         * Speculate that s2 contains all types of s1, i.e., the filter is broader than s1, thus the
         * result is s1.
         */
        if (TypeStateUtils.isSuperset(s2.typesBitSet(), s1.typesBitSet())) {
            return s1.forCanBeNull(bb, resultCanBeNull);
        }

        BitSet resultTypesBitSet = TypeStateUtils.and(s1.typesBitSet(), s2.typesBitSet());
        int typesCount = resultTypesBitSet.cardinality();
        if (typesCount == 0) {
            return TypeState.forEmpty().forCanBeNull(bb, resultCanBeNull);
        } else if (typesCount == 1) {
            AnalysisType type = bb.getUniverse().getType(resultTypesBitSet.nextSetBit(0));
            return new SingleTypeState(bb, resultCanBeNull, type);
        } else {
            MultiTypeState result = new MultiTypeState(bb, resultCanBeNull, resultTypesBitSet, typesCount);
            assert !result.equals(s1);
            return result;
        }
    }

    @Override
    public TypeState doSubtraction(PointsToAnalysis bb, MultiTypeState s1, SingleTypeState s2) {
        assert !bb.extendedAsserts() || TypeStateUtils.isContextInsensitiveTypeState(bb, s2) : "Current implementation limitation.";
        boolean resultCanBeNull = s1.canBeNull() && !s2.canBeNull();
        if (s1.containsType(s2.exactType())) {
            /* s2 is contained in s1, so remove s2's type from s1. */
            BitSet resultTypesBitSet = TypeStateUtils.clear(s1.typesBitSet(), s2.exactType().getId());
            int typesCount = resultTypesBitSet.cardinality();
            assert typesCount > 0;
            if (typesCount == 1) {
                AnalysisType type = bb.getUniverse().getType(resultTypesBitSet.nextSetBit(0));
                return new SingleTypeState(bb, resultCanBeNull, type);
            } else {
                return new MultiTypeState(bb, resultCanBeNull, resultTypesBitSet, typesCount);
            }
        } else {
            return s1.forCanBeNull(bb, resultCanBeNull);
        }
    }

    @Override
    public TypeState doSubtraction(PointsToAnalysis bb, MultiTypeState s1, MultiTypeState s2) {
        boolean resultCanBeNull = s1.canBeNull() && !s2.canBeNull();

        /* Speculate that s1 and s2 have either the same types, or no types in common. */

        if (s1.typesBitSet().equals(s2.typesBitSet())) {
            /* Speculate that s1 and s2 have the same types, i.e., the result is empty set. */
            return TypeState.forEmpty().forCanBeNull(bb, resultCanBeNull);
        }

        if (!s1.typesBitSet().intersects(s2.typesBitSet())) {
            /* Speculate that s1 and s2 have no types in common, i.e., the result is s1. */
            return s1.forCanBeNull(bb, resultCanBeNull);
        }

        /*
         * Speculate that s2 contains all types of s1, i.e., the filter is broader than s1, thus the
         * result is empty.
         */
        if (TypeStateUtils.isSuperset(s2.typesBitSet(), s1.typesBitSet())) {
            return TypeState.forEmpty().forCanBeNull(bb, resultCanBeNull);
        }

        BitSet resultTypesBitSet = TypeStateUtils.andNot(s1.typesBitSet(), s2.typesBitSet());
        int typesCount = resultTypesBitSet.cardinality();
        if (typesCount == 0) {
            return TypeState.forEmpty().forCanBeNull(bb, resultCanBeNull);
        } else if (typesCount == 1) {
            AnalysisType type = bb.getUniverse().getType(resultTypesBitSet.nextSetBit(0));
            return new SingleTypeState(bb, resultCanBeNull, type);
        } else {
            return new MultiTypeState(bb, resultCanBeNull, resultTypesBitSet, typesCount);
        }
    }

    @Override
    public void processArrayCopyStates(PointsToAnalysis bb, TypeState srcArrayState, TypeState dstArrayState) {
        /* In the default configuration, when array aliasing is enabled, this method is not used. */
        assert !bb.analysisPolicy().aliasArrayTypeFlows();

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
