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
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.llvm.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.nodes.func.LLVMCallNode;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor.LLVMRuntimeType;
import com.oracle.truffle.llvm.runtime.floating.LLVM80BitFloat;
import com.oracle.truffle.llvm.runtime.memory.LLVMHeapFunctions;
import com.oracle.truffle.llvm.runtime.memory.LLVMHeapFunctions.MallocNode;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;

public class LLVMX86_64BitVAStart extends LLVMExpressionNode {

    private static final int LONG_DOUBLE_SIZE = 16;
    private final int numberOfExplicitArguments;
    @Child private LLVMExpressionNode target;
    @Child private MallocNode malloc;

    public LLVMX86_64BitVAStart(LLVMHeapFunctions heapFunctions, int numberOfExplicitArguments, LLVMExpressionNode target) {
        if (numberOfExplicitArguments < 0) {
            throw new AssertionError();
        }
        this.numberOfExplicitArguments = numberOfExplicitArguments;
        this.target = target;
        this.malloc = heapFunctions.createMallocNode();
    }

    private enum VarArgArea {
        GP_AREA,
        FP_AREA,
        OVERFLOW_AREA;
    }

    private static VarArgArea getVarArgArea(LLVMRuntimeType type) {
        switch (type) {
            case I1:
            case I8:
            case I16:
            case I32:
            case I64:
            case I1_POINTER:
            case I8_POINTER:
            case I16_POINTER:
            case I32_POINTER:
            case I64_POINTER:
            case HALF_POINTER:
            case FLOAT_POINTER:
            case DOUBLE_POINTER:
            case ADDRESS:
                return VarArgArea.GP_AREA;
            case FLOAT:
            case DOUBLE:
                return VarArgArea.FP_AREA;
            case X86_FP80:
                return VarArgArea.OVERFLOW_AREA;
            default:
                throw new AssertionError(type);
        }
    }

    // FIXME: specialization (pass long values in function calls like TruffleC?)
    @Override
    public Object executeGeneric(VirtualFrame frame) {
        LLVMAddress address;
        try {
            address = target.executeLLVMAddress(frame);
        } catch (UnexpectedResultException e) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException(e);
        }
        initOffsets(address);
        int varArgsStartIndex = numberOfExplicitArguments;
        Object[] realArguments = getRealArguments(frame);
        int argumentsLength = realArguments.length;
        if (varArgsStartIndex != argumentsLength) {
            int nrVarArgs = argumentsLength - varArgsStartIndex;
            LLVMRuntimeType[] types = getTypes(realArguments, varArgsStartIndex);
            int gpOffset = 0;
            int fpOffset = X86_64BitVarArgs.MAX_GP_OFFSET;
            int overFlowOffset = 0;
            int size = getSize(types);
            LLVMAddress savedRegs = malloc.execute(size);
            LLVMMemory.putAddress(address.increment(X86_64BitVarArgs.REG_SAVE_AREA), savedRegs);
            LLVMAddress overflowArea = savedRegs.increment(X86_64BitVarArgs.MAX_FP_OFFSET);
            LLVMMemory.putAddress(address.increment(X86_64BitVarArgs.OVERFLOW_ARG_AREA), overflowArea);
            for (int i = 0; i < nrVarArgs; i++) {
                Object object = realArguments[varArgsStartIndex + i];
                LLVMAddress currentAddress;
                switch (getVarArgArea(types[i])) {
                    case GP_AREA:
                        if (gpOffset >= X86_64BitVarArgs.MAX_GP_OFFSET) {
                            currentAddress = overflowArea.increment(overFlowOffset);
                            overFlowOffset += X86_64BitVarArgs.TYPE_LENGTH;
                        } else {
                            currentAddress = savedRegs.increment(gpOffset);
                            gpOffset += X86_64BitVarArgs.TYPE_LENGTH;
                        }
                        break;
                    case FP_AREA:
                        if (fpOffset >= X86_64BitVarArgs.MAX_FP_OFFSET) {
                            currentAddress = overflowArea.increment(overFlowOffset);
                            overFlowOffset += X86_64BitVarArgs.TYPE_LENGTH;
                        } else {
                            currentAddress = savedRegs.increment(fpOffset);
                            fpOffset += X86_64BitVarArgs.TYPE_LENGTH;
                        }
                        break;
                    case OVERFLOW_AREA:
                        currentAddress = overflowArea.increment(overFlowOffset);
                        if (types[i] != LLVMRuntimeType.X86_FP80) {
                            throw new AssertionError();
                        }
                        overFlowOffset += LONG_DOUBLE_SIZE;
                        break;
                    default:
                        throw new AssertionError(types[i]);
                }
                storeArgument(types[i], currentAddress, object);
            }
        }
        return null;
    }

    private static Object[] getRealArguments(VirtualFrame frame) {
        Object[] arguments = frame.getArguments();
        Object[] newArguments = new Object[arguments.length - 1];
        System.arraycopy(arguments, LLVMCallNode.ARG_START_INDEX, newArguments, 0, newArguments.length);
        return newArguments;
    }

    static int getSize(LLVMRuntimeType[] types) {
        return X86_64BitVarArgs.MAX_FP_OFFSET + getOverFlowSize(types);
    }

    private static int getOverFlowSize(LLVMRuntimeType[] types) {
        int overFlowSize = 0;
        int remainingFpArea = X86_64BitVarArgs.MAX_FP_OFFSET - X86_64BitVarArgs.MAX_GP_OFFSET;
        int remainingGpArea = X86_64BitVarArgs.MAX_GP_OFFSET;
        for (LLVMRuntimeType type : types) {
            VarArgArea area = getVarArgArea(type);
            switch (area) {
                case FP_AREA:
                    if (remainingFpArea == 0) {
                        overFlowSize += X86_64BitVarArgs.TYPE_LENGTH;
                    } else {
                        remainingFpArea -= X86_64BitVarArgs.TYPE_LENGTH;
                    }
                    break;
                case GP_AREA:
                    if (remainingGpArea == 0) {
                        overFlowSize += X86_64BitVarArgs.TYPE_LENGTH;
                    } else {
                        remainingGpArea -= X86_64BitVarArgs.TYPE_LENGTH;
                    }
                    break;
                case OVERFLOW_AREA:
                    if (type != LLVMRuntimeType.X86_FP80) {
                        throw new AssertionError();
                    }
                    overFlowSize += LONG_DOUBLE_SIZE;
                    break;
                default:
                    throw new AssertionError();
            }
        }
        return overFlowSize;
    }

    static LLVMRuntimeType[] getTypes(Object[] arguments, int varArgsStartIndex) {
        Object firstArgument = arguments[varArgsStartIndex];
        LLVMRuntimeType[] types = new LLVMRuntimeType[arguments.length - varArgsStartIndex];
        getArgumentType(firstArgument);
        for (int i = varArgsStartIndex, j = 0; i < arguments.length; i++, j++) {
            types[j] = getArgumentType(arguments[i]);
        }
        return types;
    }

    private static LLVMRuntimeType getArgumentType(Object arg) {
        LLVMRuntimeType type;
        if (arg instanceof Boolean) {
            type = LLVMRuntimeType.I1;
        } else if (arg instanceof Byte) {
            type = LLVMRuntimeType.I8;
        } else if (arg instanceof Short) {
            type = LLVMRuntimeType.I16;
        } else if (arg instanceof Integer) {
            type = LLVMRuntimeType.I32;
        } else if (arg instanceof Long) {
            type = LLVMRuntimeType.I64;
        } else if (arg instanceof Float) {
            type = LLVMRuntimeType.FLOAT;
        } else if (arg instanceof Double) {
            type = LLVMRuntimeType.DOUBLE;
        } else if (arg instanceof LLVMAddress) {
            type = LLVMRuntimeType.ADDRESS;
        } else if (arg instanceof LLVM80BitFloat) {
            type = LLVMRuntimeType.X86_FP80;
        } else {
            throw new AssertionError(arg);
        }
        return type;
    }

    @ExplodeLoop
    void allocateOverflowArgArea(LLVMRuntimeType type, VirtualFrame frame, LLVMAddress address, int varArgsStartIndex, int nrVarArgs, int typeLength, final int nrVarArgsInRegisterArea) {
        if (nrVarArgsInRegisterArea != nrVarArgs) {
            final int remainingVarArgs = nrVarArgs - nrVarArgsInRegisterArea;
            LLVMAddress stackArea = malloc.execute(typeLength * remainingVarArgs);
            LLVMMemory.putAddress(address.increment(X86_64BitVarArgs.OVERFLOW_ARG_AREA), stackArea);
            LLVMAddress currentAddress = stackArea;
            for (int i = nrVarArgsInRegisterArea; i < nrVarArgs; i++) {
                storeArgument(type, currentAddress, getRealArguments(frame)[i + varArgsStartIndex]);
                currentAddress = currentAddress.increment(typeLength);
            }
        }
    }

    private static void storeArgument(LLVMRuntimeType type, LLVMAddress currentAddress, Object object) {
        switch (type) {
            case I1:
                LLVMMemory.putI1(currentAddress, (boolean) object);
                break;
            case I8:
                LLVMMemory.putI8(currentAddress, (byte) object);
                break;
            case I16:
                LLVMMemory.putI16(currentAddress, (short) object);
                break;
            case I32:
                LLVMMemory.putI32(currentAddress, (int) object);
                break;
            case I64:
                LLVMMemory.putI64(currentAddress, (long) object);
                break;
            case FLOAT:
                LLVMMemory.putFloat(currentAddress, (float) object);
                break;
            case DOUBLE:
                LLVMMemory.putDouble(currentAddress, (double) object);
                break;
            case X86_FP80:
                LLVMMemory.put80BitFloat(currentAddress, (LLVM80BitFloat) object);
                break;
            case I1_POINTER:
            case I8_POINTER:
            case I16_POINTER:
            case I32_POINTER:
            case I64_POINTER:
            case HALF_POINTER:
            case FLOAT_POINTER:
            case DOUBLE_POINTER:
            case ADDRESS:
                LLVMMemory.putAddress(currentAddress, (LLVMAddress) object);
                break;
            default:
                throw new AssertionError(type);
        }
    }

    private static void initOffsets(LLVMAddress address) {
        LLVMMemory.putI32(address.increment(X86_64BitVarArgs.GP_OFFSET), 0);
        LLVMMemory.putI32(address.increment(X86_64BitVarArgs.FP_OFFSET), X86_64BitVarArgs.MAX_GP_OFFSET);
    }

}
