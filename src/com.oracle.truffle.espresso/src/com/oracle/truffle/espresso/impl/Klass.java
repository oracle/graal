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

import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.stream.Stream;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.classfile.Constants;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.descriptors.Symbol.Signature;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.descriptors.Types;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.meta.ModifiersProvider;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.StaticObjectArray;
import com.oracle.truffle.espresso.runtime.StaticObjectClass;
import com.oracle.truffle.espresso.substitutions.Host;
import com.oracle.truffle.espresso.vm.InterpreterToVM;
import com.oracle.truffle.object.DebugCounter;

public abstract class Klass implements ModifiersProvider, ContextAccess {

    public static final Klass[] EMPTY_ARRAY = new Klass[0];

    static final DebugCounter methodLookupCount = DebugCounter.create("methodLookupCount");
    static final DebugCounter fieldLookupCount = DebugCounter.create("fieldLookupCount");
    static final DebugCounter declaredMethodLookupCount = DebugCounter.create("declaredMethodLookupCount");
    static final DebugCounter declaredFieldLookupCount = DebugCounter.create("declaredFieldLookupCount");

    private final Symbol<Name> name;
    private final Symbol<Type> type;
    private final JavaKind kind;
    private final EspressoContext context;
    private final ObjectKlass superKlass;

    @CompilationFinal(dimensions = 1) //
    private final ObjectKlass[] superInterfaces;

    @CompilationFinal //
    private ArrayKlass arrayClass;

    @CompilationFinal //
    private StaticObjectClass mirrorCache;

    public final ObjectKlass[] getSuperInterfaces() {
        return superInterfaces;
    }

    public Klass(EspressoContext context, Symbol<Name> name, Symbol<Type> type, ObjectKlass superKlass, ObjectKlass[] superInterfaces) {
        this.context = context;
        this.name = name;
        this.type = type;
        this.kind = Types.getJavaKind(type);
        this.superKlass = superKlass;
        this.superInterfaces = superInterfaces;
    }

    public abstract @Host(ClassLoader.class) StaticObject getDefiningClassLoader();

    public abstract ConstantPool getConstantPool();

    public final JavaKind getJavaKind() {
        return kind;
    }

    public final boolean isArray() {
        return Types.isArray(getType());
    }

    public StaticObject mirror() {
        // TODO(peterssen): Make thread-safe.
        if (mirrorCache == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            mirrorCache = new StaticObjectClass(this);
        }
        return mirrorCache;
    }

    @Override
    public final boolean equals(Object obj) {
        if (!(obj instanceof Klass)) {
            return false;
        }
        Klass that = (Klass) obj;
        return this.mirror().equals(that.mirror());
    }

    public final ArrayKlass getArrayClass() {
        ArrayKlass ak = arrayClass;
        if (ak == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            synchronized (this) {
                ak = arrayClass;
                if (ak == null) {
                    ak = createArrayKlass();
                    arrayClass = ak;
                }
            }
        }
        return ak;
    }

    public final ArrayKlass array() {
        return getArrayClass();
    }

    public ArrayKlass getArrayClass(int dimensions) {
        assert dimensions > 0;
        ArrayKlass array = getArrayClass();

        // Careful with of impossible void[].
        if (array == null) {
            return null;
        }

        for (int i = 1; i < dimensions; ++i) {
            array = array.getArrayClass();
        }
        return array;
    }

    protected ArrayKlass createArrayKlass() {
        return new ArrayKlass(this);
    }

    @Override
    public final int hashCode() {
        return getType().hashCode();
    }

    @Override
    public final EspressoContext getContext() {
        return context;
    }

    public final StaticObject tryInitializeAndGetStatics() {
        initialize();
        return getStatics();
    }

    public abstract StaticObject getStatics();

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
        return kind.isPrimitive();
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
    @TruffleBoundary
    public final boolean isAssignableFrom(Klass other) {
        if (this == other) {
            return true;
        }
        if (this.isPrimitive() || other.isPrimitive()) {
            // Reference equality is enough within the same context.
            assert this.getContext() == other.getContext();
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
        return getSuperKlass() == null && !isInterface() && getJavaKind() == JavaKind.Object;
    }

    /**
     * Gets the super class of this type. If this type represents either the {@code Object} class,
     * an interface, a primitive type, or void, then null is returned. If this object represents an
     * array class then the type object representing the {@code Object} class is returned.
     */
    public final ObjectKlass getSuperKlass() {
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

    public abstract Klass getElementalType();

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
        return lookupDeclaredMethod(Name.CLINIT, Signature._void);
    }

    public final Symbol<Type> getType() {
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
            throw getMeta().throwExWithCause(ExceptionInInitializerError.class, e.getException());
        }
    }

    public abstract Klass getComponentType();

    public final Klass getSupertype() {
        if (isArray()) {
            Klass component = getComponentType();
            if (this == getMeta().Object.array() || component.isPrimitive()) {
                return getMeta().Object;
            }
            return component.getSupertype().array();
        }
        if (isInterface()) {
            return getMeta().Object;
        }
        return getSuperKlass();
    }

    private final boolean isPrimaryType() {
        assert !isPrimitive();
        if (isArray()) {
            return getElementalType().isPrimaryType();
        }
        return !isInterface();
    }

    @TruffleBoundary
    private Stream<Klass> getSupertypesStream(boolean includeOwn) {
        Klass supertype = getSupertype();
        Stream<Klass> supertypes = (supertype != null)
                        ? supertype.getSupertypesStream(true)
                        : Stream.empty();
        if (includeOwn) {
            return Stream.concat(Stream.of(this), supertypes);
        }
        return supertypes;
    }

    @TruffleBoundary
    protected Stream<ObjectKlass> getInterfacesStream(boolean includeInherited) {
        Stream<ObjectKlass> interfaces = Stream.of(getInterfaces());
        ObjectKlass superclass = getSuperKlass();
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

    @TruffleBoundary
    public StaticObjectArray allocateArray(int length) {
        return InterpreterToVM.newArray(this, length);
    }

    @TruffleBoundary
    public StaticObjectArray allocateArray(int length, IntFunction<StaticObject> generator) {
        // TODO(peterssen): Store check is missing.
        StaticObject[] array = new StaticObject[length];
        for (int i = 0; i < array.length; ++i) {
            array[i] = generator.apply(i);
        }
        return new StaticObjectArray(getArrayClass(), array);
    }

    // region Lookup

    public final Field lookupDeclaredField(Symbol<Name> fieldName, Symbol<Type> fieldType) {
        declaredFieldLookupCount.inc();
        // TODO(peterssen): Improve lookup performance.
        for (Field field : getDeclaredFields()) {
            if (fieldName.equals(field.getName()) && fieldType.equals(field.getType())) {
                return field;
            }
        }
        return null;
    }

    public final Field lookupField(Symbol<Name> fieldName, Symbol<Type> fieldType) {
        fieldLookupCount.inc();
        // TODO(peterssen): Improve lookup performance.
        Field field = lookupDeclaredField(fieldName, fieldType);
        if (field == null && getSuperKlass() != null) {
            return getSuperKlass().lookupField(fieldName, fieldType);
        }
        return field;
    }

    public final Method lookupDeclaredMethod(Symbol<Name> methodName, Symbol<Signature> signature) {
        declaredMethodLookupCount.inc();
        // TODO(peterssen): Improve lookup performance.
        for (Method method : getDeclaredMethods()) {
            if (methodName.equals(method.getName()) && signature.equals(method.getRawSignature())) {
                return method;
            }
        }
        return null;
    }

    public final Method lookupMethod(Symbol<Name> methodName, Symbol<Signature> signature) {
        methodLookupCount.inc();
        // TODO(peterssen): Improve lookup performance.
        Method method = lookupDeclaredMethod(methodName, signature);
        if (method == null && getSuperKlass() != null) {
            return getSuperKlass().lookupMethod(methodName, signature);
        }
        return method;
    }

    @Override
    public final int getModifiers() {
        return getFlags() & Constants.JVM_RECOGNIZED_CLASS_MODIFIERS;
    }

    protected abstract int getFlags();

    public final StaticObject allocateInstance() {
        return InterpreterToVM.newObject(this);
    }

    public String getRuntimePackage() {
        String typeString = getType().toString();
        int lastDot = typeString.lastIndexOf('.');
        if (lastDot < 0)
            return "";
        String pkg = typeString.substring(lastDot + 1);
        if (pkg.endsWith(";")) {
            return pkg.substring(0, pkg.length() - 1);
        }
        return pkg;
    }

    public Symbol<Name> getName() {
        return name;
    }
}