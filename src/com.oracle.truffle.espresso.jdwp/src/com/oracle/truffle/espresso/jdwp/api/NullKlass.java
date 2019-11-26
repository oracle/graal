package com.oracle.truffle.espresso.jdwp.api;

public class NullKlass implements KlassRef {
    @Override
    public boolean isArray() {
        return false;
    }

    @Override
    public boolean isInterface() {
        return false;
    }

    @Override
    public String getNameAsString() {
        return null;
    }

    @Override
    public String getTypeAsString() {
        return null;
    }

    @Override
    public MethodRef[] getDeclaredMethods() {
        return new MethodRef[0];
    }

    @Override
    public Object getDefiningClassLoader() {
        return null;
    }

    @Override
    public FieldRef[] getDeclaredFields() {
        return new FieldRef[0];
    }

    @Override
    public KlassRef[] getImplementedInterfaces() {
        return new KlassRef[0];
    }

    @Override
    public int getStatus() {
        return 0;
    }

    @Override
    public KlassRef getSuperClass() {
        return null;
    }

    @Override
    public byte getTagConstant() {
        return 0;
    }

    @Override
    public boolean isPrimitive() {
        return false;
    }

    @Override
    public Object getPrepareThread() {
        return null;
    }

    @Override
    public boolean isAssignable(KlassRef klass) {
        return false;
    }

    @Override
    public Object getKlassObject() {
        return null;
    }

    @Override
    public int getModifiers() {
        return 0;
    }
}
