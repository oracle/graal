/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.polyglot.tck;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.graalvm.polyglot.Value;

/**
 * Represents a type of a polyglot value. Types include primitive types, null type, object type,
 * array type with an optional content type and union type.
 */
public final class TypeDescriptor {

    /**
     * The NULL type represents a type of null or undefined value.
     *
     * @see Value#isNull().
     */
    public static final TypeDescriptor NULL = new TypeDescriptor(new PrimitiveImpl(PrimitiveKind.NULL));
    /**
     * Represents a boolean type.
     *
     * @see Value#isBoolean().
     */
    public static final TypeDescriptor BOOLEAN = new TypeDescriptor(new PrimitiveImpl(PrimitiveKind.BOOLEAN));
    /**
     * Represents a numeric type.
     *
     * @see Value#isNumber().
     */
    public static final TypeDescriptor NUMBER = new TypeDescriptor(new PrimitiveImpl(PrimitiveKind.NUMBER));
    /**
     * Represents a string type.
     *
     * @see Value#isString().
     */
    public static final TypeDescriptor STRING = new TypeDescriptor(new PrimitiveImpl(PrimitiveKind.STRING));
    /**
     * Represents an object created by a guest language.
     *
     * @see Value#hasMembers().
     */
    public static final TypeDescriptor OBJECT = new TypeDescriptor(new PrimitiveImpl(PrimitiveKind.OBJECT));
    /**
     * Represents an array with any content type. Any array type, including those with content type,
     * is assignable to this type. This array type is not assignable to any array type having a
     * content type.
     *
     * @see #isAssignable(org.graalvm.polyglot.tck.TypeDescriptor).
     * @see Value#hasMembers().
     */
    public static final TypeDescriptor ARRAY = new TypeDescriptor(new ArrayImpl(null));
    /**
     * Represents a host object.
     *
     * @see Value#isHostObject().
     */
    public static final TypeDescriptor HOST_OBJECT = new TypeDescriptor(new PrimitiveImpl(PrimitiveKind.HOST_OBJECT));
    /**
     * Represents a native pointer.
     *
     * @see Value#isNativePointer().
     */
    public static final TypeDescriptor NATIVE_POINTER = new TypeDescriptor(new PrimitiveImpl(PrimitiveKind.NATIVE_POINTER));

    private static final TypeDescriptor[] PREDEFINED_TYPES = new TypeDescriptor[]{
                    NULL, BOOLEAN, NUMBER, STRING, HOST_OBJECT, NATIVE_POINTER, OBJECT, ARRAY
    };

    private final TypeDescriptorImpl impl;

    private TypeDescriptor(final TypeDescriptorImpl impl) {
        Objects.requireNonNull(impl);
        this.impl = impl;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return impl.hashCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != TypeDescriptor.class) {
            return false;
        }
        return impl.equals(((TypeDescriptor) obj).impl);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return impl.toString();
    }

    /**
     * Checks if the given type is assignable to this type. The primitive types are assignable only
     * to itself or to an union type containing the given primitive type. The array type with a
     * component type can be assigned to itself, to an array type without a component type and to an
     * union type containing the given array type or an array type without a component type. The
     * union type can be assigned to a type for which the intersection of the union type and the
     * type is non-empty.
     *
     * @param fromType the type to assign
     * @return true if the fromType is assignable to this type
     */
    public boolean isAssignable(final TypeDescriptor fromType) {
        final TypeDescriptorImpl narrowedImpl = impl.narrow(impl, fromType.impl);
        return narrowedImpl != null;
    }

    /**
     * Creates a new union type.
     *
     * @param types the types to include in the union
     * @return the union type containing the given types
     */
    public static TypeDescriptor union(TypeDescriptor... types) {
        Objects.requireNonNull(types);
        final Set<TypeDescriptor> typesSet = new HashSet<>();
        Collections.addAll(typesSet, types);
        switch (typesSet.size()) {
            case 0:
                throw new IllegalArgumentException("No types.");
            case 1:
                return types[0];
            default:
                final Set<TypeDescriptorImpl> typeImpls = new HashSet<>();
                for (TypeDescriptor type : typesSet) {
                    typeImpls.add(type.impl);
                }
                final TypeDescriptorImpl unionImpl = unionImpl(typeImpls);
                TypeDescriptor result = isPredefined(unionImpl);
                return result != null ? result : new TypeDescriptor(unionImpl);
        }
    }

    private static TypeDescriptorImpl unionImpl(Collection<? extends TypeDescriptorImpl> typeImpls) {
        final Set<TypeDescriptorImpl> impls = new HashSet<>();
        final Set<ArrayImpl> arrays = new HashSet<>();
        for (TypeDescriptorImpl typeImpl : typeImpls) {
            for (TypeDescriptorImpl part : typeImpl.explode()) {
                if (part instanceof ArrayImpl) {
                    arrays.add((ArrayImpl) part);
                } else {
                    impls.add(part);
                }
            }
        }
        switch (arrays.size()) {
            case 0:
                break;
            case 1:
                impls.add(arrays.iterator().next());
                break;
            default:
                boolean seenWildCard = false;
                final Set<TypeDescriptorImpl> contentTypes = new HashSet<>();
                for (ArrayImpl array : arrays) {
                    final TypeDescriptorImpl contentType = array.contentType;
                    if (contentType == null) {
                        seenWildCard = true;
                        break;
                    }
                    contentTypes.add(contentType);
                }
                impls.add(seenWildCard ? ARRAY.impl : new ArrayImpl(unionImpl(contentTypes)));
        }
        return impls.size() == 1 ? impls.iterator().next() : new UnionImpl(impls);
    }

    /**
     * Creates a new array type with given component type. To create a multi-dimensional array use
     * an array type as a component type.
     *
     * @param componentType the required component type.
     * @return an array type with given component
     */
    public static TypeDescriptor array(TypeDescriptor componentType) {
        return componentType == null ? ARRAY : new TypeDescriptor(new ArrayImpl(componentType.impl));
    }

    /**
     * Creates a type for given {@link Value}.
     *
     * @param value the value to create {@link TypeDescriptor} for
     * @return the type of value, may by an union type containing more primitive or array types.
     */
    public static TypeDescriptor forValue(final Value value) {
        final List<TypeDescriptor> descs = new ArrayList<>();
        if (value.isNull()) {
            descs.add(NULL);
        }
        if (value.isBoolean()) {
            descs.add(BOOLEAN);
        }
        if (value.isNumber()) {
            descs.add(NUMBER);
        }
        if (value.isString()) {
            descs.add(STRING);
        }
        if (value.isNativePointer()) {
            descs.add(NATIVE_POINTER);
        }
        if (value.hasArrayElements()) {
            final Set<TypeDescriptor> contentTypes = new HashSet<>();
            for (int i = 0; i < value.getArraySize(); i++) {
                final TypeDescriptor contentType = forValue(value.getArrayElement(i));
                if (contentType != NULL) {
                    contentTypes.add(contentType);
                }
            }
            switch (contentTypes.size()) {
                case 0:
                    descs.add(ARRAY);
                    break;
                case 1:
                    descs.add(array(contentTypes.iterator().next()));
                    break;
                default:
                    descs.add(array(union(contentTypes.toArray(new TypeDescriptor[contentTypes.size()]))));
                    break;
            }
        }
        if (value.hasMembers()) {
            descs.add(OBJECT);
        }
        if (value.isHostObject()) {
            descs.add(HOST_OBJECT);
        }
        switch (descs.size()) {
            case 0:
                throw new IllegalArgumentException("Unknown type of: " + value);
            case 1:
                return descs.get(0);
            default:
                return union(descs.toArray(new TypeDescriptor[descs.size()]));
        }
    }

    private static TypeDescriptor isPredefined(final TypeDescriptorImpl impl) {
        for (TypeDescriptor predef : PREDEFINED_TYPES) {
            if (impl == predef.impl) {
                return predef;
            }
        }
        return null;
    }

    private enum PrimitiveKind {
        NULL("null"),
        BOOLEAN("boolean"),
        NUMBER("number"),
        STRING("string"),
        HOST_OBJECT("hostObject"),
        NATIVE_POINTER("nativePointer"),
        OBJECT("object");

        private final String displayName;

        PrimitiveKind(final String displayName) {
            this.displayName = displayName;
        }

        String getDisplayName() {
            return displayName;
        }
    }

    private abstract static class TypeDescriptorImpl {

        abstract TypeDescriptorImpl narrow(TypeDescriptorImpl origType, TypeDescriptorImpl byType);

        abstract Set<? extends TypeDescriptorImpl> explode();

        TypeDescriptorImpl other(TypeDescriptorImpl td1, TypeDescriptorImpl td2) {
            if (td1 == this) {
                return td2;
            }
            if (td2 == this) {
                return td1;
            }
            throw new IllegalArgumentException();
        }
    }

    private static final class PrimitiveImpl extends TypeDescriptorImpl {
        private final PrimitiveKind kind;

        PrimitiveImpl(final PrimitiveKind kind) {
            Objects.requireNonNull(kind);
            this.kind = kind;
        }

        @Override
        TypeDescriptorImpl narrow(final TypeDescriptorImpl origType, final TypeDescriptorImpl byType) {
            final TypeDescriptorImpl other = other(origType, byType);
            if (other.getClass() == PrimitiveImpl.class) {
                return kind == ((PrimitiveImpl) other).kind ? this : null;
            } else {
                return other.narrow(origType, byType) != null ? this : null;
            }
        }

        @Override
        Set<? extends TypeDescriptorImpl> explode() {
            return Collections.singleton(this);
        }

        @Override
        public int hashCode() {
            return this.kind.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != PrimitiveImpl.class) {
                return false;
            }
            return this.kind == ((PrimitiveImpl) obj).kind;
        }

        @Override
        public String toString() {
            return this.kind.getDisplayName();
        }
    }

    private static final class ArrayImpl extends TypeDescriptorImpl {
        private final TypeDescriptorImpl contentType;

        ArrayImpl(final TypeDescriptorImpl contentType) {
            this.contentType = contentType;
        }

        @Override
        TypeDescriptorImpl narrow(final TypeDescriptorImpl origType, final TypeDescriptorImpl byType) {
            final TypeDescriptorImpl other = other(origType, byType);
            final Class<? extends TypeDescriptorImpl> otherClz = other.getClass();
            if (otherClz == PrimitiveImpl.class) {
                return null;
            } else if (otherClz == ArrayImpl.class) {
                final ArrayImpl origArray = (ArrayImpl) origType;
                final ArrayImpl byArray = (ArrayImpl) byType;
                if (origArray.contentType == null) {
                    return byType;
                } else if (byArray.contentType == null) {
                    return null;
                } else {
                    final TypeDescriptorImpl narrowedContentType = origArray.contentType.narrow(origArray.contentType, byArray.contentType);
                    if (narrowedContentType == null) {
                        return null;
                    } else if (narrowedContentType == origArray.contentType) {
                        return origArray;
                    } else if (narrowedContentType == byArray.contentType) {
                        return byArray;
                    } else {
                        return new ArrayImpl(narrowedContentType);
                    }
                }
            } else {
                return other.narrow(origType, byType);
            }
        }

        @Override
        Set<? extends TypeDescriptorImpl> explode() {
            return Collections.singleton(this);
        }

        @Override
        public int hashCode() {
            return contentType == null ? 0 : contentType.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != ArrayImpl.class) {
                return false;
            }
            return Objects.equals(contentType, ((ArrayImpl) obj).contentType);
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("Array<");
            if (contentType == null) {
                sb.append('?');
            } else {
                sb.append(contentType.toString());
            }
            sb.append(">");
            return sb.toString();
        }
    }

    private static final class UnionImpl extends TypeDescriptorImpl {
        private final Set<TypeDescriptorImpl> types;

        private UnionImpl(Set<TypeDescriptorImpl> types) {
            this.types = Collections.unmodifiableSet(types);
        }

        @Override
        TypeDescriptorImpl narrow(final TypeDescriptorImpl origType, TypeDescriptorImpl byType) {
            final TypeDescriptorImpl other = other(origType, byType);
            final Class<? extends TypeDescriptorImpl> otherClz = other.getClass();
            if (otherClz == PrimitiveImpl.class || otherClz == ArrayImpl.class) {
                for (TypeDescriptorImpl type : types) {
                    final TypeDescriptorImpl narrowed = other == origType ? other.narrow(other, type) : type.narrow(type, other);
                    if (narrowed != null) {
                        return narrowed;
                    }
                }
                return null;
            } else if (otherClz == UnionImpl.class) {
                final UnionImpl origUnion = (UnionImpl) origType;
                final UnionImpl byUnion = (UnionImpl) byType;
                final Set<TypeDescriptorImpl> copy = new HashSet<>(origUnion.types.size());
                ArrayImpl arrayToNarrow = null;
                for (TypeDescriptorImpl type : origUnion.types) {
                    if (byUnion.types.contains(type)) {
                        copy.add(type);
                    } else if (type.getClass() == ArrayImpl.class) {
                        arrayToNarrow = (ArrayImpl) type;
                    }
                }
                if (arrayToNarrow != null) {
                    ArrayImpl byArray = null;
                    for (TypeDescriptorImpl type : byUnion.types) {
                        if (type.getClass() == ArrayImpl.class) {
                            byArray = (ArrayImpl) type;
                            break;
                        }
                    }
                    if (byArray != null) {
                        final TypeDescriptorImpl narrowedArray = arrayToNarrow.narrow(arrayToNarrow, byArray);
                        if (narrowedArray != null) {
                            copy.add(narrowedArray);
                        }
                    }
                }
                final int copySize = copy.size();
                if (copySize == 0) {
                    return null;
                } else if (copySize == origUnion.types.size()) {
                    return origUnion;
                } else if (copySize == byUnion.types.size()) {
                    return byUnion;
                } else if (copySize == 1) {
                    return copy.iterator().next();
                } else {
                    return new UnionImpl(copy);
                }
            } else {
                return other.narrow(origType, byType);
            }
        }

        @Override
        Set<? extends TypeDescriptorImpl> explode() {
            return types;
        }

        @Override
        public int hashCode() {
            return types.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != UnionImpl.class) {
                return false;
            }
            return types.equals(((UnionImpl) obj).types);
        }

        @Override
        public String toString() {
            return types.stream().map(Object::toString).collect(Collectors.joining(" | ", "[", "]"));
        }
    }
}
