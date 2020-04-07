/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates.
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
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.llvm.tests.interop.values.NativeValue;
import com.oracle.truffle.llvm.tests.interop.values.NullValue;
import com.oracle.truffle.tck.TruffleRunner;
import com.oracle.truffle.tck.TruffleRunner.Inject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.function.LongBinaryOperator;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.junit.runners.Parameterized.UseParametersRunnerFactory;

@RunWith(Parameterized.class)
@UseParametersRunnerFactory(TruffleRunner.ParametersFactory.class)
public final class PointerArithmeticTest extends InteropTestBase {

    static TruffleObject testLibrary;

    @BeforeClass
    public static void loadLibrary() {
        testLibrary = loadTestBitcodeInternal("pointerArithmetic.c");
    }

    private static void addTest(ArrayList<Object[]> tests, String method, LongBinaryOperator op) {
        tests.add(new Object[]{method, 15L, 37L, op.applyAsLong(15, 37)});
        tests.add(new Object[]{method, new NullValue(), 42L, op.applyAsLong(0, 42)});
        tests.add(new Object[]{method, 42L, new NullValue(), op.applyAsLong(42, 0)});
        tests.add(new Object[]{method, new NativeValue(81), 17L, op.applyAsLong(81, 17)});
        tests.add(new Object[]{method, 18L, new NativeValue(27), op.applyAsLong(18, 27)});
    }

    @Parameters(name = "{0}({1},{2})")
    public static Collection<Object[]> data() {
        ArrayList<Object[]> tests = new ArrayList<>();
        addTest(tests, "test_pointer_add", (a, b) -> a + b);
        addTest(tests, "test_pointer_sub", (a, b) -> a - b);
        addTest(tests, "test_pointer_mul", (a, b) -> a * b);
        addTest(tests, "test_pointer_xor", (a, b) -> a ^ b);
        return tests;
    }

    @Parameter(0) public String name;
    @Parameter(1) public Object a;
    @Parameter(2) public Object b;
    @Parameter(3) public long expected;

    public class PointerArithmeticNode extends SulongTestNode {

        public PointerArithmeticNode() {
            super(testLibrary, name);
        }
    }

    @Test
    public void test(@Inject(PointerArithmeticNode.class) CallTarget function) {
        Object ret = function.call(a, b);
        try {
            InteropLibrary lib = InteropLibrary.getFactory().getUncached(ret);
            lib.toNative(ret);
            Assert.assertEquals("ret", expected, lib.asPointer(ret));
        } catch (UnsupportedMessageException ex) {
            throw new AssertionError("ret is not a pointer", ex);
        }
    }
}
