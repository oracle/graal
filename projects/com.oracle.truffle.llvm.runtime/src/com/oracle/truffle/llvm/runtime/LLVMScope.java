/*
 * Copyright (c) 2017, Oracle and/or its affiliates.
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

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.llvm.runtime.LLVMContext.FunctionFactory;
import com.oracle.truffle.llvm.runtime.types.FunctionType;
import com.oracle.truffle.llvm.runtime.types.MetaType;
import com.oracle.truffle.llvm.runtime.types.Type;

public final class LLVMScope {

    private final HashMap<String, LLVMFunctionHandle> functions;
    private final LLVMScope parent;
    private final LLVMGlobalVariableRegistry globalVariableRegistry;

    public static synchronized LLVMScope createFileScope(LLVMContext context) {
        return new LLVMScope(context.getGlobalScope());
    }

    public static synchronized LLVMScope createGlobalScope(LLVMContext context) {
        LLVMScope scope = new LLVMScope(null);
        LLVMFunctionDescriptor zeroFunction = scope.lookupOrCreateFunction(context, "<zero function>", true,
                        idx -> LLVMFunctionDescriptor.createDescriptor(context, "<zero function>", new FunctionType(MetaType.UNKNOWN, new Type[0], false), idx));
        assert LLVMFunction.getSulongFunctionIndex(zeroFunction.getFunctionPointer()) == 0;
        return scope;
    }

    private LLVMScope(LLVMScope parent) {
        this.functions = new HashMap<>();
        this.parent = parent;
        this.globalVariableRegistry = new LLVMGlobalVariableRegistry();
    }

    @TruffleBoundary
    public synchronized LLVMFunctionDescriptor getFunctionDescriptor(LLVMContext context, String name) {
        LLVMFunctionHandle functionHandle = functions.get(name);
        if (functionHandle != null) {
            return context.getFunctionDescriptor(functionHandle);
        } else if (functionHandle == null && parent != null) {
            return parent.getFunctionDescriptor(context, name);
        }
        throw new IllegalStateException("Unknown function: " + name);
    }

    @TruffleBoundary
    public synchronized boolean functionExists(String name) {
        return functions.containsKey(name) || (parent != null && parent.functionExists(name));
    }

    @TruffleBoundary
    public synchronized boolean globalExists(String name) {
        return globalVariableRegistry.exists(name) || (parent != null && parent.globalExists(name));
    }

    @TruffleBoundary
    public synchronized Object getGlobalVariable(String name) {
        if (globalVariableRegistry.exists(name)) {
            return globalVariableRegistry.lookup(name);
        } else if (parent != null) {
            return parent.getGlobalVariable(name);
        } else {
            throw new IllegalStateException("Unknown global: " + name);
        }
    }

    @TruffleBoundary
    public synchronized Object lookupOrCreateGlobal(String name, boolean global, Supplier<Object> generator) {
        if (global && parent != null) {
            // insert non-file-internal (global) variables in the top level (global) scope
            assert !globalVariableRegistry.exists(name) : "Global is already defined in file-local scope";
            return parent.lookupOrCreateGlobal(name, global, generator);
        }
        assert global || parent != null;
        return globalVariableRegistry.lookupOrCreate(name, generator);
    }

    @TruffleBoundary
    public synchronized LLVMFunctionDescriptor lookupOrCreateFunction(LLVMContext context, String name, boolean global, FunctionFactory generator) {
        if (global && parent != null) {
            // insert non-file-internal (global) function in the top level (global) scope
            assert !functions.containsKey(name) : "Function is already defined in file-local scope";
            return parent.lookupOrCreateFunction(context, name, global, generator);
        }
        assert global || parent != null;
        if (functions.containsKey(name)) {
            return context.getFunctionDescriptor(functions.get(name));
        } else {
            LLVMFunctionDescriptor functionDescriptor = context.createFunctionDescriptor(generator);
            functions.put(name, LLVMFunctionHandle.createHandle(functionDescriptor.getFunctionPointer()));
            return functionDescriptor;
        }
    }

    private static final class LLVMGlobalVariableRegistry {
        private final Map<String, Object> globals = new HashMap<>();

        synchronized boolean exists(String name) {
            return globals.containsKey(name);
        }

        synchronized void add(String name, Object global) {
            if (exists(name)) {
                throw new IllegalStateException("Global " + name + " already added.");
            }
            globals.put(name, global);
        }

        synchronized Object lookup(String name) {
            if (exists(name)) {
                return globals.get(name);
            }
            throw new IllegalStateException("Global " + name + " does not exist.");
        }

        synchronized Object lookupOrCreate(String name, Supplier<Object> generator) {
            if (exists(name)) {
                return lookup(name);
            } else {
                Object variable = generator.get();
                add(name, variable);
                return variable;
            }
        }
    }

}
