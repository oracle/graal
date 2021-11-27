/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates.
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

import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.llvm.runtime.CommonNodeFactory;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.NodeFactory;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.func.LLVMRootNode;
import com.oracle.truffle.llvm.runtime.nodes.vars.LLVMReadNode.LLVMObjectReadNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;
import com.oracle.truffle.llvm.runtime.pthread.LLVMPThreadContext;
import com.oracle.truffle.llvm.runtime.pthread.PThreadExitException;
import com.oracle.truffle.llvm.runtime.types.FunctionType;
import com.oracle.truffle.llvm.runtime.types.PointerType;

public final class LLVMPThreadStart {

    static final class LLVMPThreadRunnable implements Runnable {

        private final Object startRoutine;
        private final Object arg;
        private final LLVMContext context;

        LLVMPThreadRunnable(Object startRoutine, Object arg, LLVMContext context) {
            this.startRoutine = startRoutine;
            this.arg = arg;
            this.context = context;
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
                for (int key = 1; key <= pThreadContext.getNumberOfPthreadKeys(); key++) {
                    final LLVMPointer destructor = pThreadContext.getDestructor(key);
                    if (destructor != null && !destructor.isNull()) {
                        final LLVMPointer keyMapping = pThreadContext.getAndRemoveSpecificUnlessNull(key);
                        if (keyMapping != null) {
                            assert !keyMapping.isNull();
                            pThreadContext.getPthreadCallTarget().call(destructor, keyMapping);
                        }
                    }
                }
            }
        }
    }

    public static final class LLVMPThreadFunctionRootNode extends LLVMRootNode {

        @Child private LLVMExpressionNode callNode;

        private final int functionSlot;
        private final int argSlot;

        private LLVMPThreadFunctionRootNode(LLVMLanguage language, FrameDescriptor frameDescriptor, int functionSlot, int argSlot, NodeFactory nodeFactory) {
            super(language, frameDescriptor, nodeFactory.createStackAccess());
            this.functionSlot = functionSlot;
            this.argSlot = argSlot;

            this.callNode = CommonNodeFactory.createFunctionCall(
                            LLVMObjectReadNode.create(functionSlot),
                            new LLVMExpressionNode[]{
                                            nodeFactory.createGetStackFromFrame(),
                                            LLVMObjectReadNode.create(argSlot)
                            },
                            FunctionType.create(PointerType.VOID, PointerType.VOID, false));
        }

        public static LLVMPThreadFunctionRootNode create(LLVMLanguage language, NodeFactory nodeFactory) {
            FrameDescriptor.Builder builder = FrameDescriptor.newBuilder();
            nodeFactory.addStackSlots(builder);
            int functionSlot = builder.addSlot(FrameSlotKind.Object, null, null);
            int argSlot = builder.addSlot(FrameSlotKind.Object, null, null);
            FrameDescriptor descriptor = builder.build();
            return new LLVMPThreadFunctionRootNode(language, descriptor, functionSlot, argSlot, nodeFactory);
        }

        @Override
        public boolean isInternal() {
            return false;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            stackAccess.executeEnter(frame, getContext().getThreadingStack().getStack());
            try {

                // copy arguments to frame
                final Object[] arguments = frame.getArguments();
                Object function = arguments[0];
                Object arg = arguments[1];
                frame.setObject(functionSlot, function);
                frame.setObject(argSlot, arg);

                // execute it
                return callNode.executeGeneric(frame);
            } finally {
                stackAccess.executeExit(frame);
            }
        }
    }
}
