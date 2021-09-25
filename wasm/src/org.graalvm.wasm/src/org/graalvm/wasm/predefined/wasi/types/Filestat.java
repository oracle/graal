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

/** File attributes. */
public final class Filestat {

    /** Static methods only; don't let anyone instantiate this class. */
    private Filestat() {
    }

    /** Size of this structure, in bytes. */
    public static final int BYTES = 64;

    /** Reads device id of device containing the file. */
    public static long readDev(Node node, WasmMemory memory, int address) {
        return memory.load_i64(node, address + 0);
    }

    /** Writes device id of device containing the file. */
    public static void writeDev(Node node, WasmMemory memory, int address, long value) {
        memory.store_i64(node, address + 0, value);
    }

    /** Reads file serial number. */
    public static long readIno(Node node, WasmMemory memory, int address) {
        return memory.load_i64(node, address + 8);
    }

    /** Writes file serial number. */
    public static void writeIno(Node node, WasmMemory memory, int address, long value) {
        memory.store_i64(node, address + 8, value);
    }

    /** Reads file type. */
    public static Filetype readFiletype(Node node, WasmMemory memory, int address) {
        return Filetype.fromValue((byte) memory.load_i32_8u(node, address + 16));
    }

    /** Writes file type. */
    public static void writeFiletype(Node node, WasmMemory memory, int address, Filetype value) {
        memory.store_i32_8(node, address + 16, value.toValue());
    }

    /** Reads number of hard links to the file. */
    public static long readNlink(Node node, WasmMemory memory, int address) {
        return memory.load_i64(node, address + 24);
    }

    /** Writes number of hard links to the file. */
    public static void writeNlink(Node node, WasmMemory memory, int address, long value) {
        memory.store_i64(node, address + 24, value);
    }

    /**
     * Reads for regular files, the file size in bytes. for symbolic links, the length in bytes of
     * the pathname contained in the symbolic link.
     */
    public static long readSize(Node node, WasmMemory memory, int address) {
        return memory.load_i64(node, address + 32);
    }

    /**
     * Writes for regular files, the file size in bytes. for symbolic links, the length in bytes of
     * the pathname contained in the symbolic link.
     */
    public static void writeSize(Node node, WasmMemory memory, int address, long value) {
        memory.store_i64(node, address + 32, value);
    }

    /** Reads last data access timestamp. */
    public static long readAtim(Node node, WasmMemory memory, int address) {
        return memory.load_i64(node, address + 40);
    }

    /** Writes last data access timestamp. */
    public static void writeAtim(Node node, WasmMemory memory, int address, long value) {
        memory.store_i64(node, address + 40, value);
    }

    /** Reads last data modification timestamp. */
    public static long readMtim(Node node, WasmMemory memory, int address) {
        return memory.load_i64(node, address + 48);
    }

    /** Writes last data modification timestamp. */
    public static void writeMtim(Node node, WasmMemory memory, int address, long value) {
        memory.store_i64(node, address + 48, value);
    }

    /** Reads last file status change timestamp. */
    public static long readCtim(Node node, WasmMemory memory, int address) {
        return memory.load_i64(node, address + 56);
    }

    /** Writes last file status change timestamp. */
    public static void writeCtim(Node node, WasmMemory memory, int address, long value) {
        memory.store_i64(node, address + 56, value);
    }

}
