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

import org.eclipse.emf.ecore.EObject;

import com.oracle.truffle.llvm.nodes.base.LLVMExpressionNode;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMAddressNode;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMContext;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMFunctionNode;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMLanguage;
import com.oracle.truffle.llvm.nodes.impl.base.floating.LLVM80BitFloatNode;
import com.oracle.truffle.llvm.nodes.impl.base.floating.LLVMDoubleNode;
import com.oracle.truffle.llvm.nodes.impl.base.floating.LLVMFloatNode;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI16Node;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI1Node;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI32Node;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI64Node;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI8Node;
import com.oracle.truffle.llvm.nodes.impl.literals.LLVMFunctionLiteralNodeGen;
import com.oracle.truffle.llvm.nodes.impl.literals.LLVMSimpleLiteralNode.LLVM80BitFloatLiteralNode;
import com.oracle.truffle.llvm.nodes.impl.literals.LLVMSimpleLiteralNode.LLVMAddressLiteralNode;
import com.oracle.truffle.llvm.nodes.impl.literals.LLVMSimpleLiteralNode.LLVMDoubleLiteralNode;
import com.oracle.truffle.llvm.nodes.impl.literals.LLVMSimpleLiteralNode.LLVMFloatLiteralNode;
import com.oracle.truffle.llvm.nodes.impl.literals.LLVMSimpleLiteralNode.LLVMI16LiteralNode;
import com.oracle.truffle.llvm.nodes.impl.literals.LLVMSimpleLiteralNode.LLVMI1LiteralNode;
import com.oracle.truffle.llvm.nodes.impl.literals.LLVMSimpleLiteralNode.LLVMI32LiteralNode;
import com.oracle.truffle.llvm.nodes.impl.literals.LLVMSimpleLiteralNode.LLVMI64LiteralNode;
import com.oracle.truffle.llvm.nodes.impl.literals.LLVMSimpleLiteralNode.LLVMI8LiteralNode;
import com.oracle.truffle.llvm.nodes.impl.literals.LLVMSimpleLiteralNode.LLVMIVarBitLiteralNode;
import com.oracle.truffle.llvm.nodes.impl.literals.LLVMVectorLiteralNodeFactory.LLVMVectorDoubleLiteralNodeGen;
import com.oracle.truffle.llvm.nodes.impl.literals.LLVMVectorLiteralNodeFactory.LLVMVectorFloatLiteralNodeGen;
import com.oracle.truffle.llvm.nodes.impl.literals.LLVMVectorLiteralNodeFactory.LLVMVectorI16LiteralNodeGen;
import com.oracle.truffle.llvm.nodes.impl.literals.LLVMVectorLiteralNodeFactory.LLVMVectorI1LiteralNodeGen;
import com.oracle.truffle.llvm.nodes.impl.literals.LLVMVectorLiteralNodeFactory.LLVMVectorI32LiteralNodeGen;
import com.oracle.truffle.llvm.nodes.impl.literals.LLVMVectorLiteralNodeFactory.LLVMVectorI64LiteralNodeGen;
import com.oracle.truffle.llvm.nodes.impl.literals.LLVMVectorLiteralNodeFactory.LLVMVectorI8LiteralNodeGen;
import com.oracle.truffle.llvm.nodes.impl.memory.LLVMStoreNodeFactory.LLVM80BitFloatArrayLiteralNodeGen;
import com.oracle.truffle.llvm.nodes.impl.memory.LLVMStoreNodeFactory.LLVMAddressArrayCopyNodeGen;
import com.oracle.truffle.llvm.nodes.impl.memory.LLVMStoreNodeFactory.LLVMAddressArrayLiteralNodeGen;
import com.oracle.truffle.llvm.nodes.impl.memory.LLVMStoreNodeFactory.LLVMDoubleArrayLiteralNodeGen;
import com.oracle.truffle.llvm.nodes.impl.memory.LLVMStoreNodeFactory.LLVMFloatArrayLiteralNodeGen;
import com.oracle.truffle.llvm.nodes.impl.memory.LLVMStoreNodeFactory.LLVMFunctionArrayLiteralNodeGen;
import com.oracle.truffle.llvm.nodes.impl.memory.LLVMStoreNodeFactory.LLVMI16ArrayLiteralNodeGen;
import com.oracle.truffle.llvm.nodes.impl.memory.LLVMStoreNodeFactory.LLVMI32ArrayLiteralNodeGen;
import com.oracle.truffle.llvm.nodes.impl.memory.LLVMStoreNodeFactory.LLVMI64ArrayLiteralNodeGen;
import com.oracle.truffle.llvm.nodes.impl.memory.LLVMStoreNodeFactory.LLVMI8ArrayLiteralNodeGen;
import com.oracle.truffle.llvm.nodes.impl.others.LLVMAccessGlobalVariableStorageNodeGen;
import com.oracle.truffle.llvm.parser.LLVMBaseType;
import com.oracle.truffle.llvm.parser.LLVMParserRuntime;
import com.oracle.truffle.llvm.parser.base.model.LLVMToBitcodeAdapter;
import com.oracle.truffle.llvm.parser.base.model.types.ArrayType;
import com.oracle.truffle.llvm.parser.base.model.types.Type;
import com.oracle.truffle.llvm.parser.util.LLVMTypeHelper;
import com.oracle.truffle.llvm.types.LLVMAddress;
import com.oracle.truffle.llvm.types.LLVMFunction;
import com.oracle.truffle.llvm.types.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.types.LLVMFunctionDescriptor.LLVMRuntimeType;
import com.oracle.truffle.llvm.types.LLVMGlobalVariableDescriptor;
import com.oracle.truffle.llvm.types.LLVMIVarBit;
import com.oracle.truffle.llvm.types.floating.LLVM80BitFloat;

public final class LLVMLiteralFactory {

    public static final int HEX_BASE = 16;
    private static final String HEX_VALUE_PREFIX = "0x";

    private LLVMLiteralFactory() {
    }

    public static LLVMExpressionNode createUndefinedValue(LLVMParserRuntime runtime, EObject t) {
        Type resolvedType = LLVMToBitcodeAdapter.resolveType(runtime.resolve(t));
        LLVMBaseType type = resolvedType.getLLVMBaseType();
        if (LLVMTypeHelper.isVectorType(type)) {
            LLVMAddressLiteralNode addr = new LLVMAddressLiteralNode(LLVMAddress.createUndefinedAddress());
            switch (type) {
                case I1_VECTOR:
                    return LLVMVectorI1LiteralNodeGen.create(new LLVMI1Node[0], addr);
                case I8_VECTOR:
                    return LLVMVectorI8LiteralNodeGen.create(new LLVMI8Node[0], addr);
                case I16_VECTOR:
                    return LLVMVectorI16LiteralNodeGen.create(new LLVMI16Node[0], addr);
                case I32_VECTOR:
                    return LLVMVectorI32LiteralNodeGen.create(new LLVMI32Node[0], addr);
                case I64_VECTOR:
                    return LLVMVectorI64LiteralNodeGen.create(new LLVMI64Node[0], addr);
                case FLOAT_VECTOR:
                    return LLVMVectorFloatLiteralNodeGen.create(new LLVMFloatNode[0], addr);
                case DOUBLE_VECTOR:
                    return LLVMVectorDoubleLiteralNodeGen.create(new LLVMDoubleNode[0], addr);
                default:
                    throw new AssertionError(type);
            }
        }
        switch (type) {
            case I_VAR_BITWIDTH:
                int byteSize = resolvedType.sizeof();
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
                LLVMFunction functionDescriptor = context.getFunctionRegistry().createFunctionDescriptor("<undefined function>", LLVMRuntimeType.ILLEGAL, new LLVMRuntimeType[0], false);
                return LLVMFunctionLiteralNodeGen.create((LLVMFunctionDescriptor) functionDescriptor);
            default:
                throw new AssertionError(type);
        }
    }

    public static LLVMExpressionNode createSimpleConstantNoArray(String stringValue, LLVMBaseType instructionType, Type type) {
        switch (instructionType) {
            case ARRAY:
                throw new AssertionError("construction of array is not supported!");
            case I1:
                return new LLVMI1LiteralNode(Boolean.parseBoolean(stringValue));
            case I8:
                return new LLVMI8LiteralNode(Byte.parseByte(stringValue));
            case I16:
                return new LLVMI16LiteralNode(Short.parseShort(stringValue));
            case I32:
                return new LLVMI32LiteralNode(Integer.parseInt(stringValue));
            case I_VAR_BITWIDTH:
                return new LLVMIVarBitLiteralNode(LLVMIVarBit.fromString(stringValue, type.getBits()));
            case FLOAT:
                if (stringValue.startsWith(HEX_VALUE_PREFIX)) {
                    long longBits = decodeHex(HEX_VALUE_PREFIX.length(), stringValue);
                    float intBitsToFloat = (float) Double.longBitsToDouble(longBits);
                    return new LLVMFloatLiteralNode(intBitsToFloat);
                } else {
                    return new LLVMFloatLiteralNode(Float.parseFloat(stringValue));
                }
            case DOUBLE:
                if (stringValue.startsWith(HEX_VALUE_PREFIX)) {
                    long longBits = decodeHex(HEX_VALUE_PREFIX.length(), stringValue);
                    double longBitsToDouble = Double.longBitsToDouble(longBits);
                    return new LLVMDoubleLiteralNode(longBitsToDouble);
                } else {
                    return new LLVMDoubleLiteralNode(Double.parseDouble(stringValue));
                }
            case X86_FP80:
                if (stringValue.startsWith("0xK")) {
                    return new LLVM80BitFloatLiteralNode(LLVM80BitFloat.fromString(stringValue.substring("0xK".length())));
                } else {
                    throw new AssertionError(stringValue);
                }
            case I64:
                long val = Long.decode(stringValue);
                return new LLVMI64LiteralNode(val);
            case ADDRESS:
                if (stringValue.equals("null")) {
                    return new LLVMAddressLiteralNode(LLVMAddress.fromLong(0));
                } else {
                    throw new AssertionError(stringValue);
                }
            case FUNCTION_ADDRESS:
                if (stringValue.equals("null")) {
                    LLVMContext context = LLVMLanguage.INSTANCE.findContext0(LLVMLanguage.INSTANCE.createFindContextNode0());
                    LLVMFunction functionDescriptor = context.getFunctionRegistry().createFunctionDescriptor("<zero function>", LLVMRuntimeType.ILLEGAL, new LLVMRuntimeType[0], false);
                    return LLVMFunctionLiteralNodeGen.create((LLVMFunctionDescriptor) functionDescriptor);
                } else {
                    throw new AssertionError(stringValue);
                }
            default:
                throw new AssertionError();
        }
    }

    private static long decodeHex(int prefixLength, String stringValue) {
        BigInteger bigInteger = new BigInteger(stringValue.substring(prefixLength), HEX_BASE);
        return bigInteger.longValue();
    }

    public static LLVMFunctionNode[] createFunctionLiteralNodes(int nrElements, LLVMFunctionDescriptor value) {
        LLVMFunctionNode[] functionZeroInits = new LLVMFunctionNode[nrElements];
        for (int i = 0; i < nrElements; i++) {
            functionZeroInits[i] = LLVMFunctionLiteralNodeGen.create(value);
        }
        return functionZeroInits;
    }

    public static LLVMAddressNode[] createPointerLiteralNodes(int nrElements, LLVMAddress value) {
        LLVMAddressNode[] pointerZeroInits = new LLVMAddressNode[nrElements];
        for (int i = 0; i < nrElements; i++) {
            pointerZeroInits[i] = new LLVMAddressLiteralNode(value);
        }
        return pointerZeroInits;
    }

    public static LLVMDoubleNode[] createDoubleLiteralNodes(int nrElements, double value) {
        LLVMDoubleNode[] doubleZeroInits = new LLVMDoubleNode[nrElements];
        for (int i = 0; i < nrElements; i++) {
            doubleZeroInits[i] = new LLVMDoubleLiteralNode(value);
        }
        return doubleZeroInits;
    }

    public static LLVMFloatNode[] createFloatLiteralNodes(int nrElements, float value) {
        LLVMFloatNode[] floatZeroInits = new LLVMFloatNode[nrElements];
        for (int i = 0; i < nrElements; i++) {
            floatZeroInits[i] = new LLVMFloatLiteralNode(value);
        }
        return floatZeroInits;
    }

    public static LLVMI64Node[] createI64LiteralNodes(int nrElements, long value) {
        LLVMI64Node[] i64ZeroInits = new LLVMI64Node[nrElements];
        for (int i = 0; i < nrElements; i++) {
            i64ZeroInits[i] = new LLVMI64LiteralNode(value);
        }
        return i64ZeroInits;
    }

    public static LLVMI32Node[] createI32LiteralNodes(int nrElements, int value) {
        LLVMI32Node[] i32ZeroInits = new LLVMI32Node[nrElements];
        for (int i = 0; i < nrElements; i++) {
            i32ZeroInits[i] = new LLVMI32LiteralNode(value);
        }
        return i32ZeroInits;
    }

    public static LLVMI16Node[] createI16LiteralNodes(int nrElements, short value) {
        LLVMI16Node[] i16ZeroInits = new LLVMI16Node[nrElements];
        for (int i = 0; i < nrElements; i++) {
            i16ZeroInits[i] = new LLVMI16LiteralNode(value);
        }
        return i16ZeroInits;
    }

    public static LLVMI8Node[] createI8LiteralNodes(int nrElements, byte value) {
        LLVMI8Node[] i8ZeroInits = new LLVMI8Node[nrElements];
        for (int i = 0; i < nrElements; i++) {
            i8ZeroInits[i] = new LLVMI8LiteralNode(value);
        }
        return i8ZeroInits;
    }

    public static LLVMI1Node[] createI1LiteralNodes(int nrElements, boolean value) {
        LLVMI1Node[] i1ZeroInits = new LLVMI1Node[nrElements];
        for (int i = 0; i < nrElements; i++) {
            i1ZeroInits[i] = new LLVMI1LiteralNode(value);
        }
        return i1ZeroInits;
    }

    public static LLVMExpressionNode createVectorLiteralNode(List<LLVMExpressionNode> listValues, LLVMAddressNode target, LLVMBaseType type) {
        switch (type) {
            case I1_VECTOR:
                LLVMI1Node[] i1Vals = listValues.stream().map(n -> (LLVMI1Node) n).toArray(LLVMI1Node[]::new);
                return LLVMVectorI1LiteralNodeGen.create(i1Vals, target);
            case I8_VECTOR:
                LLVMI8Node[] i8Vals = listValues.stream().map(n -> (LLVMI8Node) n).toArray(LLVMI8Node[]::new);
                return LLVMVectorI8LiteralNodeGen.create(i8Vals, target);
            case I16_VECTOR:
                LLVMI16Node[] i16Vals = listValues.stream().map(n -> (LLVMI16Node) n).toArray(LLVMI16Node[]::new);
                return LLVMVectorI16LiteralNodeGen.create(i16Vals, target);
            case I32_VECTOR:
                LLVMI32Node[] i32Vals = listValues.stream().map(n -> (LLVMI32Node) n).toArray(LLVMI32Node[]::new);
                return LLVMVectorI32LiteralNodeGen.create(i32Vals, target);
            case I64_VECTOR:
                LLVMI64Node[] i64Vals = listValues.stream().map(n -> (LLVMI64Node) n).toArray(LLVMI64Node[]::new);
                return LLVMVectorI64LiteralNodeGen.create(i64Vals, target);
            case FLOAT_VECTOR:
                LLVMFloatNode[] floatVals = listValues.stream().map(n -> (LLVMFloatNode) n).toArray(LLVMFloatNode[]::new);
                return LLVMVectorFloatLiteralNodeGen.create(floatVals, target);
            case DOUBLE_VECTOR:
                LLVMDoubleNode[] doubleVals = listValues.stream().map(n -> (LLVMDoubleNode) n).toArray(LLVMDoubleNode[]::new);
                return LLVMVectorDoubleLiteralNodeGen.create(doubleVals, target);
            default:
                throw new AssertionError();
        }
    }

    public static LLVMExpressionNode createZeroVectorInitializer(int nrElements, LLVMAddressNode target, LLVMBaseType llvmType) {
        switch (llvmType) {
            case I1_VECTOR:
                LLVMI1Node[] i1Vals = createI1LiteralNodes(nrElements, false);
                return LLVMVectorI1LiteralNodeGen.create(i1Vals, target);
            case I8_VECTOR:
                LLVMI8Node[] i8Vals = createI8LiteralNodes(nrElements, (byte) 0);
                return LLVMVectorI8LiteralNodeGen.create(i8Vals, target);
            case I16_VECTOR:
                LLVMI16Node[] i16Vals = createI16LiteralNodes(nrElements, (short) 0);
                return LLVMVectorI16LiteralNodeGen.create(i16Vals, target);
            case I32_VECTOR:
                LLVMI32Node[] i32Vals = createI32LiteralNodes(nrElements, 0);
                return LLVMVectorI32LiteralNodeGen.create(i32Vals, target);
            case I64_VECTOR:
                LLVMI64Node[] i64Vals = createI64LiteralNodes(nrElements, 0);
                return LLVMVectorI64LiteralNodeGen.create(i64Vals, target);
            case FLOAT_VECTOR:
                LLVMFloatNode[] floatVals = createFloatLiteralNodes(nrElements, 0.0f);
                return LLVMVectorFloatLiteralNodeGen.create(floatVals, target);
            case DOUBLE_VECTOR:
                LLVMDoubleNode[] doubleVals = createDoubleLiteralNodes(nrElements, 0.0f);
                return LLVMVectorDoubleLiteralNodeGen.create(doubleVals, target);
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
                    return LLVMAccessGlobalVariableStorageNodeGen.create((LLVMGlobalVariableDescriptor) value);
                } else {
                    throw new AssertionError(value.getClass());
                }
            case FUNCTION_ADDRESS:
                return LLVMFunctionLiteralNodeGen.create((LLVMFunctionDescriptor) value);
            default:
                throw new AssertionError(value + " " + type);
        }
    }

    public static LLVMAddressNode createArrayLiteral(LLVMParserRuntime runtime, List<LLVMExpressionNode> arrayValues, ArrayType arrayType) {
        int nrElements = arrayValues.size();
        Type elementType = arrayType.getElementType();
        LLVMBaseType llvmElementType = elementType.getLLVMBaseType();
        int baseTypeSize = elementType.sizeof();
        int size = nrElements * baseTypeSize;
        LLVMAddressNode arrayAlloc = (LLVMAddressNode) runtime.allocateFunctionLifetime(LLVMToBitcodeAdapter.unresolveType(arrayType), size,
                        runtime.getTypeHelper().getAlignmentByte(LLVMToBitcodeAdapter.unresolveType(arrayType)));
        int byteLength = elementType.sizeof();
        if (size == 0) {
            throw new AssertionError(llvmElementType + " has size of 0!");
        }
        switch (llvmElementType) {
            case I8:
                return LLVMI8ArrayLiteralNodeGen.create(arrayValues.toArray(new LLVMI8Node[nrElements]), byteLength, arrayAlloc);
            case I16:
                return LLVMI16ArrayLiteralNodeGen.create(arrayValues.toArray(new LLVMI16Node[nrElements]), byteLength, arrayAlloc);
            case I32:
                return LLVMI32ArrayLiteralNodeGen.create(arrayValues.toArray(new LLVMI32Node[nrElements]), byteLength, arrayAlloc);
            case I64:
                return LLVMI64ArrayLiteralNodeGen.create(arrayValues.toArray(new LLVMI64Node[nrElements]), byteLength, arrayAlloc);
            case FLOAT:
                return LLVMFloatArrayLiteralNodeGen.create(arrayValues.toArray(new LLVMFloatNode[nrElements]), byteLength, arrayAlloc);
            case DOUBLE:
                return LLVMDoubleArrayLiteralNodeGen.create(arrayValues.toArray(new LLVMDoubleNode[nrElements]), byteLength, arrayAlloc);
            case X86_FP80:
                return LLVM80BitFloatArrayLiteralNodeGen.create(arrayValues.toArray(new LLVM80BitFloatNode[nrElements]), byteLength, arrayAlloc);
            case ARRAY:
            case STRUCT:
                return LLVMAddressArrayCopyNodeGen.create(arrayValues.toArray(new LLVMAddressNode[nrElements]), baseTypeSize, arrayAlloc);
            case ADDRESS:
                return LLVMAddressArrayLiteralNodeGen.create(arrayValues.toArray(new LLVMAddressNode[nrElements]), baseTypeSize, arrayAlloc);
            case FUNCTION_ADDRESS:
                return LLVMFunctionArrayLiteralNodeGen.create(arrayValues.toArray(new LLVMFunctionNode[nrElements]), byteLength, arrayAlloc);
            default:
                throw new AssertionError(llvmElementType);
        }
    }

}
