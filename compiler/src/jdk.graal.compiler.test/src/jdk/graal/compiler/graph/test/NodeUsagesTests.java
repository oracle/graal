/*
 * Copyright (c) 2014, 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.graph.test;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_IGNORED;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_IGNORED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import jdk.graal.compiler.graph.test.matchers.NodeIterableContains;
import jdk.graal.compiler.graph.test.matchers.NodeIterableIsEmpty;
import jdk.graal.compiler.graph.GraalGraphError;
import jdk.graal.compiler.graph.Graph;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.NodeInputList;
import jdk.graal.compiler.graph.NodeSuccessorList;
import jdk.graal.compiler.graph.Position;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.options.OptionValues;
import org.junit.Assert;
import org.junit.Test;

public class NodeUsagesTests extends GraphTest {

    @NodeInfo(cycles = CYCLES_IGNORED, size = SIZE_IGNORED)
    static final class Def extends Node {
        public static final NodeClass<Def> TYPE = NodeClass.create(Def.class);

        protected Def() {
            super(TYPE);
        }
    }

    @NodeInfo(cycles = CYCLES_IGNORED, size = SIZE_IGNORED)
    static final class Use extends Node {
        public static final NodeClass<Use> TYPE = NodeClass.create(Use.class);
        @Input Def in0;
        @Input Def in1;
        @Input Def in2;

        protected Use(Def in0, Def in1, Def in2) {
            super(TYPE);
            this.in0 = in0;
            this.in1 = in1;
            this.in2 = in2;
        }

    }

    @Test
    public void testReplaceAtUsages() {
        OptionValues options = getOptions();
        Graph graph = new Graph(options, getDebug(options));
        Def def0 = graph.add(new Def());
        Def def1 = graph.add(new Def());
        Use use0 = graph.add(new Use(def0, null, null));
        Use use1 = graph.add(new Use(null, def0, null));
        Use use2 = graph.add(new Use(null, null, def0));

        assertEquals(3, def0.getUsageCount());
        assertThat(def0.usages(), NodeIterableContains.contains(use0));
        assertThat(def0.usages(), NodeIterableContains.contains(use1));
        assertThat(def0.usages(), NodeIterableContains.contains(use2));

        assertThat(def0.usages(), NodeIterableIsEmpty.isNotEmpty());
        assertThat(def1.usages(), NodeIterableIsEmpty.isEmpty());

        def0.replaceAtUsages(def1);

        assertThat(def0.usages(), NodeIterableIsEmpty.isEmpty());

        assertEquals(3, def1.getUsageCount());
        assertThat(def1.usages(), NodeIterableContains.contains(use0));
        assertThat(def1.usages(), NodeIterableContains.contains(use1));
        assertThat(def1.usages(), NodeIterableContains.contains(use2));

        assertThat(def1.usages(), NodeIterableIsEmpty.isNotEmpty());
    }

    @Test
    public void testReplaceAtUsagesWithPredicateAll() {
        OptionValues options = getOptions();
        Graph graph = new Graph(options, getDebug(options));
        Def def0 = graph.add(new Def());
        Def def1 = graph.add(new Def());
        Use use0 = graph.add(new Use(def0, null, null));
        Use use1 = graph.add(new Use(null, def0, null));
        Use use2 = graph.add(new Use(null, null, def0));

        assertEquals(3, def0.getUsageCount());
        assertThat(def0.usages(), NodeIterableContains.contains(use0));
        assertThat(def0.usages(), NodeIterableContains.contains(use1));
        assertThat(def0.usages(), NodeIterableContains.contains(use2));

        assertThat(def0.usages(), NodeIterableIsEmpty.isNotEmpty());
        assertThat(def1.usages(), NodeIterableIsEmpty.isEmpty());

        def0.replaceAtMatchingUsages(def1, u -> true);

        assertThat(def0.usages(), NodeIterableIsEmpty.isEmpty());

        assertEquals(3, def1.getUsageCount());
        assertThat(def1.usages(), NodeIterableContains.contains(use0));
        assertThat(def1.usages(), NodeIterableContains.contains(use1));
        assertThat(def1.usages(), NodeIterableContains.contains(use2));

        assertThat(def1.usages(), NodeIterableIsEmpty.isNotEmpty());
    }

    @Test
    public void testReplaceAtUsagesWithPredicateNone() {
        OptionValues options = getOptions();
        Graph graph = new Graph(options, getDebug(options));
        Def def0 = graph.add(new Def());
        Def def1 = graph.add(new Def());
        Use use0 = graph.add(new Use(def0, null, null));
        Use use1 = graph.add(new Use(null, def0, null));
        Use use2 = graph.add(new Use(null, null, def0));

        assertEquals(3, def0.getUsageCount());
        assertThat(def0.usages(), NodeIterableContains.contains(use0));
        assertThat(def0.usages(), NodeIterableContains.contains(use1));
        assertThat(def0.usages(), NodeIterableContains.contains(use2));

        assertThat(def0.usages(), NodeIterableIsEmpty.isNotEmpty());
        assertThat(def1.usages(), NodeIterableIsEmpty.isEmpty());

        def0.replaceAtMatchingUsages(def1, u -> false);

        assertThat(def1.usages(), NodeIterableIsEmpty.isEmpty());

        assertEquals(3, def0.getUsageCount());
        assertThat(def0.usages(), NodeIterableContains.contains(use0));
        assertThat(def0.usages(), NodeIterableContains.contains(use1));
        assertThat(def0.usages(), NodeIterableContains.contains(use2));

        assertThat(def0.usages(), NodeIterableIsEmpty.isNotEmpty());
    }

    @Test
    public void testReplaceAtUsagesWithPredicate1() {
        OptionValues options = getOptions();
        Graph graph = new Graph(options, getDebug(options));
        Def def0 = graph.add(new Def());
        Def def1 = graph.add(new Def());
        Use use0 = graph.add(new Use(def0, null, null));
        Use use1 = graph.add(new Use(null, def0, null));
        Use use2 = graph.add(new Use(null, null, def0));

        assertEquals(3, def0.getUsageCount());
        assertThat(def0.usages(), NodeIterableContains.contains(use0));
        assertThat(def0.usages(), NodeIterableContains.contains(use1));
        assertThat(def0.usages(), NodeIterableContains.contains(use2));

        assertThat(def0.usages(), NodeIterableIsEmpty.isNotEmpty());
        assertThat(def1.usages(), NodeIterableIsEmpty.isEmpty());

        def0.replaceAtMatchingUsages(def1, u -> u == use1);

        assertEquals(1, def1.getUsageCount());
        assertThat(def1.usages(), NodeIterableContains.contains(use1));

        assertThat(def1.usages(), NodeIterableIsEmpty.isNotEmpty());

        assertEquals(2, def0.getUsageCount());
        assertThat(def0.usages(), NodeIterableContains.contains(use0));
        assertThat(def0.usages(), NodeIterableContains.contains(use2));

        assertThat(def0.usages(), NodeIterableIsEmpty.isNotEmpty());
    }

    @Test
    public void testReplaceAtUsagesWithPredicate2() {
        OptionValues options = getOptions();
        Graph graph = new Graph(options, getDebug(options));
        Def def0 = graph.add(new Def());
        Def def1 = graph.add(new Def());
        Use use0 = graph.add(new Use(def0, null, null));
        Use use1 = graph.add(new Use(null, def0, null));
        Use use2 = graph.add(new Use(null, null, def0));

        assertEquals(3, def0.getUsageCount());
        assertThat(def0.usages(), NodeIterableContains.contains(use0));
        assertThat(def0.usages(), NodeIterableContains.contains(use1));
        assertThat(def0.usages(), NodeIterableContains.contains(use2));

        assertThat(def0.usages(), NodeIterableIsEmpty.isNotEmpty());
        assertThat(def1.usages(), NodeIterableIsEmpty.isEmpty());

        def0.replaceAtMatchingUsages(def1, u -> u == use2);

        assertEquals(1, def1.getUsageCount());
        assertThat(def1.usages(), NodeIterableContains.contains(use2));

        assertThat(def1.usages(), NodeIterableIsEmpty.isNotEmpty());

        assertEquals(2, def0.getUsageCount());
        assertThat(def0.usages(), NodeIterableContains.contains(use0));
        assertThat(def0.usages(), NodeIterableContains.contains(use1));

        assertThat(def0.usages(), NodeIterableIsEmpty.isNotEmpty());
    }

    @Test
    public void testReplaceAtUsagesWithPredicate0() {
        OptionValues options = getOptions();
        Graph graph = new Graph(options, getDebug(options));
        Def def0 = graph.add(new Def());
        Def def1 = graph.add(new Def());
        Use use0 = graph.add(new Use(def0, null, null));
        Use use1 = graph.add(new Use(null, def0, null));
        Use use2 = graph.add(new Use(null, null, def0));

        assertEquals(3, def0.getUsageCount());
        assertThat(def0.usages(), NodeIterableContains.contains(use0));
        assertThat(def0.usages(), NodeIterableContains.contains(use1));
        assertThat(def0.usages(), NodeIterableContains.contains(use2));

        assertThat(def0.usages(), NodeIterableIsEmpty.isNotEmpty());
        assertThat(def1.usages(), NodeIterableIsEmpty.isEmpty());

        def0.replaceAtMatchingUsages(def1, u -> u == use0);

        assertEquals(1, def1.getUsageCount());
        assertThat(def1.usages(), NodeIterableContains.contains(use0));

        assertThat(def1.usages(), NodeIterableIsEmpty.isNotEmpty());

        assertEquals(2, def0.getUsageCount());
        assertThat(def0.usages(), NodeIterableContains.contains(use1));
        assertThat(def0.usages(), NodeIterableContains.contains(use2));

        assertThat(def0.usages(), NodeIterableIsEmpty.isNotEmpty());
    }

    @Test
    public void testReplaceAtUsagesWithPredicate02() {
        OptionValues options = getOptions();
        Graph graph = new Graph(options, getDebug(options));
        Def def0 = graph.add(new Def());
        Def def1 = graph.add(new Def());
        Use use0 = graph.add(new Use(def0, null, null));
        Use use1 = graph.add(new Use(null, def0, null));
        Use use2 = graph.add(new Use(null, null, def0));

        assertEquals(3, def0.getUsageCount());
        assertThat(def0.usages(), NodeIterableContains.contains(use0));
        assertThat(def0.usages(), NodeIterableContains.contains(use1));
        assertThat(def0.usages(), NodeIterableContains.contains(use2));

        assertThat(def0.usages(), NodeIterableIsEmpty.isNotEmpty());
        assertThat(def1.usages(), NodeIterableIsEmpty.isEmpty());

        def0.replaceAtMatchingUsages(def1, u -> u != use1);

        assertEquals(1, def0.getUsageCount());
        assertThat(def0.usages(), NodeIterableContains.contains(use1));

        assertThat(def0.usages(), NodeIterableIsEmpty.isNotEmpty());

        assertEquals(2, def1.getUsageCount());
        assertThat(def1.usages(), NodeIterableContains.contains(use0));
        assertThat(def1.usages(), NodeIterableContains.contains(use2));

        assertThat(def1.usages(), NodeIterableIsEmpty.isNotEmpty());
    }

    @Test
    public void testReplaceAtUsagesWithPredicate023() {
        OptionValues options = getOptions();
        Graph graph = new Graph(options, getDebug(options));
        Def def0 = graph.add(new Def());
        Def def1 = graph.add(new Def());
        Use use0 = graph.add(new Use(def0, null, null));
        Use use1 = graph.add(new Use(null, def0, null));
        Use use2 = graph.add(new Use(null, null, def0));
        Use use3 = graph.add(new Use(null, null, def0));

        assertEquals(4, def0.getUsageCount());
        assertThat(def0.usages(), NodeIterableContains.contains(use0));
        assertThat(def0.usages(), NodeIterableContains.contains(use1));
        assertThat(def0.usages(), NodeIterableContains.contains(use2));
        assertThat(def0.usages(), NodeIterableContains.contains(use3));

        assertThat(def0.usages(), NodeIterableIsEmpty.isNotEmpty());
        assertThat(def1.usages(), NodeIterableIsEmpty.isEmpty());

        def0.replaceAtMatchingUsages(def1, u -> u != use1);

        assertEquals(1, def0.getUsageCount());
        assertThat(def0.usages(), NodeIterableContains.contains(use1));

        assertThat(def0.usages(), NodeIterableIsEmpty.isNotEmpty());

        assertEquals(3, def1.getUsageCount());
        assertThat(def1.usages(), NodeIterableContains.contains(use0));
        assertThat(def1.usages(), NodeIterableContains.contains(use2));
        assertThat(def1.usages(), NodeIterableContains.contains(use3));

        assertThat(def1.usages(), NodeIterableIsEmpty.isNotEmpty());
    }

    @Test
    public void testReplaceAtUsagesWithPredicate013() {
        OptionValues options = getOptions();
        Graph graph = new Graph(options, getDebug(options));
        Def def0 = graph.add(new Def());
        Def def1 = graph.add(new Def());
        Use use0 = graph.add(new Use(def0, null, null));
        Use use1 = graph.add(new Use(null, def0, null));
        Use use2 = graph.add(new Use(null, null, def0));
        Use use3 = graph.add(new Use(null, null, def0));

        assertEquals(4, def0.getUsageCount());
        assertThat(def0.usages(), NodeIterableContains.contains(use0));
        assertThat(def0.usages(), NodeIterableContains.contains(use1));
        assertThat(def0.usages(), NodeIterableContains.contains(use2));
        assertThat(def0.usages(), NodeIterableContains.contains(use3));

        assertThat(def0.usages(), NodeIterableIsEmpty.isNotEmpty());
        assertThat(def1.usages(), NodeIterableIsEmpty.isEmpty());

        def0.replaceAtMatchingUsages(def1, u -> u != use2);

        assertEquals(1, def0.getUsageCount());
        assertThat(def0.usages(), NodeIterableContains.contains(use2));

        assertThat(def0.usages(), NodeIterableIsEmpty.isNotEmpty());

        assertEquals(3, def1.getUsageCount());
        assertThat(def1.usages(), NodeIterableContains.contains(use0));
        assertThat(def1.usages(), NodeIterableContains.contains(use1));
        assertThat(def1.usages(), NodeIterableContains.contains(use3));

        assertThat(def1.usages(), NodeIterableIsEmpty.isNotEmpty());
    }

    @Test
    public void testReplaceAtUsagesWithPredicate203() {
        OptionValues options = getOptions();
        Graph graph = new Graph(options, getDebug(options));
        Def def0 = graph.add(new Def());
        Def def1 = graph.add(new Def());
        Use use0 = graph.add(new Use(def0, null, null));
        Use use1 = graph.add(new Use(null, def0, null));
        Use use2 = graph.add(new Use(null, null, def0));
        Use use3 = graph.add(new Use(null, null, def0));

        assertEquals(4, def0.getUsageCount());
        assertThat(def0.usages(), NodeIterableContains.contains(use0));
        assertThat(def0.usages(), NodeIterableContains.contains(use1));
        assertThat(def0.usages(), NodeIterableContains.contains(use2));
        assertThat(def0.usages(), NodeIterableContains.contains(use3));

        assertThat(def0.usages(), NodeIterableIsEmpty.isNotEmpty());
        assertThat(def1.usages(), NodeIterableIsEmpty.isEmpty());

        def0.replaceAtMatchingUsages(def1, u -> u == use2);

        assertEquals(1, def1.getUsageCount());
        assertThat(def1.usages(), NodeIterableContains.contains(use2));

        assertThat(def1.usages(), NodeIterableIsEmpty.isNotEmpty());

        assertEquals(3, def0.getUsageCount());
        assertThat(def0.usages(), NodeIterableContains.contains(use0));
        assertThat(def0.usages(), NodeIterableContains.contains(use1));
        assertThat(def0.usages(), NodeIterableContains.contains(use3));

        assertThat(def0.usages(), NodeIterableIsEmpty.isNotEmpty());
    }

    @Test
    public void testReplaceAtUsagesWithPredicate01() {
        OptionValues options = getOptions();
        Graph graph = new Graph(options, getDebug(options));
        Def def0 = graph.add(new Def());
        Def def1 = graph.add(new Def());
        Use use0 = graph.add(new Use(def0, null, null));
        Use use1 = graph.add(new Use(null, def0, null));
        Use use2 = graph.add(new Use(null, null, def0));

        assertEquals(3, def0.getUsageCount());
        assertThat(def0.usages(), NodeIterableContains.contains(use0));
        assertThat(def0.usages(), NodeIterableContains.contains(use1));
        assertThat(def0.usages(), NodeIterableContains.contains(use2));

        assertThat(def0.usages(), NodeIterableIsEmpty.isNotEmpty());
        assertThat(def1.usages(), NodeIterableIsEmpty.isEmpty());

        def0.replaceAtMatchingUsages(def1, u -> u != use2);

        assertEquals(1, def0.getUsageCount());
        assertThat(def0.usages(), NodeIterableContains.contains(use2));

        assertThat(def0.usages(), NodeIterableIsEmpty.isNotEmpty());

        assertEquals(2, def1.getUsageCount());
        assertThat(def1.usages(), NodeIterableContains.contains(use0));
        assertThat(def1.usages(), NodeIterableContains.contains(use1));

        assertThat(def1.usages(), NodeIterableIsEmpty.isNotEmpty());
    }

    @Test
    public void testReplaceAtUsagesWithPredicate12() {
        OptionValues options = getOptions();
        Graph graph = new Graph(options, getDebug(options));
        Def def0 = graph.add(new Def());
        Def def1 = graph.add(new Def());
        Use use0 = graph.add(new Use(def0, null, null));
        Use use1 = graph.add(new Use(null, def0, null));
        Use use2 = graph.add(new Use(null, null, def0));

        assertEquals(3, def0.getUsageCount());
        assertThat(def0.usages(), NodeIterableContains.contains(use0));
        assertThat(def0.usages(), NodeIterableContains.contains(use1));
        assertThat(def0.usages(), NodeIterableContains.contains(use2));

        assertThat(def0.usages(), NodeIterableIsEmpty.isNotEmpty());
        assertThat(def1.usages(), NodeIterableIsEmpty.isEmpty());

        def0.replaceAtMatchingUsages(def1, u -> u != use0);

        assertEquals(1, def0.getUsageCount());
        assertThat(def0.usages(), NodeIterableContains.contains(use0));

        assertThat(def0.usages(), NodeIterableIsEmpty.isNotEmpty());

        assertEquals(2, def1.getUsageCount());
        assertThat(def1.usages(), NodeIterableContains.contains(use1));
        assertThat(def1.usages(), NodeIterableContains.contains(use2));

        assertThat(def1.usages(), NodeIterableIsEmpty.isNotEmpty());
    }

    @Test
    public void testIterableToString() {
        OptionValues options = getOptions();
        Graph graph = new Graph(options, getDebug(options));
        Def def0 = graph.add(new Def());
        graph.add(new Def());
        graph.add(new Use(def0, null, null));
        graph.add(new Use(null, def0, null));
        graph.add(new Use(null, null, def0));

        String str = def0.usages().toString();
        assertTrue(str.contains("usages=[2|Use, 3|Use, 4|Use]"));
    }

    @Test
    public void testGraphVerify() {
        OptionValues options = getOptions();
        options = new OptionValues(options, Graph.Options.VerifyGraalGraphEdges, Boolean.TRUE, Graph.Options.VerifyGraalGraphs, Boolean.TRUE);

        Graph graph = new Graph(options, getDebug(options));
        TestVerifyNode a = graph.add(new TestVerifyNode(null));
        TestVerifyNode b = graph.add(new TestVerifyNode(a));
        graph.add(new TestVerifyNode(b));

        graph.verify();
        Object booleanTrue = Boolean.TRUE;

        for (Position p : a.successorPositions()) {
            Assert.assertEquals(p.hashCode(), p.hashCode());
            Assert.assertTrue(p.equals(p));

            Assert.assertFalse(p.equals(null));
            Assert.assertFalse(p.equals(booleanTrue));

            Assert.assertFalse(p.equals(new Position(null, p.getIndex(), p.getSubIndex())));
            Assert.assertFalse(p.equals(new Position(null, p.getIndex() + 1, p.getSubIndex())));
            Assert.assertFalse(p.equals(new Position(null, p.getIndex(), p.getSubIndex() + 1)));
        }
    }

    @Test
    public void testGraphVerifyFails() {
        try {
            OptionValues options = getOptions();
            options = new OptionValues(options, Graph.Options.VerifyGraalGraphEdges, Boolean.TRUE, Graph.Options.VerifyGraalGraphs, Boolean.TRUE);

            Graph graph = new Graph(options, getDebug(options));
            Def def0 = graph.add(new Def());
            graph.add(new Def());
            graph.add(new Use(def0, null, null));

            graph.verify();
            Assert.fail("GraalGraphError expected");
        } catch (GraalGraphError err) {
            assertTrue(err.getMessage().contains("invalid input of type"));
            assertTrue(new GraalGraphError(err).getMessage().contains("invalid input of type"));
        }
    }

    @NodeInfo(cycles = CYCLES_IGNORED, size = SIZE_IGNORED)
    static final class TestVerifyNode extends Node {
        public static final NodeClass<TestVerifyNode> TYPE = NodeClass.create(TestVerifyNode.class);

        @Input NodeInputList<TestVerifyNode> inputs;

        @Successor NodeSuccessorList<TestVerifyNode> successors;

        protected TestVerifyNode(TestVerifyNode successorNode, TestVerifyNode... inputNodes) {
            super(TYPE);
            this.inputs = new NodeInputList<>(this, inputNodes);
            this.successors = new NodeSuccessorList<>(this, new TestVerifyNode[]{successorNode});
        }
    }
}
