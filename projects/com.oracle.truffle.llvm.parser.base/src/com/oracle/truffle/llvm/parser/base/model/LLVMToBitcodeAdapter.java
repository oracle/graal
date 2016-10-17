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
package com.oracle.truffle.llvm.parser.base.model;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.intel.llvm.ireditor.types.ResolvedArrayType;
import com.intel.llvm.ireditor.types.ResolvedFloatingType;
import com.intel.llvm.ireditor.types.ResolvedFunctionType;
import com.intel.llvm.ireditor.types.ResolvedIntegerType;
import com.intel.llvm.ireditor.types.ResolvedMetadataType;
import com.intel.llvm.ireditor.types.ResolvedNamedType;
import com.intel.llvm.ireditor.types.ResolvedOpaqueType;
import com.intel.llvm.ireditor.types.ResolvedPointerType;
import com.intel.llvm.ireditor.types.ResolvedStructType;
import com.intel.llvm.ireditor.types.ResolvedType;
import com.intel.llvm.ireditor.types.ResolvedUnknownType;
import com.intel.llvm.ireditor.types.ResolvedVarargType;
import com.intel.llvm.ireditor.types.ResolvedVectorType;
import com.intel.llvm.ireditor.types.ResolvedVoidType;
import com.oracle.truffle.llvm.parser.base.model.enums.Linkage;
import com.oracle.truffle.llvm.parser.base.model.functions.FunctionDeclaration;
import com.oracle.truffle.llvm.parser.base.model.globals.GlobalVariable;
import com.oracle.truffle.llvm.parser.base.model.types.ArrayType;
import com.oracle.truffle.llvm.parser.base.model.types.FloatingPointType;
import com.oracle.truffle.llvm.parser.base.model.types.FunctionType;
import com.oracle.truffle.llvm.parser.base.model.types.IntegerType;
import com.oracle.truffle.llvm.parser.base.model.types.MetaType;
import com.oracle.truffle.llvm.parser.base.model.types.PointerType;
import com.oracle.truffle.llvm.parser.base.model.types.StructureType;
import com.oracle.truffle.llvm.parser.base.model.types.Type;
import com.oracle.truffle.llvm.parser.base.model.types.VectorType;
import com.oracle.truffle.llvm.parser.base.util.LLVMParserRuntime;

public final class LLVMToBitcodeAdapter {

    private LLVMToBitcodeAdapter() {
    }

    public static Type[] resolveTypes(ResolvedType[] types) {
        if (types == null) {
            return null;
        }

        Type[] resolvedTypes = new Type[types.length];
        for (int i = 0; i < resolvedTypes.length; i++) {
            resolvedTypes[i] = resolveType(types[i]);
        }
        return resolvedTypes;
    }

    public static Type resolveType(ResolvedType type) {
        if (type == null) {
            return null;
        }

        if (type instanceof ResolvedNamedType) {
            return resolveType((ResolvedNamedType) type);
        } else if (type.isFunction()) {
            return resolveType((ResolvedFunctionType) type);
        } else if (type.isFloating()) {
            return resolveType((ResolvedFloatingType) type);
        } else if (type.isInteger()) {
            return resolveType((ResolvedIntegerType) type);
        } else if (type.isMetadata()) {
            return resolveType((ResolvedMetadataType) type);
        } else if (type.isPointer()) {
            return resolveType((ResolvedPointerType) type);
        } else if (type.isStruct()) {
            return resolveType((ResolvedStructType) type);
        } else if (type.isVararg()) {
            throw new AssertionError("varargs are only expected inside functions");
        } else if (type.isVector()) {
            return resolveType((ResolvedVectorType) type);
        } else if (type.isVoid()) {
            return resolveType((ResolvedVoidType) type);
        } else if (type.isUnknown()) {
            return resolveType((ResolvedUnknownType) type);
        } else if (type instanceof ResolvedArrayType) {
            return resolveType((ResolvedArrayType) type);
        } else if (type instanceof ResolvedOpaqueType) {
            return resolveType((ResolvedOpaqueType) type);
        }

        throw new AssertionError("Unknown type: " + type + " - " + type.getClass().getTypeName());
    }

    private static Map<ResolvedNamedType, Type> namedTypes = new HashMap<>();

    public static Type resolveType(ResolvedNamedType type) {
        if (!namedTypes.containsKey(type)) {
            namedTypes.put(type, MetaType.UNKNOWN); // TODO: resolve cycles
            namedTypes.put(type, resolveType(type.getReferredType()));
        }

        return namedTypes.get(type);
    }

    public static Type resolveType(ResolvedFunctionType type) {
        Type returnType = resolveType(type.getReturnType());
        List<Type> args = new ArrayList<>();
        boolean hasVararg = false;
        for (ResolvedType arg : type.getParameters()) {
            assert !hasVararg; // should be the last element of the parameterlist
            if (arg.isVararg()) {
                hasVararg = true;
            } else {
                args.add(resolveType(arg));
            }
        }
        FunctionType fType = new FunctionType(returnType, args.toArray(new Type[args.size()]), hasVararg);
        return new FunctionDeclaration(fType);
    }

    public static Type resolveType(ResolvedFloatingType type) {
        // fp128 and ppc_fp128 have the same bitwidth so we can only do string comparisons
        final String typestr = type.toString().toLowerCase();
        if (typestr.startsWith("half")) {
            return FloatingPointType.HALF;
        } else if (typestr.startsWith("float")) {
            return FloatingPointType.FLOAT;
        } else if (typestr.startsWith("double")) {
            return FloatingPointType.DOUBLE;
        } else if (typestr.startsWith("x86_fp80")) {
            return FloatingPointType.X86_FP80;
        } else if (typestr.startsWith("fp128")) {
            return FloatingPointType.FP128;
        } else if (typestr.startsWith("ppc_fp128")) {
            return FloatingPointType.PPC_FP128;
        } else {
            throw new AssertionError("Unknown Typestring: " + typestr);
        }
    }

    public static Type resolveType(ResolvedIntegerType type) {
        switch (type.getBits().intValue()) {
            case 1:
                return IntegerType.BOOLEAN;

            case Byte.SIZE:
                return IntegerType.BYTE;

            case Short.SIZE:
                return IntegerType.SHORT;

            case Integer.SIZE:
                return IntegerType.INTEGER;

            case Long.SIZE:
                return IntegerType.LONG;

            default:
                return new IntegerType(type.getBits().intValue());
        }
    }

    public static Type resolveType(@SuppressWarnings("unused") ResolvedMetadataType type) {
        return MetaType.METADATA;
    }

    public static Type resolveType(ResolvedPointerType type) {
        Type pointedType = resolveType(type.getContainedType(-1));
        return new PointerType(pointedType);
    }

    public static Type resolveType(ResolvedStructType type) {
        List<Type> elements = new ArrayList<>();
        for (ResolvedType arg : type.getFieldTypes()) {
            elements.add(resolveType(arg));
        }
        return new StructureType(type.isPacked(), elements.toArray(new Type[elements.size()]));
    }

    public static Type resolveType(ResolvedVectorType type) {
        Type elementType = resolveType(type.getContainedType(-1));
        return new VectorType(elementType, type.getSize());
    }

    public static Type resolveType(@SuppressWarnings("unused") ResolvedVoidType type) {
        return MetaType.VOID;
    }

    public static Type resolveType(@SuppressWarnings("unused") ResolvedUnknownType type) {
        return MetaType.UNKNOWN;
    }

    public static Type resolveType(ResolvedArrayType type) {
        Type elementType = resolveType(type.getContainedType(-1));
        return new ArrayType(elementType, type.getSize());
    }

    public static Type resolveType(@SuppressWarnings("unused") ResolvedOpaqueType type) {
        return MetaType.OPAQUE;
    }

    // temporary solution to convert the new type back to the old one
    public static ResolvedType unresolveType(Type type) {
        if (type == null) {
            return null;
        }

        if (type instanceof FunctionDeclaration) {
            return unresolveType((FunctionDeclaration) type);
        } else if (type instanceof FloatingPointType) {
            return unresolveType((FloatingPointType) type);
        } else if (type instanceof IntegerType) {
            return unresolveType((IntegerType) type);
        } else if (type instanceof MetaType) {
            return unresolveType((MetaType) type);
        } else if (type instanceof PointerType) {
            return unresolveType((PointerType) type);
        } else if (type instanceof StructureType) {
            return unresolveType((StructureType) type);
        } else if (type instanceof ArrayType) {
            return unresolveType((ArrayType) type);
        } else if (type instanceof VectorType) {
            return unresolveType((VectorType) type);
        }

        throw new AssertionError("Unknown type: " + type + " - " + type.getClass().getTypeName());
    }

    public static ResolvedType unresolveType(FunctionDeclaration type) {
        ResolvedType returnType = unresolveType(type.getReturnType());
        List<ResolvedType> paramTypes = new ArrayList<>();
        for (Type t : type.getArgumentTypes()) {
            paramTypes.add(unresolveType(t));
        }
        if (type.isVarArg()) {
            paramTypes.add(new ResolvedVarargType());
        }
        return new ResolvedFunctionType(returnType, paramTypes);
    }

    public static ResolvedType unresolveType(FloatingPointType type) {
        return new ResolvedFloatingType(type.name(), type.width());
    }

    public static ResolvedType unresolveType(IntegerType type) {
        return new ResolvedIntegerType(type.getBits());
    }

    public static ResolvedType unresolveType(MetaType type) {
        switch (type) {
            case UNKNOWN:
                return new ResolvedUnknownType();

            case VOID:
                return new ResolvedVoidType();

            case OPAQUE:
                return new ResolvedOpaqueType();

            case METADATA:
                return new ResolvedMetadataType();

            default:
                throw new AssertionError("Unknown type: " + type);
        }
    }

    public static ResolvedType unresolveType(PointerType type) {
        ResolvedType pointeeType = unresolveType(type.getPointeeType());
        BigInteger addrSpace = new BigInteger(new byte[]{(byte) 0}); // TODO: important?
        return new ResolvedPointerType(pointeeType, addrSpace);
    }

    public static ResolvedType unresolveType(StructureType type) {
        List<ResolvedType> fieldTypes = new ArrayList<>();
        for (int i = 0; i < type.getLength(); i++) {
            fieldTypes.add(unresolveType(type.getElementType(i)));
        }
        return new ResolvedStructType(fieldTypes, type.isPacked(), false);
    }

    public static ResolvedType unresolveType(ArrayType type) {
        ResolvedType elementType = unresolveType(type.getElementType());
        return new ResolvedArrayType(type.getLength(), elementType);
    }

    public static ResolvedType unresolveType(VectorType type) {
        ResolvedType elementType = unresolveType(type.getElementType());
        return new ResolvedVectorType(type.getLength(), elementType);
    }

    private static Linkage resolveLinkage(String linkage) {
        for (final Linkage linkageEnumVal : Linkage.values()) {
            if (linkageEnumVal.getIrString().equalsIgnoreCase(linkage)) {
                return linkageEnumVal;
            }
        }
        return Linkage.UNKNOWN;
    }

    public static GlobalVariable resolveGlobalVariable(LLVMParserRuntime runtime, com.intel.llvm.ireditor.lLVM_IR.GlobalVariable globalVariable) {
        Type type = resolveType(runtime.resolve(globalVariable.getType()));

        String alignString = globalVariable.getAlign();
        int align = alignString != null ? Integer.valueOf(alignString.replaceAll("align ", "")) : 0;

        Linkage linkage = resolveLinkage(globalVariable.getLinkage());

        GlobalVariable glob = GlobalVariable.create(type, 0, align, linkage != null ? linkage.ordinal() : 0);
        glob.setName(globalVariable.getName().substring(1));
        // glob.initialise(globalVariable.getInitialValue());

        return glob;
    }
}
