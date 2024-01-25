/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives;
import org.graalvm.wasm.api.Vector128;
import org.graalvm.wasm.exception.Failure;
import org.graalvm.wasm.exception.WasmException;
import org.graalvm.wasm.globals.WasmGlobal;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;

/**
 * The global registry holds the global values in the WebAssembly engine instance.
 * <p>
 * Global values that are declared in some WebAssembly module are stored in an array of longs.
 * Global values can also be external objects that are accessed via Interop -- such globals are
 * stored inside an array of objects, and their addresses are negative.
 */
public class GlobalRegistry {
    private static final int INITIAL_GLOBALS_SIZE = 8;

    // If we support late linking, we need to ensure that methods accessing the global array
    // are compiled with assumptions on what this field points to.
    // Such an assumption can be invalidated if the late-linking causes this array
    // to be replaced with a larger array.
    @CompilationFinal(dimensions = 0) private long[] globals;
    @CompilationFinal(dimensions = 0) private Object[] objectGlobals;
    @CompilationFinal(dimensions = 1) private WasmGlobal[] externalGlobals;
    private int globalCount;
    private int externalGlobalCount;

    public GlobalRegistry() {
        this.globals = new long[INITIAL_GLOBALS_SIZE];
        this.objectGlobals = new Object[INITIAL_GLOBALS_SIZE];
        this.externalGlobals = new WasmGlobal[INITIAL_GLOBALS_SIZE];
        this.globalCount = 0;
        this.externalGlobalCount = 0;
    }

    public int count() {
        return globalCount;
    }

    private void ensureGlobalCapacity() {
        if (globalCount == globals.length) {
            final long[] nGlobals = new long[globals.length * 2];
            System.arraycopy(globals, 0, nGlobals, 0, globals.length);
            globals = nGlobals;
            final Object[] nObjectGlobals = new Object[objectGlobals.length * 2];
            System.arraycopy(objectGlobals, 0, nObjectGlobals, 0, objectGlobals.length);
            objectGlobals = nObjectGlobals;
        }
    }

    private void ensureExternalGlobalCapacity() {
        if (externalGlobalCount == externalGlobals.length) {
            final WasmGlobal[] nExternalGlobals = new WasmGlobal[externalGlobals.length * 2];
            System.arraycopy(externalGlobals, 0, nExternalGlobals, 0, externalGlobals.length);
            externalGlobals = nExternalGlobals;
        }
    }

    public int allocateGlobal() {
        ensureGlobalCapacity();
        globals[globalCount] = 0;
        objectGlobals[globalCount] = null;
        int idx = globalCount;
        globalCount++;
        return idx;
    }

    public int allocateExternalGlobal(WasmGlobal object) {
        ensureExternalGlobalCapacity();
        externalGlobals[externalGlobalCount] = object;
        int idx = -externalGlobalCount - 1;
        externalGlobalCount++;
        return idx;
    }

    public int loadAsInt(int address) {
        return (int) loadAsLong(address);
    }

    public long loadAsLong(int address) {
        if (address < 0) {
            final WasmGlobal global = externalGlobals[-address - 1];
            return global.loadAsLong();
        }
        return globals[address];
    }

    public Vector128 loadAsVector128(int address) {
        return (Vector128) loadAsObject(address);
    }

    public Object loadAsReference(int address) {
        return loadAsObject(address);
    }

    public Object loadAsObject(int address) {
        if (address < 0) {
            final WasmGlobal global = externalGlobals[-address - 1];
            return global.loadAsObject();
        }
        return objectGlobals[address];
    }

    public void store(byte globalValueType, int address, Object value) {
        switch (globalValueType) {
            case WasmType.I32_TYPE:
                storeInt(address, (int) value);
                break;
            case WasmType.I64_TYPE:
                storeLong(address, (long) value);
                break;
            case WasmType.F32_TYPE:
                storeInt(address, Float.floatToRawIntBits((float) value));
                break;
            case WasmType.F64_TYPE:
                storeLong(address, Double.doubleToRawLongBits((double) value));
                break;
            case WasmType.V128_TYPE:
                storeVector128(address, (Vector128) value);
                break;
            case WasmType.FUNCREF_TYPE:
            case WasmType.EXTERNREF_TYPE:
                storeReference(address, value);
                break;
            default:
                throw CompilerDirectives.shouldNotReachHere();
        }
    }

    public void storeInt(int address, int value) {
        storeLong(address, value);
    }

    public void storeLong(int address, long value) {
        if (address < 0) {
            final WasmGlobal global = externalGlobals[-address - 1];
            global.storeLong(value);
        } else {
            globals[address] = value;
        }
    }

    public void storeVector128(int address, Vector128 value) {
        storeObject(address, value);
    }

    public void storeReference(int address, Object value) {
        storeObject(address, value);
    }

    public void storeObject(int address, Object value) {
        if (address < 0) {
            final WasmGlobal global = externalGlobals[-address - 1];
            global.storeObject(value);
        } else {
            objectGlobals[address] = value;
        }
    }

    public GlobalRegistry duplicate() {
        final GlobalRegistry other = new GlobalRegistry();
        for (int i = 0; i < globalCount; i++) {
            final int address = other.allocateGlobal();
            final long value = this.loadAsLong(address);
            final Object objectValue = this.loadAsObject(address);
            other.storeLong(address, value);
            other.storeObject(address, objectValue);
        }
        for (int i = 0; i < externalGlobalCount; i++) {
            other.allocateExternalGlobal(this.externalGlobals[i]);
        }
        return other;
    }

    public WasmGlobal externalGlobal(int address) {
        CompilerAsserts.neverPartOfCompilation();
        if (address >= 0) {
            throw WasmException.create(Failure.UNSPECIFIED_INTERNAL, "Global at address " + address + " is not external.");
        }
        return externalGlobals[-address - 1];
    }
}
