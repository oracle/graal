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
import com.oracle.truffle.api.dsl.GenerateAOT;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType.Method;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

@GenerateUncached
public abstract class LLVMInteropMethodInvokeNode extends LLVMNode {
    abstract Object execute(LLVMPointer receiver, String methodName, LLVMInteropType.Clazz type, Method method, long virtualIndex, Object[] argumentsWithSelf)
                    throws UnsupportedTypeException, ArityException, UnsupportedMessageException, UnknownIdentifierException;

    public static LLVMInteropMethodInvokeNode create() {
        return LLVMInteropMethodInvokeNodeGen.create();
    }

    static boolean isVirtual(long virtualIndex) {
        return virtualIndex >= 0;
    }

    /**
     * @param methodName
     * @param type
     * @param method
     * @param typeHash
     */
    @ExplodeLoop
    @Specialization(guards = {"isVirtual(virtualIndex)", "type==typeHash"})
    @GenerateAOT.Exclude
    Object doVirtualCallCached(LLVMPointer receiver, String methodName, LLVMInteropType.Clazz type,
                    Method method, long virtualIndex, Object[] arguments,
                    @CachedLibrary(limit = "5") InteropLibrary interop,
                    @Cached(value = "type") LLVMInteropType.Clazz typeHash,
                    @Cached(value = "type.getVtableAccessNames()", allowUncached = true, dimensions = 1) String[] vtableHelpNames,
                    @Cached LLVMInteropVtableAccessNode vtableAccessNode)
                    throws UnsupportedTypeException, ArityException, UnsupportedMessageException,
                    UnknownIdentifierException {
        Object curReceiver = receiver;
        for (String name : vtableHelpNames) {
            curReceiver = interop.readMember(curReceiver, name);
        }
        return vtableAccessNode.execute(curReceiver, virtualIndex, arguments);
    }

    /**
     * @param methodName
     * @param method
     */
    @ExplodeLoop
    @Specialization(guards = "isVirtual(virtualIndex)", replaces = "doVirtualCallCached")
    @GenerateAOT.Exclude
    Object doVirtualCall(LLVMPointer receiver, String methodName, LLVMInteropType.Clazz type, Method method, long virtualIndex, Object[] arguments,
                    @CachedLibrary(limit = "5") InteropLibrary interop,
                    @Cached LLVMInteropVtableAccessNode vtableAccessNode)
                    throws UnsupportedTypeException, ArityException, UnsupportedMessageException, UnknownIdentifierException {
        String[] vtableAccessNames = type.getVtableAccessNames();
        Object curReceiver = receiver;
        for (String name : vtableAccessNames) {
            curReceiver = interop.readMember(curReceiver, name);
        }
        return vtableAccessNode.execute(curReceiver, virtualIndex, arguments);
    }

    /**
     * @param virtualIndex
     */
    @Specialization(guards = "!isVirtual(virtualIndex)")
    Object doNonvirtualCall(LLVMPointer receiver, String methodName, LLVMInteropType.Clazz type, Method method, long virtualIndex, Object[] arguments,
                    @Cached LLVMInteropNonvirtualCallNode call)
                    throws UnsupportedMessageException, UnsupportedTypeException, ArityException {
        return call.execute(receiver, type, methodName, method, arguments);
    }

}
