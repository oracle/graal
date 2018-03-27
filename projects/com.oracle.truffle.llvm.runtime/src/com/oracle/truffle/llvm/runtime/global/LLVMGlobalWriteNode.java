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
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobal.GetFrame;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobal.GetNativePointer;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobal.GetSlot;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobal.IsNative;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobal.Native;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalWriteNodeFactory.WriteDoubleNodeGen;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalWriteNodeFactory.WriteFloatNodeGen;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalWriteNodeFactory.WriteI16NodeGen;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalWriteNodeFactory.WriteI1NodeGen;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalWriteNodeFactory.WriteI32NodeGen;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalWriteNodeFactory.WriteI64NodeGen;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalWriteNodeFactory.WriteI8NodeGen;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalWriteNodeFactory.WriteObjectNodeGen;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMToNativeNode;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;

public abstract class LLVMGlobalWriteNode extends LLVMNode {

    @CompilationFinal private ContextReference<LLVMContext> contextRef;

    protected LLVMContext getContext() {
        if (contextRef == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            contextRef = LLVMLanguage.getLLVMContextReference();
        }
        return contextRef.get();
    }

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

    @Child private IsNative isNativeNode = IsNative.create();

    protected boolean isNative(LLVMGlobal global) {
        return isNativeNode.execute(getContext(), global);
    }

    public static void slowPrimitiveWrite(LLVMContext context, LLVMMemory memory, PrimitiveType primitiveType, LLVMGlobal global, Object value) {
        MaterializedFrame frame = context.getGlobalFrame();
        FrameSlot slot = global.getSlot();
        boolean isNative = frame.getValue(slot) instanceof Native;
        long address = isNative ? ((Native) frame.getValue(slot)).getPointer() : 0;
        switch (primitiveType.getPrimitiveKind()) {
            case I1:
                if (isNative) {
                    memory.putI1(address, (boolean) value);
                } else {
                    frame.setBoolean(slot, (boolean) value);
                }
                return;
            case I8:
                if (isNative) {
                    memory.putI8(address, (byte) value);
                } else {
                    frame.setByte(slot, (byte) value);
                }
                return;
            case I16:
                if (isNative) {
                    memory.putI16(address, (short) value);
                } else {
                    frame.setInt(slot, (short) value);
                }
                return;
            case I32:
                if (isNative) {
                    memory.putI32(address, (int) value);
                } else {
                    frame.setInt(slot, (int) value);
                }
                return;
            case I64:
                if (isNative) {
                    memory.putI64(address, (long) value);
                } else {
                    frame.setLong(slot, (long) value);
                }
                return;
            case FLOAT:
                if (isNative) {
                    memory.putFloat(address, (float) value);
                } else {
                    frame.setFloat(slot, (float) value);
                }
                return;
            case DOUBLE:
                if (isNative) {
                    memory.putDouble(address, (double) value);
                } else {
                    frame.setDouble(slot, (double) value);
                }
                return;
        }
        CompilerDirectives.transferToInterpreter();
        throw new IllegalStateException();
    }

    public abstract static class WriteObjectNode extends LLVMGlobalWriteNode {
        public abstract Object execute(LLVMGlobal global, Object value);

        public static WriteObjectNode create() {
            return WriteObjectNodeGen.create();
        }

        @Specialization(guards = "!isNative(global)")
        protected Object doFrame(LLVMGlobal global, Object value,
                        @Cached("create()") GetFrame getFrame,
                        @Cached("create()") GetSlot getSlot) {
            getFrame.execute(getContext()).setObject(getSlot.execute(global), value);
            return value;
        }

        @Specialization(guards = "isNative(global)")
        protected Object doNative(LLVMGlobal global, Object value,
                        @Cached("create()") GetNativePointer getPointer,
                        @Cached("createToNativeWithTarget()") LLVMToNativeNode toNative) {
            getMemory().putAddress(getPointer.execute(getContext(), global), toNative.executeWithTarget(value));
            return value;
        }
    }

    public abstract static class WriteI1Node extends LLVMGlobalWriteNode {
        public abstract Object execute(LLVMGlobal global, boolean value);

        public static WriteI1Node create() {
            return WriteI1NodeGen.create();
        }

        @Specialization(guards = "!isNative(global)")
        protected Object doFrame(LLVMGlobal global, boolean value,
                        @Cached("create()") GetFrame getFrame,
                        @Cached("create()") GetSlot getSlot) {
            getFrame.execute(getContext()).setBoolean(getSlot.execute(global), value);
            return value;
        }

        @Specialization(guards = "isNative(global)")
        protected Object doNative(LLVMGlobal global, boolean value,
                        @Cached("create()") GetNativePointer getPointer) {
            getMemory().putI1(getPointer.execute(getContext(), global), value);
            return value;
        }
    }

    public abstract static class WriteI8Node extends LLVMGlobalWriteNode {
        public abstract Object execute(LLVMGlobal global, byte value);

        public static WriteI8Node create() {
            return WriteI8NodeGen.create();
        }

        @Specialization(guards = "!isNative(global)")
        protected Object doFrame(LLVMGlobal global, byte value,
                        @Cached("create()") GetFrame getFrame,
                        @Cached("create()") GetSlot getSlot) {
            getFrame.execute(getContext()).setByte(getSlot.execute(global), value);
            return value;
        }

        @Specialization(guards = "isNative(global)")
        protected Object doNative(LLVMGlobal global, byte value,
                        @Cached("create()") GetNativePointer getPointer) {
            getMemory().putI8(getPointer.execute(getContext(), global), value);
            return value;
        }
    }

    public abstract static class WriteI16Node extends LLVMGlobalWriteNode {
        public abstract Object execute(LLVMGlobal global, short value);

        public static WriteI16Node create() {
            return WriteI16NodeGen.create();
        }

        @Specialization(guards = "!isNative(global)")
        protected Object doFrame(LLVMGlobal global, short value,
                        @Cached("create()") GetFrame getFrame,
                        @Cached("create()") GetSlot getSlot) {
            getFrame.execute(getContext()).setInt(getSlot.execute(global), value);
            return value;
        }

        @Specialization(guards = "isNative(global)")
        protected Object doNative(LLVMGlobal global, short value,
                        @Cached("create()") GetNativePointer getPointer) {
            getMemory().putI16(getPointer.execute(getContext(), global), value);
            return value;
        }
    }

    public abstract static class WriteI32Node extends LLVMGlobalWriteNode {
        public abstract Object execute(LLVMGlobal global, int value);

        public static WriteI32Node create() {
            return WriteI32NodeGen.create();
        }

        @Specialization(guards = "!isNative(global)")
        protected Object doFrame(LLVMGlobal global, int value,
                        @Cached("create()") GetFrame getFrame,
                        @Cached("create()") GetSlot getSlot) {
            getFrame.execute(getContext()).setInt(getSlot.execute(global), value);
            return value;
        }

        @Specialization(guards = "isNative(global)")
        protected Object doNative(LLVMGlobal global, int value,
                        @Cached("create()") GetNativePointer getPointer) {
            getMemory().putI32(getPointer.execute(getContext(), global), value);
            return value;
        }
    }

    public abstract static class WriteI64Node extends LLVMGlobalWriteNode {
        public abstract Object execute(LLVMGlobal global, long value);

        public static WriteI64Node create() {
            return WriteI64NodeGen.create();
        }

        @Specialization(guards = "!isNative(global)")
        protected Object doFrame(LLVMGlobal global, long value,
                        @Cached("create()") GetFrame getFrame,
                        @Cached("create()") GetSlot getSlot) {
            getFrame.execute(getContext()).setLong(getSlot.execute(global), value);
            return value;
        }

        @Specialization(guards = "isNative(global)")
        protected Object doNative(LLVMGlobal global, long value,
                        @Cached("create()") GetNativePointer getPointer) {
            getMemory().putI64(getPointer.execute(getContext(), global), value);
            return value;
        }
    }

    public abstract static class WriteFloatNode extends LLVMGlobalWriteNode {
        public abstract Object execute(LLVMGlobal global, float value);

        public static WriteFloatNode create() {
            return WriteFloatNodeGen.create();
        }

        @Specialization(guards = "!isNative(global)")
        protected Object doFrame(LLVMGlobal global, float value,
                        @Cached("create()") GetFrame getFrame,
                        @Cached("create()") GetSlot getSlot) {
            getFrame.execute(getContext()).setFloat(getSlot.execute(global), value);
            return value;
        }

        @Specialization(guards = "isNative(global)")
        protected Object doNative(LLVMGlobal global, float value,
                        @Cached("create()") GetNativePointer getPointer) {
            getMemory().putFloat(getPointer.execute(getContext(), global), value);
            return value;
        }
    }

    public abstract static class WriteDoubleNode extends LLVMGlobalWriteNode {
        public abstract Object execute(LLVMGlobal global, double value);

        public static WriteDoubleNode create() {
            return WriteDoubleNodeGen.create();
        }

        @Specialization(guards = "!isNative(global)")
        protected Object doFrame(LLVMGlobal global, double value,
                        @Cached("create()") GetFrame getFrame,
                        @Cached("create()") GetSlot getSlot) {
            getFrame.execute(getContext()).setDouble(getSlot.execute(global), value);
            return value;
        }

        @Specialization(guards = "isNative(global)")
        protected Object doNative(LLVMGlobal global, double value,
                        @Cached("create()") GetNativePointer getPointer) {
            getMemory().putDouble(getPointer.execute(getContext(), global), value);
            return value;
        }
    }
}
