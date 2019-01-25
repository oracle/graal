package com.oracle.truffle.espresso.impl;

import com.oracle.truffle.espresso.runtime.EspressoContext;

// Class loaders are context-specific.
public class ClassLoader {

    private final EspressoContext context;

    public ClassLoader(EspressoContext context) {
        this.context = context;
    }

    public EspressoContext getContext() {
        return null;
    }
}
