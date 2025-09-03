/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.wasm;

import java.util.BitSet;
import java.util.Objects;

import org.graalvm.wasm.api.Vector128;
import org.graalvm.wasm.globals.WasmGlobal;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;

/**
 * The global registry holds the global values in the WebAssembly module instance.
 * <p>
 * Global values that are declared in some WebAssembly module are stored in an array of longs, or in
 * case of funcref/externref, in an array of objects. Globals that imported and/or exported --
 * so-called external globals -- are stored inside a {@link WasmGlobal} array, and their addresses
 * are negative. Exported globals may be accessed via JS/Interop. Imported globals can be exported
 * globals of other modules or allocated by JS and are supplied via the import object.
 */
public final class GlobalRegistry {

    private final long[] globals;
    private final Object[] objectGlobals;
    @CompilationFinal(dimensions = 1) private final WasmGlobal[] externalGlobals;
    private final BitSet initialized;

    public GlobalRegistry(int internalGlobalsSize, int externalGlobalsSize) {
        this.globals = new long[internalGlobalsSize];
        this.objectGlobals = new Object[internalGlobalsSize];
        this.externalGlobals = new WasmGlobal[externalGlobalsSize];
        this.initialized = new BitSet(internalGlobalsSize + externalGlobalsSize);
    }

    public int count() {
        return globals.length;
    }

    public float loadAsFloat(int globalAddress) {
        return Float.intBitsToFloat(loadAsInt(globalAddress));
    }

    public double loadAsDouble(int globalAddress) {
        return Double.longBitsToDouble(loadAsLong(globalAddress));
    }

    public int loadAsInt(int address) {
        return (int) loadAsLong(address);
    }

    public long loadAsLong(int address) {
        if (address < 0) {
            return externalGlobal(address).loadAsLong();
        } else {
            return globals[address];
        }
    }

    public Vector128 loadAsVector128(int address) {
        if (address < 0) {
            return externalGlobal(address).loadAsVector128();
        } else {
            return (Vector128) loadAsObject(address);
        }
    }

    public Object loadAsReference(int address) {
        if (address < 0) {
            return externalGlobal(address).loadAsReference();
        } else {
            return loadAsObject(address);
        }
    }

    private Object loadAsObject(int address) {
        assert address >= 0 : address;
        return objectGlobals[address];
    }

    public void store(byte globalValueType, int address, Object value) {
        switch (globalValueType) {
            case WasmType.I32_TYPE -> storeInt(address, (int) value);
            case WasmType.I64_TYPE -> storeLong(address, (long) value);
            case WasmType.F32_TYPE -> storeFloat(address, (float) value);
            case WasmType.F64_TYPE -> storeDouble(address, (double) value);
            case WasmType.V128_TYPE -> storeVector128(address, (Vector128) value);
            case WasmType.FUNCREF_TYPE, WasmType.EXTERNREF_TYPE -> storeReference(address, value);
            default -> throw CompilerDirectives.shouldNotReachHere();
        }
    }

    public void storeFloat(int address, float value) {
        storeInt(address, Float.floatToRawIntBits(value));
    }

    public void storeDouble(int address, double value) {
        storeLong(address, Double.doubleToRawLongBits(value));
    }

    public void storeInt(int address, int value) {
        storeLong(address, value);
    }

    public void storeLong(int address, long value) {
        if (address < 0) {
            externalGlobal(address).storeLong(value);
        } else {
            globals[address] = value;
        }
    }

    public void storeVector128(int address, Vector128 value) {
        if (address < 0) {
            externalGlobal(address).storeVector128(value);
        } else {
            storeObject(address, value);
        }
    }

    public void storeReference(int address, Object value) {
        if (address < 0) {
            externalGlobal(address).storeReference(value);
        } else {
            storeObject(address, value);
        }
    }

    private void storeObject(int address, Object value) {
        assert address >= 0 : address;
        objectGlobals[address] = value;
    }

    WasmGlobal externalGlobal(int address) {
        assert address < 0 : address;
        final WasmGlobal result = externalGlobals[-address - 1];
        assert result != null : "Uninitialized external global at address: " + address;
        return result;
    }

    void setExternalGlobal(int address, WasmGlobal global) {
        assert address < 0 : address;
        assert externalGlobals[-address - 1] == null : "Already initialized external global at address: " + address;
        externalGlobals[-address - 1] = Objects.requireNonNull(global);
    }

    public GlobalRegistry duplicate() {
        final GlobalRegistry other = new GlobalRegistry(globals.length, externalGlobals.length);
        for (int i = 0; i < count(); i++) {
            final int address = i;
            final long value = this.loadAsLong(address);
            final Object objectValue = this.loadAsObject(address);
            other.storeLong(address, value);
            other.storeObject(address, objectValue);
        }
        System.arraycopy(this.externalGlobals, 0, other.externalGlobals, 0, externalGlobals.length);
        return other;
    }

    public boolean isInitialized(int globalIndex) {
        return initialized.get(globalIndex);
    }

    public void setInitialized(int globalIndex, boolean value) {
        initialized.set(globalIndex, value);
    }
}
