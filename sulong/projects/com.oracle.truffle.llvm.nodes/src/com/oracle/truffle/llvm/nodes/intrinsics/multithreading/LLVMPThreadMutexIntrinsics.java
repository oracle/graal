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
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMLoadNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMStoreNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;
import org.graalvm.nativeimage.c.constant.CConstant;

import java.util.concurrent.locks.ReentrantLock;

public class LLVMPThreadMutexIntrinsics {
    public static class Mutex {
        public enum MutexType {
            DEFAULT_NORMAL,
            ERRORCHECK,
            RECURSIVE
        }

        protected final ReentrantLock internLock;
        private final MutexType type;

        public Mutex(MutexType type) {
            this.internLock = new ReentrantLock();
            if (type != null) {
                this.type = type;
            } else {
                this.type = MutexType.DEFAULT_NORMAL;
            }
        }

        public MutexType getType() {
            return this.type;
        }

        // change to int for error codes
        public boolean lock() {
            if (this.internLock.isHeldByCurrentThread()) {
                if (this.type == MutexType.DEFAULT_NORMAL) {
                    // deadlock according to spec
                    // does this make sense?
                    // TODO: user warning?
                    while (true) {
                        try {
                            Thread.sleep(Long.MAX_VALUE);
                        } catch (InterruptedException e) {
                            // should be possible to interrupt and stop the thread anyway
                            break;
                        }
                    }
                }
                if (this.type == MutexType.ERRORCHECK) {
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

    @NodeChild(type = LLVMExpressionNode.class, value = "attr")
    public abstract static class LLVMPThreadMutexattrDestroy extends LLVMBuiltin {
        @Specialization
        protected int doIntrinsic(VirtualFrame frame, LLVMPointer attr) {
            return 0;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class, value = "attr")
    public abstract static class LLVMPThreadMutexattrInit extends LLVMBuiltin {
        @Specialization
        protected int doIntrinsic(VirtualFrame frame, LLVMPointer attr) {
            // seems like pthread_mutexattr_t is just an int in the header / lib sulong uses
            // so no need to init here
            return 0;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class, value = "attr")
    @NodeChild(type = LLVMExpressionNode.class, value = "type")
    public abstract static class LLVMPThreadMutexattrSettype extends LLVMBuiltin {
        @Child
        LLVMStoreNode store = null;

        @Specialization
        protected int doIntrinsic(VirtualFrame frame, LLVMPointer attr, int type, @CachedContext(LLVMLanguage.class) TruffleLanguage.ContextReference<LLVMContext> ctxRef) {
            // seems like pthread_mutexattr_t is just an int in the header / lib sulong uses
            // so we just write the int-value type to the address attr points to
            // create store node
            if (store == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                store = ctxRef.get().getNodeFactory().createStoreNode(LLVMInteropType.ValueKind.I32);
            }
            // check if valid type
            if (type != CConstants.getPTHREADMUTEXDEFAULT() && type != CConstants.getPTHREADMUTEXERRORCHECK() && type != CConstants.getPTHREADMUTEXNORMAL() && type != CConstants.getPTHREADMUTEXRECURSIVE()) {
                return CConstants.getEINVAL();
            }
            // store type in attr var
            store.executeWithTarget(attr, type);
            return 0;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class, value = "mutex")
    public abstract static class LLVMPThreadMutexDestroy extends LLVMBuiltin {
        @Specialization
        protected int doIntrinsic(VirtualFrame frame, LLVMPointer mutex, @CachedContext(LLVMLanguage.class) TruffleLanguage.ContextReference<LLVMContext> ctxRef) {
            UtilAccess.removeLLVMPointerObj(ctxRef.get().mutexStorage, mutex);
            return 0;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class, value = "mutex")
    @NodeChild(type = LLVMExpressionNode.class, value = "attr")
    public abstract static class LLVMPThreadMutexInit extends LLVMBuiltin {
        @Child
        LLVMLoadNode read = null;

        @Specialization
        protected int doIntrinsic(VirtualFrame frame, LLVMPointer mutex, LLVMPointer attr, @CachedContext(LLVMLanguage.class) TruffleLanguage.ContextReference<LLVMContext> ctxRef) {
            if (read == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                read = ctxRef.get().getNodeFactory().createLoadNode(LLVMInteropType.ValueKind.I32);
            }
            // we can use the address of the pointer here, bc a mutex
            // must only work when using the original variable, not a copy
            // so the address may never change
            Object mutObj = UtilAccess.getLLVMPointerObj(ctxRef.get().mutexStorage, mutex);
            int attrValue = 0;
            if (!attr.isNull()) {
                attrValue = (int) read.executeWithTarget(attr);
            }
            Mutex.MutexType mutexType = Mutex.MutexType.DEFAULT_NORMAL;
            if (attrValue == CConstants.getPTHREADMUTEXERRORCHECK()) {
                mutexType = Mutex.MutexType.ERRORCHECK;
            } else if (attrValue == CConstants.getPTHREADMUTEXRECURSIVE()) {
                mutexType = Mutex.MutexType.RECURSIVE;
            }
            if (mutObj == null) {
                UtilAccess.putLLVMPointerObj(ctxRef.get().mutexStorage, mutex, new Mutex(mutexType));
            }
            return 0;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class, value = "mutex")
    public abstract static class LLVMPThreadMutexLock extends LLVMBuiltin {
        @Specialization
        protected int doIntrinsic(VirtualFrame frame, LLVMPointer mutex, @CachedContext(LLVMLanguage.class) TruffleLanguage.ContextReference<LLVMContext> ctxRef) {
            Mutex mutexObj = (Mutex) UtilAccess.getLLVMPointerObj(ctxRef.get().mutexStorage, mutex);
            if (mutexObj == null) {
                // mutex is not initialized
                // but it works anyway on most implementations
                // so we will make it work here too, just using default type
                // set the internLock counter to 1
                mutexObj = new Mutex(Mutex.MutexType.DEFAULT_NORMAL);
                UtilAccess.putLLVMPointerObj(ctxRef.get().mutexStorage, mutex, mutexObj);
            }
            // lock only returns false when mutex is errorcheck type and current thread already holds it
            return mutexObj.lock() ? 0 : CConstants.getEDEADLK();
        }
    }

    @NodeChild(type = LLVMExpressionNode.class, value = "mutex")
    public abstract static class LLVMPThreadMutexTrylock extends LLVMBuiltin {
        @Specialization
        protected int doIntrinsic(VirtualFrame frame, LLVMPointer mutex, @CachedContext(LLVMLanguage.class) TruffleLanguage.ContextReference<LLVMContext> ctxRef) {
            Mutex mutexObj = (Mutex) UtilAccess.getLLVMPointerObj(ctxRef.get().mutexStorage, mutex);
            if (mutexObj == null) {
                // mutex is not initialized
                // but it works anyway on most implementations
                // so we will make it work here too, just using default type
                // set the internLock counter to 1
                mutexObj = new Mutex(Mutex.MutexType.DEFAULT_NORMAL);
                UtilAccess.putLLVMPointerObj(ctxRef.get().mutexStorage, mutex, mutexObj);
            }
            return mutexObj.tryLock() ? 0 : CConstants.getEBUSY();
        }
    }

    @NodeChild(type = LLVMExpressionNode.class, value = "mutex")
    public abstract static class LLVMPThreadMutexUnlock extends LLVMBuiltin {
        @Specialization
        protected int doIntrinsic(VirtualFrame frame, LLVMPointer mutex, @CachedContext(LLVMLanguage.class) TruffleLanguage.ContextReference<LLVMContext> ctxRef) {
            Mutex mutexObj = (Mutex) UtilAccess.getLLVMPointerObj(ctxRef.get().mutexStorage, mutex);
            if (mutexObj == null) {
                return CConstants.getEPERM();
            }
            return mutexObj.unlock() ? 0 : CConstants.getEPERM();
        }
    }
}
