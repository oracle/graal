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

import java.util.ArrayList;
import java.util.List;

import org.graalvm.polyglot.Context;
import org.graalvm.wasm.WasmContext;
import org.graalvm.wasm.constants.Sizes;
import org.graalvm.wasm.memory.ByteArrayWasmMemory;
import org.graalvm.wasm.memory.NativeWasmMemory;
import org.graalvm.wasm.memory.UnsafeWasmMemory;
import org.graalvm.wasm.memory.WasmMemory;
import org.graalvm.wasm.memory.WasmMemoryFactory;
import org.graalvm.wasm.memory.WasmMemoryLibrary;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class MemoryCopyFromMixedSuite {
    private static final WasmMemoryLibrary MEMORY = WasmMemoryLibrary.getUncached();
    private static final byte[] PAYLOAD = new byte[]{0x11, 0x22, 0x33, 0x44, 0x55};

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

    private WasmMemory createMemory(boolean shared, boolean unsafeMemory, boolean nativeMemory) {
        boolean directByteBufferMemoryAccess = unsafeMemory && !nativeMemory && shared;
        long declaredMaxSize = nativeMemory ? Sizes.MAX_MEMORY_DECLARATION_SIZE : 1;
        WasmMemory memory = WasmMemoryFactory.createMemory(1, declaredMaxSize, false, shared, unsafeMemory, directByteBufferMemoryAccess, WasmContext.get(null));
        memories.add(memory);
        return memory;
    }

    private static void writePayload(WasmMemory memory, long offset) {
        MEMORY.initialize(memory, null, PAYLOAD, 0, offset, PAYLOAD.length);
    }

    private static void assertCopied(WasmMemory destination, long destinationOffset) {
        byte[] actual = new byte[PAYLOAD.length];
        MEMORY.copyToBuffer(destination, null, actual, destinationOffset, 0, PAYLOAD.length);
        Assert.assertArrayEquals(PAYLOAD, actual);
    }

    private static void copyAndCheck(WasmMemory destination, WasmMemory source) {
        writePayload(source, 8);
        MEMORY.copyFrom(destination, null, source, 8, 16, PAYLOAD.length);
        assertCopied(destination, 16);
    }

    @Test
    public void testCopyFromSharedToNonShared() {
        WasmMemory destination = createMemory(false, false, false);
        WasmMemory source = createMemory(true, true, false);
        Assert.assertSame(ByteArrayWasmMemory.class, destination.getClass());
        Assert.assertSame(UnsafeWasmMemory.class, source.getClass());
        copyAndCheck(destination, source);
    }

    @Test
    public void testCopyFromNonSharedToNative() {
        WasmMemory destination = createMemory(false, true, true);
        WasmMemory source = createMemory(false, false, false);
        Assert.assertSame(NativeWasmMemory.class, destination.getClass());
        Assert.assertSame(ByteArrayWasmMemory.class, source.getClass());
        copyAndCheck(destination, source);
    }
}
