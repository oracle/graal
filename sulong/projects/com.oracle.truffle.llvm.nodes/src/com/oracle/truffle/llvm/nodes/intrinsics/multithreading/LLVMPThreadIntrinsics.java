package com.oracle.truffle.llvm.nodes.intrinsics.multithreading;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

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
                if (!thReturnPtr.isNull() && retVal != null) {
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

        private final ReentrantLock internLock;
        private final Type type;

        public Mutex(Type type) {
            this.internLock = new ReentrantLock();
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
            if (this.internLock.isHeldByCurrentThread()) {
                if (this.type == Type.DEFAULT_NORMAL) {
                    // deadlock according to spec
                    // does this make sense?
                    while (true) {
                        try {
                            Thread.sleep(Long.MAX_VALUE);
                        } catch (InterruptedException e) {
                            // should be possible to interrupt and stop the thread anyway
                            break;
                        }
                    }
                }
                if (this.type == Type.ERRORCHECK) {
                    return false;
                }
            }
            internLock.lock();
            return true;
        }

        public boolean tryLock() {
            return internLock.tryLock();
        }

        public boolean unlock() {
            if (!internLock.isHeldByCurrentThread()) {
                return false;
            }
            internLock.unlock();
            return true;
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
    public abstract static class LLVMPThreadMutexDestroy extends LLVMBuiltin {
        @Specialization
        protected int doIntrinsic(VirtualFrame frame, Object mutex) {
            long mutexAddress = ((LLVMNativePointer) mutex).asNative();
            getContextReference().get().mutexStorage.remove(mutexAddress);
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
                getContextReference().get().mutexStorage.put(mutexAddress, new Mutex(mutexType));
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
                mutexObj = new Mutex(Mutex.Type.DEFAULT_NORMAL);
                getContextReference().get().mutexStorage.put(mutexAddress, mutexObj);
            }
            // TODO: error code handling
            return mutexObj.lock() ? 0 : 15;
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
                mutexObj = new Mutex(Mutex.Type.DEFAULT_NORMAL);
                getContextReference().get().mutexStorage.put(mutexAddress, mutexObj);
            }
            // TODO: error code stuff
            return mutexObj.tryLock() ? 0 : 15;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMPThreadMutexUnlock extends LLVMBuiltin {
        @Specialization
        protected int doIntrinsic(VirtualFrame frame, Object mutex) {
            long mutexAddress = ((LLVMNativePointer) mutex).asNative();
            Mutex mutexObj = (Mutex) getContextReference().get().mutexStorage.get(mutexAddress);
            // TODO: error code stuff
            if (mutexObj == null) {
                return 5;
            }
            return mutexObj.unlock() ? 0 : 15; // internLock will be changed to int type
        }
    }

    public static class Cond {
        public enum Type {
            DEFAULT
        }

        private Condition condition;
        private Mutex curMutex;
        private final Type type;

        public Cond() {
            this.condition = null;
            this.curMutex = null;
            this.type = Type.DEFAULT;
        }

        public void broadcast() {
            if (condition != null) {
                condition.signalAll();
            }
        }

        public void signal() {
            if (condition != null) {
                condition.signal();
            }
        }

        public boolean cTimedwait(Mutex mutex, long seconds) {
            if (this.curMutex == null) {
                this.curMutex = mutex;
                this.condition = mutex.internLock.newCondition();
            } else if (this.curMutex != mutex) {
                this.curMutex = mutex;
                this.condition = mutex.internLock.newCondition();
            }
            if (!this.curMutex.internLock.isHeldByCurrentThread()) {
                return false;
            }
            try {
                this.condition.await(seconds, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
            }
            return true;
        }

        public boolean cWait(Mutex mutex) {
            if (this.curMutex == null) {
                this.curMutex = mutex;
                this.condition = mutex.internLock.newCondition();
            } else if (this.curMutex != mutex) {
                this.curMutex = mutex;
                this.condition = mutex.internLock.newCondition();
            }
            if (!this.curMutex.internLock.isHeldByCurrentThread()) {
                return false;
            }
            try {
                this.condition.await();
            } catch (InterruptedException e) {
            }
            return true;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMPThreadCondDestroy extends LLVMBuiltin {
        @Specialization
        protected int doIntrinsic(VirtualFrame frame, Object mutex) {
            long condAddress = ((LLVMNativePointer) mutex).asNative();
            getContextReference().get().condStorage.remove(condAddress);
            return 0;
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
        @Child LLVMLoadNode read;

        @Specialization
        protected int doIntrinsic(VirtualFrame frame, Object cond, Object mutex, Object abstime) {
            if (read == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                read = getContextReference().get().getNodeFactory().createLoadNode(LLVMInteropType.ValueKind.I64);
            }

            long condAddress = ((LLVMNativePointer) cond).asNative();
            long mutexAddress = ((LLVMNativePointer) mutex).asNative();

            Cond condObj = (Cond) getContextReference().get().condStorage.get(condAddress);
            Mutex mutexObj = (Mutex) getContextReference().get().mutexStorage.get(mutexAddress);
            if (condObj == null) {
                // init and then wait
                condObj = new Cond();
                getContextReference().get().condStorage.put(condAddress, condObj);
            }
            // TODO: handling of time (nanoseconds possible?)
            // in sulong timespec only comes as long with the seconds as value
            long absSeconds = (long) read.executeWithTarget(abstime);
            long waitTime = absSeconds - System.currentTimeMillis() / 1000;
            condObj.cTimedwait(mutexObj, waitTime);
            return 0;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMPThreadCondWait extends LLVMBuiltin {
        @Specialization
        protected int doIntrinsic(VirtualFrame frame, Object cond, Object mutex) {
            long condAddress = ((LLVMNativePointer) cond).asNative();
            long mutexAddress = ((LLVMNativePointer) mutex).asNative();

            Cond condObj = (Cond) getContextReference().get().condStorage.get(condAddress);
            Mutex mutexObj = (Mutex) getContextReference().get().mutexStorage.get(mutexAddress);
            if (condObj == null) {
                // init and then wait
                condObj = new Cond();
                getContextReference().get().condStorage.put(condAddress, condObj);
            }
            condObj.cWait(mutexObj);
            return 0;
        }
    }

    public static class RWLock {
        // TODO: add timed stuff
        private final ReadWriteLock readWriteLock;

        public RWLock() {
            this.readWriteLock = new ReentrantReadWriteLock();
        }

        public void readLock() {
            try {
                this.readWriteLock.readLock().lockInterruptibly();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        public boolean tryReadLock() {
            return this.readWriteLock.readLock().tryLock();
        }

        public void writeLock() {
            try {
                this.readWriteLock.writeLock().lockInterruptibly();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        public boolean tryWriteLock() {
            return this.readWriteLock.writeLock().tryLock();
        }

        public void unlock() {
            try {
                this.readWriteLock.readLock().unlock();
            } catch (Exception e) {
            }
            try {
                this.readWriteLock.writeLock().unlock();
            } catch (Exception e) {
            }
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMPThreadRWLockDestroy extends LLVMBuiltin {
        @Specialization
        protected int doIntrinsic(VirtualFrame frame, Object rwlock) {
            long rwlockAddress = ((LLVMNativePointer) rwlock).asNative();
            getContextReference().get().condStorage.remove(rwlockAddress);
            return 0;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMPThreadRWLockInit extends LLVMBuiltin {
        @Specialization
        protected int doIntrinsic(VirtualFrame frame, Object rwlock, Object attr) {
            // we can use the address of the native pointer here, bc a rwlock
            // must only work when using the original variable, not a copy
            // so the address may never change
            long rwlockAddress = ((LLVMNativePointer) rwlock).asNative();
            Object condObj = getContextReference().get().condStorage.get(rwlockAddress);
            if (condObj == null) {
                getContextReference().get().condStorage.put(rwlockAddress, new RWLock());
            }
            return 0;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMPThreadRWLockRdlock extends LLVMBuiltin {
        @Specialization
        protected int doIntrinsic(VirtualFrame frame, Object rwlock) {
            long rwlockAddress = ((LLVMNativePointer) rwlock).asNative();
            RWLock rwlockObj = (RWLock) getContextReference().get().rwlockStorage.get(rwlockAddress);
            if (rwlockObj == null) {
                // rwlock is not initialized
                // but it works anyway on most implementations
                rwlockObj = new RWLock();
                getContextReference().get().mutexStorage.put(rwlockAddress, rwlockObj);
            }
            rwlockObj.readLock();
            return 0;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMPThreadRWLockTryrdlock extends LLVMBuiltin {
        @Specialization
        protected int doIntrinsic(VirtualFrame frame, Object rwlock) {
            long rwlockAddress = ((LLVMNativePointer) rwlock).asNative();
            RWLock rwlockObj = (RWLock) getContextReference().get().rwlockStorage.get(rwlockAddress);
            if (rwlockObj == null) {
                // rwlock is not initialized
                // but it works anyway on most implementations
                rwlockObj = new RWLock();
                getContextReference().get().mutexStorage.put(rwlockAddress, rwlockObj);
            }
            return rwlockObj.tryReadLock() ? 0 : 15;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMPThreadRWLockWrlock extends LLVMBuiltin {
        @Specialization
        protected int doIntrinsic(VirtualFrame frame, Object rwlock) {
            long rwlockAddress = ((LLVMNativePointer) rwlock).asNative();
            RWLock rwlockObj = (RWLock) getContextReference().get().rwlockStorage.get(rwlockAddress);
            if (rwlockObj == null) {
                // rwlock is not initialized
                // but it works anyway on most implementations
                rwlockObj = new RWLock();
                getContextReference().get().mutexStorage.put(rwlockAddress, rwlockObj);
            }
            rwlockObj.writeLock();
            return 0;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMPThreadRWLockTrywrlock extends LLVMBuiltin {
        @Specialization
        protected int doIntrinsic(VirtualFrame frame, Object rwlock) {
            long rwlockAddress = ((LLVMNativePointer) rwlock).asNative();
            RWLock rwlockObj = (RWLock) getContextReference().get().rwlockStorage.get(rwlockAddress);
            if (rwlockObj == null) {
                // rwlock is not initialized
                // but it works anyway on most implementations
                rwlockObj = new RWLock();
                getContextReference().get().mutexStorage.put(rwlockAddress, rwlockObj);
            }
            return rwlockObj.tryWriteLock() ? 0 : 15;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMPThreadRWLockUnlock extends LLVMBuiltin {
        @Specialization
        protected int doIntrinsic(VirtualFrame frame, Object rwlock) {
            long rwlockAddress = ((LLVMNativePointer) rwlock).asNative();
            RWLock rwlockObj = (RWLock) getContextReference().get().rwlockStorage.get(rwlockAddress);
            if (rwlockObj == null) {
                // rwlock is not initialized
                // but it works anyway on most implementations
                rwlockObj = new RWLock();
                getContextReference().get().mutexStorage.put(rwlockAddress, rwlockObj);
            }
            rwlockObj.unlock();
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
