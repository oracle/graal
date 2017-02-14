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
package com.oracle.truffle.llvm.parser.factories;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

import com.oracle.truffle.llvm.context.LLVMContext;
import com.oracle.truffle.llvm.context.LLVMLanguage;
import com.oracle.truffle.llvm.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.nodes.literals.LLVMFunctionLiteralNodeGen;
import com.oracle.truffle.llvm.nodes.literals.LLVMSimpleLiteralNode.LLVM80BitFloatLiteralNode;
import com.oracle.truffle.llvm.nodes.literals.LLVMSimpleLiteralNode.LLVMAddressLiteralNode;
import com.oracle.truffle.llvm.nodes.literals.LLVMSimpleLiteralNode.LLVMDoubleLiteralNode;
import com.oracle.truffle.llvm.nodes.literals.LLVMSimpleLiteralNode.LLVMFloatLiteralNode;
import com.oracle.truffle.llvm.nodes.literals.LLVMSimpleLiteralNode.LLVMI16LiteralNode;
import com.oracle.truffle.llvm.nodes.literals.LLVMSimpleLiteralNode.LLVMI1LiteralNode;
import com.oracle.truffle.llvm.nodes.literals.LLVMSimpleLiteralNode.LLVMI32LiteralNode;
import com.oracle.truffle.llvm.nodes.literals.LLVMSimpleLiteralNode.LLVMI64LiteralNode;
import com.oracle.truffle.llvm.nodes.literals.LLVMSimpleLiteralNode.LLVMI8LiteralNode;
import com.oracle.truffle.llvm.nodes.literals.LLVMSimpleLiteralNode.LLVMIVarBitLiteralNode;
import com.oracle.truffle.llvm.nodes.literals.LLVMVectorLiteralNodeFactory.LLVMVectorDoubleLiteralNodeGen;
import com.oracle.truffle.llvm.nodes.literals.LLVMVectorLiteralNodeFactory.LLVMVectorFloatLiteralNodeGen;
import com.oracle.truffle.llvm.nodes.literals.LLVMVectorLiteralNodeFactory.LLVMVectorI16LiteralNodeGen;
import com.oracle.truffle.llvm.nodes.literals.LLVMVectorLiteralNodeFactory.LLVMVectorI1LiteralNodeGen;
import com.oracle.truffle.llvm.nodes.literals.LLVMVectorLiteralNodeFactory.LLVMVectorI32LiteralNodeGen;
import com.oracle.truffle.llvm.nodes.literals.LLVMVectorLiteralNodeFactory.LLVMVectorI64LiteralNodeGen;
import com.oracle.truffle.llvm.nodes.literals.LLVMVectorLiteralNodeFactory.LLVMVectorI8LiteralNodeGen;
import com.oracle.truffle.llvm.nodes.memory.LLVMStoreNodeFactory.LLVM80BitFloatArrayLiteralNodeGen;
import com.oracle.truffle.llvm.nodes.memory.LLVMStoreNodeFactory.LLVMAddressArrayCopyNodeGen;
import com.oracle.truffle.llvm.nodes.memory.LLVMStoreNodeFactory.LLVMAddressArrayLiteralNodeGen;
import com.oracle.truffle.llvm.nodes.memory.LLVMStoreNodeFactory.LLVMDoubleArrayLiteralNodeGen;
import com.oracle.truffle.llvm.nodes.memory.LLVMStoreNodeFactory.LLVMFloatArrayLiteralNodeGen;
import com.oracle.truffle.llvm.nodes.memory.LLVMStoreNodeFactory.LLVMFunctionArrayLiteralNodeGen;
import com.oracle.truffle.llvm.nodes.memory.LLVMStoreNodeFactory.LLVMI16ArrayLiteralNodeGen;
import com.oracle.truffle.llvm.nodes.memory.LLVMStoreNodeFactory.LLVMI32ArrayLiteralNodeGen;
import com.oracle.truffle.llvm.nodes.memory.LLVMStoreNodeFactory.LLVMI64ArrayLiteralNodeGen;
import com.oracle.truffle.llvm.nodes.memory.LLVMStoreNodeFactory.LLVMI8ArrayLiteralNodeGen;
import com.oracle.truffle.llvm.nodes.others.LLVMAccessGlobalVariableStorageNode;
import com.oracle.truffle.llvm.parser.api.util.LLVMParserRuntime;
import com.oracle.truffle.llvm.parser.api.util.LLVMTypeHelper;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor.LLVMRuntimeType;
import com.oracle.truffle.llvm.runtime.LLVMGlobalVariableDescriptor;
import com.oracle.truffle.llvm.runtime.LLVMIVarBit;
import com.oracle.truffle.llvm.runtime.floating.LLVM80BitFloat;
import com.oracle.truffle.llvm.runtime.types.ArrayType;
import com.oracle.truffle.llvm.runtime.types.LLVMBaseType;
import com.oracle.truffle.llvm.runtime.types.Type;

public final class LLVMLiteralFactory {

    private LLVMLiteralFactory() {
    }

    public static LLVMExpressionNode createUndefinedValue(LLVMParserRuntime runtime, Type resolvedType) {
        LLVMBaseType type = resolvedType.getLLVMBaseType();
        if (LLVMTypeHelper.isVectorType(type)) {
            switch (type) {
                case I1_VECTOR:
                    return LLVMVectorI1LiteralNodeGen.create(new LLVMExpressionNode[0]);
                case I8_VECTOR:
                    return LLVMVectorI8LiteralNodeGen.create(new LLVMExpressionNode[0]);
                case I16_VECTOR:
                    return LLVMVectorI16LiteralNodeGen.create(new LLVMExpressionNode[0]);
                case I32_VECTOR:
                    return LLVMVectorI32LiteralNodeGen.create(new LLVMExpressionNode[0]);
                case I64_VECTOR:
                    return LLVMVectorI64LiteralNodeGen.create(new LLVMExpressionNode[0]);
                case FLOAT_VECTOR:
                    return LLVMVectorFloatLiteralNodeGen.create(new LLVMExpressionNode[0]);
                case DOUBLE_VECTOR:
                    return LLVMVectorDoubleLiteralNodeGen.create(new LLVMExpressionNode[0]);
                default:
                    throw new AssertionError(type);
            }
        }
        switch (type) {
            case I_VAR_BITWIDTH:
                int byteSize = runtime.getByteSize(resolvedType);
                byte[] loadedBytes = new byte[byteSize];
                Arrays.fill(loadedBytes, (byte) -1);
                return new LLVMIVarBitLiteralNode(LLVMIVarBit.create(byteSize * Byte.SIZE, loadedBytes));
            case I1:
                return new LLVMI1LiteralNode(false);
            case I8:
                return new LLVMI8LiteralNode((byte) -1);
            case I16:
                return new LLVMI16LiteralNode((short) -1);
            case I32:
                return new LLVMI32LiteralNode(-1);
            case I64:
                return new LLVMI64LiteralNode(-1);
            case FLOAT:
                return new LLVMFloatLiteralNode(-1);
            case DOUBLE:
                return new LLVMDoubleLiteralNode(-1);
            case ADDRESS:
                return new LLVMAddressLiteralNode(LLVMAddress.createUndefinedAddress());
            case FUNCTION_ADDRESS:
                LLVMContext context = LLVMLanguage.INSTANCE.findContext0(LLVMLanguage.INSTANCE.createFindContextNode0());
                LLVMFunctionDescriptor functionDescriptor = context.getFunctionRegistry().lookupFunctionDescriptor("<undefined function>", LLVMRuntimeType.ILLEGAL, new LLVMRuntimeType[0], false);
                return LLVMFunctionLiteralNodeGen.create(functionDescriptor);
            default:
                throw new AssertionError(type);
        }
    }

    public static LLVMExpressionNode createSimpleConstantNoArray(Object constant, LLVMBaseType instructionType, Type type) {
        switch (instructionType) {
            case ARRAY:
                throw new AssertionError("construction of array is not supported!");
            case I1:
                return new LLVMI1LiteralNode((boolean) constant);
            case I8:
                return new LLVMI8LiteralNode((byte) constant);
            case I16:
                return new LLVMI16LiteralNode((short) constant);
            case I32:
                return new LLVMI32LiteralNode((int) constant);
            case I_VAR_BITWIDTH:
                if (type.getBits() <= Long.SIZE) {
                    return new LLVMIVarBitLiteralNode(LLVMIVarBit.fromLong(type.getBits(), (long) constant));
                } else {
                    return new LLVMIVarBitLiteralNode(LLVMIVarBit.fromBigInteger(type.getBits(), (BigInteger) constant));
                }
            case FLOAT:
                return new LLVMFloatLiteralNode((float) constant);
            case DOUBLE:
                return new LLVMDoubleLiteralNode((double) constant);
            case X86_FP80:
                if (constant == null) {
                    return new LLVM80BitFloatLiteralNode(LLVM80BitFloat.fromLong(0));
                } else {
                    return new LLVM80BitFloatLiteralNode(LLVM80BitFloat.fromBytes((byte[]) constant));
                }
            case I64:
                return new LLVMI64LiteralNode((long) constant);
            case ADDRESS:
                if (constant == null) {
                    return new LLVMAddressLiteralNode(LLVMAddress.fromLong(0));
                } else {
                    throw new AssertionError("Not a Simple Constant: " + constant);
                }
            case FUNCTION_ADDRESS:
                if (constant == null) {
                    LLVMContext context = LLVMLanguage.INSTANCE.findContext0(LLVMLanguage.INSTANCE.createFindContextNode0());
                    LLVMFunctionDescriptor functionDescriptor = context.getFunctionRegistry().getZeroFunctionDescriptor();
                    return LLVMFunctionLiteralNodeGen.create(functionDescriptor);
                } else {
                    throw new AssertionError("Not a Simple Constant: " + constant);
                }
            default:
                throw new UnsupportedOperationException("Unsupported simple constant: " + constant);
        }
    }

    public static LLVMExpressionNode[] createFunctionLiteralNodes(int nrElements, LLVMFunctionDescriptor value) {
        LLVMExpressionNode[] functionZeroInits = new LLVMExpressionNode[nrElements];
        for (int i = 0; i < nrElements; i++) {
            functionZeroInits[i] = LLVMFunctionLiteralNodeGen.create(value);
        }
        return functionZeroInits;
    }

    public static LLVMExpressionNode[] createPointerLiteralNodes(int nrElements, LLVMAddress value) {
        LLVMExpressionNode[] pointerZeroInits = new LLVMExpressionNode[nrElements];
        for (int i = 0; i < nrElements; i++) {
            pointerZeroInits[i] = new LLVMAddressLiteralNode(value);
        }
        return pointerZeroInits;
    }

    public static LLVMExpressionNode[] createDoubleLiteralNodes(int nrElements, double value) {
        LLVMExpressionNode[] doubleZeroInits = new LLVMExpressionNode[nrElements];
        for (int i = 0; i < nrElements; i++) {
            doubleZeroInits[i] = new LLVMDoubleLiteralNode(value);
        }
        return doubleZeroInits;
    }

    public static LLVMExpressionNode[] createFloatLiteralNodes(int nrElements, float value) {
        LLVMExpressionNode[] floatZeroInits = new LLVMExpressionNode[nrElements];
        for (int i = 0; i < nrElements; i++) {
            floatZeroInits[i] = new LLVMFloatLiteralNode(value);
        }
        return floatZeroInits;
    }

    public static LLVMExpressionNode[] createI64LiteralNodes(int nrElements, long value) {
        LLVMExpressionNode[] i64ZeroInits = new LLVMExpressionNode[nrElements];
        for (int i = 0; i < nrElements; i++) {
            i64ZeroInits[i] = new LLVMI64LiteralNode(value);
        }
        return i64ZeroInits;
    }

    public static LLVMExpressionNode[] createI32LiteralNodes(int nrElements, int value) {
        LLVMExpressionNode[] i32ZeroInits = new LLVMExpressionNode[nrElements];
        for (int i = 0; i < nrElements; i++) {
            i32ZeroInits[i] = new LLVMI32LiteralNode(value);
        }
        return i32ZeroInits;
    }

    public static LLVMExpressionNode[] createI16LiteralNodes(int nrElements, short value) {
        LLVMExpressionNode[] i16ZeroInits = new LLVMExpressionNode[nrElements];
        for (int i = 0; i < nrElements; i++) {
            i16ZeroInits[i] = new LLVMI16LiteralNode(value);
        }
        return i16ZeroInits;
    }

    public static LLVMExpressionNode[] createI8LiteralNodes(int nrElements, byte value) {
        LLVMExpressionNode[] i8ZeroInits = new LLVMExpressionNode[nrElements];
        for (int i = 0; i < nrElements; i++) {
            i8ZeroInits[i] = new LLVMI8LiteralNode(value);
        }
        return i8ZeroInits;
    }

    public static LLVMExpressionNode[] createI1LiteralNodes(int nrElements, boolean value) {
        LLVMExpressionNode[] i1ZeroInits = new LLVMExpressionNode[nrElements];
        for (int i = 0; i < nrElements; i++) {
            i1ZeroInits[i] = new LLVMI1LiteralNode(value);
        }
        return i1ZeroInits;
    }

    public static LLVMExpressionNode createVectorLiteralNode(List<LLVMExpressionNode> listValues, LLVMBaseType type) {
        LLVMExpressionNode[] vals = listValues.toArray(new LLVMExpressionNode[listValues.size()]);
        switch (type) {
            case I1_VECTOR:
                return LLVMVectorI1LiteralNodeGen.create(vals);
            case I8_VECTOR:
                return LLVMVectorI8LiteralNodeGen.create(vals);
            case I16_VECTOR:
                return LLVMVectorI16LiteralNodeGen.create(vals);
            case I32_VECTOR:
                return LLVMVectorI32LiteralNodeGen.create(vals);
            case I64_VECTOR:
                return LLVMVectorI64LiteralNodeGen.create(vals);
            case FLOAT_VECTOR:
                return LLVMVectorFloatLiteralNodeGen.create(vals);
            case DOUBLE_VECTOR:
                return LLVMVectorDoubleLiteralNodeGen.create(vals);
            default:
                throw new AssertionError();
        }
    }

    public static LLVMExpressionNode createZeroVectorInitializer(int nrElements, LLVMBaseType llvmType) {
        switch (llvmType) {
            case I1_VECTOR:
                LLVMExpressionNode[] i1Vals = createI1LiteralNodes(nrElements, false);
                return LLVMVectorI1LiteralNodeGen.create(i1Vals);
            case I8_VECTOR:
                LLVMExpressionNode[] i8Vals = createI8LiteralNodes(nrElements, (byte) 0);
                return LLVMVectorI8LiteralNodeGen.create(i8Vals);
            case I16_VECTOR:
                LLVMExpressionNode[] i16Vals = createI16LiteralNodes(nrElements, (short) 0);
                return LLVMVectorI16LiteralNodeGen.create(i16Vals);
            case I32_VECTOR:
                LLVMExpressionNode[] i32Vals = createI32LiteralNodes(nrElements, 0);
                return LLVMVectorI32LiteralNodeGen.create(i32Vals);
            case I64_VECTOR:
                LLVMExpressionNode[] i64Vals = createI64LiteralNodes(nrElements, 0);
                return LLVMVectorI64LiteralNodeGen.create(i64Vals);
            case FLOAT_VECTOR:
                LLVMExpressionNode[] floatVals = createFloatLiteralNodes(nrElements, 0.0f);
                return LLVMVectorFloatLiteralNodeGen.create(floatVals);
            case DOUBLE_VECTOR:
                LLVMExpressionNode[] doubleVals = createDoubleLiteralNodes(nrElements, 0.0f);
                return LLVMVectorDoubleLiteralNodeGen.create(doubleVals);
            default:
                throw new AssertionError(llvmType);
        }
    }

    public static LLVMExpressionNode createLiteral(Object value, LLVMBaseType type) {
        switch (type) {
            case I1:
                return new LLVMI1LiteralNode((boolean) value);
            case I8:
                return new LLVMI8LiteralNode((byte) value);
            case I16:
                return new LLVMI16LiteralNode((short) value);
            case I32:
                return new LLVMI32LiteralNode((int) value);
            case I64:
                return new LLVMI64LiteralNode((long) value);
            case FLOAT:
                return new LLVMFloatLiteralNode((float) value);
            case DOUBLE:
                return new LLVMDoubleLiteralNode((double) value);
            case ADDRESS:
                if (value instanceof LLVMAddress) {
                    return new LLVMAddressLiteralNode((LLVMAddress) value);
                } else if (value instanceof LLVMGlobalVariableDescriptor) {
                    return new LLVMAccessGlobalVariableStorageNode((LLVMGlobalVariableDescriptor) value);
                } else {
                    throw new AssertionError(value.getClass());
                }
            case FUNCTION_ADDRESS:
                return LLVMFunctionLiteralNodeGen.create((LLVMFunctionDescriptor) value);
            default:
                throw new AssertionError(value + " " + type);
        }
    }

    public static LLVMExpressionNode createArrayLiteral(LLVMParserRuntime runtime, List<LLVMExpressionNode> arrayValues, ArrayType arrayType) {
        int nrElements = arrayValues.size();
        Type elementType = arrayType.getElementType();
        LLVMBaseType llvmElementType = elementType.getLLVMBaseType();
        int baseTypeSize = runtime.getByteSize(elementType);
        int size = nrElements * baseTypeSize;
        LLVMExpressionNode arrayAlloc = runtime.allocateFunctionLifetime(arrayType, size,
                        runtime.getByteAlignment(arrayType));
        int byteLength = runtime.getByteSize(elementType);
        if (size == 0) {
            throw new AssertionError(llvmElementType + " has size of 0!");
        }
        switch (llvmElementType) {
            case I8:
                return LLVMI8ArrayLiteralNodeGen.create(arrayValues.toArray(new LLVMExpressionNode[nrElements]), byteLength, arrayAlloc);
            case I16:
                return LLVMI16ArrayLiteralNodeGen.create(arrayValues.toArray(new LLVMExpressionNode[nrElements]), byteLength, arrayAlloc);
            case I32:
                return LLVMI32ArrayLiteralNodeGen.create(arrayValues.toArray(new LLVMExpressionNode[nrElements]), byteLength, arrayAlloc);
            case I64:
                return LLVMI64ArrayLiteralNodeGen.create(arrayValues.toArray(new LLVMExpressionNode[nrElements]), byteLength, arrayAlloc);
            case FLOAT:
                return LLVMFloatArrayLiteralNodeGen.create(arrayValues.toArray(new LLVMExpressionNode[nrElements]), byteLength, arrayAlloc);
            case DOUBLE:
                return LLVMDoubleArrayLiteralNodeGen.create(arrayValues.toArray(new LLVMExpressionNode[nrElements]), byteLength, arrayAlloc);
            case X86_FP80:
                return LLVM80BitFloatArrayLiteralNodeGen.create(arrayValues.toArray(new LLVMExpressionNode[nrElements]), byteLength, arrayAlloc);
            case ARRAY:
            case STRUCT:
                return LLVMAddressArrayCopyNodeGen.create(runtime.getHeapFunctions(), arrayValues.toArray(new LLVMExpressionNode[nrElements]), baseTypeSize, arrayAlloc);
            case ADDRESS:
                return LLVMAddressArrayLiteralNodeGen.create(arrayValues.toArray(new LLVMExpressionNode[nrElements]), baseTypeSize, arrayAlloc);
            case FUNCTION_ADDRESS:
                return LLVMFunctionArrayLiteralNodeGen.create(arrayValues.toArray(new LLVMExpressionNode[nrElements]), byteLength, arrayAlloc);
            default:
                throw new AssertionError(llvmElementType);
        }
    }

}
