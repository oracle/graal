package com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.va;

import com.oracle.truffle.api.TruffleLanguage.LanguageReference;
import com.oracle.truffle.api.dsl.CachedLanguage;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.PlatformCapability;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.types.ArrayType;
import com.oracle.truffle.llvm.runtime.types.StructureType;
import com.oracle.truffle.llvm.runtime.types.Type;

public abstract class LLVMVAListNode extends LLVMExpressionNode {

    @Specialization
    public LLVMManagedPointer createVAList(@CachedLanguage LanguageReference<LLVMLanguage> langRef) {
        Object vaListStorage = langRef.get().getCapability(PlatformCapability.class).createVAListStorage();
        return LLVMManagedPointer.create(vaListStorage);
    }

    public static boolean isVAListType(Type type) {
        return type instanceof ArrayType &&
                        ((ArrayType) type).getElementType() instanceof StructureType &&
                        "struct.__va_list_tag".equals(((StructureType) ((ArrayType) type).getElementType()).getName());
    }

}
