/*
 * Copyright (c) 2021, Oracle and/or its affiliates.
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
import java.util.ArrayList;
import java.util.Collection;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.tck.TruffleRunner;
import com.oracle.truffle.tck.TruffleRunner.Inject;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

@RunWith(Parameterized.class)
@Parameterized.UseParametersRunnerFactory(TruffleRunner.ParametersFactory.class)
public final class BufferInteropTest extends InteropTestBase {

    private static final int BUFFER_SIZE = 32;

    private static Object testLibrary;

    @BeforeClass
    public static void loadTestBitcode() {
        testLibrary = loadTestBitcodeInternal("bufferInterop.c");
    }

    private static void addTypedTests(ArrayList<Object[]> tests, String type, Object value, long valueAsLong, int valueSize) {
        byte[] valueAsBytes = new byte[8];
        ByteBuffer.wrap(valueAsBytes).order(ByteOrder.nativeOrder()).putLong(valueAsLong);
        valueAsBytes = Arrays.copyOf(valueAsBytes, valueSize);

        // valid tests
        tests.add(new Object[]{type, value, 0, valueAsBytes}); // offset 0
        tests.add(new Object[]{type, value, 8, valueAsBytes}); // offset 8 (aligned)
        tests.add(new Object[]{type, value, 7, valueAsBytes}); // offset 7 (unaligned)
        tests.add(new Object[]{type, value, BUFFER_SIZE - valueSize, valueAsBytes}); // in bounds

        // expected failures
        tests.add(new Object[]{type, value, -1, valueAsBytes}); // negative offset
        tests.add(new Object[]{type, value, BUFFER_SIZE + 20, valueAsBytes}); // out of bounds
        tests.add(new Object[]{type, value, BUFFER_SIZE - 1, valueAsBytes}); // partially OOB
    }

    @Parameters(name = "{0},{2}")
    public static Collection<Object[]> data() {
        ArrayList<Object[]> tests = new ArrayList<>();
        addTypedTests(tests, "B", (byte) 42, 42, 1);
        addTypedTests(tests, "S", (short) 0x2442, 0x2442, 2);
        addTypedTests(tests, "I", 0x12345678, 0x12345678, 4);
        addTypedTests(tests, "L", 0x1234567887654321L, 0x1234567887654321L, 8);
        addTypedTests(tests, "F", 40.2f, Float.floatToRawIntBits(40.2f), 4);
        addTypedTests(tests, "D", 40.2, Double.doubleToRawLongBits(40.2), 8);
        return tests;
    }

    @Parameter(0) public String type;
    @Parameter(1) public Object value;
    @Parameter(2) public int offset;

    @Parameter(3) public byte[] valueAsBytes;

    @Rule public ExpectedException exception = ExpectedException.none();

    public class ReadBufferNode extends SulongTestNode {

        public ReadBufferNode() {
            super(testLibrary, "readBuffer" + type);
        }
    }

    private static byte[] createEmptyArray() {
        byte[] arr = new byte[32];
        Arrays.fill(arr, (byte) 0xcc);
        return arr;
    }

    private byte[] createTestArray() {
        byte[] arr = createEmptyArray();

        for (int i = offset, j = 0; j < valueAsBytes.length; i++, j++) {
            if (i >= 0 && i < arr.length) {
                arr[i] = valueAsBytes[j];
            }
        }

        return arr;
    }

    @Test
    public void testReadBuffer(@Inject(ReadBufferNode.class) CallTarget readBuffer) {
        byte[] arr = createTestArray();

        if (offset < 0 || (offset + valueAsBytes.length) > BUFFER_SIZE) {
            exception.expectMessage(String.format("Out-of-bounds buffer access (offset %d, length %d)", offset, valueAsBytes.length));
        }

        Object buffer = runWithPolyglot.getTruffleTestEnv().asGuestValue(ByteBuffer.wrap(arr));
        Object ret = readBuffer.call(buffer, offset);
        Assert.assertEquals("ret", value, ret);
    }

    public class WriteBufferNode extends SulongTestNode {

        public WriteBufferNode() {
            super(testLibrary, "writeBuffer" + type);
        }
    }

    @Test
    public void testWriteBuffer(@Inject(WriteBufferNode.class) CallTarget writeBuffer) {
        byte[] arr = createEmptyArray();

        if (offset < 0 || (offset + valueAsBytes.length) > BUFFER_SIZE) {
            exception.expectMessage(String.format("Out-of-bounds buffer access (offset %d, length %d)", offset, valueAsBytes.length));
        }

        Object buffer = runWithPolyglot.getTruffleTestEnv().asGuestValue(ByteBuffer.wrap(arr));
        writeBuffer.call(buffer, offset, value);

        byte[] expected = createTestArray();
        Assert.assertArrayEquals(expected, arr);
    }
}
