/*
 * Copyright (c) 2011, 2021, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.nodes;

import static jdk.graal.compiler.nodeinfo.InputType.Condition;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_1;

import java.util.function.Consumer;

import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.Node.IndirectInputChangedCanonicalization;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ProfileData.BranchProbabilityData;
import jdk.graal.compiler.nodes.calc.FloatingNode;

import jdk.vm.ci.meta.TriState;

@NodeInfo(allowedUsageTypes = {Condition}, size = SIZE_1)
public abstract class LogicNode extends FloatingNode implements IndirectInputChangedCanonicalization {

    public static final NodeClass<LogicNode> TYPE = NodeClass.create(LogicNode.class);

    public LogicNode(NodeClass<? extends LogicNode> c) {
        super(c, StampFactory.forVoid());
    }

    public static LogicNode and(LogicNode a, LogicNode b, BranchProbabilityData shortCircuitProbability) {
        return and(a, false, b, false, shortCircuitProbability);
    }

    public static LogicNode and(LogicNode a, boolean negateA, LogicNode b, boolean negateB, BranchProbabilityData shortCircuitProbability) {
        StructuredGraph graph = a.graph();
        LogicNode notAorNotB = graph.addOrUniqueWithInputs(ShortCircuitOrNode.create(a, !negateA, b, !negateB, shortCircuitProbability));
        return graph.addOrUniqueWithInputs(LogicNegationNode.create(notAorNotB));
    }

    public static LogicNode or(LogicNode a, LogicNode b, BranchProbabilityData shortCircuitProbability) {
        return or(a, false, b, false, shortCircuitProbability);
    }

    public static LogicNode or(LogicNode a, boolean negateA, LogicNode b, boolean negateB, BranchProbabilityData shortCircuitProbability) {
        return a.graph().unique(new ShortCircuitOrNode(a, negateA, b, negateB, shortCircuitProbability));
    }

    public final boolean isTautology() {
        if (this instanceof LogicConstantNode) {
            LogicConstantNode logicConstantNode = (LogicConstantNode) this;
            return logicConstantNode.getValue();
        }

        return false;
    }

    public final boolean isContradiction() {
        if (this instanceof LogicConstantNode) {
            LogicConstantNode logicConstantNode = (LogicConstantNode) this;
            return !logicConstantNode.getValue();
        }

        return false;
    }

    /**
     * Selects the candidate bucket when this node is the consequent of an implication search.
     * Every known {@link #implies} result must be covered by the antecedent's
     * {@link #forEachImplicationKey} keys after value-proxy and constant normalization. The default
     * supports identity proofs.
     */
    public ValueNode getImplicationKey() {
        return this;
    }

    /**
     * Emits candidate keys for implication searches. Whenever {@link #implies} returns a known
     * result, at least one key must match the consequent's {@link #getImplicationKey() query key}.
     * Matching skips value proxies and compares constants by value; extra matches are harmless.
     * The default covers the identity and negation proofs in this class.
     * For example, comparisons emit both operands so that a query keyed by its first operand also
     * finds proofs whose operands are swapped. Sharing a key does not itself establish a proof.
     *
     * Emit intermediate logic nodes too if changing their inputs can change the keys, as with a
     * logic negation's operand. The caller tracks this node's inputs and proxies traversed while
     * normalizing keys. Return {@code false} if the proof rules cannot be represented by these keys,
     * requesting a full search; any emitted keys may be retained conservatively.
     */
    public boolean forEachImplicationKey(Consumer<ValueNode> consumer) {
        consumer.accept(this);
        return true;
    }

    /**
     * Determines what this condition implies about the other.
     *
     * <ul>
     * <li>If negate(this, thisNegated) => other, returns {@link TriState#TRUE}</li>
     * <li>If negate(this, thisNegated) => !other, returns {@link TriState#FALSE}</li>
     * </ul>
     *
     * Overrides must not unwrap a {@link LogicNegationNode} consequent. They should delegate to
     * this method when their specialized proofs return {@link TriState#UNKNOWN}, so that this
     * method unwraps the consequent, redispatches the implication proof, and flips the result.
     * Nested negations are handled recursively.
     *
     * Overrides adding proof rules must also override {@link #forEachImplicationKey} if the inherited
     * keys do not cover those rules. Returning {@code false} there requests a full search.
     *
     * @param thisNegated whether this condition should be considered as false.
     * @param other the other condition.
     */
    public TriState implies(boolean thisNegated, LogicNode other) {
        if (this == other) {
            return TriState.get(!thisNegated);
        }
        if (other instanceof LogicNegationNode) {
            return flip(this.implies(thisNegated, ((LogicNegationNode) other).getValue()));
        }
        return TriState.UNKNOWN;
    }

    private static TriState flip(TriState triState) {
        return triState.isUnknown()
                        ? triState
                        : TriState.get(!triState.toBoolean());
    }
}
