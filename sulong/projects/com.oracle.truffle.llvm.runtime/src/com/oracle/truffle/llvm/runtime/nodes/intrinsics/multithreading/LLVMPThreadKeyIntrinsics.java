package com.oracle.truffle.llvm.runtime.nodes.intrinsics.multithreading;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMStoreNode;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.LLVMBuiltin;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

import java.util.concurrent.ConcurrentHashMap;

public class LLVMPThreadKeyIntrinsics {
    @NodeChild(type = LLVMExpressionNode.class, value = "key")
    @NodeChild(type = LLVMExpressionNode.class, value = "destructor")
    public abstract static class LLVMPThreadKeyCreate extends LLVMBuiltin {
        @Child
        LLVMStoreNode store = null;

        @Specialization
        protected int doIntrinsic(VirtualFrame frame, LLVMPointer key, LLVMPointer destructor, @CachedContext(LLVMLanguage.class) LLVMContext ctx) {
            if (store == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                store = ctx.getLanguage().getNodeFactory().createStoreNode(LLVMInteropType.ValueKind.I32);
            }
            synchronized (ctx) {
                store.executeWithTarget(key, ctx.curKeyVal + 1);
                // add new key-value to key-storage, which is a hashmap(key-value->hashmap(thread-id->specific-value))
                UtilAccessCollectionWithBoundary.put(ctx.keyStorage, ctx.curKeyVal + 1, new ConcurrentHashMap<>());
                UtilAccessCollectionWithBoundary.put(ctx.destructorStorage, ctx.curKeyVal + 1, destructor);
                ctx.curKeyVal++;
            }
            return 0;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class, value = "key")
    public abstract static class LLVMPThreadKeyDelete extends LLVMBuiltin {
        @Specialization
        protected int doIntrinsic(VirtualFrame frame, int key, @CachedContext(LLVMLanguage.class) LLVMContext ctx) {
            UtilAccessCollectionWithBoundary.remove(ctx.keyStorage, key);
            UtilAccessCollectionWithBoundary.remove(ctx.destructorStorage, key);
            return 0;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class, value = "key")
    public abstract static class LLVMPThreadGetspecific extends LLVMBuiltin {
        // no relevant error code handling here
        @Specialization
        protected LLVMPointer doIntrinsic(VirtualFrame frame, int key, @CachedContext(LLVMLanguage.class) LLVMContext ctx) {
            if (ctx.keyStorage.containsKey(key) && ctx.keyStorage.get(key).containsKey(Thread.currentThread().getId())) {
                return UtilAccessCollectionWithBoundary.get(UtilAccessCollectionWithBoundary.get(ctx.keyStorage, key), Thread.currentThread().getId());
            }
            return LLVMNativePointer.createNull();
        }
    }

    @NodeChild(type = LLVMExpressionNode.class, value = "key")
    @NodeChild(type = LLVMExpressionNode.class, value = "value")
    public abstract static class LLVMPThreadSetspecific extends LLVMBuiltin {
        // [EINVAL] if key is not valid
        @Specialization
        protected int doIntrinsic(VirtualFrame frame, int key, LLVMPointer value, @CachedContext(LLVMLanguage.class) LLVMContext ctx) {
            if (!ctx.keyStorage.containsKey(key)) {
                return ctx.pthreadConstants.getConstant(UtilCConstants.CConstant.EINVAL);
            }
            UtilAccessCollectionWithBoundary.put(UtilAccessCollectionWithBoundary.get(ctx.keyStorage, key), Thread.currentThread().getId(), value);
            return 0;
        }
    }
}
