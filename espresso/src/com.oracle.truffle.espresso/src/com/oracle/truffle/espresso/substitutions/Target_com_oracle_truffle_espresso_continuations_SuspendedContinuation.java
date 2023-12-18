package com.oracle.truffle.espresso.substitutions;

import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;

/**
 * VM entry point from the SuspendedContinuation class, responsible for unwinding and rewinding the stack.
 */
@EspressoSubstitutions
public final class Target_com_oracle_truffle_espresso_continuations_SuspendedContinuation {
    @Substitution(hasReceiver = true)
    static void resume0(@JavaType(Object.class) StaticObject suspendedContinuation) {
        System.out.println("resume0 called with " + suspendedContinuation);
    }

    @Substitution(hasReceiver = true)
    static void pause1(@JavaType(Object.class) StaticObject suspendedContinuation) {
        System.out.println("pause1 called with " + suspendedContinuation);
    }
}
