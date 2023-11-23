/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.wasm.parser.bytecode;

import org.graalvm.wasm.api.Vector128;
import org.graalvm.wasm.collection.ByteArrayList;

/**
 * Class for generating bytecode data.
 */
public class BytecodeGen {
    private final ByteArrayList data;

    public BytecodeGen() {
        data = new ByteArrayList();
    }

    /**
     * Adds a values from another byte array to this bytecode.
     * 
     * @param src the source byte array
     * @param srcOffset the offset in the source array
     * @param length the number of values to copy
     */
    public void addBytes(byte[] src, int srcOffset, int length) {
        data.addRange(src, srcOffset, length);
    }

    /**
     * Adds a single byte to the bytecode.
     * 
     * @param value the byte value
     */
    public void add(byte value) {
        add1(value);
    }

    /**
     * Adds a single integer value as four bytes encoded in little endian to the bytecode.
     * 
     * @param value the integer value
     */
    public void add(int value) {
        add4(value);
    }

    /**
     * Allocates the given number of bytes in the custom data.
     *
     * @param size The number of bytes to allocate
     */
    public void allocate(int size) {
        data.allocate(size);
    }

    /**
     * Adds an integer value as a single byte to the bytecode. The caller has to guarantee that the
     * value fits into a byte.
     * 
     * @param value the integer value
     */
    protected void add1(int value) {
        data.add((byte) value);
    }

    /**
     * Adds a long value as a single byte to the bytecode. The caller has to guarantee that the
     * value fits into a byte.
     * 
     * @param value the long value
     */
    protected void add1(long value) {
        data.add((byte) value);
    }

    /**
     * Adds an integer value as two bytes in little endian to the bytecode. The caller has to
     * guarantee that the value fits into two bytes.
     * 
     * @param value the integer value
     */
    protected void add2(int value) {
        data.add((byte) (value & 0x0000_00FF));
        data.add((byte) ((value >>> 8) & 0x0000_00FF));
    }

    /**
     * Adds a long value as two bytes in little endian to the bytecode. The caller has to guarantee
     * that the value fits into two bytes.
     * 
     * @param value the long value
     */
    protected void add2(long value) {
        data.add((byte) (value & 0x0000_00FF));
        data.add((byte) ((value >>> 8) & 0x0000_00FF));
    }

    /**
     * Adds an integer value as four bytes in little endian to the bytecode.
     * 
     * @param value the integer value
     */
    protected void add4(int value) {
        data.add((byte) (value & 0x0000_00FF));
        data.add((byte) ((value >>> 8) & 0x0000_00FF));
        data.add((byte) ((value >>> 16) & 0x0000_00FF));
        data.add((byte) ((value >>> 24) & 0x0000_00FF));
    }

    /**
     * Adds a long value as four bytes in little endian to the bytecode. The caller has to guarantee
     * that the value fits into four bytes.
     * 
     * @param value the long value
     */
    protected void add4(long value) {
        data.add((byte) (value & 0x0000_00FF));
        data.add((byte) ((value >>> 8) & 0x0000_00FF));
        data.add((byte) ((value >>> 16) & 0x0000_00FF));
        data.add((byte) ((value >>> 24) & 0x0000_00FF));
    }

    /**
     * Adds a long value as eight bytes in little endian to the bytecode.
     * 
     * @param value the long value
     */
    protected void add8(long value) {
        data.add((byte) (value & 0x0000_00FF));
        data.add((byte) ((value >>> 8) & 0x0000_00FF));
        data.add((byte) ((value >>> 16) & 0x0000_00FF));
        data.add((byte) ((value >>> 24) & 0x0000_00FF));
        data.add((byte) ((value >>> 32) & 0x0000_00FF));
        data.add((byte) ((value >>> 40) & 0x0000_00FF));
        data.add((byte) ((value >>> 48) & 0x0000_00FF));
        data.add((byte) ((value >>> 56) & 0x0000_00FF));
    }

    /**
     * Adds a {@link Vector128} value as sixteen bytes in little endian to the bytecode.
     *
     * @param value the {@link Vector128} value
     */
    protected void add16(Vector128 value) {
        byte[] bytes = value.asBytes();
        for (int i = 0; i < 16; i++) {
            data.add(bytes[i]);
        }
    }

    /**
     * Sets a byte at the given location.
     * 
     * @param location the location in the bytecode
     * @param value the new byte value
     */
    protected void set(int location, byte value) {
        data.set(location, value);
    }

    /**
     * Sets an integer value as four bytes in little endian in the bytecode based on the given
     * location.
     * 
     * @param location the location in the bytecode
     * @param value the new integer value
     */
    public void set(int location, int value) {
        assert location() > location + 3;
        data.set(location, (byte) (value & 0x0000_00FF));
        data.set(location + 1, (byte) ((value >>> 8) & 0x0000_00FF));
        data.set(location + 2, (byte) ((value >>> 16) & 0x0000_00FF));
        data.set(location + 3, (byte) ((value >>> 24) & 0x0000_00FF));
    }

    /**
     * @return The current location in the bytecode.
     */
    public int location() {
        return data.size();
    }

    /**
     * @return A byte array representation of the bytecode.
     */
    public byte[] toArray() {
        return data.toArray();
    }
}
