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

import com.intel.llvm.ireditor.types.ResolvedArrayType;
import com.intel.llvm.ireditor.types.ResolvedFunctionType;
import com.intel.llvm.ireditor.types.ResolvedNamedType;
import com.intel.llvm.ireditor.types.ResolvedOpaqueType;
import com.intel.llvm.ireditor.types.ResolvedPointerType;
import com.intel.llvm.ireditor.types.ResolvedStructType;
import com.intel.llvm.ireditor.types.ResolvedType;
import com.intel.llvm.ireditor.types.ResolvedVectorType;
import com.oracle.truffle.llvm.parser.base.model.types.ArrayType;
import com.oracle.truffle.llvm.parser.base.model.types.FloatingPointType;
import com.oracle.truffle.llvm.parser.base.model.types.FunctionType;
import com.oracle.truffle.llvm.parser.base.model.types.IntegerType;
import com.oracle.truffle.llvm.parser.base.model.types.MetaType;
import com.oracle.truffle.llvm.parser.base.model.types.PointerType;
import com.oracle.truffle.llvm.parser.base.model.types.StructureType;
import com.oracle.truffle.llvm.parser.base.model.types.Type;
import com.oracle.truffle.llvm.parser.base.model.types.VectorType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class TextToBCConverter {

    private TextToBCConverter() {
    }

    public static Type convert(ResolvedType type) {
        return new TextToBCConverter().convert(type, true);
    }

    public static Type[] convertTypes(ResolvedType[] types) {
        if (types == null) {
            return null;
        }

        final Type[] resolvedTypes = new Type[types.length];
        for (int i = 0; i < resolvedTypes.length; i++) {
            resolvedTypes[i] = convert(types[i]);
        }
        return resolvedTypes;
    }

    private interface ReplacingType {
        void replaceType(Type old, Type replacement);
    }

    private static final class InternalType implements Type {
        ResolvedType referredType = null;
        ReplacingType parentType = null;
    }

    private static final class ReplacingVectorType extends VectorType implements ReplacingType {

        private Type elementType;

        ReplacingVectorType(Type type, int length) {
            super(type, length);
            this.elementType = type;
        }

        @Override
        public Type getElementType() {
            return elementType;
        }

        @Override
        public void replaceType(Type old, Type replacement) {
            if (elementType == old) {
                elementType = replacement;
            }
        }
    }

    private static final class ReplacingArrayType extends ArrayType implements ReplacingType {

        private Type elementType;

        ReplacingArrayType(Type type, int length) {
            super(type, length);
            this.elementType = type;
        }

        @Override
        public Type getElementType() {
            return elementType;
        }

        @Override
        public void replaceType(Type old, Type replacement) {
            if (elementType == old) {
                elementType = replacement;
            }
        }
    }

    private static final class ReplacingStructureType extends StructureType implements ReplacingType {

        ReplacingStructureType(boolean isPacked, Type[] types) {
            super(isPacked, types);
        }

        @Override
        public void replaceType(Type old, Type replacement) {
            for (int i = 0; i < types.length; i++) {
                if (types[i] == old) {
                    types[i] = replacement;
                }
            }
        }
    }

    private static final class ReplacingPointerType extends PointerType implements ReplacingType {

        ReplacingPointerType(Type type) {
            super(type);
        }

        @Override
        public void replaceType(Type old, Type replacement) {
            if (getPointeeType() == old) {
                setPointeeType(replacement);
            }
        }
    }

    private static final class ReplacingFunctionType extends FunctionType implements ReplacingType {

        private Type returnType;

        ReplacingFunctionType(Type type, Type[] args, boolean isVarArg) {
            super(type, args, isVarArg);
            this.returnType = type;
        }

        @Override
        public Type getType() {
            return new PointerType(returnType);
        }

        @Override
        public Type getReturnType() {
            return returnType;
        }

        @Override
        public void replaceType(Type old, Type replacement) {
            if (returnType == old) {
                returnType = replacement;
            }
            for (int i = 0; i < args.length; i++) {
                if (args[i] == old) {
                    args[i] = replacement;
                }
            }
        }
    }

    private final List<ResolvedType> alreadySeen = new ArrayList<>();

    private final List<InternalType> cyclicReferences = new ArrayList<>();

    private final Map<ResolvedType, Type> alreadyResolvedTypes = new HashMap<>();

    private Type convert(ResolvedType type, boolean isEntryPoint) {
        if (type == null) {
            return null;

        } else if (type.isFloating()) {
            // fp128 and ppc_fp128 have the same bitwidth so we can only do string comparisons
            final String typeString = type.toString().toLowerCase();
            if (typeString.startsWith("half")) {
                return FloatingPointType.HALF;

            } else if (typeString.startsWith("float")) {
                return FloatingPointType.FLOAT;

            } else if (typeString.startsWith("double")) {
                return FloatingPointType.DOUBLE;

            } else if (typeString.startsWith("x86_fp80")) {
                return FloatingPointType.X86_FP80;

            } else if (typeString.startsWith("fp128")) {
                return FloatingPointType.FP128;

            } else if (typeString.startsWith("ppc_fp128")) {
                return FloatingPointType.PPC_FP128;

            } else {
                throw new AssertionError("Unknown Typestring: " + typeString);
            }

        } else if (type.isInteger()) {
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

        } else if (type.isVararg()) {
            throw new AssertionError("varargs are only expected inside functions");

        } else if (type.isVoid()) {
            return MetaType.VOID;

        } else if (type.isUnknown()) {
            return MetaType.UNKNOWN;

        } else if (type.isMetadata()) {
            return MetaType.METADATA;

        } else if (type instanceof ResolvedOpaqueType) {
            return MetaType.OPAQUE;
        }

        Type t;
        if (type.isPointer()) {
            t = convertPointer((ResolvedPointerType) type);

        } else if (type.isVector()) {
            t = convertVector((ResolvedVectorType) type);

        } else if (type instanceof ResolvedArrayType) {
            t = convertArray((ResolvedArrayType) type);

        } else if (type instanceof ResolvedNamedType) {
            t = convertName((ResolvedNamedType) type);

        } else if (type.isStruct()) {
            // isStruct() also returns true for a ResolvedNamedType, we have to avoid that case
            // before we get here
            t = convertStruct((ResolvedStructType) type);

        } else if (type.isFunction()) {
            t = convertFunction((ResolvedFunctionType) type);

        } else {
            throw new AssertionError("Cannot convert ResolvedType: " + type);
        }

        // resolve any cyclic references
        if (isEntryPoint && !cyclicReferences.isEmpty()) {
            for (final InternalType cyclicReference : cyclicReferences) {
                final Type resolvedType = alreadyResolvedTypes.get(cyclicReference.referredType);
                cyclicReference.parentType.replaceType(cyclicReference, resolvedType);
            }
        }

        return t;
    }

    private Type convertFunction(ResolvedFunctionType type) {
        alreadySeen.add(type);

        final List<ResolvedType> argTypes = new ArrayList<>();
        boolean hasVararg = false;
        for (final ResolvedType arg : type.getParameters()) {
            if (arg.isVararg()) {
                hasVararg = true;
            } else {
                argTypes.add(arg);
            }
        }

        final Type[] args = new Type[argTypes.size()];

        final ResolvedType returnType = type.getReturnType();
        final ReplacingFunctionType convertedFunction;
        if (alreadySeen.contains(returnType)) {
            final InternalType temp = new InternalType();
            temp.referredType = returnType;
            convertedFunction = new ReplacingFunctionType(temp, args, hasVararg);
            temp.parentType = convertedFunction;
            cyclicReferences.add(temp);

        } else {
            final Type convertedReturnType = convert(returnType, false);
            convertedFunction = new ReplacingFunctionType(convertedReturnType, args, hasVararg);
        }

        for (int i = 0; i < args.length; i++) {
            final ResolvedType argType = argTypes.get(i);
            if (alreadySeen.contains(argType)) {
                final InternalType temp = new InternalType();
                temp.referredType = argType;
                temp.parentType = convertedFunction;
                args[i] = temp;
                cyclicReferences.add(temp);

            } else {
                final Type convertedArg = convert(argType, false);
                args[i] = convertedArg;
            }
        }

        alreadyResolvedTypes.put(type, convertedFunction);
        return convertedFunction;
    }

    private Type convertPointer(ResolvedPointerType type) {
        alreadySeen.add(type);

        final ResolvedType pointedType = type.getContainedType(-1);
        if (alreadySeen.contains(pointedType)) {
            final InternalType temp = new InternalType();
            final ReplacingPointerType convertedPointer = new ReplacingPointerType(temp);
            temp.parentType = convertedPointer;
            temp.referredType = pointedType;
            cyclicReferences.add(temp);
            alreadyResolvedTypes.putIfAbsent(type, convertedPointer);
            return convertedPointer;

        } else {
            final Type convertedPointee = convert(pointedType, false);
            final ReplacingPointerType convertedPointer = new ReplacingPointerType(convertedPointee);
            alreadyResolvedTypes.put(type, convertedPointer);
            return convertedPointer;
        }
    }

    private Type convertName(ResolvedNamedType type) {
        alreadySeen.add(type);
        final Type resolvedType = convert(type.getReferredType(), false);
        alreadyResolvedTypes.put(type, resolvedType);
        return resolvedType;
    }

    private Type convertStruct(ResolvedStructType type) {
        alreadySeen.add(type);

        final List<ResolvedType> fieldTypes = type.getFieldTypes();
        final Type[] resolvedFieldTypes = new Type[fieldTypes.size()];
        final ReplacingStructureType convertedStruct = new ReplacingStructureType(type.isPacked(), resolvedFieldTypes);

        for (int i = 0; i < fieldTypes.size(); i++) {
            final ResolvedType fieldType = fieldTypes.get(i);
            if (alreadySeen.contains(fieldType)) {
                final InternalType temp = new InternalType();
                resolvedFieldTypes[i] = temp;
                temp.parentType = convertedStruct;
                temp.referredType = fieldType;
                cyclicReferences.add(temp);

            } else {
                resolvedFieldTypes[i] = convert(fieldType, false);
            }
        }

        alreadyResolvedTypes.put(type, convertedStruct);
        return convertedStruct;
    }

    private Type convertArray(ResolvedArrayType type) {
        alreadySeen.add(type);

        final ResolvedType elementType = type.getContainedType(-1);
        final int length = type.getSize();
        if (alreadySeen.contains(elementType)) {
            final InternalType temp = new InternalType();
            final ReplacingArrayType convertedArray = new ReplacingArrayType(temp, length);
            temp.parentType = convertedArray;
            temp.referredType = elementType;
            cyclicReferences.add(temp);
            alreadyResolvedTypes.putIfAbsent(type, convertedArray);
            return convertedArray;

        } else {
            final Type convertedElementType = convert(elementType, false);
            final ArrayType convertedArray = new ArrayType(convertedElementType, length);
            alreadyResolvedTypes.put(type, convertedArray);
            return convertedArray;
        }
    }

    private Type convertVector(ResolvedVectorType type) {
        alreadySeen.add(type);

        final ResolvedType elementType = type.getContainedType(-1);
        final int length = type.getSize();
        if (alreadySeen.contains(elementType)) {
            final InternalType temp = new InternalType();
            final ReplacingVectorType convertedVector = new ReplacingVectorType(temp, length);
            temp.parentType = convertedVector;
            temp.referredType = elementType;
            cyclicReferences.add(temp);
            alreadyResolvedTypes.putIfAbsent(type, convertedVector);
            return convertedVector;

        } else {
            final Type convertedElementType = convert(elementType, false);
            final VectorType convertedVector = new VectorType(convertedElementType, length);
            alreadyResolvedTypes.put(type, convertedVector);
            return convertedVector;
        }
    }
}
