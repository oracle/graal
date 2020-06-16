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

import com.oracle.svm.core.graal.llvm.util.LLVMIRBuilder.Attribute;
import com.oracle.svm.core.graal.llvm.util.LLVMIRBuilder.LinkageType;
import com.oracle.svm.shadowed.org.bytedeco.llvm.LLVM.LLVMBasicBlockRef;
import com.oracle.svm.shadowed.org.bytedeco.llvm.LLVM.LLVMTypeRef;
import com.oracle.svm.shadowed.org.bytedeco.llvm.LLVM.LLVMValueRef;

/*
 * These helper functions are used to hide the specific lowerings of some instructions
 * into LLVM bitcode to the statepoint emission pass. This may be needed for two reasons:
 * 
 * 1. The pass doesn't support creating tracked object from these instructions. This is the case for the
 * inttoptr instruction, for example.
 * 2. The lowering includes treating a pointer to a Java object as an untracked pointer temporarily.
 * In this case, the helper function encapsulates the operation to prevent the untracked value from being moved
 * across a function call, which would prevent the statepoint emission pass from registering it properly.
 */
class LLVMHelperFunctions {
    private LLVMIRBuilder builder;

    private LLVMValueRef intToObjectFunction;
    private LLVMValueRef loadObjectFromUntrackedPointerFunction;
    private LLVMValueRef atomicObjectXchgFunction;
    private LLVMValueRef objectsCmpxchgFunction;

    private LLVMValueRef intToCompressedObjectFunction;
    private LLVMValueRef loadCompressedObjectFromUntrackedPointerFunction;
    private LLVMValueRef atomicCompressedObjectXchgFunction;
    private LLVMValueRef compressedObjectsCmpxchgFunction;

    private LLVMValueRef[] compressFunctions;
    private LLVMValueRef[] nonNullCompressFunctions;
    private LLVMValueRef[] uncompressFunctions;
    private LLVMValueRef[] nonNullUncompressFunctions;

    LLVMHelperFunctions(LLVMIRBuilder primary) {
        this.builder = new LLVMIRBuilder(primary);
    }

    LLVMValueRef getIntToObjectFunction(boolean compressed) {
        if (!compressed && intToObjectFunction == null) {
            intToObjectFunction = buildIntToObjectFunction(false);
        } else if (compressed && intToCompressedObjectFunction == null) {
            intToCompressedObjectFunction = buildIntToObjectFunction(true);
        }
        return compressed ? intToCompressedObjectFunction : intToObjectFunction;
    }

    LLVMValueRef getLoadObjectFromUntrackedPointerFunction(boolean compressed) {
        if (!compressed && loadObjectFromUntrackedPointerFunction == null) {
            loadObjectFromUntrackedPointerFunction = buildLoadObjectFromUntrackedPointerFunction(false);
        } else if (compressed && loadCompressedObjectFromUntrackedPointerFunction == null) {
            loadCompressedObjectFromUntrackedPointerFunction = buildLoadObjectFromUntrackedPointerFunction(true);
        }
        return compressed ? loadCompressedObjectFromUntrackedPointerFunction : loadObjectFromUntrackedPointerFunction;
    }

    LLVMValueRef getAtomicObjectXchgFunction(boolean compressed) {
        if (!compressed && atomicObjectXchgFunction == null) {
            atomicObjectXchgFunction = buildAtomicObjectXchgFunction(false);
        } else if (compressed && atomicCompressedObjectXchgFunction == null) {
            atomicCompressedObjectXchgFunction = buildAtomicObjectXchgFunction(true);
        }
        return compressed ? atomicCompressedObjectXchgFunction : atomicObjectXchgFunction;
    }

    LLVMValueRef getCmpxchgFunction(boolean compressed) {
        if (!compressed && objectsCmpxchgFunction == null) {
            objectsCmpxchgFunction = buildObjectsCmpxchgFunction(false);
        } else if (compressed && compressedObjectsCmpxchgFunction == null) {
            compressedObjectsCmpxchgFunction = buildObjectsCmpxchgFunction(true);
        }
        return compressed ? compressedObjectsCmpxchgFunction : objectsCmpxchgFunction;
    }

    private static final int MAX_COMPRESS_SHIFT = 3;

    LLVMValueRef getCompressFunction(boolean nonNull, int shift) {
        if (nonNull) {
            if (nonNullCompressFunctions == null) {
                nonNullCompressFunctions = new LLVMValueRef[MAX_COMPRESS_SHIFT];
            }
            if (nonNullCompressFunctions[shift] == null) {
                nonNullCompressFunctions[shift] = buildCompressFunction(true, shift);
            }
        } else {
            if (compressFunctions == null) {
                compressFunctions = new LLVMValueRef[MAX_COMPRESS_SHIFT];
            }
            if (compressFunctions[shift] == null) {
                compressFunctions[shift] = buildCompressFunction(false, shift);
            }
        }
        return nonNull ? nonNullCompressFunctions[shift] : compressFunctions[shift];
    }

    LLVMValueRef getUncompressFunction(boolean nonNull, int shift) {
        if (nonNull) {
            if (nonNullUncompressFunctions == null) {
                nonNullUncompressFunctions = new LLVMValueRef[MAX_COMPRESS_SHIFT];
            }
            if (nonNullUncompressFunctions[shift] == null) {
                nonNullUncompressFunctions[shift] = buildUncompressFunction(true, shift);
            }
        } else {
            if (uncompressFunctions == null) {
                uncompressFunctions = new LLVMValueRef[MAX_COMPRESS_SHIFT];
            }
            if (uncompressFunctions[shift] == null) {
                uncompressFunctions[shift] = buildUncompressFunction(false, shift);
            }
        }
        return nonNull ? nonNullUncompressFunctions[shift] : uncompressFunctions[shift];
    }

    private static final String INT_TO_OBJECT_FUNCTION_NAME = "__llvm_int_to_object";
    private static final String INT_TO_COMPRESSED_OBJECT_FUNCTION_NAME = "__llvm_int_to_compressed_object";

    private LLVMValueRef buildIntToObjectFunction(boolean compressed) {
        String funcName = compressed ? INT_TO_COMPRESSED_OBJECT_FUNCTION_NAME : INT_TO_OBJECT_FUNCTION_NAME;
        LLVMValueRef func = builder.addFunction(funcName, builder.functionType(builder.objectType(compressed), builder.wordType()));
        LLVMIRBuilder.setLinkage(func, LinkageType.LinkOnce);
        builder.setFunctionAttribute(func, Attribute.AlwaysInline);
        builder.setFunctionAttribute(func, Attribute.GCLeafFunction);

        LLVMBasicBlockRef block = builder.appendBasicBlock(func, "main");
        builder.positionAtEnd(block);
        LLVMValueRef arg = LLVMIRBuilder.getParam(func, 0);
        LLVMValueRef ref = builder.buildLLVMIntToPtr(arg, builder.objectType(compressed));
        builder.buildRet(ref);

        return func;
    }

    private static final String LOAD_OBJECT_FROM_UNTRACKED_POINTER_FUNCTION_NAME = "__llvm_load_object_from_untracked_pointer";
    private static final String LOAD_COMPRESSED_OBJECT_FROM_UNTRACKED_POINTER_FUNCTION_NAME = "__llvm_load_compressed_object_from_untracked_pointer";

    private LLVMValueRef buildLoadObjectFromUntrackedPointerFunction(boolean compressed) {
        String funcName = compressed ? LOAD_COMPRESSED_OBJECT_FROM_UNTRACKED_POINTER_FUNCTION_NAME : LOAD_OBJECT_FROM_UNTRACKED_POINTER_FUNCTION_NAME;
        LLVMValueRef func = builder.addFunction(funcName, builder.functionType(builder.objectType(compressed), builder.rawPointerType()));
        LLVMIRBuilder.setLinkage(func, LinkageType.LinkOnce);
        builder.setFunctionAttribute(func, Attribute.AlwaysInline);
        builder.setFunctionAttribute(func, Attribute.GCLeafFunction);

        LLVMBasicBlockRef block = builder.appendBasicBlock(func, "main");
        builder.positionAtEnd(block);
        LLVMValueRef address = LLVMIRBuilder.getParam(func, 0);
        LLVMValueRef castedAddress = builder.buildBitcast(address, builder.pointerType(builder.objectType(compressed), false, false));
        LLVMValueRef loadedValue = builder.buildLoad(castedAddress);
        builder.buildRet(loadedValue);

        return func;
    }

    private static final String ATOMIC_OBJECT_XCHG_FUNCTION_NAME = "__llvm_atomic_object_xchg";
    private static final String ATOMIC_COMPRESSED_OBJECT_XCHG_FUNCTION_NAME = "__llvm_atomic_compressed_object_xchg";

    private LLVMValueRef buildAtomicObjectXchgFunction(boolean compressed) {
        String funcName = compressed ? ATOMIC_COMPRESSED_OBJECT_XCHG_FUNCTION_NAME : ATOMIC_OBJECT_XCHG_FUNCTION_NAME;
        LLVMValueRef func = builder.addFunction(funcName, builder.functionType(builder.objectType(compressed), builder.objectType(false), builder.objectType(compressed)));
        LLVMIRBuilder.setLinkage(func, LinkageType.LinkOnce);
        builder.setFunctionAttribute(func, Attribute.AlwaysInline);
        builder.setFunctionAttribute(func, Attribute.GCLeafFunction);

        LLVMBasicBlockRef block = builder.appendBasicBlock(func, "main");
        builder.positionAtEnd(block);
        LLVMValueRef address = LLVMIRBuilder.getParam(func, 0);
        LLVMValueRef value = LLVMIRBuilder.getParam(func, 1);
        LLVMValueRef castedValue = builder.buildPtrToInt(value);
        LLVMValueRef ret = builder.buildLLVMAtomicXchg(address, castedValue);
        ret = builder.buildLLVMIntToPtr(ret, builder.objectType(compressed));
        builder.buildRet(ret);

        return func;
    }

    private static final String OBJECTS_CMPXCHG_FUNCTION_NAME = "__llvm_objects_cmpxchg";
    private static final String COMPRESSED_OBJECTS_CMPXCHG_FUNCTION_NAME = "__llvm_compressed_objects_cmpxchg";

    private LLVMValueRef buildObjectsCmpxchgFunction(boolean compressed) {
        String funcName = compressed ? COMPRESSED_OBJECTS_CMPXCHG_FUNCTION_NAME : OBJECTS_CMPXCHG_FUNCTION_NAME;
        LLVMTypeRef exchangeType = builder.objectType(compressed);
        LLVMValueRef func = builder.addFunction(funcName, builder.functionType(exchangeType, builder.pointerType(exchangeType, true, compressed), exchangeType, exchangeType));
        LLVMIRBuilder.setLinkage(func, LinkageType.LinkOnce);
        builder.setFunctionAttribute(func, Attribute.AlwaysInline);
        builder.setFunctionAttribute(func, Attribute.GCLeafFunction);

        LLVMBasicBlockRef block = builder.appendBasicBlock(func, "main");
        builder.positionAtEnd(block);
        LLVMValueRef addr = LLVMIRBuilder.getParam(func, 0);
        LLVMValueRef expected = LLVMIRBuilder.getParam(func, 1);
        LLVMValueRef newVal = LLVMIRBuilder.getParam(func, 2);
        LLVMValueRef result = builder.buildAtomicCmpXchg(addr, expected, newVal, true);
        builder.buildRet(result);

        return func;
    }

    private static final String COMPRESS_FUNCTION_BASE_NAME = "__llvm_compress";

    private LLVMValueRef buildCompressFunction(boolean nonNull, int shift) {
        String funcName = COMPRESS_FUNCTION_BASE_NAME + (nonNull ? "_nonNull" : "") + "_" + shift;
        LLVMValueRef func = builder.addFunction(funcName, builder.functionType(builder.objectType(true), builder.objectType(false), builder.wordType()));
        LLVMIRBuilder.setLinkage(func, LinkageType.LinkOnce);
        builder.setFunctionAttribute(func, Attribute.AlwaysInline);
        builder.setFunctionAttribute(func, Attribute.GCLeafFunction);

        LLVMBasicBlockRef block = builder.appendBasicBlock(func, "main");
        builder.positionAtEnd(block);
        LLVMValueRef uncompressed = builder.buildPtrToInt(LLVMIRBuilder.getParam(func, 0));
        LLVMValueRef heapBase = LLVMIRBuilder.getParam(func, 1);
        LLVMValueRef compressed = builder.buildSub(uncompressed, heapBase);

        if (!nonNull) {
            LLVMValueRef isNull = builder.buildIsNull(uncompressed);
            compressed = builder.buildSelect(isNull, uncompressed, compressed);
        }

        if (shift != 0) {
            compressed = builder.buildShr(compressed, builder.constantInt(shift));
        }

        compressed = builder.buildLLVMIntToPtr(compressed, builder.objectType(true));
        builder.buildRet(compressed);

        return func;
    }

    private static final String UNCOMPRESS_FUNCTION_BASE_NAME = "__llvm_uncompress";

    private LLVMValueRef buildUncompressFunction(boolean nonNull, int shift) {
        String funcName = UNCOMPRESS_FUNCTION_BASE_NAME + (nonNull ? "_nonNull" : "") + "_" + shift;
        LLVMValueRef func = builder.addFunction(funcName, builder.functionType(builder.objectType(false), builder.objectType(true), builder.wordType()));
        LLVMIRBuilder.setLinkage(func, LinkageType.LinkOnce);
        builder.setFunctionAttribute(func, Attribute.AlwaysInline);
        builder.setFunctionAttribute(func, Attribute.GCLeafFunction);

        LLVMBasicBlockRef block = builder.appendBasicBlock(func, "main");
        builder.positionAtEnd(block);
        LLVMValueRef compressed = builder.buildPtrToInt(LLVMIRBuilder.getParam(func, 0));
        LLVMValueRef heapBase = LLVMIRBuilder.getParam(func, 1);

        if (shift != 0) {
            compressed = builder.buildShl(compressed, builder.constantInt(shift));
        }

        LLVMValueRef uncompressed = builder.buildAdd(compressed, heapBase);
        if (!nonNull) {
            LLVMValueRef isNull = builder.buildIsNull(compressed);
            uncompressed = builder.buildSelect(isNull, compressed, uncompressed);
        }

        uncompressed = builder.buildLLVMIntToPtr(uncompressed, builder.objectType(false));
        builder.buildRet(uncompressed);

        return func;
    }
}
