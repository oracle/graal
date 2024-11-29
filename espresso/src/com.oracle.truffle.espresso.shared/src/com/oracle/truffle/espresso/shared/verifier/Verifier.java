package com.oracle.truffle.espresso.shared.verifier;

import com.oracle.truffle.espresso.shared.resolver.meta.FieldAccess;
import com.oracle.truffle.espresso.shared.resolver.meta.MethodAccess;
import com.oracle.truffle.espresso.shared.resolver.meta.RuntimeAccess;
import com.oracle.truffle.espresso.shared.resolver.meta.TypeAccess;

/**
 * Public API for calling the Bytecode Verifier.
 */
public final class Verifier {
    /**
     * Given a {@link RuntimeAccess runtime} and a {@link MethodAccess method}, performs bytecode
     * verification for the given method.
     * <p>
     * If this method returns without throwing, verification was successful.
     *
     * @throws VerificationException If the method was rejected by verification. The
     *             {@link VerificationException#kind()} method provides additional information so
     *             the caller may translate the {@link VerificationException} into a corresponding
     *             error in its runtime.
     *
     * @see RuntimeAccess
     */
    public static <R extends RuntimeAccess<C, M, F>, C extends TypeAccess<C, M, F>, M extends MethodAccess<C, M, F>, F extends FieldAccess<C, M, F>> void verify(R runtime, M method)
                    throws VerificationException {
        MethodVerifier.verify(runtime, method);
    }

    private Verifier() {
    }
}
