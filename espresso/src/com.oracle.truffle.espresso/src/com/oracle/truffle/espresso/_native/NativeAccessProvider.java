package com.oracle.truffle.espresso._native;

import com.oracle.truffle.espresso.runtime.EspressoContext;

public interface NativeAccessProvider {
    String id();
    NativeAccess create(EspressoContext context);
}
