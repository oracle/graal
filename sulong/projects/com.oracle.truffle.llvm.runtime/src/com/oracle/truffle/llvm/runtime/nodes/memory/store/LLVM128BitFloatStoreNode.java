package com.oracle.truffle.llvm.runtime.nodes.memory.store;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateAOT;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.llvm.runtime.floating.LLVM128BitFloat;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMManagedWriteLibrary;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMStoreNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMDerefHandleGetReceiverNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVM128BitFloatStoreNodeGen.LLVM128BitFloatOffsetStoreNodeGen;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

public abstract class LLVM128BitFloatStoreNode extends LLVMStoreNode {

    protected final boolean isRecursive;

    protected LLVM128BitFloatStoreNode() {
        this(false);
    }

    protected LLVM128BitFloatStoreNode(boolean isRecursive) {
        this.isRecursive = isRecursive;
    }

    public static LLVM128BitFloatStoreNode create() {
        return LLVM128BitFloatStoreNodeGen.create(null, null);
    }

    public static LLVM128BitFloatStoreNode createRecursive() {
        return LLVM128BitFloatStoreNodeGen.create(null, null);
    }

    public abstract void executeWithTarget(LLVMPointer address, LLVM128BitFloat value);

    @GenerateUncached
    public abstract static class LLVM128BitFloatOffsetStoreNode extends LLVMOffsetStoreNode {

        public static LLVM128BitFloatOffsetStoreNode create() {
            return LLVM128BitFloatOffsetStoreNodeGen.create(null, null, null);
        }

        public static LLVM128BitFloatOffsetStoreNode create(LLVMExpressionNode value) {
            return LLVM128BitFloatOffsetStoreNodeGen.create(null, null, value);
        }

        public abstract void executeWithTarget(LLVMPointer receiver, long offset, LLVM128BitFloat value);

        @Specialization(guards = "!isAutoDerefHandle(addr)")
        protected void doOp(LLVMNativePointer addr, long offset, LLVM128BitFloat value) {
            getLanguage().getLLVMMemory().put128BitFloat(this, addr.asNative() + offset, value);
        }

        @Specialization(guards = "isAutoDerefHandle(addr)")
        protected static void doOpDerefHandle(LLVMNativePointer addr, long offset, LLVM128BitFloat value,
                        @Cached LLVMDerefHandleGetReceiverNode getReceiver,
                        @CachedLibrary(limit = "3") LLVMManagedWriteLibrary nativeWrite) {
            doOpManaged(getReceiver.execute(addr), offset, value, nativeWrite);
        }

        @Specialization(limit = "3")
        @GenerateAOT.Exclude
        protected static void doOpManaged(LLVMManagedPointer address, long offset, LLVM128BitFloat value,
                        @CachedLibrary("address.getObject()") LLVMManagedWriteLibrary nativeWrite) {

            long currentptr = address.getOffset() + offset;
            nativeWrite.writeI64(address.getObject(), currentptr, value.getSecondFractionPart());
            currentptr += I64_SIZE_IN_BYTES;
            nativeWrite.writeI64(address.getObject(), currentptr, value.getExpSignFractionPart());
        }
    }

    @Specialization(guards = "!isAutoDerefHandle(addr)")
    protected void doOp(LLVMNativePointer addr, LLVM128BitFloat value) {
        getLanguage().getLLVMMemory().put128BitFloat(this, addr, value);
    }

    @Specialization(guards = {"!isRecursive", "isAutoDerefHandle(addr)"})
    protected static void doOpDerefHandle(LLVMNativePointer addr, LLVM128BitFloat value,
                    @Cached LLVMDerefHandleGetReceiverNode getReceiver,
                    @Cached("createRecursive()") LLVM128BitFloatStoreNode store) {
        store.executeWithTarget(getReceiver.execute(addr), value);
    }

    // TODO (chaeubl): we could store this in a more efficient way (short + long)
    // TODO (fredmorcos) When GR-26485 is fixed, use limit = "3" here.
    @Specialization
    @ExplodeLoop
    @GenerateAOT.Exclude
    protected static void doForeign(LLVMManagedPointer address, LLVM128BitFloat value,
                    // TODO (fredmorcos) When GR-26485 is fixed, use
                    // @CachedLibrary("address.getObject()") here.
                    @CachedLibrary(limit = "3") LLVMManagedWriteLibrary nativeWrite) {
        long currentptr = address.getOffset();
        nativeWrite.writeI64(address.getObject(), currentptr, value.getSecondFractionPart());
        currentptr += I64_SIZE_IN_BYTES;
        nativeWrite.writeI64(address.getObject(), currentptr, value.getExpSignFractionPart());
    }
}
