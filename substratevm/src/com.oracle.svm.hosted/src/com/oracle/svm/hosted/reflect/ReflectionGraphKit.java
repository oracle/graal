/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.reflect;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.svm.core.graal.nodes.LoweredDeadEndNode;
import com.oracle.svm.core.reflect.ReflectionAccessorHolder;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.code.FactoryMethodSupport;
import com.oracle.svm.hosted.phases.HostedGraphKit;
import com.oracle.svm.util.ReflectionUtil;

import jdk.graal.compiler.core.common.calc.FloatConvert;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.common.type.TypeReference;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.AbstractMergeNode;
import jdk.graal.compiler.nodes.CallTargetNode;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.EndNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.InvokeWithExceptionNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.MergeNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.nodes.calc.FloatConvertNode;
import jdk.graal.compiler.nodes.calc.IntegerEqualsNode;
import jdk.graal.compiler.nodes.calc.IsNullNode;
import jdk.graal.compiler.nodes.calc.SignExtendNode;
import jdk.graal.compiler.nodes.extended.BranchProbabilityNode;
import jdk.graal.compiler.nodes.extended.GuardingNode;
import jdk.graal.compiler.nodes.java.ArrayLengthNode;
import jdk.graal.compiler.nodes.java.ExceptionObjectNode;
import jdk.graal.compiler.nodes.java.InstanceOfNode;
import jdk.graal.compiler.nodes.type.StampTool;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public class ReflectionGraphKit extends HostedGraphKit {

    static final Map<JavaKind, List<JavaKind>> PRIMITIVE_UNBOXINGS;
    static {
        PRIMITIVE_UNBOXINGS = new HashMap<>();
        /*- Widening conversions (from JVM spec):
         *    byte to short, int, long, float, or double
         *    short to int, long, float, or double
         *    char to int, long, float, or double
         *    int to long, float, or double
         *    long to float or double
         *    float to double
         *
         * The list elements are checked at run time in that order, so the order affects how many
         * type checks are done. The exact fit is always first, and then we just guess what is
         * likely.
         */
        PRIMITIVE_UNBOXINGS.put(JavaKind.Boolean, Arrays.asList(JavaKind.Boolean));
        PRIMITIVE_UNBOXINGS.put(JavaKind.Byte, Arrays.asList(JavaKind.Byte));
        PRIMITIVE_UNBOXINGS.put(JavaKind.Short, Arrays.asList(JavaKind.Short, JavaKind.Byte));
        PRIMITIVE_UNBOXINGS.put(JavaKind.Char, Arrays.asList(JavaKind.Char));
        PRIMITIVE_UNBOXINGS.put(JavaKind.Int, Arrays.asList(JavaKind.Int, JavaKind.Byte, JavaKind.Short, JavaKind.Char));
        PRIMITIVE_UNBOXINGS.put(JavaKind.Long, Arrays.asList(JavaKind.Long, JavaKind.Int, JavaKind.Byte, JavaKind.Short, JavaKind.Char));
        PRIMITIVE_UNBOXINGS.put(JavaKind.Float, Arrays.asList(JavaKind.Float, JavaKind.Int, JavaKind.Long, JavaKind.Byte, JavaKind.Short, JavaKind.Char));
        PRIMITIVE_UNBOXINGS.put(JavaKind.Double, Arrays.asList(JavaKind.Double, JavaKind.Float, JavaKind.Int, JavaKind.Long, JavaKind.Byte, JavaKind.Short, JavaKind.Char));
    }

    private final List<FixedWithNextNode> illegalArgumentExceptionPaths = new ArrayList<>();
    private final List<ExceptionObjectNode> invocationTargetExceptionPaths = new ArrayList<>();

    public ReflectionGraphKit(DebugContext debug, HostedProviders providers, ResolvedJavaMethod method) {
        super(debug, providers, method);
    }

    @Override
    public AbstractMergeNode endIf() {
        AbstractMergeNode merge = super.endIf();
        if (merge != null) {
            merge.setStateAfter(getFrameState().create(bci(), merge));
        }
        return merge;
    }

    public void branchToIllegalArgumentException() {
        illegalArgumentExceptionPaths.add(lastFixedNode);
        lastFixedNode = null;
    }

    public void branchToInvocationTargetException(ExceptionObjectNode exceptionObjectNode) {
        assert exceptionObjectNode == lastFixedNode;
        invocationTargetExceptionPaths.add(exceptionObjectNode);
        lastFixedNode = null;
    }

    /**
     * To reduce machine code size, we want only one call site for each exception class that can be
     * thrown. We also do not want arguments that require phi functions. So we cannot have an error
     * message that, e.g., prints which exact cast failed.
     */
    public void emitIllegalArgumentException(ValueNode obj, ValueNode args) {
        continueWithMerge(illegalArgumentExceptionPaths);
        ResolvedJavaMethod targetMethod;
        ValueNode[] arguments;
        if (obj == null) {
            targetMethod = findMethod(ReflectionAccessorHolder.class, "throwIllegalArgumentExceptionWithoutReceiver", true);
            arguments = new ValueNode[]{args};
        } else {
            targetMethod = findMethod(ReflectionAccessorHolder.class, "throwIllegalArgumentExceptionWithReceiver", true);
            arguments = new ValueNode[]{obj, args};
        }
        InvokeWithExceptionNode invoke = createJavaCallWithExceptionAndUnwind(CallTargetNode.InvokeKind.Static, targetMethod, arguments);
        invoke.setInlineControl(Invoke.InlineControl.Never);
        append(new LoweredDeadEndNode());
    }

    private static final Constructor<InvocationTargetException> invocationTargetExceptionConstructor = ReflectionUtil.lookupConstructor(InvocationTargetException.class, Throwable.class);

    public void emitInvocationTargetException() {
        AbstractMergeNode merge = continueWithMerge(invocationTargetExceptionPaths);
        ValueNode exception = createPhi(invocationTargetExceptionPaths, merge);
        ResolvedJavaMethod throwInvocationTargetException = FactoryMethodSupport.singleton().lookup(getMetaAccess(),
                        getMetaAccess().lookupJavaMethod(invocationTargetExceptionConstructor), true);
        InvokeWithExceptionNode invoke = createJavaCallWithExceptionAndUnwind(CallTargetNode.InvokeKind.Static, throwInvocationTargetException, exception);
        invoke.setInlineControl(Invoke.InlineControl.Never);
        append(new LoweredDeadEndNode());
    }

    public ValueNode startInstanceOf(ValueNode value, ResolvedJavaType type, boolean nonNull, boolean forException) {
        TypeReference typeRef = TypeReference.createTrusted(getAssumptions(), type);
        LogicNode condition;
        if (nonNull) {
            condition = append(InstanceOfNode.create(typeRef, value));
        } else {
            condition = append(InstanceOfNode.createAllowNull(typeRef, value, null, null));
        }

        startIf(condition, forException ? BranchProbabilityNode.FAST_PATH_PROFILE : BranchProbabilityNode.LIKELY_PROFILE);
        thenPart();

        return createPiNode(value, StampFactory.object(typeRef, nonNull));
    }

    public AbstractMergeNode continueWithMerge(List<? extends FixedWithNextNode> predecessors) {
        assert predecessors.size() > 0;
        if (predecessors.size() == 1) {
            lastFixedNode = predecessors.get(0);
            return null;
        }

        MergeNode merge = graph.add(new MergeNode());
        merge.setStateAfter(getFrameState().create(bci(), merge));
        for (int i = 0; i < predecessors.size(); i++) {
            EndNode end = graph.add(new EndNode());
            graph.addAfterFixed(predecessors.get(i), end);
            merge.addForwardEnd(end);
        }
        lastFixedNode = merge;
        return merge;
    }

    public ValueNode createPhi(List<? extends ValueNode> values, AbstractMergeNode merge) {
        if (values.size() == 1) {
            assert merge == null;
            return values.get(0);
        }
        assert values.size() == merge.forwardEndCount();
        return unique(new ValuePhiNode(StampTool.meetOrNull(values, null), merge, values.toArray(ValueNode.EMPTY_ARRAY)));
    }

    public void fillArgsArray(ValueNode argumentArray, int receiverOffset, ValueNode[] args, Class<?>[] argTypes) {
        /*
         * The length of the args array at run time must be the same as the length of argTypes.
         * Unless the length of argTypes is 0: in that case, null is allowed to be passed in at run
         * time too.
         */
        LogicNode argsNullCondition = append(IsNullNode.create(argumentArray));
        startIf(argsNullCondition, BranchProbabilityNode.SLOW_PATH_PROFILE);
        thenPart();
        if (argTypes.length == 0) {
            /* No arguments, so null is an allowed value. */
        } else {
            branchToIllegalArgumentException();
        }
        elsePart();
        PiNode argumentArrayNonNull = createPiNode(argumentArray, StampFactory.objectNonNull());

        ValueNode argsLength = append(ArrayLengthNode.create(argumentArrayNonNull, getConstantReflection()));
        LogicNode argsLengthCondition = append(IntegerEqualsNode.create(argsLength, ConstantNode.forInt(argTypes.length), NodeView.DEFAULT));
        startIf(argsLengthCondition, BranchProbabilityNode.FAST_PATH_PROFILE);
        elsePart();
        branchToIllegalArgumentException();
        thenPart();
        GuardingNode argsBoundsCheckGuard = AbstractBeginNode.prevBegin(lastFixedNode);

        for (int i = 0; i < argTypes.length; i++) {
            ValueNode arg = createLoadIndexed(argumentArrayNonNull, i, JavaKind.Object, argsBoundsCheckGuard);
            ResolvedJavaType argType = getMetaAccess().lookupJavaType(argTypes[i]);
            JavaKind argKind = asKind(argType);
            if (argKind.isPrimitive()) {
                arg = unboxPrimitive(arg, argKind);
            } else {
                arg = startInstanceOf(arg, argType, false, true);
                elsePart();
                branchToIllegalArgumentException();
                endIf();
            }
            args[i + receiverOffset] = arg;
        }

        /* IfStructure from argument array length check. */
        endIf();
        /* IfStructure from null check. */
        endIf();
    }

    private ValueNode unboxPrimitive(ValueNode boxedValue, JavaKind argKind) {
        startIf(append(IsNullNode.create(boxedValue)), BranchProbabilityNode.SLOW_PATH_PROFILE);
        thenPart();
        /* Cannot unbox "null". */
        branchToIllegalArgumentException();
        elsePart();
        PiNode boxedValueNonNull = createPiNode(boxedValue, StampFactory.objectNonNull());

        List<ValueNode> widenedValues = new ArrayList<>();
        List<FixedWithNextNode> controlFlows = new ArrayList<>();
        List<JavaKind> boxedKinds = PRIMITIVE_UNBOXINGS.get(argKind);
        for (JavaKind boxedKind : boxedKinds) {
            ResolvedJavaType boxedType = getMetaAccess().lookupJavaType(boxedKind.toBoxedJavaClass());

            ValueNode boxedValueCasted = startInstanceOf(boxedValueNonNull, boxedType, true, false);
            ValueNode unboxedValue = createUnboxing(boxedValueCasted, boxedKind);
            ValueNode widenedValue = widenPrimitive(unboxedValue, boxedKind, argKind);
            widenedValues.add(widenedValue);
            /* Merge of all valid control flow edges is appended later. */
            controlFlows.add(lastFixedNode);
            lastFixedNode = null;

            /* Continue with check from next loop iteration. */
            elsePart();
            /*
             * Since we collect all passing control flow edges manually, we do not need the control
             * flow structure anymore.
             */
            endIf();
        }
        /* Not a type that can be unboxed and widened to the expected primitive kind. */
        branchToIllegalArgumentException();

        /* Continue with all the passing control flow edges. */
        AbstractMergeNode merge = continueWithMerge(controlFlows);
        return createPhi(widenedValues, merge);
    }

    private ValueNode widenPrimitive(ValueNode unboxedValue, JavaKind fromKind, JavaKind toKind) {
        JavaKind fromStackKind = fromKind.getStackKind();
        JavaKind toStackKind = toKind.getStackKind();
        if (fromStackKind == toStackKind) {
            return unboxedValue;
        }

        switch (fromStackKind) {
            case Int:
                switch (toStackKind) {
                    case Long:
                        return graph.addOrUniqueWithInputs(SignExtendNode.create(unboxedValue, toStackKind.getBitCount(), NodeView.DEFAULT));
                    case Float:
                        return graph.addOrUniqueWithInputs(FloatConvertNode.create(FloatConvert.I2F, unboxedValue, NodeView.DEFAULT));
                    case Double:
                        return graph.addOrUniqueWithInputs(FloatConvertNode.create(FloatConvert.I2D, unboxedValue, NodeView.DEFAULT));
                    default:
                        throw VMError.shouldNotReachHereUnexpectedInput(toStackKind); // ExcludeFromJacocoGeneratedReport
                }
            case Long:
                switch (toStackKind) {
                    case Float:
                        return graph.addOrUniqueWithInputs(FloatConvertNode.create(FloatConvert.L2F, unboxedValue, NodeView.DEFAULT));
                    case Double:
                        return graph.addOrUniqueWithInputs(FloatConvertNode.create(FloatConvert.L2D, unboxedValue, NodeView.DEFAULT));
                    default:
                        throw VMError.shouldNotReachHereUnexpectedInput(toStackKind); // ExcludeFromJacocoGeneratedReport
                }
            case Float:
                switch (toStackKind) {
                    case Double:
                        return graph.addOrUniqueWithInputs(FloatConvertNode.create(FloatConvert.F2D, unboxedValue, NodeView.DEFAULT));
                    default:
                        throw VMError.shouldNotReachHereUnexpectedInput(toStackKind); // ExcludeFromJacocoGeneratedReport
                }
            default:
                throw VMError.shouldNotReachHereUnexpectedInput(fromStackKind); // ExcludeFromJacocoGeneratedReport
        }
    }
}
