/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.ImplicitCast;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeCast;
import com.oracle.truffle.api.dsl.TypeCheck;
import com.oracle.truffle.api.dsl.TypeSystem;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.dsl.test.ArrayTestFactory.TestNode1NodeGen;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

public class ArrayTest {

    @Test
    public void testNode1() {
        final TestNode1 node = TestNode1NodeGen.create(null);
        RootNode root = new RootNode(null) {
            @Child TestNode1 test = node;

            @Override
            public Object execute(VirtualFrame frame) {
                return test.executeWith(frame, frame.getArguments()[0]);
            }
        };
        CallTarget target = Truffle.getRuntime().createCallTarget(root);

        Assert.assertEquals(1, (int) target.call(1));
        Assert.assertArrayEquals(new double[0], (double[]) target.call(new int[0]), 0.0d);
        Assert.assertArrayEquals(new double[0], (double[]) target.call(new double[0]), 0.0d);
        Assert.assertArrayEquals(new String[0], (String[]) target.call((Object) new String[0]));
    }

    @TypeSystemReference(ArrayTypeSystem.class)
    abstract static class BaseNode extends Node {

        abstract Object execute(VirtualFrame frame);

        int executeInt(VirtualFrame frame) throws UnexpectedResultException {
            return ArrayTypeSystemGen.expectInteger(execute(frame));
        }

        int[] executeIntArray(VirtualFrame frame) throws UnexpectedResultException {
            return ArrayTypeSystemGen.expectIntArray(execute(frame));
        }

        String[] executeStringArray(VirtualFrame frame) throws UnexpectedResultException {
            return ArrayTypeSystemGen.expectStringArray(execute(frame));
        }

        double[] executeDoubleArray(VirtualFrame frame) throws UnexpectedResultException {
            return ArrayTypeSystemGen.expectDoubleArray(execute(frame));
        }
    }

    @NodeChild
    abstract static class TestNode1 extends BaseNode {

        abstract Object executeWith(VirtualFrame frame, Object operand);

        @Specialization
        int doInt(int value) {
            return value;
        }

        @Specialization
        double[] doDoubleArray(double[] value) {
            return value;
        }

        @Specialization
        String[] doStringArray(String[] value) {
            return value;
        }

    }

    @TypeSystem({int.class, int[].class, double[].class, String[].class, Object[].class})
    public static class ArrayTypeSystem {

        @ImplicitCast
        public static double[] castFromInt(int[] array) {
            double[] newArray = new double[array.length];
            for (int i = 0; i < array.length; i++) {
                newArray[i] = array[i];
            }
            return newArray;
        }

        @TypeCheck(int[].class)
        public static boolean isIntArray2(Object array) {
            return array instanceof int[];
        }

        @TypeCast(int[].class)
        public static int[] asIntArray(Object array) {
            return (int[]) array;
        }

    }

}
