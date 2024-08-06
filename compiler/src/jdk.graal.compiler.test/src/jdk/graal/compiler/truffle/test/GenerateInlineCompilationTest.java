/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.truffle.test;

import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.truffle.test.GenerateInlineCompilationTestFactory.CustomNodeGen;
import jdk.graal.compiler.truffle.test.GenerateInlineCompilationTestFactory.TestChildrenNodeGen;
import jdk.graal.compiler.truffle.test.GenerateInlineCompilationTestFactory.TestDimensionsRootNodeGen;
import jdk.graal.compiler.truffle.test.GenerateInlineCompilationTestFactory.UseProfilesNodeGen;
import org.junit.Test;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.dsl.AOTSupport;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateAOT;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.InlineSupport.RequiredField;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExecutionSignature;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.runtime.OptimizedCallTarget;

public class GenerateInlineCompilationTest extends PartialEvaluationTest {

    @GenerateInline
    @GenerateCached
    @SuppressWarnings("unused")
    @GenerateAOT
    public abstract static class CustomNode extends Node {

        abstract int execute(Node node, int v);

        @Specialization(guards = "v == 0")
        int s0(Node node, int v) {
            return 1;
        }

        @Specialization(guards = "v == 1")
        int s1(Node node, int v) {
            return 2;
        }

        @Specialization(guards = "v == 2")
        int s2(Node node, int v) {
            return 3;
        }

        @Specialization(guards = "v == 3")
        int s3(Node node, int v) {
            return 4;
        }

    }

    @GenerateAOT
    public abstract static class UseProfilesNode extends Node {

        final boolean inlined;

        UseProfilesNode(boolean inlined) {
            this.inlined = inlined;
        }

        abstract int execute(int v);

        @Specialization
        int s0(int v, @Cached(inline = true) CustomNode inlinedNode,
                        @Cached InlinedBranchProfile inlinedBranchProfile,
                        @Cached InlinedConditionProfile inlinedConditionProfile,
                        @Cached(inline = false) CustomNode node,
                        @Cached(inline = false) BranchProfile branchProfile,
                        @Cached(inline = false) ConditionProfile conditionProfile) {
            if (inlined) {
                inlinedBranchProfile.enter(this);
                if (inlinedConditionProfile.profile(this, true)) {
                    return inlinedNode.execute(this, v);
                } else {
                    return 42;
                }
            } else {
                branchProfile.enter();
                if (conditionProfile.profile(true)) {
                    return node.execute(node, v);
                } else {
                    return 42;
                }
            }
        }
    }

    static class TestProfilesNode extends RootNode {

        @Child UseProfilesNode node;

        protected TestProfilesNode(boolean inlined) {
            super(null);
            node = UseProfilesNodeGen.create(inlined);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return node.execute(0) + node.execute(1) + node.execute(2);
        }

        @Override
        protected ExecutionSignature prepareForAOT() {
            AOTSupport.prepareForAOT(this);
            return ExecutionSignature.create(Integer.class, new Class<?>[0]);
        }

        @Override
        public String getName() {
            return "TestProfilesName[inlined=" + node.inlined + "]";
        }

        @Override
        public String toString() {
            return getName();
        }

    }

    @Test
    public void testInlined() {
        assertPartialEvalEquals(new TestProfilesNode(false), new TestProfilesNode(true), new Object[0]);
        assertTrue(lastCompiledGraph.getNodes(ReturnNode.TYPE).count() > 0);
    }

    @Test
    public void testAOT() {
        preventProfileCalls = true;
        try {
            OptimizedCallTarget notInlined = (OptimizedCallTarget) new TestProfilesNode(false).getCallTarget();
            OptimizedCallTarget inlined = (OptimizedCallTarget) new TestProfilesNode(true).getCallTarget();

            notInlined.prepareForAOT();
            inlined.prepareForAOT();

            assertPartialEvalEquals(notInlined, inlined, new Object[0], true);
            assertTrue(notInlined.isValid());
            assertTrue(inlined.isValid());
            notInlined.call();
            inlined.call();

            // test does not deopt
            assertTrue(notInlined.isValid());
            assertTrue(inlined.isValid());
        } finally {
            preventProfileCalls = false;
        }
    }

    @GenerateInline
    @GenerateCached(false)
    @SuppressWarnings("unused")
    public abstract static class ChildrenTest extends Node {

        abstract int execute(Node node, int v);

        @Specialization
        @ExplodeLoop
        int s0(Node node, int v, @Cached(value = "createChildren()", neverDefault = true) CustomNode[] children) {
            int sum = 0;
            for (CustomNode customNode : children) {
                sum += customNode.execute(customNode, v);
            }
            return sum;
        }

        static CustomNode[] createChildren() {
            CustomNode[] node = new CustomNode[10];
            for (int i = 0; i < 10; i++) {
                node[i] = CustomNodeGen.create();
            }
            return node;
        }

    }

    abstract static class TestChildrenNode extends RootNode {

        protected TestChildrenNode() {
            super(null);
        }

        @Specialization
        Object doDefault(@Cached ChildrenTest test) {
            int result = test.execute(this, 1);
            CompilerAsserts.partialEvaluationConstant(result);
            return result;
        }

        @Override
        public String getName() {
            return "TestProfilesName[]";
        }

        @Override
        public String toString() {
            return getName();
        }

    }

    /**
     * Tests usage of inlined children fields. Fails if annotation is not used on the inlined field.
     */
    @Test
    public void testChildren() {
        OptimizedCallTarget notInlined = (OptimizedCallTarget) TestChildrenNodeGen.create().getCallTarget();
        assertPartialEvalEquals((OptimizedCallTarget) new RootNode(null) {

            @Override
            public Object execute(VirtualFrame frame) {
                return 20;
            }
        }.getCallTarget(), notInlined, new Object[0], true);
    }

    @GenerateInline
    @GenerateCached(false)
    @SuppressWarnings("unused")
    public abstract static class DimensionsNode extends Node {

        abstract int execute(Node node, int v);

        @Specialization
        @ExplodeLoop
        int s0(Node node, int v, @Cached(value = "createChildren()", dimensions = 1, neverDefault = true) Object[] children) {
            int sum = 0;
            for (Object customNode : children) {
                sum += ((CustomNode) customNode).execute(((CustomNode) customNode), v);
            }
            return sum;
        }

        static Object[] createChildren() {
            Object[] node = new Object[10];
            for (int i = 0; i < 10; i++) {
                node[i] = CustomNodeGen.create();
            }
            return node;
        }

    }

    abstract static class TestDimensionsRootNode extends RootNode {

        protected TestDimensionsRootNode() {
            super(null);
        }

        @Specialization
        Object doDefault(@Cached DimensionsNode test) {
            int result = test.execute(this, 1);
            CompilerAsserts.partialEvaluationConstant(result);
            return result;
        }

        @Override
        public String getName() {
            return "TestProfilesName[]";
        }

        @Override
        public String toString() {
            return getName();
        }

    }

    /**
     * Tests propagation of compilation final dimensions to inlined fields using
     * {@link RequiredField#dimensions()}.
     */
    @Test
    public void testDimensions() {
        OptimizedCallTarget notInlined = (OptimizedCallTarget) TestDimensionsRootNodeGen.create().getCallTarget();
        assertPartialEvalEquals((OptimizedCallTarget) new RootNode(null) {

            @Override
            public Object execute(VirtualFrame frame) {
                return 20;
            }
        }.getCallTarget(), notInlined, new Object[0], true);
    }

}
