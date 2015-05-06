/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.lir.jtt;

import org.junit.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.lir.framemap.*;
import com.oracle.graal.lir.gen.*;

public class StackMoveTest extends LIRTest {
    private static final LIRTestSpecification stackCopy = new LIRTestSpecification() {
        @Override
        public void generate(LIRGeneratorTool gen, Value a) {
            FrameMapBuilder frameMapBuilder = gen.getResult().getFrameMapBuilder();
            // create slots
            StackSlotValue s1 = frameMapBuilder.allocateSpillSlot(a.getLIRKind());
            StackSlotValue s2 = frameMapBuilder.allocateSpillSlot(a.getLIRKind());
            // move stuff around
            gen.emitMove(s1, a);
            gen.append(gen.getSpillMoveFactory().createStackMove(s2, s1));
            setResult(gen.emitMove(s2));
        }
    };

    @SuppressWarnings("unused")
    @LIRIntrinsic
    public static int copyInt(LIRTestSpecification spec, int a) {
        return a;
    }

    public int testInt(int a) {
        return copyInt(stackCopy, a);
    }

    @Test
    public void runInt() throws Throwable {
        runTest("testInt", Integer.MIN_VALUE);
        runTest("testInt", -1);
        runTest("testInt", 0);
        runTest("testInt", 1);
        runTest("testInt", Integer.MAX_VALUE);
    }

    @SuppressWarnings("unused")
    @LIRIntrinsic
    public static long copyLong(LIRTestSpecification spec, long a) {
        return a;
    }

    public long testLong(long a) {
        return copyLong(stackCopy, a);
    }

    @Test
    public void runLong() throws Throwable {
        runTest("testLong", Long.MIN_VALUE);
        runTest("testLong", -1L);
        runTest("testLong", 0L);
        runTest("testLong", 1L);
        runTest("testLong", Long.MAX_VALUE);
    }

    @SuppressWarnings("unused")
    @LIRIntrinsic
    public static float copyFloat(LIRTestSpecification spec, float a) {
        return a;
    }

    public float testFloat(float a) {
        return copyFloat(stackCopy, a);
    }

    @Test
    public void runFloat() throws Throwable {
        runTest("testFloat", Float.MIN_VALUE);
        runTest("testFloat", -1f);
        runTest("testFloat", -0.1f);
        runTest("testFloat", 0f);
        runTest("testFloat", 0.1f);
        runTest("testFloat", 1f);
        runTest("testFloat", Float.MAX_VALUE);
    }

    @SuppressWarnings("unused")
    @LIRIntrinsic
    public static double copyDouble(LIRTestSpecification spec, double a) {
        return a;
    }

    public double testDouble(double a) {
        return copyDouble(stackCopy, a);
    }

    @Test
    public void runDouble() throws Throwable {
        runTest("testDouble", Double.MIN_VALUE);
        runTest("testDouble", -1.);
        runTest("testDouble", -0.1);
        runTest("testDouble", 0.);
        runTest("testDouble", 0.1);
        runTest("testDouble", 1.);
        runTest("testDouble", Double.MAX_VALUE);
    }

}
