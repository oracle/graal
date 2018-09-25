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

import java.math.BigInteger;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImplicitCast;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.TypeCast;
import com.oracle.truffle.api.dsl.TypeCheck;
import com.oracle.truffle.api.dsl.TypeSystem;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

public class TypeSystemTest {

    @TypeSystem({byte.class, short.class, int.class, long.class, double.class, boolean.class, BigInteger.class, String.class, CallTarget.class, BExtendsAbstract.class, CExtendsAbstract.class,
                    Abstract.class, Interface.class, Object[].class})
    static class SimpleTypes {

        static int intCheck;
        static int intCast;

        @TypeCheck(int.class)
        public static boolean isInteger(Object value) {
            intCheck++;
            return value instanceof Integer;
        }

        @TypeCast(int.class)
        public static int asInteger(Object value) {
            intCast++;
            return (int) value;
        }

        @ImplicitCast
        public static double castDouble(int value) {
            return value;
        }

        @ImplicitCast
        public static long castLong(int value) {
            return value;
        }

        @ImplicitCast
        public static BigInteger castBigInteger(int value) {
            return BigInteger.valueOf(value);
        }

    }

    @TypeSystemReference(SimpleTypes.class)
    @GenerateNodeFactory
    public static class ValueNode extends Node {

        public ValueNode() {
        }

        public int executeInt(VirtualFrame frame) throws UnexpectedResultException {
            return SimpleTypesGen.expectInteger(execute(frame));
        }

        public long executeLong(VirtualFrame frame) throws UnexpectedResultException {
            return SimpleTypesGen.expectLong(execute(frame));
        }

        public String executeString(VirtualFrame frame) throws UnexpectedResultException {
            return SimpleTypesGen.expectString(execute(frame));
        }

        public boolean executeBoolean(VirtualFrame frame) throws UnexpectedResultException {
            return SimpleTypesGen.expectBoolean(execute(frame));
        }

        public Object[] executeIntArray(VirtualFrame frame) throws UnexpectedResultException {
            return SimpleTypesGen.expectObjectArray(execute(frame));
        }

        public BigInteger executeBigInteger(VirtualFrame frame) throws UnexpectedResultException {
            return SimpleTypesGen.expectBigInteger(execute(frame));
        }

        public BExtendsAbstract executeBExtendsAbstract(VirtualFrame frame) throws UnexpectedResultException {
            return SimpleTypesGen.expectBExtendsAbstract(execute(frame));
        }

        public CExtendsAbstract executeCExtendsAbstract(VirtualFrame frame) throws UnexpectedResultException {
            return SimpleTypesGen.expectCExtendsAbstract(execute(frame));
        }

        public Abstract executeAbstract(VirtualFrame frame) throws UnexpectedResultException {
            return SimpleTypesGen.expectAbstract(execute(frame));
        }

        public double executeDouble(VirtualFrame frame) throws UnexpectedResultException {
            return SimpleTypesGen.expectDouble(execute(frame));
        }

        public Interface executeInterface(VirtualFrame frame) throws UnexpectedResultException {
            return SimpleTypesGen.expectInterface(execute(frame));
        }

        public Object execute(@SuppressWarnings("unused") VirtualFrame frame) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ValueNode copy() {
            return (ValueNode) super.copy();
        }
    }

    @NodeChild(value = "children", type = ValueNode[].class)
    @GenerateNodeFactory
    public abstract static class ChildrenNode extends ValueNode {

    }

    @TypeSystemReference(SimpleTypes.class)
    public static class TestRootNode<E extends ValueNode> extends RootNode {

        @Child private E node;

        public TestRootNode(E node) {
            super(null);
            this.node = node;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return node.execute(frame);
        }

        public E getNode() {
            return node;
        }
    }

    public static class ArgumentNode extends ValueNode {

        private int invocationCount;
        final int index;

        public ArgumentNode(int index) {
            this.index = index;
        }

        public int getInvocationCount() {
            return invocationCount;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            invocationCount++;
            return frame.getArguments()[index];
        }

        @Override
        public int executeInt(VirtualFrame frame) throws UnexpectedResultException {
            invocationCount++;
            // avoid casts for some tests
            Object o = frame.getArguments()[index];
            if (o instanceof Integer) {
                return (int) o;
            }
            throw new UnexpectedResultException(o);
        }

    }

    abstract static class Abstract {
    }

    static final class BExtendsAbstract extends Abstract {

        static final BExtendsAbstract INSTANCE = new BExtendsAbstract();

    }

    static final class CExtendsAbstract extends Abstract {

        static final CExtendsAbstract INSTANCE = new CExtendsAbstract();
    }

    interface Interface {
    }
}
