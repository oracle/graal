package com.oracle.truffle.espresso.jdwp.api;

public interface KlassRef {

    boolean isArray();

    boolean isInterface();

    String getNameAsString();

    String getTypeAsString();

    MethodRef[] getDeclaredMethods();

    Object getDefiningClassLoader();

    FieldRef[] getDeclaredFields();

    KlassRef[] getImplementedInterfaces();

    int getStatus();

    KlassRef getSuperClass();

    byte getTagConstant();

    boolean isPrimitive();

    Object getPrepareThread();
}
