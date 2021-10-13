/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.wasm.runtime;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.profiles.BranchProfile;
import org.graalvm.collections.EconomicMap;
import org.graalvm.wasm.constants.ExportIdentifier;
import org.graalvm.wasm.parser.module.WasmExternalValue;
import org.graalvm.wasm.parser.module.WasmModule;
import org.graalvm.wasm.runtime.memory.WasmMemory;

@ExportLibrary(InteropLibrary.class)
public final class WasmModuleInstance implements TruffleObject {
    private final WasmModule module;
    private final WasmInstance instance;

    private final EconomicMap<String, WasmExternalValue> exports;
    @CompilationFinal(dimensions = 1) private final String[] exportIndexMap;

    @CompilationFinal private int functionIndex = 0;
    @CompilationFinal private int globalIndex = 0;
    @CompilationFinal private int exportIndex = 0;
    @CompilationFinal private boolean hasTable = false;
    @CompilationFinal private boolean hasMemory = false;

    @CompilationFinal(dimensions = 1) private final String[] functionNames;

    public WasmModuleInstance(WasmModule module, WasmFunctionType[] functionTypes, WasmFunctionInstance[] functions, WasmGlobal[] globals, int exportCount, String name, String[] functionNames) {
        this.module = module;
        this.instance = new WasmInstance(name, functionTypes, functions, globals);
        this.exports = EconomicMap.create(exportCount);
        this.exportIndexMap = new String[exportCount];

        this.functionNames = functionNames;
    }

    public WasmModule getModule() {
        return module;
    }

    public int getNextFunctionIndex() {
        return functionIndex;
    }

    public int addFunction(WasmFunctionInstance function) {
        instance.addFunction(functionIndex, function);
        return functionIndex++;
    }

    public boolean hasFunction(int index) {
        return Integer.compareUnsigned(index, functionIndex) < 0;
    }

    public WasmFunctionInstance getFunction(int index) {
        return instance.getFunction(index);
    }

    public void addTable(WasmTable t) {
        instance.addTable(t);
        hasTable = true;
    }

    public boolean hasTable() {
        return hasTable;
    }

    public WasmTable getTable() {
        return instance.getTable();
    }

    public void addMemory(WasmMemory m) {
        instance.addMemory(m);
        hasMemory = true;
    }

    public boolean hasMemory() {
        return hasMemory;
    }

    public WasmMemory getMemory() {
        return instance.getMemory();
    }

    public int addGlobal(WasmGlobal global) {
        instance.addGlobal(globalIndex, global);
        return globalIndex++;
    }

    public boolean hasGlobal(int index) {
        return Integer.compareUnsigned(index, globalIndex) < 0;
    }

    public WasmGlobal getGlobal(int index) {
        return instance.getGlobal(index);
    }

    public void addExport(String exportName, WasmExternalValue externalValue) {
        exports.put(exportName, externalValue);
        exportIndexMap[exportIndex++] = exportName;
    }

    public WasmInstance getInstance() {
        return instance;
    }

    @TruffleBoundary
    public WasmExternalValue getExport(String exportName) {
        return exports.get(exportName);
    }

    @TruffleBoundary
    public WasmExternalValue getExportAt(int index) {
        return exports.get(exportIndexMap[index]);
    }

    @TruffleBoundary
    public int getExportCount() {
        return exports.size();
    }

    public boolean isBuiltin() {
        return module == null;
    }

    public String getName() {
        return instance.getName();
    }

    public String getFunctionName(int index) {
        if (functionNames == null) {
            return null;
        }
        return functionNames[index];
    }

    /**
     * Try to infer the entry function for this instance. Not part of the spec, for testing purpose
     * only.
     *
     * @return exported function named {@code _main}, exported function named {@code _start}, start
     *         function or {@code null} in this order.
     */
    public WasmFunctionInstance inferEntryPoint() {
        final WasmExternalValue mainExport = getExport("_main");
        if (mainExport != null && mainExport.isFunction() && mainExport.functionExists(this)) {
            return mainExport.getFunction(this);
        }
        final WasmExternalValue startExport = getExport("_start");
        if (startExport != null && startExport.isFunction() && startExport.functionExists(this)) {
            return startExport.getFunction(this);
        }
        return null;
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    boolean hasMembers() {
        return true;
    }

    @ExportMessage
    @TruffleBoundary
    public Object readMember(String member,
                    @Cached.Shared("error") @Cached BranchProfile errorBranch) throws UnknownIdentifierException {
        final WasmExternalValue export = getExport(member);
        if (export != null) {
            switch (export.getType()) {
                case ExportIdentifier.FUNCTION:
                    return export.getFunction(this);
                case ExportIdentifier.GLOBAL:
                    return export.getGlobal(this);
                case ExportIdentifier.MEMORY:
                    return export.getMemory(this);
            }
        }
        errorBranch.enter();
        throw UnknownIdentifierException.create(member);
    }

    @ExportMessage
    @TruffleBoundary
    public void writeMember(String member, Object value,
                    @Cached.Shared("error") @Cached BranchProfile errorBranch) throws UnknownIdentifierException, UnsupportedMessageException {
        // This method works only for mutable globals.
        final WasmExternalValue export = getExport(member);
        if (export == null || !export.isGlobal() || !export.globalExists(this)) {
            errorBranch.enter();
            throw UnknownIdentifierException.create(member);
        }
        if (!(value instanceof Number)) {
            errorBranch.enter();
            throw UnsupportedMessageException.create();
        }
        final WasmGlobal global = export.getGlobal(this);
        if (!global.isMutable()) {
            // Constant variables cannot be modified.
            errorBranch.enter();
            throw UnsupportedMessageException.create();
        }
        long longValue = ((Number) value).longValue();
        global.storeLong(longValue);
    }

    @ExportMessage
    @TruffleBoundary
    boolean isMemberReadable(String member) {
        return getExport(member) != null;
    }

    @ExportMessage
    @TruffleBoundary
    boolean isMemberModifiable(String member) {
        WasmExternalValue export = getExport(member);
        return export != null && export.isGlobal() && export.globalExists(this) && export.getGlobal(this).isMutable();
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    @TruffleBoundary
    boolean isMemberInsertable(@SuppressWarnings("unused") String member) {
        return false;
    }

    @ExportMessage
    @TruffleBoundary
    Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
        // TODO: Handle includeInternal.
        return new WasmModuleInstance.ExportedMembers(this);
    }

    @ExportLibrary(InteropLibrary.class)
    static final class ExportedMembers implements TruffleObject {
        private final WasmModuleInstance instance;

        ExportedMembers(WasmModuleInstance instance) {
            this.instance = instance;
        }

        @SuppressWarnings("static-method")
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
            return instance.getExportCount();
        }

        @ExportMessage
        @TruffleBoundary
        Object readArrayElement(long absoluteIndex,
                        @Cached BranchProfile errorBranch) throws InvalidArrayIndexException {
            if (!isArrayElementReadable(absoluteIndex)) {
                errorBranch.enter();
                throw InvalidArrayIndexException.create(absoluteIndex);
            }
            WasmExternalValue export = instance.getExportAt((int) absoluteIndex);
            if (export.isFunction()) {
                return export.getFunction(instance);
            }
            if (export.isGlobal()) {
                return export.getGlobal(instance);
            }
            return export.getMemory(instance);
        }
    }

    @Override
    public String toString() {
        return "wasm-module-instance(" + instance.getName() + ")";
    }
}
