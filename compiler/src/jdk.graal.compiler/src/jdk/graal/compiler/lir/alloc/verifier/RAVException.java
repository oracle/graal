package jdk.graal.compiler.lir.alloc.verifier;

import jdk.graal.compiler.debug.GraalError;

/**
 * Register Allocation Verification Exception -
 * a violation made by the Register Allocator occurred
 * and will be thrown in verification phase.
 */
@SuppressWarnings("serial")
public class RAVException extends GraalError {
    public RAVException(String message) {
        super(message);
    }
}
