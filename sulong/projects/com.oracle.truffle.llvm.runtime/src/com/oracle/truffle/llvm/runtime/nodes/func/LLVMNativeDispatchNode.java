/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.runtime.nodes.func;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.NFIContextExtension;
import com.oracle.truffle.llvm.runtime.NFIContextExtension.UnsupportedNativeTypeException;
import com.oracle.truffle.llvm.runtime.interop.nfi.LLVMNativeConvertNode;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack.StackPointer;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.types.FunctionType;

public abstract class LLVMNativeDispatchNode extends LLVMNode {

    private final FunctionType type;

    @CompilationFinal private Object identity;
    @Child InteropLibrary identityExecute;

    protected LLVMNativeDispatchNode(FunctionType type) {
        this.type = type;
    }

    public abstract Object executeDispatch(Object function, Object[] arguments);

    @TruffleBoundary
    protected TruffleObject identityFunction() {
        LLVMContext context = lookupContextReference(LLVMLanguage.class).get();
        NFIContextExtension nfiContextExtension = context.getContextExtension(NFIContextExtension.class);
        String signature;
        try {
            signature = nfiContextExtension.getNativeSignature(type, LLVMCallNode.USER_ARGUMENT_OFFSET);
        } catch (UnsupportedNativeTypeException e) {
            throw new IllegalStateException(e);
        }
        return nfiContextExtension.getNativeFunction(context, "identity", String.format("(POINTER):%s", signature));
    }

    protected Object dispatchIdentity(long pointer) {
        try {
            if (identity == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                if (identity == null) {
                    identity = identityFunction();
                    identityExecute = insert(InteropLibrary.getFactory().create(identity));
                }
            }
            return identityExecute.execute(identity, pointer);
        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException(e);
        }
    }

    @ExplodeLoop
    protected LLVMNativeConvertNode[] createToNativeNodes() {
        LLVMNativeConvertNode[] ret = new LLVMNativeConvertNode[type.getNumberOfArguments() - LLVMCallNode.USER_ARGUMENT_OFFSET];
        for (int i = LLVMCallNode.USER_ARGUMENT_OFFSET; i < type.getNumberOfArguments(); i++) {
            ret[i - LLVMCallNode.USER_ARGUMENT_OFFSET] = LLVMNativeConvertNode.createToNative(type.getArgumentType(i));
        }
        return ret;
    }

    protected LLVMNativeConvertNode createFromNativeNode() {
        CompilerAsserts.neverPartOfCompilation();
        return LLVMNativeConvertNode.createFromNative(type.getReturnType());
    }

    @ExplodeLoop
    private static Object[] prepareNativeArguments(Object[] arguments, LLVMNativeConvertNode[] toNative) {
        Object[] nativeArgs = new Object[arguments.length - LLVMCallNode.USER_ARGUMENT_OFFSET];
        for (int i = LLVMCallNode.USER_ARGUMENT_OFFSET; i < arguments.length; i++) {
            nativeArgs[i - LLVMCallNode.USER_ARGUMENT_OFFSET] = toNative[i - LLVMCallNode.USER_ARGUMENT_OFFSET].executeConvert(arguments[i]);
        }
        return nativeArgs;
    }

    @Specialization(guards = "function.asNative() == cachedFunction.asNative()")
    @SuppressWarnings("unused")
    protected Object doCached(LLVMNativePointer function, Object[] arguments,
                    @CachedContext(LLVMLanguage.class) ContextReference<LLVMContext> context,
                    @Cached("function") LLVMNativePointer cachedFunction,
                    @Cached("dispatchIdentity(cachedFunction.asNative())") Object nativeFunctionHandle,
                    @CachedLibrary("nativeFunctionHandle") InteropLibrary nativeCall,
                    @Cached("createToNativeNodes()") LLVMNativeConvertNode[] toNative,
                    @Cached("createFromNativeNode()") LLVMNativeConvertNode fromNative,
                    @Cached("nativeCallStatisticsEnabled(context)") boolean statistics) {
        Object[] nativeArgs = prepareNativeArguments(arguments, toNative);
        Object returnValue;
        try (StackPointer save = ((StackPointer) arguments[0]).newFrame()) {
            returnValue = LLVMNativeCallUtils.callNativeFunction(statistics, context, nativeCall, nativeFunctionHandle, nativeArgs, null);
        }
        return fromNative.executeConvert(returnValue);
    }

    @Specialization
    protected Object doGeneric(LLVMNativePointer function, Object[] arguments,
                    @CachedContext(LLVMLanguage.class) ContextReference<LLVMContext> context,
                    @Cached("createToNativeNodes()") LLVMNativeConvertNode[] toNative,
                    @Cached("createFromNativeNode()") LLVMNativeConvertNode fromNative,
                    @CachedLibrary(limit = "5") InteropLibrary nativeCall,
                    @Cached("nativeCallStatisticsEnabled(context)") boolean statistics) {
        Object[] nativeArgs = prepareNativeArguments(arguments, toNative);
        Object returnValue;
        try (StackPointer save = ((StackPointer) arguments[0]).newFrame()) {
            returnValue = LLVMNativeCallUtils.callNativeFunction(statistics, context, nativeCall, dispatchIdentity(function.asNative()), nativeArgs, null);
        }
        return fromNative.executeConvert(returnValue);
    }
}
