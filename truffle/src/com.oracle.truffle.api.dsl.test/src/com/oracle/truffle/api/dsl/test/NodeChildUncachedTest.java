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

import org.hamcrest.MatcherAssert;
import org.hamcrest.core.StringContains;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.test.NodeChildUncachedTestFactory.CustomUncachedTestNodeGen;
import com.oracle.truffle.api.dsl.test.NodeChildUncachedTestFactory.TestChildNodeGen;
import com.oracle.truffle.api.dsl.test.NodeChildUncachedTestFactory.UncachedTestNodeGen;
import com.oracle.truffle.api.dsl.test.NodeChildUncachedTestFactory.UncachedTestWithNodeGen;
import com.oracle.truffle.api.nodes.Node;

@SuppressWarnings({"truffle-inlining", "truffle-neverdefault", "truffle-sharing"})
public class NodeChildUncachedTest {

    abstract static class TestBaseNode extends Node {

        abstract int execute();
    }

    @GenerateUncached
    abstract static class TestChildNode extends TestBaseNode {

        @Specialization
        int doSomething() {
            return 5;
        }
    }

    @NodeChild(value = "child0", type = TestChildNode.class, allowUncached = true)
    @GenerateUncached
    abstract static class UncachedTestNode extends TestBaseNode {

        abstract int executeWith(int child0);

        abstract TestChildNode getChild0();

        @Specialization
        int doInt(int child0) {
            return child0 + 1;
        }
    }

    @Test
    public void testUncachedChild() {
        UncachedTestNode node = UncachedTestNodeGen.getUncached();
        Assert.assertSame("child0", TestChildNodeGen.getUncached(), node.getChild0());
        Assert.assertEquals("execute()", 6, node.execute());
        Assert.assertEquals("executeWith(41)", 42, node.executeWith(41));
    }

    @NodeChild(value = "child0", type = TestBaseNode.class)
    @NodeChild(value = "child1", type = UncachedTestNode.class, allowUncached = true, executeWith = "child0")
    @GenerateUncached
    abstract static class UncachedTestWithNode extends TestBaseNode {

        abstract TestBaseNode getChild0();

        abstract UncachedTestNode getChild1();

        abstract int executeWith(int child0);

        @Specialization
        int doInt(int child0, int child1) {
            return child0 + child1;
        }
    }

    @Test
    public void testUncachedWith() {
        UncachedTestWithNode node = UncachedTestWithNodeGen.getUncached();
        Assert.assertSame("child1", UncachedTestNodeGen.getUncached(), node.getChild1());
        Assert.assertEquals("executeWith(41)", 83, node.executeWith(41));
    }

    @Test
    public void testChildNotAvailable() {
        AssertionError error = Assert.assertThrows(AssertionError.class, () -> UncachedTestWithNodeGen.getUncached().getChild0());
        MatcherAssert.assertThat(error.getMessage(), StringContains.containsString("This getter method cannot be used for uncached node versions as it requires child nodes to be present."));
    }

    @Test
    public void testExecuteNotAvailable() {
        AssertionError error = Assert.assertThrows(AssertionError.class, () -> UncachedTestWithNodeGen.getUncached().execute());
        MatcherAssert.assertThat(error.getMessage(), StringContains.containsString("This execute method cannot be used for uncached node versions as it requires child nodes to be present. " +
                        "Use an execute method that takes all arguments as parameters."));
    }

    @NodeChild(value = "child0", type = TestBaseNode.class, uncached = "customGetUncached()")
    @GenerateUncached
    abstract static class CustomUncachedTestNode extends TestBaseNode {

        static TestChildNode customGetUncached() {
            return TestChildNodeGen.getUncached();
        }

        abstract int executeWith(int child0);

        abstract TestBaseNode getChild0();

        @Specialization
        int doInt(int child0) {
            return child0 + 3;
        }
    }

    @Test
    public void testCustomUncached() {
        CustomUncachedTestNode node = CustomUncachedTestNodeGen.getUncached();
        Assert.assertSame("child0", CustomUncachedTestNode.customGetUncached(), node.getChild0());
        Assert.assertEquals("execute()", 8, node.execute());
        Assert.assertEquals("executeWith(41)", 44, node.executeWith(41));
    }

    @ExpectError("Error parsing expression 'getUncached()': " +
                    "The method getUncached is undefined for the enclosing scope.")
    @NodeChild(type = TestBaseNode.class, allowUncached = true)
    abstract static class NotUncachableTestNode extends TestBaseNode {

        @Specialization
        int doInt(int child0) {
            return child0;
        }
    }

    @ExpectError("Error parsing expression 'error': error cannot be resolved.")
    @NodeChild(type = TestBaseNode.class, uncached = "error")
    abstract static class InvalidExpressionTestNode extends TestBaseNode {

        @Specialization
        int doInt(int child0) {
            return child0;
        }
    }

    @ExpectError("The attributes 'allowUncached' and 'uncached' are mutually exclusive. " +
                    "Remove one of the attributes to resolve this.")
    @NodeChild(type = TestBaseNode.class, uncached = "custom()", allowUncached = true)
    abstract static class RedundantPropertyTestNode extends TestBaseNode {

        static TestBaseNode custom() {
            return TestChildNodeGen.getUncached();
        }

        @Specialization
        int doInt(int child0) {
            return child0;
        }
    }
}
