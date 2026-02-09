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
import org.graalvm.wasm.types.DefinedType;
import org.graalvm.wasm.types.NumberType;

import java.util.Arrays;

public final class WasmFloat32Array extends WasmArray {

    private final float[] array;

    public WasmFloat32Array(DefinedType type, float[] array) {
        super(type, array.length);
        assert type.asArrayType().fieldType().storageType() == NumberType.F32;
        this.array = array;
    }

    public WasmFloat32Array(DefinedType type, int length, float initialValue) {
        this(type, new float[length]);
        fill(0, length, initialValue);
    }

    public WasmFloat32Array(DefinedType type, int length) {
        this(type, new float[length]);
    }

    public WasmFloat32Array(DefinedType type, int length, byte[] source, int srcOffset) {
        this(type, new float[length]);
        initialize(source, srcOffset, 0, length);
    }

    public float get(int index) {
        return array[index];
    }

    public void set(int index, float value) {
        array[index] = value;
    }

    public void copyFrom(WasmFloat32Array src, int srcOffset, int dstOffset, int length) {
        System.arraycopy(src.array, srcOffset, this.array, dstOffset, length);
    }

    public void fill(int offset, int length, float value) {
        Arrays.fill(array, offset, offset + length, value);
    }

    public void initialize(byte[] source, int srcOffset, int dstOffset, int length) {
        for (int i = 0; i < length; i++) {
            array[dstOffset + i] = byteArraySupport.getFloat(source, srcOffset + (i << 2));
        }
    }

    @Override
    public Object getObj(int index) {
        return array[index];
    }

    @Override
    public void setObj(int index, Object value) throws UnsupportedTypeException {
        if (value instanceof Float floatValue) {
            array[index] = floatValue;
        } else {
            throw UnsupportedTypeException.create(new Object[]{value});
        }
    }
}
