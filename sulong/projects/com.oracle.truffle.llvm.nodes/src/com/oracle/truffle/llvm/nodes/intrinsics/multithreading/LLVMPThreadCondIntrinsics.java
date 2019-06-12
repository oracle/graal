package com.oracle.truffle.llvm.nodes.intrinsics.multithreading;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMBuiltin;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMLoadNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;

public class LLVMPThreadCondIntrinsics {
    public static class Cond {
        public enum Type {
            DEFAULT
        }

        private Condition condition;
        private LLVMPThreadMutexIntrinsics.Mutex curMutex;
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

        public boolean cTimedwait(LLVMPThreadMutexIntrinsics.Mutex mutex, long seconds) {
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

        public boolean cWait(LLVMPThreadMutexIntrinsics.Mutex mutex) {
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
            LLVMLanguage.getLLVMContextReference().get().condStorage.remove(condAddress);
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
            Object condObj = LLVMLanguage.getLLVMContextReference().get().condStorage.get(condAddress);
            if (condObj == null) {
                LLVMLanguage.getLLVMContextReference().get().condStorage.put(condAddress, new Cond());
            }
            return 0;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMPThreadCondBroadcast extends LLVMBuiltin {
        @Specialization
        protected int doIntrinsic(VirtualFrame frame, Object cond) {
            long condAddress = ((LLVMNativePointer) cond).asNative();
            Cond condObj = (Cond) LLVMLanguage.getLLVMContextReference().get().condStorage.get(condAddress);
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
            Cond condObj = (Cond) LLVMLanguage.getLLVMContextReference().get().condStorage.get(condAddress);
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
        @Child
        LLVMLoadNode read;

        @Specialization
        protected int doIntrinsic(VirtualFrame frame, Object cond, Object mutex, Object abstime) {
            // TODO: functions __sulong_getNanoSeconds and __sulong_getSeconds return the members
            // make use of them somehow
            int i = 5;
            RootCallTarget getNanos = LLVMLanguage.getLLVMContextReference().get().getGlobalScope().getFunction("@__sulong_getNanoSeconds").getLLVMIRFunction();
            LLVMStack.StackPointer sp1 = LLVMLanguage.getLLVMContextReference().get().getThreadingStack().getStack().newFrame();
            Object nanos = getNanos.call(sp1, abstime);
            RootCallTarget getSec = LLVMLanguage.getLLVMContextReference().get().getGlobalScope().getFunction("@__sulong_getSeconds").getLLVMIRFunction();
            LLVMStack.StackPointer sp2 = LLVMLanguage.getLLVMContextReference().get().getThreadingStack().getStack().newFrame();
            Object seconds = getSec.call(sp2, abstime);
            if (read == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                read = LLVMLanguage.getLLVMContextReference().get().getNodeFactory().createLoadNode(LLVMInteropType.ValueKind.I64);
            }

            long condAddress = ((LLVMNativePointer) cond).asNative();
            long mutexAddress = ((LLVMNativePointer) mutex).asNative();

            Cond condObj = (Cond) LLVMLanguage.getLLVMContextReference().get().condStorage.get(condAddress);
            LLVMPThreadMutexIntrinsics.Mutex mutexObj = (LLVMPThreadMutexIntrinsics.Mutex) LLVMLanguage.getLLVMContextReference().get().mutexStorage.get(mutexAddress);
            if (condObj == null) {
                // init and then wait
                condObj = new Cond();
                LLVMLanguage.getLLVMContextReference().get().condStorage.put(condAddress, condObj);
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

            Cond condObj = (Cond) LLVMLanguage.getLLVMContextReference().get().condStorage.get(condAddress);
            LLVMPThreadMutexIntrinsics.Mutex mutexObj = (LLVMPThreadMutexIntrinsics.Mutex) LLVMLanguage.getLLVMContextReference().get().mutexStorage.get(mutexAddress);
            if (condObj == null) {
                // init and then wait
                condObj = new Cond();
                LLVMLanguage.getLLVMContextReference().get().condStorage.put(condAddress, condObj);
            }
            condObj.cWait(mutexObj);
            return 0;
        }
    }
}
