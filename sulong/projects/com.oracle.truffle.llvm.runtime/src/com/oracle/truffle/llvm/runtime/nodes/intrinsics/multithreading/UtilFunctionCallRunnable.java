package com.oracle.truffle.llvm.runtime.nodes.intrinsics.multithreading;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
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

public class UtilFunctionCallRunnable {
    static class FunctionCall implements Runnable {
        private boolean isThread;
        private Object startRoutine;
        private Object arg;
        private LLVMContext ctx;

        FunctionCall(Object startRoutine, Object arg, LLVMContext ctx, boolean isThread) {
            this.startRoutine = startRoutine;
            this.arg = arg;
            this.ctx = ctx;
            this.isThread = isThread;
        }

        @Override
        public void run() {
            synchronized (ctx) {
                if (ctx.pthreadCallTarget == null) {
                    ctx.pthreadCallTarget = Truffle.getRuntime().createCallTarget(new FunctionCallNode(LLVMLanguage.getLanguage()));
                }
            }
            // pthread_exit throws a control flow exception to stop the thread
            try {
                // save return value in storage
                Object retVal = ctx.pthreadCallTarget.call(startRoutine, arg);
                // no null values in concurrent hash map allowed
                if (retVal == null) {
                    retVal = LLVMNativePointer.createNull();
                }
                UtilAccessCollectionWithBoundary.put(ctx.retValStorage, Thread.currentThread().getId(), retVal);
            } catch (PThreadExitException e) {
                // return value is written to retval storage in exit function before it throws this exception
            } catch (LLVMExitException e) {
                System.exit(e.getExitStatus());
            } finally {
                // call destructors from key create
                if (this.isThread) {
                    for (int key = 1; key <= ctx.curKeyVal; key++) {
                        LLVMPointer destructor = UtilAccessCollectionWithBoundary.get(ctx.destructorStorage, key);
                        if (destructor != null && !destructor.isNull()) {
                            ConcurrentMap<Long, LLVMPointer> specValueMap = UtilAccessCollectionWithBoundary.get(ctx.keyStorage, key);
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
                                }
                                UtilAccessCollectionWithBoundary.remove(specValueMap, Thread.currentThread().getId());
                                new FunctionCall(destructor, keyVal, this.ctx, false).run();
                            }
                        }
                    }
                }
            }
        }
    }

    static final class MyArgNode extends LLVMExpressionNode {
        private final FrameSlot slot;

        private MyArgNode(FrameSlot slot) {
            this.slot = slot;
        }

        @Override
        public Object executeGeneric(VirtualFrame frame) {
            return frame.getValue(slot);
        }
    }

    private static class FunctionCallNode extends RootNode {
        @Child
        LLVMExpressionNode callNode = null;

        @CompilerDirectives.CompilationFinal
        FrameSlot functionSlot = null;

        @CompilerDirectives.CompilationFinal
        FrameSlot argSlot = null;

        @CompilerDirectives.CompilationFinal
        FrameSlot spSlot = null;

        private final LLVMContext ctx;

        protected FunctionCallNode(LLVMLanguage language) {
            super(language);
            this.ctx = language.getContextReference().get();
        }

        @Override
        public Object execute(VirtualFrame frame) {
            LLVMStack.StackPointer sp = ctx.getThreadingStack().getStack().newFrame();
            if (callNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                functionSlot = frame.getFrameDescriptor().findOrAddFrameSlot("function");
                argSlot = frame.getFrameDescriptor().findOrAddFrameSlot("arg");
                spSlot = frame.getFrameDescriptor().findOrAddFrameSlot("sp");

                callNode = ctx.getLanguage().getNodeFactory().createFunctionCall(
                        new MyArgNode(functionSlot),
                        new LLVMExpressionNode[] {
                                new MyArgNode(spSlot), new MyArgNode(argSlot)
                        },
                        new FunctionType(PointerType.VOID, new Type[] {PointerType.VOID}, false)
                );
            }
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
