/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

import java.math.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.source.*;

public class TypeSystemTest {

    @TypeSystem({int.class, double.class, boolean.class, BigInteger.class, String.class, CallTarget.class, BExtendsAbstract.class, CExtendsAbstract.class, Abstract.class, Interface.class,
                    Object[].class})
    static class SimpleTypes {

        static int intCheck;
        static int intCast;

        @TypeCheck
        public boolean isInteger(Object value) {
            intCheck++;
            return value instanceof Integer;
        }

        @TypeCast
        public int asInteger(Object value) {
            intCast++;
            return (int) value;
        }

        @ImplicitCast
        public double castDouble(int value) {
            return value;
        }

        @ImplicitCast
        public BigInteger castBigInteger(int value) {
            return BigInteger.valueOf(value);
        }

    }

    @TypeSystemReference(SimpleTypes.class)
    public static class ValueNode extends Node {

        public ValueNode() {
            super(null);
        }

        public ValueNode(SourceSection sourceSection) {
            super(sourceSection);
        }

        public int executeInt(VirtualFrame frame) throws UnexpectedResultException {
            return SimpleTypesGen.SIMPLETYPES.expectInteger(execute(frame));
        }

        public String executeString(VirtualFrame frame) throws UnexpectedResultException {
            return SimpleTypesGen.SIMPLETYPES.expectString(execute(frame));
        }

        public boolean executeBoolean(VirtualFrame frame) throws UnexpectedResultException {
            return SimpleTypesGen.SIMPLETYPES.expectBoolean(execute(frame));
        }

        public Object[] executeIntArray(VirtualFrame frame) throws UnexpectedResultException {
            return SimpleTypesGen.SIMPLETYPES.expectObjectArray(execute(frame));
        }

        public BigInteger executeBigInteger(VirtualFrame frame) throws UnexpectedResultException {
            return SimpleTypesGen.SIMPLETYPES.expectBigInteger(execute(frame));
        }

        public BExtendsAbstract executeBExtendsAbstract(VirtualFrame frame) throws UnexpectedResultException {
            return SimpleTypesGen.SIMPLETYPES.expectBExtendsAbstract(execute(frame));
        }

        public CExtendsAbstract executeCExtendsAbstract(VirtualFrame frame) throws UnexpectedResultException {
            return SimpleTypesGen.SIMPLETYPES.expectCExtendsAbstract(execute(frame));
        }

        public Abstract executeAbstract(VirtualFrame frame) throws UnexpectedResultException {
            return SimpleTypesGen.SIMPLETYPES.expectAbstract(execute(frame));
        }

        public double executeDouble(VirtualFrame frame) throws UnexpectedResultException {
            return SimpleTypesGen.SIMPLETYPES.expectDouble(execute(frame));
        }

        public Interface executeInterface(VirtualFrame frame) throws UnexpectedResultException {
            return SimpleTypesGen.SIMPLETYPES.expectInterface(execute(frame));
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
