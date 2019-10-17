package com.oracle.truffle.espresso.substitutions;

import com.oracle.truffle.espresso.runtime.StaticObject;

public interface ReferenceWrapper {
    StaticObject getGuestReference();

    void clear();
}
