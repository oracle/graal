/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.replacements;

import java.util.ArrayList;

import org.graalvm.compiler.core.common.calc.CanonicalCondition;
import org.graalvm.compiler.core.common.calc.Condition;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.debug.DebugCloseable;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.BeginNode;
import org.graalvm.compiler.nodes.ComputeObjectAddressNode;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.EndNode;
import org.graalvm.compiler.nodes.FixedGuardNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.MergeNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ProfileData;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.ValuePhiNode;
import org.graalvm.compiler.nodes.calc.AddNode;
import org.graalvm.compiler.nodes.calc.IntegerEqualsNode;
import org.graalvm.compiler.nodes.calc.IntegerLessThanNode;
import org.graalvm.compiler.nodes.calc.LeftShiftNode;
import org.graalvm.compiler.nodes.calc.ObjectEqualsNode;
import org.graalvm.compiler.nodes.calc.RightShiftNode;
import org.graalvm.compiler.nodes.calc.SignExtendNode;
import org.graalvm.compiler.nodes.calc.SubNode;
import org.graalvm.compiler.nodes.calc.UnsignedRightShiftNode;
import org.graalvm.compiler.nodes.calc.XorNode;
import org.graalvm.compiler.nodes.extended.GuardingNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.java.ArrayLengthNode;
import org.graalvm.compiler.nodes.java.LoadFieldNode;

import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * This is a helper class for writing moderately complex
 * {@link org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin InvocationPlugins}. It's
 * intentionally more limited than something like {@link GraphKit} because it's rarely useful to
 * write explicit plugins for complex pieces of logic. They are better handled by writing a snippet
 * for the complex logic and having an {@link InvocationPlugin} that performs any required null
 * checks or range checks and then adds a node which is later lowered from the snippet. A major
 * reason for this is that any complex control invariably has {@link MergeNode MergeNodes} which are
 * required to have a valid {@link org.graalvm.compiler.nodes.FrameState} when they comes out of the
 * parser. For intrinsics the only valid {@link org.graalvm.compiler.nodes.FrameState FrameStates}
 * is the entry state and the state after then return and it can be hard to correctly assign states
 * to the merges. There is also little benefit to introducing complex intrinsic graphs early because
 * their are rarely amenable to high level optimizations.
 *
 * The recommended usage pattern is to construct the helper instance in a try/resource block, which
 * performs some sanity checking when the block is exited.
 *
 * <pre>
 * try (InvocationPluginHelper helper = new InvocationPluginHelper(b, targetMethod)) {
 * }
 * </pre>
 *
 * The main idiom provided is {@link #emitReturnIf(LogicNode, boolean, ValueNode, double)} plus
 * variants. It models a side exit from a main sequence of logic.
 * {@link org.graalvm.compiler.nodes.FixedWithNextNode FixedWithNextNodes} are inserted in the main
 * control flow and until the final return value is emitted with
 * {@link #emitFinalReturn(JavaKind, ValueNode)}. If only a single value is returned the normal
 * {@link GraphBuilderContext#addPush(JavaKind, ValueNode)} can be used instead.
 */
public class InvocationPluginHelper implements DebugCloseable {
    protected final GraphBuilderContext b;
    protected final JavaKind wordKind;
    private final JavaKind returnKind;
    private final ArrayList<ReturnData> returns = new ArrayList<>();
    private boolean emittedReturn;

    public ValueNode arraylength(ValueNode receiverValue) {
        // assert StampTool.isPointerAlwaysNull(receiverValue);
        return b.add(new ArrayLengthNode(receiverValue));
    }

    @Override
    public void close() {
        if (returns.size() != 0 && !emittedReturn) {
            throw new InternalError("must call emitReturn before plugin returns");
        }
    }

    static class ReturnData {
        final EndNode end;
        final ValueNode returnValue;

        ReturnData(EndNode end, ValueNode returnValue) {
            this.end = end;
            this.returnValue = returnValue;
        }
    }

    public InvocationPluginHelper(GraphBuilderContext b, ResolvedJavaMethod targetMethod) {
        this.b = b;
        this.wordKind = b.getReplacements().getWordKind();
        this.returnKind = targetMethod.getSignature().getReturnKind();
    }

    public ValueNode shl(ValueNode node, int shiftAmount) {
        if (shiftAmount == 0) {
            return node;
        }
        return b.add(new LeftShiftNode(node, ConstantNode.forInt(shiftAmount)));
    }

    public ValueNode shr(ValueNode node, int shiftAmount) {
        if (shiftAmount == 0) {
            return node;
        }
        return b.add(new RightShiftNode(node, ConstantNode.forInt(shiftAmount)));
    }

    public ValueNode ushr(ValueNode node, int shiftAmount) {
        if (shiftAmount == 0) {
            return node;
        }
        return b.add(new UnsignedRightShiftNode(node, ConstantNode.forInt(shiftAmount)));
    }

    public ValueNode xor(ValueNode x, ValueNode y) {
        return b.add(new XorNode(x, y));
    }

    public ValueNode add(ValueNode x, ValueNode y) {
        return b.add(new AddNode(x, y));
    }

    public ValueNode sub(ValueNode x, ValueNode y) {
        return b.add(new SubNode(x, y));
    }

    public ValueNode length(ValueNode x) {
        return b.add(new ArrayLengthNode(x));
    }

    public ValueNode arrayElementPointer(ValueNode array, JavaKind kind, ValueNode index) {
        int arrayBaseOffset = b.getMetaAccess().getArrayBaseOffset(kind);
        ValueNode offset = ConstantNode.forInt(arrayBaseOffset);
        if (index != null) {
            offset = add(offset, scale(index, kind));
        }

        return b.add(new ComputeObjectAddressNode(array, asWord(offset)));
    }

    public ValueNode scale(ValueNode index, JavaKind kind) {
        int arrayIndexShift = CodeUtil.log2(b.getMetaAccess().getArrayIndexScale(kind));
        return shl(index, arrayIndexShift);
    }

    public ValueNode asWord(ValueNode index) {
        assert index.getStackKind().isPrimitive();
        if (index.getStackKind() != wordKind) {
            return SignExtendNode.create(index, wordKind.getBitCount(), NodeView.DEFAULT);
        }
        return index;
    }

    private LogicNode createCompare(ValueNode origX, ValueNode origY, Condition.CanonicalizedCondition canonicalizedCondition) {
        // Check whether the condition needs to mirror the operands.
        ValueNode x = origX;
        ValueNode y = origY;
        if (canonicalizedCondition.mustMirror()) {
            x = origY;
            y = origX;
        }
        return createCompare(canonicalizedCondition.getCanonicalCondition(), x, y);
    }

    public LogicNode createCompare(CanonicalCondition cond, ValueNode x, ValueNode y) {
        assert !x.getStackKind().isNumericFloat();
        switch (cond) {
            case EQ:
                if (x.getStackKind() == JavaKind.Object) {
                    return ObjectEqualsNode.create(b.getConstantReflection(), b.getMetaAccess(), y.getOptions(), x, y, NodeView.DEFAULT);
                } else {
                    return IntegerEqualsNode.create(b.getConstantReflection(), b.getMetaAccess(), y.getOptions(), null, x, y, NodeView.DEFAULT);
                }
            case LT:
                assert x.getStackKind() != JavaKind.Object;
                return IntegerLessThanNode.create(b.getConstantReflection(), b.getMetaAccess(), y.getOptions(), null, x, y, NodeView.DEFAULT);
            default:
                throw GraalError.shouldNotReachHere("Unexpected condition: " + cond);
        }
    }

    public ValueNode loadField(ValueNode value, ResolvedJavaField field) {
        return b.add(LoadFieldNode.create(b.getConstantFieldProvider(), b.getConstantReflection(), b.getMetaAccess(),
                        b.getOptions(), b.getAssumptions(), value, field, false, false));
    }

    public void guard(ValueNode origX, Condition condition, ValueNode origY, DeoptimizationAction action, DeoptimizationReason deoptReason) {
        Condition.CanonicalizedCondition canonicalizedCondition = condition.canonicalize();
        LogicNode compare = createCompare(origX, origY, canonicalizedCondition);
        b.add(new FixedGuardNode(compare, deoptReason, action, !canonicalizedCondition.mustNegate()));
    }

    public GuardingNode intrinsicRangeCheck(ValueNode x, Condition condition, ValueNode y) {
        Condition.CanonicalizedCondition canonicalizedCondition = condition.canonicalize();
        LogicNode compare = createCompare(x, y, canonicalizedCondition);
        return b.intrinsicRangeCheck(compare, canonicalizedCondition.mustNegate());
    }

    public AbstractBeginNode emitReturnIf(LogicNode condition, ValueNode returnValue, double returnProbability) {
        return emitReturnIf(condition, false, returnValue, returnProbability);
    }

    public AbstractBeginNode emitReturnIf(ValueNode x, Condition condition, ValueNode y, ValueNode returnValue, double returnProbability) {
        Condition.CanonicalizedCondition canonicalizedCondition = condition.canonicalize();
        LogicNode compare = createCompare(x, y, canonicalizedCondition);
        return emitReturnIf(compare, canonicalizedCondition.mustNegate(), returnValue, returnProbability);
    }

    public AbstractBeginNode emitReturnIfNot(LogicNode condition, ValueNode returnValue, double returnProbability) {
        return emitReturnIf(condition, true, returnValue, returnProbability);
    }

    /**
     * Build an {@link IfNode} that returns a value based on the condition and the negated flag.
     * This can currently only be used for simple return values and doesn't allow {@link FixedNode
     * FixedNodes} to be part of the return path. All the return values are linked to a
     * {@link MergeNode} which terminates the graphs produced by the plugin.
     */
    public AbstractBeginNode emitReturnIf(LogicNode condition, boolean negated, ValueNode returnValue, double returnProbability) {
        BeginNode trueSuccessor = b.getGraph().add(new BeginNode());
        EndNode end = b.getGraph().add(new EndNode());
        trueSuccessor.setNext(end);
        addReturnValue(end, returnKind, returnValue);
        ProfileData.BranchProbabilityData probability = ProfileData.BranchProbabilityData.injected(returnProbability, true);
        IfNode node = new IfNode(condition, negated ? null : trueSuccessor, negated ? trueSuccessor : null, probability);
        IfNode ifNode = b.append(node);
        BeginNode otherSuccessor = b.append(new BeginNode());
        if (negated) {
            ifNode.setTrueSuccessor(otherSuccessor);
        } else {
            ifNode.setFalseSuccessor(otherSuccessor);
        }
        return otherSuccessor;
    }

    public void emitFinalReturn(JavaKind kind, ValueNode origReturnValue) {
        ValueNode returnValue = origReturnValue;
        assert !emittedReturn : "must only have one final return";
        if (returnValue.isUnregistered()) {
            returnValue = b.append(returnValue);
        }
        EndNode end = b.append(new EndNode());
        addReturnValue(end, kind, returnValue);

        MergeNode returnMerge = b.append(new MergeNode());
        ValuePhiNode returnPhi = null;
        if (returnKind != JavaKind.Void) {
            returnPhi = b.add(new ValuePhiNode(StampFactory.forKind(returnKind), returnMerge));
        }
        returnMerge.setStateAfter(b.getIntrinsicReturnState(kind, returnPhi));
        for (ReturnData r : returns) {
            returnMerge.addForwardEnd(r.end);
            if (returnPhi != null) {
                returnPhi.addInput(r.returnValue);
            } else {
                assert r.returnValue == null;
            }
        }
        b.addPush(returnKind, returnPhi);
        emittedReturn = true;
    }

    /**
     * Connect an {@link EndNode} and return value into the final control flow merge.
     */
    private void addReturnValue(EndNode end, JavaKind kind, ValueNode returnValueInput) {
        assert b.canMergeIntrinsicReturns();
        ValueNode returnValue = returnValueInput;
        if (returnValue.isUnregistered()) {
            assert !(returnValue instanceof FixedNode);
            returnValue = b.add(returnValue);
        }
        assert !returnValue.isUnregistered() : returnValue;
        assert kind == returnKind;
        returns.add(new ReturnData(end, returnValue));
    }
}
