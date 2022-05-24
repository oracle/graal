/*
 * Copyright (c) 2022, Oracle and/or its affiliates.
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
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.GenerateAOT;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.llvm.runtime.interop.export.LLVMForeignGetMemberPointerNode;
import com.oracle.truffle.llvm.runtime.interop.export.LLVMForeignReadNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

@GenerateUncached
public abstract class LLVMInteropReadMemberNode extends LLVMNode {

    public static LLVMInteropReadMemberNode create() {
        return LLVMInteropReadMemberNodeGen.create();
    }

    public abstract Object execute(LLVMPointer receiver, String ident, LLVMInteropType type) throws UnsupportedMessageException, UnknownIdentifierException;

    /**
     * @param swiftClass
     */
    @Specialization
    @GenerateAOT.Exclude
    public Object readSwiftClassMember(LLVMPointer receiver, String ident, LLVMInteropType.SwiftClass swiftClass,
                    @Cached LLVMForeignReadNode read) throws UnknownIdentifierException {
        final BranchProfile symbolNotFound = BranchProfile.create();
        String functionFound = ident.startsWith("$") ? ident : getContext().getGlobalScopeChain().getMangledName(ident);
        if (functionFound != null) {
            long[] symbolOffsets = getContext().getGlobalScopeChain().getSymbolOffsets(functionFound);
            if (symbolOffsets != null) {
                LLVMPointer currentBase = receiver;
                for (long idx : symbolOffsets) {
                    currentBase = currentBase.increment(idx * 8);
                    currentBase = LLVMPointer.cast(read.execute(currentBase, LLVMInteropType.ValueKind.POINTER.type));
                }

                return new SwiftMethodMember(currentBase, receiver);
            }
        }
        symbolNotFound.enter();
        throw UnknownIdentifierException.create(functionFound);
    }

    static boolean isSwiftClass(LLVMInteropType type) {
        return type instanceof LLVMInteropType.SwiftClass;
    }

    static boolean isCppClass(LLVMInteropType type) {
        return type instanceof LLVMInteropType.CppClass;
    }

    @Specialization
    @GenerateAOT.Exclude
    public Object readCppClassMember(LLVMPointer receiver, String ident, LLVMInteropType.CppClass cppClass,
                    @Cached LLVMResolveForeignClassChainNode resolveClassChain,
                    @Cached LLVMForeignGetMemberPointerNode getElementPointer,
                    @Exclusive @Cached LLVMForeignReadNode read) throws UnsupportedMessageException, UnknownIdentifierException {
        LLVMPointer correctClassPtr = resolveClassChain.execute(receiver, ident, cppClass);
        LLVMPointer ptr = getElementPointer.execute(correctClassPtr.getExportType(), correctClassPtr, ident);
        return read.execute(ptr, ptr.getExportType());

    }

    @Specialization(guards = {"!isSwiftClass(type)", "!isCppClass(type)"})
    @GenerateAOT.Exclude
    public Object readSimpleMember(LLVMPointer receiver, String ident, LLVMInteropType type,
                    @Cached LLVMForeignGetMemberPointerNode getElementPointer,
                    @Exclusive @Cached LLVMForeignReadNode read) throws UnsupportedMessageException, UnknownIdentifierException {
        LLVMPointer ptr = getElementPointer.execute(type, receiver, ident);
        return read.execute(ptr, ptr.getExportType());

    }

    @ExportLibrary(value = InteropLibrary.class)
    /**
     * represents a class method as an invocable member
     */
    public static final class SwiftMethodMember implements TruffleObject {
        final LLVMPointer methodPointer;
        final LLVMPointer receiver;

        public SwiftMethodMember(LLVMPointer methodPointer, LLVMPointer receiver) {
            this.methodPointer = methodPointer;
            this.receiver = receiver;
        }

        @ExportMessage
        boolean isExecutable(@CachedLibrary(value = "this.methodPointer") InteropLibrary interop) {
            return interop.isExecutable(methodPointer);
        }

        @ExportMessage
        Object execute(Object[] args,
                        @Cached LLVMSelfArgumentPackNode selfArgumentPackNode,
                        @CachedLibrary(value = "this.methodPointer") InteropLibrary interop) throws UnsupportedTypeException, ArityException, UnsupportedMessageException {
            final Object[] selfArgs = selfArgumentPackNode.execute(receiver, args, false);
            return interop.execute(methodPointer, selfArgs);
        }
    }
}
