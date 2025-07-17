/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.jvmci.meta;

import static com.oracle.truffle.espresso.jvmci.EspressoJVMCIRuntime.runtime;
import static com.oracle.truffle.espresso.jvmci.meta.ExtendedModifiers.ANNOTATION;
import static com.oracle.truffle.espresso.jvmci.meta.ExtendedModifiers.ENUM;
import static com.oracle.truffle.espresso.jvmci.meta.ExtendedModifiers.FINALIZER;
import static com.oracle.truffle.espresso.jvmci.meta.ExtendedModifiers.SYNTHETIC;
import static java.lang.reflect.Modifier.ABSTRACT;
import static java.lang.reflect.Modifier.FINAL;
import static java.lang.reflect.Modifier.INTERFACE;
import static java.lang.reflect.Modifier.PUBLIC;
import static java.util.Objects.requireNonNull;

import java.lang.annotation.Annotation;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.Assumptions;
import jdk.vm.ci.meta.Assumptions.AssumptionResult;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ModifiersProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.UnresolvedJavaField;
import jdk.vm.ci.meta.UnresolvedJavaType;

public final class EspressoResolvedInstanceType extends EspressoResolvedObjectType {
    private static final int JVM_CLASS_MODIFIERS = PUBLIC | FINAL | INTERFACE | ABSTRACT | ANNOTATION | ENUM | SYNTHETIC;
    private static final SortByOffset fieldSortingMethod = new SortByOffset();

    private static final class SortByOffset implements Comparator<ResolvedJavaField> {
        @Override
        public int compare(ResolvedJavaField a, ResolvedJavaField b) {
            return a.getOffset() - b.getOffset();
        }
    }

    private static final EspressoResolvedInstanceType[] NO_INSTANCE_TYPES = new EspressoResolvedInstanceType[0];

    private final EspressoConstantPool constantPool;
    private EspressoResolvedJavaField[] instanceFields;
    private EspressoResolvedJavaField[] staticFields;
    private EspressoResolvedInstanceType[] interfaces;
    private EspressoResolvedInstanceType superClass;
    private String name;

    @SuppressWarnings("this-escape")
    public EspressoResolvedInstanceType() {
        constantPool = new EspressoConstantPool(this);
    }

    @Override
    public boolean hasFinalizer() {
        return (getFlags() & FINALIZER) != 0;
    }

    @Override
    public AssumptionResult<Boolean> hasFinalizableSubclass() {
        return new Assumptions.AssumptionResult<>(true);
    }

    @Override
    public boolean isArray() {
        return false;
    }

    @Override
    public int getModifiers() {
        return getFlags() & JVM_CLASS_MODIFIERS;
    }

    private native int getFlags();

    @Override
    public boolean isInterface() {
        return Modifier.isInterface(getFlags());
    }

    @Override
    public boolean isInstanceClass() {
        return !isInterface();
    }

    @Override
    public boolean isEnum() {
        return (getFlags() & ENUM) != 0;
    }

    @Override
    public native boolean isInitialized();

    @Override
    public native void initialize();

    @Override
    public native boolean isLinked();

    @Override
    public boolean isAssignableFrom(ResolvedJavaType other) {
        requireNonNull(other);
        if (other instanceof EspressoResolvedInstanceType) {
            EspressoResolvedInstanceType otherType = (EspressoResolvedInstanceType) other;
            return getMirror().isAssignableFrom(otherType.getMirror());
        }
        if (other instanceof EspressoResolvedArrayType) {
            if (this.equals(runtime().getJavaLangObject())) {
                return true;
            }
            if (this.isInterface()) {
                for (EspressoResolvedInstanceType iface : runtime().getArrayInterfaces()) {
                    if (this.equals(iface)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public native boolean declaresDefaultMethods();

    @Override
    public native boolean hasDefaultMethods();

    @Override
    public EspressoResolvedInstanceType getSuperclass() {
        if (isInterface()) {
            return null;
        }
        EspressoResolvedInstanceType javaLangObject = runtime().getJavaLangObject();
        if (this.equals(javaLangObject)) {
            return null;
        }
        // Cache result of native call
        if (superClass == null) {
            superClass = getSuperclass0();
        }
        return superClass;
    }

    private native EspressoResolvedInstanceType getSuperclass0();

    @Override
    public ResolvedJavaType[] getInterfaces() {
        if (interfaces == null) {
            interfaces = getInterfaces0();
            if (interfaces == null) {
                interfaces = NO_INSTANCE_TYPES;
            }
        }
        return interfaces;
    }

    private native EspressoResolvedInstanceType[] getInterfaces0();

    @Override
    public EspressoResolvedInstanceType getSingleImplementor() {
        if (!isInterface()) {
            throw new JVMCIError("Cannot call getSingleImplementor() on a non-interface type: %s", this);
        }
        // espresso only supports finding a leaf concrete implementor.
        // if there is one, it's also the only implementor that matter since others cannot be
        // instanciated
        EspressoResolvedInstanceType implementor = espressoSingleImplementor();
        if (implementor == null) {
            return this;
        }
        assert implementor.isConcrete();
        assert this.isAssignableFrom(implementor);
        // find the first class that implements the interface
        while (true) {
            EspressoResolvedInstanceType superclass = implementor.getSuperclass();
            if (!this.isAssignableFrom(superclass)) {
                return implementor;
            }
            implementor = superclass;
        }
    }

    native int getVtableLength();

    private boolean directlyImplements(EspressoResolvedInstanceType iface) {
        for (ResolvedJavaType i : getInterfaces()) {
            if (i.equals(iface)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public EspressoResolvedObjectType getSupertype() {
        if (isInterface()) {
            return runtime().getJavaLangObject();
        }
        return getSuperclass();
    }

    @Override
    public AssumptionResult<ResolvedJavaType> findLeafConcreteSubtype() {
        if (isLeaf()) {
            // No assumptions are required.
            return new AssumptionResult<>(this);
        }
        if (isLeafClass()) {
            return new AssumptionResult<>(this, new Assumptions.LeafType(this));
        }
        if (isAbstract()) {
            EspressoResolvedInstanceType espressoSingleImplementor = espressoSingleImplementor();
            if (espressoSingleImplementor != null) {
                return leafConcreteSubtype(espressoSingleImplementor);
            }
        }
        return null;
    }

    private native EspressoResolvedInstanceType espressoSingleImplementor();

    private AssumptionResult<ResolvedJavaType> leafConcreteSubtype(EspressoResolvedInstanceType type) {
        if (type.isLeaf()) {
            return new AssumptionResult<>(type, new Assumptions.ConcreteSubtype(this, type));
        } else {
            return new AssumptionResult<>(type, new Assumptions.LeafType(type), new Assumptions.ConcreteSubtype(this, type));
        }
    }

    private native boolean isLeafClass();

    @Override
    public String getName() {
        if (name == null) {
            name = getName0();
        }
        return name;
    }

    private native String getName0();

    @Override
    public ResolvedJavaType getComponentType() {
        return null;
    }

    @Override
    public boolean isDefinitelyResolvedWithRespectTo(ResolvedJavaType accessingClass) {
        assert accessingClass != null;
        ResolvedJavaType elementType = getElementalType();
        if (elementType.isPrimitive()) {
            // Primitive type resolution is context free.
            return true;
        }
        if (elementType.getName().startsWith("Ljava/") && hasSameClassLoader(runtime().getJavaLangObject())) {
            // Classes in a java.* package defined by the boot class loader are always resolved.
            return true;
        }
        EspressoResolvedInstanceType otherMirror = (EspressoResolvedInstanceType) accessingClass.getElementalType();
        return hasSameClassLoader(otherMirror);
    }

    private native boolean hasSameClassLoader(EspressoResolvedInstanceType otherMirror);

    @Override
    public EspressoResolvedJavaType resolve(ResolvedJavaType accessingClass) {
        if (isDefinitelyResolvedWithRespectTo(requireNonNull(accessingClass))) {
            return this;
        }
        EspressoResolvedInstanceType accessingType = (EspressoResolvedInstanceType) accessingClass;
        return (EspressoResolvedJavaType) runtime().lookupType(getName(), accessingType, true);
    }

    @Override
    public EspressoResolvedJavaMethod resolveMethod(ResolvedJavaMethod method, ResolvedJavaType callerType) {
        EspressoResolvedJavaMethod espressoMethod = (EspressoResolvedJavaMethod) method;
        if (isInterface()) {
            // Methods can only be resolved against concrete types
            return null;
        }
        if (espressoMethod.isConcrete() && espressoMethod.getDeclaringClass().equals(this) && espressoMethod.isPublic() && !isSignaturePolymorphicHolder(espressoMethod.getDeclaringClass())) {
            return espressoMethod;
        }
        if (!espressoMethod.getDeclaringClass().isAssignableFrom(this)) {
            return null;
        }
        if (espressoMethod.isConstructor()) {
            // Constructor calls should have been checked in the verifier and the method's
            // declaring class is assignable from this (see above) so treat it as resolved.
            return espressoMethod;
        }
        return runtime().resolveMethod(this, espressoMethod, (EspressoResolvedInstanceType) callerType);
    }

    private static boolean isSignaturePolymorphicHolder(ResolvedJavaType type) {
        String name = type.getName();
        return "Ljava/lang/invoke/MethodHandle;".equals(name) || "Ljava/lang/invoke/VarHandle;".equals(name);
    }

    @Override
    public AssumptionResult<ResolvedJavaMethod> findUniqueConcreteMethod(ResolvedJavaMethod method) {
        EspressoResolvedJavaMethod espressoMethod = (EspressoResolvedJavaMethod) method;
        EspressoResolvedInstanceType declaredHolder = espressoMethod.getDeclaringClass();
        if (!declaredHolder.isAssignableFrom(this) || this.equals(declaredHolder) || !isLinked() || isInterface()) {
            if (espressoMethod.canBeStaticallyBound()) {
                // No assumptions are required.
                return new AssumptionResult<>(espressoMethod);
            }
            if (espressoMethod.isLeafMethod()) {
                return new AssumptionResult<>(espressoMethod, new Assumptions.ConcreteMethod(method, declaredHolder, espressoMethod));
            }
            return null;
        }

        EspressoResolvedJavaMethod resolvedMethod = resolveMethod(espressoMethod, this);
        if (resolvedMethod == null) {
            // The type isn't known to implement the method.
            return null;
        }
        if (resolvedMethod.canBeStaticallyBound()) {
            // No assumptions are required.
            return new AssumptionResult<>(resolvedMethod);
        }
        if (espressoMethod.isLeafMethod()) {
            return new AssumptionResult<>(espressoMethod, new Assumptions.ConcreteMethod(method, declaredHolder, espressoMethod));
        }
        return null;
    }

    @Override
    public EspressoResolvedJavaField[] getInstanceFields(boolean includeSuperclasses) {
        if (instanceFields == null) {
            if (isInterface()) {
                instanceFields = NO_FIELDS;
            } else {
                boolean needsSort = true;
                EspressoResolvedJavaField[] result = getInstanceFields0();
                if (getSuperclass() != null) {
                    EspressoResolvedJavaField[] superFields = getSuperclass().getInstanceFields(true);
                    if (superFields.length > 0) {
                        if (result.length > 0) {
                            EspressoResolvedJavaField[] merged = new EspressoResolvedJavaField[superFields.length + result.length];
                            System.arraycopy(superFields, 0, merged, 0, superFields.length);
                            System.arraycopy(result, 0, merged, superFields.length, result.length);
                            result = merged;
                        } else {
                            result = superFields;
                            needsSort = false;
                        }
                    }
                }
                if (needsSort) {
                    Arrays.sort(result, fieldSortingMethod);
                }
                assert Arrays.stream(result).noneMatch(ModifiersProvider::isStatic);
                instanceFields = result;
            }
        }
        if (includeSuperclasses || getSuperclass() == null) {
            return instanceFields;
        }
        // filter superclass fields out
        int superClassFieldCount = getSuperclass().getInstanceFields(true).length;
        if (superClassFieldCount == instanceFields.length) {
            // This class does not have any instance fields of its own.
            return NO_FIELDS;
        } else if (superClassFieldCount != 0) {
            // Fields of the current class can be interleaved with fields of its super-classes
            // Since they were sorted and we are only removing entries, the result will be sorted
            assert instanceFields.length > superClassFieldCount : this + ": instanceFields.length=" + instanceFields.length + " superClassFieldCount=" + superClassFieldCount;
            EspressoResolvedJavaField[] result = new EspressoResolvedJavaField[instanceFields.length - superClassFieldCount];
            int i = 0;
            for (EspressoResolvedJavaField f : instanceFields) {
                if (f.getDeclaringClass().equals(this)) {
                    assert i == 0 || result[i - 1].getOffset() < f.getOffset();
                    result[i++] = f;
                }
            }
            return result;
        } else {
            // The super classes of this class do not have any instance fields.
            return instanceFields;
        }
    }

    @Override
    public EspressoResolvedJavaField[] getStaticFields() {
        if (staticFields == null) {
            EspressoResolvedJavaField[] result = getStaticFields0();
            Arrays.sort(result, fieldSortingMethod);
            assert Arrays.stream(result).allMatch(ModifiersProvider::isStatic);
            staticFields = result;
        }
        return staticFields;
    }

    private native EspressoResolvedJavaField[] getStaticFields0();

    private native EspressoResolvedJavaField[] getInstanceFields0();

    @Override
    public ResolvedJavaField findInstanceFieldWithOffset(long offset, JavaKind expectedKind) {
        ResolvedJavaField[] declaredFields = getInstanceFields(true);
        return findFieldWithOffset(offset, expectedKind, declaredFields);
    }

    private static ResolvedJavaField findFieldWithOffset(long offset, JavaKind expectedEntryKind, ResolvedJavaField[] declaredFields) {
        for (ResolvedJavaField field : declaredFields) {
            if (field.getOffset() == offset && expectedEntryKind == field.getJavaKind()) {
                return field;
            }
        }
        return null;
    }

    @Override
    public native String getSourceFileName();

    @Override
    public boolean isLocal() {
        return getMirror().isLocalClass();
    }

    @Override
    public boolean isMember() {
        return getMirror().isMemberClass();
    }

    @Override
    public ResolvedJavaType getEnclosingType() {
        Class<?> enclosingClass = getMirror().getEnclosingClass();
        if (enclosingClass == null) {
            return null;
        }
        return runtime().getHostJVMCIBackend().getMetaAccess().lookupJavaType(enclosingClass);
    }

    @Override
    public native void link();

    @Override
    public EspressoResolvedJavaMethod[] getDeclaredConstructors(boolean forceLink) {
        if (forceLink) {
            link();
        }
        return getDeclaredConstructors0();
    }

    private native EspressoResolvedJavaMethod[] getDeclaredConstructors0();

    @Override
    public EspressoResolvedJavaMethod[] getDeclaredMethods(boolean forceLink) {
        if (forceLink) {
            link();
        }
        return getDeclaredMethods0();
    }

    private native EspressoResolvedJavaMethod[] getDeclaredMethods0();

    @Override
    public List<ResolvedJavaMethod> getAllMethods(boolean forceLink) {
        if (forceLink) {
            link();
        }
        return Arrays.asList(getAllMethods0());
    }

    private native EspressoResolvedJavaMethod[] getAllMethods0();

    @Override
    public native EspressoResolvedJavaMethod getClassInitializer();

    @Override
    public boolean isCloneableWithAllocation() {
        throw JVMCIError.unimplemented();
    }

    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        return getMirror().getAnnotation(annotationClass);
    }

    @Override
    public Annotation[] getAnnotations() {
        return getMirror().getAnnotations();
    }

    @Override
    public Annotation[] getDeclaredAnnotations() {
        return getMirror().getDeclaredAnnotations();
    }

    @Override
    public ResolvedJavaField resolveField(UnresolvedJavaField unresolvedJavaField, ResolvedJavaType accessingClass) {
        for (ResolvedJavaField field : getInstanceFields(false)) {
            if (field.getName().equals(unresolvedJavaField.getName())) {
                return field;
            }
        }
        for (ResolvedJavaField field : getStaticFields()) {
            if (field.getName().equals(unresolvedJavaField.getName())) {
                return field;
            }
        }
        throw new InternalError(unresolvedJavaField.toString());
    }

    @Override
    public ResolvedJavaType lookupType(UnresolvedJavaType unresolvedJavaType, boolean resolve) {
        JavaType javaType = runtime().lookupType(unresolvedJavaType.getName(), this, resolve);
        if (javaType instanceof ResolvedJavaType) {
            return (ResolvedJavaType) javaType;
        }
        return null;
    }

    public EspressoConstantPool getConstantPool() {
        return constantPool;
    }

    @Override
    protected native Class<?> getMirror0();

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        EspressoResolvedInstanceType that = (EspressoResolvedInstanceType) o;
        return equals0(that);
    }

    private native boolean equals0(EspressoResolvedInstanceType that);

    @Override
    public native int hashCode();
}
