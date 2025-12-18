/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.espresso.jvmci.meta.ExtendedModifiers.ANNOTATION;
import static com.oracle.truffle.espresso.jvmci.meta.ExtendedModifiers.ENUM;
import static com.oracle.truffle.espresso.jvmci.meta.ExtendedModifiers.FINALIZER;
import static com.oracle.truffle.espresso.jvmci.meta.ExtendedModifiers.SYNTHETIC;
import static java.lang.reflect.Modifier.ABSTRACT;
import static java.lang.reflect.Modifier.FINAL;
import static java.lang.reflect.Modifier.INTERFACE;
import static java.lang.reflect.Modifier.PUBLIC;
import static java.util.Objects.requireNonNull;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collections;
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
import jdk.vm.ci.meta.annotation.AnnotationsInfo;

public abstract class AbstractEspressoResolvedInstanceType extends EspressoResolvedObjectType {
    private static final int JVM_CLASS_MODIFIERS = PUBLIC | FINAL | INTERFACE | ABSTRACT | ANNOTATION | ENUM | SYNTHETIC;
    private static final SortByOffset fieldSortingMethod = new SortByOffset();

    private static final class SortByOffset implements Comparator<ResolvedJavaField> {
        @Override
        public int compare(ResolvedJavaField a, ResolvedJavaField b) {
            return a.getOffset() - b.getOffset();
        }
    }

    protected static final AbstractEspressoResolvedInstanceType[] NO_INSTANCE_TYPES = new AbstractEspressoResolvedInstanceType[0];

    private AbstractEspressoResolvedJavaField[] instanceFields;
    private AbstractEspressoResolvedJavaField[] staticFields;
    private AbstractEspressoResolvedInstanceType[] interfaces;
    private List<AbstractEspressoResolvedJavaRecordComponent> recordComponents;
    private AbstractEspressoResolvedInstanceType superClass;
    private String name;

    @Override
    public boolean hasFinalizer() {
        return (getFlags() & FINALIZER) != 0;
    }

    @Override
    public AssumptionResult<Boolean> hasFinalizableSubclass() {
        // TODO?
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

    protected abstract int getFlags();

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
    public boolean isAssignableFrom(ResolvedJavaType other) {
        requireNonNull(other);
        if (other instanceof AbstractEspressoResolvedInstanceType espressoInstanceType) {
            return isAssignableFrom(espressoInstanceType);
        }
        if (other instanceof EspressoResolvedArrayType) {
            if (this.equals(getJavaLangObject())) {
                return true;
            }
            if (this.isInterface()) {
                for (AbstractEspressoResolvedInstanceType iface : getArrayInterfaces()) {
                    if (this.equals(iface)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    protected abstract boolean isAssignableFrom(AbstractEspressoResolvedInstanceType other);

    @Override
    public AbstractEspressoResolvedInstanceType getSuperclass() {
        if (isInterface()) {
            return null;
        }
        AbstractEspressoResolvedInstanceType javaLangObject = getJavaLangObject();
        if (this.equals(javaLangObject)) {
            return null;
        }
        // Cache result of native call
        if (superClass == null) {
            superClass = getSuperclass0();
        }
        return superClass;
    }

    protected abstract AbstractEspressoResolvedInstanceType getSuperclass0();

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

    protected abstract AbstractEspressoResolvedInstanceType[] getInterfaces0();

    @Override
    public List<? extends AbstractEspressoResolvedJavaRecordComponent> getRecordComponents() {
        if (!isRecord()) {
            return null;
        }
        if (recordComponents == null) {
            recordComponents = Collections.unmodifiableList(Arrays.asList(getRecordComponents0()));
        }
        return recordComponents;
    }

    protected abstract AbstractEspressoResolvedJavaRecordComponent[] getRecordComponents0();

    /// Denotes class file bytes of a `RuntimeVisibleAnnotations` attribute after
    /// the `u2 attribute_name_index; u4 attribute_length` prefix.
    static final int DECLARED_ANNOTATIONS = 0;

    /// Denotes class file bytes of a `RuntimeVisibleParameterAnnotations` attribute after
    /// the `u2 attribute_name_index; u4 attribute_length` prefix.
    static final int PARAMETER_ANNOTATIONS = 1;

    /// Denotes class file bytes of a `RuntimeVisibleTypeAnnotations` attribute after
    /// the `u2 attribute_name_index; u4 attribute_length` prefix.
    static final int TYPE_ANNOTATIONS = 2;

    /// Denotes class file bytes of a `AnnotationDefault` attribute after
    /// the `u2 attribute_name_index; u4 attribute_length` prefix.
    static final int ANNOTATION_DEFAULT_VALUE = 3;

    @Override
    public AnnotationsInfo getRawDeclaredAnnotationInfo() {
        if (isArray()) {
            return null;
        }
        byte[] bytes = getRawAnnotationBytes(DECLARED_ANNOTATIONS);
        return AnnotationsInfo.make(bytes, getConstantPool(), this);
    }

    @Override
    public AnnotationsInfo getTypeAnnotationInfo() {
        if (isArray()) {
            return null;
        }
        byte[] bytes = getRawAnnotationBytes(TYPE_ANNOTATIONS);
        return AnnotationsInfo.make(bytes, getConstantPool(), this);
    }

    protected abstract byte[] getRawAnnotationBytes(int category);

    @Override
    public AbstractEspressoResolvedInstanceType getSingleImplementor() {
        if (!isInterface()) {
            throw new JVMCIError("Cannot call getSingleImplementor() on a non-interface type: %s", this);
        }
        // espresso only supports finding a leaf concrete implementor.
        // if there is one, it's also the only implementor that matter since others cannot be
        // instantiated
        AbstractEspressoResolvedInstanceType implementor = espressoSingleImplementor();
        if (implementor == null) {
            return this;
        }
        assert implementor.isConcrete();
        assert this.isAssignableFrom(implementor);
        // find the first class that implements the interface
        while (true) {
            AbstractEspressoResolvedInstanceType superclass = implementor.getSuperclass();
            if (!this.isAssignableFrom(superclass)) {
                return implementor;
            }
            implementor = superclass;
        }
    }

    protected abstract int getVtableLength();

    @Override
    public EspressoResolvedObjectType getSupertype() {
        if (isInterface()) {
            return getJavaLangObject();
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
            AbstractEspressoResolvedInstanceType espressoSingleImplementor = espressoSingleImplementor();
            if (espressoSingleImplementor != null) {
                return leafConcreteSubtype(espressoSingleImplementor);
            }
        }
        return null;
    }

    protected abstract AbstractEspressoResolvedInstanceType espressoSingleImplementor();

    private AssumptionResult<ResolvedJavaType> leafConcreteSubtype(AbstractEspressoResolvedInstanceType type) {
        if (type.isLeaf()) {
            return new AssumptionResult<>(type, new Assumptions.ConcreteSubtype(this, type));
        } else {
            return new AssumptionResult<>(type, new Assumptions.LeafType(type), new Assumptions.ConcreteSubtype(this, type));
        }
    }

    protected abstract boolean isLeafClass();

    @Override
    public String getName() {
        if (name == null) {
            name = getName0();
        }
        return name;
    }

    protected abstract String getName0();

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
        if (elementType.getName().startsWith("Ljava/") && hasSameClassLoader(getJavaLangObject())) {
            // Classes in a java.* package defined by the boot class loader are always resolved.
            return true;
        }
        AbstractEspressoResolvedInstanceType otherMirror = (AbstractEspressoResolvedInstanceType) accessingClass.getElementalType();
        return hasSameClassLoader(otherMirror);
    }

    protected abstract boolean hasSameClassLoader(AbstractEspressoResolvedInstanceType otherMirror);

    @Override
    public EspressoResolvedJavaType resolve(ResolvedJavaType accessingClass) {
        if (isDefinitelyResolvedWithRespectTo(requireNonNull(accessingClass))) {
            return this;
        }
        AbstractEspressoResolvedInstanceType accessingType = (AbstractEspressoResolvedInstanceType) accessingClass;
        return (EspressoResolvedJavaType) lookupType(getName(), accessingType, true);
    }

    @Override
    public AbstractEspressoResolvedJavaMethod resolveMethod(ResolvedJavaMethod method, ResolvedJavaType callerType) {
        if (isInterface()) {
            // Methods can only be resolved against concrete types
            return null;
        }
        AbstractEspressoResolvedJavaMethod espressoMethod = (AbstractEspressoResolvedJavaMethod) method;
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
        return resolveMethod0(espressoMethod, (AbstractEspressoResolvedInstanceType) callerType);
    }

    protected abstract AbstractEspressoResolvedJavaMethod resolveMethod0(AbstractEspressoResolvedJavaMethod method, AbstractEspressoResolvedInstanceType callerType);

    private static boolean isSignaturePolymorphicHolder(ResolvedJavaType type) {
        String name = type.getName();
        return "Ljava/lang/invoke/MethodHandle;".equals(name) || "Ljava/lang/invoke/VarHandle;".equals(name);
    }

    @Override
    public AssumptionResult<ResolvedJavaMethod> findUniqueConcreteMethod(ResolvedJavaMethod method) {
        EspressoResolvedJavaMethod espressoMethod = (EspressoResolvedJavaMethod) method;
        AbstractEspressoResolvedInstanceType declaredHolder = espressoMethod.getDeclaringClass();
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

        AbstractEspressoResolvedJavaMethod resolvedMethod = resolveMethod(espressoMethod, this);
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
    public AbstractEspressoResolvedJavaField[] getInstanceFields(boolean includeSuperclasses) {
        if (instanceFields == null) {
            if (isInterface()) {
                instanceFields = NO_FIELDS;
            } else {
                boolean needsSort = true;
                AbstractEspressoResolvedJavaField[] result = getInstanceFields0();
                if (getSuperclass() != null) {
                    AbstractEspressoResolvedJavaField[] superFields = getSuperclass().getInstanceFields(true);
                    if (superFields.length > 0) {
                        if (result.length > 0) {
                            AbstractEspressoResolvedJavaField[] merged = new AbstractEspressoResolvedJavaField[superFields.length + result.length];
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
            AbstractEspressoResolvedJavaField[] result = new AbstractEspressoResolvedJavaField[instanceFields.length - superClassFieldCount];
            int i = 0;
            for (AbstractEspressoResolvedJavaField f : instanceFields) {
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
    public AbstractEspressoResolvedJavaField[] getStaticFields() {
        if (staticFields == null) {
            AbstractEspressoResolvedJavaField[] result = getStaticFields0();
            Arrays.sort(result, fieldSortingMethod);
            assert Arrays.stream(result).allMatch(ModifiersProvider::isStatic);
            staticFields = result;
        }
        return staticFields;
    }

    protected abstract AbstractEspressoResolvedJavaField[] getStaticFields0();

    protected abstract AbstractEspressoResolvedJavaField[] getInstanceFields0();

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
    public AbstractEspressoResolvedJavaMethod[] getDeclaredConstructors(boolean forceLink) {
        if (forceLink) {
            link();
        }
        return getDeclaredConstructors0();
    }

    protected abstract AbstractEspressoResolvedJavaMethod[] getDeclaredConstructors0();

    @Override
    public AbstractEspressoResolvedJavaMethod[] getDeclaredMethods(boolean forceLink) {
        if (forceLink) {
            link();
        }
        return getDeclaredMethods0();
    }

    protected abstract AbstractEspressoResolvedJavaMethod[] getDeclaredMethods0();

    @Override
    public List<ResolvedJavaMethod> getAllMethods(boolean forceLink) {
        if (forceLink) {
            link();
        }
        return Arrays.asList(getAllMethods0());
    }

    protected abstract AbstractEspressoResolvedJavaMethod[] getAllMethods0();

    @Override
    public boolean isCloneableWithAllocation() {
        throw JVMCIError.unimplemented();
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
        JavaType javaType = lookupType(unresolvedJavaType.getName(), this, resolve);
        if (javaType instanceof ResolvedJavaType) {
            return (ResolvedJavaType) javaType;
        }
        return null;
    }

    protected abstract JavaType lookupType(String typeName, AbstractEspressoResolvedInstanceType accessingType, boolean resolve);

    public abstract AbstractEspressoConstantPool getConstantPool();

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AbstractEspressoResolvedInstanceType that = (AbstractEspressoResolvedInstanceType) o;
        return equals0(that);
    }

    protected abstract boolean equals0(AbstractEspressoResolvedInstanceType that);

    @Override
    public abstract int hashCode();

}
