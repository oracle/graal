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

import com.oracle.truffle.llvm.runtime.global.LLVMGlobalVariable;

public final class LLVMGlobalVariableRegistry {
    private final Map<String, Object> globals = new HashMap<>();

    public synchronized boolean exists(String name) {
        return globals.containsKey(name);
    }

    public synchronized void add(String name, Object global) {
        if (global instanceof LLVMGlobalVariable) {
            System.err.println();
        }
        if (exists(name)) {
            throw new IllegalStateException("Global " + name + " already added.");
        }
        globals.put(name, global);
    }

    public synchronized Object lookup(String name) {
        if (exists(name)) {
            return globals.get(name);
        }
        throw new IllegalStateException("Global " + name + " does not exist.");
    }

    public synchronized Object lookupOrCreate(String name, Supplier<Object> generator) {
        if (exists(name)) {
            return lookup(name);
        } else {
            Object variable = generator.get();
            add(name, variable);
            return variable;
        }
    }
}
