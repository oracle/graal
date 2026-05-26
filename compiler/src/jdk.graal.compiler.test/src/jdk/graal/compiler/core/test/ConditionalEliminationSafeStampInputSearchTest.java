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

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.NodeInputList;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FixedGuardNode;
import jdk.graal.compiler.nodes.GuardedValueNode;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.FloatingNode;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.ConditionalEliminationUtil;
import jdk.graal.compiler.phases.common.SafeStampInputSearch;
import jdk.graal.compiler.phases.util.GraphOrder;

/**
 * Structural tests for the bounded safe-stamp input traversal used by
 * {@link ConditionalEliminationUtil#getOtherSafeStamp(ValueNode, SafeStampInputSearch)}.
 */
public class ConditionalEliminationSafeStampInputSearchTest extends ConditionalEliminationTestBase {

    @NodeInfo
    private static final class PlainNode extends Node {
        public static final NodeClass<PlainNode> TYPE = NodeClass.create(PlainNode.class);

        protected PlainNode() {
            super(TYPE);
        }
    }

    @NodeInfo
    private static final class PassthroughStampNode extends FloatingNode {
        public static final NodeClass<PassthroughStampNode> TYPE = NodeClass.create(PassthroughStampNode.class);

        @Input ValueNode value;

        protected PassthroughStampNode(Stamp stamp, ValueNode value) {
            super(TYPE, stamp);
            this.value = value;
        }
    }

    @NodeInfo
    private static final class ManyInputStampNode extends FloatingNode {
        public static final NodeClass<ManyInputStampNode> TYPE = NodeClass.create(ManyInputStampNode.class);

        @Input NodeInputList<ValueNode> values;

        protected ManyInputStampNode(Stamp stamp, ValueNode[] values) {
            super(TYPE, stamp);
            this.values = new NodeInputList<>(this, values);
        }
    }

    @NodeInfo
    private static final class AssociationInputStampNode extends FloatingNode {
        public static final NodeClass<AssociationInputStampNode> TYPE = NodeClass.create(AssociationInputStampNode.class);

        @Input(InputType.Association) Node association;

        protected AssociationInputStampNode(Stamp stamp, Node association) {
            super(TYPE, stamp);
            this.association = association;
        }
    }

    @NodeInfo
    private static final class OptionalInputStampNode extends FloatingNode {
        public static final NodeClass<OptionalInputStampNode> TYPE = NodeClass.create(OptionalInputStampNode.class);

        @OptionalInput ValueNode value;

        protected OptionalInputStampNode(Stamp stamp) {
            super(TYPE, stamp);
        }
    }

    @NodeInfo
    private static final class MalformedValueInputStampNode extends FloatingNode {
        public static final NodeClass<MalformedValueInputStampNode> TYPE = NodeClass.create(MalformedValueInputStampNode.class);

        @Input(InputType.Value) Node value;

        protected MalformedValueInputStampNode(Stamp stamp, Node value) {
            super(TYPE, stamp);
            this.value = value;
        }
    }

    public static int snippet(int index, int limit) {
        if (index < 0) {
            GraalDirectives.deoptimize();
        }
        if (index > 50) {
            GraalDirectives.deoptimize();
        }
        if (index < limit) {
            return index;
        }
        GraalDirectives.deoptimize();
        return -1;
    }

    public static Object objectGuardSnippet(Object value) {
        if (value == null) {
            GraalDirectives.deoptimize();
        }
        return value;
    }

    @Test
    public void testIteratorAdvancesByDepthAndClearsBetweenStarts() {
        StructuredGraph graph = prepareGraph("snippet");
        ValueNode index = graph.getParameter(0);
        ValueNode limit = graph.getParameter(1);
        ValueNode root = graph.addOrUniqueWithInputs(new PassthroughStampNode(IntegerStamp.create(32, 7, 9), index));
        SafeStampInputSearch search = new SafeStampInputSearch(graph);

        search.start(root);
        Assert.assertTrue(search.hasNext());
        Assert.assertFalse(search.atMaxDepth());
        Assert.assertSame(root, search.next());
        Assert.assertTrue(search.addInput(limit));
        Assert.assertTrue(search.hasNext());
        Assert.assertFalse(search.atMaxDepth());
        Assert.assertSame(limit, search.next());
        Assert.assertFalse(search.hasNext());

        search.start(index);
        Assert.assertTrue(search.hasNext());
        Assert.assertFalse(search.atMaxDepth());
        Assert.assertSame(index, search.next());
        Assert.assertFalse(search.hasNext());
    }

    @Test
    public void testOtherOperandInputSearchAbortsAfterMaxNodes() {
        StructuredGraph graph = prepareGraph("snippet");

        ValueNode[] inputs = new ValueNode[40];
        for (int i = 0; i < inputs.length; i++) {
            inputs[i] = ConstantNode.forInt(i, graph);
        }
        ValueNode manyInput = graph.addOrUniqueWithInputs(new ManyInputStampNode(IntegerStamp.create(32, 7, 9), inputs));
        SafeStampInputSearch search = new SafeStampInputSearch(graph);

        IntegerStamp safeStamp = (IntegerStamp) ConditionalEliminationUtil.getOtherSafeStamp(manyInput, search);
        Assert.assertEquals("The safe-stamp search must abort instead of trusting large producer cones.", Integer.MIN_VALUE, safeStamp.lowerBound());
        Assert.assertEquals("The safe-stamp search must abort instead of trusting large producer cones.", Integer.MAX_VALUE, safeStamp.upperBound());

        IntegerStamp repeatedSafeStamp = (IntegerStamp) ConditionalEliminationUtil.getOtherSafeStamp(manyInput, search);
        Assert.assertEquals("Reusing the search state must clear the visited-node count between queries.", safeStamp, repeatedSafeStamp);
        GraphOrder.assertSchedulableGraph(graph);
    }

    @Test
    public void testOtherOperandInputSearchAbortsAtDepthBound() {
        StructuredGraph graph = prepareGraph("snippet");
        ValueNode value = graph.getParameter(1);
        for (int i = 0; i < 4; i++) {
            value = graph.addOrUniqueWithInputs(new PassthroughStampNode(IntegerStamp.create(32, 7, 9), value));
        }

        IntegerStamp safeStamp = (IntegerStamp) ConditionalEliminationUtil.getOtherSafeStamp(value, new SafeStampInputSearch(graph));
        Assert.assertEquals("The safe-stamp search must not trust producer cones beyond its depth bound.", Integer.MIN_VALUE, safeStamp.lowerBound());
        Assert.assertEquals("The safe-stamp search must not trust producer cones beyond its depth bound.", Integer.MAX_VALUE, safeStamp.upperBound());
        GraphOrder.assertSchedulableGraph(graph);
    }

    @Test
    public void testOtherOperandInputSearchSkipsNullInputs() {
        StructuredGraph graph = prepareGraph("snippet");
        ValueNode withNullInput = graph.addOrUniqueWithInputs(new OptionalInputStampNode(IntegerStamp.create(32, 7, 9)));

        IntegerStamp safeStamp = (IntegerStamp) ConditionalEliminationUtil.getOtherSafeStamp(withNullInput, new SafeStampInputSearch(graph));
        Assert.assertEquals(7, safeStamp.lowerBound());
        Assert.assertEquals(9, safeStamp.upperBound());
    }

    @Test
    public void testOtherOperandInputSearchIgnoresNonValueNonControlInputs() {
        StructuredGraph graph = prepareGraph("snippet");
        Node association = graph.addWithoutUnique(new PlainNode());
        ValueNode associationOnly = graph.addOrUniqueWithInputs(new AssociationInputStampNode(IntegerStamp.create(32, 7, 9), association));

        IntegerStamp safeStamp = (IntegerStamp) ConditionalEliminationUtil.getOtherSafeStamp(associationOnly, new SafeStampInputSearch(graph));
        Assert.assertEquals("Association inputs do not contribute value stamps to the search.", 7, safeStamp.lowerBound());
        Assert.assertEquals("Association inputs do not contribute value stamps to the search.", 9, safeStamp.upperBound());
    }

    @Test
    public void testOtherOperandInputSearchRejectsValueInputThatIsNotValueNode() {
        StructuredGraph graph = prepareGraph("snippet");
        Node plainInput = graph.addWithoutUnique(new PlainNode());
        ValueNode malformed = graph.addOrUniqueWithInputs(new MalformedValueInputStampNode(IntegerStamp.create(32, 7, 9), plainInput));

        IntegerStamp safeStamp = (IntegerStamp) ConditionalEliminationUtil.getOtherSafeStamp(malformed, new SafeStampInputSearch(graph));
        Assert.assertEquals("Value-typed inputs must be ValueNodes to contribute stamp evidence.", Integer.MIN_VALUE, safeStamp.lowerBound());
        Assert.assertEquals("Value-typed inputs must be ValueNodes to contribute stamp evidence.", Integer.MAX_VALUE, safeStamp.upperBound());
    }

    @Test
    public void testOtherOperandGuardedValueStampIsNotSafe() {
        StructuredGraph graph = prepareGraph("snippet");

        ValueNode limit = graph.getParameter(1);
        FixedGuardNode piGuard = graph.getNodes().filter(FixedGuardNode.class).first();
        Assert.assertNotNull("Expected a guard to anchor the synthetic guarded value.", piGuard);

        ValueNode limitPi = graph.addOrUnique(PiNode.create(limit, IntegerStamp.create(32, 100, Integer.MAX_VALUE), piGuard.asNode()));
        ValueNode guardedLimit = graph.addOrUniqueWithInputs(GuardedValueNode.create(limitPi, piGuard));
        IntegerStamp safeStamp = (IntegerStamp) ConditionalEliminationUtil.getOtherSafeStamp(guardedLimit, new SafeStampInputSearch(graph));

        Assert.assertEquals("GuardedValueNode stamps must not be used as other-operand evidence.", Integer.MIN_VALUE, safeStamp.lowerBound());
        Assert.assertEquals("GuardedValueNode stamps must not be used as other-operand evidence.", Integer.MAX_VALUE, safeStamp.upperBound());
        GraphOrder.assertSchedulableGraph(graph);
    }

    @Test
    public void testOtherOperandObjectPiStampIsNotSafe() {
        StructuredGraph graph = prepareGraph("objectGuardSnippet");

        ValueNode value = graph.getParameter(0);
        FixedGuardNode piGuard = graph.getNodes().filter(FixedGuardNode.class).first();
        Assert.assertNotNull("Expected a guard to anchor the synthetic object PiNode.", piGuard);

        ValueNode objectPi = graph.addOrUnique(PiNode.create(value, StampFactory.objectNonNull(), piGuard.asNode()));
        Stamp safeStamp = ConditionalEliminationUtil.getOtherSafeStamp(objectPi, new SafeStampInputSearch(graph));

        Assert.assertEquals("Object Pi stamps must not be used as other-operand evidence.", StampFactory.object(), safeStamp);
        GraphOrder.assertSchedulableGraph(graph);
    }

    private StructuredGraph prepareGraph(String snippetName) {
        StructuredGraph graph = parseEager(snippetName, AllowAssumptions.YES);
        CanonicalizerPhase canonicalizer = createCanonicalizerPhase();
        prepareGraph(graph, canonicalizer, getProviders(), false);
        return graph;
    }
}
