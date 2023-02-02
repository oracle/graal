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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateAOT;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.llvm.runtime.LLVMFunction;
import com.oracle.truffle.llvm.runtime.except.LLVMLinkerException;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType.Clazz;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType.Method;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.nodes.others.LLVMDynAccessSymbolNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

@GenerateUncached
public abstract class LLVMInteropNonvirtualCallNode extends LLVMNode {
    abstract Object execute(LLVMPointer receiver, LLVMInteropType.Clazz type, String methodName, Method method, Object[] argumentsWithSelf)
                    throws UnsupportedTypeException, ArityException, UnsupportedMessageException;

    public static LLVMInteropNonvirtualCallNode create() {
        return LLVMInteropNonvirtualCallNodeGen.create();
    }

    /**
     * @param receiver
     * @param type
     * @param methodName
     * @param method
     * @param argCount
     * @param llvmFunction
     */
    @Specialization(guards = {"argCount==arguments.length", "llvmFunction!=null", "methodName==method.getName()", "type==method.getObjectClass()", "type==asClazz(receiver)"})
    @GenerateAOT.Exclude
    Object doCached(LLVMPointer receiver, LLVMInteropType.Clazz type, String methodName, Method method, Object[] arguments,
                    @CachedLibrary(limit = "5") InteropLibrary interop, @Cached(value = "arguments.length", allowUncached = true) int argCount,
                    @Cached(value = "getLLVMFunctionUncached(method, type)", allowUncached = true) LLVMFunction llvmFunction,
                    @Cached LLVMDynAccessSymbolNode accessSymbolNode)
                    throws UnsupportedTypeException, ArityException, UnsupportedMessageException {
        return interop.execute(accessSymbolNode.execute(llvmFunction), arguments);
    }

    /**
     * @param receiver
     * @param method
     */
    @Specialization
    @GenerateAOT.Exclude
    Object doResolve(LLVMPointer receiver, LLVMInteropType.Clazz type, String methodName, Method method, Object[] arguments,
                    @Cached LLVMDynAccessSymbolNode dynAccessSymbolNode,
                    @CachedLibrary(limit = "5") InteropLibrary interop,
                    @Cached BranchProfile notFound)
                    throws UnsupportedTypeException, ArityException, UnsupportedMessageException {
        Method newMethod = type.findMethodByArgumentsWithSelf(methodName, arguments);
        LLVMFunction newLLVMFunction = getLLVMFunction(newMethod, type, notFound);
        Object newReceiver = dynAccessSymbolNode.execute(newLLVMFunction);
        return interop.execute(newReceiver, arguments);
    }

    @TruffleBoundary
    private static String mkErrorMessage(LLVMInteropType.Clazz clazz, Method method) {
        String clazzName = clazz.toString().startsWith("class ") ? clazz.toString().substring(6) : clazz.toString();
        return String.format("No implementation of declared method %s::%s (%s) found", clazzName, method.getName(), method.getLinkageName());
    }

    final LLVMFunction getLLVMFunctionUncached(Method method, LLVMInteropType.Clazz clazz) {
        return getLLVMFunction(method, clazz, BranchProfile.getUncached());
    }

    final LLVMFunction getLLVMFunction(Method method, LLVMInteropType.Clazz clazz, BranchProfile notFound) {
        LLVMFunction llvmFunction = getContext().getGlobalScopeChain().getFunction(method.getLinkageName());
        if (llvmFunction == null) {
            notFound.enter();
            throw new LLVMLinkerException(this, mkErrorMessage(clazz, method));
        }
        return llvmFunction;
    }

    static LLVMInteropType.Clazz asClazz(LLVMPointer receiver) throws UnsupportedTypeException {
        LLVMInteropType type = receiver.getExportType();
        if (type instanceof LLVMInteropType.Clazz) {
            return (Clazz) type;
        } else {
            throw UnsupportedTypeException.create(new Object[]{receiver});
        }
    }
}
