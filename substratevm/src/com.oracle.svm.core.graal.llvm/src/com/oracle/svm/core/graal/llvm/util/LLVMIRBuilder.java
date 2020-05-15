/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.svm.core.graal.llvm.util;

import static com.oracle.svm.core.graal.llvm.util.LLVMUtils.FALSE;
import static com.oracle.svm.core.graal.llvm.util.LLVMUtils.TRUE;
import static com.oracle.svm.core.graal.llvm.util.LLVMUtils.dumpTypes;
import static com.oracle.svm.core.graal.llvm.util.LLVMUtils.dumpValues;
import static org.graalvm.compiler.debug.GraalError.shouldNotReachHere;

import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.compiler.core.common.calc.Condition;

import com.oracle.svm.core.FrameAccess;
import com.oracle.svm.shadowed.org.bytedeco.javacpp.BytePointer;
import com.oracle.svm.shadowed.org.bytedeco.javacpp.Pointer;
import com.oracle.svm.shadowed.org.bytedeco.javacpp.PointerPointer;
import com.oracle.svm.shadowed.org.bytedeco.llvm.LLVM.LLVMAttributeRef;
import com.oracle.svm.shadowed.org.bytedeco.llvm.LLVM.LLVMBasicBlockRef;
import com.oracle.svm.shadowed.org.bytedeco.llvm.LLVM.LLVMBuilderRef;
import com.oracle.svm.shadowed.org.bytedeco.llvm.LLVM.LLVMContextRef;
import com.oracle.svm.shadowed.org.bytedeco.llvm.LLVM.LLVMMemoryBufferRef;
import com.oracle.svm.shadowed.org.bytedeco.llvm.LLVM.LLVMModuleRef;
import com.oracle.svm.shadowed.org.bytedeco.llvm.LLVM.LLVMTypeRef;
import com.oracle.svm.shadowed.org.bytedeco.llvm.LLVM.LLVMValueRef;
import com.oracle.svm.shadowed.org.bytedeco.llvm.global.LLVM;

public class LLVMIRBuilder implements AutoCloseable {
    private static final String DEFAULT_INSTR_NAME = "";

    private LLVMContextRef context;
    private LLVMBuilderRef builder;
    private LLVMModuleRef module;
    private LLVMValueRef function;

    private boolean primary;
    private LLVMHelperFunctions helpers;

    public LLVMIRBuilder(String name) {
        this.context = LLVM.LLVMContextCreate();
        this.builder = LLVM.LLVMCreateBuilderInContext(context);
        this.module = LLVM.LLVMModuleCreateWithNameInContext(name, context);
        this.primary = true;
        this.helpers = new LLVMHelperFunctions(this);
    }

    public LLVMIRBuilder(LLVMIRBuilder primary) {
        this.context = primary.context;
        this.builder = LLVM.LLVMCreateBuilderInContext(context);
        this.module = primary.module;
        this.function = null;
        this.primary = false;
        this.helpers = null;
    }

    public void setMainFunction(String functionName, LLVMTypeRef functionType) {
        assert function == null;
        this.function = addFunction(functionName, functionType);
    }

    /* Module */

    public byte[] getBitcode() {
        LLVMMemoryBufferRef buffer = LLVM.LLVMWriteBitcodeToMemoryBuffer(module);
        BytePointer start = LLVM.LLVMGetBufferStart(buffer);
        int size = NumUtil.safeToInt(LLVM.LLVMGetBufferSize(buffer));

        byte[] bitcode = new byte[size];
        start.get(bitcode, 0, size);
        LLVM.LLVMDisposeMemoryBuffer(buffer);
        return bitcode;
    }

    public boolean verifyBitcode() {
        if (LLVM.LLVMVerifyModule(module, LLVM.LLVMPrintMessageAction, new BytePointer((Pointer) null)) == TRUE) {
            LLVM.LLVMDumpModule(module);
            return false;
        }
        return true;
    }

    @Override
    public void close() {
        LLVM.LLVMDisposeBuilder(builder);
        builder = null;
        if (primary) {
            LLVM.LLVMDisposeModule(module);
            module = null;
            LLVM.LLVMContextDispose(context);
            context = null;
        }
    }

    /* Functions */

    public LLVMValueRef addFunction(String name, LLVMTypeRef type) {
        return LLVM.LLVMAddFunction(module, name, type);
    }

    public LLVMValueRef getFunction(String name, LLVMTypeRef type) {
        LLVMValueRef func = getNamedFunction(name);
        if (func == null) {
            func = addFunction(name, type);
            setLinkage(func, LinkageType.External);
        }
        return func;
    }

    public LLVMValueRef getNamedFunction(String name) {
        return LLVM.LLVMGetNamedFunction(module, name);
    }

    public void addAlias(String alias) {
        LLVM.LLVMAddAlias(module, LLVM.LLVMTypeOf(function), function, alias);
    }

    public void setFunctionLinkage(LinkageType linkage) {
        setLinkage(function, linkage);
    }

    public static void setLinkage(LLVMValueRef global, LinkageType linkage) {
        LLVM.LLVMSetLinkage(global, linkage.code);
    }

    public enum LinkageType {
        External(LLVM.LLVMExternalLinkage),
        LinkOnce(LLVM.LLVMLinkOnceAnyLinkage),
        LinkOnceODR(LLVM.LLVMLinkOnceODRLinkage);

        private int code;

        LinkageType(int code) {
            this.code = code;
        }
    }

    public void setFunctionAttribute(Attribute attribute) {
        setFunctionAttribute(function, attribute);
    }

    public void setFunctionAttribute(LLVMValueRef func, Attribute attribute) {
        int kind = LLVM.LLVMGetEnumAttributeKindForName(attribute.name, attribute.name.length());
        LLVMAttributeRef attr;
        if (kind != 0) {
            attr = LLVM.LLVMCreateEnumAttribute(context, kind, TRUE);
        } else {
            String value = "true";
            attr = LLVM.LLVMCreateStringAttribute(context, attribute.name, attribute.name.length(), value, value.length());
        }
        LLVM.LLVMAddAttributeAtIndex(func, (int) LLVM.LLVMAttributeFunctionIndex, attr);
    }

    public enum Attribute {
        AlwaysInline("alwaysinline"),
        GCLeafFunction("gc-leaf-function"),
        Naked("naked"),
        NoInline("noinline"),
        NoRealignStack("no-realign-stack"),
        NoRedZone("noredzone"),
        StatepointID("statepoint-id");

        private String name;

        Attribute(String name) {
            this.name = name;
        }
    }

    public void setPersonalityFunction(LLVMValueRef personality) {
        LLVM.LLVMSetPersonalityFn(function, personality);
    }

    public void setGarbageCollector(GCStrategy gc) {
        setGarbageCollector(function, gc);
    }

    public void setGarbageCollector(LLVMValueRef func, GCStrategy gc) {
        LLVM.LLVMSetGC(func, gc.name);
    }

    public enum GCStrategy {
        CompressedPointers("compressed-pointer");

        private String name;

        GCStrategy(String name) {
            this.name = name;
        }
    }

    /* Basic blocks */

    public LLVMBasicBlockRef appendBasicBlock(String name) {
        return appendBasicBlock(function, name);
    }

    public LLVMBasicBlockRef appendBasicBlock(LLVMValueRef func, String name) {
        return LLVM.LLVMAppendBasicBlockInContext(context, func, name);
    }

    public void positionAtStart() {
        LLVMBasicBlockRef firstBlock = LLVM.LLVMGetFirstBasicBlock(function);
        LLVMValueRef firstInstruction = LLVM.LLVMGetFirstInstruction(firstBlock);
        if (firstInstruction != null) {
            LLVM.LLVMPositionBuilderBefore(builder, firstInstruction);
        }
    }

    public void positionAtEnd(LLVMBasicBlockRef block) {
        LLVM.LLVMPositionBuilderAtEnd(builder, block);
    }

    public void positionBeforeTerminator(LLVMBasicBlockRef block) {
        LLVM.LLVMPositionBuilderBefore(builder, blockTerminator(block));
    }

    public LLVMValueRef blockTerminator(LLVMBasicBlockRef block) {
        return LLVM.LLVMGetBasicBlockTerminator(block);
    }

    /* Types */

    public static LLVMTypeRef typeOf(LLVMValueRef value) {
        return LLVM.LLVMTypeOf(value);
    }

    public LLVMTypeRef booleanType() {
        return integerType(1);
    }

    public static boolean isBooleanType(LLVMTypeRef type) {
        return isIntegerType(type) && integerTypeWidth(type) == 1;
    }

    public LLVMTypeRef byteType() {
        return integerType(Byte.SIZE);
    }

    public static boolean isByteType(LLVMTypeRef type) {
        return isIntegerType(type) && integerTypeWidth(type) == Byte.SIZE;
    }

    public LLVMTypeRef shortType() {
        return integerType(Short.SIZE);
    }

    public static boolean isShortType(LLVMTypeRef type) {
        return isIntegerType(type) && integerTypeWidth(type) == Short.SIZE;
    }

    public LLVMTypeRef charType() {
        return integerType(Character.SIZE);
    }

    public static boolean isCharType(LLVMTypeRef type) {
        return isIntegerType(type) && integerTypeWidth(type) == Character.SIZE;
    }

    public LLVMTypeRef intType() {
        return integerType(Integer.SIZE);
    }

    public static boolean isIntType(LLVMTypeRef type) {
        return isIntegerType(type) && integerTypeWidth(type) == Integer.SIZE;
    }

    public LLVMTypeRef longType() {
        return integerType(Long.SIZE);
    }

    public static boolean isLongType(LLVMTypeRef type) {
        return isIntegerType(type) && integerTypeWidth(type) == Long.SIZE;
    }

    LLVMTypeRef integerType(int bits) {
        return LLVM.LLVMIntTypeInContext(context, bits);
    }

    public static boolean isIntegerType(LLVMTypeRef type) {
        return LLVM.LLVMGetTypeKind(type) == LLVM.LLVMIntegerTypeKind;
    }

    public static int integerTypeWidth(LLVMTypeRef intType) {
        return LLVM.LLVMGetIntTypeWidth(intType);
    }

    public LLVMTypeRef wordType() {
        return integerType(FrameAccess.wordSize() * Byte.SIZE);
    }

    public static boolean isWordType(LLVMTypeRef type) {
        return isIntegerType(type) && integerTypeWidth(type) == FrameAccess.wordSize() * Byte.SIZE;
    }

    public LLVMTypeRef floatType() {
        return LLVM.LLVMFloatTypeInContext(context);
    }

    public static boolean isFloatType(LLVMTypeRef type) {
        return LLVM.LLVMGetTypeKind(type) == LLVM.LLVMFloatTypeKind;
    }

    public LLVMTypeRef doubleType() {
        return LLVM.LLVMDoubleTypeInContext(context);
    }

    public static boolean isDoubleType(LLVMTypeRef type) {
        return LLVM.LLVMGetTypeKind(type) == LLVM.LLVMDoubleTypeKind;
    }

    /*
     * Pointer types can be of two types: references and regular pointers. References are pointers
     * to Java objects which are tracked by the GC statepoint emission pass to create reference maps
     * at call sites. Regular pointers are not tracked and represent non-java pointers. They are
     * distinguished by the pointer address space they live in (1, resp. 0).
     */
    private static final int UNTRACKED_POINTER_ADDRESS_SPACE = 0;
    private static final int TRACKED_POINTER_ADDRESS_SPACE = 1;
    private static final int COMPRESSED_POINTER_ADDRESS_SPACE = 2;

    private static int pointerAddressSpace(boolean tracked, boolean compressed) {
        assert tracked || !compressed;
        return tracked ? (compressed ? COMPRESSED_POINTER_ADDRESS_SPACE : TRACKED_POINTER_ADDRESS_SPACE) : UNTRACKED_POINTER_ADDRESS_SPACE;
    }

    public LLVMTypeRef objectType(boolean compressed) {
        return pointerType(byteType(), true, compressed);
    }

    public LLVMTypeRef rawPointerType() {
        return pointerType(byteType());
    }

    public LLVMTypeRef pointerType(LLVMTypeRef type) {
        return pointerType(type, false, false);
    }

    public LLVMTypeRef pointerType(LLVMTypeRef type, boolean tracked, boolean compressed) {
        return LLVM.LLVMPointerType(type, pointerAddressSpace(tracked, compressed));
    }

    public static LLVMTypeRef getElementType(LLVMTypeRef pointerType) {
        return LLVM.LLVMGetElementType(pointerType);
    }

    public static boolean isObjectType(LLVMTypeRef type) {
        return isPointerType(type) && isTrackedPointerType(type);
    }

    static boolean isPointerType(LLVMTypeRef type) {
        return LLVM.LLVMGetTypeKind(type) == LLVM.LLVMPointerTypeKind;
    }

    static boolean isTrackedPointerType(LLVMTypeRef pointerType) {
        int addressSpace = LLVM.LLVMGetPointerAddressSpace(pointerType);
        return addressSpace == TRACKED_POINTER_ADDRESS_SPACE || addressSpace == COMPRESSED_POINTER_ADDRESS_SPACE;
    }

    public static boolean isCompressedPointerType(LLVMTypeRef pointerType) {
        int addressSpace = LLVM.LLVMGetPointerAddressSpace(pointerType);
        assert addressSpace == COMPRESSED_POINTER_ADDRESS_SPACE || addressSpace == TRACKED_POINTER_ADDRESS_SPACE;
        return addressSpace == COMPRESSED_POINTER_ADDRESS_SPACE;
    }

    public LLVMTypeRef arrayType(LLVMTypeRef type, int length) {
        return LLVM.LLVMArrayType(type, length);
    }

    public LLVMTypeRef structType(LLVMTypeRef... types) {
        return LLVM.LLVMStructTypeInContext(context, new PointerPointer<>(types), types.length, FALSE);
    }

    public static int countElementTypes(LLVMTypeRef structType) {
        return LLVM.LLVMCountStructElementTypes(structType);
    }

    private static LLVMTypeRef getTypeAtIndex(LLVMTypeRef structType, int index) {
        return LLVM.LLVMStructGetTypeAtIndex(structType, index);
    }

    private static LLVMTypeRef[] getElementTypes(LLVMTypeRef structType) {
        LLVMTypeRef[] types = new LLVMTypeRef[countElementTypes(structType)];
        for (int i = 0; i < types.length; ++i) {
            types[i] = getTypeAtIndex(structType, i);
        }
        return types;
    }

    public LLVMTypeRef vectorType(LLVMTypeRef type, int count) {
        return LLVM.LLVMVectorType(type, count);
    }

    public LLVMTypeRef functionType(LLVMTypeRef returnType, LLVMTypeRef... argTypes) {
        return functionType(returnType, false, argTypes);
    }

    public LLVMTypeRef functionType(LLVMTypeRef returnType, boolean varargs, LLVMTypeRef... argTypes) {
        return LLVM.LLVMFunctionType(returnType, new PointerPointer<>(argTypes), argTypes.length, varargs ? TRUE : FALSE);
    }

    public LLVMTypeRef functionPointerType(LLVMTypeRef returnType, LLVMTypeRef... argTypes) {
        return pointerType(functionType(returnType, argTypes), false, false);
    }

    public static LLVMTypeRef getReturnType(LLVMTypeRef functionType) {
        return LLVM.LLVMGetReturnType(functionType);
    }

    public static LLVMTypeRef[] getParamTypes(LLVMTypeRef functionType) {
        int numParams = LLVM.LLVMCountParamTypes(functionType);
        PointerPointer<LLVMTypeRef> argTypesPointer = new PointerPointer<>(numParams);
        LLVM.LLVMGetParamTypes(functionType, argTypesPointer);
        return IntStream.range(0, numParams).mapToObj(i -> argTypesPointer.get(LLVMTypeRef.class, i)).toArray(LLVMTypeRef[]::new);
    }

    public static boolean isFunctionVarArg(LLVMTypeRef functionType) {
        return LLVM.LLVMIsFunctionVarArg(functionType) == TRUE;
    }

    public LLVMTypeRef voidType() {
        return LLVM.LLVMVoidTypeInContext(context);
    }

    public static boolean isVoidType(LLVMTypeRef type) {
        return LLVM.LLVMGetTypeKind(type) == LLVM.LLVMVoidTypeKind;
    }

    private LLVMTypeRef tokenType() {
        return LLVM.LLVMTokenTypeInContext(context);
    }

    private LLVMTypeRef metadataType() {
        return LLVM.LLVMMetadataTypeInContext(context);
    }

    public static boolean compatibleTypes(LLVMTypeRef a, LLVMTypeRef b) {
        int aKind = LLVM.LLVMGetTypeKind(a);
        int bKind = LLVM.LLVMGetTypeKind(b);
        if (aKind != bKind) {
            return false;
        }
        if (aKind == LLVM.LLVMIntegerTypeKind) {
            return LLVM.LLVMGetIntTypeWidth(a) == LLVM.LLVMGetIntTypeWidth(b);
        }
        if (aKind == LLVM.LLVMPointerTypeKind) {
            return LLVM.LLVMGetPointerAddressSpace(a) == LLVM.LLVMGetPointerAddressSpace(b) && compatibleTypes(LLVM.LLVMGetElementType(a), LLVM.LLVMGetElementType(b));
        }
        return true;
    }

    public static String intrinsicType(LLVMTypeRef type) {
        switch (LLVM.LLVMGetTypeKind(type)) {
            case LLVM.LLVMIntegerTypeKind:
                return "i" + integerTypeWidth(type);
            case LLVM.LLVMFloatTypeKind:
                return "f32";
            case LLVM.LLVMDoubleTypeKind:
                return "f64";
            case LLVM.LLVMVoidTypeKind:
                return "isVoid";
            case LLVM.LLVMPointerTypeKind:
                return "p" + LLVM.LLVMGetPointerAddressSpace(type) + intrinsicType(LLVM.LLVMGetElementType(type));
            case LLVM.LLVMFunctionTypeKind:
                String args = Arrays.stream(getParamTypes(type)).map(LLVMIRBuilder::intrinsicType).collect(Collectors.joining(""));
                return "f_" + intrinsicType(getReturnType(type)) + args + "f";
            case LLVM.LLVMStructTypeKind:
                String types = Arrays.stream(getElementTypes(type)).map(LLVMIRBuilder::intrinsicType).collect(Collectors.joining(""));
                return "sl_" + types + "s";
            default:
                throw shouldNotReachHere();
        }
    }

    /* Constants */

    public LLVMValueRef constantBoolean(boolean x) {
        return constantInteger(x ? TRUE : FALSE, 1);
    }

    public LLVMValueRef constantByte(byte x) {
        return constantInteger(x, 8);
    }

    public LLVMValueRef constantShort(short x) {
        return constantInteger(x, 16);
    }

    public LLVMValueRef constantChar(char x) {
        return constantInteger(x, 16);
    }

    public LLVMValueRef constantInt(int x) {
        return constantInteger(x, 32);
    }

    public LLVMValueRef constantLong(long x) {
        return constantInteger(x, 64);
    }

    public LLVMValueRef constantInteger(long value, int bits) {
        return LLVM.LLVMConstInt(integerType(bits), value, FALSE);
    }

    public LLVMValueRef constantFloat(float x) {
        return LLVM.LLVMConstReal(floatType(), x);
    }

    public LLVMValueRef constantDouble(double x) {
        return LLVM.LLVMConstReal(doubleType(), x);
    }

    public LLVMValueRef constantNull(LLVMTypeRef type) {
        return LLVM.LLVMConstNull(type);
    }

    public LLVMValueRef buildGlobalStringPtr(String name) {
        return LLVM.LLVMBuildGlobalStringPtr(builder, name, DEFAULT_INSTR_NAME);
    }

    public LLVMValueRef constantString(String string) {
        return LLVM.LLVMConstStringInContext(context, string, string.length(), FALSE);
    }

    public LLVMValueRef constantVector(LLVMValueRef... values) {
        return LLVM.LLVMConstVector(new PointerPointer<>(values), values.length);
    }

    /* Values */

    public LLVMValueRef getFunctionParam(int index) {
        return getParam(function, index);
    }

    public static LLVMValueRef getParam(LLVMValueRef func, int index) {
        return LLVM.LLVMGetParam(func, index);
    }

    public LLVMValueRef buildPhi(LLVMTypeRef phiType, LLVMValueRef[] incomingValues, LLVMBasicBlockRef[] incomingBlocks) {
        LLVMValueRef phi = LLVM.LLVMBuildPhi(builder, phiType, DEFAULT_INSTR_NAME);
        addIncoming(phi, incomingValues, incomingBlocks);
        return phi;
    }

    public void addIncoming(LLVMValueRef phi, LLVMValueRef[] values, LLVMBasicBlockRef[] blocks) {
        assert values.length == blocks.length;
        LLVM.LLVMAddIncoming(phi, new PointerPointer<>(values), new PointerPointer<>(blocks), blocks.length);
    }

    /*
     * External globals are globals which are not created by LLVM but need to be accessed from the
     * code, while unique globals are values created by the LLVM backend, potentially in multiple
     * functions, and are then conflated by the LLVM linker.
     */
    public LLVMValueRef getExternalObject(String name, boolean compressed) {
        LLVMValueRef val = getGlobal(name);
        if (val == null) {
            val = LLVM.LLVMAddGlobalInAddressSpace(module, objectType(compressed), name, pointerAddressSpace(true, false));
            setLinkage(val, LinkageType.External);
        }
        return val;
    }

    public LLVMValueRef getExternalSymbol(String name) {
        LLVMValueRef val = getGlobal(name);
        if (val == null) {
            val = LLVM.LLVMAddGlobalInAddressSpace(module, rawPointerType(), name, UNTRACKED_POINTER_ADDRESS_SPACE);
            setLinkage(val, LinkageType.External);
        }
        return val;
    }

    public LLVMValueRef getUniqueGlobal(String name, LLVMTypeRef type, boolean zeroInitialized) {
        LLVMValueRef global = getGlobal(name);
        if (global == null) {
            global = LLVM.LLVMAddGlobalInAddressSpace(module, type, name, pointerAddressSpace(isObjectType(type), false));
            if (zeroInitialized) {
                setInitializer(global, LLVM.LLVMConstNull(type));
            }
            setLinkage(global, LinkageType.LinkOnceODR);
        }
        return global;
    }

    private LLVMValueRef getGlobal(String name) {
        return LLVM.LLVMGetNamedGlobal(module, name);
    }

    public void setInitializer(LLVMValueRef global, LLVMValueRef value) {
        LLVM.LLVMSetInitializer(global, value);
    }

    public LLVMValueRef register(String name) {
        String nameEncoding = name + "\00";
        LLVMValueRef[] vals = new LLVMValueRef[]{LLVM.LLVMMDStringInContext(context, nameEncoding, nameEncoding.length())};
        return LLVM.LLVMMDNodeInContext(context, new PointerPointer<>(vals), vals.length);
    }

    public LLVMValueRef buildReadRegister(LLVMValueRef register) {
        LLVMTypeRef readRegisterType = functionType(wordType(), metadataType());
        return buildIntrinsicCall("llvm.read_register." + intrinsicType(wordType()), readRegisterType, register);
    }

    public LLVMValueRef buildExtractValue(LLVMValueRef struct, int i) {
        return LLVM.LLVMBuildExtractValue(builder, struct, i, DEFAULT_INSTR_NAME);
    }

    public LLVMValueRef buildInsertValue(LLVMValueRef struct, int i, LLVMValueRef value) {
        return LLVM.LLVMBuildInsertValue(builder, struct, value, i, DEFAULT_INSTR_NAME);
    }

    public LLVMValueRef buildExtractElement(LLVMValueRef vector, LLVMValueRef index) {
        return LLVM.LLVMBuildExtractElement(builder, vector, index, DEFAULT_INSTR_NAME);
    }

    public void setFunctionMetadata(String kind, LLVMValueRef metadata) {
        setMetadata(function, kind, metadata);
    }

    public void setMetadata(LLVMValueRef instr, String kind, LLVMValueRef metadata) {
        LLVM.LLVMSetMetadata(instr, LLVM.LLVMGetMDKindIDInContext(context, kind, kind.length()), metadata);
    }

    public void setValueName(LLVMValueRef value, String name) {
        LLVM.LLVMSetValueName(value, name);
    }

    /* Control flow */

    public LLVMValueRef buildCall(LLVMValueRef callee, LLVMValueRef... args) {
        return LLVM.LLVMBuildCall(builder, callee, new PointerPointer<>(args), args.length, DEFAULT_INSTR_NAME);
    }

    public LLVMValueRef buildInvoke(LLVMValueRef callee, LLVMBasicBlockRef successor, LLVMBasicBlockRef handler, LLVMValueRef... args) {
        return LLVM.LLVMBuildInvoke(builder, callee, new PointerPointer<>(args), args.length, successor, handler, DEFAULT_INSTR_NAME);
    }

    private LLVMValueRef buildIntrinsicCall(String name, LLVMTypeRef type, LLVMValueRef... args) {
        LLVMValueRef intrinsic = getFunction(name, type);
        return buildCall(intrinsic, args);
    }

    public LLVMValueRef buildInlineAsm(LLVMTypeRef functionType, String asm, boolean hasSideEffects, boolean alignStack, InlineAssemblyConstraint... constraints) {
        String[] constraintStrings = new String[constraints.length];
        for (int i = 0; i < constraints.length; ++i) {
            constraintStrings[i] = constraints[i].toString();
        }
        String constraintString = String.join(",", constraintStrings);
        return LLVM.LLVMConstInlineAsm(functionType, asm, constraintString, hasSideEffects ? TRUE : FALSE, alignStack ? TRUE : FALSE);
    }

    public static class InlineAssemblyConstraint {
        private Type type;
        private Location location;

        public InlineAssemblyConstraint(Type type, Location location) {
            this.type = type;
            this.location = location;
        }

        @Override
        public String toString() {
            return type.repr + location.repr;
        }

        public enum Type {
            Output("="),
            Input("");

            private String repr;

            Type(String repr) {
                this.repr = repr;
            }
        }

        public static final class Location {
            private String repr;

            private Location(String repr) {
                this.repr = repr;
            }

            public static Location register() {
                return new Location("r");
            }

            public static Location namedRegister(String register) {
                return new Location("{" + register + "}");
            }
        }
    }

    public void setCallSiteAttribute(LLVMValueRef call, Attribute attribute, String value) {
        LLVMAttributeRef attr = LLVM.LLVMCreateStringAttribute(context, attribute.name, attribute.name.length(), value, value.length());
        LLVM.LLVMAddCallSiteAttribute(call, (int) LLVM.LLVMAttributeFunctionIndex, attr);
    }

    public void setCallSiteAttribute(LLVMValueRef call, Attribute attribute) {
        int kind = LLVM.LLVMGetEnumAttributeKindForName(attribute.name, attribute.name.length());
        LLVMAttributeRef attr;
        if (kind != 0) {
            attr = LLVM.LLVMCreateEnumAttribute(context, kind, TRUE);
            LLVM.LLVMAddCallSiteAttribute(call, (int) LLVM.LLVMAttributeFunctionIndex, attr);
        } else {
            setCallSiteAttribute(call, attribute, "true");
        }
    }

    public void buildRetVoid() {
        LLVM.LLVMBuildRetVoid(builder);
    }

    public void buildRet(LLVMValueRef value) {
        LLVM.LLVMBuildRet(builder, value);
    }

    public void buildBranch(LLVMBasicBlockRef block) {
        LLVM.LLVMBuildBr(builder, block);
    }

    public LLVMValueRef buildIf(LLVMValueRef condition, LLVMBasicBlockRef thenBlock, LLVMBasicBlockRef elseBlock) {
        return LLVM.LLVMBuildCondBr(builder, condition, thenBlock, elseBlock);
    }

    public LLVMValueRef buildSwitch(LLVMValueRef value, LLVMBasicBlockRef defaultBlock, LLVMValueRef[] switchValues, LLVMBasicBlockRef[] switchBlocks) {
        assert switchValues.length == switchBlocks.length;

        LLVMValueRef switchVal = LLVM.LLVMBuildSwitch(builder, value, defaultBlock, switchBlocks.length);
        for (int i = 0; i < switchBlocks.length; ++i) {
            LLVM.LLVMAddCase(switchVal, switchValues[i], switchBlocks[i]);
        }
        return switchVal;
    }

    public void buildLandingPad() {
        LLVMValueRef landingPad = LLVM.LLVMBuildLandingPad(builder, tokenType(), null, 1, DEFAULT_INSTR_NAME);
        LLVM.LLVMAddClause(landingPad, constantNull(rawPointerType()));
    }

    public void buildStackmap(LLVMValueRef patchpointId, LLVMValueRef... liveValues) {
        LLVMTypeRef stackmapType = functionType(voidType(), true, longType(), intType());

        LLVMValueRef[] allArgs = new LLVMValueRef[2 + liveValues.length];
        allArgs[0] = patchpointId;
        allArgs[1] = constantInt(0);
        System.arraycopy(liveValues, 0, allArgs, 2, liveValues.length);

        buildIntrinsicCall("llvm.experimental.stackmap", stackmapType, allArgs);
    }

    public void buildUnreachable() {
        LLVM.LLVMBuildUnreachable(builder);
    }

    public void buildDebugtrap() {
        buildIntrinsicCall("llvm.debugtrap", functionType(voidType()));
    }

    public LLVMValueRef functionEntryCount(LLVMValueRef count) {
        String functionEntryCountName = "function_entry_count";
        LLVMValueRef[] values = new LLVMValueRef[2];
        values[0] = LLVM.LLVMMDStringInContext(context, functionEntryCountName, functionEntryCountName.length());
        values[1] = count;
        return LLVM.LLVMMDNodeInContext(context, new PointerPointer<>(values), values.length);
    }

    public LLVMValueRef branchWeights(LLVMValueRef... weights) {
        String branchWeightsName = "branch_weights";
        LLVMValueRef[] values = new LLVMValueRef[weights.length + 1];
        values[0] = LLVM.LLVMMDStringInContext(context, branchWeightsName, branchWeightsName.length());
        System.arraycopy(weights, 0, values, 1, weights.length);
        return LLVM.LLVMMDNodeInContext(context, new PointerPointer<>(values), values.length);
    }

    /* Comparisons */

    public LLVMValueRef buildIsNull(LLVMValueRef value) {
        return LLVM.LLVMBuildIsNull(builder, value, DEFAULT_INSTR_NAME);
    }

    public LLVMValueRef buildCompare(Condition cond, LLVMValueRef a, LLVMValueRef b, boolean unordered) {
        LLVMTypeRef aType = LLVM.LLVMTypeOf(a);
        LLVMTypeRef bType = LLVM.LLVMTypeOf(b);

        assert compatibleTypes(aType, bType) : dumpTypes("comparison", aType, bType);

        switch (LLVM.LLVMGetTypeKind(aType)) {
            case LLVM.LLVMIntegerTypeKind:
            case LLVM.LLVMPointerTypeKind:
                return buildICmp(cond, a, b);
            case LLVM.LLVMFloatTypeKind:
            case LLVM.LLVMDoubleTypeKind:
                return buildFCmp(cond, a, b, unordered);
            default:
                throw shouldNotReachHere(dumpTypes("comparison", aType, bType));
        }
    }

    public LLVMValueRef buildICmp(Condition cond, LLVMValueRef a, LLVMValueRef b) {
        int conditionCode;
        switch (cond) {
            case EQ:
                conditionCode = LLVM.LLVMIntEQ;
                break;
            case NE:
                conditionCode = LLVM.LLVMIntNE;
                break;
            case LT:
                conditionCode = LLVM.LLVMIntSLT;
                break;
            case LE:
                conditionCode = LLVM.LLVMIntSLE;
                break;
            case GT:
                conditionCode = LLVM.LLVMIntSGT;
                break;
            case GE:
                conditionCode = LLVM.LLVMIntSGE;
                break;
            case AE:
                conditionCode = LLVM.LLVMIntUGE;
                break;
            case BE:
                conditionCode = LLVM.LLVMIntULE;
                break;
            case AT:
                conditionCode = LLVM.LLVMIntUGT;
                break;
            case BT:
                conditionCode = LLVM.LLVMIntULT;
                break;
            default:
                throw shouldNotReachHere("invalid condition");
        }
        return LLVM.LLVMBuildICmp(builder, conditionCode, a, b, DEFAULT_INSTR_NAME);
    }

    private LLVMValueRef buildFCmp(Condition cond, LLVMValueRef a, LLVMValueRef b, boolean unordered) {
        int conditionCode;
        switch (cond) {
            case EQ:
                conditionCode = (unordered) ? LLVM.LLVMRealUEQ : LLVM.LLVMRealOEQ;
                break;
            case NE:
                conditionCode = (unordered) ? LLVM.LLVMRealUNE : LLVM.LLVMRealONE;
                break;
            case LT:
                conditionCode = (unordered) ? LLVM.LLVMRealULT : LLVM.LLVMRealOLT;
                break;
            case LE:
                conditionCode = (unordered) ? LLVM.LLVMRealULE : LLVM.LLVMRealOLE;
                break;
            case GT:
                conditionCode = (unordered) ? LLVM.LLVMRealUGT : LLVM.LLVMRealOGT;
                break;
            case GE:
                conditionCode = (unordered) ? LLVM.LLVMRealUGE : LLVM.LLVMRealOGE;
                break;
            default:
                throw shouldNotReachHere("invalid condition");
        }
        return LLVM.LLVMBuildFCmp(builder, conditionCode, a, b, DEFAULT_INSTR_NAME);
    }

    public LLVMValueRef buildSelect(LLVMValueRef condition, LLVMValueRef trueVal, LLVMValueRef falseVal) {
        return LLVM.LLVMBuildSelect(builder, condition, trueVal, falseVal, DEFAULT_INSTR_NAME);
    }

    /* Arithmetic */

    private interface UnaryBuilder {
        LLVMValueRef build(LLVMBuilderRef builder, LLVMValueRef a, String str);
    }

    private interface BinaryBuilder {
        LLVMValueRef build(LLVMBuilderRef builder, LLVMValueRef a, LLVMValueRef b, String str);
    }

    public LLVMValueRef buildNeg(LLVMValueRef a) {
        LLVMTypeRef type = LLVM.LLVMTypeOf(a);

        UnaryBuilder unaryBuilder;
        switch (LLVM.LLVMGetTypeKind(type)) {
            case LLVM.LLVMIntegerTypeKind:
                unaryBuilder = LLVM::LLVMBuildNeg;
                break;
            case LLVM.LLVMFloatTypeKind:
            case LLVM.LLVMDoubleTypeKind:
                unaryBuilder = LLVM::LLVMBuildFNeg;
                break;
            default:
                throw shouldNotReachHere(dumpTypes("invalid negation type", type));
        }

        return unaryBuilder.build(builder, a, DEFAULT_INSTR_NAME);
    }

    public LLVMValueRef buildAdd(LLVMValueRef a, LLVMValueRef b) {
        return buildBinaryNumberOp(a, b, LLVM::LLVMBuildAdd, LLVM::LLVMBuildFAdd);
    }

    public LLVMValueRef buildSub(LLVMValueRef a, LLVMValueRef b) {
        return buildBinaryNumberOp(a, b, LLVM::LLVMBuildSub, LLVM::LLVMBuildFSub);
    }

    public LLVMValueRef buildMul(LLVMValueRef a, LLVMValueRef b) {
        return buildBinaryNumberOp(a, b, LLVM::LLVMBuildMul, LLVM::LLVMBuildFMul);
    }

    public LLVMValueRef buildDiv(LLVMValueRef a, LLVMValueRef b) {
        return buildBinaryNumberOp(a, b, LLVM::LLVMBuildSDiv, LLVM::LLVMBuildFDiv);
    }

    public LLVMValueRef buildRem(LLVMValueRef a, LLVMValueRef b) {
        return buildBinaryNumberOp(a, b, LLVM::LLVMBuildSRem, LLVM::LLVMBuildFRem);
    }

    public LLVMValueRef buildUDiv(LLVMValueRef a, LLVMValueRef b) {
        return buildBinaryNumberOp(a, b, LLVM::LLVMBuildUDiv, null);
    }

    public LLVMValueRef buildURem(LLVMValueRef a, LLVMValueRef b) {
        return buildBinaryNumberOp(a, b, LLVM::LLVMBuildURem, null);
    }

    private LLVMValueRef buildBinaryNumberOp(LLVMValueRef a, LLVMValueRef b, BinaryBuilder integerBuilder, BinaryBuilder realBuilder) {
        LLVMTypeRef aType = LLVM.LLVMTypeOf(a);
        LLVMTypeRef bType = LLVM.LLVMTypeOf(b);
        assert compatibleTypes(aType, bType) : dumpValues("invalid binary operation arguments", a, b);

        BinaryBuilder binaryBuilder;
        switch (LLVM.LLVMGetTypeKind(aType)) {
            case LLVM.LLVMIntegerTypeKind:
                binaryBuilder = integerBuilder;
                break;
            case LLVM.LLVMFloatTypeKind:
            case LLVM.LLVMDoubleTypeKind:
                binaryBuilder = realBuilder;
                break;
            default:
                throw shouldNotReachHere(dumpValues("invalid binary operation arguments", a, b));
        }

        return binaryBuilder.build(builder, a, b, DEFAULT_INSTR_NAME);
    }

    public LLVMValueRef buildAbs(LLVMValueRef a) {
        return buildIntrinsicOp("fabs", a);
    }

    public LLVMValueRef buildLog(LLVMValueRef a) {
        return buildIntrinsicOp("log", a);
    }

    public LLVMValueRef buildLog10(LLVMValueRef a) {
        return buildIntrinsicOp("log10", a);
    }

    public LLVMValueRef buildSqrt(LLVMValueRef a) {
        return buildIntrinsicOp("sqrt", a);
    }

    public LLVMValueRef buildCos(LLVMValueRef a) {
        return buildIntrinsicOp("cos", a);
    }

    public LLVMValueRef buildSin(LLVMValueRef a) {
        return buildIntrinsicOp("sin", a);
    }

    public LLVMValueRef buildExp(LLVMValueRef a) {
        return buildIntrinsicOp("exp", a);
    }

    public LLVMValueRef buildPow(LLVMValueRef a, LLVMValueRef b) {
        return buildIntrinsicOp("pow", a, b);
    }

    public LLVMValueRef buildCeil(LLVMValueRef a) {
        return buildIntrinsicOp("ceil", a);
    }

    public LLVMValueRef buildFloor(LLVMValueRef a) {
        return buildIntrinsicOp("floor", a);
    }

    public LLVMValueRef buildMin(LLVMValueRef a, LLVMValueRef b) {
        return buildIntrinsicOp("minimum", a, b);
    }

    public LLVMValueRef buildMax(LLVMValueRef a, LLVMValueRef b) {
        return buildIntrinsicOp("maximum", a, b);
    }

    public LLVMValueRef buildCopysign(LLVMValueRef a, LLVMValueRef b) {
        return buildIntrinsicOp("copysign", a, b);
    }

    public LLVMValueRef buildFma(LLVMValueRef a, LLVMValueRef b, LLVMValueRef c) {
        return buildIntrinsicOp("fma", a, b, c);
    }

    public LLVMValueRef buildBswap(LLVMValueRef a) {
        return buildIntrinsicOp("bswap", a);
    }

    private LLVMValueRef buildIntrinsicOp(String name, LLVMTypeRef retType, LLVMValueRef... args) {
        String intrinsicName = "llvm." + name + "." + intrinsicType(retType);
        LLVMTypeRef intrinsicType = functionType(retType, Arrays.stream(args).map(LLVM::LLVMTypeOf).toArray(LLVMTypeRef[]::new));

        return buildIntrinsicCall(intrinsicName, intrinsicType, args);
    }

    private LLVMValueRef buildIntrinsicOp(String name, LLVMValueRef... args) {
        return buildIntrinsicOp(name, LLVM.LLVMTypeOf(args[0]), args);
    }

    /* Bitwise */

    public LLVMValueRef buildNot(LLVMValueRef input) {
        return LLVM.LLVMBuildNot(builder, input, DEFAULT_INSTR_NAME);
    }

    public LLVMValueRef buildAnd(LLVMValueRef a, LLVMValueRef b) {
        return LLVM.LLVMBuildAnd(builder, a, b, DEFAULT_INSTR_NAME);
    }

    public LLVMValueRef buildOr(LLVMValueRef a, LLVMValueRef b) {
        return LLVM.LLVMBuildOr(builder, a, b, DEFAULT_INSTR_NAME);
    }

    public LLVMValueRef buildXor(LLVMValueRef a, LLVMValueRef b) {
        return LLVM.LLVMBuildXor(builder, a, b, DEFAULT_INSTR_NAME);
    }

    public LLVMValueRef buildShl(LLVMValueRef a, LLVMValueRef b) {
        return buildShift(LLVM::LLVMBuildShl, a, b);
    }

    public LLVMValueRef buildShr(LLVMValueRef a, LLVMValueRef b) {
        return buildShift(LLVM::LLVMBuildAShr, a, b);
    }

    public LLVMValueRef buildUShr(LLVMValueRef a, LLVMValueRef b) {
        return buildShift(LLVM::LLVMBuildLShr, a, b);
    }

    private LLVMValueRef buildShift(BinaryBuilder binaryBuilder, LLVMValueRef a, LLVMValueRef b) {
        return binaryBuilder.build(builder, a, b, DEFAULT_INSTR_NAME);
    }

    public LLVMValueRef buildCtlz(LLVMValueRef a) {
        return buildIntrinsicOp("ctlz", a, constantBoolean(false));
    }

    public LLVMValueRef buildCttz(LLVMValueRef a) {
        return buildIntrinsicOp("cttz", a, constantBoolean(false));
    }

    public LLVMValueRef buildCtpop(LLVMValueRef a) {
        return buildIntrinsicOp("ctpop", a);
    }

    /* Conversions */

    public LLVMValueRef buildBitcast(LLVMValueRef value, LLVMTypeRef type) {
        LLVMTypeRef valueType = LLVM.LLVMTypeOf(value);
        if (compatibleTypes(valueType, type)) {
            return value;
        }

        return LLVM.LLVMBuildBitCast(builder, value, type, DEFAULT_INSTR_NAME);
    }

    public LLVMValueRef buildAddrSpaceCast(LLVMValueRef value, LLVMTypeRef type) {
        return LLVM.LLVMBuildAddrSpaceCast(builder, value, type, DEFAULT_INSTR_NAME);
    }

    public LLVMValueRef buildIntToPtr(LLVMValueRef value, LLVMTypeRef type) {
        if (isObjectType(type)) {
            return buildCall(helpers.getIntToObjectFunction(isCompressedPointerType(type)), value);
        }
        return buildLLVMIntToPtr(value, type);
    }

    LLVMValueRef buildLLVMIntToPtr(LLVMValueRef value, LLVMTypeRef type) {
        return LLVM.LLVMBuildIntToPtr(builder, value, type, DEFAULT_INSTR_NAME);
    }

    public LLVMValueRef buildPtrToInt(LLVMValueRef value) {
        return LLVM.LLVMBuildPtrToInt(builder, value, wordType(), DEFAULT_INSTR_NAME);
    }

    public LLVMValueRef buildFPToSI(LLVMValueRef value, LLVMTypeRef type) {
        return LLVM.LLVMBuildFPToSI(builder, value, type, DEFAULT_INSTR_NAME);
    }

    public LLVMValueRef buildSIToFP(LLVMValueRef value, LLVMTypeRef type) {
        return LLVM.LLVMBuildSIToFP(builder, value, type, DEFAULT_INSTR_NAME);
    }

    public LLVMValueRef buildFPCast(LLVMValueRef value, LLVMTypeRef type) {
        return LLVM.LLVMBuildFPCast(builder, value, type, DEFAULT_INSTR_NAME);
    }

    public LLVMValueRef buildTrunc(LLVMValueRef value, int toBits) {
        return LLVM.LLVMBuildTrunc(builder, value, integerType(toBits), DEFAULT_INSTR_NAME);
    }

    public LLVMValueRef buildSExt(LLVMValueRef value, int toBits) {
        return LLVM.LLVMBuildSExt(builder, value, integerType(toBits), DEFAULT_INSTR_NAME);
    }

    public LLVMValueRef buildZExt(LLVMValueRef value, int toBits) {
        return LLVM.LLVMBuildZExt(builder, value, integerType(toBits), DEFAULT_INSTR_NAME);
    }

    public LLVMValueRef buildCompress(LLVMValueRef uncompressed, LLVMValueRef heapBase, boolean nonNull, int shift) {
        return buildCall(helpers.getCompressFunction(nonNull, shift), uncompressed, heapBase);
    }

    public LLVMValueRef buildUncompress(LLVMValueRef compressed, LLVMValueRef heapBase, boolean nonNull, int shift) {
        return buildCall(helpers.getUncompressFunction(nonNull, shift), compressed, heapBase);
    }

    /* Memory */

    public LLVMValueRef buildGEP(LLVMValueRef base, LLVMValueRef... indices) {
        return LLVM.LLVMBuildGEP(builder, base, new PointerPointer<>(indices), indices.length, DEFAULT_INSTR_NAME);
    }

    public LLVMValueRef buildLoad(LLVMValueRef address, LLVMTypeRef type) {
        LLVMTypeRef addressType = LLVM.LLVMTypeOf(address);
        if (isObjectType(type) && !isObjectType(addressType)) {
            boolean compressed = isCompressedPointerType(type);
            return buildCall(helpers.getLoadObjectFromUntrackedPointerFunction(compressed), address);
        }
        LLVMValueRef castedAddress = buildBitcast(address, pointerType(type, isObjectType(addressType), false));
        return buildLoad(castedAddress);
    }

    public LLVMValueRef buildLoad(LLVMValueRef address) {
        return LLVM.LLVMBuildLoad(builder, address, DEFAULT_INSTR_NAME);
    }

    public void buildStore(LLVMValueRef value, LLVMValueRef address) {
        LLVM.LLVMBuildStore(builder, value, address);
    }

    public void buildVolatileStore(LLVMValueRef value, LLVMValueRef address, int alignment) {
        LLVMValueRef store = LLVM.LLVMBuildStore(builder, value, address);
        LLVM.LLVMSetOrdering(store, LLVM.LLVMAtomicOrderingRelease);
        LLVM.LLVMSetAlignment(store, alignment);
    }

    public LLVMValueRef buildAlloca(LLVMTypeRef type) {
        return LLVM.LLVMBuildAlloca(builder, type, DEFAULT_INSTR_NAME);
    }

    public LLVMValueRef buildArrayAlloca(LLVMTypeRef type, int slots) {
        return LLVM.LLVMBuildArrayAlloca(builder, type, constantInt(slots), DEFAULT_INSTR_NAME);
    }

    public void buildPrefetch(LLVMValueRef address) {
        LLVMTypeRef prefetchType = functionType(voidType(), LLVM.LLVMTypeOf(address), intType(), intType(), intType());
        /* llvm.prefetch(address, WRITE, NO_LOCALITY, DATA) */
        buildIntrinsicCall("llvm.prefetch", prefetchType, address, constantInt(1), constantInt(0), constantInt(1));
    }

    public LLVMValueRef buildReturnAddress(LLVMValueRef level) {
        LLVMTypeRef returnAddressType = functionType(rawPointerType(), intType());
        return buildIntrinsicCall("llvm.returnaddress", returnAddressType, level);
    }

    public LLVMValueRef buildFrameAddress(LLVMValueRef level) {
        LLVMTypeRef frameAddressType = functionType(rawPointerType(), intType());
        return buildIntrinsicCall("llvm.frameaddress", frameAddressType, level);
    }

    /* Atomic */

    public void buildFence() {
        LLVM.LLVMBuildFence(builder, LLVM.LLVMAtomicOrderingSequentiallyConsistent, FALSE, DEFAULT_INSTR_NAME);
    }

    public LLVMValueRef buildCmpxchg(LLVMValueRef address, LLVMValueRef expectedValue, LLVMValueRef newValue, boolean returnValue) {
        LLVMTypeRef exchangeType = typeOf(expectedValue);
        if (returnValue && isObjectType(typeOf(expectedValue))) {
            return buildCall(helpers.getCmpxchgFunction(isCompressedPointerType(exchangeType)), address, expectedValue, newValue);
        }
        return buildAtomicCmpXchg(address, expectedValue, newValue, returnValue);
    }

    private static final int LLVM_CMPXCHG_VALUE = 0;
    private static final int LLVM_CMPXCHG_SUCCESS = 1;

    LLVMValueRef buildAtomicCmpXchg(LLVMValueRef addr, LLVMValueRef expected, LLVMValueRef newVal, boolean returnValue) {
        LLVMValueRef cas = LLVM.LLVMBuildAtomicCmpXchg(builder, addr, expected, newVal, LLVM.LLVMAtomicOrderingMonotonic, LLVM.LLVMAtomicOrderingMonotonic, FALSE);
        return buildExtractValue(cas, returnValue ? LLVM_CMPXCHG_VALUE : LLVM_CMPXCHG_SUCCESS);
    }

    public LLVMValueRef buildAtomicXchg(LLVMValueRef address, LLVMValueRef value) {
        if (isObjectType(typeOf(value))) {
            boolean compressed = isCompressedPointerType(typeOf(value));
            return buildCall(helpers.getAtomicObjectXchgFunction(compressed), address, value);
        }
        return buildLLVMAtomicXchg(address, value);
    }

    LLVMValueRef buildLLVMAtomicXchg(LLVMValueRef address, LLVMValueRef value) {
        return buildAtomicRMW(LLVM.LLVMAtomicRMWBinOpXchg, address, value);
    }

    public LLVMValueRef buildAtomicAdd(LLVMValueRef address, LLVMValueRef value) {
        return buildAtomicRMW(LLVM.LLVMAtomicRMWBinOpAdd, address, value);
    }

    private LLVMValueRef buildAtomicRMW(int operation, LLVMValueRef address, LLVMValueRef value) {
        LLVMTypeRef valueType = LLVM.LLVMTypeOf(value);
        LLVMValueRef castedAddress = buildBitcast(address, pointerType(valueType, isObjectType(typeOf(address)), false));
        return LLVM.LLVMBuildAtomicRMW(builder, operation, castedAddress, value, LLVM.LLVMAtomicOrderingMonotonic, FALSE);
    }
}
