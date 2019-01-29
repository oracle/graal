/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package com.oracle.truffle.espresso.impl;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.stream.Stream;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.classfile.RuntimeConstantPool;
import com.oracle.truffle.espresso.descriptors.SignatureDescriptor;
import com.oracle.truffle.espresso.descriptors.TypeDescriptor;
import com.oracle.truffle.espresso.impl.ByteString.Type;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.meta.MetaUtil;
import com.oracle.truffle.espresso.meta.ModifiersProvider;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.StaticObjectArray;
import com.oracle.truffle.espresso.runtime.StaticObjectClass;

import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toList;

public abstract class Klass implements ModifiersProvider {

    public static final Klass[] EMPTY_ARRAY = new Klass[0];

    private final ByteString<Type> type;
    private final JavaKind kind;

    // All further resolutions will overwrite this copy.
    private final RuntimeConstantPool pool;

    private final LinkedKlass linkedKlass; // Shared structural metadata.
    // (copy) Constant pool, it's a spawn-off ConstantPool with the superKlass and the
    // superinterfaces resolved.

    // Espresso super
    private final ObjectKlass superKlass;

    @CompilationFinal(dimensions = 1) //
    private final ObjectKlass[] superInterfaces;

// @CompilationFinal(dimensions = 1) //
// private final Field[] declaredFields;
//
// @CompilationFinal(dimensions = 1) //
// private final Method[] declaredMethods;

    public ConstantPool getConstantPool() {
        return pool;
    }

    public Klass getSuperKlass() {
        return superKlass;
    }

    public Klass[] getSuperInterfaces() {
        return superInterfaces;
    }

    public Klass(LinkedKlass linkedKlass, ObjectKlass superKlass, ObjectKlass[] superInterfaces) {
        this.type = linkedKlass.getType();
        this.linkedKlass = linkedKlass;
        this.kind = TypeDescriptor.getJavaKind(type);
        this.pool = linkedKlass.getConstantPool(); // create runtime constant pool with writable
                                                   // entries.
        this.superKlass = superKlass;
        this.superInterfaces = superInterfaces;
    }

    public ClassLoader getDefiningClassLoader() {
        return pool.getDefiningClassLoader();
    }

    public int getFlags() {
        return linkedKlass.getFlags();
    }

    @CompilationFinal //
    private StaticObject statics;

    @CompilationFinal //
    private ArrayKlass arrayClass;

    @CompilationFinal //
    private StaticObjectClass mirrorCache;

    public final JavaKind getJavaKind() {
        return kind;
    }

    public final boolean isArray() {
        return TypeDescriptor.isArray(getType());
    }

    public StaticObject mirror() {
        if (mirrorCache == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            mirrorCache = new StaticObjectClass((ObjectKlass) getContext().getMeta().CLASS.rawKlass());
            mirrorCache.setMirror(this);
        }
        return mirrorCache;
    }

    public abstract StaticObject getClassLoader();

    @Override
    public final boolean equals(Object obj) {
        if (!(obj instanceof Klass)) {
            return false;
        }
        Klass that = (Klass) obj;
        return this.mirror().equals(that.mirror());
    }

    public ArrayKlass getArrayClass() {
        // TODO(peterssen): Make thread-safe.
        if (arrayClass == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            arrayClass = createArrayKlass();
        }
        return arrayClass;
    }

    public ArrayKlass getArrayClass(int dimensions) {
        assert dimensions > 0;
        ArrayKlass klass = getArrayClass();
        for (int i = 1; i < dimensions; ++i) {
            klass = klass.getArrayClass();
        }
        return klass;
    }

    protected ArrayKlass createArrayKlass() {
        return new ArrayKlass(this);
    }

    @Override
    public final int hashCode() {
        return getType().hashCode();
    }

    public abstract EspressoContext getContext();

    public Method findDeclaredMethod(String klassName, Class<?> returnType, Class<?>... parameterTypes) {
        SignatureDescriptor signature = getContext().getSignatureDescriptors().create(returnType, parameterTypes);
        return findDeclaredConcreteMethod(klassName, signature);
    }

    public abstract StaticObject tryInitializeAndGetStatics();

    /**
     * Checks whether this type has a finalizer method.
     *
     * @return {@code true} if this class has a finalizer
     */
    public final boolean hasFinalizer() {
        throw EspressoError.unimplemented("finalizers");
    }

    /**
     * Checks whether this type is an instance class.
     *
     * @return {@code true} if this type is an instance class
     */
    public abstract boolean isInstanceClass();

    /**
     * Checks whether this type is primitive.
     *
     * @return {@code true} if this type is primitive
     */
    public final boolean isPrimitive() {
        return kind.isPrimitive(); // TypeDescriptor.isPrimitive(getType());
    }

    /*
     * The setting of the final bit for types is a bit confusing since arrays are marked as final.
     * This method provides a semantically equivalent test that appropriate for types.
     */
    public boolean isLeaf() {
        return getElementalType().isFinalFlagSet();
    }

    /**
     * Checks whether this type is initialized. If a type is initialized it implies that it was
     * {@link #isLinked() linked} and that the static initializer has run.
     *
     * @return {@code true} if this type is initialized
     */
    public abstract boolean isInitialized();

    /**
     * Initializes this type.
     */
    public abstract void initialize();

    /**
     * Checks whether this type is linked and verified. When a type is linked the static initializer
     * has not necessarily run. An {@link #isInitialized() initialized} type is always linked.
     *
     * @return {@code true} if this type is linked
     */
    public final boolean isLinked() {
        // TODO(peterssen): Klasses in Espresso are linked in the very moment the Klass is created
        // from LinkedKlass.
        return true;
    }

    /**
     * Determines if this type is either the same as, or is a superclass or superinterface of, the
     * type represented by the specified parameter. This method is identical to
     * {@link Class#isAssignableFrom(Class)} in terms of the value return for this type.
     */
    public abstract boolean isAssignableFrom(Klass other);

    /**
     * Returns the {@link Klass} object representing the host class of this VM anonymous class (as
     * opposed to the unrelated concept specified by {@link Class#isAnonymousClass()}) or
     * {@code null} if this object does not represent a VM anonymous class.
     */
    public Klass getHostClass() {
        throw EspressoError.unimplemented();
    }

    /**
     * Returns true if this type is exactly the type {@link java.lang.Object}.
     */
    public boolean isJavaLangObject() {
        // Removed assertion due to https://bugs.eclipse.org/bugs/show_bug.cgi?id=434442
        return getSuperclass() == null && !isInterface() && getJavaKind() == JavaKind.Object;
    }

    /**
     * Gets the super class of this type. If this type represents either the {@code Object} class,
     * an interface, a primitive type, or void, then null is returned. If this object represents an
     * array class then the type object representing the {@code Object} class is returned.
     */
    public final ObjectKlass getSuperclass() {
        return superKlass;
    }

    /**
     * Gets the interfaces implemented or extended by this type. This method is analogous to
     * {@link Class#getInterfaces()} and as such, only returns the interfaces directly implemented
     * or extended by this type.
     */
    public final ObjectKlass[] getInterfaces() {
        return superInterfaces;
    }

    /**
     * Walks the class hierarchy upwards and returns the least common class that is a superclass of
     * both the current and the given type.
     *
     * @return the least common type that is a super type of both the current and the given type, or
     *         {@code null} if primitive types are involved.
     */
    public abstract Klass findLeastCommonAncestor(Klass otherType);

    // public abstract Klass getComponentType();

    public final Klass getElementalType() {
        Klass t = this;
        while (t.isArray()) {
            t = t.getComponentType();
        }
        return t;
    }

    /**
     * Resolves the method implementation for virtual dispatches on objects of this dynamic type.
     * This resolution process only searches "up" the class hierarchy of this type. For interface
     * types it returns null since no concrete object can be an interface.
     *
     * @param method the method to select the implementation of
     * @param callerType the caller or context type used to perform access checks
     * @return the method that would be selected at runtime (might be abstract) or {@code null} if
     *         it can not be resolved
     */
    public abstract Method resolveMethod(Method method, Klass callerType);

    /**
     * A convenience wrapper for {@link #resolveMethod(Method, Klass)} that only returns
     * non-abstract methods.
     *
     * @param method the method to select the implementation of
     * @param callerType the caller or context type used to perform access checks
     * @return the concrete method that would be selected at runtime, or {@code null} if there is no
     *         concrete implementation of {@code method} in this type or any of its superclasses
     */
    public Method resolveConcreteMethod(Method method, Klass callerType) {
        Method resolvedMethod = resolveMethod(method, callerType);
        if (resolvedMethod == null || resolvedMethod.isAbstract()) {
            return null;
        }
        return resolvedMethod;
    }

    /**
     * Returns the instance fields of this class, including {@linkplain Field#isInternal() internal}
     * fields. A zero-length array is returned for array and primitive types. The order of fields
     * returned by this method is stable. That is, for a single JVM execution the same order is
     * returned each time this method is called. It is also the "natural" order, which means that
     * the JVM would expect the fields in this order if no specific order is given.
     *
     * @param includeSuperclasses if true, then instance fields for the complete hierarchy of this
     *            type are included in the result
     * @return an array of instance fields
     */
    public abstract Field[] getInstanceFields(boolean includeSuperclasses);

    /**
     * Returns the static fields of this class, including {@linkplain Field#isInternal() internal}
     * fields. A zero-length array is returned for array and primitive types. The order of fields
     * returned by this method is stable. That is, for a single JVM execution the same order is
     * returned each time this method is called.
     */
    public abstract Field[] getStaticFields();

    /**
     * Returns the instance field of this class (or one of its super classes) at the given offset,
     * or {@code null} if there is no such field.
     *
     * @param offset the offset of the field to look for
     * @return the field with the given offset, or {@code null} if there is no such field.
     */
    public abstract Field findInstanceFieldWithOffset(long offset, JavaKind expectedKind);

    /**
     * Returns {@code true} if the type is a local type.
     */
    public abstract boolean isLocal();

    /**
     * Returns {@code true} if the type is a member type.
     */
    public abstract boolean isMember();

    /**
     * Returns the enclosing type of this type, if it exists, or {@code null}.
     */
    public abstract Klass getEnclosingType();

    /**
     * Returns an array reflecting all the constructors declared by this type. This method is
     * similar to {@link Class#getDeclaredConstructors()} in terms of returned constructors.
     */
    public abstract Method[] getDeclaredConstructors();

    /**
     * Returns an array reflecting all the methods declared by this type. This method is similar to
     * {@link Class#getDeclaredMethods()} in terms of returned methods.
     */
    public abstract Method[] getDeclaredMethods();

    /**
     * Returns an array reflecting all the fields declared by this type. This method is similar to
     * {@link Class#getDeclaredFields()} in terms of returned fields.
     */
    public abstract Field[] getDeclaredFields();

    /**
     * Returns the {@code <clinit>} method for this class if there is one.
     */
    public Method getClassInitializer() {
        return Arrays.stream(getDeclaredMethods()).filter(new Predicate<Method>() {
            @Override
            public boolean test(Method m) {
                return "<clinit>".equals(m.getName());
            }
        }).findAny().orElse(null);
    }

    public Method findDeclaredConcreteMethod(String methodName, SignatureDescriptor signature) {
        for (Method method : getDeclaredMethods()) {
            if (!method.isAbstract() && method.getName().equals(methodName) && method.getSignature().equals(signature)) {
                return method;
            }
        }
        return null;
    }

    public Method findMethod(String methodName, SignatureDescriptor signature) {
        for (Method method : getDeclaredMethods()) {
            if (method.getName().equals(methodName) && method.getSignature().equals(signature)) {
                return method;
            }
        }
        if (getSuperclass() != null) {
            Method m = getSuperclass().findMethod(methodName, signature);
            if (m != null) {
                return m;
            }
        }

        // No concrete method found, look interface methods.
        if (isAbstract()) {
            // Look
            for (Klass i : getInterfaces()) {
                Method m = i.findMethod(methodName, signature);
                if (m != null) {
                    return m;
                }
            }
        }

        return null;
    }

    public Method findConcreteMethod(String methodName, SignatureDescriptor signature) {
        for (Klass c = this; c != null; c = c.getSuperclass()) {
            Method method = c.findDeclaredConcreteMethod(methodName, signature);
            if (method != null && !method.isAbstract()) {
                return method;
            }
        }
        return null;
    }

    public final ByteString<Type> getType() {
        return type;
    }

    @Override
    public String toString() {
        return "Klass<" + getType() + ">";
    }


    // region Meta.Klass

    public void safeInitialize() {
        try {
            initialize();
        } catch (EspressoException e) {
            throw EspressoLanguage.getCurrentContext().getMeta().throwEx(ExceptionInInitializerError.class, e.getException());
        }
    }

    public Klass getComponentType() {
        return isArray() ? meta(klass.getComponentType()) : null;
    }

    public Meta getMeta() {
        return getContext().getMeta();
    }

//    public Method[] methods(boolean includeInherited) {
//        return methodStream(includeInherited).toArray(new IntFunction<Method[]>() {
//            @Override
//            public Method[] apply(int value) {
//                return new Method[value];
//            }
//        });
//    }

//    private Stream<Method> methodStream(boolean includeInherited) {
//        Stream<Method> methods = Arrays.stream(klass.getDeclaredMethods()).map(new Function<Method, Method>() {
//            @Override
//            public Method apply(Method method) {
//                return meta(method);
//            }
//        });
//        if (includeInherited && getSuperclass() != null) {
//            methods = Stream.concat(methods, getSuperclass().methodStream(includeInherited));
//        }
//        return methods;
//    }

    public ObjectKlass getSupertype() {
        if (isArray()) {
            Klass componentType = getComponentType();
            if (this == getMeta().OBJECT.getArray() || componentType.isPrimitive()) {
                return getMeta().OBJECT;
            }
            return componentType.getSupertype().array();
        }
        if (isInterface()) {
            return getMeta().OBJECT;
        }
        return getSuperclass();
    }

    /**
     * Determines if this type is either the same as, or is a superclass or superinterface of,
     * the type represented by the specified parameter. This method is identical to
     * {@link Class#isAssignableFrom(Class)} in terms of the value return for this type.
     */
    @TruffleBoundary
    public boolean isAssignableFrom(Klass other) {
        if (this == other) {
            return true;
        }
        if (this.isPrimitive() || other.isPrimitive()) {
            // Reference equality is enough within the same context.
            return this == other;
        }
        if (this.isArray() && other.isArray()) {
            return this.getComponentType().isAssignableFrom(other.getComponentType());
        }
        if (isInterface()) {
            return other.getInterfacesStream(true).anyMatch(new Predicate<Klass>() {
                @Override
                public boolean test(Klass i) {
                    return i == Klass.this;
                }
            });
        }
        return other.getSupertypesStream(true).anyMatch(new Predicate<Klass>() {
            @Override
            public boolean test(Klass k) {
                return k == Klass.this;
            }
        });
    }

    private final boolean isPrimaryType() {
        assert !isPrimitive();
        if (isArray()) {
            return getElementalType().isPrimaryType();
        }
        return !isInterface();
    }

    @TruffleBoundary
    private Stream<ObjectKlass> getSupertypesStream(boolean includeOwn) {
        ObjectKlass supertype = getSupertype();
        Stream<Klass> supertypes;
        if (supertype != null) {
            supertypes = supertype.getSupertypesStream(true);
        } else {
            supertypes = Stream.empty();
        }
        if (includeOwn) {
            return Stream.concat(Stream.of(this), supertypes);
        }
        return supertypes;
    }

    @TruffleBoundary
    private Stream<ObjectKlass> getInterfacesStream(boolean includeInherited) {
        Stream<ObjectKlass> interfaces = Stream.of(getInterfaces());
        ObjectKlass superclass = getSuperclass();
        if (includeInherited && superclass != null) {
            interfaces = Stream.concat(interfaces, superclass.getInterfacesStream(includeInherited));
        }
        if (includeInherited) {
            interfaces = interfaces.flatMap(new Function<ObjectKlass, Stream<? extends ObjectKlass>>() {
                @Override
                public Stream<? extends ObjectKlass> apply(ObjectKlass i) {
                    return Stream.concat(Stream.of(i), i.getInterfacesStream(includeInherited));
                }
            });
        }
        return interfaces;
    }

    public List<Klass> getInterfaces(boolean includeSuperclasses) {
        return getInterfacesStream(includeSuperclasses).collect(collectingAndThen(toList(), new Function<List<Klass>, List<Klass>>() {
            @Override
            public List<Klass> apply(List<Klass> list) {
                return Collections.unmodifiableList(list);
            }
        }));
    }

    @TruffleBoundary
    public StaticObject allocateInstance() {
        assert !isArray();
        return getContext().getInterpreterToVM().newObject(klass);
    }

    public String getName() {
        return MetaUtil.internalNameToJava(getName(), true, true);
    }

    public String getInternalName() {
        return getName();
    }

    @TruffleBoundary
    public Object allocateArray(int length) {
        return getContext().getInterpreterToVM().newArray(klass, length);
    }

    @TruffleBoundary
    public Object allocateArray(int length, IntFunction<StaticObject> generator) {
        // TODO(peterssen): Store check is missing.
        StaticObject[] array = new StaticObject[length];
        for (int i = 0; i < array.length; ++i) {
            array[i] = generator.apply(i);
        }
        return new StaticObjectArray(getArrayClass(), array);
    }

    public Optional<Method.WithInstance> getClassInitializer() {
        Method clinit = findDeclaredMethod("<clinit>", void.class);
        return Optional.ofNullable(clinit).map(new Function<Method, Method.WithInstance>() {
            @Override
            public Method.WithInstance apply(Method mi) {
                Method m = meta(mi);
                assert m.isClassInitializer();
                return m.forInstance(tryInitializeAndGetStatics());
            }
        });
    }

    @TruffleBoundary
    public Method method(String name, Class<?> returnType, Class<?>... parameterTypes) {
        SignatureDescriptor target = getContext().getSignatureDescriptors().create(returnType, parameterTypes);
        Method found = Arrays.stream(getDeclaredMethods()).filter(new Predicate<Method>() {
            @Override
            public boolean test(Method m) {
                return m.getName().equals(name) && m.getSignature().equals(target);
            }
        }).findFirst().orElse(null);
        if (found == null) {
            if (getSuperclass() != null) {
                return getSuperclass().method(name, returnType, parameterTypes);
            }
        }
        return found == null ? null : new Method(found);
    }

    public Method.WithInstance staticMethod(String name, Class<?> returnType, Class<?>... parameterTypes) {
        Method m = method(name, returnType, parameterTypes);
        assert m.isStatic();
        return m.forInstance(m.getDeclaringClass().rawKlass().tryInitializeAndGetStatics());
    }

    public Field declaredField(String name) {
        // TODO(peterssen): Improve lookup performance.
        for (Field f : getDeclaredFields()) {
            if (name.equals(f.getName())) {
                return new Field(f);
            }
        }
        return null;
    }

    public Field field(String name) {
        // TODO(peterssen): Improve lookup performance.
        Field f = declaredField(name);
        if (f == null) {
            if (getSuperclass() != null) {
                return getSuperclass().field(name);
            }
        }
        return f;
    }
}