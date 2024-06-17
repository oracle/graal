/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.test.NodeChildCreateTestFactory.CreateTestChildNodeGen;
import com.oracle.truffle.api.dsl.test.NodeChildCreateTestFactory.CreateTestNodeGen;
import com.oracle.truffle.api.dsl.test.NodeChildCreateTestFactory.CustomCreateTestNodeGen;
import com.oracle.truffle.api.nodes.Node;

@SuppressWarnings({"truffle-inlining", "truffle-neverdefault", "truffle-sharing"})
public class NodeChildCreateTest {

    abstract static class CreateTestBaseNode extends Node {

        abstract int execute();
    }

    abstract static class CreateTestChildNode extends CreateTestBaseNode {

        @Specialization
        int doSomething() {
            return 5;
        }
    }

    @NodeChild(value = "child0", type = CreateTestChildNode.class, implicit = true)
    abstract static class CreateTestNode extends CreateTestBaseNode {

        abstract CreateTestChildNode getChild0();

        @Specialization
        int doInt(int child0) {
            return child0 + 1;
        }
    }

    @Test
    public void testImplicit() {
        CreateTestNode node = CreateTestNodeGen.create();
        Assert.assertThat("child0", node.getChild0(), is(notNullValue()));
        Assert.assertTrue("child0 is adoptable", node.getChild0().isAdoptable());
        Assert.assertEquals("result", 6, node.execute());
    }

    @NodeChild(value = "child0", type = CreateTestBaseNode.class, implicitCreate = "customCreate()")
    abstract static class CustomCreateTestNode extends CreateTestBaseNode {

        static CreateTestBaseNode customCreate() {
            return CreateTestNodeGen.create();
        }

        abstract CreateTestBaseNode getChild0();

        @Specialization
        int doInt(int child0) {
            return child0 + 3;
        }
    }

    @Test
    public void testImplicitCreate() {
        CustomCreateTestNode node = CustomCreateTestNodeGen.create();
        Assert.assertThat("child0", node.getChild0(), is(instanceOf(CreateTestNode.class)));
        Assert.assertTrue("child0 is adoptable", node.getChild0().isAdoptable());
        Assert.assertEquals("result", 9, node.execute());
    }

    @ExpectError("Error parsing expression 'create()': " +
                    "The method create is undefined for the enclosing scope.")
    @NodeChild(type = CreateTestBaseNode.class, implicit = true)
    abstract static class NotUncachableTestNode extends CreateTestBaseNode {

        @Specialization
        int doInt(int child0) {
            return child0;
        }
    }

    @ExpectError("Error parsing expression 'error': error cannot be resolved.")
    @NodeChild(type = CreateTestBaseNode.class, implicitCreate = "error")
    abstract static class InvalidExpressionTestNode extends CreateTestBaseNode {

        @Specialization
        int doInt(int child0) {
            return child0;
        }
    }

    @ExpectError("The attributes 'implicit' and 'implicitCreate' are mutually exclusive. " +
                    "Remove one of the attributes to resolve this.")
    @NodeChild(type = CreateTestBaseNode.class, implicitCreate = "custom()", implicit = true)
    abstract static class RedundantPropertyTestNode extends CreateTestBaseNode {

        static CreateTestBaseNode custom() {
            return CreateTestChildNodeGen.create();
        }

        @Specialization
        int doInt(int child0) {
            return child0;
        }
    }
}
