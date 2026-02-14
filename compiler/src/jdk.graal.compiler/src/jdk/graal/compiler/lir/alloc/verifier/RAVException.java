package jdk.graal.compiler.lir.alloc.verifier;

@SuppressWarnings("serial")
public class RAVException extends RuntimeException {
    public RAVException(String message) {
        super(message);
    }
}
