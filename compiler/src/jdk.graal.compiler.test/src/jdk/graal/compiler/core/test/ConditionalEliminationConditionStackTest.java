/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.core.common.type.ObjectStamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.common.type.StampPair;
import jdk.graal.compiler.core.common.type.TypeReference;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.LogicConstantNode;
import jdk.graal.compiler.nodes.LogicNegationNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.LoopExitNode;
import jdk.graal.compiler.nodes.ParameterNode;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.ProfileData.BranchProbabilityData;
import jdk.graal.compiler.nodes.ShortCircuitOrNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.ValueProxyNode;
import jdk.graal.compiler.nodes.calc.AndNode;
import jdk.graal.compiler.nodes.calc.FloatEqualsNode;
import jdk.graal.compiler.nodes.calc.FloatLessThanNode;
import jdk.graal.compiler.nodes.calc.IntegerBelowNode;
import jdk.graal.compiler.nodes.calc.IntegerEqualsNode;
import jdk.graal.compiler.nodes.calc.IntegerLessThanNode;
import jdk.graal.compiler.nodes.calc.IntegerTestNode;
import jdk.graal.compiler.nodes.calc.IsNullNode;
import jdk.graal.compiler.nodes.calc.ObjectEqualsNode;
import jdk.graal.compiler.nodes.calc.PointerEqualsNode;
import jdk.graal.compiler.nodes.extended.ObjectIsArrayNode;
import jdk.graal.compiler.nodes.java.InstanceOfNode;
import jdk.graal.compiler.phases.common.ConditionalEliminationConditionStack;
import jdk.graal.compiler.phases.common.ConditionalEliminationUtil;
import jdk.graal.compiler.phases.common.ConditionalEliminationUtil.GuardedCondition;
import jdk.graal.compiler.phases.common.ConditionalEliminationUtil.Implication;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.TriState;

public class ConditionalEliminationConditionStackTest extends GraalCompilerTest {
    @NodeInfo
    private static final class MissingImplicationKeysNode extends LogicNode {
        static final NodeClass<MissingImplicationKeysNode> TYPE = NodeClass.create(MissingImplicationKeysNode.class);

        protected MissingImplicationKeysNode() {
            super(TYPE);
        }

        @Override
        public TriState implies(boolean negated, LogicNode other) {
            // Deliberately violates the inherited identity-key contract to exercise verification.
            return TriState.TRUE;
        }
    }

    @NodeInfo
    private static final class FullScanImplicationNode extends LogicNode {
        static final NodeClass<FullScanImplicationNode> TYPE = NodeClass.create(FullScanImplicationNode.class);

        protected FullScanImplicationNode() {
            super(TYPE);
        }

        @Override
        public boolean forEachImplicationKey(Consumer<ValueNode> consumer) {
            return false;
        }

        @Override
        public TriState implies(boolean negated, LogicNode other) {
            return TriState.get(!negated);
        }
    }

    @NodeInfo
    private static final class OperandImplicationNode extends LogicNode {
        static final NodeClass<OperandImplicationNode> TYPE = NodeClass.create(OperandImplicationNode.class);
        @Input ValueNode value;
        int implicationCalls;

        protected OperandImplicationNode(ValueNode value) {
            super(TYPE);
            this.value = value;
        }

        @Override
        public ValueNode getImplicationKey() {
            return value;
        }

        @Override
        public boolean forEachImplicationKey(Consumer<ValueNode> consumer) {
            consumer.accept(value);
            return true;
        }

        @Override
        public TriState implies(boolean negated, LogicNode other) {
            implicationCalls++;
            if (other instanceof OperandImplicationNode operand && value == operand.value) {
                return TriState.get(!negated);
            }
            return super.implies(negated, other);
        }
    }

    @NodeInfo
    private static final class DerivedPointerEqualsNode extends PointerEqualsNode {
        static final NodeClass<DerivedPointerEqualsNode> TYPE = NodeClass.create(DerivedPointerEqualsNode.class);
        int implicationCalls;

        protected DerivedPointerEqualsNode(ValueNode x, ValueNode y) {
            super(TYPE, x, y);
        }

        @Override
        public TriState implies(boolean negated, LogicNode other) {
            implicationCalls++;
            return super.implies(negated, other);
        }
    }

    @NodeInfo
    private static final class DerivedInstanceOfNode extends InstanceOfNode {
        static final NodeClass<DerivedInstanceOfNode> TYPE = NodeClass.create(DerivedInstanceOfNode.class);

        protected DerivedInstanceOfNode(ObjectStamp checkedStamp, ValueNode value) {
            super(TYPE, checkedStamp, value, null, null);
        }
    }

    private StructuredGraph graph() {
        return new StructuredGraph.Builder(getInitialOptions(), getDebugContext()).build();
    }

    private static ParameterNode parameter(StructuredGraph graph, int index, JavaKind kind) {
        return graph.addWithoutUnique(new ParameterNode(index, StampPair.createSingle(StampFactory.forKind(kind))));
    }

    private static Implication findImplication(Iterable<GuardedCondition> conditions, LogicNode query) {
        for (GuardedCondition condition : conditions) {
            TriState result = condition.getCondition().implies(condition.isNegated(), query);
            if (result.isKnown()) {
                return new Implication(condition, result);
            }
        }
        return null;
    }

    private static GuardedCondition push(ConditionalEliminationConditionStack conditions, LogicNode node, boolean negated) {
        GuardedCondition condition = new GuardedCondition(null, node, negated);
        conditions.push(condition);
        return condition;
    }

    @Test
    public void newNodeSuppliesImplicationKeys() {
        StructuredGraph graph = graph();
        ValueNode value = parameter(graph, 0, JavaKind.Int);
        LogicNode query = graph.addWithoutUnique(new OperandImplicationNode(value));
        try (ConditionalEliminationConditionStack conditions = new ConditionalEliminationConditionStack(graph)) {
            GuardedCondition proof = push(conditions, graph.addWithoutUnique(new OperandImplicationNode(value)), false);
            for (int i = 1; i < 64; i++) {
                push(conditions, graph.addWithoutUnique(new OperandImplicationNode(parameter(graph, i, JavaKind.Int))), false);
            }
            Assert.assertSame(proof, conditions.findImplication(query).condition());
        }
    }

    @Test
    public void identityOnlyNodesInheritImplicationKeys() {
        StructuredGraph graph = graph();
        LogicNode query = graph.addWithoutUnique(LogicConstantNode.tautology());
        try (ConditionalEliminationConditionStack conditions = new ConditionalEliminationConditionStack(graph)) {
            GuardedCondition proof = push(conditions, query, false);
            for (int i = 1; i < 64; i++) {
                push(conditions, graph.addWithoutUnique(LogicConstantNode.tautology()), false);
            }
            Assert.assertSame(proof, conditions.findImplication(query).condition());
        }
    }

    @Test
    public void subclassesInheritImplicationKeys() {
        StructuredGraph graph = graph();
        ValueNode x = parameter(graph, 0, JavaKind.Object);
        ValueNode y = parameter(graph, 1, JavaKind.Object);
        ObjectStamp checkedStamp = StampFactory.objectNonNull(TypeReference.createWithoutAssumptions(getMetaAccess().lookupJavaType(Integer.class)));
        LogicNode[] nodes = {
                        graph.addWithoutUnique(new DerivedPointerEqualsNode(x, y)),
                        graph.addWithoutUnique(new DerivedInstanceOfNode(checkedStamp, x))};
        LogicNode[] queries = {
                        graph.addWithoutUnique(new PointerEqualsNode(x, y)),
                        graph.addWithoutUnique(new IsNullNode(x))};
        for (int n = 0; n < nodes.length; n++) {
            try (ConditionalEliminationConditionStack conditions = new ConditionalEliminationConditionStack(graph)) {
                GuardedCondition proof = push(conditions, nodes[n], false);
                for (int i = 2; i < 64; i++) {
                    push(conditions, graph.addWithoutUnique(new IsNullNode(parameter(graph, i, JavaKind.Object))), false);
                }
                Assert.assertSame(proof, conditions.findImplication(queries[n]).condition());
            }
        }
    }

    @Test
    public void assertionsVerifyImplicationKeys() {
        StructuredGraph graph = graph();
        LogicNode query = graph.addWithoutUnique(new IsNullNode(parameter(graph, 0, JavaKind.Object)));
        try (ConditionalEliminationConditionStack conditions = new ConditionalEliminationConditionStack(graph)) {
            push(conditions, graph.addWithoutUnique(new MissingImplicationKeysNode()), false);
            if (ConditionalEliminationConditionStack.class.desiredAssertionStatus()) {
                Assert.assertThrows(AssertionError.class, () -> conditions.findImplication(query));
            } else {
                Assert.assertNull(conditions.findImplication(query));
            }
        }
    }

    @Test
    public void copiedConditionsPreserveOrderAndClearReusesTheStack() {
        StructuredGraph graph = graph();
        ValueNode a = parameter(graph, 0, JavaKind.Object);
        ValueNode b = parameter(graph, 1, JavaKind.Object);
        PiNode pi = graph.addWithoutUnique(new PiNode(a, StampFactory.object()));
        LogicNode queryA = graph.addWithoutUnique(new IsNullNode(a));
        LogicNode queryB = graph.addWithoutUnique(new IsNullNode(b));
        try (ConditionalEliminationConditionStack source = new ConditionalEliminationConditionStack(graph);
                        ConditionalEliminationConditionStack copy = new ConditionalEliminationConditionStack(graph)) {
            GuardedCondition older = push(source, graph.addWithoutUnique(new IsNullNode(pi)), false);
            GuardedCondition newer = push(source, queryB, true);
            pi.setOriginalNode(b);
            copy.pushAll(source);
            Assert.assertSame(newer, copy.findImplication(queryB).condition());
            Assert.assertSame(newer, copy.pop());
            Assert.assertSame(older, copy.findImplication(queryB).condition());
            copy.clear();
            Assert.assertEquals(0, copy.size());
            Assert.assertNull(copy.findImplication(queryB));

            pi.setOriginalNode(a);
            copy.pushAll(source);
            Assert.assertSame(older, copy.findImplication(queryA).condition());
            Assert.assertSame(newer, copy.findImplication(queryB).condition());
            Iterator<GuardedCondition> iterator = copy.iterator();
            Assert.assertSame(newer, iterator.next());
            Assert.assertSame(older, iterator.next());
            Assert.assertFalse(iterator.hasNext());
        }
    }

    @Test
    public void negationTracksChangesToDelegatedKeys() {
        StructuredGraph graph = graph();
        ValueNode x = parameter(graph, 0, JavaKind.Int);
        ValueNode y = parameter(graph, 1, JavaKind.Int);
        LogicNode condition = graph.addWithoutUnique(new OperandImplicationNode(x));
        LogicNode queryX = graph.addWithoutUnique(new OperandImplicationNode(x));
        LogicNode queryY = graph.addWithoutUnique(new OperandImplicationNode(y));
        try (ConditionalEliminationConditionStack conditions = new ConditionalEliminationConditionStack(graph)) {
            GuardedCondition proof = push(conditions, graph.addOrUniqueWithInputs(LogicNegationNode.create(condition)), false);
            Assert.assertSame(proof, conditions.findImplication(queryX).condition());
            Assert.assertEquals(TriState.FALSE, conditions.findImplication(queryX).result());
            condition.replaceFirstInput(x, y);
            Assert.assertNull(conditions.findImplication(queryX));
            Assert.assertSame(proof, conditions.findImplication(queryY).condition());
            Assert.assertEquals(TriState.FALSE, conditions.findImplication(queryY).result());
        }
    }

    /** Compare every pair, including negated antecedents and consequents, without identity shortcuts. */
    private static void assertMatchesLinearScan(StructuredGraph graph, List<LogicNode> nodes) {
        List<LogicNode> antecedents = new ArrayList<>();
        List<LogicNode> queries = new ArrayList<>();
        for (LogicNode node : nodes) {
            LogicNode copy = (LogicNode) node.copyWithInputs();
            LogicNode negation = graph.addOrUniqueWithInputs(LogicNegationNode.create(copy));
            antecedents.add(node);
            antecedents.add(graph.addOrUniqueWithInputs(LogicNegationNode.create(node)));
            queries.add(copy);
            queries.add(negation);
        }
        ArrayDeque<GuardedCondition> reference = new ArrayDeque<>();
        try (ConditionalEliminationConditionStack conditions = new ConditionalEliminationConditionStack(graph)) {
            for (LogicNode node : antecedents) {
                for (boolean negated : new boolean[]{false, true}) {
                    GuardedCondition condition = push(conditions, node, negated);
                    reference.push(condition);
                    for (LogicNode query : queries) {
                        Assert.assertEquals(findImplication(reference, query), conditions.findImplication(query));
                    }
                    reference.pop();
                    Assert.assertSame(condition, conditions.pop());
                }
            }
        }
    }

    @Test
    public void auditedComparisonFamiliesMatchLinearScan() {
        StructuredGraph graph = graph();
        List<LogicNode> nodes = new ArrayList<>();
        for (JavaKind kind : new JavaKind[]{JavaKind.Int, JavaKind.Long, JavaKind.Float, JavaKind.Double, JavaKind.Object}) {
            ValueNode x = parameter(graph, nodes.size(), kind);
            ValueNode y = parameter(graph, nodes.size() + 1, kind);
            ValueNode pi = graph.addWithoutUnique(new PiNode(x, StampFactory.forKind(kind)));
            for (ValueNode[] operands : new ValueNode[][]{{x, y}, {y, x}, {pi, y}}) {
                ValueNode a = operands[0];
                ValueNode b = operands[1];
                if (kind.isNumericInteger()) {
                    nodes.add(graph.addWithoutUnique(new IntegerEqualsNode(a, b)));
                    nodes.add(graph.addWithoutUnique(new IntegerLessThanNode(a, b)));
                    nodes.add(graph.addWithoutUnique(new IntegerBelowNode(a, b)));
                } else if (kind.isNumericFloat()) {
                    nodes.add(graph.addWithoutUnique(new FloatEqualsNode(a, b)));
                    nodes.add(graph.addWithoutUnique(new FloatLessThanNode(a, b, false)));
                    nodes.add(graph.addWithoutUnique(new FloatLessThanNode(a, b, true)));
                } else {
                    nodes.add(graph.addWithoutUnique(new ObjectEqualsNode(a, b)));
                    nodes.add(graph.addWithoutUnique(new PointerEqualsNode(a, b)));
                }
            }
        }
        ValueNode pointerX = graph.addWithoutUnique(new ParameterNode(100, StampPair.createSingle(StampFactory.pointer())));
        ValueNode pointerY = graph.addWithoutUnique(new ParameterNode(101, StampPair.createSingle(StampFactory.pointer())));
        nodes.add(graph.addWithoutUnique(new PointerEqualsNode(pointerX, pointerY)));
        nodes.add(graph.addWithoutUnique(new PointerEqualsNode(pointerY, pointerX)));
        assertMatchesLinearScan(graph, nodes);
    }

    @Test
    public void distinctEqualConstantsMatchLinearScan() {
        StructuredGraph graph = graph();
        ValueNode x = parameter(graph, 0, JavaKind.Int);
        ConstantNode c1 = graph.addWithoutUnique(ConstantNode.forInt(42));
        ConstantNode c2 = graph.addWithoutUnique(ConstantNode.forInt(42));
        Assert.assertNotSame(c1, c2);
        List<LogicNode> nodes = new ArrayList<>();
        for (ConstantNode c : new ConstantNode[]{c1, c2, ConstantNode.forInt(43, graph)}) {
            nodes.add(graph.addWithoutUnique(new IntegerEqualsNode(x, c)));
            nodes.add(graph.addWithoutUnique(new IntegerEqualsNode(c, x)));
            nodes.add(graph.addWithoutUnique(new IntegerLessThanNode(x, c)));
            nodes.add(graph.addWithoutUnique(new IntegerBelowNode(x, c)));
        }
        assertMatchesLinearScan(graph, nodes);
    }

    @Test
    public void unaryTypeAndProxyImplicationsMatchLinearScan() {
        StructuredGraph graph = graph();
        ValueNode x = parameter(graph, 0, JavaKind.Object);
        ValueNode y = parameter(graph, 1, JavaKind.Object);
        LoopExitNode exit = graph.add(new LoopExitNode(graph.add(new LoopBeginNode())));
        ValueNode proxy = graph.addWithoutUnique(new ValueProxyNode(x, exit));
        ValueNode pi = graph.addWithoutUnique(new PiNode(proxy, StampFactory.object()));
        List<LogicNode> nodes = new ArrayList<>();
        for (ValueNode value : new ValueNode[]{x, y, proxy, pi}) {
            nodes.add(graph.addWithoutUnique(new IsNullNode(value)));
            nodes.add(graph.addWithoutUnique(ObjectIsArrayNode.create(value)));
            for (Class<?> type : new Class<?>[]{Number.class, Integer.class, String.class}) {
                TypeReference reference = TypeReference.createWithoutAssumptions(getMetaAccess().lookupJavaType(type));
                nodes.add(graph.addOrUniqueWithInputs(InstanceOfNode.create(reference, value)));
                nodes.add(graph.addOrUniqueWithInputs(InstanceOfNode.createAllowNull(reference, value, null, null)));
            }
        }
        assertMatchesLinearScan(graph, nodes);
    }

    @Test
    public void proxyInputChangesUseFallbackAndRollback() {
        StructuredGraph graph = graph();
        ValueNode a = parameter(graph, 0, JavaKind.Object);
        ValueNode b = parameter(graph, 1, JavaKind.Object);
        LoopExitNode exit = graph.add(new LoopExitNode(graph.add(new LoopBeginNode())));
        ValueProxyNode proxy = graph.addWithoutUnique(new ValueProxyNode(a, exit));
        PiNode pi = graph.addWithoutUnique(new PiNode(proxy, StampFactory.object()));
        LogicNode queryA = graph.addWithoutUnique(new IsNullNode(a));
        LogicNode queryB = graph.addWithoutUnique(new IsNullNode(b));
        try (ConditionalEliminationConditionStack conditions = new ConditionalEliminationConditionStack(graph)) {
            GuardedCondition proof = push(conditions, graph.addWithoutUnique(new IsNullNode(pi)), false);
            Assert.assertSame(proof, conditions.findImplication(queryA).condition());
            proxy.replaceFirstInput(a, b);
            Assert.assertNull(conditions.findImplication(queryA));
            Assert.assertSame(proof, conditions.findImplication(queryB).condition());
            conditions.pop();
            proxy.replaceFirstInput(b, a);
            Assert.assertNull(conditions.findImplication(queryA));
        }
    }

    @Test
    public void deepSharedDependenciesCanBePoppedAndReused() {
        StructuredGraph graph = graph();
        ValueNode a = parameter(graph, 0, JavaKind.Object);
        ValueNode b = parameter(graph, 1, JavaKind.Object);
        PiNode root = graph.addWithoutUnique(new PiNode(a, StampFactory.object()));
        ValueNode shared = root;
        for (int i = 0; i < 4096; i++) {
            shared = graph.addWithoutUnique(new PiNode(shared, StampFactory.object()));
        }
        ValueNode outer = graph.addWithoutUnique(new PiNode(shared, StampFactory.object()));
        LogicNode condition = graph.addWithoutUnique(new ObjectEqualsNode(shared, outer));
        LogicNode queryA = graph.addWithoutUnique(new ObjectEqualsNode(a, a));
        LogicNode queryB = graph.addWithoutUnique(new ObjectEqualsNode(b, b));
        try (ConditionalEliminationConditionStack conditions = new ConditionalEliminationConditionStack(graph)) {
            for (int round = 0; round < 2; round++) {
                // Both operands visit the shared chain. Each dependency must be registered once.
                GuardedCondition proof = push(conditions, condition, false);
                Assert.assertSame(proof, conditions.findImplication(queryA).condition());
                root.setOriginalNode(b);
                Assert.assertSame(proof, conditions.findImplication(queryB).condition());
                Assert.assertSame(proof, conditions.pop());
                root.setOriginalNode(a);
                Assert.assertNull(conditions.findImplication(queryA));
            }
        }
    }

    @Test
    public void iterationCannotBypassStackMutations() {
        StructuredGraph graph = graph();
        LogicNode query = graph.addWithoutUnique(new IsNullNode(parameter(graph, 0, JavaKind.Object)));
        try (ConditionalEliminationConditionStack conditions = new ConditionalEliminationConditionStack(graph)) {
            GuardedCondition proof = push(conditions, query, false);
            Iterator<GuardedCondition> iterator = conditions.iterator();
            Assert.assertSame(proof, iterator.next());
            Assert.assertThrows(UnsupportedOperationException.class, iterator::remove);
            Assert.assertEquals(1, conditions.size());
            Assert.assertSame(proof, conditions.findImplication(query).condition());
            Assert.assertSame(proof, conditions.pop());
        }
    }

    @Test
    public void independentQueriesDoNotWalkTheStack() {
        StructuredGraph graph = graph();
        int n = 2000;
        List<OperandImplicationNode> nodes = new ArrayList<>();
        try (ConditionalEliminationConditionStack conditions = new ConditionalEliminationConditionStack(graph)) {
            for (int i = 0; i < n; i++) {
                OperandImplicationNode query = graph.addWithoutUnique(new OperandImplicationNode(parameter(graph, i, JavaKind.Int)));
                nodes.add(query);
                Assert.assertNull(conditions.findImplication(query));
                push(conditions, query, false);
            }
        }
        long comparisons = nodes.stream().mapToLong(node -> node.implicationCalls).sum();
        // Only the assertion's full-scan oracle visits unrelated conditions.
        Assert.assertEquals(ConditionalEliminationConditionStack.class.desiredAssertionStatus() ? (long) n * (n - 1) / 2 : 0, comparisons);
    }

    @Test
    public void newestProofAndRollbackAcrossBuckets() {
        StructuredGraph graph = graph();
        LogicNode query = graph.addOrUniqueWithInputs(new IsNullNode(parameter(graph, 0, JavaKind.Object)));
        try (ConditionalEliminationConditionStack conditions = new ConditionalEliminationConditionStack(graph)) {
            GuardedCondition old = push(conditions, query, false);
            GuardedCondition fallback = push(conditions, graph.addWithoutUnique(new FullScanImplicationNode()), true);
            GuardedCondition newest = push(conditions, LogicNegationNode.create(query), false);
            Assert.assertSame(newest, conditions.findImplication(query).condition());
            Assert.assertEquals(TriState.FALSE, conditions.findImplication(query).result());
            Assert.assertSame(newest, conditions.pop());
            Assert.assertSame(fallback, conditions.findImplication(query).condition());
            conditions.pop();
            Assert.assertSame(old, conditions.findImplication(query).condition());
            conditions.pop();
            Assert.assertNull(conditions.findImplication(query));
        }
    }

    @Test
    public void nestedAndTestImplication() {
        StructuredGraph graph = graph();
        ValueNode x = parameter(graph, 0, JavaKind.Int);
        ValueNode y = parameter(graph, 1, JavaKind.Int);
        ValueNode and = graph.addOrUniqueWithInputs(new AndNode(x, y));
        LogicNode query = graph.addOrUniqueWithInputs(new IntegerEqualsNode(and, ConstantNode.forInt(0)));
        try (ConditionalEliminationConditionStack conditions = new ConditionalEliminationConditionStack(graph)) {
            GuardedCondition proof = push(conditions, graph.addOrUniqueWithInputs(new IntegerTestNode(y, x)), true);
            Assert.assertSame(proof, conditions.findImplication(query).condition());
            Assert.assertEquals(TriState.FALSE, conditions.findImplication(query).result());
        }
    }

    @Test
    public void piChainChangesPreserveOriginalOrder() {
        StructuredGraph graph = graph();
        ValueNode a = parameter(graph, 0, JavaKind.Object);
        ValueNode b = parameter(graph, 1, JavaKind.Object);
        PiNode inner = graph.addWithoutUnique(new PiNode(a, StampFactory.object()));
        PiNode outer = graph.addWithoutUnique(new PiNode(inner, StampFactory.object()));
        LogicNode queryA = graph.addOrUniqueWithInputs(new IsNullNode(a));
        LogicNode queryB = graph.addOrUniqueWithInputs(new IsNullNode(b));
        try (ConditionalEliminationConditionStack conditions = new ConditionalEliminationConditionStack(graph)) {
            GuardedCondition older = push(conditions, graph.addOrUniqueWithInputs(new IsNullNode(outer)), false);
            GuardedCondition newer = push(conditions, graph.addWithoutUnique(new FullScanImplicationNode()), true);
            inner.setOriginalNode(b);
            Assert.assertSame(newer, conditions.findImplication(queryB).condition());
            conditions.pop();
            Assert.assertNull(conditions.findImplication(queryA));
            Assert.assertSame(older, conditions.findImplication(queryB).condition());
            inner.setOriginalNode(a);
            conditions.pop(); // A changed popped entry must never reappear.
            Assert.assertNull(conditions.findImplication(queryA));
        }
    }

    @Test
    public void directInputReplacementUsesFallback() {
        StructuredGraph graph = graph();
        ValueNode a = parameter(graph, 0, JavaKind.Object);
        ValueNode b = parameter(graph, 1, JavaKind.Object);
        LogicNode condition = graph.addOrUniqueWithInputs(new IsNullNode(a));
        try (ConditionalEliminationConditionStack conditions = new ConditionalEliminationConditionStack(graph)) {
            GuardedCondition proof = push(conditions, condition, true);
            a.replaceAtUsages(b);
            LogicNode query = graph.addWithoutUnique(new IsNullNode(b));
            Assert.assertSame(proof, conditions.findImplication(query).condition());
            Assert.assertEquals(TriState.FALSE, conditions.findImplication(query).result());
        }
    }

    @Test
    public void changedConditionsRemainSearchableAcrossNewDependenciesAndSlotReuse() {
        StructuredGraph graph = graph();
        ValueNode a = parameter(graph, 0, JavaKind.Object);
        ValueNode b = parameter(graph, 1, JavaKind.Object);
        ValueNode c = parameter(graph, 2, JavaKind.Object);
        PiNode original = graph.addWithoutUnique(new PiNode(a, StampFactory.object()));
        PiNode replacement = graph.addWithoutUnique(new PiNode(b, StampFactory.object()));
        PiNode outer = graph.addWithoutUnique(new PiNode(original, StampFactory.object()));
        LogicNode queryA = graph.addWithoutUnique(new IsNullNode(a));
        LogicNode queryB = graph.addWithoutUnique(new IsNullNode(b));
        LogicNode queryC = graph.addWithoutUnique(new IsNullNode(c));
        try (ConditionalEliminationConditionStack conditions = new ConditionalEliminationConditionStack(graph)) {
            GuardedCondition older = push(conditions, graph.addWithoutUnique(new IsNullNode(outer)), true);
            outer.setOriginalNode(replacement);
            Assert.assertSame(older, conditions.findImplication(queryB).condition());
            // The new dependency is intentionally not tracked: fallback must survive its changes.
            replacement.setOriginalNode(c);
            GuardedCondition newer = push(conditions, graph.addWithoutUnique(new IsNullNode(c)), false);
            Assert.assertSame(newer, conditions.findImplication(queryC).condition());
            conditions.pop();
            Assert.assertSame(older, conditions.findImplication(queryC).condition());
            Assert.assertEquals(TriState.FALSE, conditions.findImplication(queryC).result());
            Assert.assertNull(conditions.findImplication(queryA));
            conditions.pop();
            push(conditions, graph.addWithoutUnique(new IsNullNode(a)), false);
            Assert.assertNull(conditions.findImplication(queryC));
            Assert.assertEquals(TriState.TRUE, conditions.findImplication(queryA).result());
        }
    }

    @Test
    public void changedMembershipsPopInRegistrationOrderWithoutDuplicateComparisons() {
        StructuredGraph graph = graph();
        ValueNode a = parameter(graph, 0, JavaKind.Object);
        ValueNode b = parameter(graph, 1, JavaKind.Object);
        PiNode shared = graph.addWithoutUnique(new PiNode(a, StampFactory.object()));
        LogicNode queryA = graph.addWithoutUnique(new PointerEqualsNode(a, a));
        LogicNode queryB = graph.addWithoutUnique(new PointerEqualsNode(b, b));
        List<DerivedPointerEqualsNode> nodes = new ArrayList<>();
        try (ConditionalEliminationConditionStack conditions = new ConditionalEliminationConditionStack(graph)) {
            for (int i = 0; i < 130; i++) {
                nodes.add(graph.addWithoutUnique(new DerivedPointerEqualsNode(shared, shared)));
            }
            GuardedCondition old = push(conditions, nodes.getFirst(), false);
            for (int i = 1; i < nodes.size(); i++) {
                push(conditions, nodes.get(i), false);
            }
            shared.setOriginalNode(b);
            // All entries occur in fallback and in their old operand bucket. Visit each once,
            // including positions across multiple bit-set words.
            Assert.assertNull(conditions.findImplication(queryA));
            shared.setOriginalNode(a);
            shared.setOriginalNode(b);
            while (conditions.size() > 1) {
                conditions.pop();
            }
            Assert.assertSame(old, conditions.findImplication(queryB).condition());
            conditions.pop();
            shared.setOriginalNode(a);
            Assert.assertNull(conditions.findImplication(queryA));
        }
        long comparisons = nodes.stream().mapToLong(node -> node.implicationCalls).sum();
        Assert.assertEquals(131 * (ConditionalEliminationConditionStack.class.desiredAssertionStatus() ? 2 : 1), comparisons);
    }

    @Test
    public void identityOnlyAndRecursiveOrRemainAvailable() {
        StructuredGraph graph = graph();
        LogicNode a = graph.addOrUniqueWithInputs(new IsNullNode(parameter(graph, 0, JavaKind.Object)));
        LogicNode b = graph.addOrUniqueWithInputs(new IsNullNode(parameter(graph, 1, JavaKind.Object)));
        LogicNode or = graph.addOrUniqueWithInputs(ShortCircuitOrNode.create(a, false, b, false, BranchProbabilityData.unknown()));
        try (ConditionalEliminationConditionStack conditions = new ConditionalEliminationConditionStack(graph)) {
            GuardedCondition identity = push(conditions, or, false);
            Assert.assertSame(identity, conditions.findImplication(LogicNegationNode.create(or)).condition());
            conditions.pop();
            push(conditions, a, true);
            push(conditions, b, true);
            int[] rewrites = {0};
            Assert.assertTrue(ConditionalEliminationUtil.tryProveGuardCondition(v -> null, conditions, null, null, or,
                            (guard, result, stamp, input) -> {
                                Assert.assertFalse(result);
                                rewrites[0]++;
                                return true;
                            }, false, null));
            Assert.assertEquals(1, rewrites[0]);
        }
    }

    @Test
    public void noCapOnCandidatesSharingAnOperand() {
        StructuredGraph graph = graph();
        ValueNode x = parameter(graph, 0, JavaKind.Int);
        LogicNode query = graph.addOrUniqueWithInputs(new IntegerEqualsNode(x, parameter(graph, 1, JavaKind.Int)));
        try (ConditionalEliminationConditionStack conditions = new ConditionalEliminationConditionStack(graph)) {
            GuardedCondition oldest = push(conditions, query, false);
            for (int i = 0; i < 2048; i++) {
                push(conditions, graph.addOrUniqueWithInputs(new IntegerEqualsNode(x, parameter(graph, i + 2, JavaKind.Int))), false);
            }
            // A distinct query node prevents an exact-identity shortcut from masking a cap.
            LogicNode distinctQuery = graph.addWithoutUnique(new IntegerEqualsNode(x, ((IntegerEqualsNode) query).getY()));
            Assert.assertNotSame(query, distinctQuery);
            Assert.assertSame(oldest, conditions.findImplication(distinctQuery).condition());
        }
    }

    @Test
    public void emptyStackCanBeReused() {
        StructuredGraph graph = graph();
        ValueNode a = parameter(graph, 0, JavaKind.Object);
        ValueNode b = parameter(graph, 1, JavaKind.Object);
        PiNode pi = graph.addWithoutUnique(new PiNode(a, StampFactory.object()));
        LogicNode condition = graph.addOrUniqueWithInputs(new IsNullNode(pi));
        LogicNode queryB = graph.addOrUniqueWithInputs(new IsNullNode(b));
        try (ConditionalEliminationConditionStack conditions = new ConditionalEliminationConditionStack(graph)) {
            for (int round = 0; round < 3; round++) {
                GuardedCondition survivor = push(conditions, condition, false);
                pi.setOriginalNode(b);
                for (int i = 0; i < 4; i++) {
                    push(conditions, graph.addOrUniqueWithInputs(new IsNullNode(parameter(graph, i + 10 + round * 4, JavaKind.Object))), true);
                }
                Assert.assertSame(survivor, conditions.findImplication(queryB).condition());
                while (conditions.size() > 1) {
                    conditions.pop();
                }
                Assert.assertSame(survivor, conditions.findImplication(queryB).condition());
                pi.setOriginalNode(a);
                Assert.assertNull(conditions.findImplication(queryB));
                Assert.assertSame(survivor, conditions.pop());
            }
        }
    }

    @Test
    public void randomizedMatchesAgreeWithFullStack() {
        StructuredGraph graph = graph();
        Random random = new Random(0xCE80900);
        List<LogicNode> queries = new ArrayList<>();
        ValueNode[] values = new ValueNode[16];
        for (int i = 0; i < values.length; i++) {
            values[i] = parameter(graph, i, JavaKind.Int);
        }
        for (int i = 0; i < 200; i++) {
            ValueNode x = i % 7 == 0 ? ConstantNode.forInt(i % 5, graph) : values[random.nextInt(values.length)];
            ValueNode y = i % 3 == 0 ? ConstantNode.forInt(i % 11, graph) : values[random.nextInt(values.length)];
            LogicNode query = switch (i % 4) {
                case 0 -> new IntegerEqualsNode(x, y);
                case 1 -> new IntegerLessThanNode(x, y);
                case 2 -> new IntegerBelowNode(x, y);
                default -> new IntegerTestNode(x, y);
            };
            queries.add(graph.addOrUniqueWithInputs(query));
        }
        ArrayDeque<GuardedCondition> reference = new ArrayDeque<>();
        try (ConditionalEliminationConditionStack conditions = new ConditionalEliminationConditionStack(graph)) {
            for (int i = 0; i < 2000; i++) {
                if (!reference.isEmpty() && random.nextInt(4) == 0) {
                    Assert.assertSame(reference.pop(), conditions.pop());
                } else {
                    GuardedCondition condition = push(conditions, queries.get(random.nextInt(queries.size())), random.nextBoolean());
                    reference.push(condition);
                }
                LogicNode query = queries.get(random.nextInt(queries.size()));
                Implication expected = findImplication(reference, query);
                Implication actual = conditions.findImplication(query);
                Assert.assertEquals(expected, actual);
            }
        }
    }
}
