/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.interop.access;

import com.oracle.truffle.api.CompilerDirectives;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.Equivalence;
import org.graalvm.collections.Pair;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;

import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.debug.type.LLVMSourceArrayLikeType;
import com.oracle.truffle.llvm.runtime.debug.type.LLVMSourceBasicType;
import com.oracle.truffle.llvm.runtime.debug.type.LLVMSourceClassLikeType;
import com.oracle.truffle.llvm.runtime.debug.type.LLVMSourceFunctionType;
import com.oracle.truffle.llvm.runtime.debug.type.LLVMSourceInheritanceType;
import com.oracle.truffle.llvm.runtime.debug.type.LLVMSourceMemberType;
import com.oracle.truffle.llvm.runtime.debug.type.LLVMSourceMethodType;
import com.oracle.truffle.llvm.runtime.debug.type.LLVMSourcePointerType;
import com.oracle.truffle.llvm.runtime.debug.type.LLVMSourceStructLikeType;
import com.oracle.truffle.llvm.runtime.debug.type.LLVMSourceType;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM.ForeignToLLVMType;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMAsForeignLibrary;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;
import com.oracle.truffle.llvm.runtime.types.Type;

/**
 * Describes how foreign interop should interpret values.
 */
@ExportLibrary(InteropLibrary.class)
@ExportLibrary(value = LLVMAsForeignLibrary.class, useForAOT = false)
public abstract class LLVMInteropType implements TruffleObject {

    public static final LLVMInteropType.Value UNKNOWN = Value.primitive(null, 0);
    public static final LLVMInteropType.Buffer BUFFER = new Buffer();

    private final long size;

    private LLVMInteropType(long size) {
        this.size = size;
    }

    public long getSize() {
        return size;
    }

    public LLVMInteropType.Array toArray(long length) {
        return new Array(this, size, length);
    }

    @ExportMessage
    boolean isForeign() {
        return false;
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    boolean isMetaObject() {
        return true;
    }

    @ExportMessage(name = "getMetaSimpleName")
    @ExportMessage(name = "getMetaQualifiedName")
    @TruffleBoundary
    Object getMetaSimpleName() {
        return toString();
    }

    @ExportMessage
    boolean isMetaInstance(Object instance) {
        if (LLVMPointer.isInstance(instance)) {
            return LLVMPointer.cast(instance).getExportType() == this;
        }
        return false;
    }

    @Override
    @TruffleBoundary
    public final String toString() {
        return toString(EconomicSet.create(Equivalence.IDENTITY_WITH_SYSTEM_HASHCODE));
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    final boolean hasLanguage() {
        return true;
    }

    @ExportMessage
    @SuppressWarnings({"static-method"})
    final Class<? extends TruffleLanguage<?>> getLanguage() {
        return LLVMLanguage.class;
    }

    @ExportMessage
    @TruffleBoundary
    final String toDisplayString(@SuppressWarnings("unused") boolean allowSideEffects) {
        return toString();
    }

    protected abstract String toString(EconomicSet<LLVMInteropType> visited);

    public enum ValueKind {
        I1(ForeignToLLVMType.I1),
        I8(ForeignToLLVMType.I8),
        I16(ForeignToLLVMType.I16),
        I32(ForeignToLLVMType.I32),
        I64(ForeignToLLVMType.I64),
        FLOAT(ForeignToLLVMType.FLOAT),
        DOUBLE(ForeignToLLVMType.DOUBLE),
        POINTER(ForeignToLLVMType.POINTER);

        public final LLVMInteropType.Value type;
        public final ForeignToLLVMType foreignToLLVMType;

        ValueKind(ForeignToLLVMType foreignToLLVMType) {
            this.foreignToLLVMType = foreignToLLVMType;
            this.type = Value.primitive(this, foreignToLLVMType.getSizeInBytes());
        }
    }

    public static final class Value extends LLVMInteropType {

        public final ValueKind kind;
        public final Structured baseType;

        private static Value primitive(ValueKind kind, long size) {
            return new Value(kind, null, size);
        }

        public static Value pointer(Structured baseType, long size) {
            return new Value(ValueKind.POINTER, baseType, size);
        }

        private Value(ValueKind kind, Structured baseType, long size) {
            super(size);
            this.kind = kind;
            this.baseType = baseType;
        }

        public int getSizeInBytes() {
            return kind.foreignToLLVMType.getSizeInBytes();
        }

        @Override
        @TruffleBoundary
        protected String toString(EconomicSet<LLVMInteropType> visited) {
            if (visited.contains(this)) {
                return String.format("<recursive %s>", kind.name());
            }
            visited.add(this);
            if (baseType == null) {
                return kind.name();
            } else {
                return baseType.toString(visited) + "*";
            }
        }
    }

    public static final class Buffer extends LLVMInteropType {

        private Buffer() {
            super(0);
        }

        @Override
        protected String toString(EconomicSet<LLVMInteropType> visited) {
            return "buffer";
        }
    }

    public abstract static class Structured extends LLVMInteropType {

        Structured(long size) {
            super(size);
        }
    }

    public static final class Array extends Structured {

        public final LLVMInteropType elementType;
        public final long elementSize;
        public final long length;

        Array(InteropTypeRegistry.Register elementType, long elementSize, long length) {
            super(elementSize * length);
            this.elementType = elementType.get(this);
            this.elementSize = elementSize;
            this.length = length;
        }

        private Array(LLVMInteropType elementType, long elementSize, long length) {
            super(elementSize * length);
            this.elementType = elementType;
            this.elementSize = elementSize;
            this.length = length;
        }

        @Override
        @TruffleBoundary
        protected String toString(EconomicSet<LLVMInteropType> visited) {
            if (visited.contains(this)) {
                return "<recursive array type>";
            }
            visited.add(this);
            return String.format("%s[%d]", elementType.toString(visited), length);
        }
    }

    public static class Struct extends Structured {

        protected final String name;

        @CompilationFinal(dimensions = 1) final StructMember[] members;

        Struct(String name, StructMember[] members, long size) {
            super(size);
            this.name = name;
            this.members = members;
        }

        public StructMember getMember(int i) {
            return members[i];
        }

        @TruffleBoundary
        public StructMember findMember(String memberName) {
            for (StructMember member : members) {
                if (member.name.equals(memberName)) {
                    return member;
                }
            }
            return null;
        }

        @TruffleBoundary
        public StructMember findMember(int startOffset) {
            for (StructMember member : members) {
                if (member.startOffset == startOffset) {
                    return member;
                }
            }
            return null;
        }

        public int getMemberCount() {
            return members.length;
        }

        @Override
        protected String toString(EconomicSet<LLVMInteropType> visited) {
            return name;
        }
    }

    public static final class ClazzInheritance implements Comparable<ClazzInheritance> {
        public final Clazz superClass;
        public final long offset;
        public final boolean virtual;

        public ClazzInheritance(Clazz superClass, long offset, boolean virtual) {
            this.superClass = superClass;
            this.offset = offset;
            this.virtual = virtual;
        }

        @Override
        public int compareTo(ClazzInheritance other) {
            if (this.virtual == other.virtual) {
                return Long.compare(this.offset, other.offset);
            } else {
                return this.virtual ? 1 : -1;
            }
        }
    }

    public static final class Clazz extends Struct {

        @CompilationFinal(dimensions = 1) final Method[] methods;
        private SortedSet<ClazzInheritance> superclasses;
        private VTable vtable;
        private boolean virtualMethodsInitialized;
        private boolean virtualMethods;

        Clazz(String name, StructMember[] members, Method[] methods, long size) {
            super(name, members, size);
            this.methods = methods;
            this.vtable = null;
            this.virtualMethodsInitialized = false;
            this.superclasses = new TreeSet<>((a, b) -> a.compareTo(b));
        }

        public void addSuperClass(Clazz superclass, long offset, boolean virtual) {
            superclasses.add(new ClazzInheritance(superclass, offset, virtual));
        }

        public Method getMethod(int i) {
            return methods[i];
        }

        public int getMethodCount() {
            return methods.length;
        }

        @TruffleBoundary
        public Method findMethod(String memberName) {
            for (Method method : methods) {
                if (method.getName().equals(memberName)) {
                    return method;
                } else if (method.getLinkageName().equals(memberName)) {
                    return method;
                }
            }
            for (ClazzInheritance ci : superclasses) {
                Method method = ci.superClass.findMethod(memberName);
                if (method != null) {
                    return method;
                }
            }
            return null;
        }

        /**
         * @param ident name (String) of the StructMember
         * @return an array of long values containing offset and a boolean flag, and the type of the
         *         struct/class that contains the member named as 'ident'
         * @throws UnknownIdentifierException if neither self class nor parent classes contain a
         *             StructMember with name 'ident'
         */
        @TruffleBoundary
        public Pair<long[], Struct> getSuperOffsetInformation(String ident) throws UnknownIdentifierException {
            for (StructMember member : members) {
                if (member.name.equals(ident)) {
                    return Pair.create(new long[0], this);
                }
            }
            Pair<LinkedList<Long>, Struct> pair = getMemberAccessList(ident);
            if (pair == null) {
                throw UnknownIdentifierException.create(ident);
            }
            long[] arr = pair.getLeft().stream().mapToLong(l -> l).toArray();
            return Pair.create(arr, pair.getRight());
        }

        @TruffleBoundary
        private Pair<LinkedList<Long>, Struct> getMemberAccessList(String ident) throws UnknownIdentifierException {
            for (StructMember member : members) {
                if (member.name.equals(ident)) {
                    return Pair.create(new LinkedList<>(), this);
                }
            }
            for (ClazzInheritance ci : superclasses) {
                Pair<LinkedList<Long>, Struct> pair = ci.superClass.getMemberAccessList(ident);
                if (pair != null) {
                    for (StructMember member : members) {
                        if (member.type.equals(ci.superClass)) {
                            long offset = ci.virtual ? ci.offset : member.startOffset;
                            offset <<= 1;
                            offset |= ci.virtual ? 1 : 0;
                            pair.getLeft().addFirst(offset);
                            return pair;
                        }
                    }
                }
            }
            return null;
        }

        @Override
        @TruffleBoundary
        public StructMember findMember(String memberName) {
            for (StructMember member : members) {
                if (member.name.equals(memberName)) {
                    return member;
                }
            }
            for (ClazzInheritance ci : superclasses) {
                StructMember member = ci.superClass.findMember(memberName);
                if (member != null) {
                    return member;
                }
            }
            return null;
        }

        @TruffleBoundary
        public String[] getVtableAccessNames() {
            if (!hasVirtualMethods()) {
                throw new IllegalStateException("vtable of given LLVMInteropType.Clazz type does not exist");
            }
            ArrayList<String> list = new ArrayList<>();
            /*
             * virtual method tables are always stored at offset 0 (current class, or recursively of
             * super class at 0)
             */
            StructMember structMember = findMember(0);
            list.add(structMember.name);

            while (structMember.type instanceof LLVMInteropType.Clazz) {
                structMember = ((Clazz) structMember.type).findMember(0);
                list.add(structMember.name);
            }
            return list.toArray(new String[0]);
        }

        public Set<Clazz> getSuperClasses() {
            return new HashSet<>(superclasses.stream().map(ci -> ci.superClass).collect(Collectors.toSet()));
        }

        @TruffleBoundary
        private void initVirtualMethods() {
            virtualMethodsInitialized = true;
            for (Method m : methods) {
                if (m.getVirtualIndex() >= 0) {
                    virtualMethods = true;
                    return;
                }
            }
            for (ClazzInheritance ci : superclasses) {
                if (ci.superClass.hasVirtualMethods()) {
                    virtualMethods = true;
                    return;
                }
            }
            virtualMethods = false;
        }

        public boolean hasVirtualMethods() {
            if (!virtualMethodsInitialized) {
                initVirtualMethods();
            }
            return virtualMethods;
        }

        public VTable getVTable() {
            if (vtable == null) {
                buildVTable();
            }
            return vtable;
        }

        @TruffleBoundary
        public void buildVTable() {
            virtualMethodsInitialized = false;
            if (vtable == null && hasVirtualMethods()) {
                for (ClazzInheritance ci : superclasses) {
                    ci.superClass.buildVTable();
                }
                vtable = new VTable(this);
            }
        }

        @TruffleBoundary
        public Method findMethodByArgumentsWithSelf(String memberName, Object[] arguments) throws ArityException {
            int expectedArgCount = -1;
            for (Method method : methods) {
                if (method.getName().equals(memberName)) {
                    // check parameters to resolve overloaded methods
                    LLVMInteropType[] types = method.parameterTypes;
                    if (types.length + 1 == arguments.length) {
                        return method;
                    } else {
                        expectedArgCount = types.length;
                    }
                } else if (method.getLinkageName().equals(memberName)) {
                    return method;
                }
            }
            for (ClazzInheritance ci : superclasses) {
                Method m = ci.superClass.findMethodByArgumentsWithSelf(memberName, arguments);
                if (m != null) {
                    return m;
                }
            }
            if (expectedArgCount >= 0) {
                throw ArityException.create(expectedArgCount, expectedArgCount, arguments.length - 1);
            }
            return null;
        }

    }

    public static final class VTable {
        final Method[] table;
        final Clazz clazz;

        VTable(Clazz clazz) {
            this.clazz = clazz;
            int maxIndex = -1;
            HashMap<Integer, Method> map = new HashMap<>();
            List<Clazz> list = new LinkedList<>(clazz.getSuperClasses());
            list.add(0, clazz);
            do {
                Clazz c = list.remove(0);
                list.addAll(c.getSuperClasses());
                for (Method m : c.methods) {
                    if (m != null && m.getVirtualIndex() >= 0) {
                        int idx = Type.toUnsignedInt(m.virtualIndex);
                        map.putIfAbsent(idx, m);
                        maxIndex = Math.max(maxIndex, idx);
                    }
                }
            } while (list.size() > 0);

            this.table = new Method[maxIndex + 1];
            for (Map.Entry<Integer, Method> entry : map.entrySet()) {
                this.table[entry.getKey()] = entry.getValue();
            }
        }

        public LLVMInteropType.Method findMethod(long virtualIndex) {
            if (Long.compareUnsigned(virtualIndex, table.length) >= 0) {
                return null;
            } else {
                return table[Type.toUnsignedInt(virtualIndex)];
            }
        }
    }

    @ExportLibrary(value = InteropLibrary.class)
    public static final class VTableObjectPair implements TruffleObject {
        private final VTable vtable;
        final Object foreign;

        private VTableObjectPair(VTable vtable, Object foreign) {
            this.vtable = vtable;
            this.foreign = foreign;
        }

        public static VTableObjectPair create(VTable vtable, Object foreign) {
            return new VTableObjectPair(vtable, foreign);
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        Object readArrayElement(long index,
                        @CachedLibrary("this.foreign") InteropLibrary foreignInterop) throws UnsupportedMessageException, InvalidArrayIndexException {
            Method m = vtable.findMethod(index);
            if (m == null) {
                throw InvalidArrayIndexException.create(index);
            } else {
                final String methodName = m.getName();
                try {
                    Object readMember = foreignInterop.readMember(foreign, methodName);
                    return new RemoveSelfArgument(readMember);
                } catch (UnknownIdentifierException e) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    final String msg = String.format("External method %s (identifier \"%s\") not found in type %s", methodName, e.getUnknownIdentifier(), foreign.getClass().getSimpleName());
                    throw new IllegalStateException(msg);
                    // TODO pichristoph: change to UnknownIdentifierException
                }
            }
        }

        @ExportMessage
        long getArraySize() {
            return vtable.table.length;
        }

        @ExportMessage
        boolean isArrayElementReadable(long index) {
            return index >= 0 && index < getArraySize();
        }
    }

    /**
     * C++/LLVM methods provide the 'this'/'self' object as the first method for every method. Thus,
     * when calling a foreign language, this parameter has to be removed.
     */
    @ExportLibrary(value = InteropLibrary.class)
    public static final class RemoveSelfArgument implements TruffleObject {
        private final Object foreignMethodObject;

        RemoveSelfArgument(Object foreignMethodObject) {
            this.foreignMethodObject = foreignMethodObject;
        }

        @ExportMessage
        boolean isExecutable() {
            return InteropLibrary.getUncached(foreignMethodObject).isExecutable(foreignMethodObject);
        }

        @ExportMessage
        Object execute(Object[] arguments) throws UnsupportedTypeException, ArityException, UnsupportedMessageException {
            Object[] newArguments = new Object[arguments.length - 1];
            for (int i = 1; i < arguments.length; i++) {
                newArguments[i - 1] = arguments[i];
            }
            return InteropLibrary.getUncached(foreignMethodObject).execute(foreignMethodObject, newArguments);
        }
    }

    public static final class StructMember {

        public final Struct struct;

        public final String name;
        public final long startOffset;
        public final long endOffset;
        public final LLVMInteropType type;
        public final boolean isInheritanceMember;

        StructMember(Struct struct, String name, long startOffset, long endOffset, LLVMInteropType type, boolean isInheritanceMember) {
            this.struct = struct;
            this.name = name;
            this.startOffset = startOffset;
            this.endOffset = endOffset;
            this.type = type;
            this.isInheritanceMember = isInheritanceMember;
        }

        boolean contains(long offset) {
            return startOffset <= offset && ((startOffset == endOffset) | offset < endOffset);
        }

    }

    public static class Function extends Structured {
        final LLVMInteropType returnType;
        @CompilationFinal(dimensions = 1) final LLVMInteropType[] parameterTypes;

        Function(InteropTypeRegistry.Register returnType, LLVMInteropType[] parameterTypes, boolean isMethod) {
            super(0);
            this.returnType = returnType.get(this, isMethod);
            this.parameterTypes = parameterTypes;
        }

        @Override
        @TruffleBoundary
        protected String toString(EconomicSet<LLVMInteropType> visited) {
            if (visited.contains(this)) {
                return "<recursive function type>";
            }
            visited.add(this);
            return String.format("%s(%s)", returnType == null ? "void" : returnType.toString(visited),
                            Arrays.stream(parameterTypes).map(t -> t == null ? "<null>" : t.toString(visited)).collect(Collectors.joining(", ")));
        }

        public LLVMInteropType getReturnType() {
            return returnType;
        }

        public LLVMInteropType getParameter(int i) {
            return parameterTypes[i];
        }

        public int getNumberOfParameters() {
            return parameterTypes.length;
        }
    }

    public static final class Method extends Function {

        private final Clazz clazz;
        private final String name;
        private final String linkageName;
        private final long virtualIndex;

        Method(Clazz clazz, String name, String linkageName, InteropTypeRegistry.Register returnType, LLVMInteropType[] parameterTypes, long virtualIndex) {
            super(returnType, parameterTypes, true);
            this.clazz = clazz;
            this.name = name;
            this.linkageName = linkageName;
            this.virtualIndex = virtualIndex;
        }

        public Clazz getObjectClass() {
            return clazz;
        }

        public String getName() {
            return name;
        }

        public String getLinkageName() {
            return linkageName;
        }

        public long getVirtualIndex() {
            return virtualIndex;
        }

        @Override
        @TruffleBoundary
        protected String toString(EconomicSet<LLVMInteropType> visited) {
            if (visited.contains(this)) {
                return "<recursive function type>";
            }
            visited.add(this);
            return String.format("%s %s(%s)", returnType == null ? "void" : returnType.toString(visited), name,
                            Arrays.stream(parameterTypes).map(t -> t == null ? "<null>" : t.toString(visited)).collect(Collectors.joining(", ")));
        }
    }

    // TODO (chaeubl): Interop types contain less information than the source type so that different
    // source types can result in the creation of the same interop type. Therefore, we would need to
    // deduplicate the created interop types.
    public static final class InteropTypeRegistry {
        private final EconomicMap<LLVMSourceType, LLVMInteropType> typeCache = EconomicMap.create(Equivalence.IDENTITY_WITH_SYSTEM_HASHCODE);

        private final class Register {

            private final LLVMSourceType source;
            private final LLVMSourceType target;

            private Register(LLVMSourceType source, LLVMSourceType target) {
                this.source = source;
                this.target = target;
            }

            LLVMInteropType get(LLVMInteropType self) {
                return get(self, false);
            }

            LLVMInteropType get(LLVMInteropType self, boolean isMethod) {
                if (!isMethod) {
                    assert !typeCache.containsKey(source);
                    typeCache.put(source, self);
                }
                return InteropTypeRegistry.this.get(target);
            }
        }

        public synchronized LLVMInteropType get(LLVMSourceType type) {
            if (type == null) {
                return LLVMInteropType.UNKNOWN;
            }

            LLVMSourceType actual = type.getActualType();
            if (typeCache.containsKey(actual)) {
                return typeCache.get(actual);
            } else {
                LLVMInteropType ret = convert(actual);
                typeCache.put(actual, ret);
                return ret;
            }
        }

        private LLVMInteropType convert(LLVMSourceType type) {
            if (type instanceof LLVMSourcePointerType) {
                return convertPointer((LLVMSourcePointerType) type);
            } else if (type instanceof LLVMSourceBasicType) {
                return convertBasic((LLVMSourceBasicType) type);
            } else {
                return convertStructured(type);
            }
        }

        private Structured getStructured(LLVMSourceType type) {
            LLVMSourceType actual = type.getActualType();
            if (typeCache.containsKey(actual)) {
                LLVMInteropType ret = typeCache.get(actual);
                if (ret instanceof Structured) {
                    return (Structured) ret;
                } else {
                    return null;
                }
            } else {
                /*
                 * Structured types put themselves in the map to break cycles. Also, we don't want
                 * to put the null value in the map in case this type is not structured.
                 */
                return convertStructured(actual);
            }
        }

        private Structured convertStructured(LLVMSourceType type) {
            if (type instanceof LLVMSourceArrayLikeType) {
                return convertArray((LLVMSourceArrayLikeType) type);
            } else if (type instanceof LLVMSourceClassLikeType) {
                return convertClass((LLVMSourceClassLikeType) type);
            } else if (type instanceof LLVMSourceStructLikeType) {
                return convertStruct((LLVMSourceStructLikeType) type);
            } else if (type instanceof LLVMSourceFunctionType) {
                return convertFunction((LLVMSourceFunctionType) type);
            } else {
                return null;
            }
        }

        private Array convertArray(LLVMSourceArrayLikeType type) {
            LLVMSourceType base = type.getBaseType();
            return new Array(new Register(type, base), base.getSize() / 8, type.getLength());
        }

        private Struct convertStruct(LLVMSourceStructLikeType type) {
            Struct ret = new Struct(type.getName(), new StructMember[type.getDynamicElementCount()], type.getSize() / 8);
            typeCache.put(type, ret);
            for (int i = 0; i < ret.members.length; i++) {
                LLVMSourceMemberType member = type.getDynamicElement(i);
                LLVMSourceType memberType = member.getElementType();
                long startOffset = member.getOffset() / 8;
                long endOffset = startOffset + (memberType.getSize() + 7) / 8;
                ret.members[i] = new StructMember(ret, member.getName(), startOffset, endOffset, get(memberType), false);
            }
            return ret;
        }

        private Clazz convertClass(LLVMSourceClassLikeType type) {
            Clazz ret = new Clazz(type.getName(), new StructMember[type.getDynamicElementCount()], new Method[type.getMethodCount()], type.getSize() / 8);
            typeCache.put(type, ret);
            for (int i = 0; i < ret.members.length; i++) {
                LLVMSourceMemberType member = type.getDynamicElement(i);
                LLVMSourceType memberType = member.getElementType();
                final boolean isInheritanceType = member instanceof LLVMSourceInheritanceType;
                if (isInheritanceType) {
                    assert memberType instanceof LLVMSourceClassLikeType;
                    LLVMSourceClassLikeType sourceSuperClazz = (LLVMSourceClassLikeType) memberType;
                    if (!typeCache.containsKey(sourceSuperClazz)) {
                        convertClass(sourceSuperClazz);
                    }
                    Clazz superClazz = (Clazz) typeCache.get(sourceSuperClazz);
                    ret.addSuperClass(superClazz, member.getOffset(), ((LLVMSourceInheritanceType) member).isVirtual());
                }
                long startOffset = member.getOffset() / 8;
                long endOffset = startOffset + (memberType.getSize() + 7) / 8;
                ret.members[i] = new StructMember(ret, member.getName(), startOffset, endOffset, get(memberType), isInheritanceType);
            }
            for (int i = 0; i < ret.methods.length; i++) {
                ret.methods[i] = convertMethod(type.getMethod(i), ret);
            }
            ret.buildVTable();
            return ret;
        }

        private Function convertFunction(LLVMSourceFunctionType functionType) {
            List<LLVMSourceType> parameterTypes = functionType.getParameterTypes();
            LLVMInteropType[] interopParameterTypes = new LLVMInteropType[parameterTypes.size()];
            Function interopFunctionType = new Function(new Register(functionType, functionType.getReturnType()), interopParameterTypes, false);
            typeCache.put(functionType, interopFunctionType);
            for (int i = 0; i < interopParameterTypes.length; i++) {
                interopParameterTypes[i] = get(parameterTypes.get(i));
            }
            return interopFunctionType;
        }

        private Method convertMethod(LLVMSourceMethodType sourceMethod, Clazz clazz) {
            List<LLVMSourceType> parameterTypes = sourceMethod.getParameterTypes();
            LLVMInteropType[] interopParameterTypes = new LLVMInteropType[parameterTypes.size()];
            Method interopMethodType = new Method(clazz, sourceMethod.getName(), sourceMethod.getLinkageName(), new Register(sourceMethod, sourceMethod.getReturnType()), interopParameterTypes,
                            sourceMethod.getVirtualIndex());
            typeCache.put(sourceMethod, interopMethodType);
            for (int i = 0; i < interopParameterTypes.length; i++) {
                interopParameterTypes[i] = get(parameterTypes.get(i));
            }
            return interopMethodType;
        }

        private static Value convertBasic(LLVMSourceBasicType type) {
            switch (type.getKind()) {
                case ADDRESS:
                    return ValueKind.POINTER.type;
                case BOOLEAN:
                    return ValueKind.I1.type;
                case FLOATING:
                    switch ((int) type.getSize()) {
                        case 32:
                            return ValueKind.FLOAT.type;
                        case 64:
                            return ValueKind.DOUBLE.type;
                    }
                    break;
                case SIGNED:
                case SIGNED_CHAR:
                case UNSIGNED:
                case UNSIGNED_CHAR:
                    switch ((int) type.getSize()) {
                        case 1:
                            return ValueKind.I1.type;
                        case 8:
                            return ValueKind.I8.type;
                        case 16:
                            return ValueKind.I16.type;
                        case 32:
                            return ValueKind.I32.type;
                        case 64:
                            return ValueKind.I64.type;
                    }
                    break;
            }
            return UNKNOWN;
        }

        private Value convertPointer(LLVMSourcePointerType type) {
            // TODO(je) does this really need to be getStructured?
            return Value.pointer(getStructured(type.getBaseType()), type.getSize() / 8);
        }
    }
}
