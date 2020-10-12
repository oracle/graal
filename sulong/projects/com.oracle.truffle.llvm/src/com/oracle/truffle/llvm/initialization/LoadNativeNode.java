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
package com.oracle.truffle.llvm.initialization;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.NFIContextExtension;
import com.oracle.truffle.llvm.runtime.except.LLVMParserException;

public class LoadNativeNode extends RootNode {

    final String sourceName;
    final CallTarget callTarget;
    final LLVMContext context;

    private LoadNativeNode(String name, FrameDescriptor rootFrame, LLVMContext context, LLVMLanguage language, CallTarget callTarget) {
        super(language, rootFrame);
        this.callTarget = callTarget;
        this.sourceName = name;
        this.context = context;
    }

    public static LoadNativeNode create(String name, FrameDescriptor rootFrame, LLVMContext context, LLVMLanguage language, CallTarget callTarget) {
        return new LoadNativeNode(name, rootFrame, context, language, callTarget);
    }

    @Override
    public Object execute(VirtualFrame frame) {
        LoadModulesNode.LLVMLoadingPhase phase;
        if (frame.getArguments().length > 0 && (frame.getArguments()[0] instanceof LoadModulesNode.LLVMLoadingPhase)) {
            phase = (LoadModulesNode.LLVMLoadingPhase) frame.getArguments()[0];
        } else {
            throw new LLVMParserException("LoadNativeNode is called with unexpected arguments");
        }

        if (LoadModulesNode.LLVMLoadingPhase.INIT_SYMBOLS.isActive(phase)) {
            addLibraryHandler(callTarget.call());
        }

        return null;
    }

    @CompilerDirectives.TruffleBoundary
    private void addLibraryHandler(Object library) {
        NFIContextExtension nfiContextExtension = context.getContextExtensionOrNull(NFIContextExtension.class);
        if (nfiContextExtension != null) {
            nfiContextExtension.addLibraryHandles(library);
        }
    }

}
