package com.oracle.truffle.llvm.initialization;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.llvm.parser.LLVMParserResult;
import com.oracle.truffle.llvm.runtime.LLVMAlias;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMFunctionCode;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.LLVMIntrinsicProvider;
import com.oracle.truffle.llvm.runtime.LLVMScopeChain;
import com.oracle.truffle.llvm.runtime.LLVMSymbol;
import com.oracle.truffle.llvm.runtime.NativeContextExtension;
import com.oracle.truffle.llvm.runtime.NativeContextExtension.NativeLookupResult;
import com.oracle.truffle.llvm.runtime.NativeContextExtension.NativePointerIntoLibrary;
import com.oracle.truffle.llvm.runtime.NodeFactory;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.c.LLVMDLOpen;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

public class AllocAnExternalSymbolNode extends LLVMNode {

    private final NodeFactory nodeFactory;

    public AllocAnExternalSymbolNode(LLVMParserResult result) {
        this.nodeFactory = result.getRuntime().getNodeFactory();
    }

    public LLVMPointer execute(LLVMScopeChain localScope, LLVMScopeChain globalScope, LLVMIntrinsicProvider intrinsicProvider, NativeContextExtension nativeContextExtension,
                                        LLVMContext context, LLVMDLOpen.RTLDFlags rtldFlags, LLVMSymbol symbol) {

        LLVMPointer pointerFromLocal = lookupFromLocalScope(localScope, symbol, context, BranchProfile.create());
        if (pointerFromLocal != null && isDefaultFlagActive(rtldFlags)) {
            return pointerFromLocal;
        }

        LLVMPointer pointerFromGlobal = lookupFromLocalScope(globalScope, symbol, context, BranchProfile.create());
        if (pointerFromGlobal != null && !(isDefaultFlagActive(rtldFlags))) {
            return pointerFromGlobal;
        }

        if (symbol.isGlobalVariable()) {
            if (symbol.isExternalWeak()) {
                return LLVMNativePointer.createNull();
            } else if (!intrinsicProvider.isIntrinsified(symbol.getName()) && nativeContextExtension != null) {
                NativeContextExtension.NativePointerIntoLibrary pointer = getNativePointer(nativeContextExtension, symbol);
                if (pointer != null) {
                    return LLVMNativePointer.create(pointer.getAddress());
                }
                return null;
            }
        }

        if (symbol.isFunction()) {
             if (symbol.isExternalWeak()) {
                return LLVMNativePointer.createNull();
            } else if (intrinsicProvider != null && intrinsicProvider.isIntrinsified(symbol.getName())) {
                LLVMFunctionCode functionCode = new LLVMFunctionCode(symbol.asFunction());
                LLVMFunctionDescriptor functionDescriptor = context.createFunctionDescriptor(symbol.asFunction(), functionCode);
                functionDescriptor.getFunctionCode().define(intrinsicProvider, nodeFactory);
                return LLVMManagedPointer.create(functionDescriptor);
            } else if (intrinsicProvider != null && !intrinsicProvider.isIntrinsified(symbol.getName()) && nativeContextExtension != null) {
                 NativeLookupResult nativeFunction = getNativeFunction(nativeContextExtension, symbol);
                if (nativeFunction != null) {
                    LLVMFunctionDescriptor functionDescriptor = context.createFunctionDescriptor(symbol.asFunction(), new LLVMFunctionCode(symbol.asFunction()));
                    functionDescriptor.getFunctionCode().define(new LLVMFunctionCode.NativeFunction(nativeFunction.getObject()));
                    symbol.asFunction().setNFISymbol(nativeFunction.getObject());
                    return LLVMManagedPointer.create(functionDescriptor);
                }
                return null;
            }
        }
        return null;
    }

    @TruffleBoundary
    private static NativeLookupResult getNativeFunction(NativeContextExtension nativeContextExtension, LLVMSymbol symbol) {
        return nativeContextExtension.getNativeFunctionOrNull(symbol.getName());

    }

    @TruffleBoundary
    private static NativePointerIntoLibrary getNativePointer(NativeContextExtension nativeContextExtension, LLVMSymbol symbol) {
        return nativeContextExtension.getNativeHandle(symbol.getName());
    }

    private static LLVMPointer lookupFromLocalScope(LLVMScopeChain scope, LLVMSymbol symbol, LLVMContext context, BranchProfile exception) {
        LLVMSymbol resultSymbol = scope.get(symbol.getName());
        if (resultSymbol == null) {
            return null;
        }
        LLVMSymbol function = LLVMAlias.resolveAlias(resultSymbol);
        LLVMPointer pointer = context.getSymbol(function, exception);
        context.registerSymbol(symbol, pointer);
        return pointer;
    }

    private static boolean isDefaultFlagActive(LLVMDLOpen.RTLDFlags rtldFlags) {
        return LLVMDLOpen.RTLDFlags.RTLD_OPEN_DEFAULT.isActive(rtldFlags);
    }
}
