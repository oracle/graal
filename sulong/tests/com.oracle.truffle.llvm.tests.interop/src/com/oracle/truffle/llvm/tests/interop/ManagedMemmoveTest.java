/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.tests.interop;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.llvm.tests.interop.values.DoubleArrayObject;
import com.oracle.truffle.llvm.tests.interop.values.LongArrayObject;
import com.oracle.truffle.tck.TruffleRunner;
import com.oracle.truffle.tck.TruffleRunner.Inject;
import java.util.ArrayList;
import java.util.Collection;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
@Parameterized.UseParametersRunnerFactory(TruffleRunner.ParametersFactory.class)
public final class ManagedMemmoveTest extends ManagedMemAccessTestBase {

    @Parameters(name = "{1}->{0}")
    public static Collection<Object[]> data() {
        ArrayList<Object[]> tests = new ArrayList<>();
        for (int i = 0; i < TestType.values().length; i++) {
            for (int j = 0; j < TestType.values().length; j++) {
                tests.add(new Object[]{TestType.values()[i], TestType.values()[j]});
            }
        }
        return tests;
    }

    @Parameter(0) public TestType dstTestType;
    @Parameter(1) public TestType srcTestType;

    public static class DoMemmoveNode extends SulongTestNode {

        public DoMemmoveNode() {
            super(testLibrary, "do_memmove");
        }
    }

    @Test
    public void memmove(@Inject(DoMemmoveNode.class) CallTarget doMemmove) {
        Object srcType = getTypeID(srcTestType);
        Object dstType = getTypeID(dstTestType);

        final int arrayLength = 8;
        int size = arrayLength * srcTestType.elementSize;

        Object srcArray;
        Object srcObject;
        if (srcTestType.isFloating) {
            // float or double
            double[] src = new double[arrayLength];
            for (int i = 0; i < arrayLength; i++) {
                src[i] = i;
            }
            srcArray = src;
            srcObject = new DoubleArrayObject(srcType, src);
        } else {
            // integer
            long[] src = new long[arrayLength];
            for (int i = 0; i < arrayLength; i++) {
                src[i] = i;
            }
            srcArray = src;
            srcObject = new LongArrayObject(srcType, src);
        }

        byte[] srcBytes = serialize(srcTestType, srcArray);

        Object dstArray;
        Object dstObject;
        if (dstTestType.isFloating) {
            // float or double
            double[] dst = new double[size / dstTestType.elementSize];
            dstArray = dst;
            dstObject = new DoubleArrayObject(dstType, dst);
        } else {
            // integer
            long[] dst = new long[size / dstTestType.elementSize];
            dstArray = dst;
            dstObject = new LongArrayObject(dstType, dst);
        }

        doMemmove.call(dstObject, srcObject, size);

        byte[] dstBytes = serialize(dstTestType, dstArray);
        Assert.assertArrayEquals(srcBytes, dstBytes);
    }
}
