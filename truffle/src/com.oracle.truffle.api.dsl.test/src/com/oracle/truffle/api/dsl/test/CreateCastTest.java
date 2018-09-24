/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.dsl.test;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.dsl.CreateCast;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.test.CreateCastTestFactory.CreateCastNode1Factory;
import com.oracle.truffle.api.dsl.test.CreateCastTestFactory.CreateCastNode2Factory;
import com.oracle.truffle.api.dsl.test.CreateCastTestFactory.CreateCastNode3Factory;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.ChildrenNode;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.TestRootNode;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.ValueNode;
import com.oracle.truffle.api.nodes.Node;

@SuppressWarnings("unused")
public class CreateCastTest {

    @Test
    public void testCastNode1() {
        TestRootNode<CreateCastNode1> root = TestHelper.createRoot(CreateCastNode1Factory.getInstance());
        Assert.assertEquals(1, root.getNode().invocations);
    }

    @Test
    public void testCastNode2() {
        TestRootNode<CreateCastNode2> root = TestHelper.createRoot(CreateCastNode2Factory.getInstance());
        Assert.assertEquals(2, root.getNode().invocations);
    }

    @Test
    public void testCastNode3() {
        TestRootNode<CreateCastNode3> root = TestHelper.createRoot(CreateCastNode3Factory.getInstance());
        Assert.assertEquals(1, root.getNode().invocations);
    }

    @NodeChild("a")
    abstract static class CreateCastNode1 extends ValueNode {

        int invocations = 0;

        @CreateCast({"a"})
        public ValueNode createCast(ValueNode node) {
            invocations++;
            return node;
        }

        @Specialization
        public int doInteger(int a) {
            throw new AssertionError();
        }
    }

    @NodeChildren({@NodeChild("a"), @NodeChild("b")})
    abstract static class CreateCastNode2 extends ValueNode {

        int invocations = 0;

        @CreateCast({"a", "b"})
        public ValueNode createCast(ValueNode node) {
            invocations++;
            return node;
        }

        @Specialization
        public int doInteger(int a, int b) {
            throw new AssertionError();
        }
    }

    abstract static class CreateCastNode3 extends ChildrenNode {

        int invocations = 0;

        @CreateCast("children")
        public ValueNode[] createCast(ValueNode[] node) {
            invocations++;
            return node;
        }

        @Specialization
        public int doInteger(int a) {
            throw new AssertionError();
        }
    }

    @NodeChild("a")
    abstract static class CreateCastFailNode1 extends ValueNode {

        @ExpectError({"Specified child '' not found."})
        @CreateCast({""})
        public ValueNode createCast(Node child) {
            throw new AssertionError();
        }

        @Specialization
        public int doInteger(int a) {
            throw new AssertionError();
        }
    }

}
