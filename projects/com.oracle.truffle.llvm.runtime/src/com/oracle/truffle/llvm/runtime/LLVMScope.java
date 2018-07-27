/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.CanResolve;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobal;

public final class LLVMScope implements TruffleObject {

    private final HashMap<String, LLVMSymbol> symbols;
    private final ArrayList<String> functionKeys;

    public LLVMScope() {
        this.symbols = new HashMap<>();
        this.functionKeys = new ArrayList<>();
    }

    @TruffleBoundary
    public LLVMSymbol get(String name) {
        return symbols.get(name);
    }

    @TruffleBoundary
    public LLVMFunctionDescriptor getFunction(String name) {
        LLVMSymbol symbol = get(name);
        if (symbol != null && symbol.isFunction()) {
            return symbol.asFunction();
        }
        throw new IllegalStateException("Unknown function: " + name);
    }

    @TruffleBoundary
    public LLVMGlobal getGlobalVariable(String name) {
        LLVMSymbol symbol = get(name);
        if (symbol != null && symbol.isGlobalVariable()) {
            return symbol.asGlobalVariable();
        }
        throw new IllegalStateException("Unknown global: " + name);
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

    @Override
    public ForeignAccess getForeignAccess() {
        return LLVMGlobalScopeMessageResolutionForeign.ACCESS;
    }

    private void put(String name, LLVMSymbol symbol) {
        assert !symbols.containsKey(name);
        symbols.put(name, symbol);

        if (symbol.isFunction()) {
            assert !functionKeys.contains(name);
            assert functionKeys.size() < symbols.size();
            functionKeys.add(stripAtCharacter(name));
        }
    }

    private void remove(String name) {
        assert symbols.containsKey(name);
        LLVMSymbol removedSymbol = symbols.remove(name);

        if (removedSymbol.isFunction()) {
            boolean contained = functionKeys.remove(stripAtCharacter(name));
            assert contained;
        }
    }

    private static String stripAtCharacter(String name) {
        return name.charAt(0) == '@' ? name.substring(1) : name;
    }

    @MessageResolution(receiverType = LLVMScope.class)
    static class LLVMGlobalScopeMessageResolution {

        @CanResolve
        abstract static class CanResolveLLVMScopeNode extends Node {

            public boolean test(TruffleObject receiver) {
                return receiver instanceof LLVMScope;
            }
        }

        @Resolve(message = "KEYS")
        public abstract static class KeysOfLLVMScope extends Node {

            protected Object access(LLVMScope receiver) {
                return receiver.getKeys();
            }
        }

        @Resolve(message = "READ")
        public abstract static class ReadFromLLVMScope extends Node {

            protected Object access(LLVMScope scope, String globalName) {
                String atname = "@" + globalName; // for interop
                if (scope.contains(atname)) {
                    return scope.get(atname);
                }
                if (scope.contains(globalName)) {
                    return scope.get(globalName);
                }
                return null;
            }
        }
    }

    @MessageResolution(receiverType = Keys.class)
    static final class Keys implements TruffleObject {

        private final LLVMScope scope;

        private Keys(LLVMScope scope) {
            this.scope = scope;
        }

        static boolean isInstance(TruffleObject obj) {
            return obj instanceof Keys;
        }

        @Override
        public ForeignAccess getForeignAccess() {
            return KeysForeign.ACCESS;
        }

        @Resolve(message = "GET_SIZE")
        abstract static class GetSize extends Node {

            int access(Keys receiver) {
                return receiver.scope.functionKeys.size();
            }
        }

        @Resolve(message = "READ")
        abstract static class Read extends Node {

            Object access(Keys receiver, int index) {
                try {
                    return receiver.scope.functionKeys.get(index);
                } catch (IndexOutOfBoundsException ex) {
                    CompilerDirectives.transferToInterpreter();
                    throw UnknownIdentifierException.raise(Integer.toString(index));
                }
            }
        }
    }
}
