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

import static com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import static com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import static com.oracle.truffle.api.CompilerDirectives.transferToInterpreter;

import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

@ExportLibrary(InteropLibrary.class)
public class WasmModule implements TruffleObject {
    @CompilationFinal private final String name;
    @CompilationFinal private final SymbolTable symbolTable;
    @CompilationFinal private final ModuleGlobals globals;

    public WasmModule(String name) {
        this.name = name;
        this.symbolTable = new SymbolTable(this);
        this.globals = new ModuleGlobals();
    }

    static final class ModuleGlobals {
        /**
         * Stores the globals as 64-bit values.
         */
        @CompilationFinal(dimensions = 1) private long[] globals;

        /**
         * Stores the type of each global. A global can be of any of the valid value types.
         */
        @CompilationFinal(dimensions = 1) private byte[] globalTypes;

        /**
         * Stores whether each global is mutable.
         */
        @CompilationFinal(dimensions = 1) private boolean[] globalMut;

        private boolean initialized = false;

        private ModuleGlobals() {
        }

        public void initialize(int numGlobals) {
            if (initialized) {
                throw new RuntimeException("ModuleGlobals has already been initialized.");
            }
            this.globals = new long[numGlobals];
            this.globalTypes = new byte[numGlobals];
            this.globalMut = new boolean[numGlobals];
            initialized = true;
        }

        public void register(int index, long value, byte type, boolean isMutable) {
            Assert.assertInRange(index, 0, globals.length - 1, "Global index out-of-range.");
            this.globals[index] = value;
            this.globalTypes[index] = type;
            this.globalMut[index] = isMutable;
        }

        public int size() {
            return globals.length;
        }

        public byte type(int index) {
            return globalTypes[index];
        }

        public boolean isMutable(int index) {
            return globalMut[index];
        }

        public int getAsInt(int index) {
            return (int) globals[index];
        }

        public long getAsLong(int index) {
            return globals[index];
        }

        public float getAsFloat(int index) {
            return Float.intBitsToFloat((int) globals[index]);
        }

        public double getAsDouble(int index) {
            return Double.longBitsToDouble(globals[index]);
        }

        public void setInt(int index, int value) {
            globals[index] = value;
        }

        public void setLong(int index, long value) {
            globals[index] = value;
        }

        public void setFloat(int index, float value) {
            globals[index] = Float.floatToRawIntBits(value);
        }

        public void setDouble(int index, double value) {
            globals[index] = Double.doubleToRawLongBits(value);
        }
    }

    public SymbolTable symbolTable() {
        return symbolTable;
    }

    public String name() {
        return name;
    }

    public ModuleGlobals globals() {
        return globals;
    }

    @ExportMessage
    boolean hasMembers() {
        return true;
    }

    @ExportMessage
    @TruffleBoundary
    Object readMember(String exportName) {
        return exportName.equals("__START__") ? symbolTable.startFunction() : symbolTable.function(exportName);
    }

    @ExportMessage
    @TruffleBoundary
    boolean isMemberReadable(String member) {
        try {
            int functionIndex = Integer.parseInt(member);
            return functionIndex >= 0 && functionIndex < symbolTable.numFunctions();
        } catch (NumberFormatException exc) {
            return false;
        }
    }

    @ExportMessage
    @TruffleBoundary
    Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
        return new FunctionNamesObject(symbolTable, symbolTable.numFunctions());
    }

    @ExportLibrary(InteropLibrary.class)
    static final class FunctionNamesObject implements TruffleObject {

        private SymbolTable symbolTable;
        private int numFunctions;

        FunctionNamesObject(SymbolTable symbolTable, int numFunctions) {
            this.symbolTable = symbolTable;
            this.numFunctions = numFunctions;
        }

        @ExportMessage
        boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        boolean isArrayElementReadable(long index) {
            return index >= 0 && index < numFunctions;
        }

        @ExportMessage
        long getArraySize() {
            return numFunctions;
        }

        @ExportMessage
        Object readArrayElement(long index) throws InvalidArrayIndexException {
            if (!isArrayElementReadable(index)) {
                transferToInterpreter();
                throw InvalidArrayIndexException.create(index);
            }
            // TODO: Check whether long fits into an int, throw otherwise.
            return symbolTable.function((int) index);
        }
    }
}
