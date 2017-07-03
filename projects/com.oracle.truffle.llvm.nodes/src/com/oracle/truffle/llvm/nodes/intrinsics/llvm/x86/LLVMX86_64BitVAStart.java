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
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.llvm.nodes.func.LLVMCallNode;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMBoxedPrimitive;
import com.oracle.truffle.llvm.runtime.LLVMVarArgCompoundValue;
import com.oracle.truffle.llvm.runtime.floating.LLVM80BitFloat;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalVariable;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalVariableAccess;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;
import com.oracle.truffle.llvm.runtime.memory.LLVMProfiledMemMove;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack.NeedsStack;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.vector.LLVMFloatVector;

@NeedsStack
public final class LLVMX86_64BitVAStart extends LLVMExpressionNode {

    private static final int LONG_DOUBLE_SIZE = 16;
    private final int numberOfExplicitArguments;
    private final SourceSection sourceSection;
    private final LLVMProfiledMemMove profiledMemMove = new LLVMProfiledMemMove();
    @Child private LLVMExpressionNode target;
    @Child private LLVMForceLLVMAddressNode targetToAddress;
    @Child private Node isPointer = Message.IS_POINTER.createNode();
    @Child private Node asPointer = Message.AS_POINTER.createNode();
    @Child private Node toNative = Message.TO_NATIVE.createNode();
    @Child private Node isBoxed = Message.IS_BOXED.createNode();
    @Child private Node unbox = Message.UNBOX.createNode();

    public LLVMX86_64BitVAStart(int numberOfExplicitArguments, LLVMExpressionNode target, SourceSection sourceSection) {
        if (numberOfExplicitArguments < 0) {
            throw new AssertionError();
        }
        this.numberOfExplicitArguments = numberOfExplicitArguments;
        this.target = target;
        this.targetToAddress = getForceLLVMAddressNode();
        this.sourceSection = sourceSection;
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
        } else {
            throw new AssertionError(arg);
        }
    }

    @CompilationFinal private FrameSlot stackPointerSlot;

    private FrameSlot getStackPointerSlot() {
        if (stackPointerSlot == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            stackPointerSlot = getRootNode().getFrameDescriptor().findFrameSlot(LLVMStack.FRAME_ID);
        }
        return stackPointerSlot;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        final Object[] realArguments = getRealArguments(frame);
        unboxArguments(realArguments, numberOfExplicitArguments);
        final int nrVarArgs = realArguments.length - numberOfExplicitArguments;

        // Allocate register_save_area
        LLVMAddress structAddress = targetToAddress.executeWithTarget(target.executeGeneric(frame));
        final long regSaveArea = LLVMStack.allocateStackMemory(frame, getStackPointerSlot(), X86_64BitVarArgs.FP_LIMIT, 8);
        LLVMMemory.putAddress(structAddress.getVal() + X86_64BitVarArgs.REG_SAVE_AREA, regSaveArea);

        // Allocate overflow_arg_area
        final int overflowAreaSize = calculateOverflowArea(realArguments, numberOfExplicitArguments);
        final long overflowArgArea = LLVMStack.allocateStackMemory(frame, getStackPointerSlot(), overflowAreaSize, 8);
        LLVMMemory.putAddress(structAddress.getVal() + X86_64BitVarArgs.OVERFLOW_ARG_AREA, overflowArgArea);

        // calculate initial offsets for the register_save_area
        int gpOffset = calculateUsedGpArea(realArguments, numberOfExplicitArguments);
        int fpOffset = X86_64BitVarArgs.GP_LIMIT + calculateUsedFpArea(realArguments, numberOfExplicitArguments);
        LLVMMemory.putI32(structAddress.getVal() + X86_64BitVarArgs.GP_OFFSET, gpOffset);
        LLVMMemory.putI32(structAddress.getVal() + X86_64BitVarArgs.FP_OFFSET, fpOffset);

        // reconstruct register_save_area and overflow_arg_area according to AMD64 ABI
        if (nrVarArgs > 0) {
            int overflowOffset = 0;

            for (int i = 0; i < nrVarArgs; i++) {
                final Object object = realArguments[numberOfExplicitArguments + i];
                final VarArgArea area = getVarArgArea(object);

                switch (area) {
                    case GP_AREA:
                        if (gpOffset < X86_64BitVarArgs.GP_LIMIT) {
                            storeArgument(regSaveArea + gpOffset, object);
                            gpOffset += X86_64BitVarArgs.GP_STEP;
                        } else {
                            assert overflowAreaSize >= overflowOffset;
                            overflowOffset += storeArgument(overflowArgArea + overflowOffset, object);
                        }
                        break;

                    case FP_AREA:
                        if (fpOffset < X86_64BitVarArgs.FP_LIMIT) {
                            storeArgument(regSaveArea + fpOffset, object);
                            fpOffset += X86_64BitVarArgs.FP_STEP;
                        } else {
                            assert overflowAreaSize >= overflowOffset;
                            overflowOffset += storeArgument(overflowArgArea + overflowOffset, object);
                        }
                        break;

                    case OVERFLOW_AREA:
                        assert overflowAreaSize >= overflowOffset;
                        overflowOffset += storeArgument(overflowArgArea + overflowOffset, object);
                        break;

                    default:
                        CompilerDirectives.transferToInterpreter();
                        throw new IllegalStateException("not supported: " + area);
                }
            }
        }

        return null;
    }

    private static int calculateUsedGpArea(Object[] realArguments, int numberOfExplicitArguments) {
        assert numberOfExplicitArguments <= realArguments.length;

        int usedGpArea = 0;
        for (int i = 0; i < numberOfExplicitArguments && usedGpArea < X86_64BitVarArgs.GP_LIMIT; i++) {
            if (getVarArgArea(realArguments[i]) == VarArgArea.GP_AREA) {
                usedGpArea += X86_64BitVarArgs.GP_STEP;
            }
        }

        return usedGpArea;
    }

    private static int calculateUsedFpArea(Object[] realArguments, int numberOfExplicitArguments) {
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

    private static int calculateOverflowArea(Object[] realArguments, int numberOfExplicitArguments) {
        assert numberOfExplicitArguments <= realArguments.length;

        int overflowArea = 0;
        int gpOffset = calculateUsedGpArea(realArguments, numberOfExplicitArguments);
        int fpOffset = X86_64BitVarArgs.GP_LIMIT + calculateUsedFpArea(realArguments, numberOfExplicitArguments);
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
                overflowArea += LONG_DOUBLE_SIZE;
            } else if (arg instanceof LLVMVarArgCompoundValue) {
                LLVMVarArgCompoundValue obj = (LLVMVarArgCompoundValue) arg;
                overflowArea += obj.getSize();
            } else {
                throw new AssertionError(arg);
            }
        }
        return overflowArea;

    }

    private static Object[] getRealArguments(VirtualFrame frame) {
        Object[] arguments = frame.getArguments();
        Object[] newArguments = new Object[arguments.length - LLVMCallNode.USER_ARGUMENT_OFFSET];
        System.arraycopy(arguments, LLVMCallNode.USER_ARGUMENT_OFFSET, newArguments, 0, newArguments.length);

        return newArguments;
    }

    @Override
    public SourceSection getSourceSection() {
        return sourceSection;
    }

    private void unboxArguments(Object[] arguments, int varArgsStartIndex) {
        try {
            for (int n = varArgsStartIndex; n < arguments.length; n++) {
                Object argument = arguments[n];

                if (argument instanceof LLVMBoxedPrimitive) {
                    arguments[n] = ((LLVMBoxedPrimitive) argument).getValue();
                } else if (argument instanceof TruffleObject && notLLVM((TruffleObject) argument) && ForeignAccess.sendIsPointer(isPointer, (TruffleObject) argument)) {
                    arguments[n] = ForeignAccess.sendAsPointer(asPointer, (TruffleObject) argument);
                } else if (argument instanceof TruffleObject && notLLVM((TruffleObject) argument) && ForeignAccess.sendIsBoxed(isBoxed, (TruffleObject) argument)) {
                    arguments[n] = ForeignAccess.sendUnbox(unbox, (TruffleObject) argument);
                } else if (argument instanceof TruffleObject && notLLVM((TruffleObject) argument)) {
                    TruffleObject nativeObject = (TruffleObject) ForeignAccess.sendToNative(toNative, (TruffleObject) argument);
                    arguments[n] = ForeignAccess.sendAsPointer(asPointer, nativeObject);
                }
            }
        } catch (UnsupportedMessageException e) {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError(e);
        }
    }

    @Child private LLVMGlobalVariableAccess globalAccess = createGlobalAccess();

    private int storeArgument(long currentPtr, Object object) {
        if (object instanceof Number || object instanceof LLVM80BitFloat) {
            return doPrimitiveWrite(currentPtr, object);
        } else if (object instanceof LLVMVarArgCompoundValue) {
            LLVMVarArgCompoundValue obj = (LLVMVarArgCompoundValue) object;
            profiledMemMove.memmove(LLVMAddress.fromLong(currentPtr), LLVMAddress.fromLong(obj.getAddr()), obj.getSize());
            return obj.getSize();
        } else if (object instanceof LLVMAddress) {
            LLVMMemory.putAddress(currentPtr, (LLVMAddress) object);
            return X86_64BitVarArgs.STACK_STEP;
        } else if (object instanceof LLVMGlobalVariable) {
            LLVMMemory.putAddress(currentPtr, globalAccess.getNativeLocation(((LLVMGlobalVariable) object)));
            return X86_64BitVarArgs.STACK_STEP;
        } else if (object instanceof LLVMFloatVector) {
            return doVectorWrite(currentPtr, object);
        } else {
            throw new AssertionError(object);
        }
    }

    private static int doPrimitiveWrite(long currentPtr, Object arg) throws AssertionError {
        if (arg instanceof Boolean) {
            LLVMMemory.putI1(currentPtr, (boolean) arg);
            return X86_64BitVarArgs.STACK_STEP;
        } else if (arg instanceof Byte) {
            LLVMMemory.putI8(currentPtr, (byte) arg);
            return X86_64BitVarArgs.STACK_STEP;
        } else if (arg instanceof Short) {
            LLVMMemory.putI16(currentPtr, (short) arg);
            return X86_64BitVarArgs.STACK_STEP;
        } else if (arg instanceof Integer) {
            LLVMMemory.putI32(currentPtr, (int) arg);
            return X86_64BitVarArgs.STACK_STEP;
        } else if (arg instanceof Long) {
            LLVMMemory.putI64(currentPtr, (long) arg);
            return X86_64BitVarArgs.STACK_STEP;
        } else if (arg instanceof Float) {
            LLVMMemory.putFloat(currentPtr, (float) arg);
            return X86_64BitVarArgs.STACK_STEP;
        } else if (arg instanceof Double) {
            LLVMMemory.putDouble(currentPtr, (double) arg);
            return X86_64BitVarArgs.STACK_STEP;
        } else if (arg instanceof LLVM80BitFloat) {
            LLVMMemory.put80BitFloat(currentPtr, (LLVM80BitFloat) arg);
            return LONG_DOUBLE_SIZE;
        } else {
            throw new AssertionError(arg);
        }
    }

    private static int doVectorWrite(long startPtr, Object object) throws AssertionError {
        if (object instanceof LLVMFloatVector) {
            final LLVMFloatVector floatVec = (LLVMFloatVector) object;
            for (int i = 0; i < floatVec.getLength(); i++) {
                doPrimitiveWrite(startPtr + i * Float.BYTES, floatVec.getValue(i));
            }
            return floatVec.getLength() * Float.BYTES;
        } else {
            throw new AssertionError(object);
        }
    }

}
