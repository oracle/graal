/*
 * Copyright (c) 2019, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.wasm.binary;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;

public class Globals {
    private static final int INITIAL_GLOBALS_SIZE = 2048;

    // If we support late linking, we need to ensure that methods accessing the global array
    // are compiled with assumptions on what this field points to.
    // Such an assumption can be invalidated if the late-linking causes this array
    // to be replaced with a larger array.
    @CompilationFinal(dimensions = 1) private long[] globals;
    private int numGlobals;

    public Globals() {
        this.globals = new long[INITIAL_GLOBALS_SIZE];
        this.numGlobals = 0;
    }

    public int globalCount() {
        return numGlobals;
    }

    private void ensureCapacity() {
        if (numGlobals == globals.length) {
            final long[] nglobals = new long[globals.length * 2];
            System.arraycopy(globals, 0, nglobals, 0, globals.length);
            globals = nglobals;
        }
    }

    public int allocateGlobal() {
        ensureCapacity();
        globals[numGlobals] = 0;
        int idx = numGlobals;
        numGlobals++;
        return idx;
    }

    public int loadAsInt(int address) {
        return (int) globals[address];
    }

    public long loadAsLong(int address) {
        return globals[address];
    }

    public float loadAsFloat(int address) {
        return Float.intBitsToFloat((int) globals[address]);
    }

    public double loadAsDouble(int address) {
        return Double.longBitsToDouble(globals[address]);
    }

    public void storeInt(int address, int value) {
        globals[address] = value;
    }

    public void storeLong(int address, long value) {
        globals[address] = value;
    }

    public void storeFloat(int address, float value) {
        globals[address] = Float.floatToRawIntBits(value);
    }

    public void storeFloatWithInt(int address, int value) {
        globals[address] = value;
    }

    public void storeDouble(int address, double value) {
        globals[address] = Double.doubleToRawLongBits(value);
    }

    public void storeDoubleWithLong(int address, long value) {
        globals[address] = value;
    }
}
