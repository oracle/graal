package com.oracle.truffle.llvm.nodes.intrinsics.multithreading;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMBuiltin;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMLoadNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class LLVMPThreadRWLockIntrinsics {
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

        public boolean timedReadLock(long seconds) {
            try {
                return this.readWriteLock.readLock().tryLock(seconds, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return false;
        }

        public boolean timedWriteLock(long seconds) {
            try {
                return this.readWriteLock.writeLock().tryLock(seconds, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return false;
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
            LLVMLanguage.getLLVMContextReference().get().condStorage.remove(rwlockAddress);
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
            Object condObj = LLVMLanguage.getLLVMContextReference().get().condStorage.get(rwlockAddress);
            if (condObj == null) {
                LLVMLanguage.getLLVMContextReference().get().condStorage.put(rwlockAddress, new RWLock());
            }
            return 0;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMPThreadRWLockRdlock extends LLVMBuiltin {
        @Specialization
        protected int doIntrinsic(VirtualFrame frame, Object rwlock) {
            long rwlockAddress = ((LLVMNativePointer) rwlock).asNative();
            RWLock rwlockObj = (RWLock) LLVMLanguage.getLLVMContextReference().get().rwlockStorage.get(rwlockAddress);
            if (rwlockObj == null) {
                // rwlock is not initialized
                // but it works anyway on most implementations
                rwlockObj = new RWLock();
                LLVMLanguage.getLLVMContextReference().get().mutexStorage.put(rwlockAddress, rwlockObj);
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
            RWLock rwlockObj = (RWLock) LLVMLanguage.getLLVMContextReference().get().rwlockStorage.get(rwlockAddress);
            if (rwlockObj == null) {
                // rwlock is not initialized
                // but it works anyway on most implementations
                rwlockObj = new RWLock();
                LLVMLanguage.getLLVMContextReference().get().mutexStorage.put(rwlockAddress, rwlockObj);
            }
            // TODO: error code stuff, EBUSY should be here
            return rwlockObj.tryReadLock() ? 0 : 15;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMPThreadRWLockWrlock extends LLVMBuiltin {
        @Specialization
        protected int doIntrinsic(VirtualFrame frame, Object rwlock) {
            long rwlockAddress = ((LLVMNativePointer) rwlock).asNative();
            RWLock rwlockObj = (RWLock) LLVMLanguage.getLLVMContextReference().get().rwlockStorage.get(rwlockAddress);
            if (rwlockObj == null) {
                // rwlock is not initialized
                // but it works anyway on most implementations
                rwlockObj = new RWLock();
                LLVMLanguage.getLLVMContextReference().get().mutexStorage.put(rwlockAddress, rwlockObj);
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
            RWLock rwlockObj = (RWLock) LLVMLanguage.getLLVMContextReference().get().rwlockStorage.get(rwlockAddress);
            if (rwlockObj == null) {
                // rwlock is not initialized
                // but it works anyway on most implementations
                rwlockObj = new RWLock();
                LLVMLanguage.getLLVMContextReference().get().mutexStorage.put(rwlockAddress, rwlockObj);
            }
            return rwlockObj.tryWriteLock() ? 0 : 15;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMPThreadRWLockUnlock extends LLVMBuiltin {
        @Specialization
        protected int doIntrinsic(VirtualFrame frame, Object rwlock) {
            long rwlockAddress = ((LLVMNativePointer) rwlock).asNative();
            RWLock rwlockObj = (RWLock) LLVMLanguage.getLLVMContextReference().get().rwlockStorage.get(rwlockAddress);
            if (rwlockObj == null) {
                // rwlock is not initialized
                // but it works anyway on most implementations
                rwlockObj = new RWLock();
                LLVMLanguage.getLLVMContextReference().get().mutexStorage.put(rwlockAddress, rwlockObj);
            }
            rwlockObj.unlock();
            return 0;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMPThreadRWLockTimedrdlock extends LLVMBuiltin {
        @Child
        LLVMLoadNode read;

        @Specialization
        protected int doIntrinsic(VirtualFrame frame, Object rwlock, Object abstime) {
            if (read == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                read = LLVMLanguage.getLLVMContextReference().get().getNodeFactory().createLoadNode(LLVMInteropType.ValueKind.I64);
            }

            long rwlockAddress = ((LLVMNativePointer) rwlock).asNative();
            RWLock rwlockObj = (RWLock) LLVMLanguage.getLLVMContextReference().get().rwlockStorage.get(rwlockAddress);
            if (rwlockObj == null) {
                // rwlock is not initialized
                // but it works anyway on most implementations
                rwlockObj = new RWLock();
                LLVMLanguage.getLLVMContextReference().get().mutexStorage.put(rwlockAddress, rwlockObj);
            }
            // if lock immediately succeeds, abstime parameter should not be checked
            if (rwlockObj.tryReadLock()) {
                return 0;
            }
            // TODO: handling of time (nanoseconds possible?)
            // in sulong timespec only comes as long with the seconds as value
            long absSeconds = (long) read.executeWithTarget(abstime);
            long waitTime = absSeconds - System.currentTimeMillis() / 1000;

            rwlockObj.timedReadLock(waitTime);
            return 0;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMPThreadRWLockTimedwrlock extends LLVMBuiltin {
        @Child LLVMLoadNode read;

        @Specialization
        protected int doIntrinsic(VirtualFrame frame, Object rwlock, Object abstime) {
            if (read == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                read = LLVMLanguage.getLLVMContextReference().get().getNodeFactory().createLoadNode(LLVMInteropType.ValueKind.I64);
            }

            long rwlockAddress = ((LLVMNativePointer) rwlock).asNative();
            RWLock rwlockObj = (RWLock) LLVMLanguage.getLLVMContextReference().get().rwlockStorage.get(rwlockAddress);
            if (rwlockObj == null) {
                // rwlock is not initialized
                // but it works anyway on most implementations
                rwlockObj = new RWLock();
                LLVMLanguage.getLLVMContextReference().get().mutexStorage.put(rwlockAddress, rwlockObj);
            }
            // if lock immediately succeeds, abstime parameter should not be checked
            if (rwlockObj.tryWriteLock()) {
                return 0;
            }
            // TODO: handling of time (nanoseconds possible?)
            // in sulong timespec only comes as long with the seconds as value
            long absSeconds = (long) read.executeWithTarget(abstime);
            long waitTime = absSeconds - System.currentTimeMillis() / 1000;

            rwlockObj.timedWriteLock(waitTime);
            return 0;
        }
    }
}
