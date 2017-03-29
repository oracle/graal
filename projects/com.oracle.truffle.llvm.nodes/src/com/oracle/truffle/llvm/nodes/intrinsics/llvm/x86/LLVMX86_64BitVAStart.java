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
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.llvm.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.nodes.func.LLVMCallNode;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMGlobalVariableDescriptor;
import com.oracle.truffle.llvm.runtime.floating.LLVM80BitFloat;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;
import com.oracle.truffle.llvm.runtime.memory.LLVMNativeFunctions.MallocNode;
import com.oracle.truffle.llvm.runtime.types.FunctionType;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.llvm.runtime.types.Type;

public class LLVMX86_64BitVAStart extends LLVMExpressionNode {

    private static final int LONG_DOUBLE_SIZE = 16;
    private final int numberOfExplicitArguments;
    @Child private LLVMExpressionNode target;
    @Child private MallocNode malloc;
    @Child private Node isBoxedNode = Message.IS_BOXED.createNode();
    @Child private Node unboxNode = Message.UNBOX.createNode();

    public LLVMX86_64BitVAStart(int numberOfExplicitArguments, LLVMExpressionNode target) {
        if (numberOfExplicitArguments < 0) {
            throw new AssertionError();
        }
        this.numberOfExplicitArguments = numberOfExplicitArguments;
        this.target = target;
    }

    public MallocNode getMalloc() {
        if (malloc == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            this.malloc = insert(getContext().getNativeFunctions().createMallocNode());
        }
        return malloc;
    }

    private enum VarArgArea {
        GP_AREA,
        FP_AREA,
        OVERFLOW_AREA;
    }

    private static VarArgArea getVarArgArea(Type type) {
        if (Type.isIntegerType(type) || type instanceof PointerType || type instanceof FunctionType) {
            return VarArgArea.GP_AREA;
        } else if (type == PrimitiveType.FLOAT || type == PrimitiveType.DOUBLE) {
            return VarArgArea.FP_AREA;
        } else if (type == PrimitiveType.X86_FP80) {
            return VarArgArea.OVERFLOW_AREA;
        } else {
            throw new AssertionError(type);
        }
    }

    // FIXME: specialization (pass long values in function calls like TruffleC?)
    @Override
    public Object executeGeneric(VirtualFrame frame) {
        LLVMAddress address = target.enforceLLVMAddress(frame);
        initOffsets(address);
        int varArgsStartIndex = numberOfExplicitArguments;
        Object[] realArguments = getRealArguments(frame);
        unboxArguments(realArguments);
        int argumentsLength = realArguments.length;
        if (varArgsStartIndex != argumentsLength) {
            int nrVarArgs = argumentsLength - varArgsStartIndex;
            Type[] types = getTypes(realArguments, varArgsStartIndex);
            int gpOffset = 0;
            int fpOffset = X86_64BitVarArgs.MAX_GP_OFFSET;
            int overFlowOffset = 0;
            int size = getSize(types);
            LLVMAddress savedRegs = getMalloc().execute(size);
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
                        if (types[i] != PrimitiveType.X86_FP80) {
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

    private void unboxArguments(Object[] arguments) {
        for (int n = 0; n < arguments.length; n++) {
            Object argument = arguments[n];
            if (argument instanceof TruffleObject && ForeignAccess.sendIsBoxed(isBoxedNode, (TruffleObject) argument)) {
                try {
                    arguments[n] = ForeignAccess.sendUnbox(unboxNode, (TruffleObject) argument);
                } catch (UnsupportedMessageException e) {
                    throw new AssertionError(e);
                }
            }
        }
    }

    static int getSize(Type[] types) {
        return X86_64BitVarArgs.MAX_FP_OFFSET + getOverFlowSize(types);
    }

    private static int getOverFlowSize(Type[] types) {
        int overFlowSize = 0;
        int remainingFpArea = X86_64BitVarArgs.MAX_FP_OFFSET - X86_64BitVarArgs.MAX_GP_OFFSET;
        int remainingGpArea = X86_64BitVarArgs.MAX_GP_OFFSET;
        for (Type type : types) {
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
                    if (type != PrimitiveType.X86_FP80) {
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

    static Type[] getTypes(Object[] arguments, int varArgsStartIndex) {
        Object firstArgument = arguments[varArgsStartIndex];
        Type[] types = new Type[arguments.length - varArgsStartIndex];
        getArgumentType(firstArgument);
        for (int i = varArgsStartIndex, j = 0; i < arguments.length; i++, j++) {
            types[j] = getArgumentType(arguments[i]);
        }
        return types;
    }

    private static Type getArgumentType(Object arg) {
        Type type;
        if (arg instanceof Boolean) {
            type = PrimitiveType.I1;
        } else if (arg instanceof Byte) {
            type = PrimitiveType.I8;
        } else if (arg instanceof Short) {
            type = PrimitiveType.I16;
        } else if (arg instanceof Integer) {
            type = PrimitiveType.I32;
        } else if (arg instanceof Long) {
            type = PrimitiveType.I64;
        } else if (arg instanceof Float) {
            type = PrimitiveType.FLOAT;
        } else if (arg instanceof Double) {
            type = PrimitiveType.DOUBLE;
        } else if (arg instanceof LLVMAddress || arg instanceof LLVMGlobalVariableDescriptor) {
            type = new PointerType(null);
        } else if (arg instanceof LLVM80BitFloat) {
            type = PrimitiveType.X86_FP80;
        } else {
            throw new AssertionError(arg);
        }
        return type;
    }

    @ExplodeLoop
    void allocateOverflowArgArea(Type type, VirtualFrame frame, LLVMAddress address, int varArgsStartIndex, int nrVarArgs, int typeLength, final int nrVarArgsInRegisterArea) {
        if (nrVarArgsInRegisterArea != nrVarArgs) {
            final int remainingVarArgs = nrVarArgs - nrVarArgsInRegisterArea;
            LLVMAddress stackArea = getMalloc().execute(typeLength * remainingVarArgs);
            LLVMMemory.putAddress(address.increment(X86_64BitVarArgs.OVERFLOW_ARG_AREA), stackArea);
            LLVMAddress currentAddress = stackArea;
            for (int i = nrVarArgsInRegisterArea; i < nrVarArgs; i++) {
                storeArgument(type, currentAddress, getRealArguments(frame)[i + varArgsStartIndex]);
                currentAddress = currentAddress.increment(typeLength);
            }
        }
    }

    private static void storeArgument(Type type, LLVMAddress currentAddress, Object object) {
        if (type instanceof PrimitiveType) {
            doPrimitiveWrite(type, currentAddress, object);
        } else if (type instanceof PointerType && object instanceof LLVMAddress) {
            LLVMMemory.putAddress(currentAddress, (LLVMAddress) object);
        } else if (type instanceof PointerType && object instanceof LLVMGlobalVariableDescriptor) {
            LLVMMemory.putAddress(currentAddress, ((LLVMGlobalVariableDescriptor) object).getNativeAddress());
        } else {
            throw new AssertionError(type);
        }
    }

    private static void doPrimitiveWrite(Type type, LLVMAddress currentAddress, Object object) throws AssertionError {
        switch (((PrimitiveType) type).getPrimitiveKind()) {
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
            default:
                throw new AssertionError(type);
        }
    }

    private static void initOffsets(LLVMAddress address) {
        LLVMMemory.putI32(address.increment(X86_64BitVarArgs.GP_OFFSET), 0);
        LLVMMemory.putI32(address.increment(X86_64BitVarArgs.FP_OFFSET), X86_64BitVarArgs.MAX_GP_OFFSET);
    }

}
