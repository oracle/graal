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
package com.oracle.truffle.llvm.initialization;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.llvm.parser.LLVMParserResult;
import com.oracle.truffle.llvm.parser.model.GlobalSymbol;
import com.oracle.truffle.llvm.parser.model.symbols.globals.GlobalVariable;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMScope;
import com.oracle.truffle.llvm.runtime.NativeContextExtension;
import com.oracle.truffle.llvm.runtime.NativeContextExtension.WellKnownNativeFunctionNode;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobal;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMStatementNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMPointerLoadNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMPointerStoreNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

import java.util.ArrayList;

public abstract class InitObjcSelectorsNode extends LLVMStatementNode {
    @CompilerDirectives.CompilationFinal(dimensions = 1) private final LLVMGlobal[] selectors;

    public InitObjcSelectorsNode(LLVMParserResult parserResult) {
        ArrayList<LLVMGlobal> selectorList = new ArrayList<>();
        LLVMScope fileScope = parserResult.getRuntime().getFileScope();
        for (GlobalSymbol symbol : parserResult.getDefinedGlobals()) {
            // macOS/objC specific
            if (symbol instanceof GlobalVariable) {
                GlobalVariable globalVar = (GlobalVariable) symbol;
                String sectionName = globalVar.getSectionName();
                if (sectionName != null && sectionName.contains("__objc_selrefs")) {
                    selectorList.add(fileScope.getGlobalVariable(globalVar.getName()));
                }
            }
        }
        this.selectors = selectorList.toArray(LLVMGlobal.EMPTY);
    }

    @Specialization
    void doInit(@Cached("getSelRegisterNameFunc()") WellKnownNativeFunctionNode selRegisterName,
                    @Cached BranchProfile branchProfile,
                    @Cached LLVMPointerLoadNode.LLVMPointerOffsetLoadNode loadNode,
                    @Cached LLVMPointerStoreNode.LLVMPointerOffsetStoreNode storeNode) {
        LLVMContext context = getContext();

        for (LLVMGlobal global : selectors) {
            LLVMPointer storagePtr = context.getSymbol(global, branchProfile);
            LLVMPointer selector = loadNode.executeWithTarget(storagePtr, 0);

            Object registeredSelector = null;
            try {
                // register selector at ObjectiveC runtime
                registeredSelector = selRegisterName.execute(selector);
            } catch (InteropException e) {
                throw CompilerDirectives.shouldNotReachHere();
            }
            storeNode.executeWithTarget(storagePtr, 0, registeredSelector);
        }
    }

    WellKnownNativeFunctionNode getSelRegisterNameFunc() {
        LLVMContext context = getContext();
        NativeContextExtension nativeContextExtension = getNativeContextExtension(context);
        // extern char* sel_registerName(char*);
        return nativeContextExtension.getWellKnownNativeFunction("sel_registerName", "(POINTER):POINTER");
    }

    @CompilerDirectives.TruffleBoundary
    private static NativeContextExtension getNativeContextExtension(LLVMContext context) {
        return context.getContextExtensionOrNull(NativeContextExtension.class);
    }
}
