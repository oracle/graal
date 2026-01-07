package jdk.graal.compiler.lir.alloc.verifier;

@SuppressWarnings("serial")
public class RAVError extends Error {
    // These shouldn't happen within the verifier
    public RAVError(String message) {
        super(message);
    }
}
