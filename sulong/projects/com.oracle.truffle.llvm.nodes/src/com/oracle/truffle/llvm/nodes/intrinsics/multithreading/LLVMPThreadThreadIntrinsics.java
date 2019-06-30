package com.oracle.truffle.llvm.nodes.intrinsics.multithreading;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMBuiltin;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMStoreNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;
import com.oracle.truffle.llvm.runtime.types.FunctionType;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.Type;

public class LLVMPThreadThreadIntrinsics {
    @NodeChild(type = LLVMExpressionNode.class, value = "thread")
    @NodeChild(type = LLVMExpressionNode.class, value = "attr")
    @NodeChild(type = LLVMExpressionNode.class, value = "startRoutine")
    @NodeChild(type = LLVMExpressionNode.class, value = "arg")
    public abstract static class LLVMPThreadCreate extends LLVMBuiltin {
        // TODO: pass store in constructor
        @Child
        LLVMStoreNode store = null;

        @Specialization
        //+++ @CompilerDirectives.TruffleBoundary
        protected int doIntrinsic(VirtualFrame frame, Object thread, Object attr, Object startRoutine, Object arg, @CachedContext(LLVMLanguage.class) TruffleLanguage.ContextReference<LLVMContext> ctxRef) {
            // create store node
            if (store == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                store = ctxRef.get().getNodeFactory().createStoreNode(LLVMInteropType.ValueKind.I64);
            }

            // create thread for execution of function
            Thread t = ctxRef.get().getEnv().createThread(new InitStartOfNewThread(startRoutine, arg, ctxRef));
            // store cur id in thread var
            store.executeWithTarget(thread, t.getId());

            // store thread with thread id in context
            ctxRef.get().threadStorage.put(t.getId(), t);
            // start thread
            t.start();

            return 0;
        }
    }

    static class InitStartOfNewThread implements Runnable {
        private Object startRoutine;
        private Object arg;
        private TruffleLanguage.ContextReference<LLVMContext> ctxRef;

        public InitStartOfNewThread(Object startRoutine, Object arg, TruffleLanguage.ContextReference<LLVMContext> ctxRef) {
            this.startRoutine = startRoutine;
            this.arg = arg;
            this.ctxRef = ctxRef;
        }

        @Override
        public void run() {
            if (ctxRef.get().pthreadCallTarget == null) {
                ctxRef.get().pthreadCallTarget = Truffle.getRuntime().createCallTarget(new RunNewThreadNode(LLVMLanguage.getLanguage()));
            }
            // pthread_exit throws a control flow exception to stop the thread
            try {
                ctxRef.get().pthreadCallTarget.call(startRoutine, arg);
            } catch (PThreadExitException e) {

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



    private static class RunNewThreadNode extends RootNode {
        @Child
        LLVMExpressionNode callNode = null;

        @CompilerDirectives.CompilationFinal
        FrameSlot functionSlot = null;

        @CompilerDirectives.CompilationFinal
        FrameSlot argSlot = null;

        @CompilerDirectives.CompilationFinal
        FrameSlot spSlot = null;

        private LLVMLanguage language;

        protected RunNewThreadNode(LLVMLanguage language) {
            super(language);
            this.language = language;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            // LLVMStack stack = new LLVMStack(1000); // how big should it really be?
            // LLVMStack.StackPointer sp = stack.newFrame();
            LLVMStack.StackPointer sp = language.getContextReference().get().getThreadingStack().getStack().newFrame();
            if (callNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                functionSlot = frame.getFrameDescriptor().findOrAddFrameSlot("function");
                argSlot = frame.getFrameDescriptor().findOrAddFrameSlot("arg");
                spSlot = frame.getFrameDescriptor().findOrAddFrameSlot("sp");

                callNode = getCurrentContext(LLVMLanguage.class).getNodeFactory().createFunctionCall(
                        new MyArgNode(functionSlot),
                        new LLVMExpressionNode[] {
                                new MyArgNode(spSlot), new MyArgNode(argSlot)
                        },
                        new FunctionType(PointerType.VOID, new Type[] {}, false),
                        null
                );
            }
            // copy arguments to frame
            final Object[] arguments = frame.getArguments();
            Object function = arguments[0];
            Object arg = arguments[1];
            frame.setObject(functionSlot, function);
            frame.setObject(argSlot, arg);
            frame.setObject(spSlot, sp);

            callNode.executeGeneric(frame);
            return null;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class, value = "retval")
    public abstract static class LLVMPThreadExit extends LLVMBuiltin {
        @Specialization
        //+++ @CompilerDirectives.TruffleBoundary
        protected int doIntrinsic(VirtualFrame frame, Object retval, @CachedContext(LLVMLanguage.class) TruffleLanguage.ContextReference<LLVMContext> ctxRef) {
            // save return value in context for join calls
            ctxRef.get().retValStorage.put(Thread.currentThread().getId(), retval);
            // stop this thread
            throw new PThreadExitException();
        }
    }

    @NodeChild(type = LLVMExpressionNode.class, value = "th")
    @NodeChild(type = LLVMExpressionNode.class, value = "threadReturn")
    public abstract static class LLVMPThreadJoin extends LLVMBuiltin {
        @Child LLVMStoreNode storeNode;

        @Specialization
        //+++ @CompilerDirectives.TruffleBoundary
        protected int doIntrinsic(VirtualFrame frame, long th, Object threadReturn, @CachedContext(LLVMLanguage.class) TruffleLanguage.ContextReference<LLVMContext> ctxRef) {
            if (storeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                storeNode = ctxRef.get().getNodeFactory().createStoreNode(LLVMInteropType.ValueKind.POINTER);
            }

            try {
                // join thread
                Thread thread = ctxRef.get().threadStorage.get(th);
                if (thread == null) {
                    // TODO: error code handling
                    return 5;
                }
                thread.join();

                // get return value
                Object retVal = ctxRef.get().retValStorage.get(th);

                // store return value at ptr
                // TODO: checkstyle says cast to managed or native pointer
                LLVMPointer thReturnPtr = (LLVMPointer) threadReturn;
                if (!thReturnPtr.isNull() && retVal != null) {
                    storeNode.executeWithTarget(threadReturn, retVal);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return 0;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class, value = "t1")
    @NodeChild(type = LLVMExpressionNode.class, value = "t2")
    public abstract static class LLVMPThreadEqual extends LLVMBuiltin {
        @Specialization
        protected int doIntrinsic(VirtualFrame frame, long t1, long t2) {
            return t1 == t2 ? 0 : 1;
        }
    }

    public abstract static class LLVMPThreadSelf extends LLVMBuiltin {
        @Specialization
        protected long doIntrinsic(VirtualFrame frame) {
            return Thread.currentThread().getId();
        }
    }
}
