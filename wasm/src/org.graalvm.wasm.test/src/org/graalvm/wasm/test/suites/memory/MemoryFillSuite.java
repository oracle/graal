/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.wasm.test.suites.memory;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.graalvm.polyglot.Context;
import org.graalvm.wasm.WasmContext;
import org.graalvm.wasm.constants.Sizes;
import org.graalvm.wasm.exception.WasmException;
import org.graalvm.wasm.memory.NativeWasmMemory;
import org.graalvm.wasm.memory.UnsafeWasmMemory;
import org.graalvm.wasm.memory.WasmMemory;
import org.graalvm.wasm.memory.WasmMemoryFactory;
import org.graalvm.wasm.memory.WasmMemoryLibrary;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MemoryFillSuite {
    private static final int PAGE_SIZE = 65536;
    private static final byte SENTINEL = (byte) 0xa5;
    private static final WasmMemoryLibrary MEMORY = WasmMemoryLibrary.getUncached();

    @Parameter(0) public Class<? extends WasmMemory> memoryImplementation;
    @Parameter(1) public String memoryImplementationName;

    private Context context;
    private final List<WasmMemory> memories = new ArrayList<>();

    @Parameters(name = "{1}")
    public static Object[][] data() {
        Class<?>[] implementations = WasmMemory.class.getPermittedSubclasses();
        Object[][] parameters = new Object[implementations.length][2];
        for (int i = 0; i < implementations.length; i++) {
            parameters[i] = new Object[]{implementations[i], implementations[i].getSimpleName()};
        }
        return parameters;
    }

    @Before
    public void setUp() {
        context = Context.create("wasm");
        context.initialize("wasm");
        context.enter();
    }

    @After
    public void tearDown() {
        try {
            for (WasmMemory memory : memories) {
                MEMORY.close(memory);
            }
        } finally {
            context.leave();
            context.close();
        }
    }

    private WasmMemory createMemory() {
        return createMemory(1, 1);
    }

    private WasmMemory createMemory(long minSize, long maxSize) {
        boolean nativeMemory = memoryImplementation == NativeWasmMemory.class;
        boolean unsafeMemory = nativeMemory || memoryImplementation == UnsafeWasmMemory.class;
        boolean directByteBufferMemoryAccess = memoryImplementation == UnsafeWasmMemory.class;
        long declaredMaxSize = nativeMemory ? Sizes.MAX_MEMORY_DECLARATION_SIZE : maxSize;
        WasmMemory memory = WasmMemoryFactory.createMemory(minSize, declaredMaxSize, nativeMemory, false, unsafeMemory, directByteBufferMemoryAccess, WasmContext.get(null));
        Assert.assertSame(memoryImplementation, memory.getClass());
        memories.add(memory);
        return memory;
    }

    private static byte[] initializeWithSentinel(WasmMemory memory) {
        byte[] expected = new byte[Math.toIntExact(MEMORY.byteSize(memory))];
        Arrays.fill(expected, SENTINEL);
        MEMORY.initialize(memory, null, expected, 0, 0, expected.length);
        return expected;
    }

    private static byte[] contents(WasmMemory memory) {
        byte[] actual = new byte[Math.toIntExact(MEMORY.byteSize(memory))];
        MEMORY.copyToBuffer(memory, null, actual, 0, 0, actual.length);
        return actual;
    }

    private static byte[] backingArray(WasmMemory memory) {
        ByteBuffer buffer = MEMORY.asByteBuffer(memory);
        return buffer != null && buffer.hasArray() ? buffer.array() : null;
    }

    private static void checkFill(WasmMemory memory, int offset, int length, byte value) {
        byte[] expected = initializeWithSentinel(memory);
        Arrays.fill(expected, offset, offset + length, value);
        MEMORY.fill(memory, null, offset, length, value);
        Assert.assertArrayEquals("offset=" + offset + ", length=" + length + ", value=" + value, expected, contents(memory));
    }

    @Test
    public void testFillLengthsAndAlignments() {
        WasmMemory memory = createMemory();
        int[] lengths = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 31, 32, 33, 63, 64, 65, 127, 128, 129, 4095, 4096, 4097};
        byte[] values = {0, 1, 0x55, 0x7f, (byte) 0x80, (byte) 0xff};
        for (byte value : values) {
            for (int length : lengths) {
                for (int offset = 0; offset < 16; offset++) {
                    checkFill(memory, offset, length, value);
                }
                checkFill(memory, PAGE_SIZE - length, length, value);
            }
            checkFill(memory, 0, PAGE_SIZE, value);
        }
    }

    @Test
    public void testAllByteValues() {
        WasmMemory memory = createMemory();
        for (int value = 0; value < 256; value++) {
            for (int length : new int[]{17, 63, 64, 65, 129}) {
                checkFill(memory, 3, length, (byte) value);
            }
        }
    }

    @Test
    public void testFillAfterGrowAndReset() {
        WasmMemory memory = createMemory(1, 2);
        byte[] original = backingArray(memory);
        byte[] expectedOriginal = initializeWithSentinel(memory);

        Assert.assertEquals(1, MEMORY.grow(memory, 1));
        byte[] grown = backingArray(memory);
        if (original != null) {
            Assert.assertNotSame(original, grown);
        }
        checkFill(memory, PAGE_SIZE - 3, 129, (byte) 0xff);
        if (original != null) {
            Assert.assertArrayEquals(expectedOriginal, original);
        }

        byte[] expectedGrown = contents(memory);
        MEMORY.reset(memory);
        byte[] reset = backingArray(memory);
        if (grown != null) {
            Assert.assertNotSame(grown, reset);
        }
        Assert.assertEquals(PAGE_SIZE, MEMORY.byteSize(memory));
        checkFill(memory, 3, 129, (byte) 0x80);
        if (grown != null) {
            Assert.assertArrayEquals(expectedGrown, grown);
        }
    }

    @Test
    public void testOutOfBoundsDoesNotModifyMemory() {
        WasmMemory memory = createMemory();
        long[][] ranges = {
                        {-1, 0}, {-1, 8}, {-1, 64}, {PAGE_SIZE + 1, 0}, {PAGE_SIZE, 1}, {PAGE_SIZE - 8, 9}, {PAGE_SIZE - 64, 65},
                        {0, PAGE_SIZE + 1}, {0, -1}, {0, Long.MIN_VALUE}, {0, Long.MAX_VALUE},
                        {Long.MAX_VALUE, 8}, {Long.MIN_VALUE, 8}, {1L << 32, 8}, {0, 1L << 32},
        };
        for (long[] range : ranges) {
            byte[] expected = initializeWithSentinel(memory);
            try {
                MEMORY.fill(memory, null, range[0], range[1], (byte) 0);
                Assert.fail("Expected a trap for offset=" + range[0] + ", length=" + range[1]);
            } catch (WasmException e) {
                Assert.assertArrayEquals(expected, contents(memory));
            }
        }
    }
}
