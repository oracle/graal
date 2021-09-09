/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates.
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

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateAOT;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType.Method;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

@GenerateUncached
public abstract class LLVMInteropInvokeNode extends LLVMNode {
    public abstract Object execute(LLVMPointer receiver, LLVMInteropType type, String member, Object[] arguments)
                    throws ArityException, UnknownIdentifierException, UnsupportedTypeException, UnsupportedMessageException;

    public static LLVMInteropInvokeNode create() {
        return LLVMInteropInvokeNodeGen.create();
    }

    @Specialization
    @GenerateAOT.Exclude
    Object doClazz(LLVMPointer receiver, LLVMInteropType.Clazz type, String method, Object[] arguments,
                    @Cached LLVMInteropMethodInvokeNode invoke,
                    @Cached LLVMSelfArgumentPackNode selfPackNode,
                    @CachedLibrary(limit = "5") InteropLibrary interop)
                    throws ArityException, UnknownIdentifierException, UnsupportedTypeException, UnsupportedMessageException {
        Object[] selfArgs = selfPackNode.execute(receiver, arguments);
        Method methodObject = type.findMethodByArgumentsWithSelf(method, selfArgs);
        if (methodObject == null) {
            return doStruct(receiver, type, method, arguments, interop);
        }
        long virtualIndex = methodObject.getVirtualIndex();
        return invoke.execute(receiver, method, type, methodObject, virtualIndex, selfArgs);
    }

    /**
     * @param type
     */
    @Specialization(guards = "!isClass(type)")
    @GenerateAOT.Exclude
    Object doStruct(LLVMPointer receiver, LLVMInteropType.Struct type, String member, Object[] arguments,
                    @CachedLibrary(limit = "5") InteropLibrary interop)
                    throws UnsupportedMessageException, UnknownIdentifierException, UnsupportedTypeException, ArityException {
        Object readMember = interop.readMember(receiver, member);
        return interop.execute(readMember, arguments);
    }

    protected static boolean isClass(Object o) {
        return o instanceof LLVMInteropType.Clazz;
    }

    /**
     * @param receiver
     * @param type
     * @param member
     * @param arguments
     */
    @Fallback
    Object doError(LLVMPointer receiver, LLVMInteropType type, String member, Object[] arguments) throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

}
