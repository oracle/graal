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
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.SulongLibrary;

@ExportLibrary(value = InteropLibrary.class)
public class LLVMInteropSwiftClassAccess implements TruffleObject {
    public final SulongLibrary sulongLibrary;
    private final LLVMFunctionDescriptor functionDescriptor;
    private final String className;

    public LLVMInteropSwiftClassAccess(SulongLibrary sulongLibrary, LLVMFunctionDescriptor functionDescriptor, String className) {
        this.sulongLibrary = sulongLibrary;
        this.functionDescriptor = functionDescriptor;
        this.className = className;
    }

    @ExportMessage
    public boolean hasMembers() {
        return !LLVMLanguage.getContext().getGlobalScopeChain().getMangledNames(className).isEmpty();
    }

    /**
     * @param includeInternal
     */
    @SuppressWarnings("static-method")
    @ExportMessage
    final Object getMembers(boolean includeInternal) {
        return LLVMLanguage.getContext().getGlobalScopeChain().getMangledNames(className);
    }

    @ExportMessage
    public boolean isMemberInvocable(String member,
                    @CachedLibrary(limit = "5") InteropLibrary interop) {
        return interop.isMemberInvocable(sulongLibrary, member);
    }

    @ExportMessage
    public Object invokeMember(String member, Object[] arguments,
                    @Cached LLVMSelfArgumentPackNode argumentPackNode,
                    @CachedLibrary(limit = "5") InteropLibrary interop)
                    throws UnsupportedMessageException, ArityException, UnknownIdentifierException, UnsupportedTypeException {
        final Object[] newargs = argumentPackNode.execute(functionDescriptor, arguments, false);
        String mangledName = LLVMLanguage.getContext().getGlobalScopeChain().getMangledName(className, member);
        return interop.invokeMember(sulongLibrary, mangledName == null ? member : mangledName, newargs);
    }

}
