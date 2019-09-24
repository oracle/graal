/*
 * Copyright (c) 2019, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.intrinsics.multithreading;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMExitException;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;
import com.oracle.truffle.llvm.runtime.types.FunctionType;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.Type;

import java.util.concurrent.ConcurrentMap;

final class UtilFunctionCall {

    static final class FunctionCallRunnable implements Runnable {

        private boolean isThread;
        private Object startRoutine;
        private Object arg;
        private LLVMContext context;

        FunctionCallRunnable(Object startRoutine, Object arg, LLVMContext context, boolean isThread) {
            this.startRoutine = startRoutine;
            this.arg = arg;
            this.context = context;
            this.isThread = isThread;
        }

        @Override
        public void run() {
            synchronized (context.callTargetLock) {
                if (context.pthreadCallTarget == null) {
                    FrameDescriptor frameDescriptor = new FrameDescriptor();
                    frameDescriptor.addFrameSlot("function");
                    frameDescriptor.addFrameSlot("arg");
                    frameDescriptor.addFrameSlot("sp");
                    context.pthreadCallTarget = Truffle.getRuntime().createCallTarget(new FunctionCallNode(LLVMLanguage.getLanguage(), frameDescriptor));
                }
            }
            // pthread_exit throws a control flow exception to stop the thread
            try {
                // save return value in storage
                Object returnValue = context.pthreadCallTarget.call(startRoutine, arg);
                // no null values in concurrent hash map allowed
                if (returnValue == null) {
                    returnValue = LLVMNativePointer.createNull();
                }
                context.setThreadReturnValue(Thread.currentThread().getId(), returnValue);
            } catch (PThreadExitException e) {
                // return value is written to retval storage in exit function before it throws this
                // exception
            } catch (LLVMExitException e) {
                System.exit(e.getExitStatus());
            } finally {
                // call destructors from key create
                if (this.isThread) {
                    for (int key = 1; key <= context.curKeyVal; key++) {
                        LLVMPointer destructor = UtilAccessCollectionWithBoundary.get(context.destructorStorage, key);
                        if (destructor != null && !destructor.isNull()) {
                            ConcurrentMap<Long, LLVMPointer> specValueMap = UtilAccessCollectionWithBoundary.get(context.keyStorage, key);
                            // if key was deleted continue with next destructor
                            if (specValueMap == null) {
                                continue;
                            }
                            Object keyVal = UtilAccessCollectionWithBoundary.get(specValueMap, Thread.currentThread().getId());
                            if (keyVal != null) {
                                // if key value is null pointer continue with next destructor
                                try {
                                    LLVMPointer keyValPointer = LLVMPointer.cast(keyVal);
                                    if (keyValPointer.isNull()) {
                                        continue;
                                    }
                                } catch (Exception e) {
                                    // ignored
                                }
                                UtilAccessCollectionWithBoundary.remove(specValueMap, Thread.currentThread().getId());
                                new FunctionCallRunnable(destructor, keyVal, this.context, false).run();
                            }
                        }
                    }
                }
            }
        }
    }

    private static final class FunctionCallNode extends RootNode {

        @Child LLVMExpressionNode callNode = null;

        private final LLVMContext context;

        @CompilerDirectives.CompilationFinal
        final FrameSlot functionSlot;

        @CompilerDirectives.CompilationFinal
        final FrameSlot argSlot;

        @CompilerDirectives.CompilationFinal
        final FrameSlot spSlot;

        @CompilerDirectives.TruffleBoundary
        FunctionCallNode(LLVMLanguage language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
            this.context = language.getContextReference().get();
            this.functionSlot = frameDescriptor.findFrameSlot("function");
            this.argSlot = frameDescriptor.findFrameSlot("arg");
            this.spSlot = frameDescriptor.findFrameSlot("sp");
            CompilerDirectives.transferToInterpreterAndInvalidate();
            this.callNode = context.getLanguage().getNodeFactory().createFunctionCall(
                    context.getLanguage().getNodeFactory().createFrameRead(PointerType.VOID, functionSlot),
                    new LLVMExpressionNode[]{
                            context.getLanguage().getNodeFactory().createFrameRead(PointerType.VOID, spSlot),
                            context.getLanguage().getNodeFactory().createFrameRead(PointerType.VOID, argSlot)
                    },
                    new FunctionType(PointerType.VOID, new Type[]{PointerType.VOID}, false));
        }

        @Override
        public Object execute(VirtualFrame frame) {
            LLVMStack.StackPointer sp = context.getThreadingStack().getStack().newFrame();
            // copy arguments to frame
            final Object[] arguments = frame.getArguments();
            Object function = arguments[0];
            Object arg = arguments[1];
            frame.setObject(functionSlot, function);
            frame.setObject(argSlot, arg);
            frame.setObject(spSlot, sp);
            // execute it
            return callNode.executeGeneric(frame);
        }
    }
}
