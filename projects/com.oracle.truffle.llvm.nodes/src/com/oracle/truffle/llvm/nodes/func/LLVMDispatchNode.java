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
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor.Intrinsic;
import com.oracle.truffle.llvm.runtime.memory.LLVMThreadingStack;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.types.FunctionType;

@SuppressWarnings("unused")
public abstract class LLVMDispatchNode extends LLVMNode {

    protected static final int INLINE_CACHE_SIZE = 5;

    private final FunctionType type;
    @CompilationFinal private String signature;

    protected LLVMDispatchNode(FunctionType type) {
        this.type = type;
    }

    private String getSignature() {
        if (signature == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            this.signature = LLVMContext.getNativeSignature(type, LLVMCallNode.USER_ARGUMENT_OFFSET);
        }
        return signature;
    }

    public abstract Object executeDispatch(VirtualFrame frame, LLVMFunctionDescriptor function, Object[] arguments);

    /*
     * Function is defined in the user program (available as LLVM IR)
     */

    @Specialization(limit = "INLINE_CACHE_SIZE", guards = {"function.getFunctionPointer() == cachedFunction.getFunctionPointer()", "cachedFunction.isLLVMIRFunction()"})
    protected static Object doDirect(LLVMFunctionDescriptor function, Object[] arguments,
                    @Cached("function") LLVMFunctionDescriptor cachedFunction,
                    @Cached("create(cachedFunction.getLLVMIRFunction())") DirectCallNode callNode) {
        return callNode.call(arguments);
    }

    @Specialization(replaces = "doDirect", guards = "descriptor.isLLVMIRFunction()")
    protected static Object doIndirect(LLVMFunctionDescriptor descriptor, Object[] arguments,
                    @Cached("create()") IndirectCallNode callNode) {
        return callNode.call(descriptor.getLLVMIRFunction(), arguments);
    }

    /*
     * Function is not defined in the user program (not available as LLVM IR). This would normally
     * result in a native call BUT there is an intrinsification available
     */

    protected DirectCallNode getIntrinsificationCallNode(Intrinsic intrinsic) {
        RootCallTarget target = intrinsic.forceSplit() ? intrinsic.generateCallTarget(type) : intrinsic.cachedCallTarget(type);
        DirectCallNode directCallNode = DirectCallNode.create(target);
        if (intrinsic.forceInline()) {
            directCallNode.forceInlining();
        }
        return directCallNode;
    }

    @Specialization(limit = "INLINE_CACHE_SIZE", guards = {"function.getFunctionPointer() == cachedFunction.getFunctionPointer()", "cachedFunction.isNativeIntrinsicFunction()"})
    protected Object doDirectIntrinsic(LLVMFunctionDescriptor function, Object[] arguments,
                    @Cached("function") LLVMFunctionDescriptor cachedFunction,
                    @Cached("getIntrinsificationCallNode(cachedFunction.getNativeIntrinsic())") DirectCallNode callNode) {
        return callNode.call(arguments);
    }

    @Specialization(replaces = "doDirectIntrinsic", guards = "descriptor.isNativeIntrinsicFunction()")
    protected Object doIndirectIntrinsic(LLVMFunctionDescriptor descriptor, Object[] arguments,
                    @Cached("create()") IndirectCallNode callNode) {
        return callNode.call(descriptor.getNativeIntrinsic().cachedCallTarget(type), arguments);
    }

    /*
     * Function is not defined in the user program (not available as LLVM IR). No intrinsic
     * available. We do a native call.
     */

    @CompilationFinal private LLVMThreadingStack threadingStack = null;

    private LLVMThreadingStack getThreadingStack(LLVMContext context) {
        if (threadingStack == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            threadingStack = context.getThreadingStack();
        }
        return threadingStack;
    }

    @Specialization(limit = "10", guards = {"descriptor.getFunctionPointer() == cachedDescriptor.getFunctionPointer()", "descriptor.isNativeFunction()"})
    protected Object doCachedNative(VirtualFrame frame, LLVMFunctionDescriptor descriptor, Object[] arguments,
                    @Cached("descriptor") LLVMFunctionDescriptor cachedDescriptor,
                    @Cached("createToNativeNodes()") LLVMNativeConvertNode[] toNative,
                    @Cached("createFromNativeNode()") LLVMNativeConvertNode fromNative,
                    @Cached("createNativeCallNode()") Node nativeCall,
                    @Cached("bindSymbol(frame, cachedDescriptor)") TruffleObject cachedBoundFunction,
                    @Cached("getContext()") LLVMContext context) {

        Object[] nativeArgs = prepareNativeArguments(frame, arguments, toNative);
        getThreadingStack(context).getStack().setStackPointer((long) arguments[0]);
        Object returnValue = LLVMNativeCallUtils.callNativeFunction(context, nativeCall, cachedBoundFunction, nativeArgs, cachedDescriptor);
        getThreadingStack(context).getStack().setStackPointer((long) arguments[0]);
        return fromNative.executeConvert(frame, returnValue);
    }

    protected TruffleObject bindSymbol(VirtualFrame frame, LLVMFunctionDescriptor descriptor) {
        CompilerAsserts.neverPartOfCompilation();
        assert descriptor.getNativeFunction() != null;
        return LLVMNativeCallUtils.bindNativeSymbol(LLVMNativeCallUtils.getBindNode(), descriptor.getNativeFunction(), getSignature());
    }

    @Specialization(replaces = "doCachedNative", guards = "descriptor.isNativeFunction()")
    protected Object doNative(VirtualFrame frame, LLVMFunctionDescriptor descriptor, Object[] arguments,
                    @Cached("createToNativeNodes()") LLVMNativeConvertNode[] toNative,
                    @Cached("createFromNativeNode()") LLVMNativeConvertNode fromNative,
                    @Cached("createNativeCallNode()") Node nativeCall,
                    @Cached("getBindNode()") Node bindNode,
                    @Cached("getContext()") LLVMContext context) {

        Object[] nativeArgs = prepareNativeArguments(frame, arguments, toNative);
        TruffleObject boundSymbol = LLVMNativeCallUtils.bindNativeSymbol(bindNode, descriptor.getNativeFunction(), getSignature());
        getThreadingStack(context).getStack().setStackPointer((long) arguments[0]);
        Object returnValue = LLVMNativeCallUtils.callNativeFunction(getContext(), nativeCall, boundSymbol, nativeArgs, descriptor);
        getThreadingStack(context).getStack().setStackPointer((long) arguments[0]);
        return fromNative.executeConvert(frame, returnValue);
    }

    @ExplodeLoop
    private static Object[] prepareNativeArguments(VirtualFrame frame, Object[] arguments, LLVMNativeConvertNode[] toNative) {
        Object[] nativeArgs = new Object[arguments.length - LLVMCallNode.USER_ARGUMENT_OFFSET];
        for (int i = LLVMCallNode.USER_ARGUMENT_OFFSET; i < arguments.length; i++) {
            nativeArgs[i - LLVMCallNode.USER_ARGUMENT_OFFSET] = toNative[i - LLVMCallNode.USER_ARGUMENT_OFFSET].executeConvert(frame, arguments[i]);
        }
        return nativeArgs;
    }

    protected Node getBindNode() {
        CompilerAsserts.neverPartOfCompilation();
        return LLVMNativeCallUtils.getBindNode();
    }

    protected Node createNativeCallNode() {
        CompilerAsserts.neverPartOfCompilation();
        int argCount = type.getArgumentTypes().length - LLVMCallNode.USER_ARGUMENT_OFFSET;
        return Message.createExecute(argCount).createNode();
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
}
