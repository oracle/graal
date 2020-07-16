package com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.va;

import com.oracle.truffle.api.library.GenerateLibrary;
import com.oracle.truffle.api.library.Library;
import com.oracle.truffle.api.library.LibraryFactory;
import com.oracle.truffle.llvm.runtime.types.Type;

@GenerateLibrary
public abstract class LLVMVaListLibrary extends Library {

    static final LibraryFactory<LLVMVaListLibrary> FACTORY = LibraryFactory.resolve(LLVMVaListLibrary.class);

    public static LibraryFactory<LLVMVaListLibrary> getFactory() {
        return FACTORY;
    }

    public abstract void initialize(Object vaList, Object[] arguments, int numberOfExplicitArgumentsd);

    public abstract void cleanup(Object vaList);

    public abstract void copy(Object srcVaList, Object destVaList);

    public abstract Object shift(Object vaList, Type type);
}
