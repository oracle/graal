/*
 * Copyright (c) 2020, Oracle and/or its affiliates.
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

package com.oracle.truffle.llvm.runtime.interop.access;

import org.graalvm.collections.Pair;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType.Struct;
import com.oracle.truffle.llvm.runtime.interop.export.LLVMForeignDirectSuperElemPtrNode;
import com.oracle.truffle.llvm.runtime.interop.export.LLVMForeignGetSuperElemPtrNode;
import com.oracle.truffle.llvm.runtime.interop.export.LLVMForeignVirtualSuperElemPtrNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

@GenerateUncached
public abstract class LLVMResolveForeignClassChainNode extends LLVMNode {
    public abstract LLVMPointer execute(LLVMPointer receiver, String ident, LLVMInteropType exportType) throws UnknownIdentifierException;

    /**
     * @param ident
     * @param clazz
     * @param cachedIdent
     * @param cachedClazz
     */
    @Specialization(guards = {"ident.equals(cachedIdent)", "clazz==cachedClazz"})
    public LLVMPointer doClassResolvingCached(LLVMPointer receiver, String ident, LLVMInteropType.Clazz clazz,
                    @Cached(value = "ident", allowUncached = true) String cachedIdent,
                    @Cached(value = "clazz", allowUncached = true) LLVMInteropType.Clazz cachedClazz,
                    @Cached LLVMForeignVirtualSuperElemPtrNode virtualSuperElemPtrNode,
                    @Cached LLVMForeignDirectSuperElemPtrNode directSuperElemPtrNode,
                    @Cached(value = "clazz.getSuperOffsetInformation(ident)", allowUncached = true) Pair<long[], Struct> p,
                    @Cached(value = "p.getLeft()") long[] offsetInformation) {
        LLVMPointer curReceiver = receiver;
        for (long val : offsetInformation) {
            LLVMForeignGetSuperElemPtrNode access = (val & 1) == 1 ? virtualSuperElemPtrNode : directSuperElemPtrNode;
            curReceiver = access.execute(curReceiver, val >> 1);
        }
        return curReceiver.export(p.getRight() == null ? clazz : p.getRight());
    }

    @Specialization(replaces = "doClassResolvingCached")
    public LLVMPointer doClazzResolving(LLVMPointer receiver, String ident, LLVMInteropType.Clazz clazz,
                    @Cached LLVMForeignVirtualSuperElemPtrNode virtualSuperElemPtrNode,
                    @Cached LLVMForeignDirectSuperElemPtrNode directSuperElemPtrNode) throws UnknownIdentifierException {
        LLVMPointer curReceiver = receiver;
        Pair<long[], Struct> p = clazz.getSuperOffsetInformation(ident);
        for (long val : p.getLeft()) {
            LLVMForeignGetSuperElemPtrNode access = (val & 1) == 1 ? virtualSuperElemPtrNode : directSuperElemPtrNode;
            curReceiver = access.execute(curReceiver, val >> 1);
        }
        return curReceiver.export(p.getRight() == null ? clazz : p.getRight());
    }

    static boolean isClazzType(Object o) {
        return o instanceof LLVMInteropType.Clazz;
    }

    /**
     * @param receiver
     * @param ident
     * @param type
     */
    @Specialization(guards = "!isClazzType(type)")
    public LLVMPointer doNothing(LLVMPointer receiver, String ident, LLVMInteropType type) {
        // since the exporttype of 'receiver' is no class, no resolving is needed
        return receiver;
    }
}
