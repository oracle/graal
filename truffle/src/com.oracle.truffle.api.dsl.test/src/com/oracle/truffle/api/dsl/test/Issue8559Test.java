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
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.test.Issue8559TestFactory.Issue8559LookupAndCallBinaryNodeGen;
import com.oracle.truffle.api.dsl.test.Issue8559TestFactory.SomeNodeGen;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

// Test for GR-8559
public class Issue8559Test {

    @Test
    public void testCall() throws UnexpectedResultException {
        Issue8559LookupAndCallBinaryNode bin = Issue8559LookupAndCallBinaryNodeGen.create(new LiteralNode(1), new LiteralNode(2));
        Object result = null;

        // does not raise UnexpectedResultException
        result = bin.executeLong(2, 1);
        Assert.assertEquals(1234L, (long) result);

        // raises UnexpectedResultException
        try {
            result = bin.executeLong(null);
        } catch (UnexpectedResultException e) {
            Assert.assertEquals(1.0, (double) e.getResult(), 0.01);
        }
    }

    public abstract static class BaseNode extends Node {

        public abstract Object execute(VirtualFrame frame);

        public int executeInt(VirtualFrame frame) throws UnexpectedResultException {
            Object value = execute(frame);
            if (value instanceof Integer) {
                return (int) value;
            }
            throw new UnexpectedResultException(value);
        }

        public long executeLong(VirtualFrame frame) throws UnexpectedResultException {
            Object value = execute(frame);
            if (value instanceof Long) {
                return (long) value;
            }
            throw new UnexpectedResultException(value);
        }
    }

    public static class LiteralNode extends BaseNode {
        private final Object val;

        protected LiteralNode(Object val) {
            this.val = val;
        }

        public static LiteralNode create(Object val) {
            return new LiteralNode(val);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return val;
        }
    }

    @NodeChild(value = "arguments", type = BaseNode[].class)
    public abstract static class SomeNode extends BaseNode {

        public abstract Object execute(Object arg, Object arg2);

        public int executeInt(int arg, int arg2) throws UnexpectedResultException {
            Object result = execute(arg, arg2);
            if (result instanceof Integer) {
                return (Integer) result;
            }
            throw new UnexpectedResultException(result);
        }

        public long executeLong(long arg, long arg2) throws UnexpectedResultException {
            Object result = execute(arg, arg2);
            if (result instanceof Long) {
                return (Long) result;
            }
            throw new UnexpectedResultException(result);
        }

        @Specialization(rewriteOn = UnexpectedResultException.class)
        protected long callLong(int a, int b) throws UnexpectedResultException {
            if (a < b) {
                throw new UnexpectedResultException(1.0);
            } else {
                return 1234L;
            }
        }

        @Specialization
        @SuppressWarnings("unused")
        protected long callLong(long a, long b) {
            return 1L;
        }

    }

    @NodeChildren({@NodeChild("arg"), @NodeChild("arg2")})
    public abstract static class Issue8559LookupAndCallBinaryNode extends BaseNode {

        public abstract int executeInt(int arg, int arg2) throws UnexpectedResultException;

        public abstract long executeLong(int arg, int arg2) throws UnexpectedResultException;

        public abstract long executeLong(long arg, long arg2) throws UnexpectedResultException;

        public abstract Object executeObject(Object arg1, Object arg2);

        protected SomeNode getBuiltin() {
            return SomeNodeGen.create(null);
        }

        @Specialization(rewriteOn = UnexpectedResultException.class)
        int callInt(int left, int right) throws UnexpectedResultException {
            return getBuiltin().executeInt(left, right);
        }

        @Specialization(rewriteOn = UnexpectedResultException.class)
        long callLong(int left, int right) throws UnexpectedResultException {
            return getBuiltin().executeLong(left, right);
        }

        @Specialization(rewriteOn = UnexpectedResultException.class)
        long callLong(long left, long right) throws UnexpectedResultException {
            return getBuiltin().executeLong(left, right);
        }

    }
}
