/*
 * Copyright (c) 2018, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.interop;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.llvm.runtime.except.LLVMPolyglotException;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;

public abstract class LLVMAsForeignNode extends LLVMNode {

    final boolean allowNonForeign;

    protected LLVMAsForeignNode(boolean allowNonForeign) {
        this.allowNonForeign = allowNonForeign;
    }

    public abstract TruffleObject execute(LLVMManagedPointer pointer);

    public static LLVMAsForeignNode create() {
        return LLVMAsForeignNodeGen.create(false);
    }

    public static LLVMAsForeignNode createOptional() {
        return LLVMAsForeignNodeGen.create(true);
    }

    @Specialization(guards = "isForeign(pointer)")
    TruffleObject doForeign(LLVMManagedPointer pointer) {
        LLVMTypedForeignObject foreign = (LLVMTypedForeignObject) pointer.getObject();
        return foreign.getForeign();
    }

    @Specialization(guards = {"allowNonForeign", "!isForeign(pointer)"})
    TruffleObject doOther(@SuppressWarnings("unused") LLVMManagedPointer pointer) {
        return null;
    }

    @Specialization(guards = {"!allowNonForeign", "!isForeign(pointer)"})
    @TruffleBoundary
    TruffleObject doFail(@SuppressWarnings("unused") LLVMManagedPointer pointer) {
        throw new LLVMPolyglotException(this, "Pointer does not point to a polyglot value.");
    }

    protected static boolean isForeign(LLVMManagedPointer pointer) {
        return pointer.getOffset() == 0 && pointer.getObject() instanceof LLVMTypedForeignObject;
    }
}
