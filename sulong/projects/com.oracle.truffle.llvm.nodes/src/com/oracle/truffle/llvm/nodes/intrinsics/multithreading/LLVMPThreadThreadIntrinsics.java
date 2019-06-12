package com.oracle.truffle.llvm.nodes.intrinsics.multithreading;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMBuiltin;
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
        protected int doIntrinsic(VirtualFrame frame, Object thread, Object attr, Object startRoutine, Object arg) {
            // create store node
            if (store == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                store = LLVMLanguage.getLLVMContextReference().get().getNodeFactory().createStoreNode(LLVMInteropType.ValueKind.I64);
            }

            // create thread for execution of function
            Thread t = LLVMLanguage.getLLVMContextReference().get().getEnv().createThread(() -> {
                CompilerDirectives.transferToInterpreter();
                // RootCallTarget callTarget = Truffle.getRuntime().createCallTarget(new RunNewThreadNode(getLLVMLanguage()));
                RootCallTarget callTarget = Truffle.getRuntime().createCallTarget(new RunNewThreadNode(LLVMLanguage.getLanguage()));

                // pthread_exit throws a control flow exception to stop the thread
                try {
                    callTarget.call(startRoutine, arg);
                } catch (ControlFlowException e) {

                }
            });
            // store cur id in thread var
            store.executeWithTarget(thread, t.getId());

            // store thread with thread id in context
            LLVMLanguage.getLLVMContextReference().get().threadStorage.put(t.getId(), t);
            // start thread
            t.start();

            return 0;
        }
    }

    static final class MyArgNode extends LLVMExpressionNode {
        private FrameSlot slot;

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
        protected int doIntrinsic(VirtualFrame frame, Object retval) {
            // save return value in context for join calls
            LLVMLanguage.getLLVMContextReference().get().retValStorage.put(Thread.currentThread().getId(), retval);
            // stop this thread
            throw new ControlFlowException();
        }
    }

    @NodeChild(type = LLVMExpressionNode.class, value = "th")
    @NodeChild(type = LLVMExpressionNode.class, value = "threadReturn")
    public abstract static class LLVMPThreadJoin extends LLVMBuiltin {
        @Child LLVMStoreNode storeNode;

        @Specialization
        protected int doIntrinsic(VirtualFrame frame, Object th, Object threadReturn) {
            if (storeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                storeNode = LLVMLanguage.getLLVMContextReference().get().getNodeFactory().createStoreNode(LLVMInteropType.ValueKind.POINTER);
            }
            long thLong = (long) th;
            try {
                // join thread
                Thread thread = (Thread) LLVMLanguage.getLLVMContextReference().get().threadStorage.get(thLong);
                if (thread == null) {
                    // TODO: error code handling
                    return 5;
                }
                thread.join();

                // get return value
                Object retVal = LLVMLanguage.getLLVMContextReference().get().retValStorage.get(thLong);

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
        protected int doIntrinsic(VirtualFrame frame, Object t1, Object t2) {
            return (long) t1 == (long) t2 ? 0 : 1;
        }
    }

    public abstract static class LLVMPThreadSelf extends LLVMBuiltin {
        @Specialization
        protected long doIntrinsic(VirtualFrame frame) {
            return Thread.currentThread().getId();
        }
    }
}
