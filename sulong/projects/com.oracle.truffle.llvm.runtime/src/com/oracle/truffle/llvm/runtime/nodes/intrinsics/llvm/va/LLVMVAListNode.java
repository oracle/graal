package com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.va;

import com.oracle.truffle.api.TruffleLanguage.LanguageReference;
import com.oracle.truffle.api.dsl.CachedLanguage;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.PlatformCapability;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;

public abstract class LLVMVAListNode extends LLVMExpressionNode {

    protected LLVMVAListNode() {
    }

    @Specialization
    public LLVMManagedPointer createVAList(@CachedLanguage LanguageReference<LLVMLanguage> langRef) {
        @SuppressWarnings("unchecked")
        Object vaListStorage = langRef.get().getCapability(PlatformCapability.class).createVAListStorage();
        return LLVMManagedPointer.create(vaListStorage);
    }

}
