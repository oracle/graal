/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.global;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobal.GetFrame;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobal.GetNativePointer;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobal.GetSlot;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobal.IsNative;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalFactory.GetNativePointerNodeGen;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalReadNodeFactory.ReadDoubleNodeGen;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalReadNodeFactory.ReadFloatNodeGen;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalReadNodeFactory.ReadI16NodeGen;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalReadNodeFactory.ReadI1NodeGen;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalReadNodeFactory.ReadI32NodeGen;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalReadNodeFactory.ReadI64NodeGen;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalReadNodeFactory.ReadI8NodeGen;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalReadNodeFactory.ReadObjectNodeGen;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;

public abstract class LLVMGlobalReadNode extends LLVMNode {

    @CompilationFinal private ContextReference<LLVMContext> contextRef;

    @CompilationFinal private LLVMMemory memory;
    @CompilationFinal private boolean memoryResolved = false;

    protected LLVMMemory getMemory() {
        if (!memoryResolved) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            memory = getLLVMMemory();
            memoryResolved = true;
        }
        return memory;
    }

    protected LLVMContext getContext() {
        if (contextRef == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            contextRef = LLVMLanguage.getLLVMContextReference();
        }
        return contextRef.get();
    }

    @Child private IsNative isNativeNode = IsNative.create();

    protected boolean isNative(LLVMGlobal global) {
        return isNativeNode.execute(getContext(), global);
    }

    public abstract static class ReadObjectNode extends LLVMGlobalReadNode {
        public abstract Object execute(LLVMGlobal global);

        public static ReadObjectNode create() {
            return ReadObjectNodeGen.create();
        }

        @Specialization(guards = "!isNative(global)")
        protected Object doFrame(LLVMGlobal global,
                        @Cached("create()") GetFrame getFrame,
                        @Cached("create()") GetSlot getSlot) {
            Object value = getFrame.execute(getContext()).getValue(getSlot.execute(global));
            if (value == null) {
                return LLVMAddress.nullPointer();
            }
            return LLVMGlobal.fromManagedStore(value);
        }

        @Specialization(guards = "isNative(global)")
        protected Object doNative(LLVMGlobal global,
                        @Cached("create()") GetNativePointer getPointer) {
            return getMemory().getAddress(getPointer.execute(getContext(), global));
        }
    }

    public abstract static class ReadI1Node extends LLVMGlobalReadNode {
        public abstract boolean execute(LLVMGlobal global);

        public static ReadI1Node create() {
            return ReadI1NodeGen.create();
        }

        @Specialization(guards = "!isNative(global)")
        protected boolean doFrame(LLVMGlobal global,
                        @Cached("create()") GetFrame getFrame,
                        @Cached("create()") GetSlot getSlot) {
            try {
                return getFrame.execute(getContext()).getBoolean(getSlot.execute(global));
            } catch (FrameSlotTypeException e) {
                CompilerDirectives.transferToInterpreter();
                global.getAsNative(getMemory(), getContext());
                return doNative(global, GetNativePointerNodeGen.create());
            }
        }

        @Specialization(guards = "isNative(global)")
        protected boolean doNative(LLVMGlobal global,
                        @Cached("create()") GetNativePointer getPointer) {
            return getMemory().getI1(getPointer.execute(getContext(), global));
        }
    }

    public abstract static class ReadI8Node extends LLVMGlobalReadNode {
        public abstract byte execute(LLVMGlobal global);

        public static ReadI8Node create() {
            return ReadI8NodeGen.create();
        }

        @Specialization(guards = "!isNative(global)")
        protected byte doFrame(LLVMGlobal global,
                        @Cached("create()") GetFrame getFrame,
                        @Cached("create()") GetSlot getSlot) {
            try {
                return getFrame.execute(getContext()).getByte(getSlot.execute(global));
            } catch (FrameSlotTypeException e) {
                CompilerDirectives.transferToInterpreter();
                global.getAsNative(getMemory(), getContext());
                return doNative(global, GetNativePointerNodeGen.create());
            }
        }

        @Specialization(guards = "isNative(global)")
        protected byte doNative(LLVMGlobal global,
                        @Cached("create()") GetNativePointer getPointer) {
            return getMemory().getI8(getPointer.execute(getContext(), global));
        }
    }

    public abstract static class ReadI16Node extends LLVMGlobalReadNode {
        public abstract short execute(LLVMGlobal global);

        public static ReadI16Node create() {
            return ReadI16NodeGen.create();
        }

        @Specialization(guards = "!isNative(global)")
        protected short doFrame(LLVMGlobal global,
                        @Cached("create()") GetFrame getFrame,
                        @Cached("create()") GetSlot getSlot) {
            try {
                return (short) getFrame.execute(getContext()).getInt(getSlot.execute(global));
            } catch (FrameSlotTypeException e) {
                CompilerDirectives.transferToInterpreter();
                global.getAsNative(getMemory(), getContext());
                return doNative(global, GetNativePointerNodeGen.create());
            }
        }

        @Specialization(guards = "isNative(global)")
        protected short doNative(LLVMGlobal global,
                        @Cached("create()") GetNativePointer getPointer) {
            return getMemory().getI16(getPointer.execute(getContext(), global));
        }
    }

    public abstract static class ReadI32Node extends LLVMGlobalReadNode {
        public abstract int execute(LLVMGlobal global);

        public static ReadI32Node create() {
            return ReadI32NodeGen.create();
        }

        @Specialization(guards = "!isNative(global)")
        protected int doFrame(LLVMGlobal global,
                        @Cached("create()") GetFrame getFrame,
                        @Cached("create()") GetSlot getSlot) {
            try {
                return getFrame.execute(getContext()).getInt(getSlot.execute(global));
            } catch (FrameSlotTypeException e) {
                CompilerDirectives.transferToInterpreter();
                global.getAsNative(getMemory(), getContext());
                return doNative(global, GetNativePointerNodeGen.create());
            }
        }

        @Specialization(guards = "isNative(global)")
        protected int doNative(LLVMGlobal global,
                        @Cached("create()") GetNativePointer getPointer) {
            return getMemory().getI32(getPointer.execute(getContext(), global));
        }
    }

    public abstract static class ReadI64Node extends LLVMGlobalReadNode {
        public abstract long execute(LLVMGlobal global);

        public static ReadI64Node create() {
            return ReadI64NodeGen.create();
        }

        @Specialization(guards = "!isNative(global)")
        protected long doFrame(LLVMGlobal global,
                        @Cached("create()") GetFrame getFrame,
                        @Cached("create()") GetSlot getSlot) {
            try {
                return getFrame.execute(getContext()).getLong(getSlot.execute(global));
            } catch (FrameSlotTypeException e) {
                CompilerDirectives.transferToInterpreter();
                global.getAsNative(getMemory(), getContext());
                return doNative(global, GetNativePointerNodeGen.create());
            }
        }

        @Specialization(guards = "isNative(global)")
        protected long doNative(LLVMGlobal global,
                        @Cached("create()") GetNativePointer getPointer) {
            return getMemory().getI64(getPointer.execute(getContext(), global));
        }
    }

    public abstract static class ReadFloatNode extends LLVMGlobalReadNode {
        public abstract float execute(LLVMGlobal global);

        public static ReadFloatNode create() {
            return ReadFloatNodeGen.create();
        }

        @Specialization(guards = "!isNative(global)")
        protected float doFrame(LLVMGlobal global,
                        @Cached("create()") GetFrame getFrame,
                        @Cached("create()") GetSlot getSlot) {
            try {
                return getFrame.execute(getContext()).getFloat(getSlot.execute(global));
            } catch (FrameSlotTypeException e) {
                CompilerDirectives.transferToInterpreter();
                global.getAsNative(getMemory(), getContext());
                return doNative(global, GetNativePointerNodeGen.create());
            }
        }

        @Specialization(guards = "isNative(global)")
        protected float doNative(LLVMGlobal global,
                        @Cached("create()") GetNativePointer getPointer) {
            return getMemory().getFloat(getPointer.execute(getContext(), global));
        }
    }

    public abstract static class ReadDoubleNode extends LLVMGlobalReadNode {
        public abstract double execute(LLVMGlobal global);

        public static ReadDoubleNode create() {
            return ReadDoubleNodeGen.create();
        }

        @Specialization(guards = "!isNative(global)")
        protected double doFrame(LLVMGlobal global,
                        @Cached("create()") GetFrame getFrame,
                        @Cached("create()") GetSlot getSlot) {
            try {
                return getFrame.execute(getContext()).getDouble(getSlot.execute(global));
            } catch (FrameSlotTypeException e) {
                CompilerDirectives.transferToInterpreter();
                global.getAsNative(getMemory(), getContext());
                return doNative(global, GetNativePointerNodeGen.create());
            }
        }

        @Specialization(guards = "isNative(global)")
        protected double doNative(LLVMGlobal global,
                        @Cached("create()") GetNativePointer getPointer) {
            return getMemory().getDouble(getPointer.execute(getContext(), global));
        }
    }
}
