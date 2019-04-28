package com.oracle.truffle.llvm.nodes.intrinsics.multithreading;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.atomic.AtomicInteger;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMBuiltin;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMLoadNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMStoreNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;
import com.oracle.truffle.llvm.runtime.types.FunctionType;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.Type;

public class LLVMPThreadIntrinsics {
    public static PrintWriter debugOut = null;

    @CompilerDirectives.TruffleBoundary
    private static void printDebug(String str) {
        // create debug output tool
        if (debugOut == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            try {
                debugOut = new PrintWriter(new FileWriter("/home/florian/debug out"));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        debugOut.println(str);
        debugOut.flush();
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMPThreadCreate extends LLVMBuiltin {
        @Child
        LLVMStoreNode store = null;

        @Specialization
        protected int doIntrinsic(VirtualFrame frame, Object thread, Object attr, Object startRoutine, Object arg) {
            // create store node
            if (store == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                store = getContextReference().get().getNodeFactory().createStoreNode(LLVMInteropType.ValueKind.I64);
            }

            // create thread for execution of function
            Thread t = getContextReference().get().getEnv().createThread(() -> {
                CompilerDirectives.transferToInterpreter();
                RootCallTarget callTarget = Truffle.getRuntime().createCallTarget(new RunNewThreadNode(getLLVMLanguage()));
                callTarget.call(startRoutine, arg);
            });
            // store cur id in thread var
            store.executeWithTarget(thread, t.getId());

            // store thread with thread id in context
            getContextReference().get().threadStorage.put(t.getId(), t);

            // start thread
            t.start();

            return 0;
        }
    }

    static class MyArgNode extends LLVMExpressionNode {
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
        @Child LLVMExpressionNode callNode = null;

        @CompilerDirectives.CompilationFinal
        FrameSlot functionSlot = null;

        @CompilerDirectives.CompilationFinal
        FrameSlot argSlot = null;

        @CompilerDirectives.CompilationFinal
        FrameSlot spSlot = null;

        protected RunNewThreadNode(LLVMLanguage language) {
            super(language);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            LLVMStack stack = new LLVMStack(1000); // how big should it really be?
            LLVMStack.StackPointer sp = stack.newFrame();
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

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMPThreadExit extends LLVMBuiltin {
        @Specialization
        protected int doIntrinsic(VirtualFrame frame, Object retval) {
            getContextReference().get().retValStorage.put(Thread.currentThread().getId(), retval);
            // stop this thread, does not work yet
            // Thread.currentThread().interrupt();
            // Thread.currentThread().stop();
            int i = 5;
            return 0;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMPThreadJoin extends LLVMBuiltin {
        @Child LLVMStoreNode storeNode;

        @Specialization
        protected int doIntrinsic(VirtualFrame frame, Object th, Object threadReturn) {
            if (storeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                storeNode = getContextReference().get().getNodeFactory().createStoreNode(LLVMInteropType.ValueKind.POINTER);
            }
            long thLong = (long) th;
            try {
                // join thread
                Thread thread = (Thread) getContextReference().get().threadStorage.get(thLong);
                thread.join();

                // get return value
                Object retVal = getContextReference().get().retValStorage.get(thLong);

                // store return value in at ptr
                LLVMPointer thReturnPtr = (LLVMPointer) threadReturn;
                if (!thReturnPtr.isNull())
                    storeNode.executeWithTarget(threadReturn, retVal);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return 0;
        }
    }

    public abstract static class LLVMPThreadSelf extends LLVMBuiltin {
        @Specialization
        protected long doIntrinsic(VirtualFrame frame) {
            return Thread.currentThread().getId();
        }
    }

    class Mutex {
        private AtomicInteger lockCount;
        private long threadId; // id of the locking / owning thread

        protected Mutex(int initVal) {
            lockCount = new AtomicInteger(initVal);
            // ...
        }

        // do stuff here
        // ...
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMPThreadMutexattrInit extends LLVMBuiltin {
        @Specialization
        protected int doIntrinsic(VirtualFrame frame, Object attr) {
            // TODO: how to handle pthread_mutexattr_t?
            // seems like pthread_mutexattr_t is just an int in the header / lib sulong uses
            // so no need to init here
            return 0;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMPThreadMutexattrSettype extends LLVMBuiltin {
        @Child LLVMStoreNode store = null;

        @Specialization
        protected int doIntrinsic(VirtualFrame frame, Object attr, Object type) {
            // TODO: how to handle pthread_mutexattr_t? how to save type info to it?
            // seems like pthread_mutexattr_t is just an int in the header / lib sulong uses
            // so we just write the int-value type to the address attr points to
            // create store node
            if (store == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                store = getContextReference().get().getNodeFactory().createStoreNode(LLVMInteropType.ValueKind.I32);
            }
            // store type in attr var
            if (type == null)
                type = new Integer(512);
            store.executeWithTarget(attr, type);
            // TODO: return with error if type not in {512, 1, 2}
            // look up fitting error code
            return 0;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMPThreadMutexInit extends LLVMBuiltin {
        @Child
        LLVMLoadNode read = null;

        @Specialization
        protected int doIntrinsic(VirtualFrame frame, Object mutex, Object attr) {
            if (read == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                read = getContextReference().get().getNodeFactory().createLoadNode(LLVMInteropType.ValueKind.I32);
            }
            // TODO: change from "AtomicInteger" to "Mutex" bc we need both lock count and owner thread id
            // we can use the address of the native pointer here, bc a mutex
            // must only work when using the original variable, not a copy
            // so the address may never change
            long mutexAddr = ((LLVMNativePointer) mutex).asNative();
            Object mutObj = getContextReference().get().mutexStorage.get(mutexAddr);
            if (mutObj == null) {
                getContextReference().get().mutexStorage.put(mutexAddr, new AtomicInteger(0));
            }
            Object attrObj = read.executeWithTarget(attr);
            // already works, attrObj now has type code
            // so now save this type info for the mutex
            // and later use it in mutex lock (ERRORCHECK, RECURSIVE, DEFAULT, NORMAL)
            // ...
            return 0;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMPThreadMutexLock extends LLVMBuiltin {
        @Specialization
        protected int doIntrinsic(VirtualFrame frame, Object mutex) {
            // TODO: change from "AtomicInteger" to "Mutex" bc we need both lock count and owner thread id
            // TODO: add stuff for recursive mutexes
            long mutexAddr = ((LLVMNativePointer) mutex).asNative();
            AtomicInteger lockCount = getContextReference().get().mutexStorage.get(mutexAddr);
            if (lockCount == null) {
                // mutex is not initialized
                // but it works anyway on most implementations
                // so we will make it work here too, just using default type
                // set the lock counter to 1
                lockCount = new AtomicInteger(1);
                getContextReference().get().mutexStorage.put(mutexAddr, lockCount);
                return 0;
            }
            // add stuff for recursive mutexes
            while (!lockCount.compareAndSet(0, 1)) {
                try {
                    Thread.currentThread().sleep(75);
                } catch (InterruptedException e) {
                    // i do not care about being interrupted here, but let's just print stack trace
                    e.printStackTrace();
                }
            }
            return 0;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMPThreadMutexUnlock extends LLVMBuiltin {
        @Specialization
        protected int doIntrinsic(VirtualFrame frame, Object mutex) {
            // TODO: change from "AtomicInteger" to "Mutex" bc we need both lock count and owner thread id
            // TODO: add stuff for recursive mutexes
            long mutexAddr = ((LLVMNativePointer) mutex).asNative();
            AtomicInteger lockCount = getContextReference().get().mutexStorage.get(mutexAddr);
            // add stuff for recursive mutexes
            lockCount.set(0);
            return 0;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMPThreadMyTest extends LLVMBuiltin {
        @Specialization
        protected int doIntrinsic(VirtualFrame frame, Object arg) {
            int i = 5;
            return 35; // just to test return 35
        }
    }
}
