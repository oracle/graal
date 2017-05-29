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
package com.oracle.truffle.llvm.nodes.func;

import java.util.Deque;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMAbort;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMSignal;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMContext.DestructorStackElement;
import com.oracle.truffle.llvm.runtime.LLVMExitException;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.LLVMTruffleAddress;
import com.oracle.truffle.llvm.runtime.SulongRuntimeException;
import com.oracle.truffle.llvm.runtime.types.PointerType;

/**
 * The global entry point initializes the global scope and starts execution with the main function.
 * This class might be subclassed by other projects.
 */
public class LLVMGlobalRootNode extends RootNode {

    @Child private Node executeDestructor = Message.createExecute(1).createNode();
    private final DirectCallNode main;
    @CompilationFinal(dimensions = 1) protected final Object[] arguments;

    public LLVMGlobalRootNode(LLVMLanguage language, FrameDescriptor descriptor, CallTarget main, Object... arguments) {
        super(language, descriptor);
        this.main = Truffle.getRuntime().createDirectCallNode(main);
        this.arguments = arguments;
    }

    @Override
    @ExplodeLoop
    public Object execute(VirtualFrame frame) {
        getContext().getThreadingStack().checkThread();
        long basePointer = getContext().getThreadingStack().getStack().getStackPointer();
        try {
            Object result = null;
            assert LLVMSignal.getNumberOfRegisteredSignals() == 0;

            Object[] realArgs = new Object[arguments.length + LLVMCallNode.USER_ARGUMENT_OFFSET];
            realArgs[0] = basePointer;
            System.arraycopy(arguments, 0, realArgs, LLVMCallNode.USER_ARGUMENT_OFFSET, arguments.length);

            result = executeIteration(realArgs);

            getContext().awaitThreadTermination();
            assert LLVMSignal.getNumberOfRegisteredSignals() == 0;
            return result;
        } catch (LLVMExitException e) {
            getContext().awaitThreadTermination();
            assert LLVMSignal.getNumberOfRegisteredSignals() == 0;
            return e.getReturnCode();
        } catch (SulongRuntimeException e) {
            System.err.println();
            e.getCStackTrace().printCStackTrace();
            throw e;
        } finally {
            getContext().getThreadingStack().getStack().setStackPointer(basePointer);
            runDestructors();
            // if not done already, we want at least call a shutdown command
            getContext().shutdownThreads();
        }
    }

    @TruffleBoundary
    private void runDestructors() {
        for (DestructorStackElement destructorStackElement : getContext().getDestructorStack()) {
            try {
                ForeignAccess.sendExecute(executeDestructor, destructorStackElement.getDestructor(),
                                new LLVMTruffleAddress(destructorStackElement.getThiz(), new PointerType(null), getContext()));
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException(e);
            }
        }
    }

    protected Object executeIteration(Object[] args) {
        Object result;

        int returnCode = 0;

        try {
            result = main.call(args);
        } catch (LLVMExitException e) {
            returnCode = e.getReturnCode();
            throw e;
        } finally {
            // We shouldn't execute atexit, when there was an abort
            if (returnCode != LLVMAbort.UNIX_SIGABORT) {
                executeAtExitFunctions();
            }
        }

        return result;
    }

    @TruffleBoundary
    private void executeAtExitFunctions() {
        Deque<LLVMFunctionDescriptor> atExitFunctions = getContext().getAtExitFunctions();
        LLVMExitException lastExitException = null;
        while (!atExitFunctions.isEmpty()) {
            try {
                try {
                    ForeignAccess.sendExecute(Message.createExecute(0).createNode(), atExitFunctions.pop());
                } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                    throw new IllegalStateException(e);
                }
            } catch (LLVMExitException e) {
                lastExitException = e;
            }
        }
        if (lastExitException != null) {
            throw lastExitException;
        }
    }

    public final LLVMContext getContext() {
        return getRootNode().getLanguage(LLVMLanguage.class).getContextReference().get();
    }

}
