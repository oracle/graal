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

import static com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import static com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import static com.oracle.truffle.api.CompilerDirectives.transferToInterpreter;

import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import org.graalvm.wasm.constants.GlobalModifier;
import org.graalvm.wasm.collection.IntArrayList;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@ExportLibrary(InteropLibrary.class)
// TODO: We should make this class more Truffle-compliant.
// In particular, one thing that's missing:
// - access exported tables and read them
// - access exported memories and modify them
@SuppressWarnings("static-method")
public final class WasmModule implements TruffleObject {
    private final String name;
    private final SymbolTable symbolTable;
    @CompilationFinal(dimensions = 1) private final byte[] data;
    private boolean isLinked;
    public final WasmOptions.StoreConstantsPolicyEnum storeConstantsPolicy;

    public WasmModule(String name, byte[] data, WasmOptions.StoreConstantsPolicyEnum storeConstantsPolicy) {
        this.name = name;
        this.symbolTable = new SymbolTable(this);
        this.data = data;
        this.isLinked = false;
        this.storeConstantsPolicy = storeConstantsPolicy;
    }

    public SymbolTable symbolTable() {
        return symbolTable;
    }

    public String name() {
        return name;
    }

    public byte[] data() {
        return data;
    }

    void setLinked() {
        isLinked = true;
    }

    public boolean isLinked() {
        return isLinked;
    }

    @ExportMessage
    boolean hasMembers() {
        return true;
    }

    @ExportMessage
    @TruffleBoundary
    public Object readMember(String member) throws UnknownIdentifierException {
        final WasmFunction function = symbolTable.exportedFunctions().get(member);
        if (function != null) {
            return function;
        }
        final Integer globalIndex = symbolTable.exportedGlobals().get(member);
        if (globalIndex != null) {
            readGlobal(symbolTable, globalIndex);
        }
        if (member.equals(symbolTable.exportedMemory())) {
            return symbolTable.memory();
        }
        throw UnknownIdentifierException.create(member);
    }

    @ExportMessage
    @TruffleBoundary
    public void writeMember(String member, Object value) throws UnknownIdentifierException, UnsupportedMessageException {
        // This method works only for mutable globals.
        final Integer index = symbolTable.exportedGlobals().get(member);
        if (index == null) {
            throw UnknownIdentifierException.create(member);
        }
        final int address = symbolTable.globalAddress(index);
        if (!(value instanceof Number)) {
            throw UnsupportedMessageException.create();
        }
        final boolean mutable = symbolTable.globalMutability(index) == GlobalModifier.MUTABLE;
        if (isLinked && !mutable) {
            // Constant variables cannot be modified after linking.
            throw UnsupportedMessageException.create();
        }
        long longValue = ((Number) value).longValue();
        WasmContext.getCurrent().globals().storeLong(address, longValue);
    }

    @ExportMessage
    @TruffleBoundary
    boolean isMemberReadable(String member) {
        try {
            return symbolTable.exportedFunctions().containsKey(member) || symbolTable.exportedGlobals().containsKey(member) ||
                            member.equals(symbolTable.exportedMemory());
        } catch (NumberFormatException exc) {
            return false;
        }
    }

    @ExportMessage
    @TruffleBoundary
    boolean isMemberModifiable(String member) {
        final Integer index = symbolTable.exportedGlobals().get(member);
        if (index == null) {
            return false;
        }
        final boolean mutable = symbolTable.globalMutability(index) == GlobalModifier.MUTABLE;
        if (!mutable) {
            return false;
        }
        return true;
    }

    @ExportMessage
    @TruffleBoundary
    boolean isMemberInsertable(@SuppressWarnings("unused") String member) {
        return false;
    }

    private static Object readGlobal(SymbolTable symbolTable, int globalIndex) {
        final int address = symbolTable.globalAddress(globalIndex);
        final GlobalRegistry globals = WasmContext.getCurrent().globals();
        final byte type = symbolTable.globalValueType(globalIndex);
        switch (type) {
            case ValueTypes.I32_TYPE:
                return globals.loadAsInt(address);
            case ValueTypes.I64_TYPE:
                return globals.loadAsLong(address);
            case ValueTypes.F32_TYPE:
                return globals.loadAsFloat(address);
            case ValueTypes.F64_TYPE:
                return globals.loadAsDouble(address);
            default:
                throw new RuntimeException("Unknown type: " + type);
        }
    }

    @ExportMessage
    @TruffleBoundary
    Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
        // TODO: Handle includeInternal.
        return new ExportedMembers(symbolTable);
    }

    public Global global(String globalName) {
        final Integer index = symbolTable.exportedGlobals().get(globalName);
        if (index != null) {
            return new Global(symbolTable, index);
        }
        for (Map.Entry<Integer, ImportDescriptor> entry : symbolTable.importedGlobals().entrySet()) {
            if (entry.getValue().memberName.equals(globalName)) {
                return new Global(symbolTable, entry.getKey());
            }
        }
        return null;
    }

    public boolean isBuiltin() {
        return data == null;
    }

    @ExportLibrary(InteropLibrary.class)
    static final class ExportedMembers implements TruffleObject {
        private final SymbolTable symbolTable;
        private final List<WasmFunction> exportedFunctions;
        private final IntArrayList exportedGlobals;

        ExportedMembers(SymbolTable symbolTable) {
            this.symbolTable = symbolTable;
            this.exportedFunctions = new ArrayList<>(symbolTable.exportedFunctions().values());
            this.exportedGlobals = new IntArrayList();
            for (int globalIndex : symbolTable.exportedGlobals().values()) {
                this.exportedGlobals.add(globalIndex);
            }
        }

        @ExportMessage
        @TruffleBoundary
        boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        @TruffleBoundary
        boolean isArrayElementReadable(long index) {
            return index >= 0 && index < getArraySize();
        }

        @ExportMessage
        @TruffleBoundary
        long getArraySize() {
            return exportedFunctions.size() + exportedGlobals.size() + memoriesSize();
        }

        private int memoriesSize() {
            return (symbolTable.exportedMemory() != null ? 1 : 0);
        }

        @ExportMessage
        @TruffleBoundary
        Object readArrayElement(long absoluteIndex) throws InvalidArrayIndexException {
            long index = absoluteIndex;
            if (!isArrayElementReadable(index)) {
                transferToInterpreter();
                throw InvalidArrayIndexException.create(index);
            }
            if (index < exportedFunctions.size()) {
                return exportedFunctions.get((int) index);
            }
            index -= exportedFunctions.size();
            if (index < exportedGlobals.size()) {
                final int globalIndex = exportedGlobals.get((int) index);
                return readGlobal(symbolTable, globalIndex);
            }
            return symbolTable.memory();
        }
    }

    public static final class Global {
        private SymbolTable symbolTable;
        private final int index;

        Global(SymbolTable symbolTable, Integer index) {
            this.symbolTable = symbolTable;
            this.index = index;
        }

        public boolean isMutable() {
            return symbolTable.globalMutability(index) == GlobalModifier.MUTABLE;
        }
    }

    @Override
    public String toString() {
        return "wasm-module(" + name + ")";
    }
}
