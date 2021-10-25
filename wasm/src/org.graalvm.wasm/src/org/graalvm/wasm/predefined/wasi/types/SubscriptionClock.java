/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

/*
 * This file has been automatically generated from wasi_snapshot_preview1.witx.
 */

package org.graalvm.wasm.predefined.wasi.types;

import com.oracle.truffle.api.nodes.Node;
import org.graalvm.wasm.memory.WasmMemory;

/** The contents of a {@code subscription} when type is {@code eventtype::clock}. */
public final class SubscriptionClock {

    /** Static methods only; don't let anyone instantiate this class. */
    private SubscriptionClock() {
    }

    /** Size of this structure, in bytes. */
    public static final int BYTES = 32;

    /** Reads the clock against which to compare the timestamp. */
    public static Clockid readId(Node node, WasmMemory memory, int address) {
        return Clockid.fromValue(memory.load_i32(node, address + 0));
    }

    /** Writes the clock against which to compare the timestamp. */
    public static void writeId(Node node, WasmMemory memory, int address, Clockid value) {
        memory.store_i32(node, address + 0, value.toValue());
    }

    /** Reads the absolute or relative timestamp. */
    public static long readTimeout(Node node, WasmMemory memory, int address) {
        return memory.load_i64(node, address + 8);
    }

    /** Writes the absolute or relative timestamp. */
    public static void writeTimeout(Node node, WasmMemory memory, int address, long value) {
        memory.store_i64(node, address + 8, value);
    }

    /**
     * Reads the amount of time that the implementation may wait additionally to coalesce with other
     * events.
     */
    public static long readPrecision(Node node, WasmMemory memory, int address) {
        return memory.load_i64(node, address + 16);
    }

    /**
     * Writes the amount of time that the implementation may wait additionally to coalesce with
     * other events.
     */
    public static void writePrecision(Node node, WasmMemory memory, int address, long value) {
        memory.store_i64(node, address + 16, value);
    }

    /** Reads flags specifying whether the timeout is absolute or relative. */
    public static short readFlags(Node node, WasmMemory memory, int address) {
        return (short) memory.load_i32_16u(node, address + 24);
    }

    /** Writes flags specifying whether the timeout is absolute or relative. */
    public static void writeFlags(Node node, WasmMemory memory, int address, short value) {
        memory.store_i32_16(node, address + 24, value);
    }

}
