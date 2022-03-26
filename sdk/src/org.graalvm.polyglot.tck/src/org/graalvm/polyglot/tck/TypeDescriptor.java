/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.graalvm.polyglot.tck;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.graalvm.polyglot.Value;

/**
 * Represents a type of a polyglot value. Types include primitive types, null type, object type,
 * array type with an optional content type and union type.
 *
 * @since 0.30
 */
public final class TypeDescriptor {

    private static final TypeDescriptor NOTYPE = new TypeDescriptor(new IntersectionImpl(Collections.emptySet()));

    /**
     * The NULL type represents a type of null or undefined value.
     *
     * @see Value#isNull().
     * @since 0.30
     */
    public static final TypeDescriptor NULL = new TypeDescriptor(new PrimitiveImpl(PrimitiveKind.NULL));
    /**
     * Represents a boolean type.
     *
     * @see Value#isBoolean().
     * @since 0.30
     */
    public static final TypeDescriptor BOOLEAN = new TypeDescriptor(new PrimitiveImpl(PrimitiveKind.BOOLEAN));
    /**
     * Represents a numeric type.
     *
     * @see Value#isNumber().
     * @since 0.30
     */
    public static final TypeDescriptor NUMBER = new TypeDescriptor(new PrimitiveImpl(PrimitiveKind.NUMBER));
    /**
     * Represents a string type.
     *
     * @see Value#isString().
     * @since 0.30
     */
    public static final TypeDescriptor STRING = new TypeDescriptor(new PrimitiveImpl(PrimitiveKind.STRING));

    /**
     * Represents an iterable with any content type. Any iterable type, including those with content
     * type, is assignable to this type. This iterable type is not assignable to any iterable type
     * having a content type.
     *
     * @see #isAssignable(org.graalvm.polyglot.tck.TypeDescriptor).
     * @see #iterable(TypeDescriptor).
     * @see Value#hasIterator().
     * @since 21.1
     */
    public static final TypeDescriptor ITERABLE = new TypeDescriptor(new IterableImpl(null));

    /**
     * Represents an iterator with any content type. Any iterator type, including those with content
     * type, is assignable to this type. This iterator type is not assignable to any iterator type
     * having a content type.
     *
     * @see #isAssignable(org.graalvm.polyglot.tck.TypeDescriptor).
     * @see #iterator(TypeDescriptor).
     * @see Value#isIterator().
     * @since 21.1
     */
    public static final TypeDescriptor ITERATOR = new TypeDescriptor(new IteratorImpl(null));

    /**
     * Represents a hash map with any key type and any value type. Any hash map type, including
     * those with specified key and value types, is assignable to this type. This hash map type is
     * not assignable to any hash map type with specified key or value types.
     *
     * @see #isAssignable(org.graalvm.polyglot.tck.TypeDescriptor).
     * @see #hash(TypeDescriptor, TypeDescriptor).
     * @see Value#hasHashEntries()
     * @since 21.1
     */
    public static final TypeDescriptor HASH = new TypeDescriptor(new HashImpl(null, null));

    /**
     * Represents an object created by a guest language.
     *
     * @see Value#hasMembers().
     * @since 0.30
     */
    public static final TypeDescriptor OBJECT = new TypeDescriptor(new PrimitiveImpl(PrimitiveKind.OBJECT));
    /**
     * Represents an array with any content type. Any array type, including those with content type,
     * is assignable to this type. This array type is not assignable to any array type having a
     * content type.
     *
     * @see #isAssignable(org.graalvm.polyglot.tck.TypeDescriptor).
     * @see Value#hasArrayElements().
     * @since 0.30
     */
    public static final TypeDescriptor ARRAY;
    static {
        Set<TypeDescriptorImpl> types = new HashSet<>();
        Collections.addAll(types, new ArrayImpl(null), ITERABLE.impl);
        ARRAY = new TypeDescriptor(new IntersectionImpl(types));
    }
    /**
     * Represents a host object.
     *
     * @see Value#isHostObject().
     * @since 0.30
     */
    public static final TypeDescriptor HOST_OBJECT = new TypeDescriptor(new PrimitiveImpl(PrimitiveKind.HOST_OBJECT));
    /**
     * Represents a native pointer.
     *
     * @see Value#isNativePointer().
     * @since 0.30
     */
    public static final TypeDescriptor NATIVE_POINTER = new TypeDescriptor(new PrimitiveImpl(PrimitiveKind.NATIVE_POINTER));

    /**
     * Type descriptor for date.
     *
     * @see Value#isDate().
     * @since 20.0
     */
    public static final TypeDescriptor DATE = new TypeDescriptor(new PrimitiveImpl(PrimitiveKind.DATE));

    /**
     * Type descriptor for time.
     *
     * @see Value#isTime().
     * @since 20.0
     */
    public static final TypeDescriptor TIME = new TypeDescriptor(new PrimitiveImpl(PrimitiveKind.TIME));

    /**
     * Type descriptor for time zone.
     *
     * @see Value#isTimeZone().
     * @since 20.0
     */
    public static final TypeDescriptor TIME_ZONE = new TypeDescriptor(new PrimitiveImpl(PrimitiveKind.TIME_ZONE));

    /**
     * Type descriptor for duration.
     *
     * @see Value#isDuration().
     * @since 20.0
     */
    public static final TypeDescriptor DURATION = new TypeDescriptor(new PrimitiveImpl(PrimitiveKind.DURATION));

    /**
     * Type descriptor for metaobjects.
     *
     * @see Value#isMetaObject().
     * @since 20.0
     */
    public static final TypeDescriptor META_OBJECT = new TypeDescriptor(new PrimitiveImpl(PrimitiveKind.META_OBJECT));

    /**
     * Type descriptor for exception.
     *
     * @see Value#isException()
     * @since 19.3
     */
    public static final TypeDescriptor EXCEPTION = new TypeDescriptor(new PrimitiveImpl(PrimitiveKind.EXCEPTION));

    /**
     * Represents an executable type returning any type and accepting any number of parameters of
     * any type. To create an executable type with concrete types use
     * {@link TypeDescriptor#executable(org.graalvm.polyglot.tck.TypeDescriptor, org.graalvm.polyglot.tck.TypeDescriptor...)}
     * . This type can be used for creating value constructors but should not be used for specifying
     * expressions or statements parameter types as no other executable is assignable to it.
     *
     * <p>
     * The JavaScript sample usage for no argument function constructor:
     * {@codesnippet LanguageProviderSnippets#TypeDescriptorSnippets#createValueConstructors}
     * <p>
     *
     * @see Value#canExecute().
     * @since 0.30
     */
    public static final TypeDescriptor EXECUTABLE = new TypeDescriptor(new ExecutableImpl(ExecutableImpl.Kind.BOTTOM, null, true, Collections.emptyList()));

    /**
     * Represents a raw executable type. Any executable can be assigned into the raw executable
     * type, but the raw executable type cannot be assigned to any other executable. To create an
     * executable type with concrete types use
     * {@link TypeDescriptor#executable(org.graalvm.polyglot.tck.TypeDescriptor, org.graalvm.polyglot.tck.TypeDescriptor...)}
     * . This type can be used for specifying expressions or statements parameter types when the
     * passed executable is actually not invoked.
     *
     * <p>
     * The JavaScript sample usage for plus operator:
     * {@codesnippet LanguageProviderSnippets#JsSnippets#createExpressions}
     * <p>
     *
     * @see TypeDescriptor#EXECUTABLE
     * @since 19.0
     */
    public static final TypeDescriptor EXECUTABLE_ANY = new TypeDescriptor(new ExecutableImpl(ExecutableImpl.Kind.TOP, null, true, Collections.emptyList()));

    /**
     * Represents an instantiable type accepting any number of parameters of any type. To create an
     * instantiable type with concrete parameter types use
     * {@link TypeDescriptor#instantiable(org.graalvm.polyglot.tck.TypeDescriptor, boolean, org.graalvm.polyglot.tck.TypeDescriptor...)}
     * . This type can be used for creating value constructors but should not be used for specifying
     * expressions or statements parameter types as no other instantiable is assignable to it.
     *
     *
     * @see Value#canInstantiate().
     * @since 19.0
     */
    public static final TypeDescriptor INSTANTIABLE = new TypeDescriptor(new InstantiableImpl(ExecutableImpl.Kind.BOTTOM, null, true, Collections.emptyList()));

    /**
     * Represents a raw instantiable type. Any instantiable can be assigned into this raw
     * instantiable type, but the raw instantiable type cannot be assigned to any other
     * instantiable. To create an instantiable type with concrete types use
     * {@link TypeDescriptor#instantiable(org.graalvm.polyglot.tck.TypeDescriptor, boolean, org.graalvm.polyglot.tck.TypeDescriptor...)}
     * . This type can be used for specifying expressions or statements parameter types when the
     * passed instantiable is actually not invoked.
     *
     *
     * @see TypeDescriptor#INSTANTIABLE
     * @since 19.0
     */
    public static final TypeDescriptor INSTANTIABLE_ANY = new TypeDescriptor(new InstantiableImpl(ExecutableImpl.Kind.TOP, null, true, Collections.emptyList()));

    /**
     * Represents all types. It's an intersection of no type.
     *
     * @since 0.30
     */
    public static final TypeDescriptor ANY = new TypeDescriptor(new UnionImpl(new HashSet<>(Arrays.asList(
                    NOTYPE.impl, NULL.impl, BOOLEAN.impl, NUMBER.impl, STRING.impl, HOST_OBJECT.impl, NATIVE_POINTER.impl, OBJECT.impl, ARRAY.impl, EXECUTABLE_ANY.impl, INSTANTIABLE_ANY.impl,
                    DATE.impl, TIME.impl, TIME_ZONE.impl, DURATION.impl, META_OBJECT.impl, ITERABLE.impl, ITERATOR.impl, EXCEPTION.impl, HASH.impl))));

    private static final TypeDescriptor[] PREDEFINED_TYPES = new TypeDescriptor[]{
                    NOTYPE, NULL, BOOLEAN, NUMBER, STRING, HOST_OBJECT, DATE, TIME, TIME_ZONE, DURATION, META_OBJECT, EXCEPTION, NATIVE_POINTER, OBJECT, ARRAY, EXECUTABLE, EXECUTABLE_ANY,
                    INSTANTIABLE, ITERABLE, ITERATOR, HASH,
                    INSTANTIABLE_ANY, ANY
    };

    private final TypeDescriptorImpl impl;

    private TypeDescriptor(final TypeDescriptorImpl impl) {
        Objects.requireNonNull(impl);
        this.impl = impl;
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.30
     */
    @Override
    public int hashCode() {
        return impl.hashCode();
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.30
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
     *
     * @since 0.30
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
     * union type can be assigned to other union type containing all union elements. The
     * intersection type can be assigned to type having any intersection type. To the target
     * intersection type only an intersection type having all the target intersection elements can
     * be assigned.
     *
     * @param fromType the type to assign
     * @return true if the fromType is assignable to this type
     * @since 0.30
     */
    public boolean isAssignable(final TypeDescriptor fromType) {
        return impl.isAssignable(impl, fromType.impl);
    }

    /**
     * Checks if this {@link TypeDescriptor} represent an union type.
     *
     * @return true if this type represents an union type
     * @since 0.30
     */
    public boolean isUnion() {
        return impl.getClass() == UnionImpl.class;
    }

    /**
     * Checks if this {@link TypeDescriptor} represent an intersection type.
     *
     * @return true if this type represents an intersection type
     * @since 0.30
     */
    public boolean isIntersection() {
        return impl.getClass() == IntersectionImpl.class;
    }

    /**
     * Creates a new union type. The union type is any of the given types.
     *
     * @param types the types to include in the union
     * @return the union type containing the given types
     * @since 0.30
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

    @SuppressWarnings("unchecked")
    private static TypeDescriptorImpl unionImpl(Collection<? extends TypeDescriptorImpl> typeImpls) {
        final Collection<TypeDescriptorImpl> subtypes = new HashSet<>();
        for (TypeDescriptorImpl typeImpl : typeImpls) {
            if (typeImpl.getClass() == UnionImpl.class) {
                subtypes.addAll(((UnionImpl) typeImpl).types);
            } else {
                subtypes.add(typeImpl);
            }
        }
        Set<TypeDescriptorImpl> impls = new HashSet<>();
        Map<Class<? extends ParameterizedTypeDescriptorImpl>, List<TypeDescriptorImpl>> parameterizedTypesByClz = new HashMap<>();
        for (TypeDescriptorImpl part : subtypes) {
            if (isArray(part)) {
                List<TypeDescriptorImpl> arrays = parameterizedTypesByClz.computeIfAbsent(ArrayImpl.class, (k) -> new ArrayList<>());
                arrays.add(part);
            } else if (part.getClass() == IterableImpl.class || part.getClass() == IteratorImpl.class || part.getClass() == HashImpl.class) {
                Class<ParameterizedTypeDescriptorImpl> clz = (Class<ParameterizedTypeDescriptorImpl>) part.getClass();
                List<TypeDescriptorImpl> parameterized = parameterizedTypesByClz.computeIfAbsent(clz, (k) -> new ArrayList<>());
                parameterized.add(part);
            } else {
                impls.add(part);
            }
        }
        for (List<TypeDescriptorImpl> parameterizedTypes : parameterizedTypesByClz.values()) {
            switch (parameterizedTypes.size()) {
                case 0:
                    break;
                case 1:
                    impls.add(parameterizedTypes.get(0));
                    break;
                default:
                    List<Set<TypeDescriptorImpl>> typeParameters = new ArrayList<>();
                    BitSet seenWildCard = new BitSet();
                    for (TypeDescriptorImpl type : parameterizedTypes) {
                        ParameterizedTypeDescriptorImpl parameterizedType = asParameterizedTypeImpl(type);
                        for (int i = 0; i < parameterizedType.typeParameters.size(); i++) {
                            if (!seenWildCard.get(i)) {
                                Set<TypeDescriptorImpl> typeParametersAtIndex;
                                if (i < typeParameters.size()) {
                                    typeParametersAtIndex = typeParameters.get(i);
                                } else {
                                    typeParametersAtIndex = new HashSet<>();
                                    typeParameters.add(typeParametersAtIndex);
                                    assert typeParametersAtIndex == typeParameters.get(i);
                                }
                                TypeDescriptorImpl typeParameterAtIndex = parameterizedType.typeParameters.get(i);
                                if (typeParameterAtIndex == null || isAny(typeParameterAtIndex)) {
                                    seenWildCard.set(i);
                                    typeParametersAtIndex.clear();
                                    typeParametersAtIndex.add(ANY.impl);
                                } else {
                                    typeParametersAtIndex.add(typeParameterAtIndex);
                                }
                            }
                        }
                    }
                    ParameterizedTypeDescriptorImpl parameterizedType = asParameterizedTypeImpl(parameterizedTypes.get(0));
                    impls.add(parameterizedType.create(typeParameters.stream().map((tps) -> unionImpl(tps)).collect(Collectors.toList())));
            }
        }
        return impls.size() == 1 ? impls.iterator().next() : new UnionImpl(impls);
    }

    /**
     * Creates a new intersection type. The intersection type is all of the given types. The
     * intersection can be also used to create a no type. The no type is a type which has no other
     * specialized type. The no type can be assigned to {@link TypeDescriptor#ANY} and itself. The
     * no type is created as an empty intersection, {@code TypeDescriptor.intersection()}.
     *
     * @param types the types to include in the intersection
     * @return the intersection type containing the given types
     * @since 0.30
     */
    public static TypeDescriptor intersection(TypeDescriptor... types) {
        Objects.requireNonNull(types);
        final Set<TypeDescriptor> typesSet = new HashSet<>();
        Collections.addAll(typesSet, types);
        switch (typesSet.size()) {
            case 0:
                return NOTYPE;
            case 1:
                return types[0];
            default:
                final Set<TypeDescriptorImpl> typeImpls = new HashSet<>();
                for (TypeDescriptor type : typesSet) {
                    typeImpls.add(type.impl);
                }
                final TypeDescriptorImpl intersectionImpl = intersectionImpl(typeImpls);
                TypeDescriptor result = isPredefined(intersectionImpl);
                return result != null ? result : new TypeDescriptor(intersectionImpl);
        }
    }

    /**
     * Normalization of a newly created {@link IntersectionImpl}.
     *
     * @param typeImpls the intersection components
     * @return new {@link TypeDescriptorImpl} may not be a {@link IntersectionImpl}
     */
    private static TypeDescriptorImpl intersectionImpl(final Collection<? extends TypeDescriptorImpl> typeImpls) {
        final Set<UnionImpl> unions = new HashSet<>();
        final Set<TypeDescriptorImpl> nonUnionCompoments = new HashSet<>();
        for (TypeDescriptorImpl typeImpl : typeImpls) {
            if (typeImpl.getClass() == UnionImpl.class) {
                unions.add((UnionImpl) typeImpl);
            } else if (typeImpl.getClass() == IntersectionImpl.class) {
                nonUnionCompoments.addAll(((IntersectionImpl) typeImpl).types);
            } else {
                nonUnionCompoments.add(typeImpl);
            }
        }
        final Set<TypeDescriptorImpl> dnfComponents = new HashSet<>();
        final TypeDescriptorImpl[][] unionTypes = new TypeDescriptorImpl[unions.size()][];
        final Iterator<UnionImpl> it = unions.iterator();
        for (int i = 0; it.hasNext(); i++) {
            final UnionImpl union = it.next();
            unionTypes[i] = union.types.toArray(new TypeDescriptorImpl[union.types.size()]);
        }
        collectDNFComponents(
                        unionTypes,
                        nonUnionCompoments,
                        new int[unions.size()],
                        0,
                        dnfComponents);
        return dnfComponents.size() == 1 ? dnfComponents.iterator().next() : unionImpl(dnfComponents);
    }

    /**
     * Collects all disjunctive normal form components. Computes cartesian product of unions
     * appended by tail types. The components have also reduced arrays, executables and
     * instantiables. If there is a generic ARRAY (EXECUTABLE, INSTANTIABLE) the other arrays
     * (executables, instantiables) are removed.
     *
     * @param unionTypes the union types
     * @param tail the non union types
     * @param collector the result collector
     */
    private static void collectDNFComponents(
                    final TypeDescriptorImpl[][] unionTypes,
                    final Collection<? extends TypeDescriptorImpl> tail,
                    int[] indexes,
                    int currentIndex,
                    final Collection<? super TypeDescriptorImpl> collector) {
        if (currentIndex == indexes.length) {
            final Set<TypeDescriptorImpl> currentComponent = new HashSet<>();
            BitSet wildCards = new BitSet();
            for (int i = 0; i < unionTypes.length; i++) {
                TypeDescriptorImpl component = unionTypes[i][indexes[i]];
                findWildCards(wildCards, component);
                currentComponent.add(component);
            }
            for (TypeDescriptorImpl component : tail) {
                findWildCards(wildCards, component);
                currentComponent.add(component);
            }
            if (!wildCards.isEmpty()) {
                for (Iterator<TypeDescriptorImpl> it = currentComponent.iterator(); it.hasNext();) {
                    final TypeDescriptorImpl td = it.next();
                    if ((wildCards.get(0) && isIncludedInWildCard(td, asArrayImpl(ARRAY.impl))) ||
                                    (wildCards.get(1) && isIncludedInWildCard(td, ITERABLE.impl)) ||
                                    (wildCards.get(2) && isIncludedInWildCard(td, ITERATOR.impl)) ||
                                    (wildCards.get(3) && isIncludedInWildCard(td, EXECUTABLE.impl)) ||
                                    (wildCards.get(4) && isIncludedInWildCard(td, INSTANTIABLE.impl)) ||
                                    (wildCards.get(5) && isIncludedInWildCard(td, HASH.impl))) {
                        it.remove();
                    }
                }
            }
            collector.add(
                            currentComponent.size() == 1 ? currentComponent.iterator().next() : new IntersectionImpl(currentComponent));
        } else {
            for (int i = 0; i < unionTypes[currentIndex].length; i++) {
                indexes[currentIndex] = i;
                collectDNFComponents(unionTypes, tail, indexes, currentIndex + 1, collector);
            }
        }
    }

    private static void findWildCards(BitSet wildcards, TypeDescriptorImpl component) {
        if (component.equals(asArrayImpl(ARRAY.impl))) {
            wildcards.set(0);
        } else if (component.equals(ITERABLE.impl)) {
            wildcards.set(1);
        } else if (component.equals(ITERATOR.impl)) {
            wildcards.set(2);
        } else if (component.equals(EXECUTABLE.impl)) {
            wildcards.set(3);
        } else if (component.equals(INSTANTIABLE.impl)) {
            wildcards.set(4);
        } else if (component.equals(HASH.impl)) {
            wildcards.set(5);
        }
    }

    private static boolean isIncludedInWildCard(TypeDescriptorImpl typeDescriptor, TypeDescriptorImpl wildCard) {
        return typeDescriptor.getClass() == wildCard.getClass() && typeDescriptor != wildCard;
    }

    private static boolean isAny(TypeDescriptorImpl type) {
        return type.isAssignable(type, ANY.impl);
    }

    private static boolean isArray(TypeDescriptorImpl type) {
        return ARRAY.impl.isAssignable(ARRAY.impl, type);
    }

    private static ArrayImpl asArrayImpl(TypeDescriptorImpl type) {
        if (!isArray(type)) {
            throw new IllegalArgumentException("Non an array type " + type);
        }
        for (TypeDescriptorImpl part : ((IntersectionImpl) type).types) {
            if (part instanceof ArrayImpl) {
                return (ArrayImpl) part;
            }
        }
        throw new IllegalStateException("Missing array component " + type);
    }

    private static ParameterizedTypeDescriptorImpl asParameterizedTypeImpl(TypeDescriptorImpl type) {
        return isArray(type) ? asArrayImpl(type) : (ParameterizedTypeDescriptorImpl) type;
    }

    /**
     * Creates a new type by removing the given type from this type. The type subtraction works in
     * the following way:
     * <ul>
     * <li>If this type and the {@code toRemove} type represent the same types then no type is
     * returned.</li>
     * <li>If this type represents a {@link #isUnion() union} type the {@code toRemove} type is
     * removed from the union type. When the {@code toRemove} type is also a union type then all
     * types in the {@code toRemove} union type are removed.</li>
     * <li>If this type represents an {@link #isIntersection() intersection} type the
     * {@code toRemove} type is removed from the intersection type. When the {@code toRemove} type
     * is also an intersection type then all types in the {@code toRemove} intersection type are
     * removed.</li>
     * <li>If this and {@code toRemove} types are parameterized types (array, iterable, iterator,
     * hash) of the same kind the type parameter types are subtracted applying the same rules.</li>
     * </ul>
     * <p>
     * Examples:
     * <ul>
     * <li>NUMBER.subtract(NUMBER) -> no type</li>
     * <li>NUMBER.subtract(STRING) -> NUMBER</li>
     * <li>UNION[NUMBER|STRING|OBJECT].subtract(NUMBER) -> UNION[STRING|OBJECT]</li>
     * <li>UNION[NUMBER|STRING|OBJECT].subtract(UNION[NUMBER|STRING]) -> OBJECT</li>
     * <li>INTERSECTION[NUMBER&STRING&OBJECT].subtract(NUMBER) -> INTERSECTION[STRING&OBJECT]</li>
     * <li>INTERSECTION[NUMBER&STRING&OBJECT].subtract(INTERSECTION[NUMBER&STRING]) -> OBJECT</li>
     * <li>ARRAY[UNION[NUMBER|STRING]].subtract(ARRAY[NUMBER]) -> ARRAY[STRING]</li>
     * <li>ARRAY[INTERSECTION[NUMBER|STRING]].subtract(ARRAY[NUMBER]) -> ARRAY[STRING]</li>
     * </ul>
     * 
     * @param toRemove the type to remove.
     * @since 20.2
     */
    public TypeDescriptor subtract(TypeDescriptor toRemove) {
        TypeDescriptorImpl res = subtractImpl(impl, toRemove.impl);
        TypeDescriptor result = isPredefined(res);
        return result != null ? result : new TypeDescriptor(res);
    }

    private static TypeDescriptorImpl subtractImpl(TypeDescriptorImpl originalType, TypeDescriptorImpl toRemove) {
        if (CompositeTypeDescriptorImpl.class.isAssignableFrom(originalType.getClass())) {
            Set<TypeDescriptorImpl> originalTypes = ((CompositeTypeDescriptorImpl) originalType).types;
            Set<TypeDescriptorImpl> toRemoveTypes = originalType.getClass() == toRemove.getClass() ? ((CompositeTypeDescriptorImpl) toRemove).types : Collections.singleton(toRemove);
            return ((CompositeTypeDescriptorImpl) originalType).create(subtractImpl(originalTypes, toRemoveTypes));
        } else {
            Set<TypeDescriptorImpl> reduced = subtractImpl(Collections.singleton(originalType), Collections.singleton(toRemove));
            return reduced.isEmpty() ? NOTYPE.impl : reduced.iterator().next();
        }
    }

    private static Set<TypeDescriptorImpl> subtractImpl(Set<TypeDescriptorImpl> originalTypes, Set<TypeDescriptorImpl> toRemove) {
        Set<TypeDescriptorImpl> result = new HashSet<>(originalTypes);
        Set<TypeDescriptorImpl> todo = new HashSet<>(toRemove);
        // 1. Remove simple non parameterized non composite types
        for (Iterator<TypeDescriptorImpl> it = todo.iterator(); it.hasNext();) {
            if (result.remove(it.next())) {
                it.remove();
            }
        }
        if (todo.isEmpty()) {
            return result;
        }
        // 2. Reduce composite and parameterized types
        Map<Class<? extends TypeDescriptorImpl>, TypeDescriptorImpl> originalTypesByClz = new HashMap<>();
        for (TypeDescriptorImpl td : result) {
            originalTypesByClz.put(td.getClass(), td);
        }
        Map<Class<? extends TypeDescriptorImpl>, TypeDescriptorImpl> toRemoveByClz = new HashMap<>();
        for (TypeDescriptorImpl td : todo) {
            toRemoveByClz.put(td.getClass(), td);
        }
        for (Map.Entry<Class<? extends TypeDescriptorImpl>, TypeDescriptorImpl> typeDescriptorToRemove : toRemoveByClz.entrySet()) {
            TypeDescriptorImpl typeDescriptorToReduce = originalTypesByClz.get(typeDescriptorToRemove.getKey());
            if (typeDescriptorToReduce != null) {
                if (ParameterizedTypeDescriptorImpl.class.isAssignableFrom(typeDescriptorToRemove.getKey())) {
                    result.remove(typeDescriptorToReduce);
                    result.add(subtractParameterized((ParameterizedTypeDescriptorImpl) typeDescriptorToReduce,
                                    (ParameterizedTypeDescriptorImpl) typeDescriptorToRemove.getValue()));
                } else if (CompositeTypeDescriptorImpl.class.isAssignableFrom(typeDescriptorToRemove.getKey())) {
                    result.remove(typeDescriptorToReduce);
                    result.add(subtractComposite((CompositeTypeDescriptorImpl) typeDescriptorToReduce,
                                    (CompositeTypeDescriptorImpl) typeDescriptorToRemove.getValue()));
                }
            }
        }
        return result;
    }

    private static TypeDescriptorImpl subtractParameterized(ParameterizedTypeDescriptorImpl originalType, ParameterizedTypeDescriptorImpl toRemove) {
        assert originalType.typeParameters.size() == toRemove.typeParameters.size() : "Incompatible parameterized types " + originalType + "," + toRemove;
        List<TypeDescriptorImpl> reducedTypeParameters = new ArrayList<>();
        for (int i = 0; i < originalType.typeParameters.size(); i++) {
            reducedTypeParameters.add(subtractImpl(originalType.typeParameters.get(i), toRemove.typeParameters.get(i)));
        }
        return originalType.create(reducedTypeParameters);
    }

    private static TypeDescriptorImpl subtractComposite(CompositeTypeDescriptorImpl originalType, CompositeTypeDescriptorImpl toRemove) {
        Set<TypeDescriptorImpl> reduced = subtractImpl(originalType.types, toRemove.types);
        return originalType.create(reduced);
    }

    /**
     * Creates a new array type with given component type. To create a multi-dimensional array use
     * an array type as a component type.
     *
     * @param componentType the required component type.
     * @return an array type with given component
     * @since 0.30
     */
    public static TypeDescriptor array(TypeDescriptor componentType) {
        Objects.requireNonNull(componentType, "Component type must be non null");
        if (isAny(componentType.impl)) {
            return ARRAY;
        } else {
            Set<TypeDescriptorImpl> types = new HashSet<>();
            Collections.addAll(types, new ArrayImpl(componentType.impl), new IterableImpl(componentType.impl));
            return new TypeDescriptor(new IntersectionImpl(types));
        }
    }

    /**
     * Creates a new iterable type with given component type.
     *
     * @param componentType the required component type.
     * @return an iterable type with given component
     * @since 21.1
     */
    public static TypeDescriptor iterable(TypeDescriptor componentType) {
        Objects.requireNonNull(componentType, "Component type must be non null");
        if (isAny(componentType.impl)) {
            return ITERABLE;
        } else {
            return new TypeDescriptor(new IterableImpl(componentType.impl));
        }
    }

    /**
     * Creates a new iterator type with given component type.
     *
     * @param componentType the required component type.
     * @return an iterator type with given component
     * @since 21.1
     */
    public static TypeDescriptor iterator(TypeDescriptor componentType) {
        Objects.requireNonNull(componentType, "Component type must be non null");
        if (isAny(componentType.impl)) {
            return ITERATOR;
        } else {
            return new TypeDescriptor(new IteratorImpl(componentType.impl));
        }
    }

    /**
     * Creates a new hash map type with given key type and value type.
     *
     * @param keyType the required key type.
     * @param valueType the required value type.
     * @return a new hash map type with given key and value types
     * @since 21.1
     */
    public static TypeDescriptor hash(TypeDescriptor keyType, TypeDescriptor valueType) {
        Objects.requireNonNull(keyType, "Key type must be non null");
        Objects.requireNonNull(valueType, "Value type must be non null");
        if (isAny(keyType.impl) && isAny(valueType.impl)) {
            return HASH;
        } else {
            return new TypeDescriptor(new HashImpl(keyType.impl, valueType.impl));
        }
    }

    /**
     * Creates a new executable type with a given return type and parameter types.
     *
     * @param returnType the required return type, use ANY as any type
     * @param parameterTypes the required parameter types
     * @return an executable type
     * @since 0.30
     */
    public static TypeDescriptor executable(TypeDescriptor returnType, TypeDescriptor... parameterTypes) {
        return executable(returnType, true, parameterTypes);
    }

    /**
     * Creates a new executable type with a given return type and parameter types.
     *
     * @param returnType the required return type, use ANY as any type
     * @param vararg the executable has variable length arguments or ignores additional parameters.
     *            For executables created by the
     *            {@link LanguageProvider#createValueConstructors(org.graalvm.polyglot.Context)} set
     *            to {@code false} if the language neither ignores extra parameters nor the
     *            executable has variable arguments length.
     * @param parameterTypes the required parameter types
     * @return an executable type
     * @since 0.31
     */
    public static TypeDescriptor executable(TypeDescriptor returnType, boolean vararg, TypeDescriptor... parameterTypes) {
        Objects.requireNonNull(returnType, "Return type cannot be null");
        Objects.requireNonNull(parameterTypes, "Parameter types cannot be null");
        if (isAny(returnType.impl) && parameterTypes.length == 0 && vararg) {
            return EXECUTABLE;
        }
        final List<TypeDescriptorImpl> paramTypeImpls = new ArrayList<>(parameterTypes.length);
        for (TypeDescriptor td : parameterTypes) {
            Objects.requireNonNull(td, "Parameter types cannot contain null");
            paramTypeImpls.add(td.impl);
        }
        return new TypeDescriptor(new ExecutableImpl(ExecutableImpl.Kind.UNIT, isAny(returnType.impl) ? null : returnType.impl, vararg, paramTypeImpls));
    }

    /**
     * Creates a new instantiable type with a given parameter types.
     *
     * @param instanceType the type of an instance, use ANY as any type
     * @param vararg the instantiable has variable length arguments or ignores additional
     *            parameters. For instantiables created by the
     *            {@link LanguageProvider#createValueConstructors(org.graalvm.polyglot.Context)} set
     *            to {@code false} if the language neither ignores extra parameters nor the
     *            instantiable has variable arguments length.
     * @param parameterTypes the required parameter types
     * @return an instantiable type
     * @since 19.0
     */
    public static TypeDescriptor instantiable(TypeDescriptor instanceType, boolean vararg, TypeDescriptor... parameterTypes) {
        Objects.requireNonNull(instanceType, "Instance type cannot be null");
        Objects.requireNonNull(parameterTypes, "Parameter types cannot be null");
        if (isAny(instanceType.impl) && parameterTypes.length == 0 && vararg) {
            return INSTANTIABLE;
        }
        final List<TypeDescriptorImpl> paramTypeImpls = new ArrayList<>(parameterTypes.length);
        for (TypeDescriptor td : parameterTypes) {
            Objects.requireNonNull(td, "Parameter types cannot contain null");
            paramTypeImpls.add(td.impl);
        }
        return new TypeDescriptor(new InstantiableImpl(ExecutableImpl.Kind.UNIT, isAny(instanceType.impl) ? null : instanceType.impl, vararg, paramTypeImpls));
    }

    /**
     * Creates a type for given {@link Value}.
     *
     * @param value the value to create {@link TypeDescriptor} for
     * @return the type of value, may by an union type containing more primitive or array types.
     * @since 0.30
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
        if (value.isDate()) {
            descs.add(DATE);
        }
        if (value.isTime()) {
            descs.add(TIME);
        }
        if (value.isTimeZone()) {
            descs.add(TIME_ZONE);
        }
        if (value.isDuration()) {
            descs.add(DURATION);
        }
        if (value.isMetaObject()) {
            descs.add(META_OBJECT);
        }
        if (value.isException()) {
            descs.add(EXCEPTION);
        }

        if (value.hasArrayElements()) {
            descs.add(array(detectContentType(new ArrayValueIterator(value))));
        }
        if (value.hasMembers()) {
            descs.add(OBJECT);
        }
        if (value.isHostObject()) {
            descs.add(HOST_OBJECT);
        }
        if (value.canExecute()) {
            descs.add(EXECUTABLE);
        }
        if (value.canInstantiate()) {
            descs.add(INSTANTIABLE);
        }
        if (value.hasIterator()) {
            descs.add(iterable(detectContentType(new IteratorValueIterator(value.getIterator()))));
        }
        if (value.isIterator()) {
            descs.add(ITERATOR);
        }
        if (value.hasHashEntries()) {
            TypeDescriptor keyType = detectContentType(new HashIterator(value, HashIterator.Kind.KEY));
            TypeDescriptor valueType = detectContentType(new HashIterator(value, HashIterator.Kind.VALUE));
            descs.add(hash(keyType, valueType));
        }
        switch (descs.size()) {
            case 1:
                return descs.get(0);
            default:
                return intersection(descs.toArray(new TypeDescriptor[descs.size()]));
        }
    }

    private static TypeDescriptor detectContentType(Iterator<Value> content) {
        final Set<TypeDescriptor> contentTypes = new HashSet<>();
        while (content.hasNext()) {
            final TypeDescriptor contentType = forValue(content.next());
            if (contentType != NULL) {
                contentTypes.add(contentType);
            }
        }
        switch (contentTypes.size()) {
            case 0:
                return intersection(NOTYPE, NULL, BOOLEAN, NUMBER, STRING, HOST_OBJECT, NATIVE_POINTER, OBJECT,
                                ARRAY, EXECUTABLE, INSTANTIABLE, ITERABLE, ITERATOR, DATE, TIME, TIME_ZONE, DURATION,
                                META_OBJECT, EXCEPTION, HASH);
            case 1:
                return contentTypes.iterator().next();
            default:
                return union(contentTypes.toArray(new TypeDescriptor[contentTypes.size()]));
        }
    }

    private static TypeDescriptor isPredefined(final TypeDescriptorImpl impl) {
        for (TypeDescriptor predef : PREDEFINED_TYPES) {
            if (impl.equals(predef.impl)) {
                return predef;
            }
        }
        return null;
    }

    private static class IteratorValueIterator implements Iterator<Value> {

        private final Value iteratorValue;

        IteratorValueIterator(Value iterableValue) {
            assert iterableValue.isIterator();
            this.iteratorValue = iterableValue;
        }

        @Override
        public boolean hasNext() {
            return iteratorValue.hasIteratorNextElement();
        }

        @Override
        public Value next() {
            return map(iteratorValue.getIteratorNextElement());
        }

        protected Value map(Value value) {
            return value;
        }
    }

    private static final class ArrayValueIterator implements Iterator<Value> {

        private final Value arrayValue;
        private int index;

        ArrayValueIterator(Value arrayValue) {
            assert arrayValue.hasArrayElements();
            this.arrayValue = arrayValue;
        }

        @Override
        public boolean hasNext() {
            return index < arrayValue.getArraySize();
        }

        @Override
        public Value next() {
            try {
                return arrayValue.getArrayElement(index++);
            } catch (ArrayIndexOutOfBoundsException e) {
                throw new NoSuchElementException();
            }
        }
    }

    private static final class HashIterator extends IteratorValueIterator {

        private final Kind kind;

        HashIterator(Value hash, Kind kind) {
            super(hash.getHashEntriesIterator());
            this.kind = kind;
        }

        @Override
        protected Value map(Value value) {
            if (value.hasArrayElements()) {
                return value.getArrayElement(kind.index);
            } else {
                throw new AssertionError("Must have array elements.");
            }
        }

        enum Kind {
            KEY(0),
            VALUE(1);

            private final long index;

            Kind(long index) {
                this.index = index;
            }
        }
    }

    private enum PrimitiveKind {
        NULL("null"),
        BOOLEAN("boolean"),
        NUMBER("number"),
        STRING("string"),
        HOST_OBJECT("hostObject"),
        NATIVE_POINTER("nativePointer"),
        DATE("date"),
        TIME("time"),
        TIME_ZONE("timeZone"),
        DURATION("duration"),
        META_OBJECT("metaObject"),
        OBJECT("object"),
        EXCEPTION("exception");

        private final String displayName;

        PrimitiveKind(final String displayName) {
            this.displayName = displayName;
        }

        String getDisplayName() {
            return displayName;
        }
    }

    private abstract static class TypeDescriptorImpl {

        abstract boolean isAssignable(TypeDescriptorImpl origType, TypeDescriptorImpl byType);

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
        boolean isAssignable(final TypeDescriptorImpl origType, final TypeDescriptorImpl byType) {
            final TypeDescriptorImpl other = other(origType, byType);
            if (other.getClass() == PrimitiveImpl.class) {
                return kind == ((PrimitiveImpl) other).kind;
            } else {
                return other.isAssignable(origType, byType);
            }
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

    private static class ExecutableImpl extends TypeDescriptorImpl {
        enum Kind {
            TOP,
            BOTTOM,
            UNIT
        }

        private final Kind kind;
        private final TypeDescriptorImpl retType;
        private final boolean vararg;
        private final List<? extends TypeDescriptorImpl> paramTypes;

        ExecutableImpl(
                        final Kind kind,
                        final TypeDescriptorImpl retType,
                        final boolean vararg,
                        final List<? extends TypeDescriptorImpl> paramTypes) {
            assert kind != null;
            assert paramTypes != null;
            assert kind == Kind.UNIT || (retType == null && paramTypes.isEmpty());
            this.kind = kind;
            this.retType = retType;
            this.vararg = vararg;
            this.paramTypes = paramTypes;
        }

        @Override
        boolean isAssignable(final TypeDescriptorImpl origType, final TypeDescriptorImpl byType) {
            final TypeDescriptorImpl other = other(origType, byType);
            final Class<? extends TypeDescriptorImpl> otherClz = other.getClass();
            if (otherClz == PrimitiveImpl.class) {
                return false;
            }
            if (otherClz == getClass()) {
                final ExecutableImpl origExec = (ExecutableImpl) origType;
                final ExecutableImpl byExec = (ExecutableImpl) byType;
                if (origExec.kind == Kind.TOP) {
                    return true;
                }
                if (byExec.kind == Kind.TOP) {
                    return false;
                }
                if (!origExec.resolveRetType().isAssignable(origExec.resolveRetType(), byExec.resolveRetType())) {
                    return false;
                }
                if (origExec.paramTypes.size() < byExec.paramTypes.size()) {
                    return false;
                }
                if (!byExec.vararg && origExec.paramTypes.size() != byExec.paramTypes.size()) {
                    return false;
                }
                for (int i = 0; i < byExec.paramTypes.size(); i++) {
                    final TypeDescriptorImpl pt = origExec.paramTypes.get(i);
                    final TypeDescriptorImpl opt = byExec.paramTypes.get(i);
                    if (!opt.isAssignable(opt, pt)) {
                        return false;
                    }
                }
                return true;
            } else {
                return other.isAssignable(origType, byType);
            }
        }

        @Override
        public int hashCode() {
            int res = 17;
            res = res * 31 + (vararg ? 1 : 0);
            res = res * 31 + kind.hashCode();
            res = res * 31 + (retType == null ? 0 : retType.hashCode());
            for (TypeDescriptorImpl paramType : paramTypes) {
                res = res * 31 + paramType.hashCode();
            }
            return res;
        }

        @Override
        public boolean equals(final Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != getClass()) {
                return false;
            }
            final ExecutableImpl other = (ExecutableImpl) obj;
            return vararg == other.vararg && kind == other.kind && Objects.equals(retType, other.retType) && paramTypes.equals(other.paramTypes);
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder(getName()).append("(");
            switch (kind) {
                case TOP:
                    sb.append("? extends");
                    break;
                case BOTTOM:
                    sb.append("? super");
                    break;
                case UNIT:
                    boolean first = true;
                    for (TypeDescriptorImpl paramType : paramTypes) {
                        if (first) {
                            first = false;
                        } else {
                            sb.append(", ");
                        }
                        sb.append(paramType);
                    }
                    break;
                default:
                    throw new IllegalStateException("Unknown kind: " + kind);
            }
            if (vararg) {
                sb.append(", *");
            }
            sb.append("):");
            sb.append(retType == null ? "<any>" : retType);
            return sb.toString();
        }

        String getName() {
            return "Executable";
        }

        private TypeDescriptorImpl resolveRetType() {
            return retType != null ? retType : ANY.impl;
        }
    }

    private static final class InstantiableImpl extends ExecutableImpl {

        InstantiableImpl(final Kind kind,
                        final TypeDescriptorImpl instanceType,
                        final boolean vararg,
                        final List<? extends TypeDescriptorImpl> paramTypes) {
            super(kind, instanceType, vararg, paramTypes);
        }

        @Override
        boolean isAssignable(TypeDescriptorImpl origType, TypeDescriptorImpl byType) {
            TypeDescriptorImpl other = other(origType, byType);
            Class<? extends TypeDescriptorImpl> otherClz = other.getClass();
            if (otherClz == PrimitiveImpl.class || otherClz == ExecutableImpl.class) {
                return false;
            }
            return super.isAssignable(origType, byType);
        }

        @Override
        String getName() {
            return "Instantiable";
        }
    }

    private abstract static class ParameterizedTypeDescriptorImpl extends TypeDescriptorImpl {

        final List<? extends TypeDescriptorImpl> typeParameters;
        private final Set<Class<? extends TypeDescriptorImpl>> exclude;

        ParameterizedTypeDescriptorImpl(TypeDescriptorImpl typeParameter, Collection<Class<? extends ParameterizedTypeDescriptorImpl>> exclude) {
            this(Collections.singletonList(typeParameter), exclude);
        }

        ParameterizedTypeDescriptorImpl(List<? extends TypeDescriptorImpl> typeParameters, Collection<Class<? extends ParameterizedTypeDescriptorImpl>> exclude) {
            this.typeParameters = typeParameters;
            this.exclude = new HashSet<>();
            Collections.addAll(this.exclude, PrimitiveImpl.class, ExecutableImpl.class, InstantiableImpl.class);
            this.exclude.addAll(exclude);
        }

        abstract String getName();

        abstract TypeDescriptorImpl create(List<TypeDescriptorImpl> typeParams);

        @Override
        public final int hashCode() {
            return typeParameters.hashCode();
        }

        @Override
        public final boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != getClass()) {
                return false;
            }
            return typeParameters.equals(((ParameterizedTypeDescriptorImpl) obj).typeParameters);
        }

        @Override
        public final String toString() {
            final StringBuilder sb = new StringBuilder(getName());
            sb.append("<");
            boolean first = true;
            for (TypeDescriptorImpl typeParameter : typeParameters) {
                if (first) {
                    first = false;
                } else {
                    sb.append(", ");
                }
                if (typeParameter == null) {
                    sb.append("<any>");
                } else {
                    sb.append(typeParameter.toString());
                }
            }
            sb.append(">");
            return sb.toString();
        }

        @Override
        final boolean isAssignable(final TypeDescriptorImpl origType, final TypeDescriptorImpl byType) {
            final TypeDescriptorImpl other = other(origType, byType);
            final Class<? extends TypeDescriptorImpl> otherClz = other.getClass();
            if (exclude.contains(otherClz)) {
                return false;
            } else if (otherClz == getClass()) {
                final ParameterizedTypeDescriptorImpl origParameterizedType = (ParameterizedTypeDescriptorImpl) origType;
                final ParameterizedTypeDescriptorImpl byParameterizedType = (ParameterizedTypeDescriptorImpl) byType;
                assert origParameterizedType.typeParameters.size() == byParameterizedType.typeParameters.size();
                for (int i = 0; i < origParameterizedType.typeParameters.size(); i++) {
                    TypeDescriptorImpl origContentType = resolveContentType(origParameterizedType.typeParameters.get(i));
                    TypeDescriptorImpl byContentType = resolveContentType(byParameterizedType.typeParameters.get(i));
                    if (!origContentType.isAssignable(origContentType, byContentType)) {
                        return false;
                    }
                }
                return true;
            } else {
                return other.isAssignable(origType, byType);
            }
        }

        static TypeDescriptorImpl resolveContentType(TypeDescriptorImpl type) {
            return type != null ? type : ANY.impl;
        }
    }

    private static final class ArrayImpl extends ParameterizedTypeDescriptorImpl {

        ArrayImpl(final TypeDescriptorImpl contentType) {
            super(contentType, Collections.emptySet());
        }

        @Override
        String getName() {
            return "Array";
        }

        @Override
        TypeDescriptorImpl create(List<TypeDescriptorImpl> typeParams) {
            if (typeParams.size() != 1) {
                throw new IllegalArgumentException("Array has single type parameter, given: " + typeParams);
            }
            return isAny(typeParams.get(0)) ? ARRAY.impl : array(new TypeDescriptor(typeParams.get(0))).impl;
        }
    }

    private static final class IterableImpl extends ParameterizedTypeDescriptorImpl {

        IterableImpl(final TypeDescriptorImpl contentType) {
            super(contentType, Collections.singleton(ArrayImpl.class));
        }

        @Override
        String getName() {
            return "Iterable";
        }

        @Override
        TypeDescriptorImpl create(List<TypeDescriptorImpl> typeParams) {
            if (typeParams.size() != 1) {
                throw new IllegalArgumentException("Iterable has single type parameter, given: " + typeParams);
            }
            return isAny(typeParams.get(0)) ? ITERABLE.impl : iterable(new TypeDescriptor(typeParams.get(0))).impl;
        }
    }

    private static final class IteratorImpl extends ParameterizedTypeDescriptorImpl {

        IteratorImpl(final TypeDescriptorImpl contentType) {
            super(contentType, Arrays.asList(ArrayImpl.class, IterableImpl.class));
        }

        @Override
        String getName() {
            return "Iterator";
        }

        @Override
        TypeDescriptorImpl create(List<TypeDescriptorImpl> typeParams) {
            if (typeParams.size() != 1) {
                throw new IllegalArgumentException("Iterator has single type parameter, given: " + typeParams);
            }
            return isAny(typeParams.get(0)) ? ITERATOR.impl : iterator(new TypeDescriptor(typeParams.get(0))).impl;
        }
    }

    private static final class HashImpl extends ParameterizedTypeDescriptorImpl {

        HashImpl(TypeDescriptorImpl keyType, TypeDescriptorImpl valueType) {
            super(Arrays.asList(keyType, valueType), Arrays.asList(ArrayImpl.class, IterableImpl.class, IteratorImpl.class));
        }

        @Override
        String getName() {
            return "Hash";
        }

        @Override
        TypeDescriptorImpl create(List<TypeDescriptorImpl> typeParams) {
            if (typeParams.size() != 2) {
                throw new IllegalArgumentException("Hash has two type parameters, given: " + typeParams);
            }
            return isAny(typeParams.get(0)) && isAny(typeParams.get(1)) ? HASH.impl : hash(new TypeDescriptor(typeParams.get(0)), new TypeDescriptor(typeParams.get(1))).impl;
        }
    }

    private abstract static class CompositeTypeDescriptorImpl extends TypeDescriptorImpl {

        final Set<TypeDescriptorImpl> types;

        CompositeTypeDescriptorImpl(Set<TypeDescriptorImpl> types) {
            this.types = Collections.unmodifiableSet(types);
        }

        abstract TypeDescriptorImpl create(Set<TypeDescriptorImpl> subTypes);
    }

    private static final class IntersectionImpl extends CompositeTypeDescriptorImpl {

        IntersectionImpl(Set<TypeDescriptorImpl> types) {
            super(types);
        }

        @Override
        boolean isAssignable(TypeDescriptorImpl origType, TypeDescriptorImpl byType) {
            final TypeDescriptorImpl other = other(origType, byType);
            final Class<? extends TypeDescriptorImpl> otherClz = other.getClass();
            if (otherClz == PrimitiveImpl.class || other instanceof ParameterizedTypeDescriptorImpl || other instanceof ExecutableImpl) {
                if (other == origType) {
                    for (TypeDescriptorImpl type : types) {
                        if (other.isAssignable(other, type)) {
                            return true;
                        }
                    }
                    return false;
                } else {
                    if (types.isEmpty()) {
                        return false;
                    }
                    for (TypeDescriptorImpl type : types) {
                        if (!type.isAssignable(type, other)) {
                            return false;
                        }
                    }
                    return true;
                }
            } else if (otherClz == IntersectionImpl.class) {
                final IntersectionImpl origIntersection = (IntersectionImpl) origType;
                final IntersectionImpl byIntersection = (IntersectionImpl) byType;
                if (origIntersection.types.isEmpty()) {
                    return byIntersection.types.isEmpty();
                }
                for (TypeDescriptorImpl subType : origIntersection.types) {
                    if (byIntersection.types.contains(subType)) {
                        continue;
                    } else if (subType instanceof ParameterizedTypeDescriptorImpl) {
                        boolean included = false;
                        for (TypeDescriptorImpl bySubType : byIntersection.types) {
                            if (bySubType instanceof ParameterizedTypeDescriptorImpl) {
                                if (subType.isAssignable(subType, bySubType)) {
                                    included = true;
                                    break;
                                }
                            }
                        }
                        if (!included) {
                            return false;
                        }
                    } else if (subType instanceof ExecutableImpl) {
                        boolean included = false;
                        for (TypeDescriptorImpl bySubType : byIntersection.types) {
                            if (bySubType instanceof ExecutableImpl) {
                                if (subType.isAssignable(subType, bySubType)) {
                                    included = true;
                                    break;
                                }
                            }
                        }
                        if (!included) {
                            return false;
                        }
                    } else {
                        return false;
                    }
                }
                return true;
            } else {
                return other.isAssignable(origType, byType);
            }
        }

        @Override
        TypeDescriptorImpl create(Set<TypeDescriptorImpl> subTypes) {
            switch (subTypes.size()) {
                case 0:
                    return NOTYPE.impl;
                case 1:
                    return subTypes.iterator().next();
                default:
                    Set<TypeDescriptorImpl> useSubTypes = new HashSet<>();
                    for (TypeDescriptorImpl subType : subTypes) {
                        if (subType.getClass() == IntersectionImpl.class) {
                            useSubTypes.addAll(((IntersectionImpl) subType).types);
                        } else {
                            useSubTypes.add(subType);
                        }
                    }
                    return new IntersectionImpl(useSubTypes);
            }
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
            if (obj == null || obj.getClass() != IntersectionImpl.class) {
                return false;
            }
            return types.equals(((IntersectionImpl) obj).types);
        }

        @Override
        public String toString() {
            return types.isEmpty() ? "<none>" : types.stream().map(Object::toString).collect(Collectors.joining(" & ", "[", "]"));
        }
    }

    private static final class UnionImpl extends CompositeTypeDescriptorImpl {

        private UnionImpl(Set<TypeDescriptorImpl> types) {
            super(types);
        }

        @Override
        boolean isAssignable(final TypeDescriptorImpl origType, TypeDescriptorImpl byType) {
            final TypeDescriptorImpl other = other(origType, byType);
            final Class<? extends TypeDescriptorImpl> otherClz = other.getClass();
            if (otherClz == PrimitiveImpl.class || other instanceof ParameterizedTypeDescriptorImpl || other instanceof ExecutableImpl) {
                if (other == byType) {
                    for (TypeDescriptorImpl type : types) {
                        if (type.isAssignable(type, other)) {
                            return true;
                        }
                    }
                }
                return false;
            } else if (otherClz == IntersectionImpl.class) {
                if (other == byType) {
                    final UnionImpl origUnion = (UnionImpl) origType;
                    for (TypeDescriptorImpl unionSubType : origUnion.types) {
                        if (unionSubType.isAssignable(unionSubType, byType)) {
                            return true;
                        }
                    }
                    return false;
                } else {
                    final UnionImpl byUnion = (UnionImpl) byType;
                    for (TypeDescriptorImpl unionSubType : byUnion.types) {
                        if (!origType.isAssignable(origType, unionSubType)) {
                            return false;
                        }
                    }
                    return true;
                }
            } else if (otherClz == UnionImpl.class) {
                final UnionImpl origUnion = (UnionImpl) origType;
                final UnionImpl byUnion = (UnionImpl) byType;
                final Set<TypeDescriptorImpl> copy = new HashSet<>(byUnion.types.size());
                for (TypeDescriptorImpl type : byUnion.types) {
                    if (origUnion.types.contains(type)) {
                        copy.add(type);
                    } else if (type instanceof ParameterizedTypeDescriptorImpl || type instanceof ExecutableImpl || type.getClass() == IntersectionImpl.class) {
                        for (TypeDescriptorImpl filteredType : origUnion.types) {
                            if (filteredType.isAssignable(filteredType, type)) {
                                copy.add(type);
                                break;
                            }
                        }
                    }
                }
                return byUnion.types.equals(copy);
            } else {
                return other.isAssignable(origType, byType);
            }
        }

        @Override
        TypeDescriptorImpl create(Set<TypeDescriptorImpl> subTypes) {
            switch (subTypes.size()) {
                case 0:
                    return NOTYPE.impl;
                case 1:
                    return subTypes.iterator().next();
                default:
                    Set<TypeDescriptorImpl> useSubTypes = new HashSet<>();
                    for (TypeDescriptorImpl subType : subTypes) {
                        if (subType.getClass() == UnionImpl.class) {
                            useSubTypes.addAll(((UnionImpl) subType).types);
                        } else {
                            useSubTypes.add(subType);
                        }
                    }
                    return new UnionImpl(subTypes);
            }
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
