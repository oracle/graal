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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.meta.ModifiersProvider;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.StaticObjectClass;
import com.oracle.truffle.espresso.types.SignatureDescriptor;
import com.oracle.truffle.espresso.types.TypeDescriptor;

import java.util.Arrays;
import java.util.function.Predicate;

public abstract class Klass implements ModifiersProvider {

    protected final JavaKind kind;
    public final static Klass[] EMPTY_ARRAY = new Klass[0];
    private final String name;

    @CompilerDirectives.CompilationFinal //
    private StaticObject statics;

    @CompilerDirectives.CompilationFinal //
    private ArrayKlass arrayClass;

    @CompilerDirectives.CompilationFinal //
    private StaticObjectClass mirrorCache;

    Klass(String name, JavaKind kind) {
        this.name = name;
        this.kind = kind;
    }

    public final JavaKind getJavaKind() {
        return kind;
    }

    public abstract boolean isArray();

    public String getName() {
        return name;
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

    protected ArrayKlass createArrayKlass() {
        return new ArrayKlass(this);
    }

    @Override
    public final int hashCode() {
        return getName().hashCode();
    }

    public abstract ConstantPool getConstantPool();

    public abstract EspressoContext getContext();

    public MethodInfo findDeclaredMethod(String klassName, Class<?> returnType, Class<?>... parameterTypes) {
        SignatureDescriptor signature = getContext().getSignatureDescriptors().create(returnType, parameterTypes);
        return findDeclaredConcreteMethod(klassName, signature);
    }

    public abstract StaticObject tryInitializeAndGetStatics();

    /**
     * Checks whether this type has a finalizer method.
     *
     * @return {@code true} if this class has a finalizer
     */
    public abstract boolean hasFinalizer();

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
    public abstract boolean isPrimitive();

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
    public abstract boolean isLinked();

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
    public abstract Klass getSuperclass();

    /**
     * Gets the interfaces implemented or extended by this type. This method is analogous to
     * {@link Class#getInterfaces()} and as such, only returns the interfaces directly implemented
     * or extended by this type.
     */
    public abstract Klass[] getInterfaces();

    /**
     * Walks the class hierarchy upwards and returns the least common class that is a superclass of
     * both the current and the given type.
     *
     * @return the least common type that is a super type of both the current and the given type, or
     *         {@code null} if primitive types are involved.
     */
    public abstract Klass findLeastCommonAncestor(Klass otherType);

    public abstract Klass getComponentType();

    public Klass getElementalType() {
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
    public abstract MethodInfo resolveMethod(MethodInfo method, Klass callerType);

    /**
     * A convenience wrapper for {@link #resolveMethod(MethodInfo, Klass)} that only returns
     * non-abstract methods.
     *
     * @param method the method to select the implementation of
     * @param callerType the caller or context type used to perform access checks
     * @return the concrete method that would be selected at runtime, or {@code null} if there is no
     *         concrete implementation of {@code method} in this type or any of its superclasses
     */
    public MethodInfo resolveConcreteMethod(MethodInfo method, Klass callerType) {
        MethodInfo resolvedMethod = resolveMethod(method, callerType);
        if (resolvedMethod == null || resolvedMethod.isAbstract()) {
            return null;
        }
        return resolvedMethod;
    }

    /**
     * Returns the instance fields of this class, including {@linkplain FieldInfo#isInternal()
     * internal} fields. A zero-length array is returned for array and primitive types. The order of
     * fields returned by this method is stable. That is, for a single JVM execution the same order
     * is returned each time this method is called. It is also the "natural" order, which means that
     * the JVM would expect the fields in this order if no specific order is given.
     *
     * @param includeSuperclasses if true, then instance fields for the complete hierarchy of this
     *            type are included in the result
     * @return an array of instance fields
     */
    public abstract FieldInfo[] getInstanceFields(boolean includeSuperclasses);

    /**
     * Returns the static fields of this class, including {@linkplain FieldInfo#isInternal()
     * internal} fields. A zero-length array is returned for array and primitive types. The order of
     * fields returned by this method is stable. That is, for a single JVM execution the same order
     * is returned each time this method is called.
     */
    public abstract FieldInfo[] getStaticFields();

    /**
     * Returns the instance field of this class (or one of its super classes) at the given offset,
     * or {@code null} if there is no such field.
     *
     * @param offset the offset of the field to look for
     * @return the field with the given offset, or {@code null} if there is no such field.
     */
    public abstract FieldInfo findInstanceFieldWithOffset(long offset, JavaKind expectedKind);

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
    public abstract MethodInfo[] getDeclaredConstructors();

    /**
     * Returns an array reflecting all the methods declared by this type. This method is similar to
     * {@link Class#getDeclaredMethods()} in terms of returned methods.
     */
    public abstract MethodInfo[] getDeclaredMethods();

    /**
     * Returns an array reflecting all the fields declared by this type. This method is similar to
     * {@link Class#getDeclaredFields()} in terms of returned fields.
     */
    public abstract FieldInfo[] getDeclaredFields();

    /**
     * Returns the {@code <clinit>} method for this class if there is one.
     */
    public MethodInfo getClassInitializer() {
        return Arrays.stream(getDeclaredMethods()).filter(new Predicate<MethodInfo>() {
            @Override
            public boolean test(MethodInfo m) {
                return "<clinit>".equals(m.getName());
            }
        }).findAny().orElse(null);
    }

    public MethodInfo findDeclaredConcreteMethod(String methodName, SignatureDescriptor signature) {
        for (MethodInfo method : getDeclaredMethods()) {
            if (!method.isAbstract() && method.getName().equals(methodName) && method.getSignature().equals(signature)) {
                return method;
            }
        }
        return null;
    }

    public MethodInfo findMethod(String methodName, SignatureDescriptor signature) {
        for (MethodInfo method : getDeclaredMethods()) {
            if (method.getName().equals(methodName) && method.getSignature().equals(signature)) {
                return method;
            }
        }
        if (getSuperclass() != null) {
            MethodInfo m = getSuperclass().findMethod(methodName, signature);
            if (m != null) {
                return m;
            }
        }

        // No concrete method found, look interface methods.
        if (isAbstract()) {
            // Look
            for (Klass i : getInterfaces()) {
                MethodInfo m = i.findMethod(methodName, signature);
                if (m != null) {
                    return m;
                }
            }
        }

        return null;
    }

    public MethodInfo findConcreteMethod(String methodName, SignatureDescriptor signature) {
        for (Klass c = this; c != null; c = c.getSuperclass()) {
            MethodInfo method = c.findDeclaredConcreteMethod(methodName, signature);
            if (method != null && !method.isAbstract()) {
                return method;
            }
        }
        return null;
    }

    public TypeDescriptor getTypeDescriptor() {
        return getContext().getTypeDescriptors().make(getName());
    }

    @Override
    public String toString() {
        return getTypeDescriptor().toJavaName();
    }
}