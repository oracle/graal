/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;

public class GlobalRegistry {
    private static final int INITIAL_GLOBALS_SIZE = 2048;

    // If we support late linking, we need to ensure that methods accessing the global array
    // are compiled with assumptions on what this field points to.
    // Such an assumption can be invalidated if the late-linking causes this array
    // to be replaced with a larger array.
    @CompilationFinal(dimensions = 0) private long[] globals;
    private int numGlobals;

    public GlobalRegistry() {
        this.globals = new long[INITIAL_GLOBALS_SIZE];
        this.numGlobals = 0;
    }

    public int count() {
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

    public GlobalRegistry duplicate() {
        final GlobalRegistry other = new GlobalRegistry();
        for (int i = 0; i < numGlobals; i++) {
            final int address = other.allocateGlobal();
            final long value = this.loadAsLong(address);
            other.storeLong(address, value);
        }
        return other;
    }
}
