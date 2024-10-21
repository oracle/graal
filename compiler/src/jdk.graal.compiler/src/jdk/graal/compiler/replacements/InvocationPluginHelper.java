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
package jdk.graal.compiler.replacements;

import java.util.ArrayList;

import jdk.graal.compiler.core.common.calc.CanonicalCondition;
import jdk.graal.compiler.core.common.calc.Condition;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.BeginNode;
import jdk.graal.compiler.nodes.ComputeObjectAddressNode;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.DeoptimizeNode;
import jdk.graal.compiler.nodes.EndNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.LogicNegationNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.MergeNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.ProfileData;
import jdk.graal.compiler.nodes.StateSplit;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.nodes.calc.AddNode;
import jdk.graal.compiler.nodes.calc.IntegerBelowNode;
import jdk.graal.compiler.nodes.calc.IntegerEqualsNode;
import jdk.graal.compiler.nodes.calc.IntegerLessThanNode;
import jdk.graal.compiler.nodes.calc.LeftShiftNode;
import jdk.graal.compiler.nodes.calc.ObjectEqualsNode;
import jdk.graal.compiler.nodes.calc.OrNode;
import jdk.graal.compiler.nodes.calc.RightShiftNode;
import jdk.graal.compiler.nodes.calc.SignExtendNode;
import jdk.graal.compiler.nodes.calc.SubNode;
import jdk.graal.compiler.nodes.calc.UnsignedRightShiftNode;
import jdk.graal.compiler.nodes.calc.XorNode;
import jdk.graal.compiler.nodes.extended.GuardingNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin;
import jdk.graal.compiler.nodes.java.ArrayLengthNode;
import jdk.graal.compiler.nodes.java.LoadFieldNode;
import jdk.graal.compiler.nodes.memory.address.AddressNode;
import jdk.graal.compiler.nodes.memory.address.OffsetAddressNode;
import jdk.graal.compiler.nodes.type.StampTool;
import jdk.graal.compiler.word.Word;
import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * This is a helper class for writing moderately complex {@link InvocationPlugin InvocationPlugins}.
 * It's intentionally more limited than something like {@link GraphKit} because it's rarely useful
 * to write explicit plugins for complex pieces of logic. They are better handled by writing a
 * snippet for the complex logic and having an {@link InvocationPlugin} that performs any required
 * null checks or range checks and then adds a node which is later lowered by the snippet. A major
 * reason for this is that any complex control invariably has {@link MergeNode}s which are required
 * to have a valid {@link FrameState}s when they comes out of the parser. For intrinsics the only
 * valid {@link FrameState}s is the entry state and the state after the return and it can be hard to
 * correctly assign states to the merges. There is also little benefit to introducing complex
 * intrinsic graphs early because they are rarely amenable to high level optimizations.
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
 * variants. It models a side exit from a main sequence of logic. {@link FixedWithNextNode}s are
 * inserted in the main control flow and until the final return value is emitted with
 * {@link #emitFinalReturn(JavaKind, ValueNode)}. If only a single value is returned the normal
 * {@link GraphBuilderContext#addPush(JavaKind, ValueNode)} can be used instead.
 */
public class InvocationPluginHelper implements DebugCloseable {
    protected final GraphBuilderContext b;
    private final JavaKind wordKind;
    private final JavaKind returnKind;
    private final ArrayList<ReturnData> returns = new ArrayList<>();
    private boolean emittedReturn;
    private FixedWithNextNode fallbackEntry;

    public ValueNode arraylength(ValueNode receiverValue) {
        assert StampTool.isPointerNonNull(receiverValue);
        return b.add(ArrayLengthNode.create(receiverValue, b.getConstantReflection()));
    }

    @Override
    public void close() {
        if (returns.size() != 0 && !emittedReturn) {
            throw new InternalError("must call emitReturn before plugin returns");
        }
    }

    public JavaKind getWordKind() {
        return wordKind;
    }

    public PiNode piCast(ValueNode value, GuardingNode nonnullGuard, Stamp stamp) {
        return b.add(new PiNode(value, stamp, nonnullGuard.asNode()));
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

    public ValueNode or(ValueNode x, ValueNode y) {
        return b.add(new OrNode(x, y));
    }

    public ValueNode add(ValueNode x, ValueNode y) {
        return b.add(new AddNode(x, y));
    }

    public ValueNode sub(ValueNode x, ValueNode y) {
        return b.add(new SubNode(x, y));
    }

    public ValueNode length(ValueNode x) {
        // The visible type of the stamp should either be array type or Object. It's sometimes
        // Object because of cycles that hide the underlying type.
        ResolvedJavaType type = StampTool.typeOrNull(x);
        assert type == null || type.isArray() || type.isJavaLangObject() : x.stamp(NodeView.DEFAULT);
        return b.add(new ArrayLengthNode(x));
    }

    /**
     * Computes the address of an array element. The {@code array} is expected to be a byte[] while
     * the {@code kind} may be some larger primitive type.
     */
    public ValueNode arrayElementPointerScaled(ValueNode array, JavaKind kind, ValueNode index) {
        return arrayElementPointer(array, kind, index, true, false);
    }

    /**
     * Computes the address of an array element. The {@code kind} is expected to match type of the
     * {@code array}.
     */
    public ValueNode arrayElementPointer(ValueNode array, JavaKind kind, ValueNode index) {
        return arrayElementPointer(array, kind, index, false, false);
    }

    /**
     * Unsafe variant of {@link #arrayElementPointer(ValueNode, JavaKind, ValueNode)}.
     */
    public ValueNode arrayElementPointer(ValueNode array, JavaKind kind, ValueNode index, boolean skipComponentTypeCheck) {
        return arrayElementPointer(array, kind, index, false, skipComponentTypeCheck);
    }

    private ValueNode arrayElementPointer(ValueNode array, JavaKind kind, ValueNode index, boolean scaled, boolean skipComponentTypeCheck) {
        // Permit scaled addressing within byte arrays
        JavaKind actualKind = scaled ? JavaKind.Byte : kind;
        // The visible type of the stamp should either be array type or Object. It's sometimes
        // Object because of cycles that hide the underlying type.
        ResolvedJavaType type = StampTool.typeOrNull(array);
        assert skipComponentTypeCheck || type == null || (type.isArray() && type.getComponentType().getJavaKind() == actualKind) || type.isJavaLangObject() : array.stamp(NodeView.DEFAULT);
        int arrayBaseOffset = b.getMetaAccess().getArrayBaseOffset(kind);
        ValueNode offset = ConstantNode.forIntegerKind(wordKind, arrayBaseOffset);
        if (index != null) {
            ValueNode scaledIndex = shl(asWord(index), CodeUtil.log2(b.getMetaAccess().getArrayIndexScale(kind)));
            offset = add(offset, scaledIndex);
        }

        GraalError.guarantee(offset.getStackKind() == wordKind, "should have been promoted to word: %s", index);
        return b.add(new ComputeObjectAddressNode(array, offset));
    }

    /**
     * Returns the address of the first element of an array.
     */
    public ValueNode arrayStart(ValueNode array, JavaKind kind) {
        return arrayElementPointer(array, kind, null);
    }

    /**
     * Builds an {@link OffsetAddressNode} ensuring that the offset is also converted to a
     * {@link Word}.
     */
    public AddressNode makeOffsetAddress(ValueNode base, ValueNode offset) {
        return b.add(new OffsetAddressNode(base, asWord(offset)));
    }

    /**
     * Ensures a primitive type is word sized.
     */
    public ValueNode asWord(ValueNode index) {
        assert index.getStackKind().isPrimitive();
        if (index.getStackKind() != wordKind) {
            return SignExtendNode.create(index, wordKind.getBitCount(), NodeView.DEFAULT);
        }
        return index;
    }

    public ValueNode asWord(long index) {
        return ConstantNode.forIntegerKind(wordKind, index);
    }

    private LogicNode createCompare(ValueNode origX, Condition condition, ValueNode origY) {
        Condition.CanonicalizedCondition canonicalizedCondition = condition.canonicalize();
        // Check whether the condition needs to mirror the operands.
        ValueNode x = origX;
        ValueNode y = origY;
        if (canonicalizedCondition.mustMirror()) {
            x = origY;
            y = origX;
        }
        LogicNode compare = createCompare(x, canonicalizedCondition.getCanonicalCondition(), y);
        if (canonicalizedCondition.mustNegate()) {
            compare = LogicNegationNode.create(compare);
        }
        return compare;
    }

    public LogicNode createCompare(ValueNode x, CanonicalCondition cond, ValueNode y) {
        assert !x.getStackKind().isNumericFloat();
        switch (cond) {
            case EQ:
                if (x.getStackKind() == JavaKind.Object) {
                    return ObjectEqualsNode.create(b.getConstantReflection(), b.getMetaAccess(), y.getOptions(), x, y, NodeView.DEFAULT);
                } else {
                    return IntegerEqualsNode.create(b.getConstantReflection(), b.getMetaAccess(), y.getOptions(), null, x, y, NodeView.DEFAULT);
                }
            case LT:
                GraalError.guarantee(x.getStackKind() != JavaKind.Object, "object not allowed");
                return IntegerLessThanNode.create(b.getConstantReflection(), b.getMetaAccess(), y.getOptions(), null, x, y, NodeView.DEFAULT);
            case BT:
                GraalError.guarantee(x.getStackKind() != JavaKind.Object, "object not allowed");
                return IntegerBelowNode.create(b.getConstantReflection(), b.getMetaAccess(), y.getOptions(), null, x, y, NodeView.DEFAULT);
            default:
                throw GraalError.shouldNotReachHere("Unexpected condition: " + cond); // ExcludeFromJacocoGeneratedReport
        }
    }

    public ValueNode loadField(ValueNode value, ResolvedJavaField field) {
        return b.add(LoadFieldNode.create(b.getConstantFieldProvider(), b.getConstantReflection(), b.getMetaAccess(),
                        b.getOptions(), b.getAssumptions(), value, field, false, false, b.getGraph().currentNodeSourcePosition()));
    }

    /**
     * Finds a Java field by name.
     *
     * @throws GraalError if the field isn't found.
     */
    public ResolvedJavaField getField(ResolvedJavaType type, String fieldName) {
        for (ResolvedJavaField field : type.getInstanceFields(true)) {
            if (field.getName().equals(fieldName)) {
                return field;
            }
        }
        throw new GraalError("missing field " + fieldName + " in type " + type);
    }

    /**
     * Performs a range check for an intrinsic. This is a range check that represents a hard error
     * in the intrinsic so it's permissible to throw an exception directly for this case.
     */
    public GuardingNode intrinsicRangeCheck(ValueNode x, Condition condition, ValueNode y) {
        return b.intrinsicRangeCheck(createCompare(x, condition, y), false);
    }

    /**
     * Check that all indexes in the half open range [{@code index}, {@code index + offset}) are
     * within {@code array}.
     */
    public void intrinsicArrayRangeCheck(ValueNode array, ValueNode index, ValueNode offset) {
        intrinsicRangeCheck(index, Condition.LT, ConstantNode.forInt(0));
        ValueNode length = length(b.nullCheckedValue(array));
        intrinsicRangeCheck(add(index, offset), Condition.AT, length);
    }

    /**
     * Check that all indexes in the half open range [{@code 2 * index},
     * {@code 2 * (index + offset)}) are within {@code array}. To avoid overflow issues, the
     * underlying length is divided by 2.
     */
    public void intrinsicArrayRangeCheckScaled(ValueNode array, ValueNode index, ValueNode offset) {
        intrinsicRangeCheck(index, Condition.LT, ConstantNode.forInt(0));
        ValueNode length = length(b.nullCheckedValue(array));
        ValueNode shiftedLength = shr(length, 1);
        intrinsicRangeCheck(add(index, offset), Condition.AT, shiftedLength);
    }

    public AbstractBeginNode emitReturnIf(LogicNode condition, ValueNode returnValue, double returnProbability) {
        return emitReturnIf(condition, false, returnValue, returnProbability);
    }

    public AbstractBeginNode emitReturnIf(ValueNode x, Condition condition, ValueNode y, ValueNode returnValue, double returnProbability) {
        return emitReturnIf(createCompare(x, condition, y), false, returnValue, returnProbability);
    }

    public AbstractBeginNode emitReturnIfNot(LogicNode condition, ValueNode returnValue, double returnProbability) {
        return emitReturnIf(condition, true, returnValue, returnProbability);
    }

    /**
     * Builds an {@link IfNode} that returns a value based on the condition and the negated flag.
     * This can currently only be used for simple return values and doesn't allow {@link FixedNode
     * FixedNodes} to be part of the return path. All the return values are linked to a
     * {@link MergeNode} which terminates the graphs produced by the plugin.
     */
    private AbstractBeginNode emitReturnIf(LogicNode condition, boolean negated, ValueNode returnValue, double returnProbability) {
        BeginNode trueSuccessor = b.getGraph().add(new BeginNode());
        EndNode end = b.getGraph().add(new EndNode());
        trueSuccessor.setNext(end);
        addReturnValue(end, returnKind, returnValue);
        ProfileData.BranchProbabilityData probability = ProfileData.BranchProbabilityData.injected(returnProbability, negated);
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

    /**
     * Emits the {@code returnValue} and connects it to any other return values emitted before.
     * <p/>
     *
     * This will add the return value to the graph if necessary. If the return value is a
     * {@link StateSplit}, it should <em>not</em> be added to the graph using
     * {@link GraphBuilderContext#add} before calling this method.
     */
    public void emitFinalReturn(JavaKind kind, ValueNode returnValue) {
        GraalError.guarantee(!emittedReturn, "must only have one final return");
        GraalError.guarantee(kind == returnKind, "mismatch in return kind");
        if (kind != JavaKind.Void) {
            b.addPush(kind, returnValue);
        }

        if (returns.size() > 0) {
            // Restore the previous frame state
            if (kind != JavaKind.Void) {
                b.pop(returnKind);
            }

            EndNode end = b.append(new EndNode());
            addReturnValue(end, kind, returnValue);
            MergeNode returnMerge = b.append(new MergeNode());
            ValuePhiNode returnPhi = null;
            if (returnKind != JavaKind.Void) {
                returnPhi = b.add(new ValuePhiNode(StampFactory.forKind(returnKind), returnMerge));
            }
            returnMerge.setStateAfter(b.getInvocationPluginReturnState(kind, returnPhi));
            for (ReturnData r : returns) {
                returnMerge.addForwardEnd(r.end);
                if (returnPhi != null) {
                    returnPhi.addInput(r.returnValue);
                } else {
                    assert r.returnValue == null;
                }
            }
            if (kind != JavaKind.Void) {
                b.addPush(returnKind, returnPhi.singleValueOrThis());
            }
        }
        emittedReturn = true;
    }

    /**
     * Connects an {@link EndNode} and return value into the final control flow merge.
     */
    protected void addReturnValue(EndNode end, JavaKind kind, ValueNode returnValueInput) {
        assert b.canMergeIntrinsicReturns();
        GraalError.guarantee(kind == returnKind, "mismatch in return kind");
        ValueNode returnValue = returnValueInput;
        if (kind != JavaKind.Void) {
            if (returnValue.isUnregistered()) {
                GraalError.guarantee(!(returnValue instanceof FixedNode), "unexpected FixedNode");
                returnValue = b.add(returnValue);
            }
        }
        returns.add(new ReturnData(end, returnValue));
    }

    /**
     * Creates a branch to the fallback path, building a {@link MergeNode} if necessary.
     */
    private BeginNode branchToFallback() {
        if (fallbackEntry == null) {
            BeginNode fallbackSuccessor = b.getGraph().add(new BeginNode());
            EndNode end = b.getGraph().add(new EndNode());
            Invoke invoke = b.invokeFallback(fallbackSuccessor, end);
            if (invoke != null) {
                fallbackEntry = fallbackSuccessor;
                addReturnValue(end, returnKind, returnKind == JavaKind.Void ? null : invoke.asNode());
            } else {
                assert end.predecessor() == null;
                end.safeDelete();
            }
            return fallbackSuccessor;
        }
        // Multiple paths lead to the fallback, so upgrade it to a MergeNode
        if (!(fallbackEntry instanceof MergeNode)) {
            assert fallbackEntry instanceof BeginNode : Assertions.errorMessage(fallbackEntry);
            BeginNode begin = (BeginNode) fallbackEntry;
            FixedNode next = begin.next();
            EndNode end = b.getGraph().add(new EndNode());
            begin.setNext(end);
            MergeNode merge = b.getGraph().add(new MergeNode());
            merge.addForwardEnd(end);
            merge.setNext(next);
            fallbackEntry = merge;
            merge.setStateAfter(b.getInvocationPluginBeforeState());
        }
        MergeNode fallbackMerge = (MergeNode) fallbackEntry;
        BeginNode fallbackSuccessor = b.getGraph().add(new BeginNode());
        EndNode end = b.getGraph().add(new EndNode());
        fallbackSuccessor.setNext(end);
        fallbackMerge.addForwardEnd(end);
        return fallbackSuccessor;
    }

    public GuardingNode doFallbackIf(ValueNode x, Condition condition, ValueNode y, double returnProbability) {
        return doFallbackIf(createCompare(x, condition, y), false, returnProbability);
    }

    public GuardingNode doFallbackIfNot(LogicNode condition, double probability) {
        return doFallbackIf(condition, true, probability);
    }

    public GuardingNode doFallbackIf(LogicNode condition, double probability) {
        return doFallbackIf(condition, false, probability);
    }

    /**
     * Fallback to the original implementation based on the {@code condition}. Depending on the
     * environment the fallback may be done through a {@link DeoptimizeNode} or through a real
     * {@link Invoke}.
     */
    private GuardingNode doFallbackIf(LogicNode origCondition, boolean origNegated, double origProbability) {
        // We may insert NegationNode for API simplicity but go ahead and remove it now instead of
        // waiting for the canonicalization.
        boolean negated = origNegated;
        LogicNode condition = origCondition;
        double probability = origProbability;
        while (condition instanceof LogicNegationNode negation) {
            negated = !negated;
            condition = negation.getValue();
            probability = 1.0 - probability;
        }
        BeginNode fallbackSuccessor = branchToFallback();
        ProfileData.BranchProbabilityData probabilityData = ProfileData.BranchProbabilityData.injected(probability, negated);
        IfNode node = new IfNode(condition, negated ? null : fallbackSuccessor, negated ? fallbackSuccessor : null, probabilityData);
        IfNode ifNode = b.append(node);
        BeginNode fallThroughSuccessor = b.append(new BeginNode());
        if (negated) {
            ifNode.setTrueSuccessor(fallThroughSuccessor);
        } else {
            ifNode.setFalseSuccessor(fallThroughSuccessor);
        }
        return fallThroughSuccessor;
    }
}
