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

import static com.oracle.truffle.api.dsl.test.TestHelper.createCallTarget;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.test.NodeFieldTestFactory.IntFieldTestNodeFactory;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.ValueNode;

public class NodeChildTest {

    @Test
    public void testIntField() {
        assertEquals(42, createCallTarget(IntFieldTestNodeFactory.create(42)).call());
    }

    @NodeChild("child0")
    abstract static class Base0Node extends ValueNode {

    }

    @NodeChild(value = "child1", type = ValueNode.class)
    abstract static class Child0Node extends Base0Node {

        @Specialization
        int intField(int child0, int child1) {
            return child0 + child1;
        }
    }

    @NodeChildren({@NodeChild("child0")})
    abstract static class Base1Node extends ValueNode {

    }

    @NodeChildren({@NodeChild(value = "child1", type = ValueNode.class)})
    abstract static class Child1Node extends Base1Node {

        @Specialization
        int intField(int child0, int child1) {
            return child0 + child1;
        }
    }

    @NodeChildren({@NodeChild("child0"), @NodeChild("child1")})
    abstract static class Base2Node extends ValueNode {

    }

    @ExpectError("Not enough child node declarations found. Please annotate the node class with addtional @NodeChild annotations or remove all execute methods that do not provide all evaluated values. " +
                    "The following execute methods do not provide all evaluated values for the expected signature size 3:%")
    @NodeChildren({@NodeChild(value = "child2", type = ValueNode.class)})
    abstract static class Child2Node extends Base1Node {

        @ExpectError("Method signature (int, int, int) does not match to the expected signature:%")
        @Specialization
        int intField(int child0, int child1, int child2) {
            return child0 + child1 + child2;
        }
    }

    @NodeChildren({@NodeChild(value = "receiver", type = ValueNode.class), @NodeChild(value = "arguments", type = ValueNode[].class)})
    abstract static class BaseNode extends ValueNode {
        public abstract ValueNode getReceiver();

        public abstract ValueNode[] getArguments();
    }

    abstract static class UnaryNode extends BaseNode {
        @Specialization
        public int doIt(int value) {
            return value;
        }
    }

    abstract static class BinaryNode extends BaseNode {
        @Specialization
        public int doIt(int value0, int value1) {
            return value0 + value1;
        }
    }

    abstract static class TernaryNode extends BaseNode {
        @Specialization
        public int doIt(int value0, int value1, int value2) {
            return value0 + value1 + value2;
        }
    }

}
