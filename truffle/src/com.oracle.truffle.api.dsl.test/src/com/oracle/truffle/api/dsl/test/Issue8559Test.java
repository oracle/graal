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
