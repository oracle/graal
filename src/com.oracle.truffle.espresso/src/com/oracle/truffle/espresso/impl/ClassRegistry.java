package com.oracle.truffle.espresso.impl;

import com.oracle.truffle.espresso.types.TypeDescriptor;

public interface ClassRegistry {
    Klass resolve(TypeDescriptor type);
    Klass findLoadedClass(TypeDescriptor type);
}
