package com.oracle.truffle.llvm.nodes.intrinsics.multithreading;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMBuiltin;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class LLVMPThreadRWLockIntrinsics {
    public static class RWLock {
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
        protected int doIntrinsic(VirtualFrame frame, LLVMPointer rwlock, @CachedContext(LLVMLanguage.class) TruffleLanguage.ContextReference<LLVMContext> ctxRef) {
            UtilAccess.removeLLVMPointerObj(ctxRef.get().rwlockStorage, rwlock);
            return 0;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class, value = "rwlock")
    @NodeChild(type = LLVMExpressionNode.class, value = "attr")
    public abstract static class LLVMPThreadRWLockInit extends LLVMBuiltin {
        @Specialization
        protected int doIntrinsic(VirtualFrame frame, LLVMPointer rwlock, LLVMPointer attr, @CachedContext(LLVMLanguage.class) TruffleLanguage.ContextReference<LLVMContext> ctxRef) {
            RWLock lock = (RWLock) UtilAccess.getLLVMPointerObj(ctxRef.get().rwlockStorage, rwlock);
            // TODO: maybe we want to init it again and overwrite current state
            if (lock == null) {
                UtilAccess.putLLVMPointerObj(ctxRef.get().rwlockStorage, rwlock, new RWLock());
            }
            return 0;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class, value = "rwlock")
    public abstract static class LLVMPThreadRWLockRdlock extends LLVMBuiltin {
        @Specialization
        protected int doIntrinsic(VirtualFrame frame, LLVMPointer rwlock, @CachedContext(LLVMLanguage.class) TruffleLanguage.ContextReference<LLVMContext> ctxRef) {
            RWLock rwlockObj = (RWLock) UtilAccess.getLLVMPointerObj(ctxRef.get().rwlockStorage, rwlock);
            if (rwlockObj == null) {
                // rwlock is not initialized
                // but it works anyway on most implementations
                // so we init it here
                rwlockObj = new RWLock();
                UtilAccess.putLLVMPointerObj(ctxRef.get().rwlockStorage, rwlock, rwlockObj);
            }
            rwlockObj.readLock();
            return 0;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class, value = "rwlock")
    public abstract static class LLVMPThreadRWLockTryrdlock extends LLVMBuiltin {
        @Specialization
        protected int doIntrinsic(VirtualFrame frame, LLVMPointer rwlock, @CachedContext(LLVMLanguage.class) TruffleLanguage.ContextReference<LLVMContext> ctxRef) {
            RWLock rwlockObj = (RWLock) UtilAccess.getLLVMPointerObj(ctxRef.get().rwlockStorage, rwlock);
            if (rwlockObj == null) {
                // rwlock is not initialized
                // but it works anyway on most implementations
                // so we init it here
                rwlockObj = new RWLock();
                UtilAccess.putLLVMPointerObj(ctxRef.get().rwlockStorage, rwlock, rwlockObj);
            }
            return rwlockObj.tryReadLock() ? 0 : CConstants.getEBUSY();
        }
    }

    @NodeChild(type = LLVMExpressionNode.class, value = "rwlock")
    public abstract static class LLVMPThreadRWLockWrlock extends LLVMBuiltin {
        @Specialization
        protected int doIntrinsic(VirtualFrame frame, LLVMPointer rwlock, @CachedContext(LLVMLanguage.class) TruffleLanguage.ContextReference<LLVMContext> ctxRef) {
            RWLock rwlockObj = (RWLock) UtilAccess.getLLVMPointerObj(ctxRef.get().rwlockStorage, rwlock);
            if (rwlockObj == null) {
                // rwlock is not initialized
                // but it works anyway on most implementations
                // so we init it here
                rwlockObj = new RWLock();
                UtilAccess.putLLVMPointerObj(ctxRef.get().rwlockStorage, rwlock, rwlockObj);
            }
            rwlockObj.writeLock();
            return 0;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class, value = "rwlock")
    public abstract static class LLVMPThreadRWLockTrywrlock extends LLVMBuiltin {
        @Specialization
        protected int doIntrinsic(VirtualFrame frame, LLVMPointer rwlock, @CachedContext(LLVMLanguage.class) TruffleLanguage.ContextReference<LLVMContext> ctxRef) {
            RWLock rwlockObj = (RWLock) UtilAccess.getLLVMPointerObj(ctxRef.get().rwlockStorage, rwlock);
            if (rwlockObj == null) {
                // rwlock is not initialized
                // but it works anyway on most implementations
                // so we init it here
                rwlockObj = new RWLock();
                UtilAccess.putLLVMPointerObj(ctxRef.get().rwlockStorage, rwlock, rwlockObj);
            }
            return rwlockObj.tryWriteLock() ? 0 : CConstants.getEBUSY();
        }
    }

    @NodeChild(type = LLVMExpressionNode.class, value = "rwlock")
    public abstract static class LLVMPThreadRWLockUnlock extends LLVMBuiltin {
        @Specialization
        protected int doIntrinsic(VirtualFrame frame, LLVMPointer rwlock, @CachedContext(LLVMLanguage.class) TruffleLanguage.ContextReference<LLVMContext> ctxRef) {
            RWLock rwlockObj = (RWLock) UtilAccess.getLLVMPointerObj(ctxRef.get().rwlockStorage, rwlock);
            if (rwlockObj == null) {
                // rwlock is not initialized, but no error code specified
                return 0;
            }
            rwlockObj.unlock();
            return 0;
        }
    }
}
