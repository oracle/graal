package com.oracle.truffle.llvm.runtime.nodes.intrinsics.multithreading;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMStoreNode;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.LLVMBuiltin;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

public class LLVMPThreadThreadIntrinsics {
    @NodeChild(type = LLVMExpressionNode.class, value = "thread")
    @NodeChild(type = LLVMExpressionNode.class, value = "attr")
    @NodeChild(type = LLVMExpressionNode.class, value = "startRoutine")
    @NodeChild(type = LLVMExpressionNode.class, value = "arg")
    public abstract static class LLVMPThreadCreate extends LLVMBuiltin {
        @Child
        LLVMStoreNode store = null;

        @Specialization
        protected int doIntrinsic(VirtualFrame frame, LLVMPointer thread, LLVMPointer attr, LLVMPointer startRoutine, LLVMPointer arg, @CachedContext(LLVMLanguage.class) LLVMContext ctx) {
            if (store == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                store = ctx.getLanguage().getNodeFactory().createStoreNode(LLVMInteropType.ValueKind.I64);
            }
            UtilFunctionCallRunnable.FunctionCall init = new UtilFunctionCallRunnable.FunctionCall(startRoutine, arg, ctx, true);
            Thread t = ctx.getEnv().createThread(init);
            store.executeWithTarget(thread, t.getId());
            UtilAccessCollectionWithBoundary.add(ctx.createdThreads, t);
            UtilAccessCollectionWithBoundary.put(ctx.threadStorage, t.getId(), t);
            t.start();
            return 0;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class, value = "retval")
    public abstract static class LLVMPThreadExit extends LLVMBuiltin {
        @Specialization
        protected int doIntrinsic(VirtualFrame frame, Object retval, @CachedContext(LLVMLanguage.class) LLVMContext ctx) {
            UtilAccessCollectionWithBoundary.put(ctx.retValStorage, Thread.currentThread().getId(), retval);
            throw new PThreadExitException();
        }
    }

    @NodeChild(type = LLVMExpressionNode.class, value = "th")
    @NodeChild(type = LLVMExpressionNode.class, value = "threadReturn")
    public abstract static class LLVMPThreadJoin extends LLVMBuiltin {
        @Child LLVMStoreNode storeNode;

        @Specialization
        protected int doIntrinsic(VirtualFrame frame, long th, LLVMPointer threadReturn, @CachedContext(LLVMLanguage.class) LLVMContext ctx) {
            if (storeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                storeNode = ctx.getLanguage().getNodeFactory().createStoreNode(LLVMInteropType.ValueKind.POINTER);
            }
            try {
                Thread thread = UtilAccessCollectionWithBoundary.get(ctx.threadStorage, th);
                if (thread == null) {
                    return 0;
                }
                thread.join();
                Object retVal = UtilAccessCollectionWithBoundary.get(ctx.retValStorage, th);
                if (!threadReturn.isNull()) {
                    storeNode.executeWithTarget(threadReturn, retVal);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return 0;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class, value = "onceControl")
    @NodeChild(type = LLVMExpressionNode.class, value = "initRoutine")
    public abstract static class LLVMPThreadOnce extends LLVMBuiltin {
        @Specialization
        protected int doIntrinsic(VirtualFrame frame, LLVMPointer onceControl, LLVMPointer initRoutine, @CachedContext(LLVMLanguage.class) LLVMContext ctx) {
            synchronized (ctx) {
                if (ctx.onceStorage.contains(onceControl)) {
                    return 0;
                }
                ctx.onceStorage.add(onceControl);
            }
            UtilFunctionCallRunnable.FunctionCall init = new UtilFunctionCallRunnable.FunctionCall(initRoutine, null, ctx, false);
            init.run();
            return 0;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class, value = "t1")
    @NodeChild(type = LLVMExpressionNode.class, value = "t2")
    public abstract static class LLVMPThreadEqual extends LLVMBuiltin {
        @Specialization
        protected int doIntrinsic(VirtualFrame frame, long t1, long t2) {
            return t1 == t2 ? 1 : 0;
        }
    }

    public abstract static class LLVMPThreadSelf extends LLVMBuiltin {
        @Specialization
        protected long doIntrinsic(VirtualFrame frame) {
            return Thread.currentThread().getId();
        }
    }
}
