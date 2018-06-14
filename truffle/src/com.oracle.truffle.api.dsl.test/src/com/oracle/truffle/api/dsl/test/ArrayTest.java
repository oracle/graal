/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
