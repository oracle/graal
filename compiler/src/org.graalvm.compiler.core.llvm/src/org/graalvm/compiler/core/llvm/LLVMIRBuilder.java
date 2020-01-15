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
package org.graalvm.compiler.core.llvm;

import static org.graalvm.compiler.core.llvm.LLVMUtils.FALSE;
import static org.graalvm.compiler.core.llvm.LLVMUtils.NULL;
import static org.graalvm.compiler.core.llvm.LLVMUtils.TRUE;
import static org.graalvm.compiler.core.llvm.LLVMUtils.dumpTypes;
import static org.graalvm.compiler.core.llvm.LLVMUtils.dumpValues;
import static org.graalvm.compiler.core.llvm.LLVMUtils.getLLVMIntCond;
import static org.graalvm.compiler.core.llvm.LLVMUtils.getLLVMRealCond;
import static org.graalvm.compiler.debug.GraalError.shouldNotReachHere;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.compiler.core.common.calc.Condition;
import org.graalvm.compiler.core.llvm.LLVMUtils.TargetSpecific;
import org.graalvm.compiler.debug.GraalError;

import com.oracle.svm.shadowed.org.bytedeco.javacpp.BytePointer;
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

import jdk.vm.ci.meta.JavaKind;

public class LLVMIRBuilder {
    private static final String DEFAULT_INSTR_NAME = "";

    private LLVMContextRef context;
    private LLVMModuleRef module;
    private LLVMValueRef function;
    private LLVMBuilderRef builder;
    private LLVMBasicBlockRef currentBlock;

    private String functionName;
    private Map<Integer, LLVMValueRef> compressFunctions;
    private Map<Integer, LLVMValueRef> nonNullCompressFunctions;
    private Map<Integer, LLVMValueRef> uncompressFunctions;
    private Map<Integer, LLVMValueRef> nonNullUncompressFunctions;
    private LLVMValueRef gcRegisterFunction;
    private LLVMValueRef gcRegisterCompressedFunction;
    private LLVMValueRef atomicObjectXchgFunction;
    private LLVMValueRef atomicCompressedObjectXchgFunction;
    private LLVMValueRef loadObjectFromUntrackedPointerFunction;
    private LLVMValueRef loadCompressedObjectFromUntrackedPointerFunction;

    public LLVMIRBuilder(String functionName, LLVMContextRef context, boolean useCompressedPointers) {
        this.context = context;
        this.functionName = functionName;

        this.module = LLVM.LLVMModuleCreateWithNameInContext(functionName, context);
        this.builder = LLVM.LLVMCreateBuilderInContext(context);

        if (useCompressedPointers) {
            compressFunctions = new HashMap<>();
            nonNullCompressFunctions = new HashMap<>();
            uncompressFunctions = new HashMap<>();
            nonNullUncompressFunctions = new HashMap<>();
        }

        createGCRegisterFunction(useCompressedPointers);
        createAtomicObjectXchgFunction(useCompressedPointers);
        createLoadObjectFromUntrackedPointerFunction(useCompressedPointers);

    }

    private void createGCRegisterFunction(boolean useCompressedPointers) {
        /*
         * This function declares a GC-tracked pointer from an untracked pointer. This is needed as
         * the statepoint emission pass, which tracks live references in the function, doesn't
         * recognize an address space cast (see pointerType()) as declaring a new reference, but it
         * does a function return value.
         */
        gcRegisterFunction = buildGCRegisterFunction(false);
        if (useCompressedPointers) {
            gcRegisterCompressedFunction = buildGCRegisterFunction(true);
        }
    }

    private void createAtomicObjectXchgFunction(boolean useCompressedPointers) {
        atomicObjectXchgFunction = buildAtomicObjectXchgFunction(false);
        if (useCompressedPointers) {
            atomicCompressedObjectXchgFunction = buildAtomicObjectXchgFunction(true);
        }
    }

    private void createLoadObjectFromUntrackedPointerFunction(boolean useCompressedPointers) {
        /*
         * This function declares a GC-tracked pointer from an untracked pointer. This is needed as
         * the statepoint emission pass, which tracks live references in the function, doesn't
         * recognize an address space cast (see pointerType()) as declaring a new reference, but it
         * does a function return value.
         */
        loadObjectFromUntrackedPointerFunction = buildLoadObjectFromUntrackedPointerFunction(false);
        if (useCompressedPointers) {
            loadCompressedObjectFromUntrackedPointerFunction = buildLoadObjectFromUntrackedPointerFunction(true);
        }
    }

    private LLVMValueRef buildGCRegisterFunction(boolean compressed) {
        String funcName = compressed ? LLVMUtils.GC_REGISTER_COMPRESSED_FUNCTION_NAME : LLVMUtils.GC_REGISTER_FUNCTION_NAME;
        LLVMValueRef func = addFunction(funcName, functionType(objectType(compressed), rawPointerType()));
        LLVM.LLVMSetLinkage(func, LLVM.LLVMLinkOnceAnyLinkage);
        setAttribute(func, LLVM.LLVMAttributeFunctionIndex, LLVMUtils.ALWAYS_INLINE);
        setAttribute(func, LLVM.LLVMAttributeFunctionIndex, LLVMUtils.GC_LEAF_FUNCTION_NAME);

        LLVMBasicBlockRef block = appendBasicBlock("main", func);
        positionAtEnd(block);
        LLVMValueRef arg = getParam(func, 0);
        LLVMValueRef ref = buildAddrSpaceCast(arg, objectType(compressed));
        buildRet(ref);

        return func;
    }

    private LLVMValueRef buildCompressFunction(int shift) {
        return buildCompressFunction(false, shift);
    }

    private LLVMValueRef buildNonNullCompressFunction(int shift) {
        return buildCompressFunction(true, shift);
    }

    private LLVMValueRef buildCompressFunction(boolean nonNull, int shift) {
        LLVMBasicBlockRef previousBlock = currentBlock;

        String funcName = LLVMUtils.COMPRESS_FUNCTION_NAME + (nonNull ? "_nonNull" : "") + "_" + shift;
        LLVMValueRef func = addFunction(funcName, functionType(objectType(true), objectType(false), longType()));
        LLVM.LLVMSetLinkage(func, LLVM.LLVMLinkOnceAnyLinkage);
        setAttribute(func, LLVM.LLVMAttributeFunctionIndex, LLVMUtils.ALWAYS_INLINE);
        setAttribute(func, LLVM.LLVMAttributeFunctionIndex, LLVMUtils.GC_LEAF_FUNCTION_NAME);

        LLVMBasicBlockRef block = appendBasicBlock("main", func);
        positionAtEnd(block);
        LLVMValueRef uncompressed = buildPtrToInt(getParam(func, 0), longType());
        LLVMValueRef heapBase = getParam(func, 1);
        LLVMValueRef compressed = buildSub(uncompressed, heapBase);

        if (!nonNull) {
            LLVMValueRef isNull = buildIsNull(uncompressed);
            compressed = buildSelect(isNull, uncompressed, compressed);
        }

        if (shift != 0) {
            compressed = buildShr(compressed, constantInt(shift));
        }

        compressed = buildIntToPtr(compressed, objectType(true));
        buildRet(compressed);

        positionAtEnd(previousBlock);

        return func;
    }

    private LLVMValueRef buildUncompressFunction(int shift) {
        return buildUncompressFunction(false, shift);
    }

    private LLVMValueRef buildNonNullUncompressFunction(int shift) {
        return buildUncompressFunction(true, shift);
    }

    private LLVMValueRef buildUncompressFunction(boolean nonNull, int shift) {
        LLVMBasicBlockRef previousBlock = currentBlock;

        String funcName = LLVMUtils.UNCOMPRESS_FUNCTION_NAME + (nonNull ? "_nonNull" : "") + "_" + shift;
        LLVMValueRef func = addFunction(funcName, functionType(objectType(false), objectType(true), longType()));
        LLVM.LLVMSetLinkage(func, LLVM.LLVMLinkOnceAnyLinkage);
        setAttribute(func, LLVM.LLVMAttributeFunctionIndex, LLVMUtils.ALWAYS_INLINE);
        setAttribute(func, LLVM.LLVMAttributeFunctionIndex, LLVMUtils.GC_LEAF_FUNCTION_NAME);

        LLVMBasicBlockRef block = appendBasicBlock("main", func);
        positionAtEnd(block);
        LLVMValueRef compressed = buildPtrToInt(getParam(func, 0), longType());
        LLVMValueRef heapBase = getParam(func, 1);

        if (shift != 0) {
            compressed = buildShl(compressed, constantInt(shift));
        }

        LLVMValueRef uncompressed = buildAdd(compressed, heapBase);
        if (!nonNull) {
            LLVMValueRef isNull = buildIsNull(compressed);
            uncompressed = buildSelect(isNull, compressed, uncompressed);
        }

        uncompressed = buildIntToPtr(uncompressed, objectType(false));
        buildRet(uncompressed);

        positionAtEnd(previousBlock);

        return func;
    }

    private LLVMValueRef buildAtomicObjectXchgFunction(boolean compressed) {
        String funcName = compressed ? LLVMUtils.ATOMIC_COMPRESSED_OBJECT_XCHG_FUNCTION_NAME : LLVMUtils.ATOMIC_OBJECT_XCHG_FUNCTION_NAME;
        LLVMValueRef func = addFunction(funcName, functionType(objectType(compressed), objectType(false), objectType(compressed)));
        LLVM.LLVMSetLinkage(func, LLVM.LLVMLinkOnceAnyLinkage);
        setAttribute(func, LLVM.LLVMAttributeFunctionIndex, LLVMUtils.ALWAYS_INLINE);
        setAttribute(func, LLVM.LLVMAttributeFunctionIndex, LLVMUtils.GC_LEAF_FUNCTION_NAME);

        LLVMBasicBlockRef block = appendBasicBlock("main", func);
        positionAtEnd(block);
        LLVMValueRef address = getParam(func, 0);
        LLVMValueRef value = getParam(func, 1);
        LLVMValueRef castedValue = buildPtrToInt(value, longType());
        LLVMValueRef ret = buildAtomicRMW(LLVM.LLVMAtomicRMWBinOpXchg, address, castedValue);
        ret = buildIntToPtr(ret, objectType(compressed));
        buildRet(ret);

        return func;
    }

    private LLVMValueRef buildLoadObjectFromUntrackedPointerFunction(boolean compressed) {
        String funcName = compressed ? LLVMUtils.LOAD_COMPRESSED_OBJECT_FROM_UNTRACKED_POINTER_FUNCTION_NAME : LLVMUtils.LOAD_OBJECT_FROM_UNTRACKED_POINTER_FUNCTION_NAME;
        LLVMValueRef func = addFunction(funcName, functionType(objectType(compressed), rawPointerType()));
        LLVM.LLVMSetLinkage(func, LLVM.LLVMLinkOnceAnyLinkage);
        setAttribute(func, LLVM.LLVMAttributeFunctionIndex, LLVMUtils.ALWAYS_INLINE);
        setAttribute(func, LLVM.LLVMAttributeFunctionIndex, LLVMUtils.GC_LEAF_FUNCTION_NAME);

        LLVMBasicBlockRef block = appendBasicBlock("main", func);
        positionAtEnd(block);
        LLVMValueRef address = getParam(func, 0);
        LLVMValueRef castedAddress = buildBitcast(address, pointerType(objectType(compressed), false, false));
        LLVMValueRef loadedValue = buildLoad(castedAddress);
        buildRet(loadedValue);

        return func;
    }

    public LLVMModuleRef getModule() {
        return module;
    }

    public byte[] getBitcode() {
        if (LLVM.LLVMVerifyModule(module, LLVM.LLVMPrintMessageAction, new BytePointer(NULL)) == TRUE) {
            LLVM.LLVMDumpModule(module);
            throw new GraalError("LLVM module verification failed");
        }

        LLVMMemoryBufferRef buffer = LLVM.LLVMWriteBitcodeToMemoryBuffer(module);
        LLVM.LLVMDisposeModule(module);
        LLVM.LLVMDisposeBuilder(builder);
        LLVM.LLVMContextDispose(context);

        BytePointer start = LLVM.LLVMGetBufferStart(buffer);
        int size = NumUtil.safeToInt(LLVM.LLVMGetBufferSize(buffer));
        byte[] bitcode = new byte[size];
        start.get(bitcode, 0, size);
        LLVM.LLVMDisposeMemoryBuffer(buffer);

        return bitcode;
    }

    public String getFunctionName() {
        return functionName;
    }

    /* Functions */

    LLVMValueRef addFunction(String name, LLVMTypeRef type) {
        return LLVM.LLVMAddFunction(module, name, type);
    }

    public void addMainFunction(LLVMTypeRef type) {
        this.function = addFunction(functionName, type);
        LLVM.LLVMSetGC(function, "compressed-pointer");
        setLinkage(function, LLVM.LLVMExternalLinkage);
        setAttribute(function, LLVM.LLVMAttributeFunctionIndex, "noinline");
        setAttribute(function, LLVM.LLVMAttributeFunctionIndex, "noredzone");
        setAttribute(function, LLVM.LLVMAttributeFunctionIndex, "no-realign-stack");
    }

    public LLVMValueRef getMainFunction() {
        return function;
    }

    public void addAlias(String alias) {
        LLVM.LLVMAddAlias(getModule(), LLVM.LLVMTypeOf(getMainFunction()), getMainFunction(), alias);
    }

    private static void setLinkage(LLVMValueRef global, int linkage) {
        LLVM.LLVMSetLinkage(global, linkage);
    }

    public void setAttribute(LLVMValueRef func, long index, String attribute) {
        int kind = LLVM.LLVMGetEnumAttributeKindForName(attribute, attribute.length());
        LLVMAttributeRef attr;
        if (kind != 0) {
            attr = LLVM.LLVMCreateEnumAttribute(context, kind, TRUE);
        } else {
            String value = "true";
            attr = LLVM.LLVMCreateStringAttribute(context, attribute, attribute.length(), value, value.length());
        }
        LLVM.LLVMAddAttributeAtIndex(func, (int) index, attr);
    }

    void setCallSiteAttribute(LLVMValueRef call, long index, String attribute) {
        int kind = LLVM.LLVMGetEnumAttributeKindForName(attribute, attribute.length());
        LLVMAttributeRef attr;
        if (kind != 0) {
            attr = LLVM.LLVMCreateEnumAttribute(context, kind, TRUE);
        } else {
            String value = "true";
            attr = LLVM.LLVMCreateStringAttribute(context, attribute, attribute.length(), value, value.length());
        }
        LLVM.LLVMAddCallSiteAttribute(call, (int) index, attr);
    }

    void setPersonalityFunction(LLVMValueRef function, LLVMValueRef personality) {
        LLVM.LLVMSetPersonalityFn(function, personality);
    }

    public void setPersonalityFunction(LLVMValueRef personality) {
        setPersonalityFunction(getMainFunction(), personality);
    }

    /*
     * Calling a native function from Java code requires filling the JavaFrameAnchor with the return
     * address of the call. This wrapper allows this by creating an intermediary call frame from
     * which the return address can be accessed. The parameters to this wrapper are the anchor, the
     * native callee, and the arguments to the callee.
     */
    @SuppressWarnings("unused")
    public LLVMValueRef createJNIWrapper(LLVMValueRef callee, long statepointId, int numArgs, int anchorIPOffset) {
        LLVMTypeRef calleeType = LLVMIRBuilder.getElementType(typeOf(callee));
        String wrapperName = LLVMUtils.JNI_WRAPPER_PREFIX + intrinsicType(calleeType);

        LLVMValueRef transitionWrapper = LLVM.LLVMGetNamedFunction(module, wrapperName);
        if (transitionWrapper == null) {
            LLVMBasicBlockRef previousBlock = currentBlock;

            LLVMTypeRef wrapperType = prependArguments(calleeType, rawPointerType(), typeOf(callee));
            transitionWrapper = addFunction(wrapperName, wrapperType);
            LLVM.LLVMSetLinkage(transitionWrapper, LLVM.LLVMLinkOnceAnyLinkage);
            LLVM.LLVMSetGC(transitionWrapper, "compressed-pointer");
            setAttribute(transitionWrapper, LLVM.LLVMAttributeFunctionIndex, "noinline");

            LLVMBasicBlockRef block = appendBasicBlock("main", transitionWrapper);
            positionAtEnd(block);

            LLVMValueRef anchor = getParam(transitionWrapper, 0);
            LLVMValueRef lastIPAddr = buildGEP(anchor, constantInt(anchorIPOffset));
            LLVMValueRef callIP = buildReturnAddress(constantInt(0));
            buildStore(callIP, lastIPAddr);

            LLVMValueRef[] args = new LLVMValueRef[numArgs];
            for (int i = 0; i < numArgs; ++i) {
                args[i] = getParam(transitionWrapper, i + 2);
            }
            LLVMValueRef target = getParam(transitionWrapper, 1);
            LLVMValueRef ret = buildCall(target, args);
            setCallSiteAttribute(ret, LLVM.LLVMAttributeFunctionIndex, LLVMUtils.GC_LEAF_FUNCTION_NAME);

            if (isVoidType(getReturnType(calleeType))) {
                buildRetVoid();
            } else {
                buildRet(ret);
            }

            positionAtEnd(previousBlock);
        }
        return transitionWrapper;
    }

    LLVMBasicBlockRef appendBasicBlock(String name, LLVMValueRef func) {
        return LLVM.LLVMAppendBasicBlockInContext(context, func, name);
    }

    public LLVMBasicBlockRef appendBasicBlock(String name) {
        return appendBasicBlock(name, function);
    }

    void positionAtStart() {
        LLVMBasicBlockRef firstBlock = LLVM.LLVMGetFirstBasicBlock(function);
        LLVMValueRef firstInstruction = LLVM.LLVMGetFirstInstruction(firstBlock);
        if (firstInstruction != null) {
            LLVM.LLVMPositionBuilderBefore(builder, firstInstruction);
            currentBlock = firstBlock;
        }
    }

    public void positionAtEnd(LLVMBasicBlockRef block) {
        LLVM.LLVMPositionBuilderAtEnd(builder, block);
        currentBlock = block;
    }

    LLVMValueRef blockTerminator(LLVMBasicBlockRef block) {
        return LLVM.LLVMGetBasicBlockTerminator(block);
    }

    /* Types */

    LLVMTypeRef getLLVMStackType(JavaKind kind) {
        return getLLVMType(kind.getStackKind(), false);
    }

    LLVMTypeRef getLLVMType(JavaKind kind, boolean compressedObjects) {
        switch (kind) {
            case Boolean:
                return booleanType();
            case Byte:
                return byteType();
            case Short:
                return shortType();
            case Char:
                return charType();
            case Int:
                return intType();
            case Float:
                return floatType();
            case Long:
                return longType();
            case Double:
                return doubleType();
            case Object:
                return objectType(compressedObjects);
            case Void:
                return voidType();
            case Illegal:
            default:
                throw shouldNotReachHere("Illegal type");
        }
    }

    static LLVMTypeRef typeOf(LLVMValueRef value) {
        return LLVM.LLVMTypeOf(value);
    }

    LLVMTypeRef booleanType() {
        return integerType(1);
    }

    LLVMTypeRef byteType() {
        return integerType(8);
    }

    private LLVMTypeRef shortType() {
        return integerType(16);
    }

    private LLVMTypeRef charType() {
        return integerType(16);
    }

    public LLVMTypeRef intType() {
        return integerType(32);
    }

    public LLVMTypeRef longType() {
        return integerType(64);
    }

    private LLVMTypeRef integerType(int bits) {
        return LLVM.LLVMIntTypeInContext(context, bits);
    }

    LLVMTypeRef floatType() {
        return LLVM.LLVMFloatTypeInContext(context);
    }

    LLVMTypeRef doubleType() {
        return LLVM.LLVMDoubleTypeInContext(context);
    }

    /*
     * Pointer types can be of two types: references and regular pointers. References are pointers
     * to Java objects which are tracked by the GC statepoint emission pass to create reference maps
     * at call sites. Regular pointers are not tracked and represent non-java pointers. They are
     * distinguished by the pointer address space they live in (1, resp. 0).
     */
    public LLVMTypeRef pointerType(LLVMTypeRef type, boolean tracked, boolean compressed) {
        return LLVM.LLVMPointerType(type, pointerAddressSpace(tracked, compressed));
    }

    private static int pointerAddressSpace(boolean tracked, boolean compressed) {
        assert tracked || !compressed;
        return tracked ? (compressed ? LLVMUtils.COMPRESSED_POINTER_ADDRESS_SPACE : LLVMUtils.TRACKED_POINTER_ADDRESS_SPACE) : LLVMUtils.UNTRACKED_POINTER_ADDRESS_SPACE;
    }

    public LLVMTypeRef objectType(boolean compressed) {
        return pointerType(byteType(), true, compressed);
    }

    public LLVMTypeRef rawPointerType() {
        return pointerType(byteType(), false, false);
    }

    public LLVMTypeRef arrayType(LLVMTypeRef type, int length) {
        return LLVM.LLVMArrayType(type, length);
    }

    public LLVMTypeRef structType(LLVMTypeRef... types) {
        return LLVM.LLVMStructTypeInContext(context, new PointerPointer<>(types), types.length, FALSE);
    }

    LLVMTypeRef functionType(LLVMTypeRef returnType, LLVMTypeRef... argTypes) {
        return functionType(returnType, false, argTypes);
    }

    LLVMTypeRef functionType(LLVMTypeRef returnType, boolean varargs, LLVMTypeRef... argTypes) {
        return LLVM.LLVMFunctionType(returnType, new PointerPointer<>(argTypes), argTypes.length, varargs ? TRUE : FALSE);
    }

    private static LLVMTypeRef getReturnType(LLVMTypeRef functionType) {
        return LLVM.LLVMGetReturnType(functionType);
    }

    private static boolean isFunctionVarArg(LLVMTypeRef functionType) {
        return LLVM.LLVMIsFunctionVarArg(functionType) == TRUE;
    }

    /**
     * Creates a new function type based on the given one with the given argument types prepended to
     * the original ones.
     */
    public LLVMTypeRef prependArguments(LLVMTypeRef functionType, LLVMTypeRef... typesToAdd) {
        LLVMTypeRef returnType = getReturnType(functionType);
        boolean varargs = isFunctionVarArg(functionType);
        LLVMTypeRef[] oldTypes = getParamTypes(functionType);

        LLVMTypeRef[] newTypes = new LLVMTypeRef[oldTypes.length + typesToAdd.length];
        System.arraycopy(typesToAdd, 0, newTypes, 0, typesToAdd.length);
        System.arraycopy(oldTypes, 0, newTypes, typesToAdd.length, oldTypes.length);

        return functionType(returnType, varargs, newTypes);
    }

    private static LLVMTypeRef[] getParamTypes(LLVMTypeRef functionType) {
        int numParams = LLVM.LLVMCountParamTypes(functionType);
        PointerPointer<LLVMTypeRef> argTypesPointer = new PointerPointer<>(numParams);
        LLVM.LLVMGetParamTypes(functionType, argTypesPointer);
        return IntStream.range(0, numParams).mapToObj(i -> argTypesPointer.get(LLVMTypeRef.class, i)).toArray(LLVMTypeRef[]::new);
    }

    private LLVMTypeRef voidType() {
        return LLVM.LLVMVoidTypeInContext(context);
    }

    LLVMTypeRef vectorType(LLVMTypeRef type, int count) {
        return LLVM.LLVMVectorType(type, count);
    }

    private LLVMTypeRef tokenType() {
        return LLVM.LLVMTokenTypeInContext(context);
    }

    private LLVMTypeRef metadataType() {
        return LLVM.LLVMMetadataTypeInContext(context);
    }

    static boolean isIntegerType(LLVMTypeRef type) {
        return LLVM.LLVMGetTypeKind(type) == LLVM.LLVMIntegerTypeKind;
    }

    static int integerTypeWidth(LLVMTypeRef intType) {
        return LLVM.LLVMGetIntTypeWidth(intType);
    }

    static boolean isFloatType(LLVMTypeRef type) {
        return LLVM.LLVMGetTypeKind(type) == LLVM.LLVMFloatTypeKind;
    }

    static boolean isDoubleType(LLVMTypeRef type) {
        return LLVM.LLVMGetTypeKind(type) == LLVM.LLVMDoubleTypeKind;
    }

    static boolean isPointer(LLVMTypeRef type) {
        return LLVM.LLVMGetTypeKind(type) == LLVM.LLVMPointerTypeKind;
    }

    private static boolean isTracked(LLVMTypeRef pointerType) {
        int addressSpace = LLVM.LLVMGetPointerAddressSpace(pointerType);
        return addressSpace == LLVMUtils.TRACKED_POINTER_ADDRESS_SPACE || addressSpace == LLVMUtils.COMPRESSED_POINTER_ADDRESS_SPACE;
    }

    static boolean isCompressed(LLVMTypeRef pointerType) {
        int addressSpace = LLVM.LLVMGetPointerAddressSpace(pointerType);
        assert addressSpace == LLVMUtils.COMPRESSED_POINTER_ADDRESS_SPACE || addressSpace == LLVMUtils.TRACKED_POINTER_ADDRESS_SPACE;
        return addressSpace == LLVMUtils.COMPRESSED_POINTER_ADDRESS_SPACE;
    }

    private static LLVMTypeRef getElementType(LLVMTypeRef pointerType) {
        return LLVM.LLVMGetElementType(pointerType);
    }

    static boolean isObject(LLVMTypeRef type) {
        return isPointer(type) && isTracked(type);
    }

    static boolean isRawPointer(LLVMTypeRef type) {
        return isPointer(type) && !isTracked(type);
    }

    static boolean isVoidType(LLVMTypeRef type) {
        return LLVM.LLVMGetTypeKind(type) == LLVM.LLVMVoidTypeKind;
    }

    static boolean compatibleTypes(LLVMTypeRef a, LLVMTypeRef b) {
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

    private static String intrinsicType(LLVMTypeRef type) {
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
            default:
                throw shouldNotReachHere();
        }
    }

    /* Constants */

    LLVMValueRef constantBoolean(boolean x) {
        return constantInteger(x ? TRUE : FALSE, 1);
    }

    LLVMValueRef constantByte(byte x) {
        return constantInteger(x, 8);
    }

    LLVMValueRef constantShort(short x) {
        return constantInteger(x, 16);
    }

    LLVMValueRef constantChar(char x) {
        return constantInteger(x, 16);
    }

    public LLVMValueRef constantInt(int x) {
        return constantInteger(x, 32);
    }

    public LLVMValueRef constantLong(long x) {
        return constantInteger(x, 64);
    }

    LLVMValueRef constantInteger(long value, int bits) {
        return LLVM.LLVMConstInt(integerType(bits), value, FALSE);
    }

    LLVMValueRef constantFloat(float x) {
        return LLVM.LLVMConstReal(floatType(), x);
    }

    LLVMValueRef constantDouble(double x) {
        return LLVM.LLVMConstReal(doubleType(), x);
    }

    public LLVMValueRef constantNull(LLVMTypeRef type) {
        return LLVM.LLVMConstNull(type);
    }

    LLVMValueRef buildGlobalStringPtr(String name) {
        return LLVM.LLVMBuildGlobalStringPtr(builder, name, DEFAULT_INSTR_NAME);
    }

    LLVMValueRef constantString(String string) {
        return LLVM.LLVMConstStringInContext(context, string, string.length(), FALSE);
    }

    LLVMValueRef constantVector(LLVMValueRef... values) {
        return LLVM.LLVMConstVector(new PointerPointer<>(values), values.length);
    }

    /* Values */

    LLVMValueRef getFunction(String name, LLVMTypeRef type) {
        LLVMValueRef func = LLVM.LLVMGetNamedFunction(module, name);
        if (func == null) {
            func = addFunction(name, type);
            setLinkage(func, LLVM.LLVMExternalLinkage);
        }

        return func;
    }

    private static LLVMValueRef getParam(LLVMValueRef func, int index) {
        return LLVM.LLVMGetParam(func, index);
    }

    public LLVMValueRef getParam(int index) {
        return getParam(function, index);
    }

    LLVMValueRef buildPhi(LLVMTypeRef phiType, LLVMValueRef[] incomingValues, LLVMBasicBlockRef[] incomingBlocks) {
        LLVMValueRef phi = LLVM.LLVMBuildPhi(builder, phiType, DEFAULT_INSTR_NAME);
        addIncoming(phi, incomingValues, incomingBlocks);
        return phi;
    }

    void addIncoming(LLVMValueRef phi, LLVMValueRef[] values, LLVMBasicBlockRef[] blocks) {
        assert values.length == blocks.length;
        LLVM.LLVMAddIncoming(phi, new PointerPointer<>(values), new PointerPointer<>(blocks), blocks.length);
    }

    /*
     * External globals are globals which are not created by LLVM but need to be accessed from the
     * code, while unique globals are values created by the LLVM backend, potentially in multiple
     * functions, and are then conflated by the LLVM linker.
     */
    LLVMValueRef getExternalObject(String name, boolean compressed) {
        LLVMValueRef val = getGlobal(name);
        if (val == null) {
            val = LLVM.LLVMAddGlobalInAddressSpace(module, objectType(compressed), name, pointerAddressSpace(true, false));
            setLinkage(val, LLVM.LLVMExternalLinkage);
        }
        return val;
    }

    public LLVMValueRef getExternalSymbol(String name) {
        LLVMValueRef val = getGlobal(name);
        if (val == null) {
            val = LLVM.LLVMAddGlobalInAddressSpace(module, rawPointerType(), name, LLVMUtils.UNTRACKED_POINTER_ADDRESS_SPACE);
            setLinkage(val, LLVM.LLVMExternalLinkage);
        }
        return val;
    }

    public LLVMValueRef getUniqueGlobal(String name, LLVMTypeRef type, boolean zeroInitialized) {
        LLVMValueRef global = getGlobal(name);
        if (global == null) {
            global = LLVM.LLVMAddGlobalInAddressSpace(module, type, name, pointerAddressSpace(isObject(type), false));
            if (zeroInitialized) {
                setInitializer(global, LLVM.LLVMConstNull(type));
            }
            setLinkage(global, LLVM.LLVMLinkOnceODRLinkage);
        }
        return global;
    }

    private LLVMValueRef getGlobal(String name) {
        return LLVM.LLVMGetNamedGlobal(module, name);
    }

    void setInitializer(LLVMValueRef global, LLVMValueRef value) {
        LLVM.LLVMSetInitializer(global, value);
    }

    public LLVMValueRef register(String name) {
        String nameEncoding = name + "\00";
        LLVMValueRef[] vals = new LLVMValueRef[]{LLVM.LLVMMDStringInContext(context, nameEncoding, nameEncoding.length())};
        return LLVM.LLVMMDNodeInContext(context, new PointerPointer<>(vals), vals.length);
    }

    public LLVMValueRef buildReadRegister(LLVMValueRef register) {
        LLVMTypeRef readRegisterType = functionType(longType(), metadataType());
        return buildIntrinsicCall("llvm.read_register.i64", readRegisterType, register);
    }

    LLVMValueRef buildExtractValue(LLVMValueRef struct, int i) {
        return LLVM.LLVMBuildExtractValue(builder, struct, i, DEFAULT_INSTR_NAME);
    }

    LLVMValueRef buildExtractElement(LLVMValueRef vector, LLVMValueRef index) {
        return LLVM.LLVMBuildExtractElement(builder, vector, index, DEFAULT_INSTR_NAME);
    }

    public void setMetadata(LLVMValueRef instr, String kind, LLVMValueRef metadata) {
        LLVM.LLVMSetMetadata(instr, LLVM.LLVMGetMDKindIDInContext(context, kind, kind.length()), metadata);
    }

    public void setValueName(LLVMValueRef value, String name) {
        LLVM.LLVMSetValueName(value, name);
    }

    /* Control flow */
    public static final AtomicLong nextPatchpointId = new AtomicLong(0);

    public LLVMValueRef buildCall(LLVMValueRef callee, LLVMValueRef... args) {
        return LLVM.LLVMBuildCall(builder, callee, new PointerPointer<>(args), args.length, DEFAULT_INSTR_NAME);
    }

    LLVMValueRef buildCall(LLVMValueRef callee, long statepointId, LLVMValueRef... args) {
        LLVMValueRef result;
        result = buildCall(callee, args);
        addCallSiteAttribute(result, "statepoint-id", Long.toString(statepointId));
        return result;
    }

    private LLVMValueRef buildInvoke(LLVMValueRef callee, LLVMBasicBlockRef successor, LLVMBasicBlockRef handler, LLVMValueRef... args) {
        return LLVM.LLVMBuildInvoke(builder, callee, new PointerPointer<>(args), args.length, successor, handler, DEFAULT_INSTR_NAME);
    }

    LLVMValueRef buildInvoke(LLVMValueRef callee, LLVMBasicBlockRef successor, LLVMBasicBlockRef handler, long statepointId, LLVMValueRef... args) {
        LLVMValueRef result;
        result = buildInvoke(callee, successor, handler, args);
        addCallSiteAttribute(result, "statepoint-id", Long.toString(statepointId));
        return result;
    }

    private void addCallSiteAttribute(LLVMValueRef call, String key, String value) {
        LLVMAttributeRef attribute = LLVM.LLVMCreateStringAttribute(context, key, key.length(), value, value.length());
        LLVM.LLVMAddCallSiteAttribute(call, (int) LLVM.LLVMAttributeFunctionIndex, attribute);
    }

    private LLVMValueRef buildIntrinsicCall(String name, LLVMTypeRef type, LLVMValueRef... args) {
        LLVMValueRef intrinsic = getFunction(name, type);
        return buildCall(intrinsic, args);
    }

    void buildRetVoid() {
        LLVM.LLVMBuildRetVoid(builder);
    }

    void buildRet(LLVMValueRef value) {
        LLVM.LLVMBuildRet(builder, value);
    }

    void buildBranch(LLVMBasicBlockRef block) {
        LLVM.LLVMBuildBr(builder, block);
    }

    LLVMValueRef buildIf(LLVMValueRef condition, LLVMBasicBlockRef thenBlock, LLVMBasicBlockRef elseBlock) {
        return LLVM.LLVMBuildCondBr(builder, condition, thenBlock, elseBlock);
    }

    LLVMValueRef buildSwitch(LLVMValueRef value, LLVMBasicBlockRef defaultBlock, LLVMValueRef[] switchValues, LLVMBasicBlockRef[] switchBlocks) {
        assert switchValues.length == switchBlocks.length;

        LLVMValueRef switchVal = LLVM.LLVMBuildSwitch(builder, value, defaultBlock, switchBlocks.length);
        for (int i = 0; i < switchBlocks.length; ++i) {
            LLVM.LLVMAddCase(switchVal, switchValues[i], switchBlocks[i]);
        }
        return switchVal;
    }

    public LLVMValueRef buildLandingPad() {
        LLVMValueRef landingPad = LLVM.LLVMBuildLandingPad(builder, tokenType(), null, 1, DEFAULT_INSTR_NAME);
        LLVM.LLVMAddClause(landingPad, constantNull(rawPointerType()));
        return landingPad;
    }

    public void buildUnreachable() {
        LLVM.LLVMBuildUnreachable(builder);
    }

    public void buildStackmap(LLVMValueRef patchpointId, LLVMValueRef... liveValues) {
        LLVMTypeRef stackmapType = functionType(voidType(), true, longType(), intType());

        LLVMValueRef[] allArgs = new LLVMValueRef[2 + liveValues.length];
        allArgs[0] = patchpointId;
        allArgs[1] = constantInt(0);
        System.arraycopy(liveValues, 0, allArgs, 2, liveValues.length);

        buildIntrinsicCall("llvm.experimental.stackmap", stackmapType, allArgs);
    }

    public void buildDebugtrap() {
        buildIntrinsicCall("llvm.debugtrap", functionType(voidType()));
    }

    LLVMValueRef buildInlineAsm(LLVMTypeRef functionType, String asm, String constraints, boolean hasSideEffects, boolean alignStack) {
        return LLVM.LLVMConstInlineAsm(functionType, asm, constraints, hasSideEffects ? TRUE : FALSE, alignStack ? TRUE : FALSE);
    }

    public LLVMValueRef functionEntryCount(LLVMValueRef count) {
        String functionEntryCountName = "function_entry_count";
        LLVMValueRef[] values = new LLVMValueRef[2];
        values[0] = LLVM.LLVMMDStringInContext(context, functionEntryCountName, functionEntryCountName.length());
        values[1] = count;
        return LLVM.LLVMMDNodeInContext(context, new PointerPointer<>(values), values.length);
    }

    LLVMValueRef branchWeights(LLVMValueRef... weights) {
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

    LLVMValueRef buildCompare(Condition cond, LLVMValueRef a, LLVMValueRef b, boolean unordered) {
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
        return LLVM.LLVMBuildICmp(builder, getLLVMIntCond(cond), a, b, DEFAULT_INSTR_NAME);
    }

    private LLVMValueRef buildFCmp(Condition cond, LLVMValueRef a, LLVMValueRef b, boolean unordered) {
        return LLVM.LLVMBuildFCmp(builder, getLLVMRealCond(cond, unordered), a, b, DEFAULT_INSTR_NAME);
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

    LLVMValueRef buildNeg(LLVMValueRef a) {
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

    LLVMValueRef buildMul(LLVMValueRef a, LLVMValueRef b) {
        return buildBinaryNumberOp(a, b, LLVM::LLVMBuildMul, LLVM::LLVMBuildFMul);
    }

    LLVMValueRef buildDiv(LLVMValueRef a, LLVMValueRef b) {
        return buildBinaryNumberOp(a, b, LLVM::LLVMBuildSDiv, LLVM::LLVMBuildFDiv);
    }

    LLVMValueRef buildRem(LLVMValueRef a, LLVMValueRef b) {
        return buildBinaryNumberOp(a, b, LLVM::LLVMBuildSRem, LLVM::LLVMBuildFRem);
    }

    LLVMValueRef buildUDiv(LLVMValueRef a, LLVMValueRef b) {
        return buildBinaryNumberOp(a, b, LLVM::LLVMBuildUDiv, null);
    }

    LLVMValueRef buildURem(LLVMValueRef a, LLVMValueRef b) {
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

    LLVMValueRef buildAbs(LLVMValueRef a) {
        return buildIntrinsicOp("fabs", a);
    }

    LLVMValueRef buildLog(LLVMValueRef a) {
        return buildIntrinsicOp("log", a);
    }

    LLVMValueRef buildLog10(LLVMValueRef a) {
        return buildIntrinsicOp("log10", a);
    }

    LLVMValueRef buildSqrt(LLVMValueRef a) {
        return buildIntrinsicOp("sqrt", a);
    }

    LLVMValueRef buildCos(LLVMValueRef a) {
        return buildIntrinsicOp("cos", a);
    }

    LLVMValueRef buildSin(LLVMValueRef a) {
        return buildIntrinsicOp("sin", a);
    }

    LLVMValueRef buildExp(LLVMValueRef a) {
        return buildIntrinsicOp("exp", a);
    }

    LLVMValueRef buildPow(LLVMValueRef a, LLVMValueRef b) {
        return buildIntrinsicOp("pow", a, b);
    }

    public LLVMValueRef buildRound(LLVMValueRef a) {
        LLVMTypeRef type = LLVM.LLVMTypeOf(a);
        LLVMTypeRef returnType;
        String intrinsicName;
        if (isFloatType(type)) {
            returnType = intType();
            intrinsicName = "llvm.lround";
        } else if (isDoubleType(type)) {
            returnType = longType();
            intrinsicName = "llvm.llround";
        } else {
            throw shouldNotReachHere();
        }

        intrinsicName = intrinsicName + "." + intrinsicType(returnType) + "." + intrinsicType(type);
        LLVMTypeRef intrinsicType = functionType(returnType, type);
        return buildIntrinsicCall(intrinsicName, intrinsicType, a);
    }

    public LLVMValueRef buildRint(LLVMValueRef a) {
        return buildIntrinsicOp("round", a);
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

    LLVMValueRef buildBswap(LLVMValueRef a) {
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

    LLVMValueRef buildNot(LLVMValueRef input) {
        return LLVM.LLVMBuildNot(builder, input, DEFAULT_INSTR_NAME);
    }

    LLVMValueRef buildAnd(LLVMValueRef a, LLVMValueRef b) {
        return LLVM.LLVMBuildAnd(builder, a, b, DEFAULT_INSTR_NAME);
    }

    LLVMValueRef buildOr(LLVMValueRef a, LLVMValueRef b) {
        return LLVM.LLVMBuildOr(builder, a, b, DEFAULT_INSTR_NAME);
    }

    LLVMValueRef buildXor(LLVMValueRef a, LLVMValueRef b) {
        return LLVM.LLVMBuildXor(builder, a, b, DEFAULT_INSTR_NAME);
    }

    public LLVMValueRef buildShl(LLVMValueRef a, LLVMValueRef b) {
        return buildShift(LLVM::LLVMBuildShl, a, b);
    }

    public LLVMValueRef buildShr(LLVMValueRef a, LLVMValueRef b) {
        return buildShift(LLVM::LLVMBuildAShr, a, b);
    }

    LLVMValueRef buildUShr(LLVMValueRef a, LLVMValueRef b) {
        return buildShift(LLVM::LLVMBuildLShr, a, b);
    }

    private LLVMValueRef buildShift(BinaryBuilder binaryBuilder, LLVMValueRef a, LLVMValueRef b) {
        return binaryBuilder.build(builder, a, buildIntegerConvert(b, integerTypeWidth(LLVM.LLVMTypeOf(a))), DEFAULT_INSTR_NAME);
    }

    LLVMValueRef buildCtlz(LLVMValueRef a) {
        return buildIntrinsicOp("ctlz", a, constantBoolean(false));
    }

    LLVMValueRef buildCttz(LLVMValueRef a) {
        return buildIntrinsicOp("cttz", a, constantBoolean(false));
    }

    LLVMValueRef buildCtpop(LLVMValueRef a) {
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

    public LLVMValueRef buildRegisterObject(LLVMValueRef pointer, boolean compressed) {
        return buildCall(compressed ? gcRegisterCompressedFunction : gcRegisterFunction, pointer);
    }

    public LLVMValueRef buildIntToPtr(LLVMValueRef value, LLVMTypeRef type) {
        return LLVM.LLVMBuildIntToPtr(builder, value, type, DEFAULT_INSTR_NAME);
    }

    public LLVMValueRef buildPtrToInt(LLVMValueRef value, LLVMTypeRef type) {
        return LLVM.LLVMBuildPtrToInt(builder, value, type, DEFAULT_INSTR_NAME);
    }

    LLVMValueRef buildFPToSI(LLVMValueRef value, LLVMTypeRef type) {
        return LLVM.LLVMBuildFPToSI(builder, value, type, DEFAULT_INSTR_NAME);
    }

    LLVMValueRef buildSIToFP(LLVMValueRef value, LLVMTypeRef type) {
        return LLVM.LLVMBuildSIToFP(builder, value, type, DEFAULT_INSTR_NAME);
    }

    LLVMValueRef buildFPCast(LLVMValueRef value, LLVMTypeRef type) {
        return LLVM.LLVMBuildFPCast(builder, value, type, DEFAULT_INSTR_NAME);
    }

    LLVMValueRef buildIntegerConvert(LLVMValueRef value, int toBits) {
        int fromBits = integerTypeWidth(LLVM.LLVMTypeOf(value));
        if (fromBits < toBits) {
            return (fromBits == 1) ? buildZExt(value, toBits) : buildSExt(value, toBits);
        }
        if (fromBits > toBits) {
            return buildTrunc(value, toBits);
        }
        return value;
    }

    public LLVMValueRef buildTrunc(LLVMValueRef value, int toBits) {
        return LLVM.LLVMBuildTrunc(builder, value, integerType(toBits), DEFAULT_INSTR_NAME);
    }

    LLVMValueRef buildSExt(LLVMValueRef value, int toBits) {
        return LLVM.LLVMBuildSExt(builder, value, integerType(toBits), DEFAULT_INSTR_NAME);
    }

    LLVMValueRef buildZExt(LLVMValueRef value, int toBits) {
        return LLVM.LLVMBuildZExt(builder, value, integerType(toBits), DEFAULT_INSTR_NAME);
    }

    public LLVMValueRef buildCompress(LLVMValueRef uncompressed, LLVMValueRef heapBase, boolean nonNull, int shift) {
        LLVMValueRef compressFunction = nonNull ? nonNullCompressFunctions.computeIfAbsent(shift, this::buildNonNullCompressFunction)
                        : compressFunctions.computeIfAbsent(shift, this::buildCompressFunction);
        return buildCall(compressFunction, uncompressed, heapBase);
    }

    public LLVMValueRef buildUncompress(LLVMValueRef compressed, LLVMValueRef heapBase, boolean nonNull, int shift) {
        LLVMValueRef uncompressFunction = nonNull ? nonNullUncompressFunctions.computeIfAbsent(shift, this::buildNonNullUncompressFunction)
                        : uncompressFunctions.computeIfAbsent(shift, this::buildUncompressFunction);
        return buildCall(uncompressFunction, compressed, heapBase);
    }

    /* Memory */

    public LLVMValueRef buildGEP(LLVMValueRef base, LLVMValueRef... indices) {
        return LLVM.LLVMBuildGEP(builder, base, new PointerPointer<>(indices), indices.length, DEFAULT_INSTR_NAME);
    }

    public LLVMValueRef buildLoad(LLVMValueRef address, LLVMTypeRef type) {
        LLVMTypeRef addressType = LLVM.LLVMTypeOf(address);
        if (isObject(type) && !isObject(addressType)) {
            boolean compressed = isCompressed(type);
            return buildCall(compressed ? loadCompressedObjectFromUntrackedPointerFunction : loadObjectFromUntrackedPointerFunction, address);
        }
        LLVMValueRef castedAddress = buildBitcast(address, pointerType(type, isObject(addressType), false));
        return buildLoad(castedAddress);
    }

    LLVMValueRef buildLoad(LLVMValueRef address) {
        return LLVM.LLVMBuildLoad(builder, address, DEFAULT_INSTR_NAME);
    }

    public void buildStore(LLVMValueRef value, LLVMValueRef address) {
        LLVMTypeRef addressType = LLVM.LLVMTypeOf(address);
        LLVMTypeRef valueType = LLVM.LLVMTypeOf(value);
        LLVMValueRef castedValue = value;
        if (isObject(valueType) && !isObject(addressType)) {
            valueType = rawPointerType();
            castedValue = buildAddrSpaceCast(value, rawPointerType());
        }
        LLVMValueRef castedAddress = buildBitcast(address, pointerType(valueType, isObject(addressType), false));
        LLVM.LLVMBuildStore(builder, castedValue, castedAddress);
    }

    public LLVMValueRef buildArrayAlloca(int slots) {
        return LLVM.LLVMBuildArrayAlloca(builder, rawPointerType(), constantInt(slots), DEFAULT_INSTR_NAME);
    }

    void buildPrefetch(LLVMValueRef address) {
        LLVMTypeRef prefetchType = functionType(voidType(), LLVM.LLVMTypeOf(address), intType(), intType(), intType());
        /* llvm.prefetch(address, WRITE, NO_LOCALITY, DATA) */
        buildIntrinsicCall("llvm.prefetch", prefetchType, address, constantInt(1), constantInt(0), constantInt(1));
    }

    public LLVMValueRef buildReturnAddress(LLVMValueRef level) {
        LLVMTypeRef returnAddressType = functionType(rawPointerType(), intType());
        return buildIntrinsicCall("llvm.returnaddress", returnAddressType, level);
    }

    LLVMValueRef buildFrameAddress(LLVMValueRef level) {
        LLVMTypeRef frameAddressType = functionType(rawPointerType(), intType());
        return buildIntrinsicCall("llvm.frameaddress", frameAddressType, level);
    }

    /* Atomic */

    void buildFence() {
        LLVM.LLVMBuildFence(builder, LLVM.LLVMAtomicOrderingSequentiallyConsistent, FALSE, DEFAULT_INSTR_NAME);
    }

    private static final int LLVM_CMPXCHG_VALUE = 0;
    private static final int LLVM_CMPXCHG_SUCCESS = 1;

    LLVMValueRef buildLogicCmpxchg(LLVMValueRef address, LLVMValueRef expectedValue, LLVMValueRef newValue) {
        return buildCmpxchg(address, expectedValue, newValue, LLVM_CMPXCHG_SUCCESS);
    }

    LLVMValueRef buildValueCmpxchg(LLVMValueRef address, LLVMValueRef expectedValue, LLVMValueRef newValue) {
        return buildCmpxchg(address, expectedValue, newValue, LLVM_CMPXCHG_VALUE);
    }

    private LLVMValueRef buildCmpxchg(LLVMValueRef address, LLVMValueRef expectedValue, LLVMValueRef newValue, int resultIndex) {
        LLVMTypeRef expectedType = LLVM.LLVMTypeOf(expectedValue);
        LLVMTypeRef newType = LLVM.LLVMTypeOf(newValue);
        assert compatibleTypes(expectedType, newType) : dumpValues("invalid cmpxchg arguments", expectedValue, newValue);

        LLVMValueRef castedAddress = buildBitcast(address, pointerType(expectedType, isTracked(typeOf(address)), false));
        String cmpxchgFunctionName = "__llvm_cmpxchg_" + intrinsicType(typeOf(castedAddress)) + "_" + intrinsicType(typeOf(expectedValue)) + "_" + intrinsicType(typeOf(newValue)) +
                        resultIndex;
        LLVMValueRef cmpxchgFunction = LLVM.LLVMGetNamedFunction(module, cmpxchgFunctionName);
        if (cmpxchgFunction == null) {
            LLVMBasicBlockRef previousBlock = currentBlock;

            LLVMTypeRef returnType = resultIndex == LLVM_CMPXCHG_VALUE ? expectedType : booleanType();
            cmpxchgFunction = addFunction(cmpxchgFunctionName, functionType(returnType, typeOf(castedAddress), typeOf(expectedValue), typeOf(newValue)));
            LLVM.LLVMSetLinkage(cmpxchgFunction, LLVM.LLVMLinkOnceAnyLinkage);
            setAttribute(cmpxchgFunction, LLVM.LLVMAttributeFunctionIndex, LLVMUtils.ALWAYS_INLINE);
            setAttribute(cmpxchgFunction, LLVM.LLVMAttributeFunctionIndex, LLVMUtils.GC_LEAF_FUNCTION_NAME);

            LLVMBasicBlockRef block = appendBasicBlock("main", cmpxchgFunction);
            positionAtEnd(block);
            LLVMValueRef addr = getParam(cmpxchgFunction, 0);
            LLVMValueRef expected = getParam(cmpxchgFunction, 1);
            LLVMValueRef newVal = getParam(cmpxchgFunction, 2);
            LLVMValueRef cas = buildAtomicCmpXchg(addr, expected, newVal);
            LLVMValueRef result = buildExtractValue(cas, resultIndex);
            buildRet(result);

            positionAtEnd(previousBlock);
        }

        return buildCall(cmpxchgFunction, castedAddress, expectedValue, newValue);
    }

    private LLVMValueRef buildAtomicCmpXchg(LLVMValueRef addr, LLVMValueRef expected, LLVMValueRef newVal) {
        return LLVM.LLVMBuildAtomicCmpXchg(builder, addr, expected, newVal, LLVM.LLVMAtomicOrderingMonotonic, LLVM.LLVMAtomicOrderingMonotonic, FALSE);
    }

    LLVMValueRef buildAtomicXchg(LLVMValueRef address, LLVMValueRef value) {
        if (isObject(typeOf(value))) {
            boolean compressed = isCompressed(typeOf(value));
            return buildCall(compressed ? atomicCompressedObjectXchgFunction : atomicObjectXchgFunction, address, value);
        }
        return buildAtomicRMW(LLVM.LLVMAtomicRMWBinOpXchg, address, value);
    }

    LLVMValueRef buildAtomicAdd(LLVMValueRef address, LLVMValueRef value) {
        return buildAtomicRMW(LLVM.LLVMAtomicRMWBinOpAdd, address, value);
    }

    private LLVMValueRef buildAtomicRMW(int operation, LLVMValueRef address, LLVMValueRef value) {
        LLVMTypeRef valueType = LLVM.LLVMTypeOf(value);
        LLVMValueRef castedAddress = buildBitcast(address, pointerType(valueType, isObject(typeOf(address)), false));
        return LLVM.LLVMBuildAtomicRMW(builder, operation, castedAddress, value, LLVM.LLVMAtomicOrderingMonotonic, FALSE);
    }

    /* Inline assembly */

    public LLVMValueRef buildInlineGetRegister(String registerName) {
        LLVMValueRef getRegister = buildInlineAsm(functionType(rawPointerType()), TargetSpecific.get().getRegisterInlineAsm(registerName),
                        "={" + TargetSpecific.get().getLLVMRegisterName(registerName) + "}", false, false);
        LLVMValueRef call = buildCall(getRegister);
        setCallSiteAttribute(call, LLVM.LLVMAttributeFunctionIndex, LLVMUtils.GC_LEAF_FUNCTION_NAME);
        return call;
    }

    public LLVMValueRef buildInlineJump(LLVMValueRef address) {
        LLVMValueRef jump = buildInlineAsm(functionType(voidType(), rawPointerType()), TargetSpecific.get().getJumpInlineAsm(), "r", true, false);
        LLVMValueRef call = buildCall(jump, address);
        setCallSiteAttribute(call, LLVM.LLVMAttributeFunctionIndex, LLVMUtils.GC_LEAF_FUNCTION_NAME);
        return call;
    }
}
