/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.wasm.array;

import com.oracle.truffle.api.interop.UnsupportedTypeException;
import org.graalvm.wasm.vector.Vector128;
import org.graalvm.wasm.vector.Vector128Ops;
import org.graalvm.wasm.types.DefinedType;
import org.graalvm.wasm.types.VectorType;

import java.util.Arrays;

public final class WasmVec128Array extends WasmArray {

    private final byte[] array;

    public WasmVec128Array(DefinedType type, byte[] array) {
        super(type, array.length);
        assert type.asArrayType().fieldType().storageType() == VectorType.V128;
        this.array = array;
    }

    public <V128> WasmVec128Array(DefinedType type, int length, V128 initialValue, Vector128Ops<V128> vector128Ops) {
        this(type, new byte[length << 4]);
        fill(0, length, initialValue, vector128Ops);
    }

    public WasmVec128Array(DefinedType type, int length, Vector128 initialValue) {
        this(type, new byte[length << 4]);
        for (int i = 0; i < length; i++) {
            System.arraycopy(initialValue.getBytes(), 0, this.array, i << 4, 16);
        }
    }

    public WasmVec128Array(DefinedType type, int length) {
        this(type, new byte[length << 4]);
    }

    public WasmVec128Array(DefinedType type, int length, byte[] source, int srcOffset) {
        this(type, new byte[length << 4]);
        initialize(source, srcOffset, 0, length);
    }

    public <V128> V128 get(int index, Vector128Ops<V128> vector128Ops) {
        return vector128Ops.fromArray(array, index << 4);
    }

    public <V128> void set(int index, V128 value, Vector128Ops<V128> vector128Ops) {
        vector128Ops.intoArray(value, array, index << 4);
    }

    public void copyFrom(WasmVec128Array src, int srcOffset, int dstOffset, int length) {
        System.arraycopy(src.array, srcOffset << 4, this.array, dstOffset << 4, length << 4);
    }

    public <V128> void fill(int offset, int length, V128 value, Vector128Ops<V128> vector128Ops) {
        for (int i = offset; i < offset + length; i++) {
            vector128Ops.intoArray(value, this.array, i << 4);
        }
    }

    public void initialize(byte[] source, int srcOffset, int dstOffset, int length) {
        System.arraycopy(source, srcOffset, array, dstOffset << 4, length << 4);
    }

    @Override
    public Object getObj(int index) {
        return new Vector128(Arrays.copyOfRange(array, index << 4, (index + 1) << 4));
    }

    @Override
    public void setObj(int index, Object value) throws UnsupportedTypeException {
        if (value instanceof Vector128 vec) {
            System.arraycopy(vec.getBytes(), 0, array, index << 4, 16);
        } else {
            throw UnsupportedTypeException.create(new Object[]{value});
        }
    }
}
