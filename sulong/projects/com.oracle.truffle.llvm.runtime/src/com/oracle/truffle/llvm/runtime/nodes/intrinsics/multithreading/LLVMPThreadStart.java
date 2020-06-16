/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates.
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
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.llvm.runtime.CommonNodeFactory;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;
import com.oracle.truffle.llvm.runtime.pthread.LLVMPThreadContext;
import com.oracle.truffle.llvm.runtime.pthread.PThreadExitException;
import com.oracle.truffle.llvm.runtime.types.FunctionType;
import com.oracle.truffle.llvm.runtime.types.PointerType;

public final class LLVMPThreadStart {

    static final class LLVMPThreadRunnable implements Runnable {

        private boolean isThread;
        private Object startRoutine;
        private Object arg;
        private LLVMContext context;

        LLVMPThreadRunnable(Object startRoutine, Object arg, LLVMContext context, boolean isThread) {
            this.startRoutine = startRoutine;
            this.arg = arg;
            this.context = context;
            this.isThread = isThread;
        }

        @Override
        public void run() {
            final LLVMPThreadContext pThreadContext = context.getpThreadContext();

            // pthread_exit throws a control flow exception to stop the thread
            try {
                // save return value in storage
                Object returnValue = pThreadContext.getPthreadCallTarget().call(startRoutine, arg);
                // no null values in concurrent hash map allowed
                if (returnValue == null) {
                    returnValue = LLVMNativePointer.createNull();
                }
                pThreadContext.setThreadReturnValue(Thread.currentThread().getId(), returnValue);
            } catch (PThreadExitException e) {
                // return value is written to retval storage in exit function before it throws this
                // exception
            } catch (Throwable t) {
                // unclean exit, set return value to NULL and rethrow
                pThreadContext.setThreadReturnValue(Thread.currentThread().getId(), LLVMNativePointer.createNull());
                throw t;
            } finally {
                // call destructors from key create
                if (this.isThread) {
                    for (int key = 1; key <= pThreadContext.getNumberOfPthreadKeys(); key++) {
                        final LLVMPointer destructor = pThreadContext.getDestructor(key);
                        if (destructor != null && !destructor.isNull()) {
                            final LLVMPointer keyMapping = pThreadContext.getAndRemoveSpecificUnlessNull(key);
                            if (keyMapping != null) {
                                assert !keyMapping.isNull();
                                new LLVMPThreadRunnable(destructor, keyMapping, this.context, false).run();
                            }
                        }
                    }
                    pThreadContext.clearThreadId();
                }
            }
        }
    }

    public static final class LLVMPThreadFunctionRootNode extends RootNode {

        private static FrameDescriptor createFrameDescriptor() {
            final FrameDescriptor descriptor = new FrameDescriptor();
            descriptor.addFrameSlot("function");
            descriptor.addFrameSlot("arg");
            descriptor.addFrameSlot("sp");
            return descriptor;
        }

        @Child private LLVMExpressionNode callNode;

        private final FrameSlot functionSlot;
        private final FrameSlot argSlot;
        private final FrameSlot spSlot;

        @CompilationFinal ContextReference<LLVMContext> ctxRef;

        @TruffleBoundary
        public LLVMPThreadFunctionRootNode(LLVMLanguage language) {
            super(language, createFrameDescriptor());
            final FrameDescriptor descriptor = getFrameDescriptor();
            this.functionSlot = descriptor.findFrameSlot("function");
            this.argSlot = descriptor.findFrameSlot("arg");
            this.spSlot = descriptor.findFrameSlot("sp");

            this.callNode = CommonNodeFactory.createFunctionCall(
                            CommonNodeFactory.createFrameRead(PointerType.VOID, functionSlot),
                            new LLVMExpressionNode[]{
                                            CommonNodeFactory.createFrameRead(PointerType.VOID, spSlot),
                                            CommonNodeFactory.createFrameRead(PointerType.VOID, argSlot)
                            },
                            FunctionType.create(PointerType.VOID, PointerType.VOID, false));
        }

        @Override
        public Object execute(VirtualFrame frame) {
            if (ctxRef == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                ctxRef = lookupContextReference(LLVMLanguage.class);
            }

            try (LLVMStack.StackPointer sp = ctxRef.get().getThreadingStack().getStack().newFrame()) {

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
}
