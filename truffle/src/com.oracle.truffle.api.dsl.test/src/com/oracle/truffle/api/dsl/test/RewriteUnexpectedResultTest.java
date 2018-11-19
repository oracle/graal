/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.test.RewriteUnexpectedResultTestFactory.RewriteUnexpected1Factory;
import com.oracle.truffle.api.dsl.test.RewriteUnexpectedResultTestFactory.RewriteUnexpected2Factory;
import com.oracle.truffle.api.dsl.test.RewriteUnexpectedResultTestFactory.RewriteUnexpected3NodeGen;
import com.oracle.truffle.api.dsl.test.RewriteUnexpectedResultTestFactory.RewriteUnexpected4NodeGen;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.ValueNode;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

public class RewriteUnexpectedResultTest {

    abstract static class FailureFromImplicitObject extends Node {

        public abstract int executeInt(Object value);

        @ExpectError("Implicit 'Object' return type from UnexpectedResultException not compatible with generic type 'int'.")
        @Specialization(rewriteOn = UnexpectedResultException.class)
        int f1(int a) throws UnexpectedResultException {
            if (a == 5) {
                throw new UnexpectedResultException("foo");
            }
            return a + 1;
        }

        @Specialization(replaces = "f1")
        int f2(int a) {
            return a + 2;
        }
    }

    abstract static class FailureFromObjectReturn extends Node {

        public abstract Object executeInt(Object value);

        @ExpectError("A specialization with return type 'Object' cannot throw UnexpectedResultException.")
        @Specialization(rewriteOn = UnexpectedResultException.class)
        Object f1(int a) throws UnexpectedResultException {
            if (a == 5) {
                throw new UnexpectedResultException("foo");
            }
            return 1;
        }

        @Specialization(replaces = "f1")
        int f2(int a) {
            return a + 2;
        }
    }

    private static final class NonRepeatableNode extends ValueNode {
        private int counter = 0;

        @Override
        public Object execute(VirtualFrame frame) {
            return counter++;
        }
    }

    @Test
    public void testNoReexecute() {
        RewriteUnexpected1 node = RewriteUnexpected1Factory.create(new NonRepeatableNode());

        Assert.assertEquals(100, node.execute(null));
        Assert.assertEquals(102, node.execute(null));
        Assert.assertEquals("foo4", node.execute(null));
        Assert.assertEquals("bar6", node.execute(null));
    }

    @NodeChild("a")
    abstract static class RewriteUnexpected1 extends ValueNode {
        @Child private NonRepeatableNode child = new NonRepeatableNode();

        @Specialization(rewriteOn = UnexpectedResultException.class)
        int f1(int a) throws UnexpectedResultException {
            int value = a + (int) child.execute(null);
            if (value >= 4) {
                throw new UnexpectedResultException("foo" + value);
            }
            return value + 100;
        }

        @Specialization(replaces = "f1")
        Object f2(int a) {
            int value = a + (int) child.execute(null);
            if (value >= 4) {
                return "bar" + value;
            }
            return value + 200;
        }
    }

    @Test
    public void testMultipleExceptions() {
        RewriteUnexpected2 node = RewriteUnexpected2Factory.create(false, new NonRepeatableNode());

        Assert.assertEquals(100, node.execute(null));
        Assert.assertEquals(102, node.execute(null));
        Assert.assertEquals("foo4", node.execute(null));
        Assert.assertEquals("bar6", node.execute(null));

        // with an IllegalArgumentException, "child" is re-executed
        node = RewriteUnexpected2Factory.create(true, new NonRepeatableNode());

        Assert.assertEquals(100, node.execute(null));
        Assert.assertEquals(102, node.execute(null));
        Assert.assertEquals("bar5", node.execute(null));
        Assert.assertEquals("bar7", node.execute(null));
    }

    @NodeChild("a")
    abstract static class RewriteUnexpected2 extends ValueNode {

        private final boolean throwIllegal;
        @Child private NonRepeatableNode child = new NonRepeatableNode();

        protected RewriteUnexpected2(boolean throwIllegal) {
            this.throwIllegal = throwIllegal;
        }

        @Specialization(rewriteOn = {UnexpectedResultException.class, IllegalArgumentException.class})
        int f1(int a) throws UnexpectedResultException, IllegalArgumentException {
            int value = a + (int) child.execute(null);
            if (value >= 4) {
                if (throwIllegal) {
                    throw new IllegalArgumentException();
                } else {
                    throw new UnexpectedResultException("foo" + value);
                }
            }
            return value + 100;
        }

        @Specialization(replaces = "f1")
        Object f2(int a) {
            int value = a + (int) child.execute(null);
            if (value >= 4) {
                return "bar" + value;
            }
            return value + 200;
        }
    }

    @Test
    public void testIncompatibleResult() {
        RewriteUnexpected3 node = RewriteUnexpected3NodeGen.create();

        Assert.assertEquals(100, node.execute(0));
        Assert.assertEquals(102, node.execute(1));
        try {
            node.executeInt(2);
            Assert.fail("expected ClassCastException");
        } catch (ClassCastException e) {
            // expected
        }
        Assert.assertEquals(206, node.execute(3));
    }

    abstract static class RewriteUnexpected3 extends Node {
        @Child private NonRepeatableNode child = new NonRepeatableNode();

        public abstract Object execute(Object value);

        public abstract int executeInt(Object value);

        @Specialization(rewriteOn = UnexpectedResultException.class)
        int f1(int a) throws UnexpectedResultException {
            int value = a + (int) child.execute(null);
            if (value >= 4) {
                throw new UnexpectedResultException("foo" + value);
            }
            return value + 100;
        }

        @Specialization(replaces = "f1")
        int f2(int a) {
            int value = a + (int) child.execute(null);
            return value + 200;
        }
    }

    @Test
    public void testForward() {
        RewriteUnexpected4 node = RewriteUnexpected4NodeGen.create();

        Assert.assertEquals(100, node.execute(0));
        Assert.assertEquals(102, node.execute(1));
        try {
            node.executeInt(2);
            Assert.fail("expected UnexpectedResultException");
        } catch (UnexpectedResultException e) {
            Assert.assertEquals("foo4", e.getResult());
        }
        Assert.assertEquals(206, node.execute(3));
    }

    abstract static class RewriteUnexpected4 extends Node {
        @Child private NonRepeatableNode child = new NonRepeatableNode();

        public abstract Object execute(Object value);

        public abstract int executeInt(Object value) throws UnexpectedResultException;

        @Specialization(rewriteOn = UnexpectedResultException.class)
        int f1(int a) throws UnexpectedResultException {
            int value = a + (int) child.execute(null);
            if (value >= 4) {
                throw new UnexpectedResultException("foo" + value);
            }
            return value + 100;
        }

        @Specialization(replaces = "f1")
        int f2(int a) {
            int value = a + (int) child.execute(null);
            return value + 200;
        }
    }
}
