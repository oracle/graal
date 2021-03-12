package com.oracle.truffle.espresso.redefinition;

import com.oracle.truffle.espresso.impl.ObjectKlass;

public interface ClassLoadListener {
    void onClassLoad(ObjectKlass klass);
}
