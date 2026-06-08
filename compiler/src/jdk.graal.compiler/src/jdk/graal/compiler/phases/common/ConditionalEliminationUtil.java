/*
 * Copyright (c) 2019, 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.phases.common;

import java.util.ArrayDeque;

import org.graalvm.collections.Pair;

import jdk.graal.compiler.core.common.type.ArithmeticOpTable;
import jdk.graal.compiler.core.common.type.ArithmeticOpTable.BinaryOp;
import jdk.graal.compiler.core.common.type.ArithmeticOpTable.BinaryOp.Or;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeStack;
import jdk.graal.compiler.graph.Position;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodes.BinaryOpLogicNode;
import jdk.graal.compiler.nodes.DeoptimizingGuard;
import jdk.graal.compiler.nodes.GraphState.StageFlag;
import jdk.graal.compiler.nodes.GuardedValueNode;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ParameterNode;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.ShortCircuitOrNode;
import jdk.graal.compiler.nodes.UnaryOpLogicNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.AddNode;
import jdk.graal.compiler.nodes.calc.AndNode;
import jdk.graal.compiler.nodes.calc.BinaryArithmeticNode;
import jdk.graal.compiler.nodes.calc.BinaryNode;
import jdk.graal.compiler.nodes.calc.IntegerBelowNode;
import jdk.graal.compiler.nodes.calc.IntegerConvertNode;
import jdk.graal.compiler.nodes.calc.IntegerEqualsNode;
import jdk.graal.compiler.nodes.calc.TernaryNode;
import jdk.graal.compiler.nodes.calc.UnaryArithmeticNode;
import jdk.graal.compiler.nodes.calc.UnaryNode;
import jdk.graal.compiler.nodes.extended.GuardingNode;
import jdk.graal.compiler.nodes.spi.ValueProxy;
import jdk.graal.compiler.phases.common.util.LoopUtility;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.TriState;

public class ConditionalEliminationUtil {

    public static final class Marks {

        final int infoElementOperations;
        final int conditions;

        public Marks(int infoElementOperations, int conditions) {
            this.infoElementOperations = infoElementOperations;
            this.conditions = conditions;
        }

        public int getInfoElementOperations() {
            return infoElementOperations;
        }

        public int getConditions() {
            return conditions;
        }
    }

    public static final class GuardedCondition {
        private final GuardingNode guard;
        private final LogicNode condition;
        private final boolean negated;

        public GuardedCondition(GuardingNode guard, LogicNode condition, boolean negated) {
            this.guard = guard;
            this.condition = condition;
            this.negated = negated;
        }

        public GuardingNode getGuard() {
            return guard;
        }

        public LogicNode getCondition() {
            return condition;
        }

        public boolean isNegated() {
            return negated;
        }
    }

    @FunctionalInterface
    public interface GuardRewirer {
        /**
         * Called if the condition could be proven to have a constant value ({@code result}) under
         * {@code guard}.
         *
         * @param guard the guard whose result is proven
         * @param result the known result of the guard
         * @param newInput new input to pi nodes depending on the new guard
         * @return whether the transformation could be applied
         */
        boolean rewire(GuardingNode guard, boolean result, Stamp guardedValueStamp, ValueNode newInput);
    }

    /**
     * Checks for safe nodes when moving pending tests up.
     */
    public static class InputFilter extends Node.EdgeVisitor {
        boolean ok;
        private ValueNode value;

        InputFilter(ValueNode value) {
            this.value = value;
            this.ok = true;
        }

        @Override
        public Node apply(Node node, Node curNode) {
            if (!ok) {
                // Abort the recursion
                return curNode;
            }
            if (!(curNode instanceof ValueNode)) {
                ok = false;
                return curNode;
            }
            ValueNode curValue = (ValueNode) curNode;
            if (curValue.isConstant() || curValue == value || curValue instanceof ParameterNode) {
                return curNode;
            }
            if (curValue instanceof BinaryNode || curValue instanceof UnaryNode) {
                curValue.applyInputs(this);
            } else {
                ok = false;
            }
            return curNode;
        }
    }

    public static final class InfoElement {
        private final Stamp stamp;
        private final GuardingNode guard;
        private final ValueNode proxifiedInput;
        private final InfoElement parent;

        public InfoElement(Stamp stamp, GuardingNode guard, ValueNode proxifiedInput, InfoElement parent) {
            this.stamp = stamp;
            this.guard = guard;
            this.proxifiedInput = proxifiedInput;
            this.parent = parent;
        }

        public InfoElement getParent() {
            return parent;
        }

        public Stamp getStamp() {
            return stamp;
        }

        public GuardingNode getGuard() {
            return guard;
        }

        public ValueNode getProxifiedInput() {
            return proxifiedInput;
        }

        @Override
        public String toString() {
            return stamp + " -> " + guard;
        }
    }

    /**
     * Get the stamp that may be used for the value for which we are registering the condition. We
     * may directly use the stamp here without restriction, because any later lookup of the
     * registered info elements is in the same chain of pi nodes.
     */
    public static Stamp getSafeStamp(ValueNode x) {
        return x.stamp(NodeView.DEFAULT);
    }

    /**
     * We can only use the stamp of a second value involved in the condition if we are sure that we
     * are not implicitly creating a dependency on a guard that is responsible for that stamp.
     * Constants and values after fixed reads keep the existing behavior. For other values before
     * fixed reads, use a bounded value-input search to prove that the stamp is produced without a
     * guard, anchor, Pi, or guarded-value dependency in the inspected expression.
     */
    public static Stamp getOtherSafeStamp(ValueNode x, SafeStampInputSearch search) {
        GraalError.guarantee(search != null, "safe-stamp input search is required");
        Stamp stamp = x.stamp(NodeView.DEFAULT);
        if (x.isConstant() || x.graph().isAfterStage(StageFlag.FIXED_READS)) {
            return stamp;
        }
        Stamp unrestrictedStamp = stamp.unrestricted();
        if (stamp.equals(unrestrictedStamp)) {
            return stamp;
        } else if (hasControlFlowIndependentStamp(x, search)) {
            return stamp;
        }
        return unrestrictedStamp;
    }

    /**
     * Proves that a value's stamp can be consumed without also depending on hidden control flow. The
     * first pass records the bounded cone of value producers needed by the stamp. The second pass
     * validates those producers for explicit control dependencies. Keeping the passes separate makes
     * the bounded search about graph shape first, then about the safety property that justifies using
     * the stamp.
     */
    private static boolean hasControlFlowIndependentStamp(ValueNode x, SafeStampInputSearch search) {
        if (!collectStampProducers(x, search)) {
            return false;
        }
        NodeStack stampProducers = search.stampProducers();
        for (int i = 0; i < stampProducers.size(); i++) {
            ValueNode stampProducer = (ValueNode) stampProducers.get(i);
            if (hasControlFlowDependentStamp(stampProducer)) {
                return false;
            }
            for (Position position : stampProducer.inputPositions()) {
                Node input = position.get(stampProducer);
                if (input != null && isControlFlowDependentInput(position.getInputType())) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Collects the value producers whose stamps would have to be trusted for {@code x}'s stamp. The
     * search stops at constants and parameters, whose stamps do not depend on dominating guards, and
     * at nodes whose current stamp can be reproduced from dependency-free input stamps. Any remaining
     * value dependency must fit within the fixed search depth.
     */
    private static boolean collectStampProducers(ValueNode x, SafeStampInputSearch search) {
        search.start(x);

        while (search.hasNext()) {
            ValueNode stampProducer = search.next();
            if (hasDependencyFreeStamp(stampProducer)) {
                continue;
            } else if (search.atMaxDepth()) {
                return false;
            }

            if (!addStampProducerInputs(stampProducer, search)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns true for producers whose current stamp does not require trusting another value input
     * or hidden control-flow dependency.
     */
    private static boolean hasDependencyFreeStamp(ValueNode stampProducer) {
        return stampProducer.isConstant() || stampProducer instanceof ParameterNode || hasLocallyDerivedStamp(stampProducer, stampProducer.stamp(NodeView.DEFAULT));
    }

    /**
     * Adds the value inputs whose stamps may contribute to {@code stampProducer}'s stamp. Guard and
     * anchor inputs reject the producer immediately because they encode the hidden control-flow
     * dependencies this search is trying to avoid.
     */
    private static boolean addStampProducerInputs(ValueNode stampProducer, SafeStampInputSearch search) {
        for (Position position : stampProducer.inputPositions()) {
            Node input = position.get(stampProducer);
            if (input == null) {
                continue;
            } else if (isControlFlowDependentInput(position.getInputType())) {
                return false;
            } else if (position.getInputType() != InputType.Value) {
                // Non-value edges cannot add stamp producers. Guard and anchor edges were already
                // rejected above because those non-value edges encode hidden control dependencies.
                continue;
            } else if (input instanceof ValueNode valueInput) {
                if (!search.addInput(valueInput)) {
                    return false;
                }
            } else {
                return false;
            }
        }
        return true;
    }

    private static boolean isControlFlowDependentInput(InputType inputType) {
        return inputType == InputType.Guard || inputType == InputType.Anchor;
    }

    /**
     * Checks whether {@code stamp} can be reproduced from this node's operation after removing
     * facts proved by guards from its inputs.
     *
     * If this returns true, conditional elimination may use {@code stamp} as an "other operand"
     * stamp without recording dependencies on any input guards.
     */
    private static boolean hasLocallyDerivedStamp(ValueNode x, Stamp stamp) {
        Stamp operationStamp = null;
        if (x instanceof IntegerConvertNode<?> convert) {
            operationStamp = convert.foldStamp(StampFactory.forInteger(convert.getInputBits()));
        } else if (x instanceof UnaryArithmeticNode<?> unary) {
            operationStamp = unary.foldStamp(stampWithOnlyConstantFacts(unary.getValue()));
        } else if (x instanceof BinaryNode binary) {
            operationStamp = binary.foldStamp(stampWithOnlyConstantFacts(binary.getX()), stampWithOnlyConstantFacts(binary.getY()));
        } else if (x instanceof TernaryNode ternary) {
            operationStamp = ternary.foldStamp(stampWithOnlyConstantFacts(ternary.getX()), stampWithOnlyConstantFacts(ternary.getY()),
                            stampWithOnlyConstantFacts(ternary.getZ()));
        }
        return operationStamp != null && stampIsNoMorePreciseThan(stamp, operationStamp);
    }

    /**
     * Keeps only input facts that are dependency-free by construction. Non-constant values use their
     * unrestricted stamp because a derived value may carry guard-dependent facts even when the value
     * itself is not a {@link PiNode}, {@link GuardedValueNode}, or guarded {@link ValueProxy}.
     */
    private static Stamp stampWithOnlyConstantFacts(ValueNode value) {
        Stamp stamp = value.stamp(NodeView.DEFAULT);
        return value.isConstant() ? stamp : stamp.unrestricted();
    }

    /**
     * Returns true when {@code stamp} is equal to or less precise than {@code dependencyFreeStamp}.
     * A more precise stamp would mean that the value carries some fact the local operation did not
     * produce by itself.
     */
    private static boolean stampIsNoMorePreciseThan(Stamp stamp, Stamp dependencyFreeStamp) {
        return dependencyFreeStamp.join(stamp).equals(dependencyFreeStamp);
    }

    /**
     * These node shapes attach a value stamp to a dominating guard. Consuming their stamp as the
     * "other" side of a proof would require the guard to stay in the rewritten dependency chain.
     */
    private static boolean hasControlFlowDependentStamp(ValueNode x) {
        return x instanceof GuardedValueNode || x instanceof PiNode || (x instanceof ValueProxy valueProxy && valueProxy.getGuard() != null);
    }

    @FunctionalInterface
    public interface InfoElementProvider {
        InfoElement infoElements(ValueNode value);

        default InfoElement nextElement(InfoElement current) {
            InfoElement parent = current.getParent();
            if (parent != null) {
                return parent;
            } else {
                ValueNode proxifiedInput = current.getProxifiedInput();
                if (proxifiedInput instanceof PiNode) {
                    PiNode piNode = (PiNode) proxifiedInput;
                    return infoElements(piNode.getOriginalNode());
                }
            }
            return null;
        }
    }

    public static Pair<InfoElement, Stamp> recursiveFoldStamp(InfoElementProvider infoElementProvider, Node node) {
        if (node instanceof UnaryNode) {
            UnaryNode unary = (UnaryNode) node;
            ValueNode value = unary.getValue();
            InfoElement infoElement = infoElementProvider.infoElements(value);
            while (infoElement != null) {
                Stamp result = unary.foldStamp(infoElement.getStamp());
                if (result != null) {
                    return Pair.create(infoElement, result);
                }
                infoElement = infoElementProvider.nextElement(infoElement);
            }
        } else if (node instanceof BinaryNode) {
            BinaryNode binary = (BinaryNode) node;
            ValueNode y = binary.getY();
            ValueNode x = binary.getX();
            if (y.isConstant()) {
                InfoElement infoElement = infoElementProvider.infoElements(x);
                while (infoElement != null) {
                    Stamp result = binary.foldStamp(infoElement.getStamp(), y.stamp(NodeView.DEFAULT));
                    if (result != null) {
                        return Pair.create(infoElement, result);
                    }
                    infoElement = infoElementProvider.nextElement(infoElement);
                }
            }
        }
        return null;
    }

    /**
     * Recursively try to fold stamps within this expression using information from
     * {@link InfoElementProvider#infoElements(ValueNode)}. It's only safe to use constants and one
     * {@link InfoElement} otherwise more than one guard would be required.
     *
     * @param node
     * @return the pair of the @{link InfoElement} used and the stamp produced for the whole
     *         expression
     */
    public static Pair<InfoElement, Stamp> recursiveFoldStampFromInfo(InfoElementProvider infoElementProvider, Node node) {
        return recursiveFoldStamp(infoElementProvider, node);
    }

    /**
     * Checks whether {@code lower + lowerAddendOuter} is strictly below {@code upper}. Optional
     * constant addends are stripped from {@code lower} and {@code upper}; the proof always requires
     * both resulting bases to be the same value and then checks whether
     * {@code lowerAddend + lowerAddendOuter < upperAddend}.
     */
    private static boolean isStrictlyBelowAfterAdd(ValueNode lower, long lowerAddendOuter, ValueNode upper) {
        ValueNode lowerBase = lower;
        long lowerAddend = 0;
        if (lower instanceof AddNode add && add.getY().isJavaConstant() && add.getY().stamp(NodeView.DEFAULT).isIntegerStamp()) {
            lowerBase = add.getX();
            lowerAddend = add.getY().asJavaConstant().asLong();
        }

        ValueNode upperBase = upper;
        long upperAddend = 0;
        if (upper instanceof AddNode add && add.getY().isJavaConstant() && add.getY().stamp(NodeView.DEFAULT).isIntegerStamp()) {
            upperBase = add.getX();
            upperAddend = add.getY().asJavaConstant().asLong();
        }

        if (lowerAddendOuter < 0 || upperAddend > 0 || lowerBase != upperBase) {
            return false;
        }
        try {
            long lowerAddendSum = LoopUtility.addExact(((IntegerStamp) lower.stamp(NodeView.DEFAULT)).getBits(), lowerAddend, lowerAddendOuter);
            return lowerAddendSum < upperAddend;
        } catch (ArithmeticException e) {
            return false;
        }
    }

    /**
     * Proves a masked unsigned-below check after proving that the mask value is non-negative and
     * strictly below the upper bound. The basic pattern is:
     *
     * <pre>{@code
     * if (upper > 0) {
     *     int lower = index & (upper - 1);
     *     if (Integer.compareUnsigned(lower, upper) < 0) {
     *         inBounds();
     *     }
     * }
     * }</pre>
     *
     * This also handles constant addends on the masked lower value, the mask value, and the upper
     * value, as long as the mask and upper value have the same base and the resulting mask plus the
     * lower addend is still strictly below the upper value:
     *
     * <pre>{@code
     * if (base + upperAddend > 0) {
     *     int lower = (index & (base + maskAddend)) + lowerAddend;
     *     if (Integer.compareUnsigned(lower, base + upperAddend) < 0) {
     *         inBounds();
     *     }
     * }
     * }</pre>
     *
     * @param infoElementProvider provider for dominating stamp information
     * @param node the condition to prove
     * @param rewireGuardFunction callback used to fold the proved guard
     * @return {@code true} if {@code node} was proved and rewired
     */
    private static boolean tryProveMaskedBelow(InfoElementProvider infoElementProvider, LogicNode node, GuardRewirer rewireGuardFunction) {
        if (!(node instanceof IntegerBelowNode integerBelowNode)) {
            return false;
        }
        ValueNode upper = integerBelowNode.getY();
        ValueNode lower = integerBelowNode.getX();
        ValueNode lowerBase = lower;
        long lowerAddend = 0;
        if (lower instanceof AddNode add && add.getY().isJavaConstant() && add.getY().stamp(NodeView.DEFAULT).isIntegerStamp()) {
            lowerBase = add.getX();
            lowerAddend = add.getY().asJavaConstant().asLong();
        }
        if (!(lowerBase instanceof AndNode and)) {
            return false;
        }
        ValueNode mask = null;
        if (isStrictlyBelowAfterAdd(and.getX(), lowerAddend, upper)) {
            mask = and.getX();
        } else if (isStrictlyBelowAfterAdd(and.getY(), lowerAddend, upper)) {
            mask = and.getY();
        }
        if (mask == null) {
            return false;
        }
        Pair<InfoElement, Stamp> foldedMask = recursiveFoldStampFromInfo(infoElementProvider, mask);
        if (foldedMask != null && foldedMask.getRight() instanceof IntegerStamp integerStamp && integerStamp.lowerBound() >= 0) {
            return rewireGuards(foldedMask.getLeft().getGuard(), true, foldedMask.getLeft().getProxifiedInput(), foldedMask.getRight(), rewireGuardFunction);
        }
        return false;
    }

    public static boolean rewireGuards(GuardingNode guard, boolean result, ValueNode proxifiedInput, Stamp guardedValueStamp, GuardRewirer rewireGuardFunction) {
        return rewireGuardFunction.rewire(guard, result, guardedValueStamp, proxifiedInput);
    }

    @FunctionalInterface
    public interface GuardFolding {
        boolean foldGuard(DeoptimizingGuard thisGuard, ValueNode original, Stamp newStamp, GuardRewirer rewireGuardFunction);
    }

    /**
     * Gets the other-operand stamp for condition proofs. Only fixed {@code IfNode} folding may use
     * control-flow-dependent other-operand stamps: removing an {@code IfNode} proves an existing
     * branch condition, while guards and fixed guards can later become floating guard checks.
     */
    public static Stamp getOtherSafeStampForConditionProof(ValueNode x, boolean allowControlFlowDependentStamp, SafeStampInputSearch search) {
        return allowControlFlowDependentStamp ? x.stamp(NodeView.DEFAULT) : getOtherSafeStamp(x, search);
    }

    /**
     * Returns true if either successor carries branch-local guard usages that would be retargeted
     * when this {@link IfNode} is folded.
     */
    public static boolean ifSuccessorsHaveGuardUsages(IfNode node) {
        return node.trueSuccessor().hasUsagesOfType(InputType.Guard) || node.falseSuccessor().hasUsagesOfType(InputType.Guard);
    }

    /**
     * Returns true when any value input of {@code condition} has a stamp that cannot be used
     * without also preserving a hidden control-flow dependency.
     */
    public static boolean conditionHasUnsafeInputStamp(LogicNode condition, SafeStampInputSearch search) {
        for (Position position : condition.inputPositions()) {
            if (position.getInputType() == InputType.Value && position.get(condition) instanceof ValueNode value) {
                Stamp stamp = value.stamp(NodeView.DEFAULT);
                if (!getOtherSafeStamp(value, search).equals(stamp)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns true when the condition already folds using only the stamps currently on its direct
     * operands. Such stamps may come from input Pi nodes; recording this condition would let later
     * proofs depend on facts the condition itself would not preserve after canonicalization.
     */
    public static boolean conditionFoldsWithInputStamps(LogicNode condition) {
        if (condition instanceof UnaryOpLogicNode unaryLogicNode) {
            return unaryLogicNode.tryFold(unaryLogicNode.getValue().stamp(NodeView.DEFAULT)).isKnown();
        } else if (condition instanceof BinaryOpLogicNode binaryOpLogicNode) {
            return binaryOpLogicNode.tryFold(binaryOpLogicNode.getX().stamp(NodeView.DEFAULT), binaryOpLogicNode.getY().stamp(NodeView.DEFAULT)).isKnown();
        }
        return false;
    }

    public static boolean tryProveGuardCondition(InfoElementProvider infoElementProvider, ArrayDeque<GuardedCondition> conditions, GuardFolding guardFolding, DeoptimizingGuard thisGuard,
                    LogicNode node,
                    GuardRewirer rewireGuardFunction, boolean allowControlFlowDependentOtherStamp, SafeStampInputSearch search) {
        InfoElement infoElement = infoElementProvider.infoElements(node);
        while (infoElement != null) {
            Stamp stamp = infoElement.getStamp();
            JavaConstant constant = (JavaConstant) stamp.asConstant();
            if (constant != null) {
                // No proxified input and stamp required.
                return rewireGuards(infoElement.getGuard(), constant.asBoolean(), null, null, rewireGuardFunction);
            }
            infoElement = infoElementProvider.nextElement(infoElement);
        }

        for (GuardedCondition guardedCondition : conditions) {
            TriState result = guardedCondition.getCondition().implies(guardedCondition.isNegated(), node);
            if (result.isKnown()) {
                return rewireGuards(guardedCondition.getGuard(), result.toBoolean(), null, null, rewireGuardFunction);
            }
        }
        if (tryProveMaskedBelow(infoElementProvider, node, rewireGuardFunction)) {
            return true;
        }

        if (node instanceof UnaryOpLogicNode) {
            UnaryOpLogicNode unaryLogicNode = (UnaryOpLogicNode) node;
            ValueNode value = unaryLogicNode.getValue();
            infoElement = infoElementProvider.infoElements(value);
            while (infoElement != null) {
                Stamp stamp = infoElement.getStamp();
                TriState result = unaryLogicNode.tryFold(stamp);
                if (result.isKnown()) {
                    return rewireGuards(infoElement.getGuard(), result.toBoolean(), infoElement.getProxifiedInput(), infoElement.getStamp(), rewireGuardFunction);
                }
                infoElement = infoElementProvider.nextElement(infoElement);
            }
            Pair<InfoElement, Stamp> foldResult = recursiveFoldStampFromInfo(infoElementProvider, value);
            if (foldResult != null) {
                TriState result = unaryLogicNode.tryFold(foldResult.getRight());
                if (result.isKnown()) {
                    return rewireGuards(foldResult.getLeft().getGuard(), result.toBoolean(), foldResult.getLeft().getProxifiedInput(), foldResult.getRight(), rewireGuardFunction);
                }
            }
            if (thisGuard != null && guardFolding != null) {
                Stamp newStamp = unaryLogicNode.getSucceedingStampForValue(thisGuard.isNegated());
                if (newStamp != null && guardFolding.foldGuard(thisGuard, value, newStamp, rewireGuardFunction)) {
                    return true;
                }

            }
        } else if (node instanceof BinaryOpLogicNode) {
            BinaryOpLogicNode binaryOpLogicNode = (BinaryOpLogicNode) node;
            ValueNode x = binaryOpLogicNode.getX();
            ValueNode y = binaryOpLogicNode.getY();
            infoElement = infoElementProvider.infoElements(x);
            while (infoElement != null) {
                TriState result = binaryOpLogicNode.tryFold(infoElement.getStamp(), getOtherSafeStampForConditionProof(y, allowControlFlowDependentOtherStamp, search));
                if (result.isKnown()) {
                    return rewireGuards(infoElement.getGuard(), result.toBoolean(), infoElement.getProxifiedInput(), infoElement.getStamp(), rewireGuardFunction);
                }
                infoElement = infoElementProvider.nextElement(infoElement);
            }

            if (y.isConstant()) {
                Pair<InfoElement, Stamp> foldResult = recursiveFoldStampFromInfo(infoElementProvider, x);
                if (foldResult != null) {
                    TriState result = binaryOpLogicNode.tryFold(foldResult.getRight(), y.stamp(NodeView.DEFAULT));
                    if (result.isKnown()) {
                        return rewireGuards(foldResult.getLeft().getGuard(), result.toBoolean(), foldResult.getLeft().getProxifiedInput(), foldResult.getRight(), rewireGuardFunction);
                    }
                }
            } else {
                infoElement = infoElementProvider.infoElements(y);
                while (infoElement != null) {
                    TriState result = binaryOpLogicNode.tryFold(getOtherSafeStampForConditionProof(x, allowControlFlowDependentOtherStamp, search), infoElement.getStamp());
                    if (result.isKnown()) {
                        return rewireGuards(infoElement.getGuard(), result.toBoolean(), infoElement.getProxifiedInput(), infoElement.getStamp(), rewireGuardFunction);
                    }
                    infoElement = infoElementProvider.nextElement(infoElement);
                }
            }

            /*
             * For complex expressions involving constants, see if it's possible to fold the tests
             * by using stamps one level up in the expression. For instance, (x + n < y) might fold
             * if something is known about x and all other values are constants. The reason for the
             * constant restriction is that if more than 1 real value is involved the code might
             * need to adopt multiple guards to have proper dependences.
             */
            if (x instanceof BinaryArithmeticNode<?> && y.isConstant()) {
                BinaryArithmeticNode<?> binary = (BinaryArithmeticNode<?>) x;
                if (binary.getY().isConstant()) {
                    infoElement = infoElementProvider.infoElements(binary.getX());
                    while (infoElement != null) {
                        Stamp newStampX = binary.foldStamp(infoElement.getStamp(), binary.getY().stamp(NodeView.DEFAULT));
                        TriState result = binaryOpLogicNode.tryFold(newStampX, y.stamp(NodeView.DEFAULT));
                        if (result.isKnown()) {
                            return rewireGuards(infoElement.getGuard(), result.toBoolean(), infoElement.getProxifiedInput(), newStampX, rewireGuardFunction);
                        }
                        infoElement = infoElementProvider.nextElement(infoElement);
                    }
                }
            }

            if (thisGuard != null && guardFolding != null && binaryOpLogicNode instanceof IntegerEqualsNode && !thisGuard.isNegated()) {
                if (y.isConstant() && x instanceof AndNode) {
                    AndNode and = (AndNode) x;
                    if (and.getY() == y) {
                        /*
                         * This 'and' proves something about some of the bits in and.getX(). It's
                         * equivalent to or'ing in the mask value since those values are known to be
                         * set.
                         */
                        BinaryOp<Or> op = ArithmeticOpTable.forStamp(x.stamp(NodeView.DEFAULT)).getOr();
                        IntegerStamp newStampX = (IntegerStamp) op.foldStamp(getSafeStamp(and.getX()), getOtherSafeStamp(y, search));
                        if (guardFolding.foldGuard(thisGuard, and.getX(), newStampX, rewireGuardFunction)) {
                            return true;
                        }
                    }
                }
            }

            if (thisGuard != null && guardFolding != null) {
                if (!x.isConstant()) {
                    Stamp newStampX = binaryOpLogicNode.getSucceedingStampForX(thisGuard.isNegated(), getSafeStamp(x), getOtherSafeStamp(y, search));
                    if (newStampX != null && guardFolding.foldGuard(thisGuard, x, newStampX, rewireGuardFunction)) {
                        return true;
                    }
                }
                if (!y.isConstant()) {
                    Stamp newStampY = binaryOpLogicNode.getSucceedingStampForY(thisGuard.isNegated(), getOtherSafeStamp(x, search), getSafeStamp(y));
                    if (newStampY != null && guardFolding.foldGuard(thisGuard, y, newStampY, rewireGuardFunction)) {
                        return true;
                    }
                }
            }
        } else if (node instanceof ShortCircuitOrNode) {
            final ShortCircuitOrNode shortCircuitOrNode = (ShortCircuitOrNode) node;
            return tryProveGuardCondition(infoElementProvider, conditions, guardFolding, null, shortCircuitOrNode.getX(), (guard, result, guardedValueStamp, newInput) -> {
                if (result == !shortCircuitOrNode.isXNegated()) {
                    return rewireGuards(guard, true, newInput, guardedValueStamp, rewireGuardFunction);
                } else {
                    return tryProveGuardCondition(infoElementProvider, conditions, guardFolding, null, shortCircuitOrNode.getY(), (innerGuard, innerResult, innerGuardedValueStamp, innerNewInput) -> {
                        ValueNode proxifiedInput = newInput;
                        if (proxifiedInput == null) {
                            proxifiedInput = innerNewInput;
                        } else if (innerNewInput != null) {
                            if (innerNewInput != newInput) {
                                // Cannot canonicalize due to different proxied inputs.
                                return false;
                            }
                        }
                        // Can only canonicalize if the guards are equal.
                        if (innerGuard == guard) {
                            return rewireGuards(guard, innerResult ^ shortCircuitOrNode.isYNegated(), proxifiedInput, guardedValueStamp, rewireGuardFunction);
                        }
                        return false;
                    }, allowControlFlowDependentOtherStamp, search);
                }
            }, allowControlFlowDependentOtherStamp, search);
        }

        return false;
    }

}
