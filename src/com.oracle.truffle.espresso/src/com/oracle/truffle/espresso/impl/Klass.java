package com.oracle.truffle.espresso.impl;

import java.util.Arrays;

import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.meta.ModifiersProvider;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.StaticObjectClass;
import com.oracle.truffle.espresso.runtime.StaticObjectImpl;
import com.oracle.truffle.espresso.types.SignatureDescriptor;
import com.oracle.truffle.espresso.types.TypeDescriptor;

public abstract class Klass implements ModifiersProvider {

    public final static Klass[] EMPTY_ARRAY = new Klass[0];
    private StaticObject statics;
    private ArrayKlass arrayClass;
    private final String name;

    Klass(String name) {
        this.name = name;
    }

    public abstract JavaKind getJavaKind();

    public abstract boolean isArray();

    public String getName() {
        return name;
    }

    private StaticObject mirrorCache;

    public StaticObject mirror() {
        if (mirrorCache == null) {
            mirrorCache = new StaticObjectClass(getContext().getRegistries().resolve(getContext().getTypeDescriptors().CLASS, null));
            ((StaticObjectClass) mirrorCache).setMirror(this);
        }
        return mirrorCache;
    }

    public abstract Object getClassLoader();

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
            arrayClass = new ArrayKlass(this);
        }
        return arrayClass;
    }

    @Override
    public final int hashCode() {
        return getName().hashCode();
    }

    public abstract ConstantPool getConstantPool();

    public abstract EspressoContext getContext();

    public MethodInfo findDeclaredMethod(String name, Class<?> returnType, Class<?>... parameterTypes) {
        SignatureDescriptor signature = getContext().getSignatureDescriptors().create(returnType, parameterTypes);
        return findDeclaredConcreteMethod(name, signature);
    }

    public StaticObject getStatics() {
        if (statics == null) {
            statics = new StaticObjectImpl(this, true);
        }
        return statics;
    }

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
        return Arrays.stream(getDeclaredMethods()).filter(m -> "<clinit>".equals(m.getName())).findAny().orElse(null);
    }

    public MethodInfo findDeclaredConcreteMethod(String name, SignatureDescriptor signature) {
        for (MethodInfo method : getDeclaredMethods()) {
            if (!method.isAbstract() && method.getName().equals(name) && method.getSignature().equals(signature)) {
                return method;
            }
        }
        return null;
    }

    public MethodInfo findMethod(String name, SignatureDescriptor signature) {
        for (MethodInfo method : getDeclaredMethods()) {
            if (method.getName().equals(name) && method.getSignature().equals(signature)) {
                return method;
            }
        }
        if (getSuperclass() != null) {
            return getSuperclass().findMethod(name, signature);
        }

        return null;
    }

    public MethodInfo findConcreteMethod(String name, SignatureDescriptor signature) {
        for (Klass c = this; c != null; c = c.getSuperclass()) {
            MethodInfo method = c.findDeclaredConcreteMethod(name, signature);
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