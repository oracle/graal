package com.oracle.truffle.espresso.verifier;

import com.oracle.truffle.espresso.shared.resolver.meta.FieldAccess;
import com.oracle.truffle.espresso.shared.resolver.meta.MethodAccess;
import com.oracle.truffle.espresso.shared.resolver.meta.RuntimeAccess;
import com.oracle.truffle.espresso.shared.resolver.meta.TypeAccess;

public final class Verifier {
    public static <R extends RuntimeAccess<C, M, F>, C extends TypeAccess<C, M, F>, M extends MethodAccess<C, M, F>, F extends FieldAccess<C, M, F>> void verify(R runtime, M method)
                    throws VerificationException {
        MethodVerifier.verify(runtime, method);
    }
}
