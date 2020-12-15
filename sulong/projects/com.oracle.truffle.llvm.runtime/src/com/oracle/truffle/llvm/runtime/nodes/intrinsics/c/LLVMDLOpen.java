package com.oracle.truffle.llvm.runtime.nodes.intrinsics.c;

import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.CachedLanguage;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.LLVMIntrinsic;

public abstract class LLVMDLOpen extends LLVMIntrinsic {

    @Specialization
    protected Object doOp(@CachedContext(LLVMLanguage.class) LLVMContext ctx, @CachedLanguage LLVMLanguage language) {
        ctx.getEnv().parsePublic()
                // path lookup will be tricky part -- then we go through the truffle cache to parse
        // get back the call target
        // call that call target will call the loadmodulesnode.
        // sulong library -- return it, parse it to dlsym -- at least a specificaisation, and look up symbols from there.

        // not suppose all those symbols. should interop see all those symbols.
    }
}
