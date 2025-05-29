/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.profdiff.test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.graalvm.collections.EconomicMap;
import jdk.graal.compiler.nodes.OptimizationLogImpl;
import org.graalvm.profdiff.core.OptimizationContextTree;
import org.graalvm.profdiff.core.OptimizationContextTreeNode;
import org.graalvm.profdiff.core.OptionValues;
import org.graalvm.profdiff.core.TreeNode;
import org.graalvm.profdiff.core.Writer;
import org.graalvm.profdiff.core.inlining.InliningTree;
import org.graalvm.profdiff.core.inlining.InliningTreeNode;
import org.graalvm.profdiff.core.inlining.ReceiverTypeProfile;
import org.graalvm.profdiff.core.optimization.Optimization;
import org.graalvm.profdiff.core.optimization.OptimizationPhase;
import org.graalvm.profdiff.core.optimization.OptimizationTree;
import org.graalvm.profdiff.core.optimization.OptimizationTreeNode;
import org.graalvm.profdiff.core.optimization.Position;
import org.graalvm.profdiff.diff.DeltaTree;
import org.graalvm.profdiff.diff.DeltaTreeNode;
import org.graalvm.profdiff.diff.DeltaTreeWriterVisitor;
import org.graalvm.profdiff.diff.EditScript;
import org.graalvm.profdiff.diff.InliningDeltaTreeWriterVisitor;
import org.graalvm.profdiff.diff.InliningTreeEditPolicy;
import org.graalvm.profdiff.diff.OptimizationContextTreeEditPolicy;
import org.graalvm.profdiff.diff.OptimizationContextTreeWriterVisitor;
import org.graalvm.profdiff.diff.OptimizationTreeEditPolicy;
import org.graalvm.profdiff.diff.TreeMatcher;
import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class DeltaTreeTest {
    private static class MockTreeNode extends TreeNode<MockTreeNode> {
        protected MockTreeNode() {
            super(null);
        }
    }

    private static final class MockInfoNode extends MockTreeNode {
        @Override
        public boolean isInfoNode() {
            return true;
        }
    }

    @Test
    public void editScriptConversion() {
        EditScript<MockTreeNode> editScript = new EditScript<>();
        editScript.delete(new MockTreeNode(), 1);
        editScript.relabel(new MockTreeNode(), new MockTreeNode(), 1);
        editScript.identity(new MockTreeNode(), new MockTreeNode(), 2);
        editScript.insert(new MockTreeNode(), 2);
        editScript.identity(new MockTreeNode(), new MockTreeNode(), 2);
        editScript.identity(new MockTreeNode(), new MockTreeNode(), 1);
        editScript.insert(new MockTreeNode(), 1);
        editScript.identity(new MockTreeNode(), new MockTreeNode(), 0);
        EditScript<MockTreeNode> convertedEditScript = DeltaTree.fromEditScript(editScript).asEditScript();
        assertEquals(editScript.getOperations().toList(), convertedEditScript.getOperations().toList());
    }

    @Test
    public void editOperationEqualsAndHash() {
        MockTreeNode left = new MockTreeNode();
        MockTreeNode right = new MockTreeNode();
        EditScript<MockTreeNode> editScript = new EditScript<>();
        editScript.relabel(left, right, 0);
        editScript.identity(new MockTreeNode(), right, 0);
        editScript.identity(left, new MockTreeNode(), 0);
        editScript.delete(left, 0);
        editScript.insert(right, 0);
        editScript.identity(left, right, 1);

        EditScript<MockTreeNode> editScript2 = new EditScript<>();
        editScript2.identity(left, right, 0);
        var operation = editScript2.getOperations().iterator().next();
        assertEquals(operation, operation);

        editScript.getOperations().forEach(other -> assertNotEquals(operation, other));
        editScript.identity(left, right, 0);
        var same = editScript.getOperations().iterator().next();
        assertEquals(operation, same);
        assertEquals(operation.hashCode(), same.hashCode());
    }

    /**
     * Tests that {@link DeltaTree#pruneIdentities()} keeps only differences with context and
     * relevant info nodes.
     *
     * The tested delta tree in preorder is:
     *
     * <pre>
     *      . root
     *          . identity
     *              . info1
     *              . anonymous
     *              + insertion
     *              - deletion
     *              * relabeling
     *          . deleteMe
     *              . info2
     * </pre>
     *
     * The expected delta tree after pruning is:
     *
     * <pre>
     *      . root
     *          . identity
     *              . info1
     *              + insertion
     *              - deletion
     *              * relabeling
     * </pre>
     */
    @Test
    public void pruneIdentities() {
        DeltaTreeNode<MockTreeNode> root = new DeltaTreeNode<>(0, true, new MockTreeNode(), new MockTreeNode());
        root.addChild(true, new MockTreeNode(), new MockTreeNode());
        DeltaTreeNode<MockTreeNode> identity = root.addChild(true, new MockTreeNode(), new MockTreeNode());
        DeltaTreeNode<MockTreeNode> info1 = identity.addChild(true, new MockInfoNode(), new MockInfoNode());
        identity.addChild(true, new MockTreeNode(), new MockTreeNode());
        DeltaTreeNode<MockTreeNode> insertion = identity.addChild(false, null, new MockTreeNode());
        DeltaTreeNode<MockTreeNode> deletion = identity.addChild(false, new MockTreeNode(), null);
        DeltaTreeNode<MockTreeNode> relabeling = identity.addChild(false, new MockTreeNode(), new MockTreeNode());
        DeltaTreeNode<MockTreeNode> deleteMe = root.addChild(true, new MockTreeNode(), new MockTreeNode());
        deleteMe.addChild(true, new MockInfoNode(), new MockInfoNode());
        DeltaTree<MockTreeNode> deltaTree = new DeltaTree<>(root);
        deltaTree.pruneIdentities();
        List<DeltaTreeNode<MockTreeNode>> expectedPreorder = List.of(root, identity, info1, insertion, deletion, relabeling);
        List<DeltaTreeNode<MockTreeNode>> actualPreorder = new ArrayList<>();
        deltaTree.forEach(actualPreorder::add);
        assertEquals(expectedPreorder, actualPreorder);
    }

    @Test
    public void deltaTreeExpansion() {
        OptimizationPhase rootPhase1 = new OptimizationPhase(OptimizationLogImpl.ROOT_PHASE_NAME);
        OptimizationPhase foo1 = new OptimizationPhase("foo1");
        rootPhase1.addChild(foo1);
        OptimizationPhase foo2 = new OptimizationPhase("foo2");
        foo1.addChild(foo2);

        OptimizationPhase rootPhase2 = new OptimizationPhase(OptimizationLogImpl.ROOT_PHASE_NAME);
        OptimizationPhase bar1 = new OptimizationPhase("bar1");
        rootPhase2.addChild(bar1);
        OptimizationPhase bar2 = new OptimizationPhase("bar2");
        bar1.addChild(bar2);

        EditScript<OptimizationTreeNode> editScript = new TreeMatcher<>(new OptimizationTreeEditPolicy()).match(rootPhase1, rootPhase2);
        DeltaTree<OptimizationTreeNode> deltaTree = DeltaTree.fromEditScript(editScript);
        Supplier<Integer> deltaTreeSize = () -> {
            Integer[] result = new Integer[]{0};
            deltaTree.forEach((node) -> result[0]++);
            return result[0];
        };
        assertEquals(3, deltaTreeSize.get().intValue());
        deltaTree.expand();
        assertEquals(5, deltaTreeSize.get().intValue());
    }

    @Test
    public void emptyDeltaTree() {
        DeltaTree<MockTreeNode> deltaTree = DeltaTree.fromEditScript(new EditScript<MockTreeNode>());
        deltaTree.forEach((node) -> Assert.fail());
        deltaTree.removeIf((node) -> {
            Assert.fail();
            return false;
        });
        assertNull(deltaTree.getRoot());

        var writer = Writer.stringBuilder(new OptionValues());
        deltaTree.accept(new DeltaTreeWriterVisitor<>(writer));
        assertTrue(writer.getOutput().contains("no differences"));
    }

    @Test
    public void writeDeltaTree() {
        MockTreeNode root1 = new MockTreeNode();
        MockTreeNode left1 = new MockTreeNode();
        root1.addChild(left1);
        MockTreeNode rel1 = new MockTreeNode();
        root1.addChild(rel1);
        MockTreeNode id1 = new MockTreeNode();
        root1.addChild(id1);

        MockTreeNode root2 = new MockTreeNode();
        MockTreeNode rel2 = new MockTreeNode();
        root2.addChild(rel2);
        MockTreeNode id2 = new MockTreeNode();
        root2.addChild(id2);
        MockTreeNode right2 = new MockTreeNode();
        root2.addChild(right2);

        DeltaTreeNode<MockTreeNode> root = new DeltaTreeNode<>(0, true, root1, root2);
        root.addChild(false, left1, null);
        root.addChild(false, rel1, rel2);
        root.addChild(true, id1, id2);
        root.addChild(false, null, right2);
        var writer = Writer.stringBuilder(new OptionValues());
        root.writeRecursive(writer);
        assertEquals("""
                        . null
                            - null
                            * null
                            . null
                            + null
                        """, writer.getOutput());

        DeltaTree<MockTreeNode> tree = new DeltaTree<>(root);
        writer = Writer.stringBuilder(new OptionValues());
        tree.accept(new DeltaTreeWriterVisitor<>(writer));
        assertEquals("""
                        . null
                            - null
                            * null -> null
                            . null
                            + null
                        """, writer.getOutput());
    }

    @Test
    public void inliningDeltaTreeWriter() {
        InliningTreeNode root1 = new InliningTreeNode("foo()", -1, true, null, false, null, false);
        root1.addChild(new InliningTreeNode("bar()", 1, false, null, false, null, true));
        root1.addChild(new InliningTreeNode("bar()", 2, false, null, true, null, true));
        root1.addChild(new InliningTreeNode("bar()", 3, true, List.of("decision 1", "decision 2"), false, null, false));
        root1.addChild(new InliningTreeNode("bar()", 4, false, null, false, null, false));

        var profile1 = new ReceiverTypeProfile(true, List.of(new ReceiverTypeProfile.ProfiledType("TypeName", 0.5, "bar()")));
        InliningTreeNode devirt1 = new InliningTreeNode("bar()", 5, false, null, true, profile1, false);
        devirt1.addChild(new InliningTreeNode("baz()", 1, true, null, false, null, false));
        root1.addChild(devirt1);

        root1.addChild(new InliningTreeNode("bar()", 6, false, null, false, null, true));
        root1.addChild(new InliningTreeNode("bar()", 7, false, null, false, null, false));
        root1.addChild(new InliningTreeNode("del()", 8, false, null, false, null, true));

        InliningTreeNode root2 = new InliningTreeNode("foo()", -1, true, null, false, null, false);
        root2.addChild(new InliningTreeNode("bar()", 1, false, null, false, null, true));
        root2.addChild(new InliningTreeNode("bar()", 2, false, null, true, null, true));
        root2.addChild(new InliningTreeNode("bar()", 3, true, List.of("decision 3", "decision 4"), false, null, false));
        root2.addChild(new InliningTreeNode("bar()", 4, false, null, false, null, false));

        InliningTreeNode devirt2 = new InliningTreeNode("bar()", 5, false, null, true, new ReceiverTypeProfile(false, List.of()), false);
        devirt2.addChild(new InliningTreeNode("baz()", 1, true, null, false, null, false));
        root2.addChild(devirt2);

        root2.addChild(new InliningTreeNode("bar()", 6, true, List.of("inlined"), false, null, false));
        root2.addChild(new InliningTreeNode("bar()", 7, false, null, false, null, true));
        root2.addChild(new InliningTreeNode("ins()", 8, false, List.of("inlined"), false, null, true));

        EditScript<InliningTreeNode> editScript = new TreeMatcher<>(new InliningTreeEditPolicy()).match(root1, root2);
        DeltaTree<InliningTreeNode> deltaTree = DeltaTree.fromEditScript(editScript);

        var writer = Writer.stringBuilder(new OptionValues());
        deltaTree.accept(new InliningDeltaTreeWriterVisitor(writer));
        assertEquals("""
                        . (root) foo()
                            . (direct) bar() at bci 1
                            . (indirect) bar() at bci 2
                            . (inlined) bar() at bci 3
                            . (deleted) bar() at bci 4
                            . (devirtualized) bar() at bci 5
                                |_ receiver-type profile in experiment 1
                                        50.00% TypeName -> bar()
                                |_ immature receiver-type profile in experiment 2
                                . (inlined) baz() at bci 1
                            * (direct -> inlined) bar() at bci 6
                                |_ no inlining decisions in experiment 1
                                |_ reasoning in experiment 2: inlined
                            * (deleted -> direct) bar() at bci 7
                            - (direct) del() at bci 8
                            + (direct) ins() at bci 8
                        """, writer.getOutput());

        writer = Writer.stringBuilder(OptionValues.builder().withAlwaysPrintInlinerReasoning(true).build());
        deltaTree.accept(new InliningDeltaTreeWriterVisitor(writer));
        assertEquals("""
                        . (root) foo()
                            . (direct) bar() at bci 1
                                |_ no inlining decisions in experiment 1
                                |_ no inlining decisions in experiment 2
                            . (indirect) bar() at bci 2
                                |_ no inlining decisions in experiment 1
                                |_ no inlining decisions in experiment 2
                            . (inlined) bar() at bci 3
                                |_ reasoning in experiment 1
                                        decision 1
                                        decision 2
                                |_ reasoning in experiment 2
                                        decision 3
                                        decision 4
                            . (deleted) bar() at bci 4
                                |_ no inlining decisions in experiment 1
                                |_ no inlining decisions in experiment 2
                            . (devirtualized) bar() at bci 5
                                |_ no inlining decisions in experiment 1
                                |_ no inlining decisions in experiment 2
                                |_ receiver-type profile in experiment 1
                                        50.00% TypeName -> bar()
                                |_ immature receiver-type profile in experiment 2
                                . (inlined) baz() at bci 1
                                    |_ no inlining decisions in experiment 1
                                    |_ no inlining decisions in experiment 2
                            * (direct -> inlined) bar() at bci 6
                                |_ no inlining decisions in experiment 1
                                |_ reasoning in experiment 2: inlined
                            * (deleted -> direct) bar() at bci 7
                                |_ no inlining decisions in experiment 1
                                |_ no inlining decisions in experiment 2
                            - (direct) del() at bci 8
                                |_ no inlining decisions
                            + (direct) ins() at bci 8
                                |_ reasoning: inlined
                        """,
                        writer.getOutput());
    }

    @Test
    public void optimizationContextDeltaTreeWriterVisitor() {
        InliningTreeNode foo1 = new InliningTreeNode("foo()", -1, true, null, false, null, false);
        foo1.addChild(new InliningTreeNode("bar()", 1, false, null, false, null, true));
        foo1.addChild(new InliningTreeNode("baz()", 2, true, null, false, null, false));
        foo1.addChild(new InliningTreeNode("baz()", 2, true, null, false, null, false));
        foo1.addChild(new InliningTreeNode("del()", 3, true, null, false, null, false));
        foo1.addChild(new InliningTreeNode("rel()", 4, false, null, false, null, true));
        InliningTree inliningTree1 = new InliningTree(foo1);

        OptimizationPhase rootPhase1 = new OptimizationPhase(OptimizationLogImpl.ROOT_PHASE_NAME);
        rootPhase1.addChild(new Optimization("Opt", "FooNull", null, null));
        rootPhase1.addChild(new Optimization("Opt", "Foo10", Position.create(List.of("foo()"), List.of(10)), null));
        rootPhase1.addChild(new Optimization("Opt", "Baz20", Position.create(List.of("baz()", "foo()"), List.of(20, 2)), EconomicMap.of("prop", 1)));
        rootPhase1.addChild(new Optimization("Opt", "Del30", Position.create(List.of("del()", "foo()"), List.of(30, 3)), null));
        OptimizationTree optimizationTree1 = new OptimizationTree(rootPhase1);

        OptimizationContextTree tree1 = OptimizationContextTree.createFrom(inliningTree1, optimizationTree1);

        InliningTreeNode foo2 = new InliningTreeNode("foo()", -1, true, null, false, null, false);
        foo2.addChild(new InliningTreeNode("bar()", 1, false, null, false, null, true));
        foo2.addChild(new InliningTreeNode("baz()", 2, true, null, false, null, false));
        foo2.addChild(new InliningTreeNode("baz()", 2, true, null, false, null, false));
        foo2.addChild(new InliningTreeNode("ins()", 3, true, null, false, null, false));
        foo2.addChild(new InliningTreeNode("rel()", 4, true, null, false, null, false));
        InliningTree inliningTree2 = new InliningTree(foo2);

        OptimizationPhase rootPhase2 = new OptimizationPhase(OptimizationLogImpl.ROOT_PHASE_NAME);
        rootPhase2.addChild(new Optimization("Opt", "FooNull", null, null));
        rootPhase2.addChild(new Optimization("Opt", "Foo10", Position.create(List.of("foo()"), List.of(10)), null));
        rootPhase2.addChild(new Optimization("Opt", "Baz20", Position.create(List.of("baz()", "foo()"), List.of(20, 2)), EconomicMap.of("prop", 2)));
        rootPhase2.addChild(new Optimization("Opt", "Ins30", Position.create(List.of("ins()", "foo()"), List.of(30, 3)), null));
        OptimizationTree optimizationTree2 = new OptimizationTree(rootPhase2);

        OptimizationContextTree tree2 = OptimizationContextTree.createFrom(inliningTree2, optimizationTree2);

        EditScript<OptimizationContextTreeNode> editScript = new TreeMatcher<>(new OptimizationContextTreeEditPolicy()).match(tree1.getRoot(), tree2.getRoot());
        DeltaTree<OptimizationContextTreeNode> deltaTree = DeltaTree.fromEditScript(editScript);
        var writer = Writer.stringBuilder(new OptionValues());
        deltaTree.accept(new OptimizationContextTreeWriterVisitor(writer));
        assertEquals("""
                        . Optimization-context tree
                            . Opt FooNull
                            . (root) foo()
                                . Opt Foo10 at bci 10
                                . (direct) bar() at bci 1
                                . (inlined) baz() at bci 2
                                    . Warning: Optimizations cannot be unambiguously attributed (duplicate path)
                                    - Opt Baz20 at bci 20 with {prop: 1}
                                    + Opt Baz20 at bci 20 with {prop: 2}
                                . (inlined) baz() at bci 2
                                    . Warning: Optimizations cannot be unambiguously attributed (duplicate path)
                                    - Opt Baz20 at bci 20 with {prop: 1}
                                    + Opt Baz20 at bci 20 with {prop: 2}
                                - (inlined) del() at bci 3
                                + (inlined) ins() at bci 3
                                * (direct -> inlined) rel() at bci 4
                                    |_ no inlining decisions in experiment 1
                                    |_ no inlining decisions in experiment 2
                        """, writer.getOutput());
    }
}
