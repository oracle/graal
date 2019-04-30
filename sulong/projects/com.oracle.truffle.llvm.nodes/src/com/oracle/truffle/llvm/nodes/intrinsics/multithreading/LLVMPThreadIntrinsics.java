package com.oracle.truffle.llvm.nodes.intrinsics.multithreading;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

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
                // pthread_exit throws a control flow exception to stop the thread
                try {
                    callTarget.call(startRoutine, arg);
                } catch (ControlFlowException e) {

                }
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

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMPThreadExit extends LLVMBuiltin {
        @Specialization
        protected int doIntrinsic(VirtualFrame frame, Object retval) {
            // save return value in context for join calls
            getContextReference().get().retValStorage.put(Thread.currentThread().getId(), retval);
            // stop this thread
            throw new ControlFlowException();
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

                // store return value at ptr
                LLVMPointer thReturnPtr = (LLVMPointer) threadReturn;
                if (!thReturnPtr.isNull()) {
                    storeNode.executeWithTarget(threadReturn, retVal);
                }
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

    public static class Mutex {
        public enum Type {
            DEFAULT_NORMAL,
            ERRORCHECK,
            RECURSIVE
        }

        private AtomicInteger lockCount;
        private ConcurrentLinkedQueue<Thread> waitingThreads;
        private Type type;

        protected Mutex(int initVal, Type type) {
            this.lockCount = new AtomicInteger(initVal);
            this.waitingThreads = new ConcurrentLinkedQueue<>();
            if (type != null) {
                this.type = type;
            } else {
                this.type = Type.DEFAULT_NORMAL;
            }
        }

        // replace this by the lock / unlock / ... methods
        protected AtomicInteger getLockCount() {
            return lockCount;
        }

        // replace this by some methods
        protected ConcurrentLinkedQueue getQueue() {
            return waitingThreads;
        }

        // boolean because errorcheck-type allows lock to be not successful
        public boolean lock() {
            return false;
        }

        public boolean tryLock() {
            return false;
        }

        public void unlock() {
        }
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
            if (type == null) {
                // 512 is the value for default and normal in the native sulong call, so i use it too
                type = new Integer(512);
            }
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
            // we can use the address of the native pointer here, bc a mutex
            // must only work when using the original variable, not a copy
            // so the address may never change
            long mutexAddress = ((LLVMNativePointer) mutex).asNative();
            Object mutObj = getContextReference().get().mutexStorage.get(mutexAddress);
            Object attrObj = read.executeWithTarget(attr);
            // already works, attrObj now has type code
            // so now save this type info for the mutex
            // and later use it in mutex lock (ERRORCHECK, RECURSIVE, DEFAULT, NORMAL)
            // ...
            if (mutObj == null) {
                // replace null by correct type handling
                getContextReference().get().mutexStorage.put(mutexAddress, new Mutex(0, null));
            }
            return 0;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMPThreadMutexLock extends LLVMBuiltin {
        @Specialization
        protected int doIntrinsic(VirtualFrame frame, Object mutex) {
            // TODO: add stuff for recursive mutexes
            long mutexAddress = ((LLVMNativePointer) mutex).asNative();
            Mutex mutexObj = (Mutex) getContextReference().get().mutexStorage.get(mutexAddress);
            if (mutexObj == null) {
                // mutex is not initialized
                // but it works anyway on most implementations
                // so we will make it work here too, just using default type
                // set the lock counter to 1
                // replace null by correct type handling
                mutexObj = new Mutex(1, null);
                getContextReference().get().mutexStorage.put(mutexAddress, mutexObj);
                return 0;
            }
            // add stuff for recursive mutexes
            while (!mutexObj.getLockCount().compareAndSet(0, 1)) {
                mutexObj.getQueue().offer(Thread.currentThread());
                // we want wo to wait until we get chosen as next thread to lock / own the mutex
                // and get interrupted by an unlock call here
                try {
                    while (true) {
                        Thread.sleep(Long.MAX_VALUE);
                    }
                } catch (InterruptedException e) {
                }
            }
            // now the mutex is successfully locked
            return 0;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMPThreadMutexUnlock extends LLVMBuiltin {
        @Specialization
        protected int doIntrinsic(VirtualFrame frame, Object mutex) {
            // TODO: add stuff for recursive mutexes
            long mutexAddress = ((LLVMNativePointer) mutex).asNative();
            Mutex mutexObj = (Mutex) getContextReference().get().mutexStorage.get(mutexAddress);
            // add stuff for recursive mutexes
            mutexObj.getLockCount().set(0);
            Thread nextThread = (Thread) mutexObj.getQueue().poll();
            if (nextThread != null) {
                nextThread.interrupt();
            }
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
