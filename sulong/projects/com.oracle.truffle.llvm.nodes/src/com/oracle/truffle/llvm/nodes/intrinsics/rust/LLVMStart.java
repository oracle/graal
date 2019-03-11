/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.nodes.intrinsics.rust;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.llvm.nodes.func.LLVMDispatchNode;
import com.oracle.truffle.llvm.nodes.func.LLVMDispatchNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMIntrinsic;
import com.oracle.truffle.llvm.nodes.intrinsics.rust.LLVMStartFactory.LLVMClosureDispatchNodeGen;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.datalayout.DataLayout;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobal;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack.StackPointer;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMToNativeNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;
import com.oracle.truffle.llvm.runtime.types.FunctionType;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.StructureType;
import com.oracle.truffle.llvm.runtime.types.Type;

public abstract class LLVMStart extends LLVMIntrinsic {

    protected LLVMClosureDispatchNode createClosureDispatchNode() {
        CompilerAsserts.neverPartOfCompilation();
        return LLVMClosureDispatchNodeGen.create();
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMLangStart extends LLVMStart {

        @Specialization
        @SuppressWarnings("unused")
        protected long doOp(StackPointer stackPointer, LLVMPointer main, long argc, LLVMPointer argv,
                        @Cached("createClosureDispatchNode()") LLVMClosureDispatchNode dispatchNode) {
            dispatchNode.executeDispatch(main, new Object[]{stackPointer});
            return 0;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMLangStartInternal extends LLVMStart {

        @TruffleBoundary
        protected LangStartVtableType createLangStartVtable(LLVMContext ctx, Type vtableType) {
            DataLayout dataSpecConverter = ctx.getDataSpecConverter();
            return LangStartVtableType.create(dataSpecConverter, vtableType);
        }

        @Specialization
        @SuppressWarnings("unused")
        protected long doOp(StackPointer stackPointer, LLVMNativePointer mainPointer, LLVMNativePointer vtable, long argc, LLVMPointer argv,
                        @CachedContext(LLVMLanguage.class) LLVMContext ctx,
                        @Cached("createToNativeWithTarget()") LLVMToNativeNode toNative,
                        @Cached("createClosureDispatchNode()") LLVMClosureDispatchNode fnDispatchNode,
                        @Cached("createClosureDispatchNode()") LLVMClosureDispatchNode dropInPlaceDispatchNode) {
            LLVMMemory memory = getLLVMMemory();
            LLVMGlobal vtableGlobal = ctx.findGlobal(vtable);
            LangStartVtableType langStartVtable = createLangStartVtable(ctx, vtableGlobal.getPointeeType());
            LLVMNativePointer fn = readFn(memory, vtable, langStartVtable);
            LLVMNativePointer dropInPlace = readDropInPlace(memory, vtable, langStartVtable);
            LLVMNativePointer main = coerceMainForFn(memory, langStartVtable, mainPointer);
            Integer exitCode = (Integer) fnDispatchNode.executeDispatch(fn, new Object[]{stackPointer, main});
            dropInPlaceDispatchNode.executeDispatch(dropInPlace, new Object[]{stackPointer, mainPointer});
            return exitCode.longValue();
        }

        protected LLVMNativePointer readFn(LLVMMemory memory, LLVMNativePointer vtablePointer, LangStartVtableType langStartVtable) {
            return LLVMNativePointer.create(langStartVtable.readFn(memory, vtablePointer.asNative()));
        }

        protected LLVMNativePointer readDropInPlace(LLVMMemory memory, LLVMNativePointer vtablePointer, LangStartVtableType langStartVtable) {
            return LLVMNativePointer.create(langStartVtable.readDropInPlace(memory, vtablePointer.asNative()));
        }

        protected LLVMNativePointer coerceMainForFn(LLVMMemory memory, LangStartVtableType langStartVtable, LLVMNativePointer mainPointer) {
            return LLVMNativePointer.create(langStartVtable.coerceMainForFn(memory, mainPointer.asNative()));
        }

        static final class LangStartVtableType {
            private final long offsetFn;
            private final boolean fnExpectsCoercedMain;

            private LangStartVtableType(DataLayout datalayout, StructureType type, FunctionType fnType) {
                this.offsetFn = type.getOffsetOf(5, datalayout);
                this.fnExpectsCoercedMain = !(((PointerType) fnType.getArgumentTypes()[0]).getPointeeType() instanceof PointerType);
            }

            long readFn(LLVMMemory memory, long address) {
                return memory.getPointer(address + offsetFn).asNative();
            }

            long coerceMainForFn(LLVMMemory memory, long mainAddress) {
                if (fnExpectsCoercedMain) {
                    return memory.getPointer(mainAddress).asNative();
                }
                return mainAddress;
            }

            @SuppressWarnings("static-method")
            long readDropInPlace(LLVMMemory memory, long address) {
                return memory.getPointer(address).asNative();
            }

            static LangStartVtableType create(DataLayout datalayout, Type vtableType) {
                FunctionType fnType = (FunctionType) ((PointerType) ((StructureType) vtableType).getElementTypes()[5]).getPointeeType();
                return new LangStartVtableType(datalayout, (StructureType) vtableType, fnType);
            }

        }
    }

    abstract static class LLVMClosureDispatchNode extends LLVMNode {
        public abstract Object executeDispatch(Object closure, Object[] arguments);

        @Specialization(guards = "pointer.asNative() == cachedAddress")
        protected Object doHandleCached(@SuppressWarnings("unused") LLVMNativePointer pointer, Object[] arguments,
                        @Cached("pointer.asNative()") @SuppressWarnings("unused") long cachedAddress,
                        @Cached("getFunctionDescriptor(pointer)") LLVMFunctionDescriptor cachedDescriptor,
                        @Cached("getDispatchNode(cachedDescriptor)") LLVMDispatchNode dispatchNode) {
            return dispatchNode.executeDispatch(cachedDescriptor, arguments);
        }

        @Specialization(guards = {"isSameObject(pointer.getObject(), cachedDescriptor)", "cachedDescriptor != null", "pointer.getOffset() == 0"})
        protected Object doDirectCached(@SuppressWarnings("unused") LLVMManagedPointer pointer, Object[] arguments,
                        @Cached("asFunctionDescriptor(pointer.getObject())") LLVMFunctionDescriptor cachedDescriptor,
                        @Cached("getDispatchNode(cachedDescriptor)") LLVMDispatchNode dispatchNode) {
            return dispatchNode.executeDispatch(cachedDescriptor, arguments);
        }

        @Specialization
        protected Object doOther(@SuppressWarnings("unused") LLVMManagedPointer pointer, @SuppressWarnings("unused") Object[] arguments) {
            // based on the usage of this node, we can safely assume that the inline cache is always
            // big enough - so we don't have a fallback implementation
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException("Inline cache was not big enough");
        }

        @TruffleBoundary
        protected LLVMFunctionDescriptor getFunctionDescriptor(LLVMNativePointer fp) {
            return lookupContextReference(LLVMLanguage.class).get().getFunctionDescriptor(fp);
        }

        @TruffleBoundary
        protected LLVMDispatchNode getDispatchNode(LLVMFunctionDescriptor fd) {
            return LLVMDispatchNodeGen.create(fd.getType());
        }
    }
}
