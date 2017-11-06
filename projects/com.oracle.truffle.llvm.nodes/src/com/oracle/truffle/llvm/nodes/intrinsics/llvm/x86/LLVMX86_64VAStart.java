/*
 * Copyright (c) 2016, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.nodes.intrinsics.llvm.x86;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.llvm.nodes.func.LLVMCallNode;
import com.oracle.truffle.llvm.nodes.memory.LLVMAddressGetElementPtrNode.LLVMIncrementPointerNode;
import com.oracle.truffle.llvm.nodes.memory.LLVMAddressGetElementPtrNodeGen.LLVMIncrementPointerNodeGen;
import com.oracle.truffle.llvm.nodes.memory.store.LLVM80BitFloatStoreNodeGen;
import com.oracle.truffle.llvm.nodes.memory.store.LLVMAddressStoreNodeGen;
import com.oracle.truffle.llvm.nodes.memory.store.LLVMI32StoreNodeGen;
import com.oracle.truffle.llvm.nodes.memory.store.LLVMI64StoreNodeGen;
import com.oracle.truffle.llvm.nodes.memory.store.LLVMStoreNode;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMTruffleObject;
import com.oracle.truffle.llvm.runtime.LLVMVarArgCompoundValue;
import com.oracle.truffle.llvm.runtime.floating.LLVM80BitFloat;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalVariable;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalVariableAccess;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemMoveNode;
import com.oracle.truffle.llvm.runtime.memory.LLVMStackAllocationNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.llvm.runtime.types.VoidType;
import com.oracle.truffle.llvm.runtime.vector.LLVMFloatVector;

@NodeChild
public abstract class LLVMX86_64VAStart extends LLVMExpressionNode {

    private final int numberOfExplicitArguments;
    private final SourceSection sourceSection;
    @Child private LLVMStackAllocationNode stackAllocationNode;

    @Child private LLVMStoreNode i64RegSaveAreaStore;
    @Child private LLVMStoreNode i32RegSaveAreaStore;
    @Child private LLVMStoreNode fp80bitRegSaveAreaStore;
    @Child private LLVMIncrementPointerNode pointerArithmeticRegSaveArea;

    @Child private LLVMStoreNode i64OverflowArgAreaStore;
    @Child private LLVMStoreNode i32OverflowArgAreaStore;
    @Child private LLVMStoreNode fp80bitOverflowArgAreaStore;
    @Child private LLVMIncrementPointerNode pointerArithmeticOverflowArea;

    @Child private LLVMIncrementPointerNode pointerArithmeticStructInit;
    @Child private LLVMStoreNode gpOffsetStore;
    @Child private LLVMStoreNode fpOffsetStore;
    @Child private LLVMStoreNode overflowArgAreaStore;
    @Child private LLVMStoreNode regSaveAreaStore;

    @Child private LLVMMemMoveNode memmove;

    public LLVMX86_64VAStart(int numberOfExplicitArguments, SourceSection sourceSection,
                    LLVMStackAllocationNode stackAllocationNode,
                    LLVMMemMoveNode memmove) {
        this.numberOfExplicitArguments = numberOfExplicitArguments;
        this.sourceSection = sourceSection;
        this.stackAllocationNode = stackAllocationNode;

        this.i64RegSaveAreaStore = LLVMI64StoreNodeGen.create();
        this.i32RegSaveAreaStore = LLVMI32StoreNodeGen.create();
        this.fp80bitRegSaveAreaStore = LLVM80BitFloatStoreNodeGen.create();
        this.pointerArithmeticRegSaveArea = LLVMIncrementPointerNodeGen.create();

        this.i64OverflowArgAreaStore = LLVMI64StoreNodeGen.create();
        this.i32OverflowArgAreaStore = LLVMI32StoreNodeGen.create();
        this.fp80bitOverflowArgAreaStore = LLVM80BitFloatStoreNodeGen.create();
        this.pointerArithmeticOverflowArea = LLVMIncrementPointerNodeGen.create();

        this.pointerArithmeticStructInit = LLVMIncrementPointerNodeGen.create();
        this.gpOffsetStore = LLVMI32StoreNodeGen.create();
        this.fpOffsetStore = LLVMI32StoreNodeGen.create();
        this.overflowArgAreaStore = LLVMAddressStoreNodeGen.create(new PointerType(VoidType.INSTANCE));
        this.regSaveAreaStore = LLVMAddressStoreNodeGen.create(new PointerType(VoidType.INSTANCE));

        this.memmove = memmove;
    }

    private void setGPOffset(VirtualFrame frame, Object address, int value) {
        Object p = pointerArithmeticStructInit.executeWithTarget(address, X86_64BitVarArgs.GP_OFFSET, PrimitiveType.I32);
        gpOffsetStore.executeWithTarget(frame, p, value);
    }

    private void setFPOffset(VirtualFrame frame, Object address, int value) {
        Object p = pointerArithmeticStructInit.executeWithTarget(address, X86_64BitVarArgs.FP_OFFSET, PrimitiveType.I32);
        fpOffsetStore.executeWithTarget(frame, p, value);
    }

    private void setOverflowArgArea(VirtualFrame frame, Object address, Object value) {
        Object p = pointerArithmeticStructInit.executeWithTarget(address, X86_64BitVarArgs.OVERFLOW_ARG_AREA, new PointerType(VoidType.INSTANCE));
        overflowArgAreaStore.executeWithTarget(frame, p, value);
    }

    private void setRegSaveArea(VirtualFrame frame, Object address, Object value) {
        Object p = pointerArithmeticStructInit.executeWithTarget(address, X86_64BitVarArgs.REG_SAVE_AREA, new PointerType(VoidType.INSTANCE));
        regSaveAreaStore.executeWithTarget(frame, p, value);
    }

    private void initializeVaList(VirtualFrame frame, Object valist, int gpOffset, int fpOffset, Object overflowArgArea, Object regSaveArea) {
        setGPOffset(frame, valist, gpOffset);
        setFPOffset(frame, valist, fpOffset);
        setOverflowArgArea(frame, valist, overflowArgArea);
        setRegSaveArea(frame, valist, regSaveArea);
    }

    private enum VarArgArea {
        GP_AREA,
        FP_AREA,
        OVERFLOW_AREA;
    }

    private static VarArgArea getVarArgArea(Object arg) {
        if (arg instanceof Boolean) {
            return VarArgArea.GP_AREA;
        } else if (arg instanceof Byte) {
            return VarArgArea.GP_AREA;
        } else if (arg instanceof Short) {
            return VarArgArea.GP_AREA;
        } else if (arg instanceof Integer) {
            return VarArgArea.GP_AREA;
        } else if (arg instanceof Long) {
            return VarArgArea.GP_AREA;
        } else if (arg instanceof Float) {
            return VarArgArea.FP_AREA;
        } else if (arg instanceof Double) {
            return VarArgArea.FP_AREA;
        } else if (arg instanceof LLVMVarArgCompoundValue) {
            return VarArgArea.OVERFLOW_AREA;
        } else if (arg instanceof LLVMAddress) {
            return VarArgArea.GP_AREA;
        } else if (arg instanceof LLVMGlobalVariable) {
            return VarArgArea.GP_AREA;
        } else if (arg instanceof LLVM80BitFloat) {
            return VarArgArea.OVERFLOW_AREA;
        } else if (arg instanceof LLVMFloatVector && ((LLVMFloatVector) arg).getLength() <= 2) {
            return VarArgArea.FP_AREA;
        } else if (arg instanceof LLVMTruffleObject) {
            return VarArgArea.GP_AREA;
        } else {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError(arg);
        }
    }

    private static Object[] getArgumentsArray(VirtualFrame frame) {
        Object[] arguments = frame.getArguments();
        Object[] newArguments = new Object[arguments.length - LLVMCallNode.USER_ARGUMENT_OFFSET];
        System.arraycopy(arguments, LLVMCallNode.USER_ARGUMENT_OFFSET, newArguments, 0, newArguments.length);

        return newArguments;
    }

    private int computeOverflowArgAreaSize(Object[] realArguments) {
        assert numberOfExplicitArguments <= realArguments.length;

        int overflowArea = 0;
        int gpOffset = calculateUsedGpArea(realArguments);
        int fpOffset = X86_64BitVarArgs.GP_LIMIT + calculateUsedFpArea(realArguments);
        for (int i = numberOfExplicitArguments; i < realArguments.length; i++) {
            final Object arg = realArguments[i];
            final VarArgArea area = getVarArgArea(arg);
            if (area == VarArgArea.GP_AREA && gpOffset < X86_64BitVarArgs.GP_LIMIT) {
                gpOffset += X86_64BitVarArgs.GP_STEP;
            } else if (area == VarArgArea.FP_AREA && fpOffset < X86_64BitVarArgs.FP_LIMIT) {
                fpOffset += X86_64BitVarArgs.FP_STEP;
            } else if (area != VarArgArea.OVERFLOW_AREA) {
                overflowArea += X86_64BitVarArgs.STACK_STEP;
            } else if (arg instanceof LLVM80BitFloat) {
                overflowArea += 16;
            } else if (arg instanceof LLVMVarArgCompoundValue) {
                LLVMVarArgCompoundValue obj = (LLVMVarArgCompoundValue) arg;
                overflowArea += obj.getSize();
            } else {
                throw new AssertionError(arg);
            }
        }
        return overflowArea;

    }

    private int calculateUsedFpArea(Object[] realArguments) {
        assert numberOfExplicitArguments <= realArguments.length;

        int usedFpArea = 0;
        final int fpAreaLimit = X86_64BitVarArgs.FP_LIMIT - X86_64BitVarArgs.GP_LIMIT;
        for (int i = 0; i < numberOfExplicitArguments && usedFpArea < fpAreaLimit; i++) {
            if (getVarArgArea(realArguments[i]) == VarArgArea.FP_AREA) {
                usedFpArea += X86_64BitVarArgs.FP_STEP;
            }
        }
        return usedFpArea;
    }

    private int calculateUsedGpArea(Object[] realArguments) {
        assert numberOfExplicitArguments <= realArguments.length;

        int usedGpArea = 0;
        for (int i = 0; i < numberOfExplicitArguments && usedGpArea < X86_64BitVarArgs.GP_LIMIT; i++) {
            if (getVarArgArea(realArguments[i]) == VarArgArea.GP_AREA) {
                usedGpArea += X86_64BitVarArgs.GP_STEP;
            }
        }

        return usedGpArea;
    }

    @Specialization
    public Object vaStart(VirtualFrame frame, Object targetAddress) {
        final Object[] arguments = getArgumentsArray(frame);
        final int vaLength = arguments.length - numberOfExplicitArguments;

        Object regSaveArea = stackAllocationNode.executeWithTarget(frame, X86_64BitVarArgs.FP_LIMIT);
        int overflowArgAreaSize = computeOverflowArgAreaSize(arguments);
        Object overflowArgArea = stackAllocationNode.executeWithTarget(frame, overflowArgAreaSize);

        int gpOffset = calculateUsedGpArea(arguments);
        int fpOffset = X86_64BitVarArgs.GP_LIMIT + calculateUsedFpArea(arguments);

        initializeVaList(frame, targetAddress, gpOffset, fpOffset, overflowArgArea, regSaveArea);

        // reconstruct register_save_area and overflow_arg_area according to AMD64 ABI
        if (vaLength > 0) {
            int overflowOffset = 0;

            for (int i = 0; i < vaLength; i++) {
                final Object object = arguments[numberOfExplicitArguments + i];
                final VarArgArea area = getVarArgArea(object);

                if (area == VarArgArea.GP_AREA && gpOffset < X86_64BitVarArgs.GP_LIMIT) {
                    storeArgument(frame, regSaveArea, gpOffset, memmove, pointerArithmeticRegSaveArea, i64RegSaveAreaStore, i32RegSaveAreaStore, fp80bitRegSaveAreaStore, object);
                    gpOffset += X86_64BitVarArgs.GP_STEP;
                } else if (area == VarArgArea.FP_AREA && fpOffset < X86_64BitVarArgs.FP_LIMIT) {
                    storeArgument(frame, regSaveArea, fpOffset, memmove, pointerArithmeticRegSaveArea, i64RegSaveAreaStore, i32RegSaveAreaStore, fp80bitRegSaveAreaStore, object);
                    fpOffset += X86_64BitVarArgs.FP_STEP;
                } else {
                    assert overflowArgAreaSize >= overflowOffset;
                    overflowOffset += storeArgument(frame, overflowArgArea, overflowOffset, memmove, pointerArithmeticOverflowArea, i64OverflowArgAreaStore, i32OverflowArgAreaStore,
                                    fp80bitOverflowArgAreaStore, object);
                }
            }
        }

        return null;
    }

    @Override
    public SourceSection getSourceSection() {
        return sourceSection;
    }

    @Child private LLVMGlobalVariableAccess globalAccess = createGlobalAccess();

    private static int storeArgument(VirtualFrame frame, Object ptr, long offset, LLVMMemMoveNode memmove, LLVMIncrementPointerNode pointerArithmetic, LLVMStoreNode storeI64Node,
                    LLVMStoreNode storeI32Node,
                    LLVMStoreNode storeFP80Node, Object object) {
        if (object instanceof Number) {
            return doPrimitiveWrite(frame, ptr, offset, pointerArithmetic, storeI64Node, object);
        } else if (object instanceof LLVMVarArgCompoundValue) {
            LLVMVarArgCompoundValue obj = (LLVMVarArgCompoundValue) object;
            Object currentPtr = pointerArithmetic.executeWithTarget(ptr, offset, PrimitiveType.I64);
            memmove.executeWithTarget(frame, currentPtr, obj.getAddr(), obj.getSize());
            return obj.getSize();
        } else if (object instanceof LLVMAddress || object instanceof LLVMGlobalVariable || object instanceof LLVMTruffleObject) {
            Object currentPtr = pointerArithmetic.executeWithTarget(ptr, offset, PrimitiveType.I64);
            storeI64Node.executeWithTarget(frame, currentPtr, object);
            return X86_64BitVarArgs.STACK_STEP;
        } else if (object instanceof LLVM80BitFloat) {
            Object currentPtr = pointerArithmetic.executeWithTarget(ptr, offset, PrimitiveType.X86_FP80);
            storeFP80Node.executeWithTarget(frame, currentPtr, object);
            return 16;
        } else if (object instanceof LLVMFloatVector) {
            final LLVMFloatVector floatVec = (LLVMFloatVector) object;
            for (int i = 0; i < floatVec.getLength(); i++) {
                Object currentPtr = pointerArithmetic.executeWithTarget(ptr, offset + i * Float.BYTES, PrimitiveType.FLOAT);
                storeI32Node.executeWithTarget(frame, currentPtr, Float.floatToIntBits(floatVec.getValue(i)));
            }
            return floatVec.getLength() * Float.BYTES;
        } else {
            throw new AssertionError(object);
        }
    }

    private static int doPrimitiveWrite(VirtualFrame frame, Object ptr, long offset, LLVMIncrementPointerNode pointerArithmetic, LLVMStoreNode storeNode, Object arg) throws AssertionError {
        Object currentPtr = pointerArithmetic.executeWithTarget(ptr, offset, PrimitiveType.I64);
        long value;
        if (arg instanceof Boolean) {
            value = ((boolean) arg) ? 1L : 0L;
        } else if (arg instanceof Byte) {
            value = Integer.toUnsignedLong((byte) arg);
        } else if (arg instanceof Short) {
            value = Integer.toUnsignedLong((short) arg);
        } else if (arg instanceof Integer) {
            value = Integer.toUnsignedLong((int) arg);
        } else if (arg instanceof Long) {
            value = (long) arg;
        } else if (arg instanceof Float) {
            value = Integer.toUnsignedLong(Float.floatToIntBits((float) arg));
        } else if (arg instanceof Double) {
            value = Double.doubleToRawLongBits((double) arg);
        } else {
            throw new AssertionError(arg);
        }
        storeNode.executeWithTarget(frame, currentPtr, value);
        return X86_64BitVarArgs.STACK_STEP;
    }

}
