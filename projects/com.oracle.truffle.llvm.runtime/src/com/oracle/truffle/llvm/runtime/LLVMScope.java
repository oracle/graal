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
import java.util.HashMap;
import java.util.Map;
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
import com.oracle.truffle.llvm.runtime.debug.LLVMSourceSymbol;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobal;
import com.oracle.truffle.llvm.runtime.types.FunctionType;
import com.oracle.truffle.llvm.runtime.types.PointerType;

public final class LLVMScope implements TruffleObject {

    private final LLVMFunctionRegistry functionRegistry;
    private final LLVMGlobalRegistry globalVariableRegistry;

    public LLVMScope() {
        this.functionRegistry = new LLVMFunctionRegistry();
        this.globalVariableRegistry = new LLVMGlobalRegistry();
    }

    public LLVMFunctionRegistry functions() {
        return functionRegistry;
    }

    public LLVMGlobalRegistry globals() {
        return globalVariableRegistry;
    }

    public boolean isEmpty() {
        return functions().isEmpty() && globals().isEmpty();
    }

    public void addMissingEntries(LLVMScope other) {
        for (Entry<String, LLVMFunctionDescriptor> entry : other.functionRegistry.functions.entrySet()) {
            if (!this.functions().contains(entry.getKey())) {
                this.functions().put(entry.getKey(), entry.getValue());
            }
        }

        for (Entry<String, LLVMGlobal> entry : other.globalVariableRegistry.globals.entrySet()) {
            if (!this.globals().contains(entry.getKey())) {
                this.globals().put(entry.getKey(), entry.getValue());
            }
        }
    }

    public static final class LLVMFunctionRegistry {
        private final HashMap<String, LLVMFunctionDescriptor> functions;
        private final ArrayList<String> functionKeys;

        private LLVMFunctionRegistry() {
            this.functions = new HashMap<>();
            this.functionKeys = new ArrayList<>();
        }

        @TruffleBoundary
        public LLVMFunctionDescriptor get(String name) {
            LLVMFunctionDescriptor functionDescriptor = functions.get(name);
            if (functionDescriptor != null) {
                return functionDescriptor;
            }
            throw new IllegalStateException("Unknown function: " + name);
        }

        @TruffleBoundary
        public LLVMFunctionDescriptor getOrCreate(LLVMContext context, String name, FunctionType type) {
            if (functions.containsKey(name)) {
                return functions.get(name);
            } else {
                LLVMFunctionDescriptor descriptor = context.createFunctionDescriptor(name, type);
                put(descriptor.getName(), descriptor);
                return descriptor;
            }
        }

        @TruffleBoundary
        public void register(LLVMFunctionDescriptor descriptor) {
            register(descriptor.getName(), descriptor);
        }

        @TruffleBoundary
        public void registerAlias(String name, LLVMFunctionDescriptor descriptor) {
            register(name, descriptor);
        }

        @TruffleBoundary
        public boolean contains(String name) {
            return functions.containsKey(name);
        }

        @TruffleBoundary
        public boolean contains(LLVMFunctionDescriptor descriptor) {
            return functions.get(descriptor.getName()) == descriptor;
        }

        @TruffleBoundary
        public boolean exports(LLVMContext context, String name) {
            if (contains(name) && context.getGlobalScope().functions().contains(name)) {
                LLVMFunctionDescriptor localMainFunctionDescriptor = get(name);
                LLVMFunctionDescriptor globalMainFunctionDescriptor = context.getGlobalScope().functions().get(name);
                return localMainFunctionDescriptor == globalMainFunctionDescriptor;
            }
            return false;
        }

        @TruffleBoundary
        public LLVMFunctionDescriptor[] toArray() {
            return functions.values().toArray(new LLVMFunctionDescriptor[functions.size()]);
        }

        public boolean isEmpty() {
            return functions.isEmpty();
        }

        private void register(String name, LLVMFunctionDescriptor descriptor) {
            LLVMFunctionDescriptor existing = functions.get(name);
            assert existing == null || existing == descriptor;
            if (existing == null) {
                put(name, descriptor);
            }
        }

        private void put(String name, LLVMFunctionDescriptor descriptor) {
            String realName = name;
            if (realName.charAt(0) == '@') {
                realName = realName.substring(1);
            }
            assert !functions.containsKey(name) && !functionKeys.contains(realName);
            assert functionKeys.size() == functions.size();
            functions.put(name, descriptor);
            functionKeys.add(realName);
        }
    }

    public static final class LLVMGlobalRegistry {
        private final Map<String, LLVMGlobal> globals;

        private LLVMGlobalRegistry() {
            globals = new HashMap<>();
        }

        @TruffleBoundary
        public LLVMGlobal get(String name) {
            LLVMGlobal global = globals.get(name);
            if (global != null) {
                return global;
            }
            throw new IllegalStateException("Unknown global: " + name);
        }

        @TruffleBoundary
        public LLVMGlobal getOrCreate(LLVMContext context, String name, PointerType type, LLVMSourceSymbol sourceSymbol, boolean readOnly) {
            if (globals.containsKey(name)) {
                return globals.get(name);
            } else {
                LLVMGlobal global = LLVMGlobal.create(context, name, type, sourceSymbol, readOnly);
                put(name, global);
                return global;
            }
        }

        @TruffleBoundary
        public void register(LLVMGlobal global) {
            register(global.getName(), global);
        }

        @TruffleBoundary
        public void registerAlias(String name, LLVMGlobal global) {
            register(name, global);
        }

        private void register(String name, LLVMGlobal global) {
            LLVMGlobal existing = globals.get(name);
            assert existing == null || existing == global;
            if (existing == null) {
                put(name, global);
            }
        }

        @TruffleBoundary
        public boolean contains(String name) {
            return globals.containsKey(name);
        }

        @TruffleBoundary
        public boolean contains(LLVMGlobal global) {
            return globals.get(global.getName()) == global;
        }

        @TruffleBoundary
        public LLVMGlobal[] toArray() {
            return globals.values().toArray(new LLVMGlobal[globals.size()]);
        }

        public boolean isEmpty() {
            return globals.isEmpty();
        }

        private void put(String name, LLVMGlobal global) {
            globals.put(name, global);
        }

    }

    @Override
    public ForeignAccess getForeignAccess() {
        return LLVMGlobalScopeMessageResolutionForeign.ACCESS;
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
                if (scope.functions().contains(atname)) {
                    return scope.functions().get(atname);
                }
                if (scope.globals().contains(globalName)) {
                    return scope.globals().get(globalName);
                }
                return null;
            }
        }
    }

    public TruffleObject getKeys() {
        return new Keys(this);
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
                return receiver.scope.functions().functionKeys.size();
            }
        }

        @Resolve(message = "READ")
        abstract static class Read extends Node {

            Object access(Keys receiver, int index) {
                try {
                    return receiver.scope.functions().functionKeys.get(index);
                } catch (IndexOutOfBoundsException ex) {
                    CompilerDirectives.transferToInterpreter();
                    throw UnknownIdentifierException.raise(Integer.toString(index));
                }
            }
        }
    }
}
