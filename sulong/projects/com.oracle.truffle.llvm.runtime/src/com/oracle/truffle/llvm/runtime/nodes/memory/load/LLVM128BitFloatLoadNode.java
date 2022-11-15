package com.oracle.truffle.llvm.runtime.nodes.memory.load;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateAOT;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.llvm.runtime.floating.LLVM128BitFloat;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMManagedReadLibrary;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMLoadNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;

public abstract class LLVM128BitFloatLoadNode extends LLVMLoadNode {

    protected final boolean isRecursive;

    protected LLVM128BitFloatLoadNode() {
        this(false);
    }

    protected LLVM128BitFloatLoadNode(boolean isRecursive) {
        this.isRecursive = isRecursive;
    }

    static LLVM128BitFloatLoadNode create() {
        return LLVM128BitFloatLoadNodeGen.create((LLVMExpressionNode) null);
    }

    static LLVM128BitFloatLoadNode createRecursive() {
        return LLVM128BitFloatLoadNodeGen.create(true, (LLVMExpressionNode) null);
    }

    public abstract LLVM128BitFloat executeWithTarget(LLVMManagedPointer addr);

    @Specialization(guards = "!isAutoDerefHandle(addr)")
    protected LLVM128BitFloat do128BitFloatNative(LLVMNativePointer addr) {
        return getLanguage().getLLVMMemory().get128BitFloat(this, addr);
    }

    @Specialization(guards = {"!isRecursive", "isAutoDerefHandle(addr)"})
    protected LLVM128BitFloat do128BitFloatDerefHandle(LLVMNativePointer addr,
                                                     @Cached LLVMDerefHandleGetReceiverNode getReceiver,
                                                     @Cached("createRecursive()") LLVM128BitFloatLoadNode load) {
        return load.executeWithTarget(getReceiver.execute(addr));
    }

    @Specialization(limit = "3")
    @ExplodeLoop
    @GenerateAOT.Exclude
    protected LLVM128BitFloat doForeign(LLVMManagedPointer addr,
                                       @CachedLibrary("addr.getObject()") LLVMManagedReadLibrary nativeRead) throws UnexpectedResultException {
        long currentAddressPtr = addr.getOffset();
        long fraction = nativeRead.readI64(addr.getObject(), currentAddressPtr);
        currentAddressPtr += Double.BYTES;
        long expSignFraction = nativeRead.readI64(addr.getObject(), currentAddressPtr);
        return new LLVM128BitFloat(expSignFraction, fraction);
    }
}
