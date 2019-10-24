package com.oracle.truffle.espresso.debugger.api;

public interface klassRef {

    boolean isArray();

    boolean isInterface();

    String getNameAsString();

    String getTypeAsString();

    MethodRef[] getDeclaredMethods();

    Object getDefiningClassLoader();

    FieldRef[] getDeclaredFields();

    klassRef[] getImplementedInterfaces();

    int getStatus();

    klassRef getSuperClass();

    byte getTagConstant();

    boolean isPrimitive();
}
