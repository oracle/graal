/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.intrinsics.interop;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.TruffleLanguage.LanguageReference;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.ExternalLibrary;
import com.oracle.truffle.llvm.runtime.LLVMFunction;
import com.oracle.truffle.llvm.runtime.LLVMFunctionCode.LLVMIRFunction;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.LLVMSymbol;
import com.oracle.truffle.llvm.runtime.except.LLVMPolyglotException;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMAsForeignLibrary;
import com.oracle.truffle.llvm.runtime.memory.LLVMNativeMemory;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack.StackPointer;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.func.LLVMDispatchNode;
import com.oracle.truffle.llvm.runtime.nodes.func.LLVMDispatchNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.LLVMIntrinsic;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMDerefHandleGetReceiverNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.types.FunctionType;

@NodeChildren({@NodeChild(type = LLVMExpressionNode.class), @NodeChild(type = LLVMExpressionNode.class)})
public abstract class LLVMTruffleDecorateFunction extends LLVMIntrinsic {

    @CompilationFinal private ContextReference<LLVMContext> contextRef;

    @CompilationFinal private LanguageReference<LLVMLanguage> languageRef;

    protected boolean isAutoDerefHandle(LLVMNativePointer addr) {
        if (languageRef == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            languageRef = lookupLanguageReference(LLVMLanguage.class);
        }
        // checking the bit is cheaper than getting the assumption in interpreted mode
        if (CompilerDirectives.inCompiledCode() && languageRef.get().getNoDerefHandleAssumption().isValid()) {
            return false;
        }
        return LLVMNativeMemory.isDerefHandleMemory(addr.asNative());
    }

    protected abstract static class DecoratedRoot extends RootNode {

        protected DecoratedRoot(TruffleLanguage<?> language) {
            super(language);
        }
    }

    protected static class NativeDecoratedRoot extends DecoratedRoot {
        @Child private DirectCallNode funcCallNode;
        @Child private DirectCallNode wrapperCallNode;

        protected NativeDecoratedRoot(TruffleLanguage<?> language, LLVMFunctionDescriptor descriptor, LLVMFunctionDescriptor wrapper) {
            super(language);
            this.funcCallNode = Truffle.getRuntime().createDirectCallNode(descriptor.getFunctionCode().getLLVMIRFunctionSlowPath());
            this.wrapperCallNode = Truffle.getRuntime().createDirectCallNode(wrapper.getFunctionCode().getLLVMIRFunctionSlowPath());
            this.funcCallNode.cloneCallTarget();
            this.wrapperCallNode.cloneCallTarget();
            this.funcCallNode.forceInlining();
            this.wrapperCallNode.forceInlining();
        }

        @Override
        public Object execute(VirtualFrame frame) {
            Object[] arguments = frame.getArguments();
            Object result;
            try (StackPointer sp = ((StackPointer) arguments[0]).newFrame()) {
                result = funcCallNode.call(arguments);
            }
            Object[] wrapperArgs = new Object[]{arguments[0], result};
            try (StackPointer sp = ((StackPointer) arguments[0]).newFrame()) {
                return wrapperCallNode.call(wrapperArgs);
            }
        }

    }

    protected static class ForeignDecoratedRoot extends DecoratedRoot {
        @Child private LLVMDispatchNode funcCallNode;
        @Child private DirectCallNode wrapperCallNode;

        private final Object func;

        protected ForeignDecoratedRoot(TruffleLanguage<?> language, FunctionType type, Object func, LLVMFunctionDescriptor wrapper) {
            super(language);
            this.funcCallNode = LLVMDispatchNodeGen.create(type);
            this.func = func;
            this.wrapperCallNode = Truffle.getRuntime().createDirectCallNode(wrapper.getFunctionCode().getLLVMIRFunctionSlowPath());
            this.wrapperCallNode.cloneCallTarget();
        }

        @Override
        public Object execute(VirtualFrame frame) {
            Object[] arguments = frame.getArguments();
            Object result = funcCallNode.executeDispatch(func, arguments);
            Object[] wrapperArgs = new Object[]{arguments[0], result};
            try (StackPointer sp = ((StackPointer) arguments[0]).newFrame()) {
                return wrapperCallNode.call(wrapperArgs);
            }
        }

    }

    @Specialization(guards = "!isAutoDerefHandle(func)")
    protected Object decorate(LLVMNativePointer func, LLVMNativePointer wrapper) {
        return decorate(getContext().getFunctionDescriptor(func), getContext().getFunctionDescriptor(wrapper));
    }

    @Specialization(guards = "isAutoDerefHandle(func)")
    protected Object decorateDerefHandle(LLVMNativePointer func, LLVMNativePointer wrapper,
                    @Cached LLVMDerefHandleGetReceiverNode getReceiver,
                    @Cached ConditionProfile isFunctionDescriptorProfile) {
        LLVMManagedPointer resolved = getReceiver.execute(func);
        if (isFunctionDescriptorProfile.profile(isFunctionDescriptor(resolved.getObject()))) {
            return decorate(resolved, wrapper);
        }
        return doGeneric(func, wrapper);
    }

    @Specialization(guards = {"!isAutoDerefHandle(func)", "isFunctionDescriptor(wrapper.getObject())"})
    protected Object decorate(LLVMNativePointer func, LLVMManagedPointer wrapper) {
        return decorate(getContext().getFunctionDescriptor(func), (LLVMFunctionDescriptor) wrapper.getObject());
    }

    @Specialization(guards = {"isAutoDerefHandle(func)", "isFunctionDescriptor(wrapper.getObject())"})
    protected Object decorateDerefHandle(LLVMNativePointer func, LLVMManagedPointer wrapper,
                    @Cached LLVMDerefHandleGetReceiverNode getReceiver,
                    @Cached ConditionProfile isFunctionDescriptorProfile,
                    @CachedLibrary(limit = "3") LLVMAsForeignLibrary foreigns) {
        LLVMManagedPointer resolved = getReceiver.execute(func);
        if (isFunctionDescriptorProfile.profile(isFunctionDescriptor(resolved.getObject()))) {
            return decorate(resolved, wrapper);
        } else if (foreigns.isForeign(resolved.getObject())) {
            return decorateForeign(resolved, wrapper);
        }
        return doGeneric(func, wrapper);
    }

    private Object decorateForeign(LLVMManagedPointer resolved, LLVMManagedPointer wrapper) {
        Object foreign = resolved.getObject();
        return decorateForeign(foreign, (LLVMFunctionDescriptor) wrapper.getObject());
    }

    @Specialization(guards = "isFunctionDescriptor(func.getObject())")
    protected Object decorate(LLVMManagedPointer func, LLVMNativePointer wrapper) {
        return decorate((LLVMFunctionDescriptor) func.getObject(), getContext().getFunctionDescriptor(wrapper));
    }

    @Specialization(guards = {"isFunctionDescriptor(func.getObject())", "isFunctionDescriptor(wrapper.getObject())"})
    protected Object decorate(LLVMManagedPointer func, LLVMManagedPointer wrapper) {
        return decorate((LLVMFunctionDescriptor) func.getObject(), (LLVMFunctionDescriptor) wrapper.getObject());
    }

    @Fallback
    protected Object doGeneric(@SuppressWarnings("unused") Object func, @SuppressWarnings("unused") Object wrapper) {
        throw new LLVMPolyglotException(this, "invalid arguments for function composition");
    }

    @TruffleBoundary
    private Object decorate(LLVMFunctionDescriptor function, LLVMFunctionDescriptor wrapperFunction) {
        assert function != null && wrapperFunction != null;
        FunctionType type = wrapperFunction.getLLVMFunction().getType();
        FunctionType newFunctionType = FunctionType.copy(type);
        NativeDecoratedRoot decoratedRoot = new NativeDecoratedRoot(lookupLanguageReference(LLVMLanguage.class).get(), function, wrapperFunction);
        return registerRoot(function.getLLVMFunction().getLibrary(), newFunctionType, decoratedRoot);
    }

    @TruffleBoundary
    private Object decorateForeign(Object function, LLVMFunctionDescriptor wrapperFunction) {
        assert function != null && wrapperFunction != null;
        FunctionType newFunctionType = new FunctionType(wrapperFunction.getLLVMFunction().getType().getReturnType(), 0, true);
        DecoratedRoot decoratedRoot = new ForeignDecoratedRoot(lookupLanguageReference(LLVMLanguage.class).get(), newFunctionType, function, wrapperFunction);
        return registerRoot(wrapperFunction.getLLVMFunction().getLibrary(), newFunctionType, decoratedRoot);
    }

    private Object registerRoot(ExternalLibrary lib, FunctionType newFunctionType, DecoratedRoot decoratedRoot) {
        LLVMIRFunction function = new LLVMIRFunction(Truffle.getRuntime().createCallTarget(decoratedRoot), null);
        LLVMFunction functionDetail = LLVMFunction.create("<wrapper>", lib, function, newFunctionType, LLVMSymbol.INVALID_INDEX, LLVMSymbol.INVALID_INDEX, false);
        LLVMFunctionDescriptor wrappedFunction = new LLVMFunctionDescriptor(getContext(), functionDetail);
        return LLVMManagedPointer.create(wrappedFunction);
    }

    private LLVMContext getContext() {
        if (contextRef == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            contextRef = lookupContextReference(LLVMLanguage.class);
        }
        return contextRef.get();
    }

}
