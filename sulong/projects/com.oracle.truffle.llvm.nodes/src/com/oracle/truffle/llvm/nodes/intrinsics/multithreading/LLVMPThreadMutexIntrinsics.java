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
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMStoreNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

import java.util.concurrent.locks.ReentrantLock;

public class LLVMPThreadMutexIntrinsics {
    public static class Mutex {
        public enum Type {
            DEFAULT_NORMAL,
            ERRORCHECK,
            RECURSIVE
        }

        protected final ReentrantLock internLock;
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
        @Child
        LLVMStoreNode store = null;

        @Specialization
        protected int doIntrinsic(VirtualFrame frame, Object attr, Object type) {
            // seems like pthread_mutexattr_t is just an int in the header / lib sulong uses
            // so we just write the int-value type to the address attr points to
            // create store node
            if (store == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                store = LLVMLanguage.getLLVMContextReference().get().getNodeFactory().createStoreNode(LLVMInteropType.ValueKind.I32);
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
            LLVMLanguage.getLLVMContextReference().get().mutexStorage.remove(mutexAddress);
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
                read = LLVMLanguage.getLLVMContextReference().get().getNodeFactory().createLoadNode(LLVMInteropType.ValueKind.I32);
            }
            // we can use the address of the native pointer here, bc a mutex
            // must only work when using the original variable, not a copy
            // so the address may never change
            long mutexAddress = ((LLVMNativePointer) mutex).asNative();
            Object mutObj = LLVMLanguage.getLLVMContextReference().get().mutexStorage.get(mutexAddress);
            int attrValue = 0;

            if (!((LLVMPointer) attr).isNull()) {
                attrValue = (int) read.executeWithTarget(attr);
            }
            Mutex.Type mutexType = Mutex.Type.DEFAULT_NORMAL;
            if (attrValue == 1) {
                mutexType = Mutex.Type.RECURSIVE;
            } else if (attrValue == 2) {
                mutexType = Mutex.Type.ERRORCHECK;
            }
            if (mutObj == null) {
                LLVMLanguage.getLLVMContextReference().get().mutexStorage.put(mutexAddress, new Mutex(mutexType));
            }
            return 0;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMPThreadMutexLock extends LLVMBuiltin {
        @Specialization
        protected int doIntrinsic(VirtualFrame frame, Object mutex) {
            long mutexAddress = ((LLVMNativePointer) mutex).asNative();
            Mutex mutexObj = (Mutex) LLVMLanguage.getLLVMContextReference().get().mutexStorage.get(mutexAddress);
            if (mutexObj == null) {
                // mutex is not initialized
                // but it works anyway on most implementations
                // so we will make it work here too, just using default type
                // set the internLock counter to 1
                mutexObj = new Mutex(Mutex.Type.DEFAULT_NORMAL);
                LLVMLanguage.getLLVMContextReference().get().mutexStorage.put(mutexAddress, mutexObj);
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
            Mutex mutexObj = (Mutex) LLVMLanguage.getLLVMContextReference().get().mutexStorage.get(mutexAddress);
            if (mutexObj == null) {
                // mutex is not initialized
                // but it works anyway on most implementations
                // so we will make it work here too, just using default type
                // set the internLock counter to 1
                mutexObj = new Mutex(Mutex.Type.DEFAULT_NORMAL);
                LLVMLanguage.getLLVMContextReference().get().mutexStorage.put(mutexAddress, mutexObj);
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
            Mutex mutexObj = (Mutex) LLVMLanguage.getLLVMContextReference().get().mutexStorage.get(mutexAddress);
            // TODO: error code stuff
            if (mutexObj == null) {
                return 5;
            }
            // TODO: unlock currently not locked mutex: ok (0)
            // TODO: unlock currently by other thread locked mutex: error
            return mutexObj.unlock() ? 0 : 15; // internLock will be changed to int type
        }
    }
}
