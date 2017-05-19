/*
 * Copyright (c) 2017, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.nodes.func;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMFunction;
import com.oracle.truffle.llvm.runtime.LLVMFunctionHandle;
import com.oracle.truffle.llvm.runtime.memory.LLVMThreadingStack;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.types.FunctionType;

@SuppressWarnings("unused")
public abstract class LLVMNativeDispatchNode extends LLVMNode {

    private final FunctionType type;
    private final String signature;
    @Child private Node identityExecuteNode = Message.createExecute(1).createNode();
    @Child private Node nativeCallNode;

    protected LLVMNativeDispatchNode(FunctionType type) {
        this.type = type;
        this.signature = LLVMContext.getNativeSignature(type, LLVMCallNode.USER_ARGUMENT_OFFSET);
        this.nativeCallNode = Message.createExecute(type.getArgumentTypes().length).createNode();
    }

    public abstract Object executeDispatch(VirtualFrame frame, LLVMFunctionHandle function, Object[] arguments);

    @TruffleBoundary
    protected TruffleObject identityFunction() {
        return getContext().getNativeLookup().getNativeFunction("@identity", String.format("(POINTER):%s", signature));
    }

    protected TruffleObject dispatchIdentity(TruffleObject identity, long pointer) {
        assert LLVMFunction.isExternNativeFunctionPointer(pointer);
        try {
            return (TruffleObject) ForeignAccess.sendExecute(identityExecuteNode, identity,
                            pointer);
        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException(e);
        }
    }

    @CompilationFinal private LLVMThreadingStack threadingStack = null;

    private LLVMThreadingStack getThreadingStack(LLVMContext context) {
        if (threadingStack == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            threadingStack = context.getThreadingStack();
        }
        return threadingStack;
    }

    @ExplodeLoop
    protected LLVMNativeConvertNode[] createToNativeNodes() {
        LLVMNativeConvertNode[] ret = new LLVMNativeConvertNode[type.getArgumentTypes().length - LLVMCallNode.USER_ARGUMENT_OFFSET];
        for (int i = LLVMCallNode.USER_ARGUMENT_OFFSET; i < type.getArgumentTypes().length; i++) {
            ret[i - LLVMCallNode.USER_ARGUMENT_OFFSET] = LLVMNativeConvertNode.createToNative(type.getArgumentTypes()[i]);
        }
        return ret;
    }

    protected LLVMNativeConvertNode createFromNativeNode() {
        CompilerAsserts.neverPartOfCompilation();
        return LLVMNativeConvertNode.createFromNative(type.getReturnType());
    }

    @ExplodeLoop
    private static Object[] prepareNativeArguments(VirtualFrame frame, Object[] arguments, LLVMNativeConvertNode[] toNative) {
        Object[] nativeArgs = new Object[arguments.length - LLVMCallNode.USER_ARGUMENT_OFFSET];
        for (int i = LLVMCallNode.USER_ARGUMENT_OFFSET; i < arguments.length; i++) {
            nativeArgs[i - LLVMCallNode.USER_ARGUMENT_OFFSET] = toNative[i - LLVMCallNode.USER_ARGUMENT_OFFSET].executeConvert(frame, arguments[i]);
        }
        return nativeArgs;
    }

    @Specialization(guards = "function.getFunctionPointer() == cachedFunction.getFunctionPointer()")
    public Object doCached(VirtualFrame frame, LLVMFunctionHandle function, Object[] arguments,
                    @Cached("getContext()") LLVMContext context,
                    @Cached("function") LLVMFunctionHandle cachedFunction,
                    @Cached("identityFunction()") TruffleObject identity,
                    @Cached("dispatchIdentity(identity, cachedFunction.getFunctionPointer())") TruffleObject nativeFunctionHandle,
                    @Cached("createToNativeNodes()") LLVMNativeConvertNode[] toNative,
                    @Cached("createFromNativeNode()") LLVMNativeConvertNode fromNative) {
        Object[] nativeArgs = prepareNativeArguments(frame, arguments, toNative);
        getThreadingStack(context).getStack().setStackPointer((long) arguments[0]);
        Object returnValue = LLVMNativeCallUtils.callNativeFunction(context, nativeCallNode, nativeFunctionHandle, nativeArgs, null);
        getThreadingStack(context).getStack().setStackPointer((long) arguments[0]);
        return fromNative.executeConvert(frame, returnValue);
    }

    @Specialization
    public Object doGeneric(VirtualFrame frame, LLVMFunctionHandle function, Object[] arguments,
                    @Cached("getContext()") LLVMContext context,
                    @Cached("identityFunction()") TruffleObject identity,
                    @Cached("createToNativeNodes()") LLVMNativeConvertNode[] toNative,
                    @Cached("createFromNativeNode()") LLVMNativeConvertNode fromNative) {
        Object[] nativeArgs = prepareNativeArguments(frame, arguments, toNative);
        getThreadingStack(context).getStack().setStackPointer((long) arguments[0]);
        Object returnValue = LLVMNativeCallUtils.callNativeFunction(context, nativeCallNode, dispatchIdentity(identity, function.getFunctionPointer()), nativeArgs, null);
        getThreadingStack(context).getStack().setStackPointer((long) arguments[0]);
        return fromNative.executeConvert(frame, returnValue);
    }
}
