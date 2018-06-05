/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.dsl.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;

import java.util.Iterator;

import org.junit.Test;

import com.oracle.truffle.api.dsl.Executed;
import com.oracle.truffle.api.dsl.Introspectable;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.test.ExecutedTestFactory.ChildAndChildrenNodeGen;
import com.oracle.truffle.api.dsl.test.ExecutedTestFactory.ChildOrderTestSubNodeGen;
import com.oracle.truffle.api.dsl.test.ExecutedTestFactory.ChildrenNodeGen;
import com.oracle.truffle.api.dsl.test.ExecutedTestFactory.ExecuteWithNodeGen;
import com.oracle.truffle.api.dsl.test.ExecutedTestFactory.SingleChildNodeGen;
import com.oracle.truffle.api.dsl.test.ExecutedTestFactory.TwoChildNodeGen;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

@SuppressWarnings("unused")
public class ExecutedTest {

    @ExpectError("The type ErrorAccessorMethodsDisallowed must implement the inherited abstract method getChild0Node().")
    abstract static class ErrorAccessorMethodsDisallowed extends BaseNode {

        @Child @Executed BaseNode child0Node;

        @Specialization
        protected Object s0(Object child0) {
            return "s0";
        }

        public abstract BaseNode getChild0Node();

    }

    abstract static class ErrorNodeChildAnnotation extends BaseNode {

        @ExpectError("Field annotated with @Executed must also be annotated with @Child.") @Executed BaseNode child0Node;

        @Specialization
        protected Object s0(int child0) {
            return "s0";
        }

    }

    abstract static class ErrorPrivateField extends BaseNode {

        @ExpectError("Field annotated with @Executed must be visible for the generated subclass to execute.") @Child @Executed private BaseNode child0Node;

        @Specialization
        protected Object s0(int child0) {
            return "s0";
        }

    }

    abstract static class SingleChildNode extends BaseNode {

        @Child @Executed BaseNode child0Node;

        SingleChildNode(BaseNode child0Node) {
            this.child0Node = child0Node;
        }

        @Specialization
        protected Object s0(int child0) {
            return "s0";
        }

    }

    @Test
    public void testSingleChild() {
        SingleChildNode child = SingleChildNodeGen.create(new ConstantNode(42));
        assertEquals("s0", child.execute(null));
        Iterator<Node> nodes = child.getChildren().iterator();
        assertSame(child.child0Node, nodes.next());
        assertFalse(nodes.hasNext());
    }

    abstract static class TwoChildNode extends BaseNode {

        @Child @Executed BaseNode child0Node;
        @Child @Executed BaseNode child1Node;

        TwoChildNode(BaseNode child0Node, BaseNode child1Node) {
            this.child0Node = child0Node;
            this.child1Node = child1Node;
        }

        @Specialization
        protected Object s0(int child0, int child1) {
            return "s0";
        }

    }

    @Test
    public void testTwoChildNode() {
        TwoChildNode child = TwoChildNodeGen.create(new ConstantNode(42), new ConstantNode(42));
        assertEquals("s0", child.execute(null));

        Iterator<Node> nodes = child.getChildren().iterator();
        assertSame(child.child0Node, nodes.next());
        assertSame(child.child1Node, nodes.next());
        assertFalse(nodes.hasNext());
    }

    abstract static class ChildrenNode extends BaseNode {

        @Children @Executed BaseNode[] children;

        ChildrenNode(BaseNode... children) {
            this.children = children;
        }

        @Specialization
        protected Object s0(int child0, int child1) {
            return "s0";
        }

    }

    @Test
    public void testChildrenNode() {
        ChildrenNode child = ChildrenNodeGen.create(new ConstantNode(42), new ConstantNode(42));
        assertEquals("s0", child.execute(null));

        Iterator<Node> nodes = child.getChildren().iterator();
        assertSame(child.children[0], nodes.next());
        assertSame(child.children[1], nodes.next());
        assertFalse(nodes.hasNext());
    }

    abstract static class ChildAndChildrenNode extends BaseNode {

        @Child @Executed BaseNode first;
        @Children @Executed BaseNode[] children;

        ChildAndChildrenNode(BaseNode first, BaseNode... children) {
            this.first = first;
            this.children = children;
        }

        @Specialization
        protected Object s0(int child0, int child1) {
            return "s0";
        }

    }

    @Test
    public void testChildAndChildrenNode() {
        ChildAndChildrenNode child = ChildAndChildrenNodeGen.create(new ConstantNode(42), new ConstantNode(42));
        assertEquals("s0", child.execute(null));

        Iterator<Node> nodes = child.getChildren().iterator();
        assertSame(child.first, nodes.next());
        assertSame(child.children[0], nodes.next());
        assertFalse(nodes.hasNext());
    }

    abstract static class ErrorChildrenAndChildNode extends BaseNode {

        @Children @Executed BaseNode[] children;
        @ExpectError("Field annotated with @Executed is hidden by executed field 'children'. " +
                        "Executed child fields with multiple children hide all following executed child declarations. " +
                        "Reorder or remove this executed child declaration.") @Executed @Child BaseNode first;

        ErrorChildrenAndChildNode(BaseNode[] children, BaseNode first) {
            this.children = children;
            this.first = first;
        }

        @Specialization
        protected Object s0(int child0, int child1) {
            return "s0";
        }

    }

    @Test
    public void testChildInheritanceOrder() {
        ChildOrderTestSubNode child = ChildOrderTestSubNodeGen.create(new ConstantNode(20), new ConstantNode(22));
        assertEquals(42, child.execute(null));

        Iterator<Node> nodes = child.getChildren().iterator();
        assertSame(child.child0Node, nodes.next());
        assertSame(child.child1Node, nodes.next());
        assertFalse(nodes.hasNext());
    }

    abstract static class ChildOrderTestNode extends BaseNode {

        @Child @Executed BaseNode child0Node;

        ChildOrderTestNode(BaseNode child0) {
            this.child0Node = child0;
        }

    }

    abstract static class ChildOrderTestSubNode extends ChildOrderTestNode {

        @Child @Executed BaseNode child1Node;

        ChildOrderTestSubNode(BaseNode child0, BaseNode child1) {
            super(child0);
            this.child1Node = child1;
        }

        @Specialization
        protected Object s0(int child0, int child1) {
            return child0 + child1;
        }

    }

    @Test
    public void testExecuteWithNode() {
        ExecuteWithNode child = ExecuteWithNodeGen.create(new ConstantNode(42), new ExecutableWithNode() {
            @Override
            Object executeWith(Object argument) {
                return 41;
            }
        });
        assertEquals("s0", child.execute(null));

        Iterator<Node> nodes = child.getChildren().iterator();
        assertSame(child.child0Node, nodes.next());
        assertSame(child.child1Node, nodes.next());
        assertFalse(nodes.hasNext());
    }

    abstract static class ExecutableWithNode extends Node {

        abstract Object executeWith(Object argument);

    }

    abstract static class ExecuteWithNode extends BaseNode {

        @Child @Executed BaseNode child0Node;
        @Child @Executed(with = "child0Node") ExecutableWithNode child1Node;

        ExecuteWithNode(BaseNode child0, ExecutableWithNode child1) {
            this.child0Node = child0;
            this.child1Node = child1;
        }

        @Specialization
        protected Object s0(int child0, int child1) {
            return "s0";
        }

    }

    abstract static class ErrorExecuteWithNode extends BaseNode {

        @Child @Executed BaseNode child0Node;
        @ExpectError("No generic execute method found with 0 evaluated arguments for node type ExecutableWithNode and frame types%") @Child @Executed ExecutableWithNode child1Node;

        ErrorExecuteWithNode(BaseNode child0, ExecutableWithNode child1) {
            this.child0Node = child0;
            this.child1Node = child1;
        }

        @Specialization
        protected Object s0(int child0, int child1) {
            return "s0";
        }

    }

    abstract static class ErrorSameChildName extends ChildOrderTestNode {

        @ExpectError("Field annotated with @Executed has duplicate name 'child0Node'. " +
                        "Executed children must have unique names.") @Child @Executed BaseNode child0Node;

        ErrorSameChildName(BaseNode child0, BaseNode child1) {
            super(child0);
            this.child0Node = child1;
        }

        @Specialization
        protected Object s0(int child0, int child1) {
            return "s0";
        }

    }

    // utility classes

    @Introspectable
    abstract static class BaseNode extends Node {

        abstract Object execute(VirtualFrame frame);

    }

    static final class ConstantNode extends BaseNode {

        private final Object constant;

        ConstantNode(Object constant) {
            this.constant = constant;
        }

        @Override
        Object execute(VirtualFrame frame) {
            return constant;
        }
    }

}
