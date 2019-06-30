package com.oracle.truffle.llvm.nodes.intrinsics.multithreading;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMBuiltin;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;

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

    @NodeChild(type = LLVMExpressionNode.class, value = "rwlock")
    public abstract static class LLVMPThreadRWLockDestroy extends LLVMBuiltin {
        @Specialization
        //+++ @CompilerDirectives.TruffleBoundary
        protected int doIntrinsic(VirtualFrame frame, Object rwlock, @CachedContext(LLVMLanguage.class) TruffleLanguage.ContextReference<LLVMContext> ctxRef) {
            long rwlockAddress = ((LLVMNativePointer) rwlock).asNative();
            ctxRef.get().rwlockStorage.remove(rwlockAddress);
            return 0;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class, value = "rwlock")
    @NodeChild(type = LLVMExpressionNode.class, value = "attr")
    public abstract static class LLVMPThreadRWLockInit extends LLVMBuiltin {
        @Specialization
        //+++ @CompilerDirectives.TruffleBoundary
        protected int doIntrinsic(VirtualFrame frame, Object rwlock, Object attr, @CachedContext(LLVMLanguage.class) TruffleLanguage.ContextReference<LLVMContext> ctxRef) {
            // we can use the address of the native pointer here, bc a rwlock
            // must only work when using the original variable, not a copy
            // so the address may never change
            long rwlockAddress = ((LLVMNativePointer) rwlock).asNative();
            RWLock lock = (RWLock) ctxRef.get().rwlockStorage.get(rwlockAddress);
            if (lock == null) {
                ctxRef.get().rwlockStorage.put(rwlockAddress, new RWLock());
            }
            return 0;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class, value = "rwlock")
    public abstract static class LLVMPThreadRWLockRdlock extends LLVMBuiltin {
        @Specialization
        //+++ @CompilerDirectives.TruffleBoundary
        protected int doIntrinsic(VirtualFrame frame, Object rwlock, @CachedContext(LLVMLanguage.class) TruffleLanguage.ContextReference<LLVMContext> ctxRef) {
            long rwlockAddress = ((LLVMNativePointer) rwlock).asNative();
            RWLock rwlockObj = (RWLock) ctxRef.get().rwlockStorage.get(rwlockAddress);
            if (rwlockObj == null) {
                // rwlock is not initialized
                // but it works anyway on most implementations
                rwlockObj = new RWLock();
                ctxRef.get().rwlockStorage.put(rwlockAddress, rwlockObj);
            }
            rwlockObj.readLock();
            return 0;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class, value = "rwlock")
    public abstract static class LLVMPThreadRWLockTryrdlock extends LLVMBuiltin {
        @Specialization
        //+++ @CompilerDirectives.TruffleBoundary
        protected int doIntrinsic(VirtualFrame frame, Object rwlock, @CachedContext(LLVMLanguage.class) TruffleLanguage.ContextReference<LLVMContext> ctxRef) {
            long rwlockAddress = ((LLVMNativePointer) rwlock).asNative();
            RWLock rwlockObj = (RWLock) ctxRef.get().rwlockStorage.get(rwlockAddress);
            if (rwlockObj == null) {
                // rwlock is not initialized
                // but it works anyway on most implementations
                rwlockObj = new RWLock();
                ctxRef.get().rwlockStorage.put(rwlockAddress, rwlockObj);
            }
            // TODO: error code stuff, EBUSY should be here
            return rwlockObj.tryReadLock() ? 0 : 15;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class, value = "rwlock")
    public abstract static class LLVMPThreadRWLockWrlock extends LLVMBuiltin {
        @Specialization
        //+++ @CompilerDirectives.TruffleBoundary
        protected int doIntrinsic(VirtualFrame frame, Object rwlock, @CachedContext(LLVMLanguage.class) TruffleLanguage.ContextReference<LLVMContext> ctxRef) {
            long rwlockAddress = ((LLVMNativePointer) rwlock).asNative();
            RWLock rwlockObj = (RWLock) ctxRef.get().rwlockStorage.get(rwlockAddress);
            if (rwlockObj == null) {
                // rwlock is not initialized
                // but it works anyway on most implementations
                rwlockObj = new RWLock();
                ctxRef.get().rwlockStorage.put(rwlockAddress, rwlockObj);
            }
            rwlockObj.writeLock();
            return 0;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class, value = "rwlock")
    public abstract static class LLVMPThreadRWLockTrywrlock extends LLVMBuiltin {
        @Specialization
        //+++ @CompilerDirectives.TruffleBoundary
        protected int doIntrinsic(VirtualFrame frame, Object rwlock, @CachedContext(LLVMLanguage.class) TruffleLanguage.ContextReference<LLVMContext> ctxRef) {
            long rwlockAddress = ((LLVMNativePointer) rwlock).asNative();
            RWLock rwlockObj = (RWLock) ctxRef.get().rwlockStorage.get(rwlockAddress);
            if (rwlockObj == null) {
                // rwlock is not initialized
                // but it works anyway on most implementations
                rwlockObj = new RWLock();
                ctxRef.get().rwlockStorage.put(rwlockAddress, rwlockObj);
            }
            return rwlockObj.tryWriteLock() ? 0 : 15;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class, value = "rwlock")
    public abstract static class LLVMPThreadRWLockUnlock extends LLVMBuiltin {
        @Specialization
        //+++ @CompilerDirectives.TruffleBoundary
        protected int doIntrinsic(VirtualFrame frame, Object rwlock, @CachedContext(LLVMLanguage.class) TruffleLanguage.ContextReference<LLVMContext> ctxRef) {
            long rwlockAddress = ((LLVMNativePointer) rwlock).asNative();
            RWLock rwlockObj = (RWLock) ctxRef.get().rwlockStorage.get(rwlockAddress);
            if (rwlockObj == null) {
                // rwlock is not initialized
                // but it works anyway on most implementations
                rwlockObj = new RWLock();
                ctxRef.get().rwlockStorage.put(rwlockAddress, rwlockObj);
            }
            rwlockObj.unlock();
            return 0;
        }
    }
}
