/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
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
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.llvm.nodes.func.LLVMDispatchNode;
import com.oracle.truffle.llvm.nodes.func.LLVMDispatchNodeGen;
import com.oracle.truffle.llvm.nodes.func.LLVMLookupDispatchNode;
import com.oracle.truffle.llvm.nodes.func.LLVMLookupDispatchNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMIntrinsic;
import com.oracle.truffle.llvm.nodes.intrinsics.rust.LLVMStartFactory.LLVMClosureDispatchNodeGen;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.datalayout.DataLayout;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobal;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack.StackPointer;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMToNativeNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;
import com.oracle.truffle.llvm.runtime.types.FunctionType;
import com.oracle.truffle.llvm.runtime.types.StructureType;
import com.oracle.truffle.llvm.runtime.types.Type;

public abstract class LLVMStart extends LLVMIntrinsic {

    protected LLVMClosureDispatchNode getClosureDispatchNode() {
        CompilerAsserts.neverPartOfCompilation();
        return LLVMClosureDispatchNodeGen.create();
    }

    @NodeChildren({@NodeChild(type = LLVMExpressionNode.class), @NodeChild(type = LLVMExpressionNode.class), @NodeChild(type = LLVMExpressionNode.class), @NodeChild(type = LLVMExpressionNode.class)})
    public abstract static class LLVMLangStart extends LLVMStart {

        @Specialization
        @SuppressWarnings("unused")
        protected long doOp(StackPointer stackPointer, LLVMNativePointer main, long argc, LLVMPointer argv,
                        @Cached("getClosureDispatchNode()") LLVMClosureDispatchNode dispatchNode) {
            dispatchNode.executeDispatch(main, new Object[]{stackPointer});
            return 0;
        }

        @Specialization
        @SuppressWarnings("unused")
        protected long doOp(StackPointer stackPointer, LLVMFunctionDescriptor main, long argc, LLVMPointer argv,
                        @Cached("getClosureDispatchNode()") LLVMClosureDispatchNode dispatchNode) {
            dispatchNode.executeDispatch(main, new Object[]{stackPointer});
            return 0;
        }

    }

    @NodeChildren({@NodeChild(type = LLVMExpressionNode.class), @NodeChild(type = LLVMExpressionNode.class), @NodeChild(type = LLVMExpressionNode.class), @NodeChild(type = LLVMExpressionNode.class),
                    @NodeChild(type = LLVMExpressionNode.class)})
    public abstract static class LLVMLangStartInternal extends LLVMStart {

        protected LangStartVtableType createLangStartVtable(Type vtableType) {
            DataLayout dataSpecConverter = getContextReference().get().getDataSpecConverter();
            return LangStartVtableType.create(dataSpecConverter, vtableType);
        }

        @Specialization
        @SuppressWarnings("unused")
        protected long doOp(StackPointer stackPointer, LLVMNativePointer mainPointer, LLVMGlobal vtable, long argc, LLVMPointer argv,
                        @Cached("createToNativeWithTarget()") LLVMToNativeNode toNative,
                        @Cached("getClosureDispatchNode()") LLVMClosureDispatchNode fnDispatchNode,
                        @Cached("getClosureDispatchNode()") LLVMClosureDispatchNode dropInPlaceDispatchNode) {
            LLVMMemory memory = getLLVMMemory();
            LangStartVtableType langStartVtable = createLangStartVtable(vtable.getPointeeType());
            LLVMNativePointer vtablePointer = toNative.executeWithTarget(vtable);
            LLVMNativePointer fn = readFn(memory, vtablePointer, langStartVtable);
            LLVMNativePointer dropInPlace = readDropInPlace(memory, vtablePointer, langStartVtable);
            LLVMNativePointer main = derefMain(memory, mainPointer);
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

        protected LLVMNativePointer derefMain(LLVMMemory memory, LLVMNativePointer mainPointer) {
            return LLVMNativePointer.create(memory.getFunctionPointer(mainPointer));
        }

        static final class LangStartVtableType {
            private final long offsetFn;

            private LangStartVtableType(DataLayout datalayout, StructureType type) {
                this.offsetFn = type.getOffsetOf(5, datalayout);
            }

            long readFn(LLVMMemory memory, long address) {
                return memory.getFunctionPointer(LLVMNativePointer.create(address + offsetFn));
            }

            @SuppressWarnings("static-method")
            long readDropInPlace(LLVMMemory memory, long address) {
                return memory.getFunctionPointer(LLVMNativePointer.create(address));
            }

            static LangStartVtableType create(DataLayout datalayout, Type vtableType) {
                return new LangStartVtableType(datalayout, (StructureType) vtableType);
            }

        }
    }

    abstract static class LLVMClosureDispatchNode extends LLVMNode {

        public abstract Object executeDispatch(Object closure, Object[] arguments);

        @Specialization(guards = "closure.asNative() == cachedClosure.asNative()")
        @SuppressWarnings("unused")
        protected Object doCached(LLVMNativePointer closure, Object[] arguments,
                        @Cached("closure") LLVMNativePointer cachedClosure,
                        @Cached("getFunctionDescriptor(cachedClosure)") LLVMFunctionDescriptor closureDescriptor,
                        @Cached("getDispatchNode(closureDescriptor)") LLVMDispatchNode dispatchNode) {
            return dispatchNode.executeDispatch(closureDescriptor, arguments);
        }

        @Specialization
        protected Object doLookup(LLVMNativePointer closure, Object[] arguments,
                        @Cached("getLookupDispatchNode(closure)") LLVMLookupDispatchNode dispatchNode) {
            return dispatchNode.executeDispatch(closure, arguments);
        }

        @Specialization(guards = "closure == cachedClosure")
        @SuppressWarnings("unused")
        protected Object doCached(LLVMFunctionDescriptor closure, Object[] arguments,
                        @Cached("closure") LLVMFunctionDescriptor cachedClosure,
                        @Cached("getDispatchNode(cachedClosure)") LLVMDispatchNode dispatchNode) {
            return dispatchNode.executeDispatch(closure, arguments);
        }

        @Specialization
        protected Object doDirect(LLVMFunctionDescriptor closure, Object[] arguments,
                        @Cached("getDispatchNode(closure)") LLVMDispatchNode dispatchNode) {
            return dispatchNode.executeDispatch(closure, arguments);
        }

        @TruffleBoundary
        protected LLVMFunctionDescriptor getFunctionDescriptor(LLVMNativePointer fp) {
            return getContextReference().get().getFunctionDescriptor(fp);
        }

        protected LLVMDispatchNode getDispatchNode(LLVMFunctionDescriptor fd) {
            CompilerAsserts.neverPartOfCompilation();
            return LLVMDispatchNodeGen.create(fd.getType());
        }

        protected LLVMLookupDispatchNode getLookupDispatchNode(LLVMNativePointer fp) {
            CompilerAsserts.neverPartOfCompilation();
            FunctionType functionType = getContextReference().get().getFunctionDescriptor(fp).getType();
            return LLVMLookupDispatchNodeGen.create(functionType);
        }

    }

}
