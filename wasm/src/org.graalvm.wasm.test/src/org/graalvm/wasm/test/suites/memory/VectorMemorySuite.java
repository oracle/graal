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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.ByteSequence;
import org.graalvm.wasm.WasmContext;
import org.graalvm.wasm.test.WasmTestUtils;
import org.graalvm.wasm.exception.WasmException;
import org.graalvm.wasm.memory.ByteArrayWasmMemory;
import org.graalvm.wasm.memory.NativeWasmMemory;
import org.graalvm.wasm.memory.UnsafeWasmMemory;
import org.graalvm.wasm.memory.WasmMemory;
import org.graalvm.wasm.memory.WasmMemoryFactory;
import org.graalvm.wasm.memory.WasmMemoryLibrary;
import org.graalvm.wasm.vector.Vector128Ops;
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class VectorMemorySuite {
    private static final int PAGE_SIZE = 65536;
    private static final WasmMemoryLibrary MEMORY = WasmMemoryLibrary.getUncached();
    private static final byte[] VECTOR = new byte[]{0, 1, 2, 3, 4, 5, 6, 7, (byte) 0x80, (byte) 0xff, 10, 11, 12, 13, 14, 15};

    @Parameter(0) public Class<? extends WasmMemory> implementation;
    @Parameter(1) public boolean memory64;
    @Parameter(2) public boolean shared;

    @Parameters(name = "{0}, memory64={1}, shared={2}")
    public static Object[][] data() {
        return new Object[][]{
                        {ByteArrayWasmMemory.class, false, false},
                        {UnsafeWasmMemory.class, false, false},
                        {UnsafeWasmMemory.class, false, true},
                        {UnsafeWasmMemory.class, true, true},
                        {NativeWasmMemory.class, false, false},
                        {NativeWasmMemory.class, true, false}
        };
    }

    private Context context;
    private final List<WasmMemory> memories = new ArrayList<>();

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

    private WasmMemory createMemory(long min) {
        boolean nativeMemory = implementation == NativeWasmMemory.class;
        boolean unsafe = implementation != ByteArrayWasmMemory.class;
        WasmMemory memory = WasmMemoryFactory.createMemory(min, nativeMemory ? 65536 : 3, memory64, shared, unsafe,
                        implementation == UnsafeWasmMemory.class, WasmContext.get(null));
        Assert.assertSame(implementation, memory.getClass());
        memories.add(memory);
        return memory;
    }

    private static byte[] contents(WasmMemory memory) {
        byte[] result = new byte[Math.toIntExact(MEMORY.byteSize(memory))];
        MEMORY.copyToBuffer(memory, null, result, 0, 0, result.length);
        return result;
    }

    private static byte[] vectorBytes(Object vector) {
        return Vector128Ops.SINGLETON_IMPLEMENTATION.toArray(Vector128Ops.cast(vector));
    }

    private static Object vector() {
        return Vector128Ops.SINGLETON_IMPLEMENTATION.fromArray(VECTOR);
    }

    @Test
    public void loadsAtAllAlignmentsAndPageEnd() {
        WasmMemory memory = createMemory(1);
        byte[] bytes = new byte[PAGE_SIZE];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) (i * 37 + 11);
        }
        MEMORY.initialize(memory, null, bytes, 0, 0, bytes.length);
        for (int alignment = 0; alignment < 16; alignment++) {
            for (int address : new int[]{alignment, 4090 + alignment, PAGE_SIZE - 31 + alignment}) {
                Assert.assertArrayEquals(Arrays.copyOfRange(bytes, address, address + 16), vectorBytes(MEMORY.load_i128(memory, null, address)));
            }
        }
    }

    @Test
    public void storesAtAllAlignmentsAndPageEnd() {
        WasmMemory memory = createMemory(1);
        byte[] bytes = new byte[PAGE_SIZE];
        Object vector = vector();
        for (int alignment = 0; alignment < 16; alignment++) {
            for (int address : new int[]{alignment, 4090 + alignment, PAGE_SIZE - 31 + alignment}) {
                Arrays.fill(bytes, (byte) 0xa5);
                MEMORY.initialize(memory, null, bytes, 0, 0, bytes.length);
                System.arraycopy(VECTOR, 0, bytes, address, VECTOR.length);
                MEMORY.store_i128(memory, null, address, vector);
                Assert.assertArrayEquals(bytes, contents(memory));
            }
        }
    }

    @Test
    public void outOfBoundsDoesNotPartiallyStore() {
        WasmMemory memory = createMemory(1);
        byte[] before = contents(memory);
        Object vector = vector();
        for (long address : new long[]{-1, Long.MIN_VALUE, Long.MAX_VALUE, PAGE_SIZE - 15, PAGE_SIZE - 1, PAGE_SIZE}) {
            Assert.assertThrows(WasmException.class, () -> MEMORY.load_i128(memory, null, address));
            Assert.assertThrows(WasmException.class, () -> MEMORY.store_i128(memory, null, address, vector));
            Assert.assertArrayEquals(before, contents(memory));
        }
        WasmMemory empty = createMemory(0);
        Assert.assertThrows(WasmException.class, () -> MEMORY.load_i128(empty, null, 0));
        Assert.assertThrows(WasmException.class, () -> MEMORY.store_i128(empty, null, 0, vector));
    }

    @Test
    public void vectorIsASnapshot() {
        WasmMemory memory = createMemory(1);
        MEMORY.store_i128(memory, null, 1, vector());
        Object loaded = MEMORY.load_i128(memory, null, 1);
        for (int i = 0; i < 16; i++) {
            MEMORY.store_i32_8(memory, null, 1 + i, (byte) 0);
        }
        Assert.assertArrayEquals(VECTOR, vectorBytes(loaded));
        MEMORY.store_i128(memory, null, 33, loaded);
        Assert.assertArrayEquals(VECTOR, Arrays.copyOfRange(contents(memory), 33, 49));
    }

    // memory32, shared memory32, memory64 and shared memory64; each exports a v128 copy.
    private static final String[] COPY_MODULES = {
                    "0061736d0100000001060160027f7f00030201000506010101808004071102066d656d6f7279020004636f707900000a10010e0020002001fd000400fd0b04000b",
                    "0061736d0100000001060160027f7f0003020100050401030103071102066d656d6f7279020004636f707900000a10010e0020002001fd000400fd0b04000b",
                    "0061736d0100000001060160027e7e00030201000506010501808004071102066d656d6f7279020004636f707900000a10010e0020002001fd000400fd0b04000b",
                    "0061736d0100000001060160027e7e0003020100050401070103071102066d656d6f7279020004636f707900000a10010e0020002001fd000400fd0b04000b"
    };

    @Test
    public void compiledLoadsAndStores() throws IOException {
        Assume.assumeTrue("Optimizing runtime is not available", Engine.supportsCompilation());
        byte[] binary = WasmTestUtils.hexStringToByteArray(COPY_MODULES[(memory64 ? 2 : 0) + (shared ? 1 : 0)]);
        Source source = Source.newBuilder("wasm", ByteSequence.create(binary), "v128-copy").build();
        Context.Builder builder = Context.newBuilder("wasm").allowExperimentalOptions(true);
        builder.option("wasm.UseUnsafeMemory", Boolean.toString(implementation != ByteArrayWasmMemory.class));
        builder.option("wasm.DirectByteBufferMemoryAccess", Boolean.toString(implementation == UnsafeWasmMemory.class));
        builder.option("wasm.Memory64", Boolean.toString(memory64));
        builder.option("wasm.Threads", Boolean.toString(shared));
        builder.option("engine.BackgroundCompilation", "false");
        builder.option("engine.CompileImmediately", "true");
        builder.option("engine.CompilationFailureAction", "Throw");
        try (Context compiled = builder.build()) {
            compiled.enter();
            try {
                Value exports = compiled.eval(source).newInstance().getMember("exports");
                WasmMemory memory = (WasmMemory) WasmContext.get(null).environment().asGuestValue(exports.getMember("memory"));
                Assert.assertSame(implementation, memory.getClass());
                MEMORY.initialize(memory, null, VECTOR, 0, 128, VECTOR.length);
                Value copy = exports.getMember("copy");
                for (int address = 0; address < 16; address++) {
                    copy.executeVoid(pointer(address), pointer(128));
                    Assert.assertArrayEquals(VECTOR, vectorBytes(MEMORY.load_i128(memory, null, address)));
                }
                Assert.assertEquals(1, MEMORY.grow(memory, 1));
                copy.executeVoid(pointer(PAGE_SIZE - 7), pointer(128));
                Assert.assertArrayEquals(VECTOR, vectorBytes(MEMORY.load_i128(memory, null, PAGE_SIZE - 7)));
                byte[] before = contents(memory);
                Assert.assertThrows(org.graalvm.polyglot.PolyglotException.class, () -> copy.executeVoid(pointer(2 * PAGE_SIZE - 15), pointer(128)));
                Assert.assertArrayEquals(before, contents(memory));
            } finally {
                compiled.leave();
            }
        }
    }

    private Object pointer(long address) {
        if (memory64) {
            return address;
        }
        return Math.toIntExact(address);
    }

    @Test
    public void growResetAndFree() {
        WasmMemory memory = createMemory(1);
        Object vector = vector();
        MEMORY.store_i128(memory, null, PAGE_SIZE - 16, vector);
        Assert.assertEquals(1, MEMORY.grow(memory, 1));
        Assert.assertArrayEquals(VECTOR, vectorBytes(MEMORY.load_i128(memory, null, PAGE_SIZE - 16)));
        MEMORY.store_i128(memory, null, PAGE_SIZE - 7, vector);
        Assert.assertArrayEquals(VECTOR, vectorBytes(MEMORY.load_i128(memory, null, PAGE_SIZE - 7)));
        MEMORY.reset(memory);
        Assert.assertEquals(1, MEMORY.size(memory));
        Assert.assertArrayEquals(new byte[16], vectorBytes(MEMORY.load_i128(memory, null, PAGE_SIZE - 16)));
        Assert.assertThrows(WasmException.class, () -> MEMORY.store_i128(memory, null, PAGE_SIZE - 7, vector));
        memories.remove(memory);
        MEMORY.close(memory);
        if (implementation != ByteArrayWasmMemory.class) {
            Assert.assertThrows(WasmException.class, () -> MEMORY.load_i128(memory, null, 0));
            Assert.assertThrows(WasmException.class, () -> MEMORY.store_i128(memory, null, 0, vector));
        }
    }
}
