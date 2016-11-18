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
package com.oracle.truffle.llvm.parser.impl;

import com.intel.llvm.ireditor.types.ResolvedArrayType;
import com.intel.llvm.ireditor.types.ResolvedFloatingType;
import com.intel.llvm.ireditor.types.ResolvedFunctionType;
import com.intel.llvm.ireditor.types.ResolvedIntegerType;
import com.intel.llvm.ireditor.types.ResolvedMetadataType;
import com.intel.llvm.ireditor.types.ResolvedOpaqueType;
import com.intel.llvm.ireditor.types.ResolvedPointerType;
import com.intel.llvm.ireditor.types.ResolvedStructType;
import com.intel.llvm.ireditor.types.ResolvedType;
import com.intel.llvm.ireditor.types.ResolvedUnknownType;
import com.intel.llvm.ireditor.types.ResolvedVarargType;
import com.intel.llvm.ireditor.types.ResolvedVectorType;
import com.intel.llvm.ireditor.types.ResolvedVoidType;
import com.oracle.truffle.llvm.parser.base.model.types.ArrayType;
import com.oracle.truffle.llvm.parser.base.model.types.FloatingPointType;
import com.oracle.truffle.llvm.parser.base.model.types.FunctionType;
import com.oracle.truffle.llvm.parser.base.model.types.IntegerType;
import com.oracle.truffle.llvm.parser.base.model.types.MetaType;
import com.oracle.truffle.llvm.parser.base.model.types.PointerType;
import com.oracle.truffle.llvm.parser.base.model.types.StructureType;
import com.oracle.truffle.llvm.parser.base.model.types.Type;
import com.oracle.truffle.llvm.parser.base.model.types.VectorType;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

public final class BCToTextConverter {

    private interface ReplacingResolvedType {
        void replaceResolvedType(ResolvedType old, ResolvedType replacement);
    }

    private static final class ConvertedPointerType extends ResolvedPointerType implements ReplacingResolvedType {

        private ResolvedType containedType;

        ConvertedPointerType(ResolvedType pointedType, BigInteger addrSpace) {
            super(pointedType, addrSpace);
            this.containedType = pointedType;
        }

        @Override
        public ResolvedType getContainedType(int index) {
            return containedType;
        }

        @Override
        public String toString() {
            String addrSpaceStr = "";
            if (this.getAddrSpace().equals(BigInteger.valueOf(-1L))) {
                addrSpaceStr = " addrspace(m)";
            } else if (!this.getAddrSpace().equals(BigInteger.ZERO)) {
                addrSpaceStr = " addrspace(" + this.getAddrSpace().toString() + ")";
            }

            return this.containedType.toString() + addrSpaceStr + "*";
        }

        @Override
        protected boolean uniAccepts(ResolvedType t) {
            return t instanceof ResolvedPointerType && (this.getAddrSpace().longValue() == -1L || this.getAddrSpace().equals(((ResolvedPointerType) t).getAddrSpace())) &&
                            this.containedType.accepts(t.getContainedType(-1));
        }

        @Override
        public void replaceResolvedType(ResolvedType old, ResolvedType replacement) {
            if (this.containedType == old) {
                this.containedType = replacement;
            }
        }
    }

    private static final class ConvertedArrayType extends ResolvedArrayType implements ReplacingResolvedType {

        private ResolvedType elementType;

        ConvertedArrayType(int size, ResolvedType elementType) {
            super(size, elementType);
            this.elementType = elementType;
        }

        @Override
        public void replaceResolvedType(ResolvedType old, ResolvedType replacement) {
            if (this.elementType == old) {
                this.elementType = replacement;
            }
        }

        @Override
        public BigInteger getBits() {
            return BigInteger.valueOf(this.getSize()).multiply(this.elementType.getBits());
        }

        @Override
        public String toString() {
            return "[" + this.getSize() + " x " + this.elementType.toString() + "]";
        }

        @Override
        public ResolvedType getContainedType(int index) {
            return this.elementType;
        }

        @Override
        protected boolean uniAccepts(ResolvedType t) {
            return t instanceof ResolvedArrayType && this.getSize() == ((ResolvedArrayType) t).getSize() && this.elementType.accepts(t.getContainedType(0));
        }
    }

    private static final class ConvertedVectorType extends ResolvedVectorType implements ReplacingResolvedType {

        private ResolvedType elementType;

        ConvertedVectorType(int size, ResolvedType elementType) {
            super(size, elementType);
            this.elementType = elementType;
        }

        @Override
        public void replaceResolvedType(ResolvedType old, ResolvedType replacement) {
            if (this.elementType == old) {
                this.elementType = replacement;
            }
        }

        @Override
        public BigInteger getBits() {
            return BigInteger.valueOf(this.getSize()).multiply(this.elementType.getBits());
        }

        @Override
        public String toString() {
            return "<" + this.getSize() + " x " + this.elementType.toString() + ">";
        }

        @Override
        public ResolvedType getContainedType(int index) {
            return elementType;
        }

        @Override
        protected boolean uniAccepts(ResolvedType t) {
            return t instanceof ResolvedVectorType && this.getSize() == ((ResolvedVectorType) t).getSize() && this.elementType.accepts(t.getContainedType(0));
        }
    }

    private static final class ConvertedFunctionType extends ResolvedFunctionType implements ReplacingResolvedType {

        private ResolvedType returnType;

        private final List<ResolvedType> paramTypes;

        ConvertedFunctionType(ResolvedType rettype, List<ResolvedType> paramTypes) {
            super(rettype, paramTypes);
            this.returnType = rettype;
            this.paramTypes = paramTypes;
        }

        @Override
        public String toString() {
            final StringJoiner joiner = new StringJoiner(", ", returnType.toString() + "(", ")");
            for (ResolvedType rt : paramTypes) {
                joiner.add(rt.toString());
            }
            return joiner.toString();
        }

        @Override
        protected boolean uniAccepts(ResolvedType t) {
            return t instanceof ResolvedFunctionType && this.returnType.accepts(((ResolvedFunctionType) t).getReturnType()) &&
                            this.listAccepts(this.paramTypes, ((ResolvedFunctionType) t).getParameters());
        }

        @Override
        public ResolvedType getReturnType() {
            return returnType;
        }

        @Override
        public Iterable<? extends ResolvedType> getParameters() {
            return paramTypes;
        }

        @Override
        public void replaceResolvedType(ResolvedType old, ResolvedType replacement) {
            if (returnType == old) {
                returnType = replacement;
            }

            for (int i = 0; i < paramTypes.size(); i++) {
                if (paramTypes.get(i) == old) {
                    paramTypes.set(i, replacement);
                }
            }
        }
    }

    private static final class ConvertedStructType extends ResolvedStructType implements ReplacingResolvedType {

        private final List<ResolvedType> fieldTypes;

        ConvertedStructType(List<ResolvedType> fieldTypes, boolean packed, boolean fromLiteral) {
            super(fieldTypes, packed, fromLiteral);
            this.fieldTypes = fieldTypes;
        }

        @Override
        public BigInteger getBits() {
            BigInteger result = BigInteger.ZERO;

            ResolvedType t;
            for (Iterator<ResolvedType> var3 = this.fieldTypes.iterator(); var3.hasNext(); result = result.add(t.getBits())) {
                t = var3.next();
            }

            return result;
        }

        @Override
        public String toString() {
            final StringJoiner joiner = new StringJoiner(", ", "{", "}");
            for (final ResolvedType rt : fieldTypes) {
                joiner.add(rt.toString());
            }
            if (isPacked()) {
                return String.format("<%s>", joiner.toString());
            } else {
                return joiner.toString();
            }
        }

        @Override
        public ResolvedType getContainedType(int index) {
            return index >= this.fieldTypes.size() ? null : this.fieldTypes.get(index);
        }

        @Override
        protected boolean uniAccepts(ResolvedType t) {
            return t instanceof ResolvedStructType && isPacked() == ((ResolvedStructType) t).isPacked() && this.listAccepts(this.fieldTypes, ((ResolvedStructType) t).getFieldTypes());
        }

        @Override
        public List<ResolvedType> getFieldTypes() {
            return fieldTypes;
        }

        @Override
        public void replaceResolvedType(ResolvedType old, ResolvedType replacement) {
            for (int i = 0; i < fieldTypes.size(); i++) {
                if (fieldTypes.get(i) == old) {
                    fieldTypes.set(i, replacement);
                }
            }
        }
    }

    private static final class InternalType extends ResolvedType {

        Type referredType = null;

        ReplacingResolvedType parentType = null;

        @Override
        public String toString() {
            return "Internal Type";
        }

        @Override
        protected boolean uniAccepts(ResolvedType resolvedType) {
            return false;
        }
    }

    private final List<Type> alreadySeen = new ArrayList<>();

    private final List<InternalType> cyclicReferences = new ArrayList<>();

    private final Map<Type, ResolvedType> alreadyResolvedTypes = new HashMap<>();

    private BCToTextConverter() {
    }

    public static ResolvedType convert(Type type) {
        return new BCToTextConverter().convert(type, true);
    }

    private ResolvedType convert(Type type, boolean isEntryPoint) {
        if (type == null) {
            return null;
        } else if (type instanceof IntegerType) {
            return new ResolvedIntegerType(type.getBits());

        } else if (type instanceof FloatingPointType) {
            final FloatingPointType fpt = (FloatingPointType) type;
            return new ResolvedFloatingType(fpt.name(), fpt.width());

        } else if (type instanceof MetaType) {
            final MetaType mt = (MetaType) type;
            switch (mt) {
                case UNKNOWN:
                    return new ResolvedUnknownType();
                case VOID:
                    return new ResolvedVoidType();
                case OPAQUE:
                    return new ResolvedOpaqueType();
                case METADATA:
                    return new ResolvedMetadataType();
                default:
                    throw new AssertionError("Cannot unresolve MetaType: " + type);
            }
        }

        // compound types may contain references to themselves, these cycles need to be handled
        final ResolvedType t;
        if (type instanceof PointerType) {
            t = convertPointer((PointerType) type);
        } else if (type instanceof ArrayType) {
            t = convertArray((ArrayType) type);
        } else if (type instanceof VectorType) {
            t = convertVector((VectorType) type);
        } else if (type instanceof FunctionType) {
            t = convertFunction((FunctionType) type);
        } else if (type instanceof StructureType) {
            t = convertStruct((StructureType) type);
        } else {
            throw new AssertionError("Cannot unresolve Type: " + type);
        }

        // resolve any cyclic references
        if (isEntryPoint && !cyclicReferences.isEmpty()) {
            for (InternalType cyclicReference : cyclicReferences) {
                final ReplacingResolvedType parent = cyclicReference.parentType;
                if (!alreadyResolvedTypes.containsKey(cyclicReference.referredType)) {
                    throw new IllegalStateException();
                }
                final ResolvedType child = alreadyResolvedTypes.get(cyclicReference.referredType);
                parent.replaceResolvedType(cyclicReference, child);
            }
        }

        return t;
    }

    private ResolvedType convertStruct(StructureType type) {
        alreadySeen.add(type);

        final List<ResolvedType> resolvedFields = new ArrayList<>(type.getLength());
        final ConvertedStructType convertedStruct = new ConvertedStructType(resolvedFields, type.isPacked(), false);

        for (int i = 0; i < type.getLength(); i++) {
            final Type currentFieldType = type.getElementType(i);

            if (alreadySeen.contains(currentFieldType)) {
                final InternalType temp = new InternalType();
                temp.parentType = convertedStruct;
                temp.referredType = currentFieldType;
                resolvedFields.add(temp);
                cyclicReferences.add(temp);

            } else {
                final ResolvedType convertedFieldType = convert(currentFieldType, false);
                resolvedFields.add(convertedFieldType);
            }
        }

        alreadyResolvedTypes.put(type, convertedStruct);
        return convertedStruct;
    }

    private ResolvedType convertFunction(FunctionType type) {
        alreadySeen.add(type);

        final Type[] argTypes = type.getArgumentTypes();
        final List<ResolvedType> convertedParams = new ArrayList<>(argTypes.length);
        final ConvertedFunctionType convertedFunction;
        if (alreadySeen.contains(type.getReturnType())) {
            final InternalType temp = new InternalType();
            convertedFunction = new ConvertedFunctionType(temp, convertedParams);
            temp.parentType = convertedFunction;
            temp.referredType = type.getReturnType();
            cyclicReferences.add(temp);

        } else {
            convertedFunction = new ConvertedFunctionType(convert(type.getReturnType(), false), convertedParams);
        }

        for (final Type currentArgType : argTypes) {
            if (alreadySeen.contains(currentArgType)) {
                final InternalType temp = new InternalType();
                convertedParams.add(temp);
                temp.parentType = convertedFunction;
                temp.referredType = currentArgType;
                cyclicReferences.add(temp);

            } else {
                convertedParams.add(convert(currentArgType, false));
            }
        }
        if (type.isVarArg()) {
            convertedParams.add(new ResolvedVarargType());
        }

        alreadyResolvedTypes.put(type, convertedFunction);
        return convertedFunction;
    }

    private ResolvedType convertVector(VectorType type) {
        alreadySeen.add(type);

        final Type elementType = type.getElementType();
        if (alreadySeen.contains(elementType)) {
            final InternalType temp = new InternalType();
            final ConvertedVectorType v = new ConvertedVectorType(type.getLength(), temp);
            temp.parentType = v;
            temp.referredType = elementType;
            cyclicReferences.add(temp);
            alreadyResolvedTypes.putIfAbsent(type, v);
            return v;

        } else {
            alreadySeen.add(elementType);
            final ResolvedType resolvedElement = convert(elementType, false);
            final ResolvedVectorType resolvedVector = new ResolvedVectorType(type.getLength(), resolvedElement);
            alreadyResolvedTypes.put(type, resolvedVector);
            return resolvedVector;
        }
    }

    private ResolvedType convertArray(ArrayType type) {
        alreadySeen.add(type);

        final Type elementType = type.getElementType();
        if (alreadySeen.contains(elementType)) {
            final InternalType temp = new InternalType();
            final ConvertedArrayType a = new ConvertedArrayType(type.getLength(), temp);
            temp.parentType = a;
            temp.referredType = elementType;
            cyclicReferences.add(temp);
            alreadyResolvedTypes.putIfAbsent(type, a);
            return a;

        } else {
            final ResolvedType resolvedElement = convert(elementType, false);
            final ResolvedArrayType resolvedArray = new ResolvedArrayType(type.getLength(), resolvedElement);
            alreadyResolvedTypes.put(type, resolvedArray);
            return resolvedArray;
        }
    }

    private static final BigInteger POINTER_ADDR_SPACE = new BigInteger(new byte[]{(byte) 0});

    private ResolvedType convertPointer(PointerType type) {
        alreadySeen.add(type);

        final Type pointeeType = type.getPointeeType();
        if (alreadySeen.contains(pointeeType)) {
            final InternalType temp = new InternalType();
            final ConvertedPointerType p = new ConvertedPointerType(temp, POINTER_ADDR_SPACE);
            temp.parentType = p;
            temp.referredType = pointeeType;
            cyclicReferences.add(temp);
            alreadyResolvedTypes.putIfAbsent(type, p);
            return p;

        } else {
            final ResolvedType resolvedPointee = convert(pointeeType, false);
            final ResolvedType resolvedPointer = new ResolvedPointerType(resolvedPointee, POINTER_ADDR_SPACE);
            alreadyResolvedTypes.put(type, resolvedPointer);
            return resolvedPointer;
        }
    }

}
