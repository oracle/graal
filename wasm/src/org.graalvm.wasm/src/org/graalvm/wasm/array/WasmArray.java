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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.memory.ByteArraySupport;
import org.graalvm.wasm.WasmTypedHeapObject;
import org.graalvm.wasm.constants.Mutability;
import org.graalvm.wasm.types.DefinedType;

@ExportLibrary(InteropLibrary.class)
public abstract class WasmArray extends WasmTypedHeapObject {

    protected static final ByteArraySupport byteArraySupport = ByteArraySupport.littleEndian();

    protected final int size;

    protected WasmArray(DefinedType type, int size) {
        super(type);
        assert type.isArrayType();
        this.size = size;
    }

    public final int length() {
        return size;
    }

    public abstract Object getObj(int index);

    public abstract void setObj(int index, Object value) throws UnsupportedTypeException;

    @ExportMessage
    protected static boolean hasArrayElements(@SuppressWarnings("unused") WasmArray receiver) {
        return true;
    }

    @ExportMessage
    protected long getArraySize() {
        return size;
    }

    @ExportMessage
    protected boolean isArrayElementReadable(long index) {
        return index >= 0 && index < size;
    }

    @ExportMessage
    protected Object readArrayElement(long index) throws InvalidArrayIndexException {
        if (isArrayElementReadable(index)) {
            return getObj((int) index);
        } else {
            throw InvalidArrayIndexException.create(index);
        }
    }

    @ExportMessage
    protected boolean isArrayElementModifiable(long index) {
        return type().asArrayType().fieldType().mutability() == Mutability.MUTABLE && index >= 0 && index < size;
    }

    @ExportMessage
    protected void writeArrayElement(long index, Object value) throws InvalidArrayIndexException, UnsupportedTypeException {
        if (isArrayElementModifiable(index)) {
            setObj((int) index, value);
        } else {
            throw InvalidArrayIndexException.create(index);
        }
    }

    @ExportMessage
    protected boolean isArrayElementInsertable(@SuppressWarnings("unused") long index) {
        return false;
    }

    @Override
    @TruffleBoundary
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("wasm-array:<");
        for (int i = 0; i < size; i++) {
            sb.append(getObj(i));
            if (i < size - 1) {
                sb.append(", ");
            }
        }
        sb.append(">");
        return sb.toString();
    }

    @ExportMessage
    protected String toDisplayString(@SuppressWarnings("unused") boolean allowSideEffects) {
        return toString();
    }
}
