/*
 * Copyright (c) 2016, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.nodes.impl.func;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.llvm.nodes.base.LLVMNode;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.LLVMExitException;
import com.oracle.truffle.llvm.types.LLVMAddress;
import com.oracle.truffle.llvm.types.memory.LLVMHeap;
import com.oracle.truffle.llvm.types.memory.LLVMStack;

/**
 * The global entry point initializes the global scope and starts execution with the main function.
 */
public class LLVMGlobalRootNode extends RootNode {

    public static LLVMGlobalRootNode createNoArgumentsMain(LLVMNode[] staticInits, CallTarget main, LLVMAddress[] llvmAddresses) {
        return new LLVMGlobalRootNode(staticInits, main, llvmAddresses);
    }

    public static LLVMGlobalRootNode createArgsCountMain(LLVMNode[] staticInits, CallTarget main, LLVMAddress[] llvmAddresses, int argsCount) {
        return new LLVMGlobalRootNode(staticInits, main, llvmAddresses, argsCount);
    }

    public static LLVMGlobalRootNode createArgsMain(LLVMNode[] staticInits, CallTarget main, LLVMAddress[] llvmAddresses, int argsCount, LLVMAddress args) {
        return new LLVMGlobalRootNode(staticInits, main, llvmAddresses, argsCount, args);
    }

    public static LLVMGlobalRootNode createArgsEnvMain(LLVMNode[] staticInits, CallTarget main, LLVMAddress[] llvmAddresses, int argsCount, LLVMAddress args, LLVMAddress envp) {
        return new LLVMGlobalRootNode(staticInits, main, llvmAddresses, argsCount, args, envp);
    }

    @Children private final LLVMNode[] staticInits;
    private final DirectCallNode main;
    private final LLVMAddress[] llvmAddresses;
    private final Object[] arguments;

    public LLVMGlobalRootNode(LLVMNode[] staticInits, CallTarget main, LLVMAddress[] llvmAddresses, Object... arguments) {
        super(LLVMLanguage.class, null, null);
        this.staticInits = staticInits;
        this.main = Truffle.getRuntime().createDirectCallNode(main);
        this.llvmAddresses = llvmAddresses;
        this.arguments = arguments;
    }

    @Override
    @ExplodeLoop
    public Object execute(VirtualFrame frame) {
        CompilerAsserts.compilationConstant(staticInits);
        LLVMStack.allocate();
        for (LLVMNode init : staticInits) {
            init.executeVoid(frame);
        }
        try {
            return main.call(frame, arguments);
        } catch (LLVMExitException e) {
            return e.getReturnCode();
        } finally {
            for (LLVMAddress alloc : llvmAddresses) {
                LLVMHeap.freeMemory(alloc);
            }
            LLVMStack.free();
        }
    }

}
