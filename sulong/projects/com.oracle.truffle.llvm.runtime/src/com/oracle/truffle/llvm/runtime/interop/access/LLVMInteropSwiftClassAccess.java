package com.oracle.truffle.llvm.runtime.interop.access;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.SulongLibrary;

@ExportLibrary(value = InteropLibrary.class)
public class LLVMInteropSwiftClassAccess implements TruffleObject {
    public final SulongLibrary sulongLibrary;
    private final LLVMFunctionDescriptor functionDescriptor;
    private final String className;

    public LLVMInteropSwiftClassAccess(SulongLibrary sulongLibrary, LLVMFunctionDescriptor functionDescriptor, String className) {
        this.sulongLibrary = sulongLibrary;
        this.functionDescriptor = functionDescriptor;
        this.className = className;
    }

    @ExportMessage
    public boolean hasMembers() {
        // TODO correct
        return false;
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    final Object getMembers(boolean includeInternal) {
        // TODO correct
        return null;
    }

    @ExportMessage
    public boolean isMemberInvocable(String member,
                    @CachedLibrary(limit = "5") InteropLibrary interop) {
        return interop.isMemberInvocable(sulongLibrary, member);
    }

    @ExportMessage
    public Object invokeMember(String member, Object[] arguments,
                    @Cached LLVMSelfArgumentPackNode argumentPackNode,
                    @CachedLibrary(limit = "5") InteropLibrary interop)
                    throws UnsupportedMessageException, ArityException, UnknownIdentifierException, UnsupportedTypeException {
        final Object[] newargs = argumentPackNode.execute(functionDescriptor, arguments, false);
        String mangledName = LLVMLanguage.getContext().getGlobalScopeChain().getMangledName(className, member);
        return interop.invokeMember(sulongLibrary, mangledName == null ? member : mangledName, newargs);
    }

}
