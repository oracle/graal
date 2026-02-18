package jdk.graal.compiler.lir.alloc.verifier;

import jdk.graal.compiler.debug.GraalError;

/**
 * An internal error occurred within the
 * verification process, not caused by
 * the Register Allocator.
 */
@SuppressWarnings("serial")
public class RAVError extends GraalError {
    public RAVError(String message) {
        super(message);
    }
}
