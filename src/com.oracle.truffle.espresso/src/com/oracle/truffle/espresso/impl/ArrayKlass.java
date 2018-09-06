package com.oracle.truffle.espresso.impl;

import java.lang.reflect.Modifier;

import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;

public final class ArrayKlass extends Klass {
    private final Klass componentType;

    ArrayKlass(Klass componentType) {
        super("[" + componentType.getName());
        this.componentType = componentType;
    }

    @Override
    public ConstantPool getConstantPool() {
        return getComponentType().getConstantPool();
    }

    @Override
    public EspressoContext getContext() {
        return getComponentType().getContext();
    }

    @Override
    public boolean hasFinalizer() {
        return false;
    }

    @Override
    public int getModifiers() {
        return (getElementalType().getModifiers() & (Modifier.PUBLIC | Modifier.PRIVATE | Modifier.PROTECTED)) | Modifier.FINAL | Modifier.ABSTRACT;
    }

    @Override
    public boolean isInterface() {
        return false;
    }

    @Override
    public boolean isInstanceClass() {
        return false;
    }

    @Override
    public boolean isPrimitive() {
        return false;
    }

    @Override
    public boolean isInitialized() {
        return getComponentType().isInitialized();
    }

    @Override
    public void initialize() {
        getComponentType().initialize();
    }

    @Override
    public boolean isLinked() {
        return getComponentType().isLinked();
    }

    @Override
    public boolean isAssignableFrom(Klass other) {
        throw EspressoError.unimplemented();
    }

    @Override
    public Klass getHostClass() {
        return null;
    }

    @Override
    public Klass getSuperclass() {
        Object classLoader = null; // BCL
        if (getConstantPool() != null) {
            classLoader = getConstantPool().getClassLoader();
        }
        return getContext().getRegistries().resolve(getContext().getTypeDescriptors().OBJECT, classLoader);
    }

    @Override
    public Klass[] getInterfaces() {
        return Klass.EMPTY_ARRAY;
    }

    @Override
    public Klass findLeastCommonAncestor(Klass otherType) {
        throw EspressoError.unimplemented();
    }

    @Override
    public Klass getComponentType() {
        return componentType;
    }

    @Override
    public MethodInfo resolveMethod(MethodInfo method, Klass callerType) {
        return null;
    }

    @Override
    public JavaKind getJavaKind() {
        return JavaKind.Object;
    }

    @Override
    public boolean isArray() {
        return true;
    }

    @Override
    public Object getClassLoader() {
        return getComponentType().getClassLoader();
    }

    @Override
    public FieldInfo[] getInstanceFields(boolean includeSuperclasses) {
        return FieldInfo.EMPTY_ARRAY;
    }

    @Override
    public FieldInfo[] getStaticFields() {
        return FieldInfo.EMPTY_ARRAY;
    }

    @Override
    public FieldInfo findInstanceFieldWithOffset(long offset, JavaKind expectedKind) {
        throw EspressoError.unimplemented();
    }

    @Override
    public boolean isLocal() {
        return false;
    }

    @Override
    public boolean isMember() {
        return false;
    }

    @Override
    public Klass getEnclosingType() {
        return null;
    }

    @Override
    public MethodInfo[] getDeclaredConstructors() {
        throw EspressoError.unimplemented();
    }

    @Override
    public MethodInfo[] getDeclaredMethods() {
        return MethodInfo.EMPTY_ARRAY;
    }

    @Override
    public FieldInfo[] getDeclaredFields() {
        return FieldInfo.EMPTY_ARRAY;
    }

    @Override
    public MethodInfo getClassInitializer() {
        return null;
    }

}
