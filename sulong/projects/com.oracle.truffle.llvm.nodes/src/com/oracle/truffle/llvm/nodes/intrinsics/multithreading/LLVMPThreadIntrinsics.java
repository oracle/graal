package com.oracle.truffle.llvm.nodes.intrinsics.multithreading;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

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
                // TODO: checkstyle says cast to managed or native pointer
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

        private final AtomicInteger lockCount;
        private final ConcurrentLinkedQueue<Thread> waitingThreads;
        private final ReentrantLock internLock;
        private Thread curOwner;
        private final Type type;

        public Mutex(int initVal, Type type) {
            this.lockCount = new AtomicInteger(initVal);
            this.waitingThreads = new ConcurrentLinkedQueue<>();
            this.internLock = new ReentrantLock();
            if (initVal != 0) {
                this.curOwner = Thread.currentThread();
            }
            if (type != null) {
                this.type = type;
            } else {
                this.type = Type.DEFAULT_NORMAL;
            }
        }

        public Type getType() {
            return this.type;
        }

        // change to int for error codes
        public boolean lock() {
            if (this.curOwner == Thread.currentThread()) {
                if (this.type == Type.ERRORCHECK) {
                    return false;
                }
                if (this.type == Type.RECURSIVE) {
                    this.lockCount.incrementAndGet();
                    return true;
                }
                // default_normal relock leads to deadlock
            }
            while (true) {
                // it should not be possible for compareAndSet to fail
                // but then in this moment the current locking thread frees / unlocks
                // and notifys the next thread
                // before this thread is in the queue
                // because that would lead to a deadlock
                // so we have this internLock here and in the unlock method
                // so this internLock prevents:
                // THREAD A: compareAndSet(0, 1) fails
                // THREAD B: set(0)
                // THREAD B: notifyNextThread()
                // THREAD A: waitingThreads.offer()
                internLock.lock();
                if (this.lockCount.compareAndSet(0, 1)) {
                    this.curOwner = Thread.currentThread();
                    internLock.unlock();
                    return true;
                }
                this.waitingThreads.offer(Thread.currentThread());
                internLock.unlock();
                // we want wo to wait until we get chosen as next thread to internLock / own the mutex
                // and get interrupted by an unlock call here
                try {
                    while (true) {
                        Thread.sleep(Long.MAX_VALUE);
                    }
                } catch (InterruptedException e) {
                }
            }
        }

        public boolean tryLock() {
            if (this.curOwner == Thread.currentThread()) {
                if (this.type == Type.RECURSIVE) {
                    this.lockCount.incrementAndGet();
                    return true;
                }
                return false;
            }
            if (this.lockCount.compareAndSet(0, 1)) {
                this.curOwner = Thread.currentThread();
                return true;
            }
            return false;
        }

        public boolean unlock() {
            if (this.curOwner != Thread.currentThread()) {
                return false;
            }
            if (this.type == Type.DEFAULT_NORMAL || this.type == Type.ERRORCHECK) {
                // this internLock prevents
                // THREAD A: compareAndSet(0, 1) fails
                // THREAD B: set(0)
                // THREAD B: notifyNextThread()
                // THREAD A: waitingThreads.offer()
                internLock.lock();
                this.lockCount.set(0);
                this.curOwner = null;
                this.notifyNextThread();
                internLock.unlock();
                return true;
            } else if (this.type == Type.RECURSIVE) {
                if (this.lockCount.decrementAndGet() == 0) {
                    this.curOwner = null;
                }
                return true;
            }
            // should not be reached, because type is always set
            return false;
        }

        private void notifyNextThread() {
            Thread nextThread = this.waitingThreads.poll();
            if (nextThread != null) {
                nextThread.interrupt();
            }
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMPThreadMutexattrInit extends LLVMBuiltin {
        @Specialization
        protected int doIntrinsic(VirtualFrame frame, Object attr) {
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
            // seems like pthread_mutexattr_t is just an int in the header / lib sulong uses
            // so we just write the int-value type to the address attr points to
            // create store node
            if (store == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                store = getContextReference().get().getNodeFactory().createStoreNode(LLVMInteropType.ValueKind.I32);
            }
            // store type in attr var
            if (type == null) {
                type = new Integer(0);
            }
            store.executeWithTarget(attr, type);
            // TODO: return with error if type not in {0, 1, 2}
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
            int attrValue = (int) read.executeWithTarget(attr);
            Mutex.Type mutexType = Mutex.Type.DEFAULT_NORMAL;
            if (attrValue == 1) {
                mutexType = Mutex.Type.RECURSIVE;
            } else if (attrValue == 2) {
                mutexType = Mutex.Type.ERRORCHECK;
            }
            if (mutObj == null) {
                getContextReference().get().mutexStorage.put(mutexAddress, new Mutex(0, mutexType));
            }
            return 0;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMPThreadMutexLock extends LLVMBuiltin {
        @Specialization
        protected int doIntrinsic(VirtualFrame frame, Object mutex) {
            long mutexAddress = ((LLVMNativePointer) mutex).asNative();
            Mutex mutexObj = (Mutex) getContextReference().get().mutexStorage.get(mutexAddress);
            if (mutexObj == null) {
                // mutex is not initialized
                // but it works anyway on most implementations
                // so we will make it work here too, just using default type
                // set the internLock counter to 1
                mutexObj = new Mutex(1, Mutex.Type.DEFAULT_NORMAL);
                getContextReference().get().mutexStorage.put(mutexAddress, mutexObj);
                return 0;
            }
            return mutexObj.lock() ? 0 : 15; // internLock will be changed to int type
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMPThreadMutexTrylock extends LLVMBuiltin {
        @Specialization
        protected int doIntrinsic(VirtualFrame frame, Object mutex) {
            long mutexAddress = ((LLVMNativePointer) mutex).asNative();
            Mutex mutexObj = (Mutex) getContextReference().get().mutexStorage.get(mutexAddress);
            if (mutexObj == null) {
                // mutex is not initialized
                // but it works anyway on most implementations
                // so we will make it work here too, just using default type
                // set the internLock counter to 1
                mutexObj = new Mutex(1, Mutex.Type.DEFAULT_NORMAL);
                getContextReference().get().mutexStorage.put(mutexAddress, mutexObj);
                return 0;
            }
            return mutexObj.tryLock() ? 0 : 15; // internLock will be changed to int type
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMPThreadMutexUnlock extends LLVMBuiltin {
        @Specialization
        protected int doIntrinsic(VirtualFrame frame, Object mutex) {
            // TODO: handle calls for not init mutexes
            long mutexAddress = ((LLVMNativePointer) mutex).asNative();
            Mutex mutexObj = (Mutex) getContextReference().get().mutexStorage.get(mutexAddress);
            return mutexObj.unlock() ? 0 : 15; // internLock will be changed to int type
        }
    }

    public static class Cond {
        public enum Type {
            DEFAULT
        }

        private final ConcurrentLinkedQueue<Thread> waitingThreads;
        private final Condition condition;
        private final ReentrantLock internLock;
        private final Type type;

        public Cond() {
            this.waitingThreads = new ConcurrentLinkedQueue<>();
            this.internLock = new ReentrantLock();
            this.condition = internLock.newCondition();
            this.type = Type.DEFAULT;
        }

        public void broadcast() {
            condition.signalAll();
        }

        public void signal() {
            condition.signal();
        }

        public void cTimedwait(long nanos) {
            try {
                condition.awaitNanos(nanos);
            } catch (InterruptedException e) {
            }
        }

        public void cWait() {
            try {
                condition.await();
            } catch (InterruptedException e) {
            }
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMPThreadCondInit extends LLVMBuiltin {
        @Specialization
        protected int doIntrinsic(VirtualFrame frame, Object cond, Object attr) {
            // we can use the address of the native pointer here, bc a cond
            // must only work when using the original variable, not a copy
            // so the address may never change
            long condAddress = ((LLVMNativePointer) cond).asNative();
            Object condObj = getContextReference().get().condStorage.get(condAddress);
            if (condObj == null) {
                getContextReference().get().condStorage.put(condAddress, new Cond());
            }
            return 0;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMPThreadCondBroadcast extends LLVMBuiltin {
        @Specialization
        protected int doIntrinsic(VirtualFrame frame, Object cond) {
            long condAddress = ((LLVMNativePointer) cond).asNative();
            Cond condObj = (Cond) getContextReference().get().condStorage.get(condAddress);
            if (condObj == null) {
                return 15; // cannot broadcast to cond that does not exist yet
            }
            condObj.broadcast();
            return 0;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMPThreadCondSignal extends LLVMBuiltin {
        @Specialization
        protected int doIntrinsic(VirtualFrame frame, Object cond) {
            long condAddress = ((LLVMNativePointer) cond).asNative();
            Cond condObj = (Cond) getContextReference().get().condStorage.get(condAddress);
            if (condObj == null) {
                return 15; // cannot signal to cond that does not exist yet
            }
            condObj.signal();
            return 0;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMPThreadCondTimedwait extends LLVMBuiltin {
        @Specialization
        protected int doIntrinsic(VirtualFrame frame, Object cond, Object mutex, Object abstime) {
            long condAddress = ((LLVMNativePointer) cond).asNative();
            Cond condObj = (Cond) getContextReference().get().condStorage.get(condAddress);
            if (condObj == null) {
                // init and then wait
                condObj = new Cond();
                getContextReference().get().condStorage.put(condAddress, condObj);
            }
            // TODO: handling of time
            condObj.cTimedwait(100);
            return 0;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMPThreadCondWait extends LLVMBuiltin {
        @Specialization
        protected int doIntrinsic(VirtualFrame frame, Object cond, Object mutex) {
            long condAddress = ((LLVMNativePointer) cond).asNative();
            Cond condObj = (Cond) getContextReference().get().condStorage.get(condAddress);
            if (condObj == null) {
                // init and then wait
                condObj = new Cond();
                getContextReference().get().condStorage.put(condAddress, condObj);
            }
            condObj.cWait();
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
