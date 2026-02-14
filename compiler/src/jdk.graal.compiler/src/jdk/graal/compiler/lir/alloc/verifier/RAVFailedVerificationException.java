package jdk.graal.compiler.lir.alloc.verifier;

import java.util.List;

@SuppressWarnings("serial")
public class RAVFailedVerificationException extends RAVException {
    public RAVFailedVerificationException(String compUnitName, List<RAVException> exceptions) {
        super(RAVFailedVerificationException.getMessage(compUnitName, exceptions));
    }

    static String getMessage(String compUnitName, List<RAVException> exceptions) {
        StringBuilder sb = new StringBuilder("Failed to verify ");
        sb.append(compUnitName);
        sb.append(":");
        for (var e : exceptions) {
            sb.append(" - ");
            sb.append(e.getMessage());
            sb.append("\n");
        }
        return sb.toString();
    }
}
