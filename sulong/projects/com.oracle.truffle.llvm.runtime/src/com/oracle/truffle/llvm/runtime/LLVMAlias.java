/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates.
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

import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.Equivalence;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.llvm.runtime.except.LLVMLinkerException;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobal;

public class LLVMAlias extends LLVMSymbol {

    @CompilationFinal private LLVMSymbol target;

    public LLVMAlias(ExternalLibrary library, String name, LLVMSymbol target, boolean exported) {
        super(name, library, LLVMSymbol.INVALID_ID, LLVMSymbol.INVALID_ID, exported);
        setTarget(target);
    }

    public LLVMSymbol getTarget() {
        return target;
    }

    public void setTarget(LLVMSymbol value) {
        this.target = value;
        if (target instanceof LLVMAlias) {
            EconomicSet<LLVMAlias> visited = EconomicSet.create(Equivalence.IDENTITY);
            checkForCycle(this, visited);
        }
    }

    @Override
    public boolean isDefined() {
        return true;
    }

    @Override
    public boolean isFunction() {
        return target.isFunction();
    }

    @Override
    public boolean isGlobalVariable() {
        return target.isGlobalVariable();
    }

    @Override
    public boolean isAlias() {
        return true;
    }

    @Override
    public LLVMFunction asFunction() {
        return target.asFunction();
    }

    @Override
    public LLVMGlobal asGlobalVariable() {
        return target.asGlobalVariable();
    }

    @Override
    public String toString() {
        return super.getName() + " -> " + target.toString();
    }

    private void checkForCycle(LLVMAlias alias, EconomicSet<LLVMAlias> visited) {
        if (visited.contains(alias)) {
            throw new LLVMLinkerException("Found a cycle between the following aliases: " + visited.toString());
        }
        visited.add(alias);
        if (alias.getTarget() instanceof LLVMAlias) {
            checkForCycle((LLVMAlias) alias.getTarget(), visited);
        }
    }
}
