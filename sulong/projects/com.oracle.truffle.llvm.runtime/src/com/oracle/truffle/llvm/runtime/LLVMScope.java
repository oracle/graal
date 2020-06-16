/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map.Entry;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.utilities.AssumedValue;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobal;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

@ExportLibrary(InteropLibrary.class)
@SuppressWarnings("static-method")
public class LLVMScope implements TruffleObject {

    private final HashMap<String, LLVMSymbol> symbols;
    private final ArrayList<String> functionKeys;
    private final HashMap<String, String> linkageNames;

    public LLVMScope() {
        this.symbols = new HashMap<>();
        this.functionKeys = new ArrayList<>();
        this.linkageNames = new HashMap<>();
    }

    @TruffleBoundary
    public LLVMSymbol get(String name) {
        return symbols.get(name);
    }

    @TruffleBoundary
    public String getKey(int idx) {
        return functionKeys.get(idx);
    }

    /**
     * Lookup a function in the scope by name. If not found, interpret the name as linkageName and
     * lookup the function by its original name.
     *
     * @param name Function name to lookup.
     * @return A handle to the function if found, null otherwise.
     */
    @TruffleBoundary
    public LLVMFunction getFunction(String name) {
        LLVMSymbol symbol = get(name);
        if (symbol != null && symbol.isFunction()) {
            return symbol.asFunction();
        }
        final String newName = linkageNames.get(name);
        if (newName != null) {
            symbol = get(newName);
            if (symbol != null && symbol.isFunction()) {
                return symbol.asFunction();
            }
        }

        return null;
    }

    /**
     * Add a tuple of function name and function linkage name to the map.
     *
     * @param name Function name as specified in original (e.g. C/C++) source.
     * @param linkageName Function name in LLVM code if @param name has been changed during
     *            compilation to LLVM bitcode.
     */
    public void registerLinkageName(String name, String linkageName) {
        linkageNames.put(name, linkageName);
    }

    /**
     * Lookup a global variable in the scope by name.
     *
     * @param name Variable name to lookup.
     * @return A handle to the global if found, null otherwise.
     */
    @TruffleBoundary
    public LLVMGlobal getGlobalVariable(String name) {
        LLVMSymbol symbol = get(name);
        if (symbol != null && symbol.isGlobalVariable()) {
            return symbol.asGlobalVariable();
        }
        return null;
    }

    @TruffleBoundary
    public void register(LLVMSymbol symbol) {
        LLVMSymbol existing = symbols.get(symbol.getName());
        if (existing == null) {
            put(symbol.getName(), symbol);
        } else {
            assert existing == symbol;
        }
    }

    @TruffleBoundary
    public boolean contains(String name) {
        return symbols.containsKey(name);
    }

    @TruffleBoundary
    public boolean exports(LLVMContext context, String name) {
        LLVMSymbol localSymbol = get(name);
        LLVMSymbol globalSymbol = context.getGlobalScope().get(name);
        return localSymbol != null && localSymbol == globalSymbol;
    }

    public boolean isEmpty() {
        return symbols.isEmpty();
    }

    @TruffleBoundary
    public void addMissingEntries(LLVMScope other) {
        for (Entry<String, LLVMSymbol> entry : other.symbols.entrySet()) {
            symbols.putIfAbsent(entry.getKey(), entry.getValue());
        }
        for (Entry<String, String> entry : other.linkageNames.entrySet()) {
            linkageNames.putIfAbsent(entry.getKey(), entry.getValue());
        }
    }

    @TruffleBoundary
    public Collection<LLVMSymbol> values() {
        return symbols.values();
    }

    @TruffleBoundary
    public void rename(String oldName, LLVMSymbol symbol) {
        remove(oldName);
        register(symbol);
    }

    public TruffleObject getKeys() {
        return new Keys(this);
    }

    private void put(String name, LLVMSymbol symbol) {
        assert !symbols.containsKey(name);
        symbols.put(name, symbol);

        if (symbol.isFunction()) {
            assert !functionKeys.contains(name);
            assert functionKeys.size() < symbols.size();
            functionKeys.add(name);
        }
    }

    private void remove(String name) {
        assert symbols.containsKey(name);
        LLVMSymbol removedSymbol = symbols.remove(name);

        if (removedSymbol.isFunction()) {
            boolean contained = functionKeys.remove(name);
            assert contained;
        }
    }

    @ExportMessage
    boolean hasMembers() {
        return true;
    }

    @ExportMessage
    Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
        return getKeys();
    }

    @ExportMessage
    boolean isMemberReadable(@SuppressWarnings("unused") String name) {
        return contains(name);
    }

    @ExportMessage
    Object readMember(String globalName,
                    @Cached BranchProfile exception,
                    @CachedContext(LLVMLanguage.class) LLVMContext context) throws UnknownIdentifierException {

        if (contains(globalName)) {
            LLVMSymbol symbol = get(globalName);
            if (symbol != null && symbol.isFunction()) {
                if (symbol.hasValidIndexAndID()) {
                    int index = symbol.getSymbolIndex(false);
                    int bitcodeID = symbol.getBitcodeID(false);
                    if (context.symbolTableExists(bitcodeID)) {
                        AssumedValue<LLVMPointer>[] symbolTable = context.findSymbolTable(bitcodeID);
                        if (index < symbolTable.length) {
                            LLVMPointer pointer = symbolTable[index].get();
                            return LLVMManagedPointer.cast(pointer).getObject();
                        }
                    }
                }
                exception.enter();
                throw UnknownIdentifierException.create(globalName);
            }
            return symbol;
        }
        exception.enter();
        throw UnknownIdentifierException.create(globalName);
    }

    @ExportLibrary(InteropLibrary.class)
    static final class Keys implements TruffleObject {

        private final LLVMScope scope;

        private Keys(LLVMScope scope) {
            this.scope = scope;
        }

        @ExportMessage
        boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        long getArraySize() {
            return scope.functionKeys.size();
        }

        @ExportMessage
        boolean isArrayElementReadable(long index) {
            return 0 <= index && index < getArraySize();
        }

        @ExportMessage
        Object readArrayElement(long index,
                        @Cached BranchProfile exception) throws InvalidArrayIndexException {
            if (isArrayElementReadable(index)) {
                return scope.getKey((int) index);
            } else {
                exception.enter();
                throw InvalidArrayIndexException.create(index);
            }
        }
    }
}
